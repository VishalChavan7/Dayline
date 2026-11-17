import SwiftUI
import AppKit
import DaylineCore

/// Converts between the phone's note HTML (<p>, <h1>, <h2>, <b>, <i>, <u>, <s>, <ul>/<ol>/<li>, <br>) and NSAttributedString.
enum NoteHTML {
    static let bodySize: CGFloat = 15
    static let h1Size: CGFloat = 24
    static let h2Size: CGFloat = 19

    static func attributed(from html: String) -> NSAttributedString {
        let base = NSFont.systemFont(ofSize: bodySize)
        guard NoteCodec.isHtml(html) else {
            return NSAttributedString(string: html, attributes: [.font: base, .foregroundColor: NSColor.textColor])
        }
        let styled = """
        <html><head><meta charset="utf-8"><style>
        body { font-family: -apple-system, 'Helvetica Neue'; font-size: \(Int(bodySize))px; }
        p, li { margin: 0; line-height: 1.45; } h1 { font-size: \(Int(h1Size))px; margin: 0; } h2 { font-size: \(Int(h2Size))px; margin: 0; }
        ul, ol { margin: 0; }
        </style></head><body>\(html)</body></html>
        """
        guard let data = styled.data(using: .utf8),
              let a = try? NSMutableAttributedString(data: data, options: [.documentType: NSAttributedString.DocumentType.html,
                                                                        .characterEncoding: String.Encoding.utf8.rawValue], documentAttributes: nil)
        else { return NSAttributedString(string: NoteCodec.plainText(html), attributes: [.font: base]) }
        // Follow the app's appearance: drop fixed colours from the HTML import.
        let all = NSRange(location: 0, length: a.length)
        a.removeAttribute(.foregroundColor, range: all)
        a.removeAttribute(.backgroundColor, range: all)
        a.addAttribute(.foregroundColor, value: NSColor.textColor, range: all)
        // Trim the trailing newline the importer adds.
        while a.length > 0, a.string.hasSuffix("\n") { a.deleteCharacters(in: NSRange(location: a.length - 1, length: 1)) }
        return a
    }

    static func html(from a: NSAttributedString) -> String {
        let ns = a.string as NSString
        var out = ""
        var openList: String?
        func closeList() { if let l = openList { out += "</\(l)>"; openList = nil } }
        ns.enumerateSubstrings(in: NSRange(location: 0, length: ns.length), options: [.byParagraphs, .substringNotRequired]) { _, range, _, _ in
            if range.length == 0 { closeList(); out += "<br>"; return }
            let style = a.attribute(.paragraphStyle, at: range.location, effectiveRange: nil) as? NSParagraphStyle
            let font = a.attribute(.font, at: range.location, effectiveRange: nil) as? NSFont
            let size = font?.pointSize ?? bodySize
            let list = style?.textLists.first
            let tag: String = list != nil ? "li" : size >= (h1Size - 1) ? "h1" : size >= (h2Size - 1) ? "h2" : "p"
            var inner = ""
            var first = true
            a.enumerateAttributes(in: range) { attrs, r, _ in
                var text = ns.substring(with: r)
                if first && list != nil {
                    // Drop the list marker the text system shows ("\t•\t", "\t1.\t").
                    text = text.replacingOccurrences(of: "^\\t?[^\\t]{0,4}\\t", with: "", options: .regularExpression)
                }
                first = false
                var piece = NoteCodec.escape(text).replacingOccurrences(of: "\u{2028}", with: "<br>")
                let f = attrs[.font] as? NSFont
                let traits = f?.fontDescriptor.symbolicTraits ?? []
                if traits.contains(.bold) && tag != "h1" && tag != "h2" { piece = "<b>\(piece)</b>" }
                if traits.contains(.italic) { piece = "<i>\(piece)</i>" }
                if let u = attrs[.underlineStyle] as? Int, u != 0 { piece = "<u>\(piece)</u>" }
                if let s = attrs[.strikethroughStyle] as? Int, s != 0 { piece = "<s>\(piece)</s>" }
                inner += piece
            }
            if let list {
                let kind = list.markerFormat == .decimal ? "ol" : "ul"
                if openList != kind { closeList(); out += "<\(kind)>"; openList = kind }
            } else { closeList() }
            out += "<\(tag)>\(inner)</\(tag)>"
        }
        closeList()
        return out
    }
}

