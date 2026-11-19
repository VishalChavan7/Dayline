import SwiftUI
import AppKit
import DaylineCore
import DaylineEditor

/// Notes in up to three panes, like Bear: lists and tags, the notes in a list, and the editor.
struct NotesView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var ui: NotesUI
    @EnvironmentObject var nav: Navigator
    @State private var editingLabel: String?
    @State private var query = ""
    @State private var confirmEmptyTrash = false

    var body: some View {
        GeometryReader { geo in
            panes(width: geo.size.width)
                .background(SwipeCatcher { leftward in
                    if leftward { ui.collapse(nav, shown: shown(width: geo.size.width)) } else { ui.expand(nav) }
                })
        }
        .navigationTitle("Notes")
        .sheet(item: Binding(get: { editingLabel.map(LabelRef.init) }, set: { editingLabel = $0?.name })) { ref in
            LabelEditor(label: ref.name) { renamed in if ui.filter == .tag(ref.name) { ui.filter = .tag(renamed) } }
        }
        .sheet(isPresented: $ui.quickOpen) { QuickOpen().environmentObject(store).environmentObject(ui) }
        .alert("Empty the Trash?", isPresented: $confirmEmptyTrash) {
            Button("Empty Trash", role: .destructive) { emptyTrash() }
            Button("Cancel", role: .cancel) {}
        } message: { Text("\(store.count(.trash)) note\(store.count(.trash) == 1 ? "" : "s") will be deleted for good, here and on your phone.") }
        .onReceive(NotificationCenter.default.publisher(for: .emptyNotesTrash)) { _ in confirmEmptyTrash = true }
    }

    /// The panes that fit: the editor keeps at least 440 points; the tags column, then the list, give way first.
    /// Columns shown at this width: 1 = editor, 2 = list and editor, 3 = tags, list and editor.
    func shown(width: CGFloat) -> Int {
        let panel: CGFloat = ui.panel != nil && ui.selected != nil ? 250 : 0
        let showList = ui.layout >= 2 && width - panel - 300 >= 440
        let showTags = ui.layout >= 3 && showList && width - panel - 300 - 200 >= 440
        return showTags ? 3 : showList ? 2 : 1
    }

    func panes(width: CGFloat) -> some View {
        let columns = shown(width: width)
        let showList = columns >= 2, showTags = columns >= 3
        return HStack(spacing: 0) {
            if showTags {
                NotesSidebar(editingLabel: $editingLabel).frame(width: 200)
                Divider()
            }
            if showList {
                listPane(compactFilters: !showTags).frame(width: 300)
                Divider()
            }
            HStack(spacing: 0) {
                Group {
                    if let id = ui.selected {
                        NotePane(noteId: id, presetLabel: presetLabel, shownColumns: columns).id(ui.token)
                    } else {
                        EmptyState(art: .notebook, title: store.noteCount == 0 ? "Your notebook is empty" : "Pick a note",
                                   message: "Write with headings, lists, tables and checklists. Add #tags (even #work/meetings) to keep things tidy — they sync with your phone.",
                                   action: ("New Note", { ui.newNote() }))
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                if let panel = ui.panel, let id = ui.selected, id != 0, let n = store.note(id) {
                    Divider()
                    NoteInspector(note: n, panel: panel).frame(width: 250)
                }
            }
        }
    }

    var presetLabel: String? { if case .tag(let t) = ui.filter { return t }; return nil }

    func emptyTrash() {
        if let id = ui.selected, store.note(id)?.trashedAt != nil { ui.close() }
        store.emptyTrash()
    }

    func listPane(compactFilters: Bool) -> some View {
        let shown = store.notes(in: ui.filter, query: query, sort: ui.sort, pinnedFirst: ui.pinnedFirst)
        let pinnedFirst = ui.pinnedFirst && ui.filter != .trash
        let pinned = pinnedFirst ? shown.filter(\.isPinned) : [], others = pinnedFirst ? shown.filter { !$0.isPinned } : shown
        return ScrollView {
            LazyVStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .bottom) {
                    ScreenTitle(title: ui.filter.title, subtitle: "\(shown.count) note\(shown.count == 1 ? "" : "s")")
                    Spacer()
                    HStack(spacing: 2) {
                        sortMenu
                        if ui.filter == .trash {
                            IconButton(symbol: "trash.slash", help: "Empty Trash", size: 30) { confirmEmptyTrash = true }.disabled(shown.isEmpty)
                        } else {
                            IconButton(symbol: "square.and.pencil", help: "New note (⌥⌘N)", size: 30) { ui.newNote() }
                        }
                    }
                }
                .padding(.bottom, 4)
                SearchField(prompt: "Search \(ui.filter == .all ? "your notes" : ui.filter.title)", text: $query)
                if compactFilters { filterChips }
                if ui.filter == .trash && !shown.isEmpty {
                    Text("Notes in Trash stay until you delete them or empty the Trash.").font(.caption).foregroundStyle(.secondary)
                }
                // One list with one identity per note, so a card moving between Pinned and Others
                // is redrawn in its new place (two lists could leave a stale, still-selected copy).
                ForEach(listRows(pinned: pinned, others: others)) { r in
                    switch r {
                    case .header(let title): group(title)
                    case .note(let n): row(n)
                    }
                }
                if shown.isEmpty { Text(emptyText).foregroundStyle(.secondary).padding(.top, 20) }
            }
            .padding(.leading, compactFilters ? Page.h : 16).padding(.trailing, 16).padding(.top, Page.top).padding(.bottom, 16)
        }
    }

    var emptyText: String {
        if !query.isEmpty { return "No matching notes." }
        switch ui.filter {
        case .trash: return "Trash is empty."
        case .archive: return "Nothing archived. Archive notes you're done with to keep them out of the way."
        case .locked: return "No locked notes. Lock a note from its ⋯ menu."
        case .today: return "No notes written or edited today."
        default: return store.noteCount == 0 ? "No notes yet." : "No notes here."
        }
    }

    var sortMenu: some View {
        Menu {
            Picker("Sort By", selection: $ui.sort) { ForEach(NoteSort.allCases, id: \.self) { Text($0.title).tag($0) } }
            Toggle("Pinned Notes First", isOn: $ui.pinnedFirst)
        } label: { IconMenuLabel(symbol: "arrow.up.arrow.down", size: 30) }
            .menuStyle(.button).buttonStyle(.plain).menuIndicator(.hidden).fixedSize().help("Sort notes")
    }

    /// Lists and tags as chips when the sidebar is hidden.
    var filterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(NotesFilter.views, id: \.self) { f in
                    Chip(text: f.title, symbol: f.symbol, on: ui.filter == f) { ui.filter = f }
                }
                ForEach(store.allNoteLabels, id: \.self) { l in
                    Chip(text: l, symbol: store.labelSymbol(l), on: ui.filter == .tag(l)) { ui.filter = .tag(l) }
                        .contextMenu { Button("Rename and Change Icon…") { editingLabel = l } }
                }
            }
        }
        .padding(.bottom, 4)
    }

    enum ListRow: Identifiable {
        case header(String), note(Note)
        var id: String { switch self { case .header(let t): "h-" + t; case .note(let n): "n-\(n.id)" } }
    }

    func listRows(pinned: [Note], others: [Note]) -> [ListRow] {
        var rows: [ListRow] = []
        if !pinned.isEmpty { rows.append(.header("PINNED")); rows += pinned.map(ListRow.note) }
        if !others.isEmpty {
            if !pinned.isEmpty { rows.append(.header("OTHERS")) }
            rows += others.map(ListRow.note)
        }
        return rows
    }

    func group(_ title: String) -> some View {
        Text(title).font(.system(size: 11, weight: .semibold)).foregroundStyle(.secondary).padding(.top, 8).padding(.leading, 2)
    }

    func row(_ n: Note) -> some View {
        NoteCard(note: n, selected: ui.selected == n.id)
            .onTapGesture { ui.open(n.id) }
            .contextMenu { NoteActions(note: n) }
    }
}

