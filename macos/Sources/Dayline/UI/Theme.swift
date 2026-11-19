import SwiftUI
import AppKit
import DaylineCore

extension Color {
    /// Android ARGB int (as stored in the sync file).
    init(argb: Int32) {
        let v = UInt32(bitPattern: argb)
        self.init(.sRGB, red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255,
                  blue: Double(v & 0xFF) / 255, opacity: Double((v >> 24) & 0xFF) / 255)
    }

    init(hex: UInt32) { self.init(argb: Int32(bitPattern: 0xFF00_0000 | hex)) }

    /// Light/dark pair that follows the window's appearance.
    static func dynamic(light: Color, dark: Color) -> Color {
        let l = NSColor(light), d = NSColor(dark)
        return Color(nsColor: NSColor(name: nil) { a in a.bestMatch(from: [.darkAqua, .vibrantDark]) != nil ? d : l })
    }
}

/// Hue/saturation/lightness, used to build the same tonal colours as the phone.
struct HSL {
    var h: Double, s: Double, l: Double

    init(h: Double, s: Double, l: Double) { self.h = h; self.s = s; self.l = l }

    init(argb: Int32) {
        let v = UInt32(bitPattern: argb)
        let r = Double((v >> 16) & 0xFF) / 255, g = Double((v >> 8) & 0xFF) / 255, b = Double(v & 0xFF) / 255
        let mx = max(r, g, b), mn = min(r, g, b), d = mx - mn
        l = (mx + mn) / 2
        if d == 0 { h = 0; s = 0; return }
        s = d / (1 - abs(2 * l - 1))
        switch mx {
        case r: h = 60 * ((g - b) / d).truncatingRemainder(dividingBy: 6)
        case g: h = 60 * ((b - r) / d + 2)
        default: h = 60 * ((r - g) / d + 4)
        }
        if h < 0 { h += 360 }
    }

    static func tone(_ h: Double, _ s: Double, _ l: Double) -> Color {
        let h = (h.truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)
        let s = min(1, max(0, s)), l = min(1, max(0, l))
        let c = (1 - abs(2 * l - 1)) * s
        let x = c * (1 - abs((h / 60).truncatingRemainder(dividingBy: 2) - 1))
        let m = l - c / 2
        let (r, g, b): (Double, Double, Double)
        switch h {
        case 0..<60: (r, g, b) = (c, x, 0)
        case 60..<120: (r, g, b) = (x, c, 0)
        case 120..<180: (r, g, b) = (0, c, x)
        case 180..<240: (r, g, b) = (0, x, c)
        case 240..<300: (r, g, b) = (x, 0, c)
        default: (r, g, b) = (c, 0, x)
        }
        return Color(.sRGB, red: r + m, green: g + m, blue: b + m, opacity: 1)
    }
}

/// Theme choices beyond a single colour, stored in the same "accent" setting as on the phone
/// (values that can't be real colours, so older versions keep working).
enum ThemeStyles {
    static let monochrome: Int32 = 0x0000_0101
    static let dynamic: Int32 = 0x0000_0201
    struct Gradient: Identifiable { let id: Int32; let name: String; let start: UInt32; let end: UInt32 }
    static let gradients: [Gradient] = [
        Gradient(id: 0x0000_0301, name: "Sunset", start: 0xF2545B, end: 0xF7A23B),
        Gradient(id: 0x0000_0302, name: "Ocean", start: 0x2F6FED, end: 0x2BB6D9),
        Gradient(id: 0x0000_0303, name: "Aurora", start: 0x7B3FF2, end: 0xE040B5),
        Gradient(id: 0x0000_0304, name: "Forest", start: 0x1F7A4D, end: 0x8BC34A),
        Gradient(id: 0x0000_0305, name: "Dusk", start: 0x3D3B8E, end: 0xE0607E),
    ]
    static func gradient(_ id: Int32) -> Gradient? { gradients.first { $0.id == id } }