/// Lets the formatting bar talk to the text view.
@MainActor
final class RichTextController: ObservableObject {
    weak var textView: NSTextView?

    func toggleTrait(_ trait: NSFontTraitMask) {
        guard let tv = textView, let storage = tv.textStorage else { return }
        let range = tv.selectedRange()
        let fm = NSFontManager.shared
        if range.length == 0 {
            let f = (tv.typingAttributes[.font] as? NSFont) ?? NSFont.systemFont(ofSize: NoteHTML.bodySize)
            let has = fm.traits(of: f).contains(trait)
            tv.typingAttributes[.font] = has ? fm.convert(f, toNotHaveTrait: trait) : fm.convert(f, toHaveTrait: trait)
            return
        }
        var allHave = true
        storage.enumerateAttribute(.font, in: range) { v, _, _ in if let f = v as? NSFont, !fm.traits(of: f).contains(trait) { allHave = false } }
        storage.beginEditing()
        storage.enumerateAttribute(.font, in: range) { v, r, _ in
            let f = (v as? NSFont) ?? NSFont.systemFont(ofSize: NoteHTML.bodySize)
            storage.addAttribute(.font, value: allHave ? fm.convert(f, toNotHaveTrait: trait) : fm.convert(f, toHaveTrait: trait), range: r)
        }
        storage.endEditing()
        tv.didChangeText()
    }

    func toggleLine(_ key: NSAttributedString.Key) {
        guard let tv = textView, let storage = tv.textStorage else { return }
        let range = tv.selectedRange()
        guard range.length > 0 else {
            let on = (tv.typingAttributes[key] as? Int ?? 0) != 0
            tv.typingAttributes[key] = on ? 0 : NSUnderlineStyle.single.rawValue
            return
        }
        var allOn = true
        storage.enumerateAttribute(key, in: range) { v, _, _ in if (v as? Int ?? 0) == 0 { allOn = false } }
        storage.addAttribute(key, value: allOn ? 0 : NSUnderlineStyle.single.rawValue, range: range)
        tv.didChangeText()
    }

    /// Body, heading 1 or heading 2 for the selected paragraphs.
    func setSize(_ size: CGFloat) {
        guard let tv = textView, let storage = tv.textStorage else { return }
        let ns = storage.string as NSString
        let range = ns.paragraphRange(for: tv.selectedRange())
        storage.beginEditing()
        storage.enumerateAttribute(.font, in: range) { v, r, _ in
            let f = (v as? NSFont) ?? NSFont.systemFont(ofSize: NoteHTML.bodySize)
            var nf = NSFontManager.shared.convert(f, toSize: size)
            nf = size > NoteHTML.bodySize ? NSFontManager.shared.convert(nf, toHaveTrait: .boldFontMask) : NSFontManager.shared.convert(nf, toNotHaveTrait: .boldFontMask)
            storage.addAttribute(.font, value: nf, range: r)
        }
        storage.endEditing()
        tv.typingAttributes[.font] = NSFont.systemFont(ofSize: size, weight: size > NoteHTML.bodySize ? .bold : .regular)
        tv.didChangeText()
    }

