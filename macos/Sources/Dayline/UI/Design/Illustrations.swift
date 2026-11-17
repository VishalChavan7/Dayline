import SwiftUI

/// Original vector artwork: calm skies and landscapes that change with the time of day.
enum DayPart: CaseIterable {
    case dawn, day, dusk, night
    static var now: DayPart {
        switch Calendar.current.component(.hour, from: Date()) {
        case 5..<9: .dawn
        case 9..<17: .day
        case 17..<20: .dusk
        default: .night
        }
    }
    var sky: [Color] {
        switch self {
        case .dawn: [Color(hex: 0xFFD6B8), Color(hex: 0xF8B4C4), Color(hex: 0xB9B5F0)]
        case .day: [Color(hex: 0xBFE3FF), Color(hex: 0xD9ECFF), Color(hex: 0xF3F7FF)]
        case .dusk: [Color(hex: 0x6E62C9), Color(hex: 0xE58BB0), Color(hex: 0xFFC48A)]
        case .night: [Color(hex: 0x0E1433), Color(hex: 0x1E2457), Color(hex: 0x3A3478)]
        }
    }
    var hills: [Color] {
        switch self {
        case .dawn: [Color(hex: 0xE7A0B6), Color(hex: 0xC383B7), Color(hex: 0x8F6FB0)]
        case .day: [Color(hex: 0x9CCFA8), Color(hex: 0x6FB592), Color(hex: 0x4E967E)]
        case .dusk: [Color(hex: 0x9E5F9C), Color(hex: 0x6E4B8E), Color(hex: 0x433A73)]
        case .night: [Color(hex: 0x2C2F66), Color(hex: 0x22265A), Color(hex: 0x171B45)]
        }
    }
    var sun: Color {
        switch self {
        case .dawn: Color(hex: 0xFFE6A8)
        case .day: Color(hex: 0xFFF2B8)
        case .dusk: Color(hex: 0xFFD08A)
        case .night: Color(hex: 0xF4F1FF)
        }
    }
}

/// A rolling hill line across the full width.
struct Hill: Shape {
    var base: CGFloat      // 0…1 from the top where the hill sits
    var amplitude: CGFloat // 0…1 of the height
    var phase: CGFloat
    var waves: CGFloat = 1.3
    func path(in r: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: r.minX, y: r.maxY))
        let steps = 60
        for i in 0...steps {
            let x = r.minX + r.width * CGFloat(i) / CGFloat(steps)
            let t = CGFloat(i) / CGFloat(steps)
            let y = r.minY + r.height * (base - amplitude * sin((t * waves + phase) * .pi))
            p.addLine(to: CGPoint(x: x, y: y))
        }
        p.addLine(to: CGPoint(x: r.maxX, y: r.maxY))
        p.closeSubpath()
        return p
    }
}

