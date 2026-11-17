import SwiftUI
import AppKit
import DaylineCore

/// Which note is open (shared so Search and "New Note" can open one).
@MainActor
final class NotesUI: ObservableObject {
    /// nil = nothing open, 0 = a new, not yet saved note.
    @Published var selected: Int64?
    /// Changes when a different note is opened (not when the open note gets its id on first save).
    @Published var token = UUID()

    func open(_ id: Int64) { selected = id; token = UUID() }
    func newNote() { selected = 0; token = UUID() }
}

/// Notes in two panes: the phone's note cards (search, labels, pinned first) and an editor beside them.
struct NotesView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var ui: NotesUI
    @State private var label: String?
    @State private var query = ""

    var body: some View {
        HStack(spacing: 0) {
            listPane
                .frame(width: 350)
            Divider()
            Group {
                if let id = ui.selected {
                    NotePane(noteId: id, presetLabel: label).id(ui.token)
                } else {
                    EmptyState(art: .notebook, title: store.noteCount == 0 ? "Your notebook is empty" : "Pick a note",
                               message: "Write with headings, lists and checklists. Add #labels to keep things tidy — they sync with your phone.",
                               action: ("New Note", { ui.newNote() }))
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .navigationTitle("Notes")
    }

    var notes: [Note] {
        let q = query.trimmingCharacters(in: .whitespaces)
        return store.data.notes
            .filter { !store.isJournal($0) }
            .filter { label == nil || store.labels(of: $0).contains(label!) }
            .filter { q.isEmpty || $0.title.localizedCaseInsensitiveContains(q) || NoteCodec.plainText($0.body).localizedCaseInsensitiveContains(q) }
            .sorted { $0.updatedAt > $1.updatedAt }
    }

    var listPane: some View {
        let shown = notes
        let pinned = shown.filter(\.isPinned), others = shown.filter { !$0.isPinned }
        return ScrollView {
            LazyVStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .bottom) {
                    ScreenTitle(title: "Notes", subtitle: "\(store.noteCount) note\(store.noteCount == 1 ? "" : "s")")
                    Spacer()
                    IconButton(symbol: "square.and.pencil", help: "New note (⌥⌘N)", size: 30) { ui.newNote() }
                }
                .padding(.bottom, 4)
                SearchField(prompt: "Search your notes", text: $query)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        Chip(text: "All", on: label == nil) { label = nil }
                        ForEach(store.allNoteLabels, id: \.self) { l in Chip(text: l, symbol: "tag", on: label == l) { label = l } }
                    }
                }
                .padding(.bottom, 4)
                if !pinned.isEmpty {
                    group("PINNED")
                    ForEach(pinned) { n in row(n) }
                }
                if !others.isEmpty {
                    if !pinned.isEmpty { group("OTHERS") }
                    ForEach(others) { n in row(n) }
                }
                if shown.isEmpty { Text(store.noteCount == 0 ? "No notes yet." : "No matching notes.").foregroundStyle(.secondary).padding(.top, 20) }
            }
            .padding(.leading, Page.h).padding(.trailing, 16).padding(.top, Page.top).padding(.bottom, 16)
        }
    }

    func group(_ title: String) -> some View {
        Text(title).font(.system(size: 11, weight: .semibold)).foregroundStyle(.secondary).padding(.top, 8).padding(.leading, 2)
    }

    func row(_ n: Note) -> some View {
        NoteCard(note: n, selected: ui.selected == n.id)
            .onTapGesture { ui.open(n.id) }
            .contextMenu {
                Button(n.isPinned ? "Unpin" : "Pin") { store.setPinned(note: n.id, !n.isPinned) }
                Menu("Colour") {
                    NoteColorItems(selected: n.color) { c in var m = n; m.color = c; store.save(m) }
                }
                Divider()
                Button("Delete", role: .destructive) { if ui.selected == n.id { ui.selected = nil }; store.deleteNote(n.id) }
            }
    }
}

/// A note as a card in its colour, like the phone.
struct NoteCard: View {
    @EnvironmentObject var store: Store
    @Environment(\.scheme) private var scheme
    let note: Note
    let selected: Bool
    var body: some View {
        let text = NoteCodec.plainText(note.body)
        let lines = text.split(separator: "\n", omittingEmptySubsequences: true).map(String.init)
        let title = note.title.isEmpty ? (lines.first ?? "Untitled") : note.title
        let preview = (note.title.isEmpty ? Array(lines.dropFirst()) : lines).prefix(3).joined(separator: "\n")
        let fill = NoteColors.color(note.color)
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                Text(title).font(.system(size: 14, weight: .semibold)).lineLimit(2)
                Spacer()
                if note.isPinned { Image(systemName: "pin.fill").font(.system(size: 10)).foregroundStyle(.secondary) }
            }
            let labels = store.labels(of: note)
            if !labels.isEmpty {
                HStack(spacing: 4) {
                    ForEach(labels.prefix(3), id: \.self) { l in
                        Label(l, systemImage: "tag").labelStyle(CompactLabel()).font(.system(size: 10.5, weight: .medium))
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(RoundedRectangle(cornerRadius: 4).fill(Color.primary.opacity(0.07)))
                    }
                }
            }
            if !preview.isEmpty { Text(preview).font(.system(size: 12.5)).foregroundStyle(.secondary).lineLimit(3) }
            Text(note.updatedAt.formatted(.relative(presentation: .named))).font(.system(size: 10.5)).foregroundStyle(.tertiary)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .surface(fill: fill ?? scheme.card)
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).strokeBorder(selected ? Color.accentColor : .clear, lineWidth: 2))
        .contentShape(Rectangle())
    }
}

