import Foundation

public struct CheckItem: Hashable, Sendable {
    public var text: String; public var done: Bool
    public init(text: String, done: Bool = false) { self.text = text; self.done = done }
}

/// A picture, drawing or voice recording attached on the phone (the files stay on the phone).
public struct NoteMedia: Hashable, Sendable {
    public enum Kind: String, Sendable { case IMAGE, DRAWING, AUDIO }
    public var type: Kind; public var file: String; public var durationMs: Int64; public var name: String
}

public struct NoteBody: Hashable, Sendable {
    public var html: String; public var checklist: [CheckItem]; public var media: [NoteMedia]
    public init(html: String, checklist: [CheckItem] = [], media: [NoteMedia] = []) { self.html = html; self.checklist = checklist; self.media = media }
}

/// Note bodies are stored as one string: formatted text as HTML, then (optionally) a Keep-style checklist and
/// a media list in HTML comments. Plain-text notes load as-is. Mirrors the phone's NoteCodec.
public enum NoteCodec {
    static let start = "<!--dl-checklist", end = "-->", mediaStart = "<!--dl-media"
    public static let checkOpen = "\u{2610} ", checkDone = "\u{2611}\u{FE0E} "

    static func re(_ p: String, _ o: NSRegularExpression.Options = []) -> NSRegularExpression { try! NSRegularExpression(pattern: p, options: o) }
    static let tagHint = re("<(p|br|b|i|u|s|em|strong|span|ol|ul|li|h[1-6]|div|mark|blockquote|pre|code|hr|table|a)[\\s>/]", .caseInsensitive)
    static let hashtag = re("(?<![\\p{L}\\p{N}_&#/])#([\\p{L}\\p{N}_-]{1,32}(?:/[\\p{L}\\p{N}_-]{1,32}){0,5})")