/// The actions on a note, shared by the list's right-click menu and the editor's ⋯ menu.
struct NoteActions: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var ui: NotesUI
    /// The note as the menu was built for; actions read the store's current copy, because a
    /// right-click menu can outlive a change (pinning from the editor, sync).
    let shown: Note
    init(note: Note) { shown = note }
    var note: Note { store.note(shown.id) ?? shown }
    var body: some View {
        if note.trashedAt != nil {
            Button("Restore") { store.restoreNote(note.id) }
            Button("Delete Forever…", role: .destructive) { deleteForever() }
        } else {
            if note.isPinned {
                Button("Unpin") { store.setPinned(note: shown.id, false) }
            } else {
                Button("Pin") { store.setPinned(note: shown.id, true) }
            }
            Menu("Colour") { NoteColorItems(selected: note.color) { c in var m = note; m.color = c; store.save(m) } }
            Button(note.locked ? "Remove Lock…" : "Lock Note") { toggleLock() }
            Button(note.archivedAt != nil ? "Unarchive" : "Archive") {
                if note.archivedAt != nil { store.unarchiveNote(note.id) } else { store.archiveNote(note.id) }
            }
            Divider()
            Menu("Export As") {
                ForEach(NoteExport.Format.allCases) { f in Button(f.title + "…") { guarded { NoteExport.export(note, as: f, store: store) } } }
            }
            Button("Copy As Markdown") { guarded { NoteExport.copyMarkdown(note) } }
            Button("Print…") { guarded { NoteExport.print(note) } }
            Divider()
            Button("Move to Trash", role: .destructive) { store.trashNote(note.id) }
        }
    }

    /// Locked notes need Touch ID or the password before their text leaves the app.
    func guarded(_ f: @escaping () -> Void) {
        if !note.locked || ui.unlocked.contains(note.id) { f(); return }
        Task { if await NoteLock.authenticate("show a locked note") { ui.unlocked.insert(note.id); f() } }
    }

    func toggleLock() {
        if !note.locked { store.setLocked(note: note.id, true); ui.relock(note.id); return }
        guarded { store.setLocked(note: note.id, false) }
    }

    func deleteForever() {
        let a = NSAlert()
        a.messageText = "Delete “\(store.noteTitle(note))” forever?"
        a.informativeText = "It will be deleted here and on your phone. This can't be undone."
        a.addButton(withTitle: "Delete"); a.addButton(withTitle: "Cancel")
        a.buttons.first?.hasDestructiveAction = true
        guard a.runModal() == .alertFirstButtonReturn else { return }
        if ui.selected == note.id { ui.close() }
        store.deleteNote(note.id)
    }
}

extension Notification.Name {
    static let emptyNotesTrash = Notification.Name("dayline.emptyNotesTrash")
}