/// The note editor: a centred writing column and a floating format bar.
struct NotePane: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var ui: NotesUI
    let noteId: Int64
    var presetLabel: String?
    /// Journal entries: dated header, bookmark instead of pin, no colours or labels.
    var journal = false
    var promptTitle: String?
    @State private var note = Note(id: 0, title: "")
    @State private var html = ""
    @State private var checklist: [CheckItem] = []
    @State private var media: [NoteMedia] = []
    @State private var labels: [String] = []
    @State private var newLabel = ""
    @State private var newItem = ""
    @State private var loaded = false
    @State private var words = 0
    @State private var pendingSave: Task<Void, Never>?
    @StateObject private var controller = RichTextController()
    @Environment(\.scheme) private var scheme

    var body: some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 8) {
                        Text(meta).font(.system(size: 12)).foregroundStyle(.tertiary)
                        Spacer()
                        if !journal {
                            Menu {
                                NoteColorItems(selected: note.color) { c in note.color = c; changed() }
                            } label: { Image(systemName: "paintpalette") }.menuStyle(.borderlessButton).fixedSize().help("Colour")
                        }
                        Button { note.isPinned.toggle(); changed() } label: {
                            Image(systemName: journal ? (note.isPinned ? "bookmark.fill" : "bookmark") : (note.isPinned ? "pin.fill" : "pin"))
                        }.buttonStyle(.borderless).help(journal ? "Bookmark" : "Pin")
                        Button { checklist.append(CheckItem(text: "")); changed() } label: { Image(systemName: "checkmark.square") }.buttonStyle(.borderless).help("Add a checklist")
                        if note.id != 0 {
                            Button(role: .destructive) { let id = note.id; ui.selected = nil; store.deleteNote(id) } label: { Image(systemName: "trash") }.buttonStyle(.borderless).help(journal ? "Delete entry" : "Delete note")
                        }
                    }
                    if journal {
                        Text(entryDate.formatted(.dateTime.weekday(.wide).day().month(.wide).year()))
                            .font(.system(size: 13, weight: .semibold)).foregroundStyle(scheme.primary)
                    }
                    TextField("Title", text: $note.title, axis: .vertical)
                        .font(.system(size: 28, weight: .bold))
                        .textFieldStyle(.plain)
                        .onChange(of: note.title) { _, _ in changed() }
                    if !journal { labelRow }
                    RichTextEditor(html: $html, controller: controller)
                        .frame(minHeight: 360)
                        .onChange(of: html) { _, _ in changed() }
                    if !checklist.isEmpty { checklistView }
                    if !media.isEmpty {
                        Label(media.map { m in m.type == .AUDIO ? "Voice note" : m.type == .DRAWING ? "Drawing" : "Image" }.joined(separator: ", ") + " — open on your phone to view",
                              systemImage: "photo.on.rectangle").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 90)
                }
                .padding(.horizontal, 48).padding(.top, 28)
                .frame(maxWidth: 760)
                .frame(maxWidth: .infinity)
            }
            FormatBar(controller: controller)
                .padding(.horizontal, 12).frame(height: 38)
                .surface(Radius.inner)
                .padding(.bottom, 18)
        }
        .background((journal ? nil : NoteColors.color(note.color)?.opacity(0.35)) ?? scheme.card)
        .onAppear(perform: load)
        .onDisappear { save() }
    }

    var entryDate: Date { note.id == 0 ? Date() : note.createdAt }

    var meta: String {
        let edited = note.id == 0 ? "New note" : "Edited " + note.updatedAt.formatted(.relative(presentation: .named))
        return "\(edited) · \(words) word\(words == 1 ? "" : "s")"
    }

    var labelRow: some View {
        HStack(spacing: 6) {
            ForEach(labels, id: \.self) { l in
                HStack(spacing: 3) {
                    Text("#" + l)
                    Button { labels.removeAll { $0 == l }; changed() } label: { Image(systemName: "xmark").font(.caption2) }.buttonStyle(.borderless)
                }
                .font(.system(size: 12, weight: .semibold))
                .padding(.horizontal, 8).padding(.vertical, 3)
                .background(RoundedRectangle(cornerRadius: Radius.small).fill(Color.primary.opacity(0.07)))
            }
            TextField("Add #label", text: $newLabel).textFieldStyle(.plain).font(.system(size: 12)).frame(width: 100).onSubmit {
                let l = newLabel.trimmingCharacters(in: .whitespaces).droppingPrefix("#").lowercased()
                if !l.isEmpty && !labels.contains(l) { labels.append(l); changed() }
                newLabel = ""
            }
        }
    }

    var checklistView: some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(checklist.indices, id: \.self) { i in
                HStack {
                    Toggle("", isOn: Binding(get: { checklist[i].done }, set: { checklist[i].done = $0; changed() })).toggleStyle(.checkbox).labelsHidden()
                    TextField("Item", text: Binding(get: { checklist[i].text }, set: { checklist[i].text = $0; changed() })).textFieldStyle(.plain).strikethrough(checklist[i].done)
                    Button { checklist.remove(at: i); changed() } label: { Image(systemName: "xmark") }.buttonStyle(.borderless).foregroundStyle(.secondary)
                }
            }
            HStack {
                Image(systemName: "plus").foregroundStyle(.secondary)
                TextField("List item", text: $newItem).textFieldStyle(.plain).onSubmit {
                    if !newItem.isEmpty { checklist.append(CheckItem(text: newItem)); newItem = ""; changed() }
                }
            }
        }
        .padding(14)
        .background(RoundedRectangle(cornerRadius: Radius.inner, style: .continuous).fill(Color.primary.opacity(0.04)))
    }

    func load() {
        guard !loaded else { return }
        loaded = true
        if noteId != 0, let n = store.note(noteId) { note = n }
        let d = NoteCodec.decode(note.body)
        html = d.html; checklist = d.checklist; media = d.media
        labels = store.labels(of: note)
        countWords()
        if noteId == 0, let presetLabel, presetLabel != "__pinned" { labels = [presetLabel] }
        if noteId == 0, journal, let promptTitle { note.title = promptTitle }
    }

    /// Saves shortly after you stop typing (so sync picks it up), and when you leave.
    func changed() {
        guard loaded else { return }
        pendingSave?.cancel()
        pendingSave = Task { try? await Task.sleep(for: .milliseconds(700)); if !Task.isCancelled { countWords(); save() } }
    }

    func countWords() {
        words = NoteCodec.plainText(NoteCodec.encode(html, checklist: checklist)).split(whereSeparator: \.isWhitespace).count
    }

    func save() {
        pendingSave?.cancel()
        // Deleted (here, from the list, or by sync) while open: don't bring it back.
        if note.id != 0 && store.note(note.id) == nil { return }
        var n = note
        n.body = NoteCodec.encode(html, checklist: checklist, media: media)
        var all = labels
        for l in NoteCodec.labelsOf(title: n.title, body: n.body) where !all.contains(l) { all.append(l) }
        n.category = all.isEmpty ? nil : all.joined(separator: ",")
        if n.id != 0, let current = store.note(n.id), current.title == n.title, current.body == n.body, current.category == n.category,
           current.color == n.color, current.isPinned == n.isPinned { return }
        let id = store.save(n)
        if id != 0 {
            note = store.note(id) ?? note
            if ui.selected == 0 || ui.selected == noteId { ui.selected = id }
        }
    }
}

