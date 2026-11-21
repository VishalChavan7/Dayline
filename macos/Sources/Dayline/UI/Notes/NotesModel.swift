import SwiftUI
import AppKit
import LocalAuthentication
import DaylineCore

/// The lists of notes in the notes sidebar.
enum NotesFilter: Hashable {
    case all, today, locked, pinned, archive, trash
    case tag(String)

    static let views: [NotesFilter] = [.all, .today, .locked, .pinned, .archive, .trash]

    var title: String {
        switch self {
        case .all: "Notes"; case .today: "Today"
        case .locked: "Locked"; case .pinned: "Pinned"; case .archive: "Archive"; case .trash: "Trash"
        case .tag(let t): "#" + t
        }
    }
    var symbol: String {
        switch self {
        case .all: "note.text"; case .today: "calendar"
        case .locked: "lock"; case .pinned: "pin"; case .archive: "archivebox"; case .trash: "trash"
        case .tag: "number"
        }
    }
    /// Menu shortcut (⌥⌘ + key).
    var key: Character {
        switch self {
        case .all: "1"; case .today: "2"; case .locked: "3"; case .pinned: "4"
        case .archive: "5"; case .trash: "6"; case .tag: " "
        }
    }
}

enum NoteSort: String, CaseIterable {
    case edited, created, title
    var title: String { switch self { case .edited: "Date Edited"; case .created: "Date Created"; case .title: "Title" } }
}

enum TagSort: String, CaseIterable {
    case name, count
    var title: String { self == .name ? "Name" : "Number of Notes" }
}

/// Side panel beside the editor.
enum NotePanel: String { case info, contents, backlinks }

/// Which note is open and how the notes screen is laid out (shared so menus, Search and "New Note" reach it).
@MainActor
final class NotesUI: ObservableObject {
    /// nil = nothing open, 0 = a new, not yet saved note.
    @Published var selected: Int64?
    /// Changes when a different note is opened (not when the open note gets its id on first save).
    @Published var token = UUID()
    @Published var filter: NotesFilter = .all
    /// Locked notes unlocked in this session (they lock again when you open another note).
    @Published var unlocked: Set<Int64> = []
    @Published var panel: NotePanel?
    @Published var zoom: CGFloat = 1
    @Published var quickOpen = false
    @Published var expandedTags: Set<String> = []
    /// 1 = editor only, 2 = notes and editor, 3 = tags, notes and editor (Journal: 1 = entry only, 2 = timeline and entry).
    @Published var layout: Int { didSet { UserDefaults.standard.set(layout, forKey: layoutKey) } }
    let layoutKey: String
    let maxLayout: Int
    @Published var sort: NoteSort { didSet { UserDefaults.standard.set(sort.rawValue, forKey: "notesSort") } }
    @Published var pinnedFirst: Bool { didSet { UserDefaults.standard.set(pinnedFirst, forKey: "notesPinnedFirst") } }
    @Published var tagSort: TagSort { didSet { UserDefaults.standard.set(tagSort.rawValue, forKey: "notesTagSort") } }
    private(set) var back: [Int64] = []
    private(set) var forward: [Int64] = []

    init(layoutKey: String = "notesLayout", maxLayout: Int = 3) {
        let d = UserDefaults.standard
        self.layoutKey = layoutKey; self.maxLayout = maxLayout
        layout = (d.object(forKey: layoutKey) as? Int).map { min(maxLayout, max(1, $0)) } ?? maxLayout
        sort = NoteSort(rawValue: d.string(forKey: "notesSort") ?? "") ?? .edited
        pinnedFirst = d.object(forKey: "notesPinnedFirst") as? Bool ?? true
        tagSort = TagSort(rawValue: d.string(forKey: "notesTagSort") ?? "") ?? .name
    }