/// Lists (All, Todo, Today, …) and the tag tree.
struct NotesSidebar: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var ui: NotesUI
    @Binding var editingLabel: String?
    @Environment(\.scheme) private var scheme

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 2) {
                ForEach(NotesFilter.views, id: \.self) { f in
                    item(f.title, symbol: f.symbol, count: f == .trash || f == .archive ? nil : store.count(f), on: ui.filter == f, depth: 0, action: { ui.filter = f })
                        .dropDestination(for: String.self) { ids, _ in drop(ids, on: f) }
                }
                let tree = store.tagTree(sort: ui.tagSort)
                if !tree.isEmpty {
                    HStack {
                        Text("TAGS").font(.system(size: 11, weight: .semibold)).foregroundStyle(.secondary)
                        Spacer()
                        Menu {
                            Picker("Sort Tags By", selection: $ui.tagSort) { ForEach(TagSort.allCases, id: \.self) { Text($0.title).tag($0) } }
                            Button("Expand All") { ui.expandedTags = Set(allPaths(tree)) }
                            Button("Collapse All") { ui.expandedTags = [] }
                        } label: { Image(systemName: "ellipsis").font(.system(size: 11, weight: .semibold)) }
                            .menuStyle(.borderlessButton).menuIndicator(.hidden).fixedSize()
                    }
                    .padding(.top, 16).padding(.bottom, 4).padding(.horizontal, 8)
                    ForEach(flatten(tree), id: \.path) { node in tagRow(node) }
                }
            }
            .padding(.horizontal, 10).padding(.top, Page.top).padding(.bottom, 16)
        }
    }

    func allPaths(_ nodes: [TagNode]) -> [String] { nodes.flatMap { [$0.path] + allPaths($0.children) } }

    /// Tags shown, with the children of expanded tags.
    func flatten(_ nodes: [TagNode]) -> [TagNode] {
        nodes.flatMap { n in [n] + (ui.expandedTags.contains(n.path) ? flatten(n.children) : []) }
    }

    /// A tag lines up with the lists above it (icon under icon); nested tags step in, and the
    /// arrow that opens a tag's children sits at the end of the row.
    func tagRow(_ node: TagNode) -> some View {
        item(node.name, symbol: store.labelSymbol(node.path), count: store.count(.tag(node.path)), on: ui.filter == .tag(node.path), depth: node.depth,
             disclosure: node.children.isEmpty ? nil : ui.expandedTags.contains(node.path)) {
            ui.filter = .tag(node.path)
        } toggle: {
            if ui.expandedTags.contains(node.path) { ui.expandedTags.remove(node.path) } else { ui.expandedTags.insert(node.path) }
        }
        .contextMenu {
            Button("Rename and Change Icon…") { editingLabel = node.path }
        }
    }

    /// `disclosure`: nil = no children, else whether they're shown.
    func item(_ title: String, symbol: String, count: Int?, on: Bool, depth: Int, disclosure: Bool? = nil,
              action: @escaping () -> Void, toggle: @escaping () -> Void = {}) -> some View {
        Button(action: action) {
            HStack(spacing: 8) {
                Image(systemName: symbol).font(.system(size: 12, weight: .semibold)).frame(width: 16)
                    .foregroundStyle(on ? scheme.onPrimaryContainer : Color.secondary)
                Text(title).font(.system(size: 13, weight: on ? .semibold : .regular)).lineLimit(1)
                Spacer(minLength: 4)
                if let count, count > 0 { Text("\(count)").font(.system(size: 11)).foregroundStyle(.secondary).monospacedDigit() }
                if let open = disclosure {
                    Button(action: toggle) {
                        Image(systemName: open ? "chevron.down" : "chevron.right")
                            .font(.system(size: 9, weight: .bold)).foregroundStyle(.secondary)
                            .frame(width: 16, height: 20).contentShape(Rectangle())
                    }
                    .buttonStyle(.plain).help(open ? "Hide nested tags" : "Show nested tags")
                }
            }
            .padding(.leading, 8 + CGFloat(depth) * 16).padding(.trailing, disclosure == nil ? 8 : 2).frame(height: 28)
            .foregroundStyle(on ? scheme.onPrimaryContainer : Color.primary)
            .background(RoundedRectangle(cornerRadius: Radius.small, style: .continuous).fill(on ? scheme.primaryContainer : .clear))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }

    /// Dragging a note onto Archive, Trash or Pinned.
    func drop(_ ids: [String], on f: NotesFilter) -> Bool {
        let notes = ids.compactMap(Int64.init).compactMap(store.note)
        guard !notes.isEmpty else { return false }
        for n in notes {
            switch f {
            case .trash: store.trashNote(n.id)
            case .archive: store.archiveNote(n.id)
            case .pinned: store.setPinned(note: n.id, true)
            case .all: if n.trashedAt != nil { store.restoreNote(n.id) }; if n.archivedAt != nil { store.unarchiveNote(n.id) }
            default: return false
            }
        }
        return true
    }
}

