import SwiftUI
import DaylineCore

/// Which note is open (shared so Search can open one).
@MainActor
final class NotesUI: ObservableObject {
    @Published var editing: Int64?
}

struct NotesView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var ui: NotesUI
    @State private var label: String?
    @State private var query = ""

    var body: some View {
        let notes = filtered
        let pinned = notes.filter(\.isPinned), others = notes.filter { !$0.isPinned }
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                TextField("Filter notes", text: $query).textFieldStyle(.roundedBorder).frame(maxWidth: 260)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        chip("All", selected: label == nil) { label = nil }
                        ForEach(store.allNoteLabels, id: \.self) { l in chip("#" + l, selected: label == l) { label = label == l ? nil : l } }
                    }
                }
            }
            .padding(.horizontal, 20).padding(.vertical, 10)
            if notes.isEmpty {
                EmptyState(symbol: "note.text", title: store.data.notes.isEmpty ? "No notes yet" : "No matching notes",
                           message: store.data.notes.isEmpty ? "Notes sync with the phone app. Use headings, checklists and #labels." : "")
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 14) {
                        if !pinned.isEmpty {
                            Text("Pinned").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                            grid(pinned)
                            if !others.isEmpty { Text("Others").font(.caption.weight(.semibold)).foregroundStyle(.secondary).padding(.top, 6) }
                        }
                        grid(others)
                    }
                    .padding(20)
                }
            }
        }
        .navigationTitle("Notes")
        .toolbar { Button { ui.editing = 0 } label: { Label("New Note", systemImage: "square.and.pencil") }.keyboardShortcut("n", modifiers: [.command, .option]) }
        .sheet(item: Binding(get: { ui.editing.map(IdBox.init) }, set: { ui.editing = $0?.id })) { b in
            NoteEditor(note: b.id == 0 ? Note(id: 0, title: "") : (store.note(b.id) ?? Note(id: 0, title: "")), presetLabel: label)
        }
    }

    var filtered: [Note] {
        let q = query.trimmingCharacters(in: .whitespaces)
        return store.data.notes
            .filter { label == nil || store.labels(of: $0).contains(label!) }
            .filter { q.isEmpty || $0.title.localizedCaseInsensitiveContains(q) || NoteCodec.plainText($0.body).localizedCaseInsensitiveContains(q) }
            .sorted { $0.updatedAt > $1.updatedAt }
    }

    func chip(_ text: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(text).font(.callout).padding(.horizontal, 10).padding(.vertical, 4)
                .background(Capsule().fill(selected ? Color.accentColor : Color.primary.opacity(0.07)))
                .foregroundStyle(selected ? .white : .primary)
        }.buttonStyle(.plain)
    }

    func grid(_ notes: [Note]) -> some View {
        Masonry(columns: 3, spacing: 12) {
            ForEach(notes) { n in
                NoteCard(note: n)
                    .onTapGesture { ui.editing = n.id }
                    .contextMenu {
                        Button(n.isPinned ? "Unpin" : "Pin") { store.setPinned(note: n.id, !n.isPinned) }
                        Menu("Colour") {
                            Button("Default") { var m = n; m.color = 0; store.save(m) }
                            ForEach(NoteColors.all) { sw in Button(sw.name) { var m = n; m.color = sw.key; store.save(m) } }
                        }
                        Divider()
                        Button("Delete", role: .destructive) { store.deleteNote(n.id) }
                    }
            }
        }
    }
}

struct NoteCard: View {
    @EnvironmentObject var store: Store
    let note: Note
    var body: some View {
        let body = NoteCodec.decode(note.body)
        let text = NoteCodec.plainText(note.body)
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                if !note.title.isEmpty { Text(note.title).font(.headline).lineLimit(2) }
                Spacer()
                if note.isPinned { Image(systemName: "pin.fill").font(.caption).foregroundStyle(.secondary) }
            }
            if !text.isEmpty { Text(String(text.prefix(400))).font(.callout).foregroundStyle(.primary.opacity(0.8)).lineLimit(9) }
            if !body.media.isEmpty {
                Label("\(body.media.count) attachment\(body.media.count == 1 ? "" : "s") on your phone", systemImage: "photo.on.rectangle").font(.caption).foregroundStyle(.secondary)
            }
            let labels = store.labels(of: note)
            if !labels.isEmpty {
                Text(labels.map { "#" + $0 }.joined(separator: " ")).font(.caption).foregroundStyle(.tint).lineLimit(1)
            }
            Text(note.updatedAt.formatted(.relative(presentation: .named))).font(.caption2).foregroundStyle(.tertiary)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(NoteColors.color(note.color) ?? Color(nsColor: .controlBackgroundColor)))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(Color.primary.opacity(0.08)))
        .contentShape(Rectangle())
    }
}

