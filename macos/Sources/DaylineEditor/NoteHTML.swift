import AppKit
import DaylineCore

public extension NSAttributedString.Key {
    /// Block a paragraph belongs to: "quote", "callout:<kind>", "code:<language>", "table", "hr".
    static let dlBlock = NSAttributedString.Key("dlBlock")
    /// Heading level (1–6) of a paragraph.
    static let dlHeading = NSAttributedString.Key("dlHeading")
    /// The text has a colour the person picked (otherwise it uses the theme's ink).
    static let dlColor = NSAttributedString.Key("dlColor")
    /// The background is a highlighter colour.
    static let dlHighlight = NSAttributedString.Key("dlHighlight")
    /// Inline code.
    static let dlCode = NSAttributedString.Key("dlCode")
    /// A table header cell.
    static let dlHeader = NSAttributedString.Key("dlHeader")
}

/// Colours and sizes of the note editor for one appearance. Text never relies on a system colour that could
/// resolve against the wrong appearance: ink is fixed per theme.
public struct NoteStyle: Equatable {
    public var dark: Bool
    public init(dark: Bool) { self.dark = dark }

    public static let bodySize: CGFloat = 15
    /// Sizes for headings 1–6.
    public static let headingSizes: [CGFloat] = [26, 21, 18, 16, 15, 13]
    public static let codeSize: CGFloat = 13.5

    public var ink: NSColor { dark ? NSColor(srgbRed: 0.95, green: 0.95, blue: 0.97, alpha: 1) : NSColor(srgbRed: 0.114, green: 0.114, blue: 0.122, alpha: 1) }
    public var secondaryInk: NSColor { dark ? NSColor(white: 0.72, alpha: 1) : NSColor(white: 0.38, alpha: 1) }
    /// Ink on highlighted text, readable on every highlighter colour in both themes (as on the phone).
    public static let highlightInk = NSColor(srgbRed: 31 / 255, green: 31 / 255, blue: 31 / 255, alpha: 1)
    public var codeBackground: NSColor { dark ? NSColor(white: 1, alpha: 0.08) : NSColor(white: 0, alpha: 0.05) }
    public var rule: NSColor { dark ? NSColor(white: 1, alpha: 0.18) : NSColor(white: 0, alpha: 0.15) }
    public var quoteBar: NSColor { dark ? NSColor(white: 1, alpha: 0.3) : NSColor(white: 0, alpha: 0.22) }
    public var tableHeader: NSColor { dark ? NSColor(white: 1, alpha: 0.07) : NSColor(white: 0, alpha: 0.04) }

    /// Accent and background of a callout kind.
    public func callout(_ kind: String) -> (accent: NSColor, fill: NSColor) {
        let accent: NSColor
        switch kind {
        case "tip": accent = NSColor(srgbRed: 0.16, green: 0.62, blue: 0.33, alpha: 1)
        case "important": accent = NSColor(srgbRed: 0.53, green: 0.33, blue: 0.86, alpha: 1)
        case "warning": accent = NSColor(srgbRed: 0.85, green: 0.55, blue: 0.0, alpha: 1)
        case "caution": accent = NSColor(srgbRed: 0.85, green: 0.22, blue: 0.2, alpha: 1)
        default: accent = NSColor(srgbRed: 0.1, green: 0.45, blue: 0.91, alpha: 1)
        }
        return (accent, accent.withAlphaComponent(dark ? 0.18 : 0.1))
    }

    public static func font(size: CGFloat = bodySize, bold: Bool = false, italic: Bool = false, weight: NSFont.Weight? = nil) -> NSFont {
        var f = NSFont.systemFont(ofSize: size, weight: weight ?? (bold ? .bold : .regular))
        if italic { f = NSFontManager.shared.convert(f, toHaveTrait: .italicFontMask) }
        return f
    }
    public static var codeFont: NSFont { NSFont.monospacedSystemFont(ofSize: codeSize, weight: .regular) }