/// A note as a card in its colour, like the phone.
struct NoteCard: View {
    @EnvironmentObject var store: Store
    @Environment(\.scheme) private var scheme
    let note: Note
    let selected: Bool
    var body: some View {
        let text = note.locked ? "" : NoteCodec.plainText(note.body)
        let lines = text.split(separator: "\n", omittingEmptySubsequences: true).map(String.init)
        let title = store.noteTitle(note)
        let preview = (note.title.isEmpty ? Array(lines.dropFirst()) : lines).prefix(3).joined(separator: "\n")
        let fill = NoteColors.color(note.color)
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .top) {
                Text(title).font(.system(size: 14, weight: .semibold)).lineLimit(2)
                Spacer()
                if note.locked { Image(systemName: "lock.fill").font(.system(size: 10)).foregroundStyle(.secondary) }
                if note.isPinned { Image(systemName: "pin.fill").font(.system(size: 10)).foregroundStyle(.secondary) }
            }
            let labels = store.labels(of: note)
            if !labels.isEmpty {
                HStack(spacing: 4) {
                    ForEach(labels.prefix(3), id: \.self) { l in
                        Label(l, systemImage: store.labelSymbol(l)).labelStyle(CompactLabel()).font(.system(size: 10.5, weight: .medium))
                            .padding(.horizontal, 6).padding(.vertical, 2)
                            .background(RoundedRectangle(cornerRadius: 4).fill(Color.primary.opacity(0.07)))
                    }
                }
            }
            if note.locked {
                Label("Locked", systemImage: "lock").font(.system(size: 12.5)).foregroundStyle(.secondary)
            } else if !preview.isEmpty {
                Text(preview).font(.system(size: 12.5)).foregroundStyle(.secondary).lineLimit(3)
            }
            Text(note.updatedAt.formatted(.relative(presentation: .named))).font(.system(size: 10.5)).foregroundStyle(.tertiary)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .surface(fill: fill ?? scheme.card)
        .overlay(RoundedRectangle(cornerRadius: Radius.card, style: .continuous).strokeBorder(selected ? Color.accentColor : .clear, lineWidth: 2))
        .contentShape(Rectangle())
        .draggable(String(note.id))
    }
}

