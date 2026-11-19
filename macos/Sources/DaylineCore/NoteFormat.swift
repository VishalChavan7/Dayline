import Foundation

/// The parts of a note that are more than formatted text, and everything that reads a note's structure:
/// blocks (quote, callout, code, table, divider), Markdown in and out, nested #tags, [[wiki links]],
/// headings and statistics. Twin of the phone's NoteFormat.kt — keep the two in step.
///
/// Blocks are stored as:
///   <blockquote>…</blockquote>
///   <div class="callout" data-kind="note|tip|important|warning|caution">…</div>
///   <pre><code class="language-x">…</code></pre>
///   <table><tr><th>…</th></tr><tr><td>…</td></tr></table>
///   <hr>
public enum NoteFormat {

    public enum Block: Equatable, Sendable {
        case rich(String)
        case quote(String)
        case callout(kind: String, html: String)
        case code(text: String, language: String)
        case table(rows: [[String]], header: Bool)
        case divider
    }

    public static let calloutKinds = ["note", "tip", "important", "warning", "caution"]

    static func re(_ p: String, _ o: NSRegularExpression.Options = [.caseInsensitive]) -> NSRegularExpression { try! NSRegularExpression(pattern: p, options: o) }
    static let opener = re("<(blockquote|div|pre|table|hr)(\\s[^>]*)?/?>")
    static let calloutClass = re("class\\s*=\\s*\"[^\"]*\\bcallout\\b")

    static func ns(_ s: String) -> NSString { s as NSString }
    static func sub(_ s: String, _ r: NSRange) -> String { ns(s).substring(with: r) }
    static func group(_ m: NSTextCheckingResult, _ i: Int, _ s: String) -> String {
        let r = m.range(at: i); return r.location == NSNotFound ? "" : sub(s, r)
    }
    static func first(_ r: NSRegularExpression, _ s: String, from: Int = 0) -> NSTextCheckingResult? {
        let n = ns(s).length
        guard from <= n else { return nil }
        return r.firstMatch(in: s, range: NSRange(location: from, length: n - from))
    }
    static func all(_ r: NSRegularExpression, _ s: String) -> [NSTextCheckingResult] { r.matches(in: s, range: NSRange(location: 0, length: ns(s).length)) }
    static func replace(_ s: String, _ pattern: String, _ opts: NSRegularExpression.Options = [.caseInsensitive], _ f: (NSTextCheckingResult, String) -> String) -> String {
        let r = re(pattern, opts)
        var out = "", last = 0
        for m in all(r, s) {
            out += sub(s, NSRange(location: last, length: m.range.location - last)) + f(m, s)
            last = m.range.location + m.range.length
        }
        return out + sub(s, NSRange(location: last, length: ns(s).length - last))
    }

    /// Splits a note's HTML into ordinary text and blocks, in order. Joining them gives the same note back.
    public static func split(_ html: String) -> [Block] {
        var out: [Block] = []
        var cursor = 0, from = 0
        let n = ns(html).length
        while let m = first(opener, html, from: from) {
            let tag = group(m, 1, html).lowercased()
            let attrs = group(m, 2, html)
            let openEnd = m.range.location + m.range.length
            if tag == "div" && first(calloutClass, attrs) == nil { from = openEnd; continue }
            let end = tag == "hr" ? openEnd : closing(html, tag, openEnd)
            guard end >= 0 else { from = openEnd; continue }
            let before = sub(html, NSRange(location: cursor, length: m.range.location - cursor))
            if !before.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { out.append(.rich(before)) }
            var inner = ""
            if tag != "hr" {
                let closeAt = ns(html).range(of: "</", options: .backwards, range: NSRange(location: openEnd, length: end - openEnd)).location
                inner = sub(html, NSRange(location: openEnd, length: max(0, (closeAt == NSNotFound ? end : closeAt) - openEnd)))
            }
            switch tag {
            case "hr": out.append(.divider)
            case "blockquote": out.append(.quote(inner.trimmingCharacters(in: .whitespacesAndNewlines)))
            case "div":
                let k = attr(attrs, "data-kind")?.lowercased()
                out.append(.callout(kind: k.flatMap { calloutKinds.contains($0) ? $0 : nil } ?? "note", html: inner.trimmingCharacters(in: .whitespacesAndNewlines)))
            case "pre":
                var raw = inner, lang = ""
                if let c = first(re("^\\s*<code(\\s[^>]*)?>([\\s\\S]*)</code>\\s*$"), inner) {
                    raw = group(c, 2, inner)
                    lang = (attr(group(c, 1, inner), "class") ?? "").split(separator: " ").first { $0.hasPrefix("language-") }.map { String($0.dropFirst(9)) } ?? ""
                }
                raw = raw.replacingOccurrences(of: "<br\\s*/?>", with: "\n", options: [.regularExpression, .caseInsensitive])
                    .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
                out.append(.code(text: NoteCodec.decodeEntities(raw), language: lang))
            default: out.append(table(inner))
            }
            cursor = end; from = end
            if from >= n { break }
        }
        let rest = sub(html, NSRange(location: cursor, length: n - cursor))
        if !rest.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || out.isEmpty { out.append(.rich(rest)) }
        return out
    }

