import SwiftUI
import AppKit
import DaylineCore
import DaylineEditor

/// The note editor that's open, so menu commands (Format, Edit) can reach it.
@MainActor
final class EditorHub {
    static let shared = EditorHub()
    weak var current: NoteTextView?

    /// Runs a command on the open editor. Character formatting only applies while you're typing in the note.
    func run(needsFocus: Bool = false, _ f: (NoteTextView) -> Void) {
        guard let tv = current, tv.isEditable else { NSSound.beep(); return }
        if needsFocus, tv.window?.firstResponder !== tv { NSSound.beep(); return }
        tv.window?.makeFirstResponder(tv)
        f(tv)
    }
}

/// Lets the format bar talk to the text view and shows what's active.
@MainActor
final class RichTextController: ObservableObject {
    weak var textView: NoteTextView?
    @Published var state = FormatState()

    func run(_ f: (NoteTextView) -> Void) {
        guard let tv = textView, tv.isEditable else { return }
        tv.window?.makeFirstResponder(tv)
        f(tv)
        state = tv.formatState
    }
}

struct RichTextEditor: NSViewRepresentable {
    @Binding var html: String
    @ObservedObject var controller: RichTextController
    var editable = true
    var zoom: CGFloat = 1
    var onOpenNote: (String) -> Void = { _ in }
    @Environment(\.colorScheme) private var colorScheme
    @Environment(\.scheme) private var scheme

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeNSView(context: Context) -> NSScrollView {
        let scroll = NSScrollView()
        let tv = NoteTextView.make(style: NoteStyle(dark: colorScheme == .dark))
        // The text always fits the visible width (the view starts at zero size and follows the clip view).
        tv.frame = NSRect(origin: .zero, size: NSSize(width: max(1, scroll.contentSize.width), height: 1))
        scroll.documentView = tv
        scroll.contentView.postsFrameChangedNotifications = true
        context.coordinator.observe(scroll)
        scroll.automaticallyAdjustsContentInsets = false
        // Room to scroll past the end, and to keep the typing line clear of the floating format bar.
        scroll.contentInsets = NSEdgeInsets(top: 0, left: 0, bottom: 110, right: 0)
        tv.caretRoom = 100
        scroll.drawsBackground = false
        scroll.hasVerticalScroller = true
        scroll.autohidesScrollers = true
        scroll.allowsMagnification = true
        scroll.minMagnification = 0.6
        scroll.maxMagnification = 2.5
        tv.load(html: html)
        tv.delegate = context.coordinator
        tv.onStateChange = { [weak controller] s in controller?.state = s }
        tv.onOpenNote = { context.coordinator.parent.onOpenNote($0) }
        tv.isEditable = editable
        tv.checkAccent = NSColor(scheme.primary)
        context.coordinator.lastHTML = html
        controller.textView = tv
        EditorHub.shared.current = tv
        return scroll
    }

    func updateNSView(_ scroll: NSScrollView, context: Context) {
        guard let tv = scroll.documentView as? NoteTextView else { return }
        context.coordinator.parent = self
        controller.textView = tv
        EditorHub.shared.current = tv
        let style = NoteStyle(dark: colorScheme == .dark)
        if tv.style != style { tv.setStyle(style) }
        tv.checkAccent = NSColor(scheme.primary)
        if tv.isEditable != editable { tv.isEditable = editable }
        if abs(scroll.magnification - zoom) > 0.001 { scroll.magnification = zoom }
        let w = scroll.contentView.bounds.width
        if w > 1, abs(tv.frame.width - w) > 0.5 { tv.setFrameSize(NSSize(width: w, height: tv.frame.height)) }
        if html != context.coordinator.lastHTML {
            context.coordinator.lastHTML = html
            tv.load(html: html)
        }
    }

    static func dismantleNSView(_ scroll: NSScrollView, coordinator: Coordinator) {
        if let tv = scroll.documentView as? NoteTextView, EditorHub.shared.current === tv { EditorHub.shared.current = nil }
    }