/// The note editor: title, tags and the text, with a floating format bar.
struct NotePane: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var ui: NotesUI
    @EnvironmentObject var services: Services
    let noteId: Int64
    var presetLabel: String?
    /// Columns that fit beside the editor right now (see NotesView.shown).
    var shownColumns: Int? = nil
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
    /// Title, text and tags as last loaded or saved. While the editor still matches it, nothing was
    /// edited here: the store's copy is the truth, so changes from the phone (sync) show up in an open
    /// note instead of being saved over, and just looking at a note never saves it.
    @State private var baseline = Snapshot()
    struct Snapshot: Equatable { var title = "", body = "", category: String? = nil }
    @StateObject private var controller = RichTextController()
    @Environment(\.scheme) private var scheme
    @AppStorage(NotePage.marginKey) private var margin = NotePage.defaultMargin
    @AppStorage(NotePage.limitKey) private var limitWidth = true

    var trashed: Bool { note.trashedAt != nil }
    var lockedAway: Bool { note.locked && note.id != 0 && !ui.unlocked.contains(note.id) }

    var body: some View {
        Group {
            if lockedAway { lockedView } else { editor }
        }
        .background((journal ? nil : NoteColors.color(note.color)?.opacity(0.35)) ?? scheme.card)
        .onAppear(perform: load)
        .onDisappear { save() }
        .onChange(of: store.note(note.id == 0 ? noteId : note.id)) { _, n in
            // Pinned, locked, archived or restored from elsewhere (the list, a menu, sync).
            guard let n, n.id == note.id, n.id != 0 else { return }
            if snapshot() == baseline && (n.title != note.title || n.body != note.body || n.category != note.category) {
                reload(from: n)   // edited on the phone (or elsewhere) while open here
                return
            }
            note.isPinned = n.isPinned; note.locked = n.locked; note.archivedAt = n.archivedAt; note.trashedAt = n.trashedAt; note.color = n.color
            note.updatedAt = n.updatedAt
        }
    }

    var lockedView: some View {
        VStack(spacing: 14) {
            Image(systemName: "lock.fill").font(.system(size: 40)).foregroundStyle(.secondary)
            Text(store.noteTitle(note)).font(.system(size: 18, weight: .semibold))
            Text("This note is locked.").foregroundStyle(.secondary)
            Button("Unlock") { unlock() }.buttonStyle(.filled).keyboardShortcut(.defaultAction)
            Text("Locking hides a note's text in Dayline until you use Touch ID or your Mac's password. It isn't encryption: the note syncs to your phone as usual.")
                .font(.caption).foregroundStyle(.tertiary).multilineTextAlignment(.center).frame(maxWidth: 360)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    func unlock() {
        Task { if await NoteLock.authenticate("unlock “\(store.noteTitle(note))”") { ui.unlocked.insert(note.id) } }
    }

    var editor: some View {
        ZStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 0) {
                VStack(alignment: .leading, spacing: 10) {
                    toolbar
                    if journal {
                        Text(entryDate.formatted(.dateTime.weekday(.wide).day().month(.wide).year()))
                            .font(.system(size: 13, weight: .semibold)).foregroundStyle(scheme.primary)
                    }
                    if trashed { banner("trash", "This note is in Trash.", [("Restore", { store.restoreNote(note.id) })]) }
                    else if note.archivedAt != nil { banner("archivebox", "This note is archived.", [("Unarchive", { store.unarchiveNote(note.id) })]) }
                    TextField("Title", text: $note.title, axis: .vertical)
                        .font(.system(size: 28, weight: .bold))
                        .textFieldStyle(.plain)
                        .disabled(trashed)
                        .onChange(of: note.title) { _, _ in changed() }
                    if !journal { labelRow }
                    if !checklist.isEmpty { checklistView }
                    if !media.isEmpty {
                        Label(media.map { m in m.type == .AUDIO ? "Voice note" : m.type == .DRAWING ? "Drawing" : "Image" }.joined(separator: ", ") + " — open on your phone to view",
                              systemImage: "photo.on.rectangle").font(.caption).foregroundStyle(.secondary)
                    }
                }
                .padding(.horizontal, margin).padding(.top, 22).padding(.bottom, 16)
                RichTextEditor(html: $html, controller: controller, editable: !trashed, zoom: ui.zoom, onOpenNote: openLinked)
                    .padding(.horizontal, margin)
                    .onChange(of: html) { _, _ in changed() }
            }
            .frame(maxWidth: limitWidth ? NotePage.lineWidth + 2 * margin : .infinity)
            .frame(maxWidth: .infinity)
            if !trashed {
                FormatBar(controller: controller)
                    .padding(.horizontal, 10).frame(height: 38)
                    .surface(Radius.inner)
                    .padding(.bottom, 16)
            }
        }
    }

    var toolbar: some View {
        HStack(spacing: 10) {
            PaneButtons(ui: ui, nav: services.nav, shown: shownColumns)
            Button { ui.togglePanel(.info) } label: { Text(meta).font(.system(size: 12)).foregroundStyle(.tertiary).lineLimit(1) }
                .buttonStyle(.plain).help("Statistics (⇧⌘I)")
            Spacer()
            if !journal && !trashed {
                NoteColorButton(selected: note.color) { c in
                    note.color = c
                    if note.id != 0, var m = store.note(note.id) { m.color = c; store.save(m) } else { changed() }
                }
            }
            if !trashed {
                Button {
                    note.isPinned.toggle()
                    if note.id != 0 { store.setPinned(note: note.id, note.isPinned) } else { changed() }
                } label: {
                    Image(systemName: journal ? (note.isPinned ? "bookmark.fill" : "bookmark") : (note.isPinned ? "pin.fill" : "pin"))
                }.buttonStyle(.borderless).help(journal ? "Bookmark" : "Pin")
            }
            if !journal && note.id != 0 {
                Button { ui.togglePanel(.contents) } label: { Image(systemName: "list.bullet.indent") }.buttonStyle(.borderless).help("Contents (⇧⌘A)")
                Button { ui.togglePanel(.backlinks) } label: { Image(systemName: "arrow.uturn.backward.circle") }.buttonStyle(.borderless).help("Backlinks (⇧⌘B)")
            }
            if note.id != 0 {
                if journal {
                    Button(role: .destructive) { let id = note.id; ui.selected = nil; store.deleteNote(id) } label: { Image(systemName: "trash") }
                        .buttonStyle(.borderless).help("Delete entry")
                } else {
                    Menu { NoteActions(note: store.note(note.id) ?? note) } label: { Image(systemName: "ellipsis.circle") }
                        .menuStyle(.borderlessButton).menuIndicator(.hidden).fixedSize().help("More")
                }
            }
        }
    }

    func banner(_ symbol: String, _ text: String, _ actions: [(String, () -> Void)]) -> some View {
        HStack(spacing: 10) {
            Image(systemName: symbol).foregroundStyle(.secondary)
            Text(text).font(.system(size: 13))
            Spacer()
            ForEach(actions.indices, id: \.self) { i in Button(actions[i].0, action: actions[i].1) }
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(RoundedRectangle(cornerRadius: Radius.inner, style: .continuous).fill(Color.primary.opacity(0.06)))
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
                    Image(systemName: store.labelSymbol(l)).font(.system(size: 10, weight: .semibold))
                    Text(l)
                    if !trashed { Button { labels.removeAll { $0 == l }; changed() } label: { Image(systemName: "xmark").font(.caption2) }.buttonStyle(.borderless) }
                }
                .font(.system(size: 12, weight: .semibold))
                .padding(.horizontal, 8).padding(.vertical, 3)
                .background(RoundedRectangle(cornerRadius: Radius.small).fill(Color.primary.opacity(0.07)))
            }
            if !trashed {
                TextField("Add #tag", text: $newLabel).textFieldStyle(.plain).font(.system(size: 12)).frame(width: 110).onSubmit {
                    let l = newLabel.trimmingCharacters(in: .whitespaces).droppingPrefix("#").lowercased()
                        .replacingOccurrences(of: ",", with: "").replacingOccurrences(of: " ", with: "-")
                    if !l.isEmpty && !labels.contains(l) { labels.append(l); changed() }
                    newLabel = ""
                }
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
        .disabled(trashed)
        .padding(14)
        .background(RoundedRectangle(cornerRadius: Radius.inner, style: .continuous).fill(Color.primary.opacity(0.04)))
    }

    /// Clicking a [[link]]: open that note, or start it when there isn't one yet.
    func openLinked(_ title: String) {
        save()
        if let n = store.note(titled: title) { ui.open(n.id); return }
        let id = store.createNote(titled: title)
        if id != 0 { ui.open(id) }
    }

    func load() {
        guard !loaded else { return }
        if noteId != 0, let n = store.note(noteId) { note = n }
        fill(from: note)
        if noteId == 0, let presetLabel, presetLabel != "__pinned" { labels = [presetLabel] }
        if noteId == 0, journal, let promptTitle { note.title = promptTitle }
        loaded = true
    }

    func fill(from n: Note) {
        let d = NoteCodec.decode(n.body)
        html = d.html; checklist = d.checklist; media = d.media
        labels = store.labels(of: n)
        countWords()
        baseline = snapshot(title: n.title)
    }

    /// Shows the store's copy (changed by sync) in place of what's on screen.
    func reload(from n: Note) {
        note = n
        fill(from: n)
    }

    /// What the editor holds, in the store's format.
    func snapshot(title: String? = nil) -> Snapshot {
        let body = NoteCodec.encode(html, checklist: checklist, media: media)
        let t = title ?? note.title
        var all = labels
        for l in NoteCodec.labelsOf(title: t, body: body) where !all.contains(l) { all.append(l) }
        return Snapshot(title: t, body: body, category: all.isEmpty ? nil : all.joined(separator: ","))
    }

    /// Saves shortly after you stop typing (so sync picks it up), and when you leave.
    func changed() {
        guard loaded, !trashed else { return }
        pendingSave?.cancel()
        pendingSave = Task { try? await Task.sleep(for: .milliseconds(700)); if !Task.isCancelled { countWords(); save() } }
    }

    func countWords() {
        words = NoteFormat.stats(title: "", body: NoteCodec.encode(html, checklist: checklist)).words
    }

    func save() {
        pendingSave?.cancel()
        guard loaded, !trashed, !lockedAway else { return }
        // Nothing edited here: never write the copy shown back over a newer one.
        let snap = snapshot()
        if note.id != 0 && snap == baseline { return }
        // Deleted (here, from the list, or by sync) while open: don't bring it back.
        if note.id != 0 && store.note(note.id) == nil { return }
        var n = note
        if let current = store.note(note.id) {
            // State changed elsewhere (pinned from the list, archived, locked) wins over this copy.
            n.archivedAt = current.archivedAt; n.trashedAt = current.trashedAt; n.locked = current.locked
            if current.trashedAt != nil { return }
        }
        n.body = snap.body
        n.category = snap.category
        if n.id != 0, let current = store.note(n.id), current.title == n.title, current.body == n.body, current.category == n.category,
           current.color == n.color, current.isPinned == n.isPinned { baseline = snap; return }
        let id = store.save(n)
        baseline = snap
        if id != 0 {
            note = store.note(id) ?? note
            if ui.selected == 0 || ui.selected == noteId { ui.selected = id }
        }
    }
}