    static func closing(_ html: String, _ tag: String, _ start: Int) -> Int {
        let r = re("<(/?)\(tag)(\\s[^>]*)?>")
        var depth = 1, i = start
        while let m = first(r, html, from: i) {
            depth += group(m, 1, html).isEmpty ? 1 : -1
            i = m.range.location + m.range.length
            if depth == 0 { return i }
        }
        return -1
    }

    static func attr(_ attrs: String, _ name: String) -> String? {
        first(re("\(name)\\s*=\\s*\"([^\"]*)\""), attrs).map { group($0, 1, attrs) }
    }

    static func table(_ inner: String) -> Block {
        var header = false
        var rows: [[String]] = []
        for (i, row) in all(re("<tr(\\s[^>]*)?>([\\s\\S]*?)</tr>"), inner).enumerated() {
            let rowHtml = group(row, 2, inner)
            let cells = all(re("<(t[hd])(\\s[^>]*)?>([\\s\\S]*?)</t[hd]>"), rowHtml).map { c -> String in
                if i == 0 && group(c, 1, rowHtml).lowercased() == "th" { header = true }
                return NoteCodec.blockText(group(c, 3, rowHtml))
            }
            if !cells.isEmpty { rows.append(cells) }
        }
        let width = rows.map(\.count).max() ?? 1
        let padded = rows.map { $0 + Array(repeating: "", count: width - $0.count) }
        return .table(rows: padded.isEmpty ? [Array(repeating: "", count: width)] : padded, header: header)
    }

    public static func join(_ blocks: [Block]) -> String { blocks.map(blockHtml).joined() }

    public static func blockHtml(_ b: Block) -> String {
        switch b {
        case .rich(let h): return h
        case .quote(let h): return "<blockquote>\(h.isBlank ? "<p></p>" : h)</blockquote>"
        case .callout(let k, let h): return "<div class=\"callout\" data-kind=\"\(k)\">\(h.isBlank ? "<p></p>" : h)</div>"
        case .code(let t, let l): return "<pre><code" + (l.isBlank ? "" : " class=\"language-\(NoteCodec.escape(l))\"") + ">" + NoteCodec.escape(t) + "</code></pre>"
        case .divider: return "<hr>"
        case .table(let rows, let header):
            return "<table>" + rows.enumerated().map { i, r in
                let t = i == 0 && header ? "th" : "td"
                return "<tr>" + r.map { "<\(t)>\(NoteCodec.escape($0))</\(t)>" }.joined() + "</tr>"
            }.joined() + "</table>"
        }
    }

    // MARK: Headings

    public struct Heading: Equatable, Sendable {
        public let level: Int, text: String
        public init(level: Int, text: String) { self.level = level; self.text = text }
    }

    static let emLevels: [(Double, Int)] = [(2.0, 1), (1.5, 2), (1.17, 3), (1.12, 4), (0.83, 5), (0.75, 6)]
    static let pxLevels: [(Double, Int)] = [(26, 1), (21, 2), (18, 3), (16.5, 4), (15.5, 5), (14.5, 6)]
    /// The sizes the phone writes for headings 1–6 (px), so both apps show the same.
    public static let headingPx: [Double] = [26, 21, 18, 16.5, 15.5, 14.5]

    /// Heading level for a CSS font size ("26.0px", "1.5em"), or 0 for ordinary text.
    public static func headingLevel(fontSize value: Double, unit: String) -> Int {
        let em = unit.lowercased() == "em"
        if em ? value < 0.7 : value < 14 { return 0 }
        return (em ? emLevels : pxLevels).min { abs($0.0 - value) < abs($1.0 - value) }!.1
    }