    /// The Mac's own accent colour, standing in for the phone's wallpaper colours.
    static var systemAccent: Int32 {
        guard let c = NSColor.controlAccentColor.usingColorSpace(.sRGB) else { return Int32(bitPattern: 0xFF2F7DF6) }
        let v = (UInt32(0xFF) << 24) | (UInt32(c.redComponent * 255) << 16) | (UInt32(c.greenComponent * 255) << 8) | UInt32(c.blueComponent * 255)
        return Int32(bitPattern: v)
    }
}

/// The app's colours, derived from the theme exactly like the phone's colour scheme, with
/// calm Mac-style surfaces: a soft tinted window background and solid, lightly frosted cards.
struct Scheme {
    let primary: Color
    let primaryContainer: Color
    let onPrimaryContainer: Color
    let tertiary: Color
    let tertiaryContainer: Color
    let onTertiaryContainer: Color
    let background: Color
    let card: Color
    let cardStroke: Color
    /// Inset fills inside cards: search fields, chips, tiles.
    let well: Color
    let selection: Color
    /// Filled controls (buttons, today's date) and the text on them.
    let accent: Color
    let onAccent: Color
    /// Gradient themes: vivid ends for strong elements, soft ends for the progress card.
    var brandStrong: [Color]? = nil
    var brandSoft: [Color]? = nil

    @MainActor private static var cache: [Int32: Scheme] = [:]

    @MainActor static func of(_ theme: Int32) -> Scheme {
        let key = theme == ThemeStyles.dynamic ? ThemeStyles.systemAccent ^ 0x7 : theme
        if let s = cache[key] { return s }
        let s: Scheme
        if theme == ThemeStyles.monochrome {
            s = mono()
        } else if theme == ThemeStyles.dynamic {
            s = make(ThemeStyles.systemAccent)
        } else if let g = ThemeStyles.gradient(theme) {
            var base = make(Int32(bitPattern: 0xFF00_0000 | g.start))
            let a = Color(hex: g.start), b = Color(hex: g.end)
            base.brandStrong = [a, b]
            base.brandSoft = [Color.dynamic(light: mix(g.start, 0xFFFFFF, 0.78), dark: mix(g.start, 0x000000, 0.55)),
                              Color.dynamic(light: mix(g.end, 0xFFFFFF, 0.78), dark: mix(g.end, 0x000000, 0.55))]
            s = base
        } else if (UInt32(bitPattern: theme) >> 24) == 0 {
            s = make(Int32(bitPattern: 0xFF2F7DF6)) // unknown style: fall back to blue
        } else {
            s = make(theme)
        }
        cache[key] = s
        return s
    }

