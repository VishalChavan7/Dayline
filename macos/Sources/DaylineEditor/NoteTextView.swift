import AppKit
import DaylineCore

public extension NSPasteboard.PasteboardType {
    /// Dayline's own copy of a selection (the note HTML), so copy and paste between notes keeps every block.
    static let daylineNote = NSPasteboard.PasteboardType("com.dayline.note-html")
}

/// What the formatting bar shows as active.
public struct FormatState: Equatable {
    public var heading = 0
    public var bold = false, italic = false, underline = false, strike = false, code = false
    public var list: String?          // "ul", "ol"
    public var checklist = false
    public var block: String?         // dlBlock of the paragraph
    public var link: String?
    public var color: NSColor?
    public var highlight: NSColor?
    public var alignment: NSTextAlignment = .natural
    public var inTable = false
    public init() {}
}

/// The note editor's text view. Every formatting command lives here so it can be tested without a window.
@MainActor
public final class NoteTextView: NSTextView, NSTextStorageDelegate {
    public private(set) var style = NoteStyle(dark: false)
    public var onOpenNote: ((String) -> Void)?
    public var onStateChange: ((FormatState) -> Void)?
    /// Used when the view has no window (tests); otherwise the window's undo manager.
    public var ownUndoManager: UndoManager?
    public override var undoManager: UndoManager? { ownUndoManager ?? super.undoManager }

    public static func make(style: NoteStyle) -> NoteTextView {
        let storage = NSTextStorage()
        let layout = NSLayoutManager()
        storage.addLayoutManager(layout)
        let container = NSTextContainer(size: NSSize(width: 600, height: CGFloat.greatestFiniteMagnitude))
        container.widthTracksTextView = true
        container.lineFragmentPadding = 0
        layout.addTextContainer(container)
        let tv = NoteTextView(frame: NSRect(x: 0, y: 0, width: 600, height: 400), textContainer: container)
        tv.configure()
        tv.setStyle(style)
        return tv
    }