    final class Coordinator: NSObject, NSTextViewDelegate {
        var parent: RichTextEditor
        var lastHTML = ""
        init(_ p: RichTextEditor) { parent = p }
        var token: NSObjectProtocol?
        deinit { if let token { NotificationCenter.default.removeObserver(token) } }
        func observe(_ scroll: NSScrollView) {
            token = NotificationCenter.default.addObserver(forName: NSView.frameDidChangeNotification, object: scroll.contentView, queue: .main) { [weak scroll] _ in
                MainActor.assumeIsolated {
                    guard let scroll, let tv = scroll.documentView as? NSTextView else { return }
                    let w = scroll.contentView.bounds.width
                    if abs(tv.frame.width - w) > 0.5 { tv.setFrameSize(NSSize(width: w, height: tv.frame.height)) }
                }
            }
        }
        func textDidChange(_ n: Notification) {
            guard let tv = n.object as? NoteTextView else { return }
            let h = tv.html
            guard h != lastHTML else { return }
            lastHTML = h
            parent.html = h
        }
        func textView(_ textView: NSTextView, clickedOnLink link: Any, at charIndex: Int) -> Bool {
            if let url = (link as? URL) ?? (link as? String).flatMap(URL.init(string:)), ["http", "https", "mailto"].contains(url.scheme?.lowercased() ?? "") {
                NSWorkspace.shared.open(url); return true
            }
            return true   // anything else (javascript:, file:) is never opened
        }
    }
}

/// The floating formatting bar under a note.
struct FormatBar: View {
    @ObservedObject var controller: RichTextController
    @State private var colors = false
    @State private var highlights = false
    @State private var linking = false
    @State private var url = ""
    @Environment(\.scheme) private var scheme

    var s: FormatState { controller.state }

    var body: some View {
        // Narrow windows (three panes and the info panel) get the compact bar instead of pushing the window wider.
        ViewThatFits(in: .horizontal) {
            full
            compact
        }
        .font(.system(size: 13))
    }

    var compact: some View {
        HStack(spacing: 1) {
            headingMenu
            sep
            tool("bold", "Bold (⌘B)", s.bold) { $0.toggleTrait(.boldFontMask) }
            tool("italic", "Italic (⌘I)", s.italic) { $0.toggleTrait(.italicFontMask) }
            tool("underline", "Underline (⌘U)", s.underline) { $0.toggleLine(.underlineStyle) }
            colourButton
            highlightButton
            sep
            Menu {
                Button("Strikethrough") { controller.run { $0.toggleLine(.strikethroughStyle) } }
                Divider()
                Button("Bulleted List") { controller.run { $0.toggleList(ordered: false) } }
                Button("Numbered List") { controller.run { $0.toggleList(ordered: true) } }
                Button("Checklist") { controller.run { $0.toggleChecklist() } }
                Divider()
                Button("Quote") { controller.run { $0.toggleBlock("quote") } }
                Menu("Callout") { ForEach(NoteFormat.calloutKinds, id: \.self) { k in Button(k.capitalized) { controller.run { $0.toggleBlock("callout:" + k) } } } }
                Button("Code Block") { controller.run { $0.toggleBlock("code:") } }
                Button("Table") { controller.run { $0.insertTable(rows: 3, columns: 3) } }
                Button("Line Separator") { controller.run { $0.insertDivider() } }
                Divider()
                Button("Link…") { controller.run { LinkPrompt.run($0) } }
            } label: { Image(systemName: "plus.circle") }
                .menuStyle(.borderlessButton).menuIndicator(.hidden).fixedSize().help("Lists, blocks and links").frame(width: 26)
            moreMenu
        }
    }

    var headingMenu: some View {
        Menu {
            Button("Body Text") { controller.run { $0.setHeading(0) } }
            ForEach(1...6, id: \.self) { l in Button("Heading \(l)") { controller.run { $0.setHeading(l) } } }
        } label: {
            Text(s.heading > 0 ? "H\(s.heading)" : "Aa").font(.system(size: 13, weight: .semibold)).frame(minWidth: 26)
        }
        .menuStyle(.borderlessButton).menuIndicator(.hidden).fixedSize().help("Heading level")
    }

