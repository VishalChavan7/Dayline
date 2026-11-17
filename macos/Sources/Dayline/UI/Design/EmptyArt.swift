import SwiftUI

/// Small outlined illustrations with a friendly face, drawn in the accent colour, for empty pages.
enum ArtKind {
    case inbox, calendar, checklist, search, sprout, notebook, journal, chart

    /// Picks the drawing that fits an SF Symbol name used by older call sites.
    static func forSymbol(_ s: String) -> ArtKind {
        switch s {
        case let x where x.contains("tray"): .inbox
        case let x where x.contains("calendar"): .calendar
        case let x where x.contains("magnifyingglass"): .search
        case let x where x.contains("figure") || x.contains("flame") || x.contains("leaf"): .sprout
        case let x where x.contains("note") || x.contains("pencil"): .notebook
        case let x where x.contains("book"): .journal
        case let x where x.contains("chart"): .chart
        default: .checklist
        }
    }
}

struct EmptyArt: View {
    let kind: ArtKind
    var size: CGFloat = 150
    @Environment(\.scheme) private var scheme
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        let line = scheme.primary
        let soft = scheme.primaryContainer
        let leaf = scheme.tertiaryContainer
        let cheek = Color(hex: 0xF48FB1).opacity(colorScheme == .dark ? 0.45 : 0.55)
        Canvas { ctx, sz in
            let k = sz.width / 160
            func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: x * k, y: y * k) }
            func r(_ x0: CGFloat, _ y0: CGFloat, _ x1: CGFloat, _ y1: CGFloat) -> CGRect { CGRect(x: x0 * k, y: y0 * k, width: (x1 - x0) * k, height: (y1 - y0) * k) }
            let stroke = StrokeStyle(lineWidth: 2.6 * k, lineCap: .round, lineJoin: .round)
            func draw(_ path: Path, fill: Color? = nil) {
                if let fill { ctx.fill(path, with: .color(fill)) }
                ctx.stroke(path, with: .color(line), style: stroke)
            }
            func box(_ rect: CGRect, _ radius: CGFloat, fill: Color? = soft) {
                draw(Path(roundedRect: rect, cornerRadius: radius * k), fill: fill)
            }
            func lines(_ pts: [(CGFloat, CGFloat)], fill: Color? = nil, close: Bool = false) {
                var path = Path()
                path.move(to: p(pts[0].0, pts[0].1))
                for q in pts.dropFirst() { path.addLine(to: p(q.0, q.1)) }
                if close { path.closeSubpath() }
                draw(path, fill: fill)
            }
            func face(_ cx: CGFloat, _ cy: CGFloat) {
                for dx in [-8.0, 8.0] {
                    ctx.fill(Path(ellipseIn: r(cx + dx - 2.4, cy - 4.4, cx + dx + 2.4, cy + 0.4)), with: .color(line))
                    ctx.fill(Path(ellipseIn: r(cx + dx * 1.55 - 3.5, cy + 1.5, cx + dx * 1.55 + 3.5, cy + 5.5)), with: .color(cheek))
                }
                var smile = Path()
                smile.move(to: p(cx - 5, cy + 3))
                smile.addQuadCurve(to: p(cx + 5, cy + 3), control: p(cx, cy + 9))
                ctx.stroke(smile, with: .color(line), style: stroke)
            }
            func sparkle(_ x: CGFloat, _ y: CGFloat, _ s: CGFloat = 5) {
                var path = Path()
                path.move(to: p(x - s, y)); path.addLine(to: p(x + s, y))
                path.move(to: p(x, y - s)); path.addLine(to: p(x, y + s))
                ctx.stroke(path, with: .color(line.opacity(0.7)), style: StrokeStyle(lineWidth: 2 * k, lineCap: .round))
            }
            func dot(_ x: CGFloat, _ y: CGFloat) { ctx.fill(Path(ellipseIn: r(x - 2.2, y - 2.2, x + 2.2, y + 2.2)), with: .color(line.opacity(0.5))) }

            switch kind {
            case .inbox:
                box(r(58, 24, 102, 62), 4, fill: Color.white.opacity(colorScheme == .dark ? 0.08 : 0.9))
                lines([(66, 36), (94, 36)]); lines([(66, 45), (86, 45)])
                lines([(30, 64), (54, 64), (60, 76), (100, 76), (106, 64), (130, 64), (130, 104), (30, 104)], fill: soft, close: true)
                face(80, 88)
                sparkle(30, 34); sparkle(136, 30, 4); dot(140, 52)
            case .calendar:
                box(r(36, 30, 124, 112), 12)
                lines([(36, 52), (124, 52)])
                lines([(58, 22), (58, 38)]); lines([(102, 22), (102, 38)])
                face(80, 82)
                sparkle(26, 30); sparkle(138, 100, 4); dot(140, 60)
            case .checklist:
                box(r(44, 28, 116, 114), 10)
                box(r(64, 20, 96, 36), 6, fill: Color.white.opacity(colorScheme == .dark ? 0.08 : 0.95))
                lines([(62, 58), (74, 70), (98, 46)])
                face(80, 92)
                sparkle(30, 40); sparkle(132, 104, 4); dot(134, 34)
            case .search:
                draw(Path(ellipseIn: r(40, 26, 104, 90)), fill: soft)
                var handle = Path(); handle.move(to: p(96, 84)); handle.addLine(to: p(120, 108))
                ctx.stroke(handle, with: .color(line), style: StrokeStyle(lineWidth: 9 * k, lineCap: .round))
                ctx.stroke(handle, with: .color(soft), style: StrokeStyle(lineWidth: 4 * k, lineCap: .round))
                face(72, 60)
                sparkle(126, 30); sparkle(28, 100, 4); dot(30, 34)
            case .sprout:
                var stem = Path(); stem.move(to: p(80, 72)); stem.addLine(to: p(80, 42))
                ctx.stroke(stem, with: .color(line), style: stroke)
                var l1 = Path(); l1.move(to: p(80, 54)); l1.addQuadCurve(to: p(54, 38), control: p(58, 58)); l1.addQuadCurve(to: p(80, 54), control: p(74, 34)); draw(l1, fill: leaf)
                var l2 = Path(); l2.move(to: p(80, 46)); l2.addQuadCurve(to: p(108, 28), control: p(104, 48)); l2.addQuadCurve(to: p(80, 46), control: p(84, 24)); draw(l2, fill: leaf)
                lines([(54, 80), (106, 80), (98, 114), (62, 114)], fill: soft, close: true)
                box(r(48, 70, 112, 82), 4)
                face(80, 96)
                sparkle(30, 44); sparkle(134, 70, 4); dot(128, 28)
            case .notebook:
                box(r(42, 24, 110, 112), 10)
                lines([(54, 24), (54, 112)])
                lines([(66, 44), (98, 44)]); lines([(66, 54), (90, 54)])
                face(82, 84)
                var pencil = Path(); pencil.move(to: p(132, 40)); pencil.addLine(to: p(112, 84))
                ctx.stroke(pencil, with: .color(line), style: StrokeStyle(lineWidth: 10 * k, lineCap: .round))
                ctx.stroke(pencil, with: .color(leaf), style: StrokeStyle(lineWidth: 5 * k, lineCap: .round))
                sparkle(26, 34); dot(134, 104)
            case .journal:
                box(r(40, 24, 118, 114), 10)
                lines([(94, 24), (94, 52), (101, 46), (108, 52), (108, 24)], fill: leaf)
                var heart = Path()
                heart.move(to: p(68, 58))
                heart.addCurve(to: p(56, 46), control1: p(60, 54), control2: p(54, 50))
                heart.addCurve(to: p(68, 44), control1: p(58, 40), control2: p(66, 40))
                heart.addCurve(to: p(80, 46), control1: p(70, 40), control2: p(78, 40))
                heart.addCurve(to: p(68, 58), control1: p(82, 50), control2: p(76, 54))
                draw(heart, fill: Color(hex: 0xF48FB1).opacity(0.6))
                face(79, 86)
                sparkle(28, 36); sparkle(134, 96, 4); dot(132, 30)
            case .chart:
                lines([(30, 110), (130, 110)])
                box(r(40, 80, 64, 110), 6)
                box(r(68, 44, 92, 110), 6)
                box(r(96, 64, 120, 110), 6)
                face(80, 66)
                sparkle(32, 46); sparkle(130, 36, 4); dot(118, 50)
            }
        }
        .frame(width: size, height: size * 130 / 160)
        .accessibilityHidden(true)
    }
}
