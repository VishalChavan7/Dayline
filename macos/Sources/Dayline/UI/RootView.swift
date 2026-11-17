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

    func open(_ id: Int64) { selectedTask = id; showInspector = true }
    func go(_ d: Destination) { search = ""; destination = d }
}

struct RootView: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @EnvironmentObject var sync: SyncService

    var body: some View {
        NavigationSplitView {
            Sidebar()
                .navigationSplitViewColumnWidth(min: 200, ideal: 230, max: 300)
        } detail: {
            ZStack(alignment: .bottom) {
                content
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
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
                SyncButton()
                Button { nav.newTaskDraft = newDraft() } label: { Label("New Task", systemImage: "plus") }
                    .help("New task (⌘N)")
                Button { nav.showInspector.toggle() } label: { Label("Details", systemImage: "sidebar.right") }
                    .help("Show or hide task details")
            }
        }
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

    /// A new task prefilled from where you are (project, list, or today).
    func newDraft() -> TaskDraft {
        var t = TaskItem(id: 0, title: "")
        t.priority = store.defaultPriority
        switch nav.destination {
        case .project(let id): t.projectId = id
        case .list(let id): t.listId = id; t.projectId = store.list(id)?.projectId
        case .today, .none: t.dueDate = store.today
        default: break
        }
        var reminders: [Reminder] = []
        if t.dueDate != nil, let off = store.defaultReminderOffset { reminders.append(Reminder(id: 0, taskId: 0, type: .DEADLINE, offsetMinutes: off)) }
        return TaskDraft(task: t, reminders: reminders)
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
                    Label(d.title, systemImage: d.symbol)
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
                Label("Completed", systemImage: Destination.completed.symbol).tag(Destination.completed)
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