    /// Heading level of a paragraph written as a single sized span (how the phone writes headings), or 0.
    public static func spanHeadingLevel(_ inner: String) -> Int {
        guard let m = first(re("^\\s*<span\\s+style=\"[^\"]*font-size:\\s*([0-9.]+)(px|em)"), inner), let v = Double(group(m, 1, inner)) else { return 0 }
        return headingLevel(fontSize: v, unit: group(m, 2, inner))
    }

    public static func headings(_ html: String) -> [Heading] {
        var out: [Heading] = []
        for b in split(html) {
            let h: String
            switch b { case .rich(let x), .quote(let x): h = x; case .callout(_, let x): h = x; default: continue }
            for m in all(re("<(h[1-6]|p)(\\s[^>]*)?>([\\s\\S]*?)</\\1>"), h) {
                let tag = group(m, 1, h).lowercased()
                let inner = group(m, 3, h)
                let level = tag.hasPrefix("h") ? Int(String(tag.last!))! : spanHeadingLevel(inner)
                let text = NoteCodec.blockText(inner)
                if level > 0 && !text.isBlank { out.append(Heading(level: level, text: text)) }
            }
        }
        return out
    }

    // MARK: Tags, links, todos, stats

    public static func tagPath(_ tag: String) -> [String] {
        let parts = tag.split(separator: "/").map(String.init).filter { !$0.isBlank }
        return parts.indices.map { parts[0...$0].joined(separator: "/") }
    }

    public static func tagMatches(_ tag: String, _ filter: String) -> Bool { tag == filter || tag.hasPrefix(filter + "/") }

    static let wiki = re("\\[\\[([^\\[\\]\\n|]{1,120})(?:\\|[^\\]\\n]{0,120})?\\]\\]", [])

    public static func wikiLinks(_ text: String) -> [String] {
        var seen = Set<String>(), out: [String] = []
        for m in all(wiki, text) {
            let t = group(m, 1, text).trimmingCharacters(in: .whitespaces)
            if !t.isEmpty, seen.insert(t.lowercased()).inserted { out.append(t) }
        }
        return out
    }

    public static func linksTo(_ text: String, title: String) -> Bool {
        let t = title.trimmingCharacters(in: .whitespaces).lowercased()
        return !t.isEmpty && wikiLinks(text).contains { $0.lowercased() == t }
    }

    public static func hasOpenTodo(_ body: String) -> Bool {
        let d = NoteCodec.decode(body)
        if d.checklist.contains(where: { !$0.done && !$0.text.isBlank }) { return true }
        return NoteCodec.plainText(d.html).components(separatedBy: "\n").contains { $0.trimmingCharacters(in: .whitespaces).hasPrefix("\u{2610}") }
    }

    public struct Stats: Equatable, Sendable {
        public let words: Int, characters: Int, charactersNoSpaces: Int, paragraphs: Int, readMinutes: Int
    }

    public static func stats(title: String, body: String) -> Stats {
        let text = NoteCodec.plainText(body)
        let words = all(re("[\\p{L}\\p{N}][\\p{L}\\p{N}'’_-]*", []), text).count
        return Stats(words: words, characters: text.count, charactersNoSpaces: text.filter { !$0.isWhitespace }.count,
                     paragraphs: text.components(separatedBy: "\n").filter { !$0.isBlank }.count,
                     readMinutes: words == 0 ? 0 : max(1, (words + 199) / 200))
    }

    // MARK: Markdown

    public static func toMarkdown(title: String, body: String) -> String {
        let d = NoteCodec.decode(body)
        var s = ""
        if !title.isBlank { s += "# " + title.trimmingCharacters(in: .whitespaces) + "\n\n" }
        func quoted(_ text: String) -> String {
            text.trimmingTrailingWhitespace().components(separatedBy: "\n").map { $0.isBlank ? ">" : "> " + $0 }.joined(separator: "\n")
        }
        for b in split(d.html) {
            if case .rich = b {} else if !s.isEmpty && !s.hasSuffix("\n\n") { s += "\n" }
            switch b {
            case .rich(let h): s += richToMarkdown(h)
            case .quote(let h): s += quoted(richToMarkdown(h)) + "\n\n"
            case .callout(let k, let h): s += "> [!\(k.uppercased())]\n" + quoted(richToMarkdown(h)) + "\n\n"
            case .code(let t, let l): s += "```\(l)\n" + t.trimmingTrailingNewlines() + "\n```\n\n"
            case .divider: s += "---\n\n"
            case .table(let rows, let header):
                func line(_ r: [String]) -> String { "| " + r.map { $0.replacingOccurrences(of: "|", with: "\\|").replacingOccurrences(of: "\n", with: " ") }.joined(separator: " | ") + " |" }
                let head = header ? rows[0] : Array(repeating: "", count: rows[0].count)
                s += line(head) + "\n" + "|" + head.map { _ in " --- " }.joined(separator: "|") + "|\n"
                for r in (header ? Array(rows.dropFirst()) : rows) { s += line(r) + "\n" }
                s += "\n"
            }
        }
        for c in d.checklist { s += (c.done ? "- [x] " : "- [ ] ") + c.text + "\n" }
        let collapsed = s.replacingOccurrences(of: "\n{3,}", with: "\n\n", options: .regularExpression)
        return collapsed.trimmingCharacters(in: .whitespacesAndNewlines) + "\n"
    }