    /// Turns the selected paragraphs into checklist lines (or back), like the phone.
    func toggleChecklist() {
        guard let tv = textView, let storage = tv.textStorage else { return }
        let ns = storage.string as NSString
        let full = ns.paragraphRange(for: tv.selectedRange())
        var paras: [NSRange] = []
        ns.enumerateSubstrings(in: full, options: [.byParagraphs, .substringNotRequired]) { _, r, _, _ in paras.append(r) }
        if paras.isEmpty { paras = [NSRange(location: full.location, length: 0)] }
        let all = paras.allSatisfy { NoteCodec.checkPrefix(ns.substring(with: $0)) > 0 }
        storage.beginEditing()
        for r in paras.reversed() {
            let text = ns.substring(with: r)
            let p = NoteCodec.checkPrefix(text)
            if all && p > 0 {
                let len = (String(text.unicodeScalars.prefix(p)) as NSString).length
                storage.deleteCharacters(in: NSRange(location: r.location, length: len))
            } else if !all && p == 0 {
                let attrs = r.location < storage.length ? storage.attributes(at: r.location, effectiveRange: nil) : tv.typingAttributes
                storage.insert(NSAttributedString(string: NoteCodec.checkOpen, attributes: attrs), at: r.location)
            }
        }
        storage.endEditing()
        tv.didChangeText()
    }

    func toggleBullets() {
        guard let tv = textView else { return }
        tv.window?.makeFirstResponder(tv)
        // Uses AppKit's own list support (exported as <ul><li>).
        let ns = (tv.string as NSString)
        let range = ns.paragraphRange(for: tv.selectedRange())
        let style = tv.textStorage?.attribute(.paragraphStyle, at: min(range.location, max(0, (tv.textStorage?.length ?? 1) - 1)), effectiveRange: nil) as? NSParagraphStyle
        let hasList = !(style?.textLists.isEmpty ?? true)
        guard let storage = tv.textStorage else { return }
        storage.beginEditing()
        var paras: [NSRange] = []
        ns.enumerateSubstrings(in: range, options: [.byParagraphs, .substringNotRequired]) { _, r, _, _ in paras.append(r) }
        for r in paras.reversed() {
            let text = ns.substring(with: r)
            let ps = (style?.mutableCopy() as? NSMutableParagraphStyle) ?? NSMutableParagraphStyle()
            if hasList {
                ps.textLists = []
                let stripped = text.replacingOccurrences(of: "^\\t?[^\\t]{0,4}\\t", with: "", options: .regularExpression)
                let attrs = r.length > 0 ? storage.attributes(at: r.location, effectiveRange: nil) : tv.typingAttributes
                storage.replaceCharacters(in: r, with: NSAttributedString(string: stripped, attributes: attrs))
                storage.addAttribute(.paragraphStyle, value: ps, range: NSRange(location: r.location, length: (stripped as NSString).length))
            } else {
                let list = NSTextList(markerFormat: .disc, options: 0)
                ps.textLists = [list]
                ps.headIndent = 18; ps.firstLineHeadIndent = 0
                let attrs = r.length > 0 ? storage.attributes(at: r.location, effectiveRange: nil) : tv.typingAttributes
                let marked = "\t\(list.marker(forItemNumber: 1))\t" + text
                storage.replaceCharacters(in: r, with: NSAttributedString(string: marked, attributes: attrs))
                storage.addAttribute(.paragraphStyle, value: ps, range: NSRange(location: r.location, length: (marked as NSString).length))
            }
        }
        storage.endEditing()
        tv.didChangeText()
    }
}

/// Clicking a ☐/☑ box at the start of a line ticks it.
final class NoteTextView: NSTextView {
    override func mouseDown(with event: NSEvent) {
        let p = convert(event.locationInWindow, from: nil)
        let i = characterIndexForInsertion(at: p)
        let ns = string as NSString
        for idx in [i, i - 1] where idx >= 0 && idx < ns.length {
            let ch = ns.character(at: idx)
            if ch == 0x2610 || ch == 0x2611 {
                let para = ns.paragraphRange(for: NSRange(location: idx, length: 0))
                if para.location == idx, let storage = textStorage {
                    let text = ns.substring(with: para)
                    let p = NoteCodec.checkPrefix(text)
                    let len = (String(text.unicodeScalars.prefix(p)) as NSString).length
                    let attrs = storage.attributes(at: idx, effectiveRange: nil)
                    let replacement = ch == 0x2610 ? NoteCodec.checkDone : NoteCodec.checkOpen
                    storage.replaceCharacters(in: NSRange(location: idx, length: len), with: NSAttributedString(string: replacement, attributes: attrs))
                    didChangeText()
                    return
                }
            }
        }
        super.mouseDown(with: event)
    }
}

