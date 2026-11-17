import SwiftUI
import DaylineCore

enum Destination: Hashable {
    case today, upcoming, inbox, calendar, habits, focus, notes, stats, completed
    case project(Int64), list(Int64), tag(Int64)

    var title: String {
        switch self {
        case .today: "Today"; case .upcoming: "Upcoming"; case .inbox: "Inbox"; case .calendar: "Calendar"; case .habits: "Habits"
        case .focus: "Focus"; case .notes: "Notes"; case .stats: "Statistics"; case .completed: "Completed"
        case .project: "Project"; case .list: "List"; case .tag: "Tag"
        }
    }
    var symbol: String {
        switch self {
        case .today: "sun.max.fill"; case .upcoming: "calendar.badge.clock"; case .inbox: "tray.fill"; case .calendar: "calendar"
        case .habits: "flame.fill"; case .focus: "timer"; case .notes: "note.text"; case .stats: "chart.bar.xaxis"
        case .completed: "checkmark.circle.fill"; case .project: "folder.fill"; case .list: "list.bullet"; case .tag: "number"
        }
    }
    static let main: [Destination] = [.today, .upcoming, .inbox, .calendar, .habits, .focus, .notes, .stats]

    /// Tile colour in the sidebar and the tint of the screen's background wash.
    var tint: Color {
        switch self {
        case .today: Color(hex: 0xF5A524); case .upcoming: Color(hex: 0x8B5CF6); case .inbox: Color(hex: 0x3B82F6)
        case .calendar: Color(hex: 0xEF4444); case .habits: Color(hex: 0xF97316); case .focus: Color(hex: 0x14B8A6)
        case .notes: Color(hex: 0xEAB308); case .stats: Color(hex: 0x6366F1); case .completed: Color(hex: 0x22C55E)
        case .project, .list, .tag: .accentColor
        }
    }
}

enum CreateKind: String, CaseIterable, Identifiable {
    case task, note, habit, project, list
    var id: String { rawValue }
    var label: String { "New " + rawValue.capitalized }
    var symbol: String {
        switch self { case .task: "checkmark.circle"; case .note: "square.and.pencil"; case .habit: "flame"; case .project: "folder.badge.plus"; case .list: "list.bullet" }
    }
}

/// Window-level navigation and selection.
@MainActor
final class Navigator: ObservableObject {
    @Published var destination: Destination? = .today
    @Published var selectedTask: Int64?
    @Published var showInspector = false
    @Published var search = ""
    @Published var editingProject: Project?
    @Published var editingList: TaskList?
    @Published var newTaskDraft: TaskDraft?
    @Published var undo: (message: String, action: () -> Void)?
    @Published var editingHabit: Habit?

    /// What "+" makes on the current screen.
    var primaryCreate: CreateKind {
        switch destination { case .notes: .note; case .habits: .habit; default: .task }
    }

    func open(_ id: Int64) { selectedTask = id; showInspector = true }
    func go(_ d: Destination) { search = ""; destination = d }
}