    var full: some View {
        HStack(spacing: 1) {
            Menu {
                Button("Body Text") { controller.run { $0.setHeading(0) } }
                ForEach(1...6, id: \.self) { l in Button("Heading \(l)") { controller.run { $0.setHeading(l) } } }
            } label: {
                Text(s.heading > 0 ? "H\(s.heading)" : "Aa").font(.system(size: 13, weight: .semibold)).frame(minWidth: 26)
            }
            .menuStyle(.borderlessButton).menuIndicator(.hidden).fixedSize().help("Heading level")
            sep
            tool("bold", "Bold (⌘B)", s.bold) { $0.toggleTrait(.boldFontMask) }
            tool("italic", "Italic (⌘I)", s.italic) { $0.toggleTrait(.italicFontMask) }
            tool("underline", "Underline (⌘U)", s.underline) { $0.toggleLine(.underlineStyle) }
            tool("strikethrough", "Strikethrough (⇧⌘E)", s.strike) { $0.toggleLine(.strikethroughStyle) }
            colourButton
            highlightButton
            sep
            tool("list.bullet", "Bulleted list (⌘L)", s.list == "ul") { $0.toggleList(ordered: false) }
            tool("list.number", "Numbered list (⇧⌘L)", s.list == "ol") { $0.toggleList(ordered: true) }
            tool("checklist", "Checklist (⌘T) — click a box to tick it", s.checklist) { $0.toggleChecklist() }
            sep
            tool("text.quote", "Quote (⇧⌘T)", s.block == "quote") { $0.toggleBlock("quote") }
            Menu {
                ForEach(NoteFormat.calloutKinds, id: \.self) { k in
                    Button(k.capitalized) { controller.run { $0.toggleBlock("callout:" + k) } }
                }
            } label: { Image(systemName: "exclamationmark.bubble") }
                .menuStyle(.borderlessButton).menuIndicator(.hidden).fixedSize().help("Callout").frame(width: 26)
                .background(active(s.block?.hasPrefix("callout") == true))
            tool("chevron.left.forwardslash.chevron.right", "Code block (⌥⌘C)", s.block?.hasPrefix("code") == true) { $0.toggleBlock("code:") }
            Menu {
                if s.inTable {
                    Button("Add Row") { controller.run { $0.editTable(.addRow) } }
                    Button("Add Column") { controller.run { $0.editTable(.addColumn) } }
                    Button("Delete Row") { controller.run { $0.editTable(.deleteRow) } }
                    Button("Delete Column") { controller.run { $0.editTable(.deleteColumn) } }
                    Button("Header Row On/Off") { controller.run { $0.editTable(.toggleHeader) } }
                    Divider()
                    Button("Delete Table", role: .destructive) { controller.run { $0.editTable(.delete) } }
                } else {
                    ForEach([(2, 2), (3, 3), (4, 3), (3, 5)], id: \.0) { r, c in
                        Button("\(c) × \(r) Table") { controller.run { $0.insertTable(rows: r, columns: c) } }
                    }
                }
            } label: { Image(systemName: "tablecells") }
                .menuStyle(.borderlessButton).menuIndicator(.hidden).fixedSize().help("Table").frame(width: 26)
                .background(active(s.inTable))
            tool("minus", "Line separator (⌥⌘S)", false) { $0.insertDivider() }
            sep
            Button { url = s.link ?? ""; linking = true } label: { Image(systemName: "link").frame(width: 26, height: 26) }
                .buttonStyle(.borderless).help("Link (⌘K)").background(active(s.link != nil))
                .popover(isPresented: $linking, arrowEdge: .top) { linkEditor }
            moreMenu
        }
    }

    var colourButton: some View {
        Button { colors.toggle() } label: {
            VStack(spacing: 1) {
                Image(systemName: "character").font(.system(size: 12, weight: .semibold))
                RoundedRectangle(cornerRadius: 1).fill(s.color.map(Color.init(nsColor:)) ?? Color.primary).frame(width: 14, height: 3)
            }.frame(width: 26, height: 26)
        }
        .buttonStyle(.borderless).help("Text colour")
        .popover(isPresented: $colors, arrowEdge: .top) {
            SwatchGrid(title: "Text colour", colors: NoteStyle.textColors, selected: s.color, noneLabel: "Default") { c in
                controller.run { $0.setTextColor(c) }; colors = false
            }
        }
    }

    var highlightButton: some View {
        Button { highlights.toggle() } label: {
            Image(systemName: "highlighter").font(.system(size: 13)).foregroundStyle(s.highlight.map(Color.init(nsColor:)) ?? Color.primary).frame(width: 26, height: 26)
        }
        .buttonStyle(.borderless).help("Highlighter (⌃⌘H)")
        .popover(isPresented: $highlights, arrowEdge: .top) {
            SwatchGrid(title: "Highlighter", colors: NoteStyle.highlightColors, selected: s.highlight, noneLabel: "None") { c in
                controller.run { $0.setHighlight(c) }; highlights = false
            }
        }
    }