/// Page margins of the note editor (Settings → Notes), kept on this Mac.
enum NotePage {
    static let marginKey = "notePageMargin", limitKey = "notePageLimitWidth"
    static let defaultMargin = 28.0
    static let range = 0.0...120.0
    /// Longest comfortable line when "Limit line length" is on.
    static let lineWidth = 760.0
}

/// ‹‹ hides the next pane on the left (the app's sidebar first), ›› brings one back. Same as swiping.
struct PaneButtons: View {
    @ObservedObject var ui: NotesUI
    @ObservedObject var nav: Navigator
    var shown: Int?
    var body: some View {
        HStack(spacing: 2) {
            Button { ui.collapse(nav, shown: shown) } label: { Image(systemName: "chevron.left.2").frame(width: 22, height: 22).contentShape(Rectangle()) }
                .help("Hide a pane (swipe left with two fingers)").disabled(!ui.canCollapse(nav, shown: shown))
            Button { ui.expand(nav) } label: { Image(systemName: "chevron.right.2").frame(width: 22, height: 22).contentShape(Rectangle()) }
                .help("Show a pane (swipe right with two fingers)").disabled(!ui.canExpand(nav))
        }
        .buttonStyle(.borderless)
        .font(.system(size: 11, weight: .semibold))
    }
}