struct RootView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @EnvironmentObject var sync: SyncService
    @EnvironmentObject var notesUI: NotesUI
    @EnvironmentObject var services: Services

    var body: some View {
        NavigationSplitView {
            Sidebar()
                .navigationSplitViewColumnWidth(min: 200, ideal: 230, max: 300)
        } detail: {
            ZStack(alignment: .bottom) {
                content
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(AmbientBackdrop(colors: backdropColors))
                if let u = nav.undo { UndoBar(message: u.message, action: u.action) { nav.undo = nil } }
                if let n = store.notice { NoticeBar(message: n) { store.notice = nil } }
            }
            .inspector(isPresented: $nav.showInspector) {
                Group {
                    if let id = nav.selectedTask, store.task(id) != nil {
                        TaskInspector(taskId: id).id(id)
                    } else {
                        EmptyState(symbol: "sidebar.right", title: "No task selected", message: "Select a task to see its details.")
                    }
                }
                .inspectorColumnWidth(min: 300, ideal: 340, max: 460)
            }
        }
        .searchable(text: $nav.search, placement: .sidebar, prompt: "Search tasks, notes, projects")
        .toolbar {
            ToolbarItemGroup(placement: .primaryAction) {
                RefreshButton()
                CreateMenu { services.create($0) }
                Button { nav.showInspector.toggle() } label: { Label("Details", systemImage: "sidebar.right") }
                    .help("Show or hide task details")
            }
        }
        .sheet(item: $nav.editingHabit) { h in HabitEditor(habit: h) }
        .sheet(item: Binding(get: { nav.newTaskDraft.map(DraftBox.init) }, set: { nav.newTaskDraft = $0?.draft })) { box in
            TaskEditorSheet(draft: box.draft)
        }
        .sheet(item: $nav.editingProject) { p in ProjectEditor(project: p) }
        .sheet(item: $nav.editingList) { l in ListEditor(list: l) }
        .onChange(of: store.notice) { _, n in
            guard n != nil else { return }
            Task { try? await Task.sleep(for: .seconds(5)); if store.notice == n { store.notice = nil } }
        }
    }

    var backdropColors: [Color] {
        switch nav.destination ?? .today {
        case .project(let id): let c = Color(argb: store.project(id)?.color ?? 0); return [c, c.opacity(0.6), .accentColor]
        case let d: return [d.tint, .accentColor, d.tint.opacity(0.7)]
        }
    }

    @ViewBuilder var content: some View {
        if !nav.search.trimmingCharacters(in: .whitespaces).isEmpty {
            SearchView(query: nav.search)
        } else {
            switch nav.destination ?? .today {
            case .today: TodayView()
            case .upcoming: UpcomingView()
            case .inbox: InboxView()
            case .calendar: CalendarView()
            case .habits: HabitsView()
            case .focus: FocusView()
            case .notes: NotesView()
            case .stats: StatsView()
            case .completed: CompletedView()
            case .project(let id): ProjectView(projectId: id)
            case .list(let id): ListView(listId: id)
            case .tag(let id): TagView(tagId: id)
            }
        }
    }
}

struct DraftBox: Identifiable { let draft: TaskDraft; var id: Int { 0 } }

struct Sidebar: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @State private var showArchived = false

    var body: some View {
        List(selection: $nav.destination) {
            Section {
                ForEach(Destination.main, id: \.self) { d in
                    Label { Text(d.title) } icon: { IconTile(symbol: d.symbol, color: d.tint) }
                        .badge(badge(d))
                        .tag(d)
                }
            }
            Section {
                ForEach(store.activeProjects) { p in
                    Label { Text(p.name) } icon: { ColorBadge(color: p.color, symbol: Icons.symbol(p.icon), size: 18) }
                        .badge(store.openTasks.filter { $0.projectId == p.id }.count)
                        .tag(Destination.project(p.id))
                        .contextMenu {
                            Button("Edit Project…") { nav.editingProject = p }
                            Button("Archive") { var q = p; q.isArchived = true; store.save(q) }
                        }
                }
            } header: {
                HStack {
                    Text("Projects")
                    Spacer()
                    Button { nav.editingProject = Project(id: 0, name: "", color: Palette.itemColors[0]) } label: { Image(systemName: "plus") }
                        .buttonStyle(.borderless).help("New project")
                }
            }
            let lists = store.data.lists.sorted { ($0.sortOrder, $0.id) < ($1.sortOrder, $1.id) }
            Section {
                ForEach(lists) { l in
                    Label { Text(l.name) } icon: { Image(systemName: Icons.symbol(l.icon)).foregroundStyle(Color(argb: l.color)) }
                        .badge(store.openTasks.filter { $0.listId == l.id }.count)
                        .tag(Destination.list(l.id))
                        .contextMenu {
                            Button("Edit List…") { nav.editingList = l }
                            Button("Delete List", role: .destructive) { store.deleteList(l.id); if nav.destination == .list(l.id) { nav.go(.inbox) } }
                        }
                }
            } header: {
                HStack {
                    Text("Lists")
                    Spacer()
                    Button { nav.editingList = TaskList(id: 0, name: "", color: Palette.itemColors[2]) } label: { Image(systemName: "plus") }
                        .buttonStyle(.borderless).help("New list")
                }
            }
            if !store.data.tags.isEmpty {
                Section("Tags") {
                    ForEach(store.data.tags.sorted { $0.name.localizedLowercase < $1.name.localizedLowercase }) { t in
                        Label(t.name, systemImage: "number").tag(Destination.tag(t.id))
                            .contextMenu { Button("Delete Tag", role: .destructive) { store.deleteTag(t.id) } }
                    }
                }
            }
            Section {
                Label { Text("Completed") } icon: { IconTile(symbol: Destination.completed.symbol, color: Destination.completed.tint) }.tag(Destination.completed)
                let archived = store.data.projects.filter(\.isArchived)
                if !archived.isEmpty {
                    DisclosureGroup("Archived projects", isExpanded: $showArchived) {
                        ForEach(archived) { p in
                            Label(p.name, systemImage: "archivebox").tag(Destination.project(p.id))
                                .contextMenu { Button("Unarchive") { var q = p; q.isArchived = false; store.save(q) } }
                        }
                    }
                }
            }
        }
        .listStyle(.sidebar)
        .safeAreaInset(edge: .bottom) { SyncFooter() }
    }

    func badge(_ d: Destination) -> Int {
        let today = store.today
        switch d {
        case .today: return store.openTasks.filter { t in (t.scheduledDate.map { !($0 > today) } ?? false) }.count
        case .inbox: return store.openTasks.filter { $0.scheduledDate == nil && $0.projectId == nil && $0.listId == nil }.count
        default: return 0
        }
    }
}