    public static func headingFont(_ level: Int, italic: Bool = false) -> NSFont {
        let size = headingSizes[max(1, min(6, level)) - 1]
        return font(size: size, italic: italic, weight: level == 3 ? .semibold : .bold)
    }

    public static func baseParagraph() -> NSMutableParagraphStyle {
        let p = NSMutableParagraphStyle(); p.lineSpacing = 4; p.paragraphSpacing = 4
        return p
    }

    public var bodyAttributes: [NSAttributedString.Key: Any] {
        [.font: NoteStyle.font(), .foregroundColor: ink, .paragraphStyle: NoteStyle.baseParagraph()]
    }

    /// The palettes match the phone's.
    public static let textColors: [NSColor] = [0xD93025, 0xE8710A, 0xF9AB00, 0x188038, 0x1A73E8, 0x9334E6, 0x795548, 0x5F6368].map(NSColor.init(rgb:))
    public static let highlightColors: [NSColor] = [0xFFF176, 0xA5D6A7, 0x90CAF9, 0xF48FB1, 0xFFCC80].map(NSColor.init(rgb:))
}

public extension NSColor {
    convenience init(rgb: Int) {
        self.init(srgbRed: CGFloat((rgb >> 16) & 0xFF) / 255, green: CGFloat((rgb >> 8) & 0xFF) / 255, blue: CGFloat(rgb & 0xFF) / 255, alpha: 1)
    }
    /// "rgba(217, 48, 37, 1.0)", the form the phone writes.
    var cssRGBA: String {
        let c = usingColorSpace(.sRGB) ?? self
        func v(_ x: CGFloat) -> Int { Int((max(0, min(1, x)) * 255).rounded()) }
        return "rgba(\(v(c.redComponent)), \(v(c.greenComponent)), \(v(c.blueComponent)), \(String(format: "%.1f", c.alphaComponent)))"
    }
    /// Parses #rgb, #rrggbb, rgb(…), rgba(…) and a few names.
    static func css(_ s0: String) -> NSColor? {
        let s = s0.trimmingCharacters(in: .whitespaces).lowercased()
        if s.hasPrefix("#") {
            var hex = String(s.dropFirst())
            if hex.count == 3 { hex = hex.map { "\($0)\($0)" }.joined() }
            guard hex.count == 6 || hex.count == 8, let v = Int(hex.prefix(6), radix: 16) else { return nil }
            return NSColor(rgb: v)
        }
        if s.hasPrefix("rgb") {
            let nums = s.drop { $0 != "(" }.dropFirst().prefix { $0 != ")" }.split(separator: ",").map { Double($0.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "%", with: "")) }
            guard nums.count >= 3, let r = nums[0], let g = nums[1], let b = nums[2] else { return nil }
            let a = nums.count > 3 ? (nums[3] ?? 1) : 1
            return NSColor(srgbRed: r / 255, green: g / 255, blue: b / 255, alpha: a)
        }
        let names: [String: Int] = ["red": 0xFF0000, "green": 0x008000, "blue": 0x0000FF, "black": 0, "white": 0xFFFFFF, "yellow": 0xFFFF00, "orange": 0xFFA500, "purple": 0x800080, "gray": 0x808080, "grey": 0x808080]
        return names[s].map(NSColor.init(rgb:))
    }
    func isClose(to o: NSColor, tolerance: CGFloat = 0.02) -> Bool {
        guard let a = usingColorSpace(.sRGB), let b = o.usingColorSpace(.sRGB) else { return false }
        return abs(a.redComponent - b.redComponent) < tolerance && abs(a.greenComponent - b.greenComponent) < tolerance && abs(a.blueComponent - b.blueComponent) < tolerance
    }
}

/// Converts between a note's HTML (the format both apps store) and the editor's attributed text.
public enum NoteHTML {

    // MARK: HTML → text

