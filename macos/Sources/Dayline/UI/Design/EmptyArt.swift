import SwiftUI

/// The pictures on empty pages (drawn by tools/empty-art/gen.py, the same as on the phone).
enum ArtKind: String {
    case inbox, calendar, checklist, trophy, search, sprout, notebook, journal, chart, hello

    /// Picks the picture that fits an SF Symbol name used by older call sites.
    static func forSymbol(_ s: String) -> ArtKind {
        switch s {
        case let x where x.contains("tray"): .inbox
        case let x where x.contains("calendar"): .calendar
        case let x where x.contains("magnifyingglass"): .search
        case let x where x.contains("figure") || x.contains("flame") || x.contains("leaf"): .sprout
        case let x where x.contains("note") || x.contains("pencil"): .notebook
        case let x where x.contains("book"): .journal
        case let x where x.contains("chart"): .chart
        case let x where x.contains("checkmark") || x.contains("checklist"): .checklist
        default: .hello
        }
    }
}

struct EmptyArt: View {
    let kind: ArtKind
    /// Width; the picture keeps the proportions of its canvas (EmptyArtData.width × height).
    var size: CGFloat = 320
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.scheme) private var scheme

    var body: some View {
        let dark = colorScheme == .dark
        let elements = ArtShapes.scene(kind)
        var colors: [Character: Color] = ["a": scheme.primary.opacity(dark ? 0.85 : 0.8)]
        for (role, c) in EmptyArtData.palette { colors[role] = Color(hex: dark ? c.dark : c.light) }
        let palette = colors
        return Canvas { ctx, sz in
            let k = sz.width / EmptyArtData.width
            ctx.scaleBy(x: k, y: k)
            // One layer, so a cut erases only the shapes drawn before it, never the page behind.
            ctx.drawLayer { layer in
                for e in elements {
                    let style = StrokeStyle(lineWidth: e.width, lineCap: .round, lineJoin: .round)
                    if e.fill == "x" || e.stroke == "x" {
                        layer.blendMode = .destinationOut
                        if e.fill == "x" { layer.fill(e.path, with: .color(.black)) }
                        if e.stroke == "x" { layer.stroke(e.path, with: .color(.black), style: style) }
                        layer.blendMode = .normal
                        continue
                    }
                    if let f = e.fill, let c = palette[f] { layer.fill(e.path, with: .color(c)) }
                    if let s = e.stroke, let c = palette[s] { layer.stroke(e.path, with: .color(c), style: style) }
                }
            }
        }
        .frame(width: size, height: size * EmptyArtData.height / EmptyArtData.width)
        .clipped()
        .accessibilityHidden(true)
    }
}

/// Parses the pictures once: '<fill><stroke><width>:<path>' per element, path in absolute M/L/C/Z.
enum ArtShapes {
    struct Element { let fill: Character?; let stroke: Character?; let width: CGFloat; let path: Path }

    @MainActor private static var cache: [ArtKind: [Element]] = [:]

    @MainActor static func scene(_ kind: ArtKind) -> [Element] {
        if let c = cache[kind] { return c }
        let parsed = (EmptyArtData.scenes[kind.rawValue] ?? "").split(separator: "|").compactMap(element)
        cache[kind] = parsed
        return parsed
    }

    static func element(_ s: Substring) -> Element? {
        guard let colon = s.firstIndex(of: ":"), s.count > 2 else { return nil }
        let head = s[..<colon]
        let fill = head.first.flatMap { $0 == "-" ? nil : $0 }
        let stroke = head.dropFirst().first.flatMap { $0 == "-" ? nil : $0 }
        let width = CGFloat(Double(head.dropFirst(2)) ?? 2)
        return Element(fill: fill, stroke: stroke, width: width, path: path(s[s.index(after: colon)...]))
    }

    static func path(_ d: Substring) -> Path {
        var p = Path()
        var nums: [CGFloat] = []
        var cmd: Character = "M"
        func flush() {
            switch cmd {
            case "M": var i = 0; while i + 1 < nums.count { if i == 0 { p.move(to: CGPoint(x: nums[0], y: nums[1])) } else { p.addLine(to: CGPoint(x: nums[i], y: nums[i + 1])) }; i += 2 }
            case "L": var i = 0; while i + 1 < nums.count { p.addLine(to: CGPoint(x: nums[i], y: nums[i + 1])); i += 2 }
            case "C":
                var i = 0
                while i + 5 < nums.count {
                    p.addCurve(to: CGPoint(x: nums[i + 4], y: nums[i + 5]), control1: CGPoint(x: nums[i], y: nums[i + 1]), control2: CGPoint(x: nums[i + 2], y: nums[i + 3]))
                    i += 6
                }
            default: break
            }
            nums.removeAll(keepingCapacity: true)
        }
        var token = ""
        func endNumber() { if let v = Double(token) { nums.append(CGFloat(v)) }; token = "" }
        for ch in d {
            if ch == "M" || ch == "L" || ch == "C" || ch == "Z" {
                endNumber(); flush()
                if ch == "Z" { p.closeSubpath() }
                cmd = ch
            } else if ch == " " || ch == "," {
                endNumber()
            } else if ch == "-" && !token.isEmpty {
                endNumber(); token = "-"
            } else {
                token.append(ch)
            }
        }
        endNumber(); flush()
        return p
    }
}
