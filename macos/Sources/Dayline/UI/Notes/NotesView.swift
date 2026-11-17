import SwiftUI
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

/// Notes in two panes, inspired by writing apps like Bear: a list with search and labels, and a calm editor.
struct NotesView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var ui: NotesUI
    @State private var label: String?
    @State private var query = ""

    var body: some View {
        HStack(spacing: 0) {
            listPane
                .frame(width: 310)
                .glass(Radius.panel)
                .padding([.vertical, .leading], 16)
            Group {
                if let id = ui.selected {
                    NotePane(noteId: id, presetLabel: label).id(ui.token)
                } else {
                    IllustratedEmpty(title: store.data.notes.isEmpty ? "Your notebook is empty" : "Pick a note", message: "Write with headings, lists and checklists. Add #labels to keep things tidy — they sync with your phone.") {
                        NotebookArt(color: Destination.notes.tint)
                    }
                    .overlay(alignment: .bottom) {
                        Button { ui.newNote() } label: { Label("New Note", systemImage: "square.and.pencil") }.glassButton(prominent: true).controlSize(.large).padding(.bottom, 70)
                    }
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .navigationTitle("Notes")
    }

    var notes: [Note] {
        let q = query.trimmingCharacters(in: .whitespaces)
        return store.data.notes
            .filter { label == nil || (label == "__pinned" ? $0.isPinned : store.labels(of: $0).contains(label!)) }
            .filter { q.isEmpty || $0.title.localizedCaseInsensitiveContains(q) || NoteCodec.plainText($0.body).localizedCaseInsensitiveContains(q) }
            .sorted { a, b in a.isPinned != b.isPinned ? a.isPinned : a.updatedAt > b.updatedAt }
    }

    var listPane: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text("Notes").font(.system(size: 22, weight: .bold, design: .rounded))
                Text("\(store.data.notes.count)").font(.callout.monospacedDigit()).foregroundStyle(.secondary)
                Spacer()
                GlassIconButton(symbol: "square.and.pencil", help: "New note (⌥⌘N)", size: 30) { ui.newNote() }
            }
            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("Search notes", text: $query).textFieldStyle(.plain)
            }
            .padding(.horizontal, 10).padding(.vertical, 7)
            .background(RoundedRectangle(cornerRadius: Radius.small, style: .continuous).fill(Color.primary.opacity(0.06)))
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    chip("All", nil)
                    if store.data.notes.contains(where: \.isPinned) { chip("Pinned", "__pinned") }
                    ForEach(store.allNoteLabels, id: \.self) { l in chip("#" + l, l) }
                }
            }
            ScrollView {
                LazyVStack(spacing: 4) {
                    ForEach(notes) { n in
                        NoteRow(note: n, selected: ui.selected == n.id)
                            .onTapGesture { ui.open(n.id) }
                            .contextMenu {
                                Button(n.isPinned ? "Unpin" : "Pin") { store.setPinned(note: n.id, !n.isPinned) }
                                Menu("Colour") {
                                    Button("Default") { var m = n; m.color = 0; store.save(m) }
                                    ForEach(NoteColors.all) { sw in Button(sw.name) { var m = n; m.color = sw.key; store.save(m) } }
                                }
                                Divider()
                                Button("Delete", role: .destructive) { if ui.selected == n.id { ui.selected = nil }; store.deleteNote(n.id) }
                            }
                    }
                    if notes.isEmpty { Text(store.data.notes.isEmpty ? "No notes yet." : "No matching notes.").foregroundStyle(.secondary).padding(.top, 20) }
                }
            }
        }
        .padding(14)
    }

    func chip(_ text: String, _ value: String?) -> some View {
        let on = label == value
        return Button { label = value } label: {
            Text(text).font(.system(size: 12, weight: .semibold, design: .rounded)).padding(.horizontal, 10).padding(.vertical, 4)
                .foregroundStyle(on ? .white : .primary)
                .background(Capsule().fill(on ? Destination.notes.tint : Color.primary.opacity(0.06)))
        }.buttonStyle(.plain)
    }
}