    public static func attributed(from html: String, style: NoteStyle) -> NSAttributedString {
        var paras: [Para] = []
        if !NoteCodec.isHtml(html) {
            for line in html.components(separatedBy: "\n") { paras.append(Para(text: NSMutableAttributedString(string: line))) }
        } else {
            for b in NoteFormat.split(html) { paras += paragraphs(of: b, style: style) }
        }
        // Something to type into after a block that ends the note.
        if let last = paras.last, last.block != nil {
            // A plain line after a block that ends the note (and the end of the text after it), to type into.
            paras.append(Para(text: NSMutableAttributedString())); paras.append(Para(text: NSMutableAttributedString()))
        }
        let out = assemble(paras, style: style)
        applyTheme(out, style: style)
        return out
    }

    /// One paragraph while building: its text and what kind of paragraph it is.
    struct Para {
        var text: NSMutableAttributedString
        var heading = 0
        var listLevels: [Bool] = []   // ordered? for each nesting level
        var number = 1
        var align: NSTextAlignment = .natural
        var block: String?
        var textBlock: NSTextBlock?
        /// Attributes for the whole line, newline included (so an empty table header cell still types bold).
        var lineAttrs: [NSAttributedString.Key: Any] = [:]
    }

    static func paragraphs(of b: NoteFormat.Block, style: NoteStyle) -> [Para] {
        switch b {
        case .rich(let h): return parseRich(h)
        case .quote(let h):
            let tb = NoteBlocks.quote(style: style)
            return parseRich(h).ifEmpty([Para(text: NSMutableAttributedString())]).map { var p = $0; p.block = "quote"; p.textBlock = tb; return p }
        case .callout(let kind, let h):
            let tb = NoteBlocks.callout(kind, style: style)
            return parseRich(h).ifEmpty([Para(text: NSMutableAttributedString())]).map { var p = $0; p.block = "callout:" + kind; p.textBlock = tb; return p }
        case .code(let text, let lang):
            let tb = NoteBlocks.code(style: style)
            return text.components(separatedBy: "\n").map { line in
                var p = Para(text: NSMutableAttributedString(string: line, attributes: [.font: NoteStyle.codeFont]))
                p.block = "code:" + lang; p.textBlock = tb; return p
            }
        case .divider:
            var p = Para(text: NSMutableAttributedString(attributedString: NoteBlocks.dividerString()))
            p.block = "hr"; return [p]
        case .table(let rows, let header):
            return NoteBlocks.tableParagraphs(rows: rows, header: header, style: style)
        }
    }

    static func assemble(_ paras: [Para], style: NoteStyle) -> NSMutableAttributedString {
        let out = NSMutableAttributedString()
        for (i, p) in paras.enumerated() {
            let start = out.length
            let content = NSMutableAttributedString(attributedString: p.text)
            if !p.listLevels.isEmpty {
                let marker = NoteBlocks.marker(ordered: p.listLevels.last!, number: p.number, depth: p.listLevels.count)
                content.insert(NSAttributedString(string: "\t\(marker)\t"), at: 0)
            }
            out.append(content)
            if i < paras.count - 1 { out.append(NSAttributedString(string: "\n")) }
            let range = NSRange(location: start, length: out.length - start)
            // Base font where the parser set none.
            out.enumerateAttribute(.font, in: range) { v, r, _ in if v == nil { out.addAttribute(.font, value: NoteStyle.font(), range: r) } }
            if p.heading > 0 {
                out.enumerateAttribute(.font, in: range) { v, r, _ in
                    let italic = (v as? NSFont).map { NSFontManager.shared.traits(of: $0).contains(.italicFontMask) } ?? false
                    out.addAttribute(.font, value: NoteStyle.headingFont(p.heading, italic: italic), range: r)
                }
                out.addAttribute(.dlHeading, value: p.heading, range: range)
            }
            let ps = NoteStyle.baseParagraph()
            ps.alignment = p.align
            if !p.listLevels.isEmpty { NoteBlocks.applyList(ps, levels: p.listLevels) }
            if let tb = p.textBlock { ps.textBlocks = [tb] }
            if p.block?.hasPrefix("code:") == true { ps.lineSpacing = 2; ps.paragraphSpacing = 0 }
            out.addAttribute(.paragraphStyle, value: ps, range: range)
            if let b = p.block { out.addAttribute(.dlBlock, value: b, range: range) }
            if !p.lineAttrs.isEmpty, range.length > 0 { out.addAttributes(p.lineAttrs, range: range) }
        }
        return out
    }