/// A small rounded colour tile behind a sidebar symbol (macOS Tahoe style).
struct IconTile: View {
    let symbol: String
    let color: Color
    var size: CGFloat = 20
    var body: some View {
        Image(systemName: symbol)
            .font(.system(size: size * 0.55, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(RoundedRectangle(cornerRadius: size * 0.3, style: .continuous).fill(color.gradient))
    }
}

/// Sync now. Spins while syncing; explains how to turn sync on when it's off.
struct RefreshButton: View {
    @EnvironmentObject var sync: SyncService
    @State private var explain = false
    @State private var spin = false
    var body: some View {
        Button {
            if sync.enabled { Task { await sync.syncNow() } } else { explain = true }
        } label: {
            Label("Refresh", systemImage: "arrow.clockwise")
                .rotationEffect(.degrees(spin ? 360 : 0))
                .animation(spin ? .linear(duration: 0.9).repeatForever(autoreverses: false) : .default, value: spin)
        }
        .help(sync.enabled ? "Sync now · \(sync.statusText)" : "Refresh")
        .keyboardShortcut("r")
        .onChange(of: sync.status) { _, s in spin = s == .syncing }
        .popover(isPresented: $explain, arrowEdge: .bottom) {
            VStack(alignment: .leading, spacing: 10) {
                Text("Sync is off").font(.headline)
                Text("Connect your Google account to keep Dayline in step with your phone.").foregroundStyle(.secondary).fixedSize(horizontal: false, vertical: true)
                SettingsLink { Text("Open Sync Settings…") }
            }
            .padding(16).frame(width: 260)
        }
    }
}

/// "+" makes the obvious thing for this screen; the arrow offers everything else.
struct CreateMenu: View {
    @EnvironmentObject var nav: Navigator
    let create: (CreateKind) -> Void
    var body: some View {
        Menu {
            ForEach(CreateKind.allCases) { k in Button { create(k) } label: { Label(k.label, systemImage: k.symbol) } }
        } label: {
            Label(nav.primaryCreate.label, systemImage: "plus")
        } primaryAction: {
            create(nav.primaryCreate)
        }
        .help("\(nav.primaryCreate.label) (⌘N)")
    }
}

struct UndoBar: View {
    let message: String
    let action: () -> Void
    let dismiss: () -> Void
    var body: some View {
        HStack(spacing: 14) {
            Text(message).lineLimit(1)
            Button("Undo") { action(); dismiss() }.buttonStyle(.borderless).fontWeight(.semibold)
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
        .background(.regularMaterial, in: Capsule())
        .shadow(radius: 8, y: 2)
        .padding(.bottom, 18)
        .task { try? await Task.sleep(for: .seconds(6)); dismiss() }
    }
}

struct NoticeBar: View {
    let message: String
    let dismiss: () -> Void
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "info.circle.fill").foregroundStyle(.tint)
            Text(message).lineLimit(2)
            Button { dismiss() } label: { Image(systemName: "xmark") }.buttonStyle(.borderless)
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
        .background(.regularMaterial, in: Capsule())
        .shadow(radius: 8, y: 2)
        .padding(.bottom, 18)
    }
}