struct RichTextEditor: NSViewRepresentable {
    @Binding var html: String
    @ObservedObject var controller: RichTextController

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeNSView(context: Context) -> NSScrollView {
        let scroll = NSScrollView()
        let tv = NoteTextView(frame: .zero)
        tv.minSize = .zero
        tv.maxSize = NSSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude)
        tv.isVerticallyResizable = true
        tv.isHorizontallyResizable = false
        tv.autoresizingMask = [.width]
        tv.textContainer?.widthTracksTextView = true
        tv.textContainer?.containerSize = NSSize(width: 0, height: CGFloat.greatestFiniteMagnitude)
        scroll.documentView = tv
        tv.isRichText = true
        tv.allowsUndo = true
        tv.drawsBackground = false
        tv.textContainerInset = NSSize(width: 0, height: 6)
        tv.textContainer?.lineFragmentPadding = 0
        tv.font = NSFont.systemFont(ofSize: NoteHTML.bodySize)
        let para = NSMutableParagraphStyle(); para.lineSpacing = 4; para.paragraphSpacing = 4
        tv.typingAttributes = [.font: NSFont.systemFont(ofSize: NoteHTML.bodySize), .foregroundColor: NSColor.textColor, .paragraphStyle: para]
        tv.defaultParagraphStyle = para
        tv.isAutomaticQuoteSubstitutionEnabled = false
        tv.textStorage?.setAttributedString(NoteHTML.attributed(from: html))
        tv.delegate = context.coordinator
        scroll.drawsBackground = false
        scroll.hasVerticalScroller = true
        context.coordinator.lastHTML = html
        controller.textView = tv
        return scroll
    }

    func updateNSView(_ scroll: NSScrollView, context: Context) {
        guard let tv = scroll.documentView as? NSTextView else { return }
        controller.textView = tv
        if html != context.coordinator.lastHTML {
            context.coordinator.lastHTML = html
            tv.textStorage?.setAttributedString(NoteHTML.attributed(from: html))
        }
    }

    final class Coordinator: NSObject, NSTextViewDelegate {
        var parent: RichTextEditor
        var lastHTML = ""
        init(_ p: RichTextEditor) { parent = p }
        func textDidChange(_ n: Notification) {
            guard let tv = n.object as? NSTextView, let s = tv.textStorage else { return }
            let h = NoteHTML.html(from: s)
            lastHTML = h
            parent.html = h
        }
    }
}

struct FormatBar: View {
    @ObservedObject var controller: RichTextController
    var body: some View {
        HStack(spacing: 2) {
            Group {
                Button { controller.toggleTrait(.boldFontMask) } label: { Image(systemName: "bold") }.help("Bold (⌘B)").keyboardShortcut("b")
                Button { controller.toggleTrait(.italicFontMask) } label: { Image(systemName: "italic") }.help("Italic (⌘I)").keyboardShortcut("i")
                Button { controller.toggleLine(.underlineStyle) } label: { Image(systemName: "underline") }.help("Underline (⌘U)").keyboardShortcut("u")
                Button { controller.toggleLine(.strikethroughStyle) } label: { Image(systemName: "strikethrough") }.help("Strikethrough")
            }
            Divider().frame(height: 16).padding(.horizontal, 4)
            Group {
                Button { controller.setSize(NoteHTML.h1Size) } label: { Text("H1").fontWeight(.bold) }.help("Heading")
                Button { controller.setSize(NoteHTML.h2Size) } label: { Text("H2").fontWeight(.semibold) }.help("Subheading")
                Button { controller.setSize(NoteHTML.bodySize) } label: { Text("Aa") }.help("Body text")
            }
            Divider().frame(height: 16).padding(.horizontal, 4)
            Button { controller.toggleBullets() } label: { Image(systemName: "list.bullet") }.help("Bulleted list")
            Button { controller.toggleChecklist() } label: { Image(systemName: "checklist") }.help("Checklist lines — click a box to tick it")
        }
        .buttonStyle(.borderless)
        .font(.system(size: 13))
    }
}