    // A small HTML reader for the subset the apps write (and what other apps paste).
    static let token = try! NSRegularExpression(pattern: "<!--[\\s\\S]*?-->|<(/?)([a-zA-Z][a-zA-Z0-9]*)((?:\\s+[^>]*?)?)\\s*(/?)>|([^<]+)|<", options: [])
    static let attrRe = try! NSRegularExpression(pattern: "([a-zA-Z-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", options: [])

    static func attributes(_ s: String) -> [String: String] {
        var out: [String: String] = [:]
        let ns = s as NSString
        for m in attrRe.matches(in: s, range: NSRange(location: 0, length: ns.length)) {
            let key = ns.substring(with: m.range(at: 1)).lowercased()
            let v = [3, 4, 5].lazy.map { m.range(at: $0) }.first { $0.location != NSNotFound }.map { ns.substring(with: $0) } ?? ""
            out[key] = NoteCodec.decodeEntities(v)
        }
        return out
    }

    static func css(_ style: String?) -> [String: String] {
        var out: [String: String] = [:]
        for part in (style ?? "").split(separator: ";") {
            let kv = part.split(separator: ":", maxSplits: 1)
            if kv.count == 2 { out[kv[0].trimmingCharacters(in: .whitespaces).lowercased()] = kv[1].trimmingCharacters(in: .whitespaces) }
        }
        return out
    }

    /// Inline formatting in effect at a point of the HTML.
    struct Inline {
        var bold = false, italic = false, underline = false, strike = false, code = false
        var link: String?
        var color: NSColor?
        var background: NSColor?
        var size: (Double, String)?
    }