    func configure() {
        minSize = .zero
        maxSize = NSSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude)
        isVerticallyResizable = true
        isHorizontallyResizable = false
        autoresizingMask = [.width]
        isRichText = true
        importsGraphics = false
        allowsUndo = true
        drawsBackground = false
        textContainerInset = NSSize(width: 0, height: 6)
        usesFindBar = true
        isIncrementalSearchingEnabled = true
        isContinuousSpellCheckingEnabled = UserDefaults.standard.object(forKey: "notesSpellCheck") as? Bool ?? true
        isGrammarCheckingEnabled = UserDefaults.standard.object(forKey: "notesGrammarCheck") as? Bool ?? true
        isAutomaticSpellingCorrectionEnabled = false
        isAutomaticQuoteSubstitutionEnabled = false
        isAutomaticDashSubstitutionEnabled = false
        isAutomaticLinkDetectionEnabled = true
        smartInsertDeleteEnabled = true
        textStorage?.delegate = self
    }

    // MARK: Appearance

    /// Sets light or dark, recolouring the text so it can never be white on white (or black on black).
    public func setStyle(_ s: NoteStyle) {
        style = s
        appearance = NSAppearance(named: s.dark ? .darkAqua : .aqua)
        insertionPointColor = s.ink
        linkTextAttributes = [.foregroundColor: s.dark ? NSColor(srgbRed: 0.4, green: 0.65, blue: 1, alpha: 1) : NSColor(srgbRed: 0.05, green: 0.4, blue: 0.85, alpha: 1),
                              .underlineStyle: NSUnderlineStyle.single.rawValue, .cursor: NSCursor.pointingHand]
        if let ts = textStorage, ts.length > 0 {
            ts.beginEditing(); NoteHTML.applyTheme(ts, style: s); ts.endEditing()
        }
        var t = typingAttributes
        if t[.dlHighlight] != nil { t[.foregroundColor] = NoteStyle.highlightInk } else if t[.dlColor] == nil { t[.foregroundColor] = s.ink }
        if t[.font] == nil { t[.font] = NoteStyle.font() }
        typingAttributes = t
        decorate()
        needsDisplay = true
    }

    // MARK: Loading and saving

    public func load(html: String) {
        let a = NoteHTML.attributed(from: html, style: style)
        textStorage?.setAttributedString(a)
        typingAttributes = a.length > 0 ? cleanTyping(a.attributes(at: max(0, a.length - 1), effectiveRange: nil)) : style.bodyAttributes
        undoManager?.removeAllActions()
        setSelectedRange(NSRange(location: a.length, length: 0))
        decorate()
    }

    public var html: String { NoteHTML.html(from: textStorage ?? NSAttributedString()) }

    func cleanTyping(_ a: [NSAttributedString.Key: Any]) -> [NSAttributedString.Key: Any] {
        var t = a
        t.removeValue(forKey: .attachment)
        if t[.dlBlock] as? String == "hr" { t.removeValue(forKey: .dlBlock) }
        if t[.font] == nil { t[.font] = NoteStyle.font() }
        if t[.dlHighlight] == nil && t[.dlColor] == nil { t[.foregroundColor] = style.ink }
        return t
    }

    /// Whatever arrives (typing, paste, drag, dictation), plain text uses the theme's ink.
    public nonisolated func textStorage(_ ts: NSTextStorage, didProcessEditing editedMask: NSTextStorageEditActions, range: NSRange, changeInLength delta: Int) {
        guard editedMask.contains(.editedCharacters) || editedMask.contains(.editedAttributes), range.length > 0, range.location + range.length <= ts.length else { return }
        MainActor.assumeIsolated {
            ts.enumerateAttributes(in: range) { a, r, _ in
                if a[.dlHighlight] != nil {
                    if !((a[.foregroundColor] as? NSColor)?.isClose(to: NoteStyle.highlightInk) ?? false) { ts.addAttribute(.foregroundColor, value: NoteStyle.highlightInk, range: r) }
                } else if a[.dlColor] == nil {
                    if !((a[.foregroundColor] as? NSColor)?.isClose(to: style.ink) ?? false) { ts.addAttribute(.foregroundColor, value: style.ink, range: r) }
                }
                if a[.font] == nil { ts.addAttribute(.font, value: NoteStyle.font(), range: r) }
            }
        }
    }

    public override func didChangeText() {
        super.didChangeText()
        decorate()
    }

    public override func setSelectedRanges(_ ranges: [NSValue], affinity: NSSelectionAffinity, stillSelecting: Bool) {
        super.setSelectedRanges(ranges, affinity: affinity, stillSelecting: stillSelecting)
        guard !stillSelecting, let ts = textStorage else { return }
        // At the start of a line, type in that line's style (a table cell, a quote, a heading), not the previous line's.
        let r = selectedRange()
        if r.length == 0, r.location < ts.length, r.location == 0 || ns.character(at: r.location - 1) == 10 {
            var t = cleanTyping(ts.attributes(at: r.location, effectiveRange: nil))
            if ns.character(at: r.location) == 10 {
                // An empty line: keep a style chosen for it (bold, a colour) but take the line's paragraph.
                for k: NSAttributedString.Key in [.dlColor, .dlHighlight, .dlCode, .foregroundColor, .backgroundColor, .underlineStyle, .strikethroughStyle] { t[k] = typingAttributes[k] ?? t[k] }
            }
            typingAttributes = t
        }
        onStateChange?(formatState)
    }

    // MARK: Wiki links and tags

    static let wikiRe = try! NSRegularExpression(pattern: "\\[\\[([^\\[\\]\\n|]{1,120})(?:\\|[^\\]\\n]{0,120})?\\]\\]")
    static let tagRe = try! NSRegularExpression(pattern: "(?<![\\p{L}\\p{N}_&#/])#[\\p{L}\\p{N}_-]{1,32}(?:/[\\p{L}\\p{N}_-]{1,32}){0,5}")

    /// Colours [[links]] and #tags (display only; nothing is stored).
    public func decorate() {
        guard let lm = layoutManager, let ts = textStorage else { return }
        let all = NSRange(location: 0, length: ts.length)
        lm.removeTemporaryAttribute(.foregroundColor, forCharacterRange: all)
        lm.removeTemporaryAttribute(.underlineStyle, forCharacterRange: all)
        let accent = style.dark ? NSColor(srgbRed: 0.4, green: 0.65, blue: 1, alpha: 1) : NSColor(srgbRed: 0.05, green: 0.4, blue: 0.85, alpha: 1)
        for m in Self.wikiRe.matches(in: ts.string, range: all) {
            lm.addTemporaryAttributes([.foregroundColor: accent, .underlineStyle: NSUnderlineStyle.single.rawValue], forCharacterRange: m.range)
        }
        for m in Self.tagRe.matches(in: ts.string, range: all) {
            let block = ts.attribute(.dlBlock, at: m.range.location, effectiveRange: nil) as? String
            if block?.hasPrefix("code") == true || ts.attribute(.dlCode, at: m.range.location, effectiveRange: nil) != nil { continue }
            lm.addTemporaryAttributes([.foregroundColor: accent], forCharacterRange: m.range)
        }
    }

    public func wikiTitle(at index: Int) -> String? {
        let ns = string as NSString
        for m in Self.wikiRe.matches(in: string, range: NSRange(location: 0, length: ns.length)) where NSLocationInRange(index, m.range) {
            return ns.substring(with: m.range(at: 1)).trimmingCharacters(in: .whitespaces)
        }
        return nil
    }

    // MARK: Mouse

    public override func mouseDown(with event: NSEvent) {
        let p = convert(event.locationInWindow, from: nil)
        let i = characterIndexForInsertion(at: p)
        if toggleCheckbox(near: i) { return }
        if let title = wikiTitle(at: i) ?? (i > 0 ? wikiTitle(at: i - 1) : nil), event.clickCount == 1, onOpenNote != nil,
           event.modifierFlags.contains(.command) || !isEditable || selectedRange().length == 0 && clickIsOnText(p, i) {
            onOpenNote?(title)
            return
        }
        super.mouseDown(with: event)
    }

    func clickIsOnText(_ p: NSPoint, _ i: Int) -> Bool {
        guard let lm = layoutManager, let tc = textContainer, i < (textStorage?.length ?? 0) else { return false }
        let glyph = lm.glyphIndexForCharacter(at: i)
        var rect = lm.boundingRect(forGlyphRange: NSRange(location: glyph, length: 1), in: tc)
        rect.origin.x += textContainerOrigin.x; rect.origin.y += textContainerOrigin.y
        return rect.insetBy(dx: -8, dy: -2).contains(p)
    }

    /// Ticks or unticks the ☐/☑ box at the start of the line at `i`. Returns whether it did.
    @discardableResult
    public func toggleCheckbox(near i: Int) -> Bool {
        let ns = string as NSString
        for idx in [i, i - 1] where idx >= 0 && idx < ns.length {
            let ch = ns.character(at: idx)
            guard ch == 0x2610 || ch == 0x2611 else { continue }
            let para = ns.paragraphRange(for: NSRange(location: idx, length: 0))
            guard para.location == idx, let storage = textStorage else { continue }
            let p = NoteCodec.checkPrefix(ns.substring(with: para))
            let len = (String(ns.substring(with: para).unicodeScalars.prefix(p)) as NSString).length
            let attrs = storage.attributes(at: idx, effectiveRange: nil)
            let replacement = ch == 0x2610 ? NoteCodec.checkDone : NoteCodec.checkOpen
            replace(NSRange(location: idx, length: len), with: NSAttributedString(string: replacement, attributes: attrs))
            return true
        }
        return false
    }

    // MARK: Editing helpers

    var storage: NSTextStorage { textStorage! }
    var ns: NSString { storage.string as NSString }

    /// Replaces text with undo.
    public func replace(_ r: NSRange, with s: NSAttributedString, select: NSRange? = nil) {
        guard shouldChangeText(in: r, replacementString: s.string) else { return }
        storage.replaceCharacters(in: r, with: s)
        didChangeText()
        if let select { setSelectedRange(select) }
    }

    /// Changes attributes with undo.
    func changeAttributes(_ r: NSRange, _ f: (NSTextStorage) -> Void) {
        guard shouldChangeText(in: r, replacementString: nil) else { return }
        storage.beginEditing(); f(storage); storage.endEditing()
        didChangeText()
    }

    /// Paragraphs touched by a range, each with its terminator.
    public func paragraphs(in r0: NSRange) -> [NSRange] {
        var r = r0
        // A selection that ends at the start of a line doesn't include that line.
        if r.length > 0, r.upperBound <= ns.length, r.upperBound > 0, ns.character(at: r.upperBound - 1) == 10 { r.length -= 1 }
        let full = ns.paragraphRange(for: r)
        var out: [NSRange] = []
        ns.enumerateSubstrings(in: full, options: [.byParagraphs, .substringNotRequired]) { _, _, enclosing, _ in out.append(enclosing) }
        if out.isEmpty { out = [NSRange(location: full.location, length: 0)] }
        // The empty last line after a final newline.
        if r.location == ns.length && ns.length > 0 && ns.hasSuffix("\n") { out = [NSRange(location: ns.length, length: 0)] }
        return out
    }

    /// The lines a command works on: the selected lines, without empty lines at the end of a longer selection.
    func commandParagraphs() -> [NSRange] {
        var paras = paragraphs(in: selectedRange())
        while paras.count > 1, let last = paras.last, contentRange(last).length == 0 { paras.removeLast() }
        return paras
    }

    func contentRange(_ p: NSRange) -> NSRange {
        var len = p.length
        while len > 0 { let c = ns.character(at: p.location + len - 1); if c == 10 || c == 0x2029 { len -= 1 } else { break } }
        return NSRange(location: p.location, length: len)
    }

    func attr<T>(_ key: NSAttributedString.Key, _ p: NSRange) -> T? {
        guard storage.length > 0 else { return typingAttributes[key] as? T }
        if p.location >= storage.length { return typingAttributes[key] as? T }
        return storage.attribute(key, at: p.location, effectiveRange: nil) as? T
    }

    func paragraphStyle(_ p: NSRange) -> NSMutableParagraphStyle {
        ((attr(.paragraphStyle, p) as NSParagraphStyle?)?.mutableCopy() as? NSMutableParagraphStyle) ?? NoteStyle.baseParagraph()
    }


    // MARK: Character formatting

    public func toggleTrait(_ trait: NSFontTraitMask) {
        let r = selectedRange()
        let fm = NSFontManager.shared
        if r.length == 0 {
            let f = (typingAttributes[.font] as? NSFont) ?? NoteStyle.font()
            typingAttributes[.font] = fm.traits(of: f).contains(trait) ? fm.convert(f, toNotHaveTrait: trait) : fm.convert(f, toHaveTrait: trait)
            onStateChange?(formatState)
            return
        }
        var allHave = true
        storage.enumerateAttribute(.font, in: r) { v, _, _ in if let f = v as? NSFont, !fm.traits(of: f).contains(trait) { allHave = false } }
        changeAttributes(r) { s in
            s.enumerateAttribute(.font, in: r) { v, rr, _ in
                let f = (v as? NSFont) ?? NoteStyle.font()
                s.addAttribute(.font, value: allHave ? fm.convert(f, toNotHaveTrait: trait) : fm.convert(f, toHaveTrait: trait), range: rr)
            }
        }
        onStateChange?(formatState)
    }

    public func toggleLine(_ key: NSAttributedString.Key) {
        let r = selectedRange()
        guard r.length > 0 else {
            typingAttributes[key] = (typingAttributes[key] as? Int ?? 0) != 0 ? 0 : NSUnderlineStyle.single.rawValue
            onStateChange?(formatState)
            return
        }
        var allOn = true
        storage.enumerateAttribute(key, in: r) { v, _, _ in if (v as? Int ?? 0) == 0 { allOn = false } }
        changeAttributes(r) { s in
            if allOn { s.removeAttribute(key, range: r) } else { s.addAttribute(key, value: NSUnderlineStyle.single.rawValue, range: r) }
        }
        onStateChange?(formatState)
    }

    public func toggleInlineCode() {
        let r = selectedRange()
        guard r.length > 0 else {
            if typingAttributes[.dlCode] != nil {
                typingAttributes.removeValue(forKey: .dlCode); typingAttributes.removeValue(forKey: .backgroundColor); typingAttributes[.font] = NoteStyle.font()
            } else {
                typingAttributes[.dlCode] = true; typingAttributes[.font] = NoteStyle.codeFont; typingAttributes[.backgroundColor] = style.codeBackground
            }
            onStateChange?(formatState); return
        }
        var allCode = true
        storage.enumerateAttribute(.dlCode, in: r) { v, _, _ in if v == nil { allCode = false } }
        changeAttributes(r) { s in
            if allCode {
                s.removeAttribute(.dlCode, range: r)
                s.enumerateAttribute(.dlHighlight, in: r) { v, rr, _ in if v == nil { s.removeAttribute(.backgroundColor, range: rr) } }
                s.addAttribute(.font, value: NoteStyle.font(), range: r)
            } else {
                s.addAttribute(.dlCode, value: true, range: r)
                s.addAttribute(.font, value: NoteStyle.codeFont, range: r)
                s.enumerateAttribute(.dlHighlight, in: r) { v, rr, _ in if v == nil { s.addAttribute(.backgroundColor, value: style.codeBackground, range: rr) } }
            }
        }
        onStateChange?(formatState)
    }

    /// A text colour, or nil for the theme's ink.
    public func setTextColor(_ c: NSColor?) {
        let r = selectedRange()
        if r.length == 0 {
            if let c { typingAttributes[.foregroundColor] = c; typingAttributes[.dlColor] = true }
            else { typingAttributes.removeValue(forKey: .dlColor); typingAttributes[.foregroundColor] = typingAttributes[.dlHighlight] != nil ? NoteStyle.highlightInk : style.ink }
            onStateChange?(formatState); return
        }
        changeAttributes(r) { s in
            if let c { s.addAttribute(.foregroundColor, value: c, range: r); s.addAttribute(.dlColor, value: true, range: r) }
            else { s.removeAttribute(.dlColor, range: r); NoteHTML.applyTheme(s, style: style, range: r) }
        }
        onStateChange?(formatState)
    }

    /// A highlighter colour, or nil to remove highlighting.
    public func setHighlight(_ c: NSColor?) {
        let r = selectedRange()
        if r.length == 0 {
            if let c { typingAttributes[.backgroundColor] = c; typingAttributes[.dlHighlight] = true; typingAttributes[.foregroundColor] = NoteStyle.highlightInk }
            else {
                typingAttributes.removeValue(forKey: .dlHighlight); typingAttributes.removeValue(forKey: .backgroundColor)
                if typingAttributes[.dlColor] == nil { typingAttributes[.foregroundColor] = style.ink }
            }
            onStateChange?(formatState); return
        }
        changeAttributes(r) { s in
            if let c {
                s.addAttribute(.backgroundColor, value: c, range: r); s.addAttribute(.dlHighlight, value: true, range: r)
                s.addAttribute(.foregroundColor, value: NoteStyle.highlightInk, range: r)
            } else {
                s.removeAttribute(.dlHighlight, range: r); s.removeAttribute(.backgroundColor, range: r)
                NoteHTML.applyTheme(s, style: style, range: r)
            }
        }
        onStateChange?(formatState)
    }

    /// Links the selection (or inserts the link text) to a web address; nil removes the link.
    public func setLink(_ url: URL?, text: String? = nil) {
        var r = selectedRange()
        if r.length == 0, let url {
            let t = (text?.isEmpty == false ? text! : url.absoluteString)
            var a = typingAttributes; a[.link] = url
            replace(r, with: NSAttributedString(string: t, attributes: a), select: NSRange(location: r.location + (t as NSString).length, length: 0))
            typingAttributes.removeValue(forKey: .link)
            return
        }
        if r.length == 0, url == nil {
            var eff = NSRange()
            guard r.location < storage.length, storage.attribute(.link, at: r.location, longestEffectiveRange: &eff, in: NSRange(location: 0, length: storage.length)) != nil else { return }
            r = eff
        }
        changeAttributes(r) { s in if let url { s.addAttribute(.link, value: url, range: r) } else { s.removeAttribute(.link, range: r) } }
        onStateChange?(formatState)
    }

    public func insertWikiLink() {
        let r = selectedRange()
        let sel = ns.substring(with: r)
        let a = typingAttributes
        replace(r, with: NSAttributedString(string: "[[\(sel)]]", attributes: a),
                select: NSRange(location: r.location + 2 + (sel as NSString).length, length: 0))
    }

    public func insertPlain(_ text: String) {
        let r = selectedRange()
        replace(r, with: NSAttributedString(string: text, attributes: cleanTyping(typingAttributes)), select: NSRange(location: r.location + (text as NSString).length, length: 0))
    }

    public func clearFormatting() {
        let paras = paragraphs(in: selectedRange())
        let r = selectedRange().length > 0 ? selectedRange() : NSUnionRange(paras.first!, paras.last!)
        guard r.length > 0 else { typingAttributes = style.bodyAttributes; return }
        changeAttributes(r) { s in
            for k: NSAttributedString.Key in [.dlColor, .dlHighlight, .dlCode, .backgroundColor, .underlineStyle, .strikethroughStyle, .link, .dlHeading] { s.removeAttribute(k, range: r) }
            s.enumerateAttribute(.dlBlock, in: r) { v, rr, _ in
                let code = (v as? String)?.hasPrefix("code") == true
                s.addAttribute(.font, value: code ? NoteStyle.codeFont : NoteStyle.font(), range: rr)
            }
            NoteHTML.applyTheme(s, style: style, range: r)
        }
        onStateChange?(formatState)
    }

    // MARK: Paragraph formatting

    /// Heading 1–6, or 0 for body text. Choosing the level a line already has makes it body text again.
    public func setHeading(_ level: Int) {
        let paras = commandParagraphs()
        let all = paras.allSatisfy { (attr(.dlHeading, $0) as Int? ?? 0) == level }
        let target = all && level > 0 ? 0 : level
        let blocked = paras.contains { ($0.length > 0 ? attr(.dlBlock, $0) as String? : nil).map { $0.hasPrefix("code") || $0 == "table" || $0 == "hr" } ?? false }
        if blocked { return }
        let hull = NSUnionRange(paras.first!, paras.last!)
        if hull.length > 0 {
            changeAttributes(hull) { s in
                s.enumerateAttribute(.font, in: hull) { v, rr, _ in
                    let italic = (v as? NSFont).map { NSFontManager.shared.traits(of: $0).contains(.italicFontMask) } ?? false
                    let f = target > 0 ? NoteStyle.headingFont(target, italic: italic) : NoteStyle.font(italic: italic)
                    s.addAttribute(.font, value: (v as? NSFont).map { NSFontManager.shared.traits(of: $0).contains(.fixedPitchFontMask) } == true && target == 0 ? NoteStyle.codeFont : f, range: rr)
                }
                if target > 0 { s.addAttribute(.dlHeading, value: target, range: hull) } else { s.removeAttribute(.dlHeading, range: hull) }
            }
        }
        var t = typingAttributes
        t[.font] = target > 0 ? NoteStyle.headingFont(target) : NoteStyle.font()
        if target > 0 { t[.dlHeading] = target } else { t.removeValue(forKey: .dlHeading) }
        typingAttributes = t
        onStateChange?(formatState)
    }

    public func setAlignment(_ a: NSTextAlignment) {
        let paras = commandParagraphs()
        let hull = NSUnionRange(paras.first!, paras.last!)
        if hull.length > 0 {
            changeAttributes(hull) { s in
                for p in paras where p.length > 0 {
                    let ps = paragraphStyle(p); ps.alignment = a
                    s.addAttribute(.paragraphStyle, value: ps, range: p)
                }
            }
        }
        let ps = ((typingAttributes[.paragraphStyle] as? NSParagraphStyle)?.mutableCopy() as? NSMutableParagraphStyle) ?? NoteStyle.baseParagraph()
        ps.alignment = a; typingAttributes[.paragraphStyle] = ps
        onStateChange?(formatState)
    }

    // MARK: Lists

    static let markerRe = try! NSRegularExpression(pattern: "^\\t[^\\t]{0,6}\\t")

    func markerLength(_ p: NSRange) -> Int {
        let c = contentRange(p)
        return Self.markerRe.firstMatch(in: storage.string, range: c).map { $0.range.location == c.location ? $0.range.length : 0 } ?? 0
    }

    func listLevels(_ p: NSRange) -> [NSTextList] { (attr(.paragraphStyle, p) as NSParagraphStyle?)?.textLists ?? [] }

    func isPlainBlockFree(_ p: NSRange) -> Bool {
        guard let b: String = attr(.dlBlock, p) else { return true }
        return b == "quote" || b.hasPrefix("callout")
    }

    /// Bulleted or numbered list on the selected lines (or back to plain lines).
    public func toggleList(ordered: Bool) {
        let paras = commandParagraphs().filter(isPlainBlockFree)
        guard !paras.isEmpty else { return }
        let remove = paras.allSatisfy { p in listLevels(p).last.map { NoteBlocks.isOrdered($0) == ordered } ?? false }
        rebuildParagraphs(paras) { p, text, ps in
            let depth = max(1, ps.textLists.count)
            if remove {
                ps.textLists = []; NoteBlocks.setListIndent(ps, depth: 0)
            } else {
                var levels = ps.textLists.map(NoteBlocks.isOrdered)
                if levels.isEmpty { levels = [ordered] } else { levels[levels.count - 1] = ordered }
                ps.textLists = levels.enumerated().map { NoteBlocks.list(ordered: $0.element, depth: $0.offset + 1) }
                NoteBlocks.setListIndent(ps, depth: depth)
            }
            // Checklist boxes and list markers don't mix.
            if !remove, NoteCodec.checkPrefix(text.string) > 0 {
                let len = (String(text.string.unicodeScalars.prefix(NoteCodec.checkPrefix(text.string))) as NSString).length
                text.deleteCharacters(in: NSRange(location: 0, length: len))
            }
        }
        renumberLists()
    }

    /// Deeper or shallower list level for the selected list lines.
    public func indent(_ delta: Int) {
        let paras = commandParagraphs().filter { !listLevels($0).isEmpty }
        guard !paras.isEmpty else { return }
        rebuildParagraphs(paras) { _, _, ps in
            var levels = ps.textLists.map(NoteBlocks.isOrdered)
            if delta > 0 { if levels.count < 5 { levels.append(levels.last ?? false) } }
            else { levels.removeLast() }
            ps.textLists = levels.enumerated().map { NoteBlocks.list(ordered: $0.element, depth: $0.offset + 1) }
            NoteBlocks.setListIndent(ps, depth: levels.count)
        }
        renumberLists()
    }

    /// Rewrites each paragraph's content (without its list marker) and paragraph style, then puts markers back.
    func rebuildParagraphs(_ paras: [NSRange], lineAttributes: [NSAttributedString.Key: Any?] = [:],
                           _ f: (NSRange, NSMutableAttributedString, NSMutableParagraphStyle) -> Void) {
        let hull = NSUnionRange(paras.first!, paras.last!)
        let out = NSMutableAttributedString()
        var firstContentOffset = 0
        var lastStyle = NoteStyle.baseParagraph()
        for (i, p) in paras.enumerated() {
            let c = contentRange(p)
            let ml = markerLength(p)
            let body = NSMutableAttributedString(attributedString: storage.attributedSubstring(from: NSRange(location: c.location + ml, length: c.length - ml)))
            let terminator = storage.attributedSubstring(from: NSRange(location: c.location + c.length, length: p.length - c.length))
            let ps = paragraphStyle(p)
            f(p, body, ps)
            let line = NSMutableAttributedString(attributedString: body)
            let levels = ps.textLists
            if !levels.isEmpty {
                let a = body.length > 0 ? body.attributes(at: 0, effectiveRange: nil) : cleanTyping(typingAttributes)
                var ma = a; ma.removeValue(forKey: .link); ma.removeValue(forKey: .dlCode); ma.removeValue(forKey: .backgroundColor); ma.removeValue(forKey: .dlHighlight)
                line.insert(NSAttributedString(string: "\t\(NoteBlocks.marker(ordered: NoteBlocks.isOrdered(levels.last!), number: 1, depth: levels.count))\t", attributes: ma), at: 0)
            }
            line.append(terminator)
            let lr = NSRange(location: 0, length: line.length)
            if lr.length > 0 {
                line.addAttribute(.paragraphStyle, value: ps, range: lr)
                for (k, v) in lineAttributes { if let v { line.addAttribute(k, value: v, range: lr) } else { line.removeAttribute(k, range: lr) } }
            }
            lastStyle = ps
            if i == 0 { firstContentOffset = line.length - terminator.length }
            out.append(line)
        }
        let caret = hull.location + (paras.count == 1 ? firstContentOffset : out.length)
        replace(hull, with: out, select: NSRange(location: min(caret, hull.location + out.length), length: 0))
        // An empty last line has no characters to hold its style: the typing attributes carry it.
        var t = typingAttributes
        t[.paragraphStyle] = lastStyle
        for (k, v) in lineAttributes { if let v { t[k] = v } else { t.removeValue(forKey: k) } }
        typingAttributes = t
        onStateChange?(formatState)
    }

    /// Numbers ordered lists 1, 2, 3… per level again after edits.
    public func renumberLists() {
        var counters: [Int] = []
        var edits: [(NSRange, String)] = []
        let ns = self.ns
        ns.enumerateSubstrings(in: NSRange(location: 0, length: ns.length), options: [.byParagraphs, .substringNotRequired]) { _, r, enc, _ in
            let lists = r.location < self.storage.length || enc.length > 0 ? self.listLevels(enc) : []
            if lists.isEmpty { counters = []; return }
            let depth = lists.count
            if counters.count > depth { counters.removeLast(counters.count - depth) }
            while counters.count < depth { counters.append(0) }
            counters[depth - 1] += 1
            let ml = self.markerLength(enc)
            guard ml > 0 else { return }
            let want = "\t\(NoteBlocks.marker(ordered: NoteBlocks.isOrdered(lists.last!), number: counters[depth - 1], depth: depth))\t"
            let have = ns.substring(with: NSRange(location: r.location, length: ml))
            if have != want { edits.append((NSRange(location: r.location, length: ml), want)) }
        }
        guard !edits.isEmpty else { return }
        let sel = selectedRange()
        var shift = 0
        let typing = typingAttributes
        for (r, s) in edits.reversed() {
            let attrs = storage.attributes(at: r.location, effectiveRange: nil)
            replace(r, with: NSAttributedString(string: s, attributes: attrs))
            if r.location < sel.location { shift += (s as NSString).length - r.length }
        }
        setSelectedRange(NSRange(location: max(0, min(storage.length, sel.location + shift)), length: sel.length))
        typingAttributes = typing
    }

    /// Turns the selected lines into checklist lines (or back).
    public func toggleChecklist() {
        let paras = commandParagraphs().filter(isPlainBlockFree)
        guard !paras.isEmpty else { return }
        let all = paras.allSatisfy { p in NoteCodec.checkPrefix(ns.substring(with: contentRange(p))) > 0 }
        rebuildParagraphs(paras) { _, text, ps in
            let p = NoteCodec.checkPrefix(text.string)
            if all, p > 0 {
                let len = (String(text.string.unicodeScalars.prefix(p)) as NSString).length
                text.deleteCharacters(in: NSRange(location: 0, length: len))
            } else if !all, p == 0 {
                let a = text.length > 0 ? text.attributes(at: 0, effectiveRange: nil) : cleanTyping(typingAttributes)
                text.insert(NSAttributedString(string: NoteCodec.checkOpen, attributes: a), at: 0)
                ps.textLists = []; NoteBlocks.setListIndent(ps, depth: 0)
            }
        }
        if paras.count == 1 { setSelectedRange(NSRange(location: contentRange(paragraphs(in: selectedRange()).first!).upperBound, length: 0)) }
    }

    // MARK: Blocks

    /// The block at the selection: "quote", "callout:tip", "code:swift", "table", "hr" or nil.
    public var currentBlock: String? { attr(.dlBlock, paragraphs(in: selectedRange()).first!) }

    /// Wraps the selected lines in a quote, a callout ("callout:<kind>") or a code block ("code:<language>"),
    /// or takes them out of it when they already are.
    public func toggleBlock(_ kind: String) {
        let paras = commandParagraphs()
        let family = kind.split(separator: ":").first.map(String.init) ?? kind
        let current = paras.compactMap { p -> String? in attr(.dlBlock, p) }
        if current.contains(where: { $0 == "table" || $0 == "hr" }) { return }
        let remove = current.count == paras.count && current.allSatisfy { $0 == kind || ($0.hasPrefix(family) && family != "callout") }
        let toCode = family == "code" && !remove
        let fromCode = current.contains { $0.hasPrefix("code") }
        let tb: NSTextBlock? = remove ? nil : family == "quote" ? NoteBlocks.quote(style: style)
            : family == "callout" ? NoteBlocks.callout(String(kind.dropFirst(8)), style: style) : NoteBlocks.code(style: style)
        var lineAttrs: [NSAttributedString.Key: Any?] = [.dlBlock: tb == nil ? nil : kind]
        if toCode { lineAttrs.updateValue(nil, forKey: .dlHeading) }
        rebuildParagraphs(paras, lineAttributes: lineAttrs) { _, text, ps in
            if toCode || fromCode {
                // Code is plain text; leaving code gives plain body text.
                let plain = text.string
                text.setAttributedString(NSAttributedString(string: plain, attributes: [.font: toCode ? NoteStyle.codeFont : NoteStyle.font()]))
                ps.textLists = []; NoteBlocks.setListIndent(ps, depth: 0)
            }
            ps.textBlocks = tb.map { [$0] } ?? []
            ps.lineSpacing = toCode ? 2 : 4; ps.paragraphSpacing = toCode ? 0 : 4
        }
        if toCode { typingAttributes[.font] = NoteStyle.codeFont } else if fromCode { typingAttributes[.font] = NoteStyle.font() }
        ensureTrailingLine()
        onStateChange?(formatState)
    }

    /// Keeps an ordinary empty line at the end after a block, so there is always somewhere to type.
    public func ensureTrailingLine() {
        guard storage.length > 0, storage.attribute(.dlBlock, at: storage.length - 1, effectiveRange: nil) != nil else { return }
        let sel = selectedRange(), typing = typingAttributes
        let add = NSMutableAttributedString()
        if ns.character(at: storage.length - 1) != 10 {
            add.append(NSAttributedString(string: "\n", attributes: storage.attributes(at: storage.length - 1, effectiveRange: nil)))
        }
        add.append(NSAttributedString(string: "\n", attributes: style.bodyAttributes))
        replace(NSRange(location: storage.length, length: 0), with: add)
        setSelectedRange(sel); typingAttributes = typing
    }

    /// Inserts attributed block text on its own line(s) at the caret.
    func insertBlock(_ block: NSAttributedString, selectInside: Int? = nil) {
        let r = selectedRange()
        let p = paragraphs(in: r).first!
        let c = contentRange(p)
        let out = NSMutableAttributedString()
        if r.location > c.location {
            out.append(NSAttributedString(string: "\n", attributes: storage.attributes(at: r.location - 1, effectiveRange: nil)))
        }
        let blockStart = r.location + out.length
        out.append(block)
        if !block.string.hasSuffix("\n") {
            out.append(NSAttributedString(string: "\n", attributes: block.attributes(at: max(0, block.length - 1), effectiveRange: nil)))
        }
        var target = r
        // Nothing after the caret on this line: the block's own newline replaces the line's.
        if c.upperBound <= r.upperBound { target.length = max(r.length, p.upperBound - r.location) }
        let caret = blockStart + (selectInside ?? (block.string.hasSuffix("\n") ? block.length - 1 : block.length))
        replace(target, with: out, select: NSRange(location: caret, length: 0))
        ensureTrailingLine()
        setSelectedRange(NSRange(location: min(caret, storage.length), length: 0))
        onStateChange?(formatState)
    }

    public func insertDivider() {
        let d = NSMutableAttributedString(attributedString: NoteBlocks.dividerString())
        d.addAttributes([.dlBlock: "hr", .paragraphStyle: NoteStyle.baseParagraph(), .font: NoteStyle.font()], range: NSRange(location: 0, length: d.length))
        insertBlock(d)
        // Caret after the divider, on the following line.
        let i = min(storage.length, selectedRange().location + 1)
        setSelectedRange(NSRange(location: i, length: 0))
        typingAttributes = style.bodyAttributes
    }

    public func insertTable(rows: Int = 3, columns: Int = 3) {
        let cells = Array(repeating: Array(repeating: "", count: max(1, columns)), count: max(1, rows))
        insertBlock(NoteBlocks.tableString(rows: cells, header: true, style: style), selectInside: 0)
    }

    // MARK: Tables

    /// The paragraphs of the table at the caret, in order.
    func tableParagraphs(at i: Int) -> [NSRange]? {
        let p = paragraphs(in: NSRange(location: i, length: 0)).first!
        guard let cell = (attr(.paragraphStyle, p) as NSParagraphStyle?)?.textBlocks.first as? NSTextTableBlock else { return nil }
        var out: [NSRange] = []
        ns.enumerateSubstrings(in: NSRange(location: 0, length: ns.length), options: [.byParagraphs, .substringNotRequired]) { _, _, enc, _ in
            if let c = (self.attr(.paragraphStyle, enc) as NSParagraphStyle?)?.textBlocks.first as? NSTextTableBlock, c.table === cell.table { out.append(enc) }
        }
        return out.isEmpty ? nil : out
    }

    public var currentCell: (row: Int, column: Int)? {
        let p = paragraphs(in: selectedRange()).first!
        guard let c = (attr(.paragraphStyle, p) as NSParagraphStyle?)?.textBlocks.first as? NSTextTableBlock else { return nil }
        return (c.startingRow, c.startingColumn)
    }

    public enum TableEdit { case addRow, addColumn, deleteRow, deleteColumn, toggleHeader, delete }

    public func editTable(_ e: TableEdit) {
        guard let paras = tableParagraphs(at: selectedRange().location), let cell = currentCell else { return }
        let hull = NSUnionRange(paras.first!, paras.last!)
        var (rows, header) = NoteBlocks.tableRows(storage, paras.map { contentRange($0) })
        var focus = cell
        switch e {
        case .addRow: rows.insert(Array(repeating: "", count: rows[0].count), at: cell.row + 1); focus = (cell.row + 1, cell.column)
        case .addColumn: for i in rows.indices { rows[i].insert("", at: cell.column + 1) }; focus = (cell.row, cell.column + 1)
        case .deleteRow: if rows.count > 1 { rows.remove(at: cell.row); focus = (min(cell.row, rows.count - 1), cell.column) } else { return editTable(.delete) }
        case .deleteColumn:
            if rows[0].count > 1 { for i in rows.indices { rows[i].remove(at: cell.column) }; focus = (cell.row, min(cell.column, rows[0].count - 1)) } else { return editTable(.delete) }
        case .toggleHeader: header.toggle()
        case .delete:
            replace(hull, with: NSAttributedString(string: ""), select: NSRange(location: hull.location, length: 0))
            typingAttributes = style.bodyAttributes
            return
        }
        let t = NSMutableAttributedString(attributedString: NoteBlocks.tableString(rows: rows, header: header, style: style))
        let endsWithNewline = hull.length > 0 && ns.character(at: hull.upperBound - 1) == 10
        if !endsWithNewline { t.deleteCharacters(in: NSRange(location: t.length - 1, length: 1)) }
        replace(hull, with: t, select: NSRange(location: hull.location, length: 0))
        moveToCell(row: focus.row, column: focus.column)
        ensureTrailingLine()
    }

    public func moveToCell(row: Int, column: Int) {
        guard let paras = tableParagraphs(at: selectedRange().location) else { return }
        for p in paras {
            if let c = (attr(.paragraphStyle, p) as NSParagraphStyle?)?.textBlocks.first as? NSTextTableBlock, c.startingRow == row, c.startingColumn == column {
                setSelectedRange(NSRange(location: contentRange(p).upperBound, length: 0)); return
            }
        }
    }

    // MARK: Keys

    public override func insertNewline(_ sender: Any?) {
        let r = selectedRange()
        guard r.length == 0 else { return super.insertNewline(sender) }
        let p = paragraphs(in: r).first!
        let c = contentRange(p)
        let text = ns.substring(with: c)
        let block: String? = (c.length > 0 || p.length > 0) ? attr(.dlBlock, p) : typingAttributes[.dlBlock] as? String
        // Checklist: a new box on the next line; an empty box ends the checklist.
        let box = NoteCodec.checkPrefix(text)
        if box > 0 && !(block?.hasPrefix("code") ?? false) {
            let boxLen = (String(text.unicodeScalars.prefix(box)) as NSString).length
            if c.length == boxLen {
                replace(NSRange(location: c.location, length: boxLen), with: NSAttributedString(string: ""), select: NSRange(location: c.location, length: 0)); return
            }
            if r.location == c.upperBound {
                let a = cleanTyping(storage.attributes(at: c.location, effectiveRange: nil))
                replace(r, with: NSAttributedString(string: "\n" + NoteCodec.checkOpen, attributes: a), select: NSRange(location: r.location + 1 + (NoteCodec.checkOpen as NSString).length, length: 0))
                return
            }
        }
        // Return on an empty line inside a quote, callout or code block leaves the block.
        if let block, block != "table", block != "hr", c.length == 0 {
            toggleBlock(block); return
        }
        let heading: Int = (c.length > 0 ? attr(.dlHeading, p) : nil) ?? 0
        super.insertNewline(sender)
        if heading > 0 && r.location == c.upperBound {
            // The line after a heading is body text.
            let np = paragraphs(in: selectedRange()).first!
            if np.length > 0 { changeAttributes(np) { s in s.removeAttribute(.dlHeading, range: np); s.addAttribute(.font, value: NoteStyle.font(), range: np) } }
            typingAttributes.removeValue(forKey: .dlHeading); typingAttributes[.font] = NoteStyle.font()
        }
        if block == "hr" {
            typingAttributes = style.bodyAttributes
        }
        if !listLevels(paragraphs(in: selectedRange()).first!).isEmpty { renumberLists() }
    }

    public override func insertTab(_ sender: Any?) {
        if let cell = currentCell, let paras = tableParagraphs(at: selectedRange().location) {
            let (rows, _) = NoteBlocks.tableRows(storage, paras.map { contentRange($0) })
            let cols = rows.first?.count ?? 1
            if cell.column + 1 < cols { moveToCell(row: cell.row, column: cell.column + 1) }
            else if cell.row + 1 < rows.count { moveToCell(row: cell.row + 1, column: 0) }
            else { editTable(.addRow); moveToCell(row: cell.row + 1, column: 0) }
            return
        }
        if !listLevels(paragraphs(in: selectedRange()).first!).isEmpty { indent(1); return }
        super.insertTab(sender)
    }

    public override func insertBacktab(_ sender: Any?) {
        if let cell = currentCell, let paras = tableParagraphs(at: selectedRange().location) {
            let cols = NoteBlocks.tableRows(storage, paras.map { contentRange($0) }).0.first?.count ?? 1
            if cell.column > 0 { moveToCell(row: cell.row, column: cell.column - 1) } else if cell.row > 0 { moveToCell(row: cell.row - 1, column: cols - 1) }
            return
        }
        if !listLevels(paragraphs(in: selectedRange()).first!).isEmpty { indent(-1); return }
        super.insertBacktab(sender)
    }

    // MARK: Moving lines

    /// Moves the selected lines up or down past the neighbouring line.
    public func moveLines(up: Bool) {
        let paras = paragraphs(in: selectedRange())
        let hull = NSUnionRange(paras.first!, paras.last!)
        if up {
            guard hull.location > 0 else { return }
            let prev = paragraphs(in: NSRange(location: hull.location - 1, length: 0)).first!
            let moving = NSMutableAttributedString(attributedString: storage.attributedSubstring(from: hull))
            var above = NSMutableAttributedString(attributedString: storage.attributedSubstring(from: prev))
            if !moving.string.hasSuffix("\n") {
                // The last line has no newline: move the one from the line above.
                moving.append(NSAttributedString(string: "\n", attributes: above.attributes(at: max(0, above.length - 1), effectiveRange: nil)))
                above = NSMutableAttributedString(attributedString: above.attributedSubstring(from: NSRange(location: 0, length: max(0, above.length - 1))))
            }
            let out = NSMutableAttributedString(attributedString: moving); out.append(above)
            let offset = selectedRange().location - hull.location
            replace(NSUnionRange(prev, hull), with: out, select: NSRange(location: prev.location + offset, length: selectedRange().length))
        } else {
            guard hull.upperBound < ns.length else { return }
            let next = paragraphs(in: NSRange(location: hull.upperBound, length: 0)).first!
            guard next.length > 0 else { return }
            let below = NSMutableAttributedString(attributedString: storage.attributedSubstring(from: next))
            var moving = NSMutableAttributedString(attributedString: storage.attributedSubstring(from: hull))
            if !below.string.hasSuffix("\n") {
                below.append(NSAttributedString(string: "\n", attributes: moving.attributes(at: max(0, moving.length - 1), effectiveRange: nil)))
                moving = NSMutableAttributedString(attributedString: moving.attributedSubstring(from: NSRange(location: 0, length: max(0, moving.length - 1))))
            }
            let out = NSMutableAttributedString(attributedString: below); out.append(moving)
            let offset = selectedRange().location - hull.location
            let sel = selectedRange().length
            replace(NSUnionRange(hull, next), with: out, select: NSRange(location: hull.location + below.length + offset, length: sel))
        }
        renumberLists()
    }

    // MARK: Paste and copy

    public override var readablePasteboardTypes: [NSPasteboard.PasteboardType] { [.daylineNote] + super.readablePasteboardTypes }
    public override var writablePasteboardTypes: [NSPasteboard.PasteboardType] { [.daylineNote] + super.writablePasteboardTypes }

    public override func writeSelection(to pboard: NSPasteboard, type: NSPasteboard.PasteboardType) -> Bool {
        if type == .daylineNote {
            let r = selectedRange()
            guard r.length > 0 else { return false }
            return pboard.setString(NoteHTML.html(from: storage.attributedSubstring(from: r)), forType: .daylineNote)
        }
        return super.writeSelection(to: pboard, type: type)
    }

    public override func readSelection(from pboard: NSPasteboard, type: NSPasteboard.PasteboardType) -> Bool {
        if type == .daylineNote, let html = pboard.string(forType: .daylineNote) {
            pasteAttributed(NoteHTML.attributed(from: html, style: style), keepBlocks: true); return true
        }
        if let a = Self.richText(pboard, type) {
            pasteAttributed(sanitize(a), keepBlocks: false); return true
        }
        if type == .string, let s = pboard.string(forType: .string) {
            pasteAttributed(NSAttributedString(string: s, attributes: cleanTyping(typingAttributes)), keepBlocks: false); return true
        }
        return super.readSelection(from: pboard, type: type)
    }

    static func richText(_ pboard: NSPasteboard, _ type: NSPasteboard.PasteboardType) -> NSAttributedString? {
        guard let data = pboard.data(forType: type) else { return nil }
        switch type {
        case .rtf: return NSAttributedString(rtf: data, documentAttributes: nil)
        case .rtfd, NSPasteboard.PasteboardType("com.apple.flat-rtfd"): return NSAttributedString(rtfd: data, documentAttributes: nil)
        case .html: return NSAttributedString(html: data, documentAttributes: nil)
        default: return nil
        }
    }

    func pasteAttributed(_ a: NSAttributedString, keepBlocks: Bool) {
        let r = selectedRange()
        let m = NSMutableAttributedString(attributedString: a)
        if !keepBlocks, m.length > 0 {
            // Pasted text takes the paragraph it lands in (inside a quote it becomes quote text).
            let ps = typingAttributes[.paragraphStyle] ?? NoteStyle.baseParagraph()
            m.addAttribute(.paragraphStyle, value: ps, range: NSRange(location: 0, length: m.length))
            if let b = typingAttributes[.dlBlock] as? String, b != "hr", b != "table" { m.addAttribute(.dlBlock, value: b, range: NSRange(location: 0, length: m.length)) }
        }
        replace(r, with: m, select: NSRange(location: r.location + m.length, length: 0))
        ensureTrailingLine()
    }

    /// Text pasted from other apps keeps bold, italic, underline, strikethrough and links, nothing else:
    /// no fonts, sizes or colours (pasting white text from a dark app gave invisible text).
    public func sanitize(_ a: NSAttributedString) -> NSAttributedString {
        let out = NSMutableAttributedString(string: a.string.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\u{FFFC}", with: ""))
        guard out.length == (a.string as NSString).length else {
            return NSAttributedString(string: out.string, attributes: cleanTyping(typingAttributes))
        }
        let base = cleanTyping(typingAttributes)
        let baseFont = (base[.font] as? NSFont) ?? NoteStyle.font()
        out.setAttributes(base, range: NSRange(location: 0, length: out.length))
        out.removeAttribute(.dlColor, range: NSRange(location: 0, length: out.length))
        out.removeAttribute(.dlHighlight, range: NSRange(location: 0, length: out.length))
        out.removeAttribute(.backgroundColor, range: NSRange(location: 0, length: out.length))
        out.addAttribute(.foregroundColor, value: style.ink, range: NSRange(location: 0, length: out.length))
        a.enumerateAttributes(in: NSRange(location: 0, length: a.length)) { attrs, r, _ in
            let fm = NSFontManager.shared
            var f = baseFont
            if let src = attrs[.font] as? NSFont {
                let t = fm.traits(of: src)
                if t.contains(.boldFontMask) { f = fm.convert(f, toHaveTrait: .boldFontMask) }
                if t.contains(.italicFontMask) { f = fm.convert(f, toHaveTrait: .italicFontMask) }
            }
            out.addAttribute(.font, value: f, range: r)
            if (attrs[.underlineStyle] as? Int ?? 0) != 0 && attrs[.link] == nil { out.addAttribute(.underlineStyle, value: NSUnderlineStyle.single.rawValue, range: r) }
            if (attrs[.strikethroughStyle] as? Int ?? 0) != 0 { out.addAttribute(.strikethroughStyle, value: NSUnderlineStyle.single.rawValue, range: r) }
            if let l = attrs[.link] { out.addAttribute(.link, value: (l as? URL) ?? URL(string: "\(l)") as Any, range: r) }
        }
        return out
    }

    // MARK: State

    public var formatState: FormatState {
        var s = FormatState()
        guard let ts = textStorage else { return s }
        let r = selectedRange()
        let p = paragraphs(in: r).first!
        let a: [NSAttributedString.Key: Any] = r.length > 0 && r.location < ts.length ? ts.attributes(at: r.location, effectiveRange: nil) : typingAttributes
        let traits = (a[.font] as? NSFont).map { NSFontManager.shared.traits(of: $0) } ?? []
        s.bold = traits.contains(.boldFontMask); s.italic = traits.contains(.italicFontMask)
        s.underline = (a[.underlineStyle] as? Int ?? 0) != 0; s.strike = (a[.strikethroughStyle] as? Int ?? 0) != 0
        s.code = a[.dlCode] != nil
        s.link = (a[.link] as? URL)?.absoluteString ?? a[.link] as? String
        s.color = a[.dlColor] != nil ? a[.foregroundColor] as? NSColor : nil
        s.highlight = a[.dlHighlight] != nil ? a[.backgroundColor] as? NSColor : nil
        let pa: [NSAttributedString.Key: Any] = (p.length > 0 && p.location < ts.length) ? ts.attributes(at: p.location, effectiveRange: nil) : typingAttributes
        s.heading = pa[.dlHeading] as? Int ?? 0
        s.block = pa[.dlBlock] as? String
        let ps = pa[.paragraphStyle] as? NSParagraphStyle
        s.list = ps?.textLists.last.map { NoteBlocks.isOrdered($0) ? "ol" : "ul" }
        s.alignment = ps?.alignment ?? .natural
        s.inTable = ps?.textBlocks.first is NSTextTableBlock
        s.checklist = NoteCodec.checkPrefix(ns.substring(with: contentRange(p))) > 0
        return s
    }
}