    static func richToMarkdown(_ html: String) -> String {
        guard NoteCodec.isHtml(html) else { return html.trimmingCharacters(in: .whitespacesAndNewlines) + "\n\n" }
        var s = "", ordered = 0
        for m in all(re("<(p|li|h[1-6])(\\s[^>]*)?>([\\s\\S]*?)</\\1>|<(ol|ul)(\\s[^>]*)?>|</(ol|ul)>|<br\\s*/?>"), html) {
            let list = group(m, 4, html), closeList = group(m, 6, html), tag = group(m, 1, html).lowercased()
            if !list.isEmpty { ordered = list.lowercased() == "ol" ? 1 : 0; continue }
            if !closeList.isEmpty { ordered = 0; s += "\n"; continue }
            if tag.isEmpty { s += "\n"; continue }
            let inner = group(m, 3, html)
            var text = inlineToMarkdown(inner).trimmingCharacters(in: .whitespaces)
            let plain = NoteCodec.blockText(inner)
            if NoteCodec.checkPrefix(plain) > 0 {
                let done = plain.unicodeScalars.first == "\u{2611}"
                text = text.replacingOccurrences(of: "^[\\u2610\\u2611]\\uFE0E?\\s?", with: "", options: .regularExpression)
                s += (done ? "- [x] " : "- [ ] ") + text + "\n"; continue
            }
            if tag == "li" { s += (ordered > 0 ? "\(ordered). " : "- ") + text + "\n"; if ordered > 0 { ordered += 1 } }
            else if tag.hasPrefix("h") { s += String(repeating: "#", count: Int(String(tag.last!))!) + " " + text + "\n\n" }
            else {
                let level = spanHeadingLevel(inner)
                if level > 0 { s += String(repeating: "#", count: level) + " " + text.replacingOccurrences(of: "^\\*\\*(.*)\\*\\*$", with: "$1", options: .regularExpression) + "\n\n" }
                else { s += text + "\n\n" }
            }
        }
        return s
    }

    static func inlineToMarkdown(_ inner: String) -> String {
        var s = inner
        s = replace(s, "<a\\s[^>]*href=\"([^\"]*)\"[^>]*>([\\s\\S]*?)</a>") { m, x in "[" + group(m, 2, x) + "](" + group(m, 1, x) + ")" }
        s = replace(s, "<(b|strong)(\\s[^>]*)?>([\\s\\S]*?)</\\1>") { m, x in "**" + group(m, 3, x) + "**" }
        s = replace(s, "<(i|em)(\\s[^>]*)?>([\\s\\S]*?)</\\1>") { m, x in "*" + group(m, 3, x) + "*" }
        s = replace(s, "<(s|del|strike)(\\s[^>]*)?>([\\s\\S]*?)</\\1>") { m, x in "~~" + group(m, 3, x) + "~~" }
        s = replace(s, "<code(\\s[^>]*)?>([\\s\\S]*?)</code>") { m, x in "`" + group(m, 2, x) + "`" }
        s = replace(s, "<mark(\\s[^>]*)?>([\\s\\S]*?)</mark>") { m, x in "==" + group(m, 2, x) + "==" }
        s = replace(s, "<span\\s+style=\"[^\"]*background[^\"]*\"[^>]*>([\\s\\S]*?)</span>") { m, x in "==" + group(m, 1, x) + "==" }
        s = s.replacingOccurrences(of: "<br\\s*/?>", with: "  \n", options: [.regularExpression, .caseInsensitive])
        s = s.replacingOccurrences(of: "</?(span|u|font)(\\s[^>]*)?>", with: "", options: [.regularExpression, .caseInsensitive])
        s = s.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        return NoteCodec.decodeEntities(s).replacingOccurrences(of: "\u{00A0}", with: " ")
    }