    public static func isHtml(_ s: String) -> Bool { tagHint.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)) != nil }

    static func matches(_ r: NSRegularExpression, _ s: String) -> [NSTextCheckingResult] { r.matches(in: s, range: NSRange(s.startIndex..., in: s)) }
    static func group(_ m: NSTextCheckingResult, _ i: Int, _ s: String) -> String {
        guard let r = Range(m.range(at: i), in: s) else { return "" }
        return String(s[r])
    }

    public static func labels(_ text: String) -> [String] {
        var seen = Set<String>(), out: [String] = []
        for m in matches(hashtag, text) {
            let t = group(m, 1, text).lowercased()
            if t.contains(where: \.isLetter), seen.insert(t).inserted { out.append(t) }
        }
        return out
    }

    public static func labelsOf(title: String, body: String) -> [String] { labels(title + "\n" + plainText(body)) }

    public static func media(_ body: String) -> [NoteMedia] {
        guard let i = body.range(of: mediaStart) else { return [] }
        let endIdx = body.range(of: end, range: i.upperBound..<body.endIndex)?.lowerBound ?? body.endIndex
        return body[i.upperBound..<endIdx].split(whereSeparator: \.isNewline).compactMap { line in
            let parts = line.trimmingCharacters(in: .whitespaces).components(separatedBy: "|")
            guard parts.count >= 2, let k = NoteMedia.Kind(rawValue: parts[0]) else { return nil }
            return NoteMedia(type: k, file: parts[1], durationMs: Int64(parts.count > 2 ? parts[2] : "") ?? 0, name: parts.count > 3 ? parts[3] : "")
        }
    }

    static func stripMedia(_ body: String) -> String {
        guard let i = body.range(of: mediaStart) else { return body }
        let e = body.range(of: end, range: i.upperBound..<body.endIndex)?.upperBound ?? body.endIndex
        return (String(body[..<i.lowerBound]) + String(body[e...])).trimmingTrailing()
    }

    public static func decode(_ body0: String) -> NoteBody {
        let media = media(body0)
        let body = stripMedia(body0)
        guard let i = body.range(of: start) else { return NoteBody(html: body, media: media) }
        let e = body.range(of: end, range: i.upperBound..<body.endIndex)
        let block = String(body[i.upperBound..<(e?.lowerBound ?? body.endIndex)])
        let items: [CheckItem] = block.components(separatedBy: "\n").compactMap { line in
            let c = Array(line)
            guard c.count >= 2, c[1] == "|" else { return nil }
            return CheckItem(text: String(c[2...]), done: c[0] == "1")
        }
        let rest = String(body[..<i.lowerBound]) + (e.map { String(body[$0.upperBound...]) } ?? "")
        return NoteBody(html: rest.trimmingTrailing(), checklist: items, media: media)
    }

    public static func encode(_ html: String, checklist: [CheckItem] = [], media: [NoteMedia] = []) -> String {
        var out = html
        let items = checklist.filter { !$0.text.trimmingCharacters(in: .whitespaces).isEmpty }
        if !items.isEmpty {
            let lines = items.map { ($0.done ? "1|" : "0|") + $0.text.replacingOccurrences(of: "\n", with: " ").replacingOccurrences(of: end, with: "→") }
            out += "\n\(start)\n" + lines.joined(separator: "\n") + "\n\(end)"
        }
        if !media.isEmpty {
            out += "\n\(mediaStart)\n" + media.map {
                "\($0.type.rawValue)|\($0.file)|\($0.durationMs)|" + $0.name.replacingOccurrences(of: "|", with: "/").replacingOccurrences(of: "\n", with: " ").replacingOccurrences(of: "-->", with: "→")
            }.joined(separator: "\n") + "\n\(end)"
        }
        return out
    }

    /// Readable text for previews, search and notifications.
    public static func plainText(_ body: String) -> String {
        let d = decode(body)
        var s = d.html
        if isHtml(s) {
            s = s.replacingOccurrences(of: "<br\\s*/?>", with: "\n", options: [.regularExpression, .caseInsensitive])
                .replacingOccurrences(of: "<hr\\s*/?>", with: "\n———\n", options: [.regularExpression, .caseInsensitive])
                .replacingOccurrences(of: "</t[hd]>", with: "  ", options: [.regularExpression, .caseInsensitive])
                .replacingOccurrences(of: "(?:</(?:p|li|h[1-6]|div|blockquote|pre|tr)>\\s*)+", with: "\n", options: [.regularExpression, .caseInsensitive])
                .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
            s = decodeEntities(s)
        }
        var lines = s.components(separatedBy: "\n").map { $0.trimmingTrailing() }
        while lines.first?.trimmingCharacters(in: .whitespaces).isEmpty == true { lines.removeFirst() }
        while lines.last?.trimmingCharacters(in: .whitespaces).isEmpty == true { lines.removeLast() }
        let text = lines.joined(separator: "\n")
        let list = d.checklist.map { ($0.done ? "☑ " : "☐ ") + $0.text }.joined(separator: "\n")
        let media = d.media.map { m -> String in
            switch m.type { case .IMAGE: "🖼 Image"; case .DRAWING: "✏️ Drawing"; case .AUDIO: "🎙 " + (m.name.isEmpty ? "Voice note" : m.name) }
        }.joined(separator: " ")
        return [text, list, media].filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }.joined(separator: "\n")
    }

    /// Length of the check prefix at the start of a paragraph's text (0 if none).
    public static func checkPrefix(_ text: String) -> Int {
        let s = Array(text.unicodeScalars)
        guard let f = s.first else { return 0 }
        let box: Int
        if f == "\u{2611}" && s.count > 1 && s[1] == "\u{FE0E}" { box = 2 }
        else if f == "\u{2610}" || f == "\u{2611}" { box = 1 }
        else { return 0 }
        return s.count > box && s[box] == " " ? box + 1 : box
    }

    public static func escape(_ t: String) -> String {
        t.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;").replacingOccurrences(of: ">", with: "&gt;")
    }

    static let named: [String: String] = [
        "amp": "&", "lt": "<", "gt": ">", "quot": "\"", "apos": "'", "nbsp": " ", "sol": "/", "bsol": "\\", "colon": ":",
        "semi": ";", "comma": ",", "period": ".", "excl": "!", "quest": "?", "num": "#", "dollar": "$", "percnt": "%",
        "commat": "@", "lpar": "(", "rpar": ")", "lsqb": "[", "rsqb": "]", "lcub": "{", "rcub": "}", "ast": "*", "plus": "+",
        "equals": "=", "verbar": "|", "lowbar": "_", "hat": "^", "grave": "`", "tilde": "~", "hyphen": "-", "dash": "-",
        "ndash": "–", "mdash": "—", "hellip": "…", "lsquo": "‘", "rsquo": "’", "ldquo": "“", "rdquo": "”", "bull": "•",
        "middot": "·", "copy": "©", "reg": "®", "trade": "™", "deg": "°", "times": "×", "divide": "÷", "euro": "€",
        "pound": "£", "yen": "¥", "cent": "¢", "rarr": "→", "larr": "←", "uarr": "↑", "darr": "↓", "NewLine": "\n", "Tab": "\t",
    ]
    static let entity = re("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[A-Za-z][A-Za-z0-9]{1,15});")

    public static func decodeEntities(_ text: String) -> String {
        var out = "", last = text.startIndex
        for m in matches(entity, text) {
            guard let r = Range(m.range, in: text) else { continue }
            out += text[last..<r.lowerBound]
            let code = group(m, 1, text)
            var rep: String?
            if code.hasPrefix("#x") || code.hasPrefix("#X") { rep = UInt32(code.dropFirst(2), radix: 16).flatMap(Unicode.Scalar.init).map { String($0) } }
            else if code.hasPrefix("#") { rep = UInt32(code.dropFirst()).flatMap(Unicode.Scalar.init).map { String($0) } }
            else { rep = named[code] }
            out += rep ?? String(text[r])
            last = r.upperBound
        }
        return out + text[last...]
    }

    /// Plain text of one block's inner HTML.
    public static func blockText(_ inner: String) -> String {
        decodeEntities(inner.replacingOccurrences(of: "<br\\s*/?>", with: " ", options: [.regularExpression, .caseInsensitive])
            .replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)).trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

extension String {
    func trimmingTrailing() -> String {
        var s = self
        while let l = s.last, l.isWhitespace { s.removeLast() }
        return s
    }
}