struct NoteRow: View {
    @EnvironmentObject var store: Store
    let note: Note
    let selected: Bool
    var body: some View {
        let text = NoteCodec.plainText(note.body)
        let firstLine = text.split(separator: "\n").first.map(String.init) ?? ""
        let title = note.title.isEmpty ? (firstLine.isEmpty ? "Untitled" : firstLine) : note.title
        let preview = note.title.isEmpty ? text.split(separator: "\n").dropFirst().joined(separator: " ") : text.replacingOccurrences(of: "\n", with: " ")
        HStack(alignment: .top, spacing: 8) {
            if note.color != 0, let c = NoteColors.color(note.color) { RoundedRectangle(cornerRadius: 2).fill(c).frame(width: 4) }
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(title).font(.system(size: 14, weight: .semibold)).lineLimit(1)
                    Spacer()
                    if note.isPinned { Image(systemName: "pin.fill").font(.caption2).foregroundStyle(.secondary) }
                }
                if !preview.isEmpty { Text(preview).font(.system(size: 12.5)).foregroundStyle(.secondary).lineLimit(2) }
                HStack(spacing: 6) {
                    Text(note.updatedAt.formatted(.relative(presentation: .named))).foregroundStyle(.tertiary)
                    let labels = store.labels(of: note)
                    if !labels.isEmpty { Text(labels.prefix(3).map { "#" + $0 }.joined(separator: " ")).foregroundStyle(Destination.notes.tint).lineLimit(1) }
                }
                .font(.system(size: 11))
            }
        }
        .padding(10)
        .background(RoundedRectangle(cornerRadius: Radius.inner, style: .continuous).fill(selected ? Destination.notes.tint.opacity(0.18) : Color.clear))
        .contentShape(Rectangle())
    }
}

/// The note editor: a centred writing column, a floating glass format bar.
struct NotePane: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var ui: NotesUI
    let noteId: Int64
    var presetLabel: String?
    @State private var note = Note(id: 0, title: "")
    @State private var html = ""
    @State private var checklist: [CheckItem] = []
    @State private var media: [NoteMedia] = []
    @State private var labels: [String] = []
    @State private var newLabel = ""
    @State private var newItem = ""
    @State private var loaded = false
    @State private var pendingSave: Task<Void, Never>?
    @StateObject private var controller = RichTextController()

    var body: some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    HStack(spacing: 8) {
                        Text(meta).font(.system(size: 12)).foregroundStyle(.tertiary)
                        Spacer()
                        Menu {
                            Button("Default") { note.color = 0; changed() }
                            ForEach(NoteColors.all) { sw in Button(sw.name) { note.color = sw.key; changed() } }
                        } label: { Image(systemName: "paintpalette") }.menuStyle(.borderlessButton).fixedSize().help("Colour")
                        Button { note.isPinned.toggle(); changed() } label: { Image(systemName: note.isPinned ? "pin.fill" : "pin") }.buttonStyle(.borderless).help("Pin")
                        Button { checklist.append(CheckItem(text: "")); changed() } label: { Image(systemName: "checkmark.square") }.buttonStyle(.borderless).help("Add a checklist")
                        if note.id != 0 {
                            Button(role: .destructive) { let id = note.id; ui.selected = nil; store.deleteNote(id) } label: { Image(systemName: "trash") }.buttonStyle(.borderless).help("Delete note")
                        }
                    }
                    TextField("Title", text: $note.title, axis: .vertical)
                        .font(.system(size: 30, weight: .bold))
                        .textFieldStyle(.plain)
                        .onChange(of: note.title) { _, _ in changed() }
                    labelRow
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
                .padding(.horizontal, 16).padding(.vertical, 9)
                .glassCapsule()
                .padding(.bottom, 20)
        }
        .background(NoteColors.color(note.color)?.opacity(0.28) ?? Color.clear)
        .onAppear(perform: load)
        .onDisappear { save() }
    }

    var meta: String {
        let words = NoteCodec.plainText(NoteCodec.encode(html, checklist: checklist)).split(whereSeparator: \.isWhitespace).count
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
                .foregroundStyle(Destination.notes.tint)
                .background(Capsule().fill(Destination.notes.tint.opacity(0.14)))
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
        if noteId == 0, let presetLabel, presetLabel != "__pinned" { labels = [presetLabel] }
    }

    /// Saves shortly after you stop typing (so sync picks it up), and when you leave.
    func changed() {
        guard loaded else { return }
        pendingSave?.cancel()
        pendingSave = Task { try? await Task.sleep(for: .milliseconds(700)); if !Task.isCancelled { save() } }
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