    private static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
        func ch(_ v: UInt32, _ sh: UInt32) -> Double { Double((v >> sh) & 0xFF) / 255 }
        func m(_ sh: UInt32) -> Double { ch(a, sh) * (1 - t) + ch(b, sh) * t }
        return Color(.sRGB, red: m(16), green: m(8), blue: m(0), opacity: 1)
    }

    static func make(_ seed: Int32) -> Scheme {
        let c = HSL(argb: seed)
        let h = c.h, s = min(0.85, max(0.35, c.s))
        func t(_ dh: Double, _ ss: Double, _ l: Double) -> Color { HSL.tone(h + dh, ss, l) }
        return Scheme(
            primary: .dynamic(light: t(0, s, 0.45), dark: t(0, s, 0.74)),
            primaryContainer: .dynamic(light: t(0, s, 0.91), dark: t(0, s * 0.7, 0.26)),
            onPrimaryContainer: .dynamic(light: t(0, s, 0.17), dark: t(0, s, 0.90)),
            tertiary: .dynamic(light: t(60, s * 0.6, 0.40), dark: t(60, s * 0.6, 0.76)),
            tertiaryContainer: .dynamic(light: t(60, s * 0.7, 0.91), dark: t(60, s * 0.5, 0.25)),
            onTertiaryContainer: .dynamic(light: t(60, s * 0.6, 0.16), dark: t(60, s * 0.6, 0.90)),
            background: .dynamic(light: t(0, 0.12, 0.952), dark: t(0, 0.06, 0.105)),
            card: .dynamic(light: Color.white.opacity(0.88), dark: t(0, 0.06, 0.155)),
            cardStroke: .dynamic(light: Color.black.opacity(0.07), dark: Color.white.opacity(0.07)),
            well: .dynamic(light: t(0, 0.10, 0.935), dark: t(0, 0.06, 0.205)),
            selection: .dynamic(light: t(0, s, 0.45).opacity(0.12), dark: t(0, s, 0.74).opacity(0.16)),
            accent: Color(argb: seed),
            onAccent: .white
        )
    }

    /// Black, greys and white (the phone's Mono theme).
    static func mono() -> Scheme {
        func d(_ l: UInt32, _ dk: UInt32) -> Color { .dynamic(light: Color(hex: l), dark: Color(hex: dk)) }
        return Scheme(
            primary: d(0x151515, 0xF2F2F2),
            primaryContainer: d(0xE4E4E4, 0x3A3A3A),
            onPrimaryContainer: d(0x151515, 0xF2F2F2),
            tertiary: d(0x3A3A3A, 0xD6D6D6),
            tertiaryContainer: d(0xDADADA, 0x444444),
            onTertiaryContainer: d(0x111111, 0xF0F0F0),
            background: d(0xF1F1F1, 0x1A1A1A),
            card: .dynamic(light: Color.white.opacity(0.9), dark: Color(hex: 0x262626)),
            cardStroke: .dynamic(light: Color.black.opacity(0.08), dark: Color.white.opacity(0.08)),
            well: d(0xE9E9E9, 0x303030),
            selection: .dynamic(light: Color.black.opacity(0.08), dark: Color.white.opacity(0.12)),
            accent: d(0x1C1C1C, 0xE6E6E6),
            onAccent: d(0xFFFFFF, 0x111111)
        )
    }
}

private struct SchemeKey: EnvironmentKey {
    static let defaultValue = Scheme.make(Int32(bitPattern: 0xFF2F7DF6))
}

extension EnvironmentValues {
    var scheme: Scheme {
        get { self[SchemeKey.self] }
        set { self[SchemeKey.self] = newValue }
    }
}

/// Soft container and readable foreground for a user colour (habits, labels), as on the phone.
enum ItemColor {
    @MainActor private static var cache: [Int32: (Color, Color)] = [:]

    @MainActor static func pair(_ argb: Int32) -> (container: Color, content: Color) {
        if let p = cache[argb] { return p }
        let c = HSL(argb: argb)
        let p = (Color.dynamic(light: HSL.tone(c.h, c.s * 0.7, 0.92), dark: HSL.tone(c.h, c.s * 0.55, 0.24)),
                 Color.dynamic(light: HSL.tone(c.h, c.s, 0.40), dark: HSL.tone(c.h, c.s, 0.76)))
        cache[argb] = p
        return p
    }
    @MainActor static func container(_ argb: Int32) -> Color { pair(argb).container }
    @MainActor static func content(_ argb: Int32) -> Color { pair(argb).content }
}

enum Palette {
    /// Accents offered in Settings (same as the phone).
    static let accents: [Int32] = ([0xFF2F7DF6, 0xFF3E9B4F, 0xFFE0892B, 0xFFE5484D, 0xFFD6409F, 0xFF8E4EC6] as [UInt32]).map { Int32(bitPattern: $0) }
    static let accentNames = ["Blue", "Green", "Amber", "Red", "Pink", "Violet"]
    /// Wider set for habits and labels.
    static let itemColors: [Int32] = ([0xFF4F5BD5, 0xFF0E9F8E, 0xFF2F7DF6, 0xFF3E9B4F, 0xFFE0892B, 0xFFE5484D, 0xFFD6409F, 0xFF8E4EC6,
        0xFF6E56CF, 0xFF00A2C7, 0xFFC2410C, 0xFF64748B] as [UInt32]).map { Int32(bitPattern: $0) }
}