    func open(_ id: Int64) {
        if let cur = selected, cur != 0, cur != id { back.append(cur); forward.removeAll(); relock(cur) }
        selected = id; token = UUID()
    }
    func newNote() {
        if let cur = selected, cur != 0 { back.append(cur); forward.removeAll(); relock(cur) }
        selected = 0; token = UUID()
    }
    func close() { if let cur = selected { relock(cur) }; selected = nil }
    var canGoBack: Bool { !back.isEmpty }
    var canGoForward: Bool { !forward.isEmpty }
    func goBack(exists: (Int64) -> Bool) {
        while let id = back.popLast() {
            guard exists(id) else { continue }
            if let cur = selected, cur != 0 { forward.append(cur); relock(cur) }
            selected = id; token = UUID(); return
        }
    }
    func goForward(exists: (Int64) -> Bool) {
        while let id = forward.popLast() {
            guard exists(id) else { continue }
            if let cur = selected, cur != 0 { back.append(cur); relock(cur) }
            selected = id; token = UUID(); return
        }
    }
    func relock(_ id: Int64) { unlocked.remove(id) }

    func togglePanel(_ p: NotePanel) { panel = panel == p ? nil : p }

    // MARK: Collapsing panes (swipe right to left, or the ‹‹ button), step by step:
    // the app's sidebar, then the tags column, then the list, until only the editor is left.
    // Swiping left to right (or ››) brings them back in the opposite order.

    /// How many columns the window has room for right now (nil: all of them). Set by the view.
    var capacity: Int?
    private var room: Int { min(maxLayout, capacity ?? maxLayout) }

    /// The slide used when a pane is hidden or shown.
    static let paneAnimation = Animation.smooth(duration: 0.32)

    /// `shown`: how many columns fit right now (a narrow window may already hide some).
    func canCollapse(_ nav: Navigator, shown: Int? = nil) -> Bool { nav.sidebar != .detailOnly || min(layout, shown ?? room) > 1 }
    func canExpand(_ nav: Navigator) -> Bool { min(layout, room) < room || nav.sidebar == .detailOnly }

    func collapse(_ nav: Navigator, shown: Int? = nil) {
        withAnimation(Self.paneAnimation) {
            if nav.sidebar != .detailOnly { nav.sidebar = .detailOnly; return }
            let current = min(layout, shown ?? room)
            if current > 1 { layout = current - 1 }
        }
    }

    /// Brings back the next pane that will actually appear: when the window has no room for another
    /// column, the app's sidebar comes back instead of a column that wouldn't show.
    func expand(_ nav: Navigator) {
        withAnimation(Self.paneAnimation) {
            let current = min(layout, room)
            if current < room { layout = current + 1 } else if nav.sidebar == .detailOnly { nav.sidebar = .all }
        }
    }
}

/// Touch ID or the Mac's password, for locked notes. Locking hides a note's text in Dayline; it isn't encryption.
@MainActor
enum NoteLock {
    static func authenticate(_ reason: String) async -> Bool {
        if ServicesLocator.shared?.demo == true { return true }
        let ctx = LAContext()
        var err: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &err) else { return true }   // no password set on this Mac
        return (try? await ctx.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: reason)) ?? false
    }
}

/// A #tag and the tags inside it (#work/meetings sits under #work).
struct TagNode: Identifiable, Hashable {
    let path: String
    var name: String { String(path.split(separator: "/").last ?? "") }
    var depth: Int { path.split(separator: "/").count - 1 }
    var children: [TagNode]
    var id: String { path }
}

extension Store {
    /// Copies left behind when a sync gave a note a new id while it was open (fixed in 1.3.5): notes with
    /// the same title made at the same millisecond. The most recently edited one of each stays; these are the rest.
    var duplicateNotes: [Note] {
        let live = data.notes.filter { $0.trashedAt == nil && $0.createdAt.epochMillis > 0 }
        let groups = Dictionary(grouping: live) { "\($0.createdAt.epochMillis)|\($0.title.trimmingCharacters(in: .whitespaces).lowercased())" }
        return groups.values.filter { $0.count > 1 }.flatMap { $0.sorted { $0.updatedAt > $1.updatedAt }.dropFirst() }
    }

    /// Moves those copies to Trash, where they can still be restored. Returns how many.
    @discardableResult func trashDuplicateNotes() -> Int {
        let copies = duplicateNotes
        for n in copies { trashNote(n.id) }
        return copies.count
    }
}