/// Sky, sun or moon, stars and three layers of hills.
struct Landscape: View {
    var part: DayPart = .now
    var sunPosition = CGPoint(x: 0.78, y: 0.42)
    var body: some View {
        GeometryReader { geo in
            let w = geo.size.width, h = geo.size.height
            ZStack {
                LinearGradient(colors: part.sky, startPoint: .top, endPoint: .bottom)
                if part == .night {
                    ForEach(0..<28, id: \.self) { i in
                        let x = CGFloat((i * 73) % 100) / 100, y = CGFloat((i * 37) % 55) / 100
                        Circle().fill(.white.opacity(0.4 + Double(i % 3) * 0.2))
                            .frame(width: CGFloat(1 + i % 3), height: CGFloat(1 + i % 3))
                            .position(x: x * w, y: y * h)
                    }
                }
                let sunSize = min(w, h) * 0.34
                Circle().fill(part.sun.opacity(0.35)).frame(width: sunSize * 1.9).blur(radius: sunSize * 0.35)
                    .position(x: sunPosition.x * w, y: sunPosition.y * h)
                Circle().fill(RadialGradient(colors: [part.sun, part.sun.opacity(0.85)], center: .center, startRadius: 0, endRadius: sunSize / 2))
                    .frame(width: sunSize).position(x: sunPosition.x * w, y: sunPosition.y * h)
                    .overlay {
                        if part == .night {
                            Circle().fill(part.sky[1]).frame(width: sunSize * 0.82)
                                .position(x: sunPosition.x * w + sunSize * 0.25, y: sunPosition.y * h - sunSize * 0.12)
                        }
                    }
                Hill(base: 0.72, amplitude: 0.10, phase: 0.2, waves: 1.6).fill(part.hills[0].opacity(0.9))
                Hill(base: 0.82, amplitude: 0.09, phase: 0.9, waves: 1.2).fill(part.hills[1].opacity(0.95))
                Hill(base: 0.92, amplitude: 0.07, phase: 0.4, waves: 1.8).fill(part.hills[2])
                if part != .night {
                    ForEach(0..<3, id: \.self) { i in
                        Bird().stroke(part.hills[2].opacity(0.55), style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
                            .frame(width: 14 - CGFloat(i) * 3, height: 5)
                            .position(x: w * (0.22 + CGFloat(i) * 0.07), y: h * (0.30 + CGFloat(i % 2) * 0.06))
                    }
                }
            }
        }
    }
}

struct Bird: Shape {
    func path(in r: CGRect) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: r.minX, y: r.minY))
        p.addQuadCurve(to: CGPoint(x: r.midX, y: r.maxY), control: CGPoint(x: r.width * 0.3, y: r.minY))
        p.addQuadCurve(to: CGPoint(x: r.maxX, y: r.minY), control: CGPoint(x: r.width * 0.7, y: r.minY))
        return p
    }
}

/// Soft colour wash behind each screen, so glass has something to catch.
struct AmbientBackdrop: View {
    var colors: [Color]
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        ZStack {
            Color(nsColor: .windowBackgroundColor)
            GeometryReader { geo in
                let w = geo.size.width, h = geo.size.height
                ZStack {
                    ForEach(Array(colors.enumerated()), id: \.offset) { i, c in
                        let size = max(w, h) * 0.8
                        Circle()
                            .fill(RadialGradient(colors: [c.opacity(scheme == .dark ? 0.30 : 0.24), c.opacity(0)], center: .center, startRadius: 0, endRadius: size / 2))
                            .frame(width: size, height: size)
                            .position(x: w * [0.12, 0.88, 0.55, 0.2][i % 4], y: h * [0.08, 0.2, 0.95, 0.75][i % 4])
                    }
                }
            }
        }
        .ignoresSafeArea()
    }
}

// MARK: Small illustrations for empty states

/// A sprout in a pot — habits.
struct SproutArt: View {
    var color: Color = .green
    var body: some View {
        Canvas { ctx, size in
            let w = size.width, h = size.height
            var pot = Path()
            pot.move(to: CGPoint(x: w * 0.3, y: h * 0.62)); pot.addLine(to: CGPoint(x: w * 0.7, y: h * 0.62))
            pot.addLine(to: CGPoint(x: w * 0.64, y: h * 0.95)); pot.addLine(to: CGPoint(x: w * 0.36, y: h * 0.95)); pot.closeSubpath()
            ctx.fill(pot, with: .color(Color(hex: 0xE39A6C)))
            ctx.fill(Path(roundedRect: CGRect(x: w * 0.26, y: h * 0.57, width: w * 0.48, height: h * 0.08), cornerRadius: 4), with: .color(Color(hex: 0xC97B4F)))
            var stem = Path(); stem.move(to: CGPoint(x: w * 0.5, y: h * 0.58)); stem.addQuadCurve(to: CGPoint(x: w * 0.5, y: h * 0.3), control: CGPoint(x: w * 0.46, y: h * 0.45))
            ctx.stroke(stem, with: .color(color), style: StrokeStyle(lineWidth: max(2, w * 0.03), lineCap: .round))
            var l1 = Path(); l1.move(to: CGPoint(x: w * 0.5, y: h * 0.38))
            l1.addQuadCurve(to: CGPoint(x: w * 0.22, y: h * 0.24), control: CGPoint(x: w * 0.3, y: h * 0.42))
            l1.addQuadCurve(to: CGPoint(x: w * 0.5, y: h * 0.38), control: CGPoint(x: w * 0.38, y: h * 0.18)); ctx.fill(l1, with: .color(color.opacity(0.85)))
            var l2 = Path(); l2.move(to: CGPoint(x: w * 0.5, y: h * 0.32))
            l2.addQuadCurve(to: CGPoint(x: w * 0.8, y: h * 0.14), control: CGPoint(x: w * 0.72, y: h * 0.36))
            l2.addQuadCurve(to: CGPoint(x: w * 0.5, y: h * 0.32), control: CGPoint(x: w * 0.58, y: h * 0.1)); ctx.fill(l2, with: .color(color))
        }
    }
}