/// Menu items for note colours, each with a swatch of the colour in front of its name.
struct NoteColorItems: View {
    let selected: Int32
    let apply: (Int32) -> Void
    var body: some View {
        Button { apply(0) } label: { Label { Text(selected == 0 ? "Default ✓" : "Default") } icon: { Image(nsImage: Swatch.image(nil)) } }
        ForEach(NoteColors.all) { sw in
            Button { apply(sw.key) } label: {
                Label { Text(selected == sw.key ? "\(sw.name) ✓" : sw.name) } icon: { Image(nsImage: Swatch.image(sw)) }
            }
        }
    }
}

/// Small round colour swatch for menus (menus need real images; SF Symbols there turn monochrome).
enum Swatch {
    @MainActor private static var cache: [Int32: NSImage] = [:]
    @MainActor static func image(_ sw: NoteColors.Swatch?) -> NSImage {
        let key = sw?.key ?? 0
        if let i = cache[key] { return i }
        let light = sw.map { NSColor(Color(hex: $0.light)) }, dark = sw.map { NSColor(Color(hex: $0.dark)) }
        let img = NSImage(size: NSSize(width: 14, height: 14), flipped: false) { rect in
            let isDark = NSAppearance.currentDrawing().bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
            let circle = NSBezierPath(ovalIn: rect.insetBy(dx: 1, dy: 1))
            if let fill = isDark ? dark : light { fill.setFill(); circle.fill() }
            NSColor.secondaryLabelColor.withAlphaComponent(0.5).setStroke()
            circle.lineWidth = 1
            circle.stroke()
            return true
        }
        img.isTemplate = false
        cache[key] = img
        return img
    }
}