struct NoteEditor: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) var dismiss
    @State var note: Note
    var presetLabel: String?
    @State private var html = ""
    @State private var checklist: [CheckItem] = []
    @State private var media: [NoteMedia] = []
    @State private var labels: [String] = []
    @State private var newLabel = ""
    @State private var newItem = ""
    @StateObject private var controller = RichTextController()
    @State private var loaded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                FormatBar(controller: controller)
                Spacer()
                Menu {
                    Button("Default") { note.color = 0 }
                    ForEach(NoteColors.all) { sw in Button(sw.name) { note.color = sw.key } }
                } label: { Image(systemName: "paintpalette") }.menuStyle(.borderlessButton).fixedSize().help("Colour")
                Button { note.isPinned.toggle() } label: { Image(systemName: note.isPinned ? "pin.fill" : "pin") }.buttonStyle(.borderless).help("Pin")
                Button { checklist.append(CheckItem(text: "")) } label: { Image(systemName: "checkmark.square") }.buttonStyle(.borderless).help("Add a checklist")
                if note.id != 0 {
                    Button(role: .destructive) { store.deleteNote(note.id); dismiss() } label: { Image(systemName: "trash") }.buttonStyle(.borderless).help("Delete note")
                }
            }
            .padding(.horizontal, 16).padding(.vertical, 10)
            Divider()
            VStack(alignment: .leading, spacing: 10) {
                TextField("Title", text: $note.title).font(.title2.weight(.semibold)).textFieldStyle(.plain)
                RichTextEditor(html: $html, controller: controller).frame(minHeight: 200)
                if !checklist.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        ForEach(checklist.indices, id: \.self) { i in
                            HStack {
                                Toggle("", isOn: $checklist[i].done).toggleStyle(.checkbox).labelsHidden()
                                TextField("Item", text: $checklist[i].text).textFieldStyle(.plain).strikethrough(checklist[i].done)
                                Button { checklist.remove(at: i) } label: { Image(systemName: "xmark") }.buttonStyle(.borderless).foregroundStyle(.secondary)
                            }
                        }
                        HStack {
                            Image(systemName: "plus").foregroundStyle(.secondary)
                            TextField("List item", text: $newItem).textFieldStyle(.plain).onSubmit {
                                if !newItem.isEmpty { checklist.append(CheckItem(text: newItem)); newItem = "" }
                            }
                        }
                    }
                }
                if !media.isEmpty {
                    Label(media.map { m in m.type == .AUDIO ? "Voice note" : m.type == .DRAWING ? "Drawing" : "Image" }.joined(separator: ", ") + " — open on your phone to view",
                          systemImage: "photo.on.rectangle").font(.caption).foregroundStyle(.secondary)
                }
                HStack {
                    ForEach(labels, id: \.self) { l in
                        HStack(spacing: 3) {
                            Text("#" + l)
                            Button { labels.removeAll { $0 == l } } label: { Image(systemName: "xmark").font(.caption2) }.buttonStyle(.borderless)
                        }
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .background(Capsule().fill(Color.accentColor.opacity(0.14)))
                    }
                    TextField("Add label", text: $newLabel).textFieldStyle(.plain).frame(width: 100).onSubmit {
                        let l = newLabel.trimmingCharacters(in: .whitespaces).droppingPrefix("#").lowercased()
                        if !l.isEmpty && !labels.contains(l) { labels.append(l) }
                        newLabel = ""
                    }
                    Spacer()
                    Text(note.id == 0 ? "New note" : "Edited \(note.updatedAt.formatted(.relative(presentation: .named)))").font(.caption).foregroundStyle(.tertiary)
                    Button("Done") { save(); dismiss() }.keyboardShortcut(.defaultAction)
                }
            }
            .padding(16)
        }
        .frame(width: 640, height: 600)
        .background(NoteColors.color(note.color)?.opacity(0.35) ?? Color.clear)
        .onAppear {
            guard !loaded else { return }
            loaded = true
            let d = NoteCodec.decode(note.body)
            html = d.html; checklist = d.checklist; media = d.media
            labels = store.labels(of: note)
            if note.id == 0, let presetLabel { labels = [presetLabel] }
        }
        .onDisappear { save() }
    }

    func save() {
        var n = note
        n.body = NoteCodec.encode(html, checklist: checklist, media: media)
        // #words typed in the text become labels too (like the phone).
        var all = labels
        for l in NoteCodec.labelsOf(title: n.title, body: n.body) where !all.contains(l) { all.append(l) }
        n.category = all.isEmpty ? nil : all.joined(separator: ",")
        let id = store.save(n)
        if id != 0 { note.id = id; note = store.note(id) ?? note }
    }
}