/// Keep-style note colours; the key is stored in Note.color (0 = default). Same swatches as the phone.
enum NoteColors {
    struct Swatch: Identifiable { let key: Int32; let name: String; let light: UInt32; let dark: UInt32; var id: Int32 { key } }
    static let all: [Swatch] = [
        Swatch(key: 1, name: "Coral", light: 0xFAAFA8, dark: 0x77172E), Swatch(key: 2, name: "Peach", light: 0xF39F76, dark: 0x692B17),
        Swatch(key: 3, name: "Sand", light: 0xFFF8B8, dark: 0x7C4A03), Swatch(key: 4, name: "Mint", light: 0xE2F6D3, dark: 0x264D3B),
        Swatch(key: 5, name: "Sage", light: 0xB4DDD3, dark: 0x0C625D), Swatch(key: 6, name: "Fog", light: 0xD4E4ED, dark: 0x256377),
        Swatch(key: 7, name: "Storm", light: 0xAECCDC, dark: 0x284255), Swatch(key: 8, name: "Dusk", light: 0xD3BFDB, dark: 0x472E5B),
        Swatch(key: 9, name: "Blossom", light: 0xF6E2DD, dark: 0x6C394F), Swatch(key: 10, name: "Clay", light: 0xE9E3D4, dark: 0x4B443A),
        Swatch(key: 11, name: "Chalk", light: 0xEFEFF1, dark: 0x232427),
    ]
    private static let colors: [Int32: Color] = Dictionary(uniqueKeysWithValues: all.map { ($0.key, Color.dynamic(light: Color(hex: $0.light), dark: Color(hex: $0.dark))) })
    static func color(_ key: Int32) -> Color? { colors[key] }
}

extension Priority {
    var color: Color { PriorityColors.of(self) }
    var symbol: String {
        switch self { case .URGENT: "exclamationmark.3"; case .HIGH: "exclamationmark.2"; case .MEDIUM: "exclamationmark"; case .LOW: "arrow.down" }
    }
}

enum PriorityColors {
    static let urgent = Color.dynamic(light: Color(hex: 0xD92D3A), dark: Color(hex: 0xFF8A8F))
    static let high = Color.dynamic(light: Color(hex: 0xD9700A), dark: Color(hex: 0xFFB366))
    static let medium = Color.dynamic(light: Color(hex: 0x2F6BE0), dark: Color(hex: 0x8FB2FF))
    static let low = Color.dynamic(light: Color(hex: 0x5E7A68), dark: Color(hex: 0x9DB0A3))
    static func of(_ p: Priority) -> Color {
        switch p { case .URGENT: urgent; case .HIGH: high; case .MEDIUM: medium; case .LOW: low }
    }
}

enum StatusColor {
    static let overdue = Color.dynamic(light: Color(hex: 0xC62A36), dark: Color(hex: 0xFF8A8F))
    static let success = Color.dynamic(light: Color(hex: 0x1E8E4A), dark: Color(hex: 0x7FD69A))
    static let streak = Color.dynamic(light: Color(hex: 0xE0700A), dark: Color(hex: 0xFFB366))
}