/// Statistics, contents (headings) and backlinks of the open note.
struct NoteInspector: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var ui: NotesUI
    let note: Note
    let panel: NotePanel

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Picker("", selection: Binding(get: { panel }, set: { ui.panel = $0 })) {
                Text("Info").tag(NotePanel.info); Text("Contents").tag(NotePanel.contents); Text("Backlinks").tag(NotePanel.backlinks)
            }
            .pickerStyle(.segmented).labelsHidden()
            if note.locked && !ui.unlocked.contains(note.id) {
                Text("Unlock the note to see this.").foregroundStyle(.secondary)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 10) {
                        switch panel {
                        case .info: info
                        case .contents: contents
                        case .backlinks: backlinks
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .background(Color.primary.opacity(0.02))
    }

    var info: some View {
        let s = NoteFormat.stats(title: note.title, body: note.body)
        return VStack(alignment: .leading, spacing: 10) {
            stat("Words", "\(s.words)")
            stat("Characters", "\(s.characters)")
            stat("Without spaces", "\(s.charactersNoSpaces)")
            stat("Paragraphs", "\(s.paragraphs)")
            stat("Reading time", s.readMinutes == 0 ? "—" : "\(s.readMinutes) min")
            Divider()
            stat("Created", note.createdAt.formatted(date: .abbreviated, time: .shortened))
            stat("Edited", note.updatedAt.formatted(date: .abbreviated, time: .shortened))
            if let a = note.archivedAt { stat("Archived", a.formatted(date: .abbreviated, time: .shortened)) }
        }
    }

    func stat(_ k: String, _ v: String) -> some View {
        HStack { Text(k).foregroundStyle(.secondary); Spacer(); Text(v).monospacedDigit() }.font(.system(size: 12.5))
    }

    var contents: some View {
        let hs = NoteFormat.headings(NoteCodec.decode(note.body).html)
        return VStack(alignment: .leading, spacing: 6) {
            if hs.isEmpty { Text("Headings in the note appear here.").font(.system(size: 12.5)).foregroundStyle(.secondary) }
            ForEach(hs.indices, id: \.self) { i in
                Button { EditorHub.shared.reveal(hs[i].text, occurrence: hs[..<i].filter { $0.text == hs[i].text }.count) } label: {
                    Text(hs[i].text).font(.system(size: hs[i].level <= 2 ? 13 : 12.5, weight: hs[i].level == 1 ? .semibold : .regular))
                        .lineLimit(2).frame(maxWidth: .infinity, alignment: .leading).contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .padding(.leading, CGFloat(hs[i].level - 1) * 10)
            }
        }
    }

    var backlinks: some View {
        let links = store.backlinks(to: note)
        return VStack(alignment: .leading, spacing: 8) {
            if note.title.isEmpty { Text("Give the note a title, then link to it from other notes with [[\("Title")]].").font(.system(size: 12.5)).foregroundStyle(.secondary) }
            else if links.isEmpty { Text("No notes link here yet. Link to this note from another with [[\(note.title)]].").font(.system(size: 12.5)).foregroundStyle(.secondary) }
            ForEach(links) { n in
                Button { ui.open(n.id) } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(store.noteTitle(n)).font(.system(size: 13, weight: .semibold)).lineLimit(1)
                        Text(n.updatedAt.formatted(.relative(presentation: .named))).font(.system(size: 11)).foregroundStyle(.tertiary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading).padding(8)
                    .background(RoundedRectangle(cornerRadius: Radius.small).fill(Color.primary.opacity(0.05)))
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
    }
}

/// ⌘O: jump to a note by typing part of its title.
struct QuickOpen: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var ui: NotesUI
    @Environment(\.dismiss) private var dismiss
    @State private var query = ""
    @State private var index = 0
    @FocusState private var focused: Bool

    var results: [Note] {
        let all = store.notes(in: .all, query: "", sort: .edited, pinnedFirst: false) + store.notes(in: .archive, pinnedFirst: false)
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return Array(all.prefix(12)) }
        let titled = all.filter { store.noteTitle($0).localizedCaseInsensitiveContains(q) }
        let rest = all.filter { n in !titled.contains { $0.id == n.id } && !n.locked && NoteCodec.plainText(n.body).localizedCaseInsensitiveContains(q) }
        return Array((titled.sorted { a, b in
            let pa = store.noteTitle(a).lowercased().hasPrefix(q.lowercased()), pb = store.noteTitle(b).lowercased().hasPrefix(q.lowercased())
            return pa != pb ? pa : a.updatedAt > b.updatedAt
        } + rest).prefix(12))
    }

    var body: some View {
        let r = results
        VStack(spacing: 0) {
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(.secondary)
                TextField("Open note…", text: $query).textFieldStyle(.plain).font(.system(size: 17)).focused($focused)
                    .onSubmit { pick(r) }
                    .onChange(of: query) { _, _ in index = 0 }
            }
            .padding(14)
            Divider()
            ScrollView {
                VStack(spacing: 2) {
                    ForEach(Array(r.enumerated()), id: \.element.id) { i, n in
                        Button { index = i; pick(r) } label: {
                            HStack {
                                Image(systemName: n.locked ? "lock" : n.archivedAt != nil ? "archivebox" : "note.text").foregroundStyle(.secondary).frame(width: 18)
                                Text(store.noteTitle(n)).lineLimit(1)
                                Spacer()
                                Text(n.updatedAt.formatted(.relative(presentation: .named))).font(.caption).foregroundStyle(.tertiary)
                            }
                            .padding(.horizontal, 12).frame(height: 32)
                            .background(RoundedRectangle(cornerRadius: 6).fill(i == index ? Color.accentColor.opacity(0.18) : .clear))
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                    }
                    if r.isEmpty { Text("No notes found.").foregroundStyle(.secondary).padding(20) }
                }
                .padding(8)
            }
            .frame(height: 300)
        }
        .frame(width: 520)
        .onAppear { focused = true }
        .onKeyPress(.downArrow) { index = min(index + 1, max(0, r.count - 1)); return .handled }
        .onKeyPress(.upArrow) { index = max(index - 1, 0); return .handled }
        .onKeyPress(.escape) { dismiss(); return .handled }
    }

    func pick(_ r: [Note]) {
        guard r.indices.contains(index) else { return }
        let n = r[index]
        if n.archivedAt != nil { ui.filter = .archive } else if case .tag = ui.filter {} else if ui.filter == .trash { ui.filter = .all }
        ui.open(n.id)
        dismiss()
    }
}

extension EditorHub {
    /// Scrolls the open editor to some text (a heading from Contents) and flashes it.
    func reveal(_ text: String, occurrence: Int = 0) {
        guard let tv = current else { return }
        let ns = tv.string as NSString
        var from = 0, found = NSRange(location: NSNotFound, length: 0)
        for _ in 0...occurrence {
            found = ns.range(of: text, range: NSRange(location: from, length: ns.length - from))
            if found.location == NSNotFound { return }
            from = found.upperBound
        }
        tv.window?.makeFirstResponder(tv)
        tv.setSelectedRange(NSRange(location: found.location, length: 0))
        tv.scrollRangeToVisible(found)
        tv.showFindIndicator(for: found)
    }
}

/// Menu items for note colours, each with a swatch of the colour in front of its name.
struct NoteColorItems: View {
    let selected: Int32
    let apply: (Int32) -> Void
    var body: some View {
        Button { apply(0) } label: { Label { Text(selected == 0 ? "Default ✓" : "Default") } icon: { Image(nsImage: Swatch.image(nil)) } }
            .labelStyle(.titleAndIcon)
        ForEach(NoteColors.all) { sw in
            Button { apply(sw.key) } label: {
                Label { Text(selected == sw.key ? "\(sw.name) ✓" : sw.name) } icon: { Image(nsImage: Swatch.image(sw)) }
            }
            .labelStyle(.titleAndIcon)
        }
    }
}

/// The note's colour: a swatch of the current colour that opens a palette of every colour, each with its name.
struct NoteColorButton: View {
    let selected: Int32
    let apply: (Int32) -> Void
    @State private var open = false
    @Environment(\.scheme) private var scheme

    var body: some View {
        Button { open.toggle() } label: { Image(systemName: "paintpalette") }
        .buttonStyle(.borderless)
        .help("Colour")
        .popover(isPresented: $open, arrowEdge: .bottom) {
            VStack(alignment: .leading, spacing: 10) {
                Text("Note colour").font(.system(size: 12, weight: .semibold)).foregroundStyle(.secondary)
                LazyVGrid(columns: Array(repeating: GridItem(.fixed(58), spacing: 6), count: 4), spacing: 10) {
                    choice(0, "Default")
                    ForEach(NoteColors.all) { sw in choice(sw.key, sw.name) }
                }
            }
            .padding(14)
        }
    }

    func choice(_ key: Int32, _ name: String) -> some View {
        Button { apply(key); open = false } label: {
            VStack(spacing: 5) {
                ZStack {
                    dot(key, size: 30)
                    if key == selected {
                        Image(systemName: "checkmark").font(.system(size: 12, weight: .bold)).foregroundStyle(Color.primary.opacity(0.75))
                    }
                }
                .overlay(Circle().strokeBorder(key == selected ? scheme.primary : .clear, lineWidth: 2).padding(-3))
                Text(name).font(.system(size: 10.5)).foregroundStyle(.secondary).lineLimit(1)
            }
            .frame(width: 58)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .help(name)
    }

    @ViewBuilder func dot(_ key: Int32, size: CGFloat) -> some View {
        Circle().fill(NoteColors.color(key) ?? scheme.card)
            .overlay(Circle().strokeBorder(Color.primary.opacity(0.18), lineWidth: 1))
            .frame(width: size, height: size)
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

struct LabelRef: Identifiable { let name: String; var id: String { name } }

/// Rename a #label (on every note that has it) and pick its icon, like the phone's label sheet.
struct LabelEditor: View {
    @EnvironmentObject var store: Store
    @Environment(\.dismiss) private var dismiss
    @Environment(\.scheme) private var scheme
    @State var label: String
    var onRename: (String) -> Void = { _ in }
    @State private var name = ""

    var body: some View {
        let current = store.labelIcon(label)
        let labels = store.allNoteLabels
        VStack(alignment: .leading, spacing: 16) {
            HStack {
                Text("Note labels").font(.system(size: 17, weight: .bold))
                Spacer()
                Button("Done") { dismiss() }.keyboardShortcut(.defaultAction)
            }
            if labels.count > 1 {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(labels, id: \.self) { l in
                            Chip(text: l, symbol: store.labelSymbol(l), on: l == label) { label = l; name = l }
                        }
                    }
                }
            }
            VStack(alignment: .leading, spacing: 6) {
                Text("Name").font(.system(size: 12, weight: .semibold)).foregroundStyle(.secondary)
                HStack(spacing: 8) {
                    TextField("Label name", text: $name)
                        .textFieldStyle(.roundedBorder)
                        .onSubmit(rename)
                    Button("Rename", action: rename)
                        .disabled(clean.isEmpty || clean == label.lowercased() || clean == Store.journalLabel)
                }
                Text("Renames #\(label) on all \(store.noteCount(label: label)) note\(store.noteCount(label: label) == 1 ? "" : "s") that use it, here and on your phone.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("Icon").font(.system(size: 12, weight: .semibold)).foregroundStyle(.secondary)
                    Spacer()
                    if current != nil { Button("Use default") { store.setLabelIcon(label, nil) }.buttonStyle(.borderless).font(.caption) }
                }
                LazyVGrid(columns: Array(repeating: GridItem(.fixed(40), spacing: 8), count: 8), spacing: 8) {
                    ForEach(Icons.keys, id: \.self) { key in
                        let on = key == current
                        Button { store.setLabelIcon(label, key) } label: {
                            Image(systemName: Icons.symbol(key))
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(on ? scheme.onPrimaryContainer : Color.secondary)
                                .frame(width: 40, height: 40)
                                .background(RoundedRectangle(cornerRadius: Radius.inner, style: .continuous).fill(on ? scheme.primaryContainer : Color.primary.opacity(0.06)))
                                .overlay(RoundedRectangle(cornerRadius: Radius.inner, style: .continuous).strokeBorder(on ? scheme.primary : .clear, lineWidth: 1.5))
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .help(key.capitalized)
                    }
                }
            }
        }
        .padding(20)
        .frame(width: 420)
        .onAppear { name = label }
    }

    var clean: String { name.lowercased().replacingOccurrences(of: ",", with: "").replacingOccurrences(of: "#", with: "").trimmingCharacters(in: .whitespaces) }

    func rename() {
        let to = clean
        guard !to.isEmpty, to != label.lowercased(), to != Store.journalLabel else { return }
        store.renameLabel(label, to)
        onRename(to)
        label = to
        name = to
    }
}
