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
        Color(nsColor: NSColor(name: nil) { a in
            a.bestMatch(from: [.darkAqua, .vibrantDark]) != nil ? NSColor(dark) : NSColor(light)
        })
    }
}

enum Palette {
    /// Accents offered in Settings (same as the phone).
    static let accents: [Int32] = ([0xFF2F7DF6, 0xFF3E9B4F, 0xFFE0892B, 0xFFE5484D, 0xFFD6409F, 0xFF8E4EC6] as [UInt32]).map { Int32(bitPattern: $0) }
    static let accentNames = ["Blue", "Green", "Amber", "Red", "Pink", "Violet"]
    /// Wider set for projects, lists, habits and labels.
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
    static func color(_ key: Int32) -> Color? {
        all.first { $0.key == key }.map { Color.dynamic(light: Color(hex: $0.light), dark: Color(hex: $0.dark)) }
    }
}

extension Priority {
    var color: Color {
        switch self {
        case .URGENT: .dynamic(light: Color(hex: 0xD92D3A), dark: Color(hex: 0xFF8A8F))
        case .HIGH: .dynamic(light: Color(hex: 0xD9700A), dark: Color(hex: 0xFFB366))
        case .MEDIUM: .dynamic(light: Color(hex: 0x2F6BE0), dark: Color(hex: 0x8FB2FF))
        case .LOW: .dynamic(light: Color(hex: 0x5E7A68), dark: Color(hex: 0x9DB0A3))
        }
    }
    var symbol: String {
        switch self { case .URGENT: "exclamationmark.3"; case .HIGH: "exclamationmark.2"; case .MEDIUM: "exclamationmark"; case .LOW: "arrow.down" }
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
@MainActor
struct Formats {
    let store: Store

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
        let f = DateFormatter()
        switch store.dateFormat {
        case "DAY_MONTH": f.dateFormat = d.year == today.year ? "d MMM" : "d MMM yyyy"
        case "NUMERIC_DMY": f.dateFormat = "dd/MM/yyyy"
        case "NUMERIC_MDY": f.dateFormat = "MM/dd/yyyy"
        case "ISO": f.dateFormat = "yyyy-MM-dd"
        default: f.dateFormat = d.year == today.year ? "MMM d" : "MMM d, yyyy"
        }
        return f.string(from: d.date())
    }

    func time(_ t: TimeOfDay) -> String {
        let f = DateFormatter()
        switch store.timeFormat {
        case "H12": f.dateFormat = "h:mm a"
        case "H24": f.dateFormat = "HH:mm"
        default: f.timeStyle = .short
        }
        return f.string(from: Day.today().date(at: t))
    }

    func due(_ t: TaskItem) -> String? {
        guard let d = t.scheduledDate else { return nil }
        let time = t.dueTime ?? t.startTime
        return time.map { "\(date(d)), \(self.time($0))" } ?? date(d)
    }

    static func minutes(_ m: Int) -> String {
        if m < 60 { return "\(m)m" }
        return m % 60 == 0 ? "\(m / 60)h" : "\(m / 60)h \(m % 60)m"
    }
}

struct Card<Content: View>: View {
    var padding: CGFloat = 16
    @ViewBuilder var content: Content
    var body: some View {
        content
            .padding(padding)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(Color(nsColor: .controlBackgroundColor)))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Color.primary.opacity(0.07)))
    }
}

struct SectionHeader: View {
    let title: String
    var count: Int?
    var color: Color = .secondary
    var body: some View {
        HStack(spacing: 6) {
            Text(title).font(.headline).foregroundStyle(color)
            if let count { Text("\(count)").font(.subheadline.monospacedDigit()).foregroundStyle(.secondary) }
            Spacer()
        }
    }
}

struct EmptyState: View {
    let symbol: String
    let title: String
    var message: String = ""
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: symbol).font(.system(size: 38, weight: .light)).foregroundStyle(.tint.opacity(0.7))
            Text(title).font(.title3.weight(.semibold))
            if !message.isEmpty { Text(message).foregroundStyle(.secondary).multilineTextAlignment(.center).frame(maxWidth: 320) }
        }
        .padding(40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// A coloured dot or icon badge for projects, lists and habits.
struct ColorBadge: View {
    let color: Int32
    var symbol: String?
    var size: CGFloat = 22
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.3, style: .continuous).fill(Color(argb: color).gradient)
            if let symbol { Image(systemName: symbol).font(.system(size: size * 0.5, weight: .semibold)).foregroundStyle(.white) }
        }
        .frame(width: size, height: size)
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
    var body: some View {
        ZStack {
            Circle().stroke(color.opacity(0.15), lineWidth: lineWidth)
            Circle().trim(from: 0, to: max(0.0001, min(1, progress)))
                .stroke(color, style: StrokeStyle(lineWidth: lineWidth, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.spring(duration: 0.6), value: progress)
        }
    }
}