/// An open notebook — notes.
struct NotebookArt: View {
    var color: Color = .accentColor
    var body: some View {
        Canvas { ctx, size in
            let w = size.width, h = size.height
            let left = Path(roundedRect: CGRect(x: w * 0.1, y: h * 0.18, width: w * 0.4, height: h * 0.66), cornerRadius: 8)
            let right = Path(roundedRect: CGRect(x: w * 0.5, y: h * 0.18, width: w * 0.4, height: h * 0.66), cornerRadius: 8)
            ctx.fill(left, with: .color(Color(hex: 0xFFF8E7))); ctx.fill(right, with: .color(Color(hex: 0xFFFDF5)))
            ctx.stroke(left, with: .color(.black.opacity(0.08)), lineWidth: 1); ctx.stroke(right, with: .color(.black.opacity(0.08)), lineWidth: 1)
            for i in 0..<5 {
                let y = h * (0.32 + CGFloat(i) * 0.1)
                ctx.fill(Path(roundedRect: CGRect(x: w * 0.16, y: y, width: w * (i == 4 ? 0.16 : 0.28), height: 3), cornerRadius: 1.5), with: .color(.black.opacity(0.12)))
                ctx.fill(Path(roundedRect: CGRect(x: w * 0.56, y: y, width: w * (i == 2 ? 0.18 : 0.28), height: 3), cornerRadius: 1.5), with: .color(.black.opacity(0.12)))
            }
            var pen = Path(); pen.move(to: CGPoint(x: w * 0.72, y: h * 0.92)); pen.addLine(to: CGPoint(x: w * 0.95, y: h * 0.6))
            ctx.stroke(pen, with: .color(color), style: StrokeStyle(lineWidth: max(4, w * 0.05), lineCap: .round))
        }
    }
}

/// A sun rising over a checklist — tasks.
struct ChecklistArt: View {
    var color: Color = .accentColor
    var body: some View {
        Canvas { ctx, size in
            let w = size.width, h = size.height
            ctx.fill(Path(ellipseIn: CGRect(x: w * 0.55, y: h * 0.05, width: w * 0.36, height: w * 0.36)), with: .color(Color(hex: 0xFFD98A).opacity(0.9)))
            let card = Path(roundedRect: CGRect(x: w * 0.14, y: h * 0.22, width: w * 0.6, height: h * 0.7), cornerRadius: 12)
            ctx.fill(card, with: .color(.white)); ctx.stroke(card, with: .color(.black.opacity(0.08)), lineWidth: 1)
            for i in 0..<3 {
                let y = h * (0.36 + CGFloat(i) * 0.17)
                let box = Path(roundedRect: CGRect(x: w * 0.22, y: y - 6, width: 12, height: 12), cornerRadius: 4)
                if i < 2 { ctx.fill(box, with: .color(color)) } else { ctx.stroke(box, with: .color(color), lineWidth: 2) }
                ctx.fill(Path(roundedRect: CGRect(x: w * 0.22 + 20, y: y - 2, width: w * (i == 1 ? 0.24 : 0.32), height: 4), cornerRadius: 2), with: .color(.black.opacity(0.14)))
            }
        }
    }
}

/// Empty state with an illustration.
struct IllustratedEmpty<Art: View>: View {
    let title: String
    var message: String = ""
    @ViewBuilder var art: Art
    var body: some View {
        VStack(spacing: 14) {
            art.frame(width: 140, height: 120)
            Text(title).font(.system(size: 20, weight: .semibold, design: .rounded))
            if !message.isEmpty { Text(message).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: 340) }
        }
        .padding(40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