extension Store {
    /// Notes (not journal entries) in a sidebar list, searched and sorted.
    func notes(in filter: NotesFilter, query: String = "", sort: NoteSort = .edited, pinnedFirst: Bool = true) -> [Note] {
        let q = query.trimmingCharacters(in: .whitespaces)
        let today = Day.today()
        let list = data.notes.filter { n in
            guard !isJournal(n) else { return false }
            let live = n.archivedAt == nil && n.trashedAt == nil
            switch filter {
            case .all: return live
            case .today: return live && (Day(n.updatedAt) == today || Day(n.createdAt) == today)
            case .locked: return live && n.locked
            case .pinned: return live && n.isPinned
            case .archive: return n.archivedAt != nil && n.trashedAt == nil
            case .trash: return n.trashedAt != nil
            case .tag(let t): return live && labels(of: n).contains { NoteFormat.tagMatches($0, t) }
            }
        }
        .filter { n in
            q.isEmpty || n.title.localizedCaseInsensitiveContains(q) || (n.category ?? "").localizedCaseInsensitiveContains(q)
                || (!n.locked && NoteCodec.plainText(n.body).localizedCaseInsensitiveContains(q))
        }
        return list.sorted { a, b in
            if pinnedFirst && filter != .trash && a.isPinned != b.isPinned { return a.isPinned }
            switch sort {
            case .edited: return a.updatedAt > b.updatedAt
            case .created: return a.createdAt > b.createdAt
            case .title:
                let c = noteTitle(a).localizedStandardCompare(noteTitle(b))
                return c == .orderedSame ? a.id < b.id : c == .orderedAscending
            }
        }
    }

    func count(_ filter: NotesFilter) -> Int { notes(in: filter, pinnedFirst: false).count }

    /// A note's title, or its first line when it has none.
    func noteTitle(_ n: Note) -> String {
        if !n.title.trimmingCharacters(in: .whitespaces).isEmpty { return n.title }
        if n.locked { return "Locked note" }
        return NoteCodec.plainText(n.body).split(separator: "\n").first.map(String.init) ?? "Untitled"
    }

    /// Tags of notes in use, as a tree (#a/b under #a), sorted by name or by how many notes use them.
    func tagTree(sort: TagSort) -> [TagNode] {
        var paths = Set<String>()
        for n in data.notes where !isJournal(n) && n.trashedAt == nil && n.archivedAt == nil {
            for l in labels(of: n) { NoteFormat.tagPath(l).forEach { paths.insert($0) } }
        }
        func counted(_ p: String) -> Int { notes(in: .tag(p), pinnedFirst: false).count }
        func build(_ parent: String?) -> [TagNode] {
            let kids = paths.filter { p in
                let parts = p.split(separator: "/")
                if let parent { return p.hasPrefix(parent + "/") && parts.count == parent.split(separator: "/").count + 1 }
                return parts.count == 1
            }
            let nodes = kids.map { TagNode(path: $0, children: build($0)) }
            return sort == .name ? nodes.sorted { $0.path.localizedStandardCompare($1.path) == .orderedAscending }
                : nodes.sorted { (counted($0.path), $1.path) > (counted($1.path), $0.path) }
        }
        return build(nil)
    }

    /// The note a [[link]] points at (by title, any case), not counting Trash.
    func note(titled title: String) -> Note? {
        let t = title.trimmingCharacters(in: .whitespaces).lowercased()
        guard !t.isEmpty else { return nil }
        return data.notes.filter { $0.trashedAt == nil && $0.title.trimmingCharacters(in: .whitespaces).lowercased() == t }
            .max { $0.updatedAt < $1.updatedAt }
    }

    /// Notes that link to this one with [[its title]].
    func backlinks(to n: Note) -> [Note] {
        let title = n.title.trimmingCharacters(in: .whitespaces)
        guard !title.isEmpty else { return [] }
        return data.notes.filter { $0.id != n.id && $0.trashedAt == nil && NoteFormat.linksTo($0.title + "\n" + NoteCodec.plainText($0.body), title: title) }
            .sorted { $0.updatedAt > $1.updatedAt }
    }

    /// A new note with a title (from a [[link]] to a note that doesn't exist yet).
    func createNote(titled title: String) -> Int64 {
        var n = Note(id: 0, title: title.trimmingCharacters(in: .whitespaces))
        n.body = ""
        return save(n)
    }
}