/// Phone icon keys → SF Symbols.
enum Icons {
    static let keys = ["folder", "work", "home", "school", "book", "code", "idea", "rocket", "star", "heart", "fitness", "hike", "spa",
                       "meditate", "mind", "water", "sleep", "food", "coffee", "shopping", "savings", "travel", "music", "art",
                       "palette", "movie", "language", "garden", "pets"]
    static func symbol(_ key: String) -> String {
        switch key {
        case "folder": "folder.fill"; case "work": "briefcase.fill"; case "home": "house.fill"; case "school": "graduationcap.fill"
        case "book": "book.fill"; case "code": "chevron.left.forwardslash.chevron.right"; case "idea": "lightbulb.fill"
        case "rocket": "paperplane.fill"; case "star": "star.fill"; case "heart": "heart.fill"; case "fitness": "dumbbell.fill"
        case "hike": "figure.hiking"; case "spa": "leaf.fill"; case "meditate": "figure.mind.and.body"; case "mind": "brain.head.profile"
        case "water": "drop.fill"; case "sleep": "moon.fill"; case "food": "fork.knife"; case "coffee": "cup.and.saucer.fill"
        case "shopping": "cart.fill"; case "savings": "banknote.fill"; case "travel": "airplane"; case "music": "music.note"
        case "art": "paintbrush.fill"; case "palette": "paintpalette.fill"; case "movie": "film.fill"; case "language": "globe"
        case "garden": "camera.macro"; case "pets": "pawprint.fill"; case "list": "list.bullet"
        default: "folder.fill"
        }
    }
}

/// Date and time text that follows the shared date/time format settings.
/// Formatters are created once and reused: making one per row is slow.
@MainActor
struct Formats {
    let store: Store

    private static var formatters: [String: DateFormatter] = [:]

    static func formatter(_ pattern: String) -> DateFormatter {
        if let f = formatters[pattern] { return f }
        let f = DateFormatter()
        if pattern == "#short-time" { f.timeStyle = .short; f.dateStyle = .none } else { f.dateFormat = pattern }
        formatters[pattern] = f
        return f
    }

    func date(_ d: Day, relative: Bool = true) -> String {
        let today = store.today
        if relative {
            switch d - today {
            case 0: return "Today"
            case 1: return "Tomorrow"
            case -1: return "Yesterday"
            case 2...6: return d.weekday.name
            default: break
            }
        }
        let pattern: String
        switch store.dateFormat {
        case "DAY_MONTH": pattern = d.year == today.year ? "d MMM" : "d MMM yyyy"
        case "NUMERIC_DMY": pattern = "dd/MM/yyyy"
        case "NUMERIC_MDY": pattern = "MM/dd/yyyy"
        case "ISO": pattern = "yyyy-MM-dd"
        default: pattern = d.year == today.year ? "MMM d" : "MMM d, yyyy"
        }
        return Formats.formatter(pattern).string(from: d.date())
    }

    func time(_ t: TimeOfDay) -> String {
        let pattern: String
        switch store.timeFormat {
        case "H12": pattern = "h:mm a"
        case "H24": pattern = "HH:mm"
        default: pattern = "#short-time"
        }
        return Formats.formatter(pattern).string(from: Day.today().date(at: t))
    }

    func due(_ t: TaskItem) -> String? {
        guard let d = t.scheduledDate else { return nil }
        let time = t.dueTime ?? t.startTime
        return time.map { "\(date(d)) \(self.time($0))" } ?? date(d)
    }

    static func minutes(_ m: Int) -> String {
        if m < 60 { return "\(m)m" }
        return m % 60 == 0 ? "\(m / 60)h" : "\(m / 60)h \(m % 60)m"
    }
}

/// A plain card: solid surface, hairline edge, modest corners.
struct Card<Content: View>: View {
    var padding: CGFloat = 16
    var fill: Color?
    @ViewBuilder var content: Content
    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .surface(fill: fill)
    }
}

/// Coloured header label for groups of rows (like "Tomorrow" or "Pinned" on the phone).
struct SectionHeader: View {
    let title: String
    var count: Int?
    var color: Color?
    @Environment(\.scheme) private var scheme
    var body: some View {
        HStack(spacing: 6) {
            Text(title).font(.system(size: 13, weight: .semibold)).foregroundStyle(color ?? scheme.primary)
            Spacer()
            if let count { Text("\(count)").font(.system(size: 12).monospacedDigit()).foregroundStyle(.secondary) }
        }
    }
}