    public struct Imported: Equatable, Sendable { public let title: String; public let html: String }

    public static func fromMarkdown(_ md: String, fallbackTitle: String = "") -> Imported {
        let lines = md.replacingOccurrences(of: "\r\n", with: "\n").replacingOccurrences(of: "\r", with: "\n").components(separatedBy: "\n")
        var i = 0, title = fallbackTitle
        while i < lines.count && lines[i].isBlank { i += 1 }
        if i < lines.count, lines[i].range(of: "^#\\s+\\S", options: .regularExpression) != nil {
            title = String(lines[i].dropFirst()).trimmingCharacters(in: .whitespaces); i += 1
        }
        var out = "", para: [String] = [], list: String? = nil
        func flushPara() { if !para.isEmpty { out += "<p>" + para.map(inlineFromMarkdown).joined(separator: "<br>") + "</p>"; para = [] } }
        func closeList() { if let l = list { out += "</\(l)>"; list = nil } }
        func matches(_ s: String, _ p: String) -> Bool { s.range(of: p, options: .regularExpression) != nil }
        while i < lines.count {
            let t = lines[i].trimmingCharacters(in: .whitespaces)
            if t.hasPrefix("```") {
                flushPara(); closeList()
                let lang = String(t.dropFirst(3)).trimmingCharacters(in: .whitespaces)
                var code: [String] = []
                i += 1
                while i < lines.count && !lines[i].trimmingCharacters(in: .whitespaces).hasPrefix("```") { code.append(lines[i]); i += 1 }
                out += blockHtml(.code(text: code.joined(separator: "\n"), language: lang))
            } else if t.isEmpty { flushPara(); closeList() }
            else if matches(t, "^(-{3,}|\\*{3,}|_{3,})$") { flushPara(); closeList(); out += "<hr>" }
            else if matches(t, "^#{1,6}\\s") {
                flushPara(); closeList()
                let level = t.prefix { $0 == "#" }.count
                out += "<h\(level)>" + inlineFromMarkdown(String(t.dropFirst(level)).trimmingCharacters(in: .whitespaces)) + "</h\(level)>"
            } else if t.hasPrefix(">") {
                flushPara(); closeList()
                var q: [String] = []
                while i < lines.count, lines[i].trimmingCharacters(in: .whitespaces).hasPrefix(">") {
                    var l = String(lines[i].trimmingCharacters(in: .whitespaces).dropFirst())
                    if l.hasPrefix(" ") { l.removeFirst() }
                    q.append(l); i += 1
                }
                i -= 1
                if let f = q.first, let m = first(re("^\\[!(\\w+)\\]\\s*$", []), f) {
                    let k = group(m, 1, f).lowercased()
                    out += blockHtml(.callout(kind: calloutKinds.contains(k) ? k : "note", html: fromMarkdown(q.dropFirst().joined(separator: "\n")).html))
                } else { out += blockHtml(.quote(fromMarkdown(q.joined(separator: "\n")).html)) }
            } else if t.hasPrefix("|"), i + 1 < lines.count, matches(lines[i + 1].trimmingCharacters(in: .whitespaces), "^\\|?\\s*:?-{3,}") {
                flushPara(); closeList()
                func cells(_ s: String) -> [String] {
                    var x = s.trimmingCharacters(in: .whitespaces)
                    if x.hasPrefix("|") { x.removeFirst() }
                    if x.hasSuffix("|") { x.removeLast() }
                    return x.replacingOccurrences(of: "\\|", with: "\u{1}").components(separatedBy: "|").map { $0.trimmingCharacters(in: .whitespaces).replacingOccurrences(of: "\u{1}", with: "|") }
                }
                var rows = [cells(t)]
                i += 2
                while i < lines.count, lines[i].trimmingCharacters(in: .whitespaces).hasPrefix("|") { rows.append(cells(lines[i])); i += 1 }
                i -= 1
                let w = rows.map(\.count).max() ?? 1
                out += blockHtml(.table(rows: rows.map { $0 + Array(repeating: "", count: w - $0.count) }, header: rows[0].contains { !$0.isBlank }))
            } else if let m = first(re("^[-*+]\\s+\\[([ xX])\\]\\s+(.*)$", []), t) {
                flushPara(); closeList()
                let done = !group(m, 1, t).trimmingCharacters(in: .whitespaces).isEmpty
                out += "<p>" + (done ? NoteCodec.checkDone : NoteCodec.checkOpen) + inlineFromMarkdown(group(m, 2, t)) + "</p>"
            } else if matches(t, "^[-*+]\\s+") || matches(t, "^\\d+[.)]\\s+") {
                flushPara()
                let kind = t.first!.isNumber ? "ol" : "ul"
                if list != kind { closeList(); out += "<\(kind)>"; list = kind }
                out += "<li>" + inlineFromMarkdown(t.replacingOccurrences(of: "^([-*+]|\\d+[.)])\\s+", with: "", options: .regularExpression)) + "</li>"
            } else { closeList(); para.append(t) }
            i += 1
        }
        flushPara(); closeList()
        return Imported(title: title, html: out)
    }