    var moreMenu: some View {
        Menu {
            Button("Inline Code") { controller.run { $0.toggleInlineCode() } }
            Button("Link to Another Note") { controller.run { $0.insertWikiLink() } }
            Button("Insert Date") { controller.run { $0.insertPlain(Date().formatted(date: .long, time: .omitted)) } }
            Divider()
            Button("Indent") { controller.run { $0.indent(1) } }
            Button("Outdent") { controller.run { $0.indent(-1) } }
            Divider()
            Button("Align Left") { controller.run { $0.setAlignment(.natural) } }
            Button("Centre") { controller.run { $0.setAlignment(.center) } }
            Button("Align Right") { controller.run { $0.setAlignment(.right) } }
            Divider()
            Button("Move Line Up") { controller.run { $0.moveLines(up: true) } }
            Button("Move Line Down") { controller.run { $0.moveLines(up: false) } }
            Divider()
            Button("Clear Formatting") { controller.run { $0.clearFormatting() } }
        } label: { Image(systemName: "ellipsis.circle") }
            .menuStyle(.borderlessButton).menuIndicator(.hidden).fixedSize().help("More formatting").frame(width: 26)
    }

    var sep: some View { Divider().frame(height: 16).padding(.horizontal, 4) }

    func active(_ on: Bool) -> some View {
        RoundedRectangle(cornerRadius: 6, style: .continuous).fill(on ? scheme.primary.opacity(0.18) : .clear)
    }

    func tool(_ symbol: String, _ help: String, _ on: Bool, _ f: @escaping (NoteTextView) -> Void) -> some View {
        Button { controller.run(f) } label: {
            Image(systemName: symbol).frame(width: 26, height: 26).foregroundStyle(on ? scheme.primary : Color.primary)
        }
        .buttonStyle(.borderless).help(help)
        .background(active(on))
    }

    var linkEditor: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Link").font(.system(size: 12, weight: .semibold)).foregroundStyle(.secondary)
            TextField("https://", text: $url).textFieldStyle(.roundedBorder).frame(width: 260).onSubmit(applyLink)
            HStack {
                if s.link != nil { Button("Remove Link") { controller.run { $0.setLink(nil) }; linking = false } }
                Spacer()
                Button("Cancel") { linking = false }
                Button("Apply", action: applyLink).keyboardShortcut(.defaultAction).disabled(NoteLinks.url(url) == nil)
            }
        }
        .padding(14)
    }

    func applyLink() {
        guard let u = NoteLinks.url(url) else { return }
        controller.run { $0.setLink(u) }
        linking = false
    }
}

enum NoteLinks {
    /// A web address from what was typed ("dayline.app" → https://dayline.app). Only web and mail links.
    static func url(_ s0: String) -> URL? {
        let s = s0.trimmingCharacters(in: .whitespaces)
        guard !s.isEmpty, !s.contains(" ") else { return nil }
        // "javascript:…", "file:…" and other schemes are never links ("localhost:8080" is a host and port).
        if let m = s.range(of: "^[a-zA-Z][a-zA-Z0-9+.-]*:(?![0-9])", options: .regularExpression),
           !["http:", "https:", "mailto:"].contains(s[m].lowercased()) { return nil }
        let withScheme = s.contains("://") || s.hasPrefix("mailto:") ? s : "https://" + s
        guard let u = URL(string: withScheme), let scheme = u.scheme?.lowercased(), ["http", "https", "mailto"].contains(scheme) else { return nil }
        if scheme != "mailto", (u.host ?? "").isEmpty { return nil }
        return u
    }
}

/// A grid of colour swatches with a "none" choice.
struct SwatchGrid: View {
    let title: String
    let colors: [NSColor]
    let selected: NSColor?
    let noneLabel: String
    let pick: (NSColor?) -> Void
    @Environment(\.scheme) private var scheme

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(title).font(.system(size: 12, weight: .semibold)).foregroundStyle(.secondary)
            LazyVGrid(columns: Array(repeating: GridItem(.fixed(30), spacing: 8), count: 5), spacing: 8) {
                Button { pick(nil) } label: {
                    ZStack {
                        Circle().strokeBorder(Color.primary.opacity(0.3), lineWidth: 1)
                        Image(systemName: "slash.circle").font(.system(size: 14)).foregroundStyle(.secondary)
                    }
                    .frame(width: 26, height: 26)
                    .overlay(Circle().strokeBorder(selected == nil ? scheme.primary : .clear, lineWidth: 2).padding(-3))
                }
                .buttonStyle(.plain).help(noneLabel)
                ForEach(colors.indices, id: \.self) { i in
                    let c = colors[i]
                    Button { pick(c) } label: {
                        Circle().fill(Color(nsColor: c)).frame(width: 26, height: 26)
                            .overlay(Circle().strokeBorder(Color.primary.opacity(0.15), lineWidth: 1))
                            .overlay(Circle().strokeBorder(selected.map { $0.isClose(to: c) } == true ? scheme.primary : .clear, lineWidth: 2).padding(-3))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(14)
    }
}