    static func parseRich(_ html: String) -> [Para] {
        var paras: [Para] = []
        var cur: Para?
        var stack: [(tag: String, style: Inline)] = []
        var lists: [(ordered: Bool, count: Int)] = []
        var inline: Inline { stack.last?.style ?? Inline() }

        func flush() { if let c = cur { paras.append(c); cur = nil } }
        func start(_ align: NSTextAlignment = .natural, heading: Int = 0) {
            flush()
            var p = Para(text: NSMutableAttributedString())
            p.align = align; p.heading = heading
            cur = p
        }
        func append(_ text: String) {
            if cur == nil { start() }
            let s = inline
            var a: [NSAttributedString.Key: Any] = [:]
            var font = s.code ? NoteStyle.codeFont : NoteStyle.font(bold: s.bold, italic: s.italic)
            if s.code && (s.bold || s.italic) {
                font = NSFontManager.shared.convert(font, toHaveTrait: [s.bold ? .boldFontMask : [], s.italic ? .italicFontMask : []])
            }
            a[.font] = font
            if s.underline { a[.underlineStyle] = NSUnderlineStyle.single.rawValue }
            if s.strike { a[.strikethroughStyle] = NSUnderlineStyle.single.rawValue }
            if s.code { a[.dlCode] = true }
            if let l = s.link, let u = URL(string: l) { a[.link] = u }
            if let bg = s.background {
                a[.backgroundColor] = bg; a[.dlHighlight] = true
            }
            if let c = s.color, !(s.background != nil && c.isClose(to: NoteStyle.highlightInk, tolerance: 0.03)) {
                a[.foregroundColor] = c; a[.dlColor] = true
            }
            if let sz = s.size { a[.dlSizeHint] = "\(sz.0)\(sz.1)" }
            cur!.text.append(NSAttributedString(string: text, attributes: a))
        }
        func alignment(_ attrs: [String: String]) -> NSTextAlignment {
            switch (css(attrs["style"])["text-align"] ?? attrs["align"] ?? "").lowercased() {
            case "center": return .center
            case "right", "end": return .right
            case "justify": return .justified
            default: return .natural
            }
        }

        let ns = html as NSString
        for m in token.matches(in: html, range: NSRange(location: 0, length: ns.length)) {
            if m.range(at: 5).location != NSNotFound {
                var t = NoteCodec.decodeEntities(ns.substring(with: m.range(at: 5)))
                if t.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && (t.contains("\n") || cur == nil) { continue }
                t = t.replacingOccurrences(of: "\r", with: "").replacingOccurrences(of: "\n", with: " ")
                append(t)
                continue
            }
            guard m.range(at: 2).location != NSNotFound else {
                if ns.substring(with: m.range).hasPrefix("<!--") { continue }
                append(ns.substring(with: m.range)); continue
            }
            let closing = ns.substring(with: m.range(at: 1)) == "/"
            let tag = ns.substring(with: m.range(at: 2)).lowercased()
            let attrs = closing ? [:] : attributes(ns.substring(with: m.range(at: 3)))
            switch tag {
            case "p", "div", "h1", "h2", "h3", "h4", "h5", "h6":
                // Inline styles never run on past the end of a paragraph (an unclosed <b> stays in its line).
                if closing { flush(); stack.removeAll() } else {
                    let level = tag.hasPrefix("h") ? Int(String(tag.last!))! : 0
                    start(alignment(attrs), heading: level)
                }
            case "br":
                if cur != nil { append("\u{2028}") } else { paras.append(Para(text: NSMutableAttributedString())) }
            case "ul", "ol":
                if closing { flush(); if !lists.isEmpty { lists.removeLast() } } else { flush(); lists.append((tag == "ol", 0)) }
            case "li":
                if closing { flush(); stack.removeAll() } else {
                    if lists.isEmpty { lists.append((false, 0)) }
                    lists[lists.count - 1].count += 1
                    start(alignment(attrs))
                    cur!.listLevels = lists.map(\.ordered)
                    cur!.number = lists.last!.count
                }
            case "b", "strong", "i", "em", "u", "ins", "s", "del", "strike", "code", "mark", "a", "span", "font", "sub", "sup", "small", "big":
                if closing {
                    if let i = stack.lastIndex(where: { $0.tag == tag }) { stack.removeSubrange(i...) }
                    continue
                }
                var s = inline
                switch tag {
                case "b", "strong": s.bold = true
                case "i", "em": s.italic = true
                case "u", "ins": s.underline = true
                case "s", "del", "strike": s.strike = true
                case "code": s.code = true
                case "mark": s.background = NoteStyle.highlightColors[0]
                case "a": s.link = attrs["href"]
                case "font": if let c = attrs["color"].flatMap(NSColor.css) { s.color = c }
                default: break
                }
                let c = css(attrs["style"])
                if let v = c["color"], let col = NSColor.css(v) { s.color = col }
                if let v = c["background"] ?? c["background-color"], let col = NSColor.css(v), col.alphaComponent > 0.01 { s.background = col }
                if let w = c["font-weight"], w == "bold" || (Int(w) ?? 400) >= 600 { s.bold = true }
                if c["font-style"] == "italic" { s.italic = true }
                if let d = c["text-decoration"] ?? c["text-decoration-line"] {
                    if d.contains("underline") { s.underline = true }
                    if d.contains("line-through") { s.strike = true }
                }
                if let fs = c["font-size"], let m2 = fs.range(of: "^[0-9.]+(px|em)$", options: .regularExpression) {
                    let str = String(fs[m2])
                    let unit = str.hasSuffix("em") ? "em" : "px"
                    if let v = Double(str.dropLast(2)) { s.size = (v, unit) }
                }
                stack.append((tag, s))
            default:
                continue   // img, table pieces outside a table, and anything unknown: keep the text only
            }
        }
        flush()
        // A paragraph written as one sized span is a heading (that's how the phone writes headings).
        for i in paras.indices where paras[i].heading == 0 && paras[i].listLevels.isEmpty {
            if let level = spanHeading(paras[i].text) { paras[i].heading = level }
        }
        for i in paras.indices { paras[i].text.removeAttribute(.dlSizeHint, range: NSRange(location: 0, length: paras[i].text.length)) }
        return paras
    }

