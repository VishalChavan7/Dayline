import SwiftUI
import AppKit

/// Where every page starts: titles line up across screens.
enum Page {
    static let h: CGFloat = 28
    static let top: CGFloat = 20
    static let bottom: CGFloat = 28
}

/// Corner radii: modest, Mac-like corners.
enum Radius {
    static let card: CGFloat = 12
    static let inner: CGFloat = 8
    static let small: CGFloat = 6
}

/// Solid surfaces only. No live blur, no shadows: these are what made scrolling stutter,
/// because every card had to be re-rendered off-screen on each frame.
struct SurfaceBackground: ViewModifier {
    var cornerRadius: CGFloat = Radius.card
    var fill: Color?
    var stroke = true
    @Environment(\.scheme) private var scheme

    func body(content: Content) -> some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        content
            .background(shape.fill(fill ?? scheme.card))
            .overlay { if stroke { shape.strokeBorder(scheme.cardStroke, lineWidth: 1) } }
    }
}

extension View {
    /// A card surface behind the view.
    func surface(_ cornerRadius: CGFloat = Radius.card, fill: Color? = nil, stroke: Bool = true) -> some View {
        modifier(SurfaceBackground(cornerRadius: cornerRadius, fill: fill, stroke: stroke))
    }

    /// The window's content background.
    func screenBackground() -> some View { modifier(ScreenBackground()) }
}

private struct ScreenBackground: ViewModifier {
    @Environment(\.scheme) private var scheme
    func body(content: Content) -> some View { content.background(scheme.background) }
}

/// Filled accent button (the phone's main buttons).
struct FilledButtonStyle: ButtonStyle {
    var tonal = false
    @Environment(\.scheme) private var scheme
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 13, weight: .semibold))
            .lineLimit(1).fixedSize()
            .padding(.horizontal, 14).frame(minHeight: 30)
            .foregroundStyle(tonal ? scheme.onPrimaryContainer : scheme.onAccent)
            .background {
                let shape = RoundedRectangle(cornerRadius: Radius.inner, style: .continuous)
                if tonal { shape.fill(scheme.primaryContainer) }
                else if let g = scheme.brandStrong { shape.fill(LinearGradient(colors: g, startPoint: .leading, endPoint: .trailing)) }
                else { shape.fill(scheme.accent) }
            }
            .opacity(configuration.isPressed ? 0.8 : enabled ? 1 : 0.5)
            .contentShape(Rectangle())
    }
}

extension ButtonStyle where Self == FilledButtonStyle {
    static var filled: FilledButtonStyle { FilledButtonStyle() }
    static var tonal: FilledButtonStyle { FilledButtonStyle(tonal: true) }
}

/// Small square icon button with a hover highlight.
struct IconButton: View {
    let symbol: String
    var help: String = ""
    var size: CGFloat = 28
    let action: () -> Void
    @State private var hover = false
    var body: some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: size * 0.45, weight: .semibold))
                .frame(width: size, height: size)
                .background(RoundedRectangle(cornerRadius: Radius.small, style: .continuous).fill(Color.primary.opacity(hover ? 0.08 : 0)))
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .onHover { hover = $0 }
        .help(help)
        .accessibilityLabel(help)
    }
}

/// The label of a menu that sits beside IconButtons: same size, symbol weight and hover.
struct IconMenuLabel: View {
    let symbol: String
    var size: CGFloat = 28
    @State private var hover = false
    var body: some View {
        Image(systemName: symbol).font(.system(size: size * 0.45, weight: .semibold))
            .frame(width: size, height: size)
            .background(RoundedRectangle(cornerRadius: Radius.small, style: .continuous).fill(Color.primary.opacity(hover ? 0.08 : 0)))
            .contentShape(Rectangle())
            .onHover { hover = $0 }
    }
}

/// Big title and subtitle at the top of a screen, like the phone's top bar.
struct ScreenTitle: View {
    let title: String
    var subtitle: String?
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.system(size: 28, weight: .bold))
            if let subtitle, !subtitle.isEmpty { Text(subtitle).font(.system(size: 13, weight: .medium)).foregroundStyle(.secondary) }
        }
    }
}

/// Month / Week / Year style switcher, like the phone's segmented buttons.
struct Segmented<T: Hashable>: View {
    let options: [T]
    @Binding var selection: T
    let label: (T) -> String
    @Environment(\.scheme) private var scheme
    var body: some View {
        HStack(spacing: 2) {
            ForEach(options, id: \.self) { o in
                let on = o == selection
                Button { selection = o } label: {
                    Text(label(o)).font(.system(size: 12.5, weight: on ? .semibold : .medium)).lineLimit(1).fixedSize()
                        .foregroundStyle(on ? scheme.onAccent : .primary)
                        .padding(.horizontal, 14).frame(height: 26)
                        .background(RoundedRectangle(cornerRadius: Radius.small, style: .continuous).fill(on ? scheme.accent : .clear))
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .padding(3)
        .background(RoundedRectangle(cornerRadius: Radius.inner, style: .continuous).fill(scheme.well))
    }
}

/// Rounded-rectangle chip used for filters (note labels, habit days).
struct Chip: View {
    let text: String
    var symbol: String?
    let on: Bool
    let action: () -> Void
    @Environment(\.scheme) private var scheme
    var body: some View {
        Button(action: action) {
            HStack(spacing: 4) {
                if let symbol { Image(systemName: symbol).font(.system(size: 10, weight: .semibold)) }
                Text(text)
            }
            .font(.system(size: 12, weight: .medium))
            .foregroundStyle(on ? scheme.onPrimaryContainer : .primary)
            .padding(.horizontal, 10).frame(height: 26)
            .background(RoundedRectangle(cornerRadius: Radius.small, style: .continuous).fill(on ? scheme.primaryContainer : .clear))
            .overlay(RoundedRectangle(cornerRadius: Radius.small, style: .continuous).strokeBorder(on ? .clear : scheme.cardStroke, lineWidth: 1))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}

/// Search field in the phone's style.
struct SearchField: View {
    let prompt: String
    @Binding var text: String
    @Environment(\.scheme) private var scheme
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
            TextField(prompt, text: $text).textFieldStyle(.plain)
            if !text.isEmpty {
                Button { text = "" } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(.secondary) }.buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 12).frame(height: 36)
        .background(RoundedRectangle(cornerRadius: Radius.inner, style: .continuous).fill(scheme.well))
    }
}
