import SwiftUI
import AppKit

/// Corner radii that nest like macOS Tahoe's concentric corners.
enum Radius {
    static let panel: CGFloat = 26
    static let card: CGFloat = 22
    static let inner: CGFloat = 14
    static let small: CGFloat = 10
}

/// Liquid Glass. On macOS 26 (Tahoe) this is Apple's real glass material; on macOS 14–15 it's a close
/// stand-in: a frosted material with a soft light edge and a gentle shadow.
struct GlassBackground: ViewModifier {
    var cornerRadius: CGFloat = Radius.card
    var tint: Color?
    var interactive = false
    @Environment(\.colorScheme) private var scheme

    func body(content: Content) -> some View {
        if #available(macOS 26.0, *) {
            content.glassEffect(glass, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
        } else {
            let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
            content
                .background {
                    ZStack {
                        shape.fill(.regularMaterial)
                        if let tint { shape.fill(tint.opacity(scheme == .dark ? 0.16 : 0.10)) }
                        shape.fill(LinearGradient(colors: [.white.opacity(scheme == .dark ? 0.06 : 0.35), .clear], startPoint: .top, endPoint: .center))
                    }
                }
                .overlay {
                    shape.strokeBorder(
                        LinearGradient(colors: [.white.opacity(scheme == .dark ? 0.22 : 0.85), .white.opacity(scheme == .dark ? 0.04 : 0.25), .black.opacity(scheme == .dark ? 0.25 : 0.06)],
                                       startPoint: .topLeading, endPoint: .bottomTrailing),
                        lineWidth: 1)
                }
                .shadow(color: .black.opacity(scheme == .dark ? 0.35 : 0.08), radius: 18, y: 8)
        }
    }

    @available(macOS 26.0, *)
    private var glass: Glass {
        var g = Glass.regular
        if let tint { g = g.tint(tint.opacity(0.35)) }
        if interactive { g = g.interactive() }
        return g
    }
}

extension View {
    func glass(_ cornerRadius: CGFloat = Radius.card, tint: Color? = nil, interactive: Bool = false) -> some View {
        modifier(GlassBackground(cornerRadius: cornerRadius, tint: tint, interactive: interactive))
    }

    /// A padded glass card.
    func glassCard(padding: CGFloat = 18, tint: Color? = nil) -> some View {
        self.padding(padding).frame(maxWidth: .infinity, alignment: .leading).glass(Radius.card, tint: tint)
    }

    /// Capsule-shaped glass, for toolbars and floating controls.
    func glassCapsule(tint: Color? = nil) -> some View {
        modifier(GlassBackground(cornerRadius: 999, tint: tint, interactive: true))
    }
}

/// Glass buttons: Apple's on Tahoe, a matching capsule elsewhere.
struct GlassButtonStyle: ButtonStyle {
    var prominent = false
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.system(size: 13, weight: .semibold))
            .padding(.horizontal, 14).padding(.vertical, 7)
            .foregroundStyle(prominent ? AnyShapeStyle(.white) : AnyShapeStyle(.primary))
            .background {
                if prominent { Capsule().fill(Color.accentColor.gradient) }
            }
            .glassCapsule(tint: prominent ? .accentColor : nil)
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .opacity(enabled ? 1 : 0.5)
            .animation(.snappy(duration: 0.18), value: configuration.isPressed)
    }
}

extension View {
    @ViewBuilder func glassButton(prominent: Bool = false) -> some View {
        if #available(macOS 26.0, *) {
            if prominent { self.buttonStyle(.glassProminent) } else { self.buttonStyle(.glass) }
        } else {
            self.buttonStyle(GlassButtonStyle(prominent: prominent))
        }
    }
}

/// A round icon button on glass.
struct GlassIconButton: View {
    let symbol: String
    var help: String = ""
    var size: CGFloat = 30
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: symbol).font(.system(size: size * 0.42, weight: .semibold))
                .frame(width: size, height: size)
                .contentShape(Circle())
        }
        .buttonStyle(.plain)
        .glass(size / 2, interactive: true)
        .help(help)
        .accessibilityLabel(help)
    }
}

/// Large rounded title used at the top of each screen.
struct ScreenTitle: View {
    let title: String
    var subtitle: String?
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.system(size: 30, weight: .bold, design: .rounded))
            if let subtitle, !subtitle.isEmpty { Text(subtitle).font(.system(size: 14, weight: .medium, design: .rounded)).foregroundStyle(.secondary) }
        }
    }
}