    static func spanHeading(_ t: NSAttributedString) -> Int? {
        // Every run with text must be the same heading size; a line of only spaces goes by its spaces.
        let hasText = !t.string.trimmingCharacters(in: .whitespaces).isEmpty
        var level: Int?
        var ok = true
        t.enumerateAttribute(.dlSizeHint, in: NSRange(location: 0, length: t.length)) { v, r, stop in
            let text = (t.string as NSString).substring(with: r)
            if hasText && text.trimmingCharacters(in: .whitespaces).isEmpty { return }
            guard let hint = v as? String, let num = Double(hint.dropLast(2)) else { ok = false; stop.pointee = true; return }
            let l = NoteFormat.headingLevel(fontSize: num, unit: String(hint.suffix(2)))
            if l == 0 || (level != nil && level != l) { ok = false; stop.pointee = true; return }
            level = l
        }
        return ok ? level : nil
    }

    /// Fixes the colours for an appearance: theme ink on plain text, dark ink on highlights, code shading.
    public static func applyTheme(_ s: NSMutableAttributedString, style: NoteStyle, range: NSRange? = nil) {
        let all = range ?? NSRange(location: 0, length: s.length)
        guard all.length > 0 else { return }
        s.enumerateAttributes(in: all) { a, r, _ in
            if a[.dlHighlight] != nil { s.addAttribute(.foregroundColor, value: NoteStyle.highlightInk, range: r) }
            else if a[.dlColor] == nil { s.addAttribute(.foregroundColor, value: style.ink, range: r) }
            if a[.dlCode] != nil && a[.dlHighlight] == nil { s.addAttribute(.backgroundColor, value: style.codeBackground, range: r) }
        }
        NoteBlocks.restyleBlocks(s, style: style, range: all)
    }

    // MARK: text → HTML

    public static func html(from a: NSAttributedString) -> String {
        let ns = a.string as NSString
        var paras: [NSRange] = []
        ns.enumerateSubstrings(in: NSRange(location: 0, length: ns.length), options: [.byParagraphs, .substringNotRequired]) { _, r, _, _ in paras.append(r) }
        if ns.length > 0 && ns.hasSuffix("\n") { paras.append(NSRange(location: ns.length, length: 0)) }
        // Drop empty paragraphs at the end (the editor keeps one after a block to type into).
        while let last = paras.last, blockKey(a, last) == nil, ns.substring(with: last).trimmingCharacters(in: .whitespaces).isEmpty {
            paras.removeLast()
        }
        var out = ""
        var i = 0
        while i < paras.count {
            let key = blockKey(a, paras[i])
            var j = i + 1
            while j < paras.count, sameBlock(a, paras[i], paras[j], key) { j += 1 }
            let group = Array(paras[i..<j])
            switch key?.kind {
            case nil: out += richHTML(a, group)
            case "quote": out += "<blockquote>" + richHTML(a, group) + "</blockquote>"
            case "callout": out += NoteFormat.blockHtml(.callout(kind: key!.arg, html: richHTML(a, group)))
            case "code":
                let text = group.map { ns.substring(with: $0) }.joined(separator: "\n")
                out += NoteFormat.blockHtml(.code(text: text, language: key!.arg))
            case "hr": out += String(repeating: "<hr>", count: group.count)
            case "table":
                let (rows, header) = NoteBlocks.tableRows(a, group)
                out += NoteFormat.blockHtml(.table(rows: rows, header: header))
            default: out += richHTML(a, group)
            }
            i = j
        }
        return out
    }