/// Empty page: a friendly outlined drawing, a title and a line of help, centred on the page.
struct EmptyState: View {
    var art: ArtKind
    let title: String
    var message: String = ""
    var action: (String, () -> Void)?

    init(symbol: String, title: String, message: String = "", action: (String, () -> Void)? = nil) {
        self.art = ArtKind.forSymbol(symbol); self.title = title; self.message = message; self.action = action
    }
    init(art: ArtKind, title: String, message: String = "", action: (String, () -> Void)? = nil) {
        self.art = art; self.title = title; self.message = message; self.action = action
    }

    var body: some View {
        VStack(spacing: 14) {
            EmptyArt(kind: art, size: 320)
            Text(title).font(.system(size: 18, weight: .semibold))
            if !message.isEmpty {
                Text(message).font(.system(size: 13)).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: 340)
            }
            if let action { Button(action.0, action: action.1).buttonStyle(.filled).padding(.top, 4) }
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Tonal icon tile, like the phone's icon badges.
struct IconBadge: View {
    let symbol: String
    let color: Color
    var size: CGFloat = 32
    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size * 0.48, weight: .semibold))
            .foregroundStyle(color)
            .frame(width: size, height: size)
            .background(RoundedRectangle(cornerRadius: size * 0.3, style: .continuous).fill(color.opacity(0.15)))
    }
}

/// Icon tile in a user colour (habits).
struct ColorBadge: View {
    let color: Int32
    var symbol: String?
    var size: CGFloat = 22
    var body: some View {
        let p = ItemColor.pair(color)
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.3, style: .continuous).fill(p.container)
            if let symbol { Image(systemName: symbol).font(.system(size: size * 0.5, weight: .semibold)).foregroundStyle(p.content) }
        }
        .frame(width: size, height: size)
    }
}

/// Small count bubble used in section headers.
struct CountPill: View {
    let text: String
    let color: Color
    var body: some View {
        Text(text).font(.system(size: 11, weight: .semibold).monospacedDigit())
            .foregroundStyle(color)
            .padding(.horizontal, 7).padding(.vertical, 2)
            .background(Capsule().fill(color.opacity(0.15)))
    }
}

struct ColorPickerRow: View {
    @Binding var color: Int32
    var colors: [Int32] = Palette.itemColors
    var body: some View {
        HStack(spacing: 8) {
            ForEach(colors, id: \.self) { c in
                Circle().fill(c == 0 ? Color(nsColor: .controlBackgroundColor) : Color(argb: c))
                    .overlay(Circle().strokeBorder(Color.primary.opacity(c == color ? 0.8 : 0.12), lineWidth: c == color ? 2.5 : 1))
                    .frame(width: 22, height: 22)
                    .onTapGesture { color = c }
                    .accessibilityLabel("Colour")
                    .accessibilityAddTraits(c == color ? .isSelected : [])
            }
        }
    }
}

struct IconPickerGrid: View {
    @Binding var icon: String
    let color: Int32
    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.fixed(30), spacing: 6), count: 10), spacing: 6) {
            ForEach(Icons.keys, id: \.self) { k in
                Image(systemName: Icons.symbol(k))
                    .frame(width: 28, height: 28)
                    .foregroundStyle(k == icon ? .white : .primary)
                    .background(RoundedRectangle(cornerRadius: 7).fill(k == icon ? Color(argb: color) : Color.primary.opacity(0.06)))
                    .onTapGesture { icon = k }
                    .help(k.capitalized)
            }
        }
    }
}

/// Progress ring used on Today, Focus and Habits.
struct Ring: View {
    let progress: Double
    var lineWidth: CGFloat = 10
    var color: Color = .accentColor
    var track: Color?
    var body: some View {
        ZStack {
            Circle().stroke(track ?? color.opacity(0.15), lineWidth: lineWidth)
            Circle().trim(from: 0, to: min(1, max(0, progress)))
                .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.easeOut(duration: 0.4), value: progress)
        }
        .padding(lineWidth / 2)
    }
}