    static func inlineFromMarkdown(_ s0: String) -> String {
        var codes: [String] = []
        var s = replace(s0, "`([^`]+)`", []) { m, x in codes.append(group(m, 1, x)); return "\u{0}\(codes.count - 1)\u{0}" }
        s = NoteCodec.escape(s)
        s = replace(s, "\\[([^\\]]+)\\]\\(([^)\\s]+)\\)", []) { m, x in "<a href=\"\(group(m, 2, x))\">\(group(m, 1, x))</a>" }
        s = replace(s, "\\*\\*([^*]+)\\*\\*|__([^_]+)__", []) { m, x in let a = group(m, 1, x); return "<b>" + (a.isEmpty ? group(m, 2, x) : a) + "</b>" }
        s = replace(s, "(?<![*\\w])\\*([^*\\n]+)\\*(?!\\*)|(?<![_\\w])_([^_\\n]+)_(?![_\\w])", []) { m, x in let a = group(m, 1, x); return "<i>" + (a.isEmpty ? group(m, 2, x) : a) + "</i>" }
        s = replace(s, "~~([^~]+)~~", []) { m, x in "<s>\(group(m, 1, x))</s>" }
        s = replace(s, "==([^=]+)==", []) { m, x in "<mark>\(group(m, 1, x))</mark>" }
        s = replace(s, "\u{0}(\\d+)\u{0}", []) { m, x in "<code>" + NoteCodec.escape(codes[Int(group(m, 1, x))!]) + "</code>" }
        return s
    }

    /// The note as a standalone HTML page (export and printing).
    public static func toHtmlPage(title: String, body: String) -> String {
        let d = NoteCodec.decode(body)
        let checks = d.checklist.map { "<p>" + ($0.done ? "☑ " : "☐ ") + NoteCodec.escape($0.text) + "</p>" }.joined()
        return """
        <!DOCTYPE html><html><head><meta charset="utf-8"><title>\(NoteCodec.escape(title))</title><style>
        body{font:16px/1.55 -apple-system,Roboto,"Segoe UI",sans-serif;max-width:720px;margin:40px auto;padding:0 24px;color:#1d1d1f}
        h1{font-size:30px}blockquote{border-left:3px solid #c7c7cc;margin:12px 0;padding:2px 14px;color:#444}
        .callout{background:#eef3fd;border-radius:10px;padding:10px 14px;margin:12px 0}.callout[data-kind=tip]{background:#e8f6ec}
        .callout[data-kind=warning],.callout[data-kind=caution]{background:#fdf0e7}.callout[data-kind=important]{background:#f3ecfd}
        pre{background:#f5f5f7;border-radius:8px;padding:12px;overflow:auto}code{font-family:ui-monospace,Menlo,monospace;font-size:14px}
        table{border-collapse:collapse;margin:12px 0}td,th{border:1px solid #d2d2d7;padding:6px 10px;text-align:left}th{background:#f5f5f7}
        hr{border:0;border-top:1px solid #d2d2d7;margin:20px 0}mark{background:#fff59d}
        </style></head><body>
        """ + (title.isBlank ? "" : "<h1>\(NoteCodec.escape(title))</h1>") + d.html + checks + "</body></html>"
    }
}

extension String {
    var isBlank: Bool { trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    func trimmingTrailingWhitespace() -> String { replacingOccurrences(of: "\\s+$", with: "", options: .regularExpression) }
    func trimmingTrailingNewlines() -> String { replacingOccurrences(of: "\\n+$", with: "", options: .regularExpression) }
}