    struct BlockKey: Equatable { let kind: String; let arg: String }

    static func blockAttr(_ a: NSAttributedString, _ r: NSRange) -> (String?, NSParagraphStyle?) {
        guard a.length > 0 else { return (nil, nil) }
        let at = min(r.location, a.length - 1)
        // An empty last paragraph has no characters of its own: it takes its kind from typing, so treat it as plain.
        if r.length == 0 && r.location >= a.length { return (nil, nil) }
        return (a.attribute(.dlBlock, at: at, effectiveRange: nil) as? String, a.attribute(.paragraphStyle, at: at, effectiveRange: nil) as? NSParagraphStyle)
    }

    static func blockKey(_ a: NSAttributedString, _ r: NSRange) -> BlockKey? {
        guard let b = blockAttr(a, r).0 else { return nil }
        let parts = b.split(separator: ":", maxSplits: 1).map(String.init)
        return BlockKey(kind: parts[0], arg: parts.count > 1 ? parts[1] : "")
    }

    static func sameBlock(_ a: NSAttributedString, _ r1: NSRange, _ r2: NSRange, _ key: BlockKey?) -> Bool {
        let k2 = blockKey(a, r2)
        guard k2 == key else { return false }
        guard let key, key.kind != "hr" else { return true }
        // Two quotes in a row stay two quotes: each has its own text block.
        let b1 = blockAttr(a, r1).1?.textBlocks.first, b2 = blockAttr(a, r2).1?.textBlocks.first
        if key.kind == "table" { return (b1 as? NSTextTableBlock)?.table === (b2 as? NSTextTableBlock)?.table }
        return b1 === b2
    }

    static func richHTML(_ a: NSAttributedString, _ paras: [NSRange]) -> String {
        let ns = a.string as NSString
        var out = ""
        var lists: [(kind: String, liOpen: Bool)] = []
        func closeLists(to depth: Int) {
            while lists.count > depth {
                if lists.last!.liOpen { out += "</li>" }
                out += "</\(lists.removeLast().kind)>"
            }
        }
        for r in paras {
            let ps = r.length > 0 || r.location < a.length ? a.attribute(.paragraphStyle, at: min(r.location, max(0, a.length - 1)), effectiveRange: nil) as? NSParagraphStyle : nil
            let textLists = ps?.textLists ?? []
            let align = ps.map { alignCSS($0.alignment) } ?? ""
            if r.length == 0 || (textLists.isEmpty && ns.substring(with: r).isEmpty) {
                closeLists(to: 0); out += "<br>"; continue
            }
            if !textLists.isEmpty {
                let depth = textLists.count
                let kind = NoteBlocks.isOrdered(textLists.last!) ? "ol" : "ul"
                closeLists(to: depth)
                if lists.count == depth && lists.last!.kind != kind { closeLists(to: depth - 1) }
                while lists.count < depth {
                    if let last = lists.last, !last.liOpen { out += "<li>"; lists[lists.count - 1].liOpen = true }
                    let k = lists.count == depth - 1 ? kind : (NoteBlocks.isOrdered(textLists[lists.count]) ? "ol" : "ul")
                    out += "<\(k)>"; lists.append((k, false))
                }
                if lists.last!.liOpen { out += "</li>" }
                out += "<li\(align)>" + inlineHTML(a, r, stripMarker: true, heading: 0)
                lists[lists.count - 1].liOpen = true
                continue
            }
            closeLists(to: 0)
            let level = a.attribute(.dlHeading, at: r.location, effectiveRange: nil) as? Int ?? 0
            let inner = inlineHTML(a, r, stripMarker: false, heading: level)
            if level > 0 {
                let px = String(format: "%.1f", NoteFormat.headingPx[level - 1])
                out += level == 3
                    ? "<p\(align)><span style=\"font-size: \(px)px; font-weight: 600;\">\(inner)</span></p>"
                    : "<p\(align)><span style=\"font-size: \(px)px;\"><b>\(inner)</b></span></p>"
            } else {
                out += "<p\(align)>\(inner)</p>"
            }
        }
        closeLists(to: 0)
        return out
    }

    static func alignCSS(_ a: NSTextAlignment) -> String {
        switch a {
        case .center: return " style=\"text-align: center;\""
        case .right: return " style=\"text-align: right;\""
        case .justified: return " style=\"text-align: justify;\""
        default: return ""
        }
    }

    static func inlineHTML(_ a: NSAttributedString, _ r: NSRange, stripMarker: Bool, heading: Int) -> String {
        let ns = a.string as NSString
        var range = r
        if stripMarker, let m = ns.substring(with: r).range(of: "^\\t[^\\t]{0,6}\\t", options: .regularExpression) {
            let len = (String(ns.substring(with: r)[m]) as NSString).length
            range = NSRange(location: r.location + len, length: r.length - len)
        }
        // Runs that differ only in things HTML doesn't keep (a fallback font for a symbol) are written as one.
        var pieces: [(open: String, close: String, text: String)] = []
        a.enumerateAttributes(in: range) { attrs, rr, _ in
            var t = ns.substring(with: rr).replacingOccurrences(of: "\u{FFFC}", with: "")
            if t.isEmpty { return }
            t = NoteCodec.escape(t).replacingOccurrences(of: "\u{2028}", with: "<br>")
            let font = attrs[.font] as? NSFont
            let traits = font.map { NSFontManager.shared.traits(of: $0) } ?? []
            var open = "", close = ""
            func wrap(_ o: String, _ c: String) { open = o + open; close += c }
            if attrs[.dlCode] != nil { wrap("<code>", "</code>") }
            if traits.contains(.boldFontMask) && heading == 0 && attrs[.dlCode] == nil { wrap("<b>", "</b>") }
            if traits.contains(.italicFontMask) { wrap("<i>", "</i>") }
            if (attrs[.underlineStyle] as? Int ?? 0) != 0 && attrs[.link] == nil { wrap("<u>", "</u>") }
            if (attrs[.strikethroughStyle] as? Int ?? 0) != 0 { wrap("<s>", "</s>") }
            if let link = attrs[.link] {
                let href = (link as? URL)?.absoluteString ?? (link as? String) ?? ""
                wrap("<a href=\"\(NoteCodec.escape(href).replacingOccurrences(of: "\"", with: "&quot;"))\">", "</a>")
            }
            if attrs[.dlHighlight] != nil, let bg = attrs[.backgroundColor] as? NSColor {
                wrap("<span style=\"color: \(NoteStyle.highlightInk.cssRGBA); background: \(bg.cssRGBA);\">", "</span>")
            } else if attrs[.dlColor] != nil, let c = attrs[.foregroundColor] as? NSColor {
                wrap("<span style=\"color: \(c.cssRGBA);\">", "</span>")
            }
            if let last = pieces.last, last.open == open { pieces[pieces.count - 1].text += t }
            else { pieces.append((open, close, t)) }
        }
        let out = pieces.map { $0.open + $0.text + $0.close }.joined()
        return out
    }
}

extension NSAttributedString.Key {
    /// Font size from the HTML while reading it (used to spot headings), never kept.
    static let dlSizeHint = NSAttributedString.Key("dlSizeHint")
}

extension Array {
    func ifEmpty(_ other: [Element]) -> [Element] { isEmpty ? other : self }
}
