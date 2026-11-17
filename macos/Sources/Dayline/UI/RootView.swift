import SwiftUI
import DaylineCore

/// The same places as the phone: the four main tabs, then everything from its More page.
enum Destination: Hashable, CaseIterable {
    case today, calendar, notes, habits
    case inbox, upcoming, all, completed
    case focus, stats

    var title: String {
        switch self {
        case .today: "Today"; case .calendar: "Calendar"; case .notes: "Notes"; case .habits: "Habits"
        case .inbox: "Inbox"; case .upcoming: "Upcoming"; case .all: "All tasks"; case .completed: "Completed"
        case .focus: "Focus timer"; case .stats: "Statistics"
        }
    }
    var symbol: String {
        switch self {
        case .today: "sun.max.fill"; case .calendar: "calendar"; case .notes: "note.text"; case .habits: "figure.mind.and.body"
        case .inbox: "tray.fill"; case .upcoming: "calendar.badge.clock"; case .all: "checkmark.circle"; case .completed: "checklist.checked"
        case .focus: "timer"; case .stats: "chart.bar.fill"
        }
    }
    static let main: [Destination] = [.today, .calendar, .notes, .habits]
    static let tasks: [Destination] = [.inbox, .upcoming, .all, .completed]
    static let productivity: [Destination] = [.focus, .stats]
    static let ordered: [Destination] = main + tasks + productivity
}

enum CreateKind: String, CaseIterable, Identifiable {
    case task, note, habit
    var id: String { rawValue }
    var label: String { "New " + rawValue.capitalized }
    var symbol: String {
        switch self { case .task: "checkmark.circle"; case .note: "square.and.pencil"; case .habit: "figure.mind.and.body" }
    }
}

/// Window-level navigation and selection.
@MainActor
final class Navigator: ObservableObject {
    @Published var destination: Destination? = .today
    @Published var selectedTask: Int64?
    @Published var showInspector = false
    @Published var search = ""
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
    @EnvironmentObject var services: Services

    var body: some View {
        NavigationSplitView {
            Sidebar()
                .navigationSplitViewColumnWidth(min: 210, ideal: 240, max: 300)
        } detail: {
            ZStack(alignment: .bottom) {
                content
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .screenBackground()
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
        .searchable(text: $nav.search, placement: .sidebar, prompt: "Search tasks and notes")
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
        .onChange(of: store.notice) { _, n in
            guard n != nil else { return }
            Task { try? await Task.sleep(for: .seconds(5)); if store.notice == n { store.notice = nil } }
        }
    }

    @ViewBuilder var content: some View {
        if !nav.search.trimmingCharacters(in: .whitespaces).isEmpty {
            SearchView(query: nav.search)
        } else {
            switch nav.destination ?? .today {
            case .today: TodayView()
            case .calendar: CalendarView()
            case .notes: NotesView()
            case .habits: HabitsView()
            case .inbox: InboxView()
            case .upcoming: UpcomingView()
            case .all: AllTasksView()
            case .completed: CompletedView()
            case .focus: FocusView()
            case .stats: StatsView()
            }
        }
    }
}

struct DraftBox: Identifiable { let draft: TaskDraft; var id: Int { 0 } }

struct Sidebar: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @Environment(\.scheme) private var scheme

    var body: some View {
        let counts = badges()
        List(selection: $nav.destination) {
            Section {
                ForEach(Destination.main, id: \.self) { row($0, counts[$0]) }
            }
            Section("Tasks") {
                ForEach(Destination.tasks, id: \.self) { row($0, counts[$0]) }
            }
            Section("Productivity") {
                ForEach(Destination.productivity, id: \.self) { row($0, counts[$0]) }
            }
        }
        .listStyle(.sidebar)
        .safeAreaInset(edge: .bottom) { SyncFooter() }
    }

    func row(_ d: Destination, _ badge: Int?) -> some View {
        Label {
            Text(d.title).font(.system(size: 13.5))
        } icon: {
            IconBadge(symbol: d.symbol, color: scheme.primary, size: 26)
        }
        .padding(.vertical, 5)
        .badge(badge ?? 0)
        .tag(d)
    }

    func badges() -> [Destination: Int] {
        let today = store.today
        var todayCount = 0, inbox = 0
        for t in store.openTasks {
            if let d = t.scheduledDate { if !(d > today) { todayCount += 1 } }
            else if t.projectId == nil && t.listId == nil { inbox += 1 }
        }
        return [.today: todayCount, .inbox: inbox]
    }
}

/// Sync now. Spins while syncing; explains how to turn sync on when it's off.
struct RefreshButton: View {
    @EnvironmentObject var sync: SyncService
    @State private var explain = false
    var body: some View {
        Button {
            if sync.enabled { Task { await sync.syncNow() } } else { explain = true }
        } label: {
            if sync.status == .syncing {
                ProgressView().controlSize(.small).frame(width: 16, height: 16)
            } else {
                Label("Refresh", systemImage: "arrow.clockwise")
            }
        }
        .help(sync.enabled ? "Sync now · \(sync.statusText)" : "Refresh")
        .keyboardShortcut("r")
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

/// "+" makes the obvious thing for this screen; the arrow offers the rest.
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
            Text(message).lineLimit(1).foregroundStyle(.white)
            Button("Undo") { action(); dismiss() }.buttonStyle(.plain).fontWeight(.semibold).foregroundStyle(Color(hex: 0xA8C7FA))
        }
        .padding(.horizontal, 16).frame(height: 40)
        .background(RoundedRectangle(cornerRadius: Radius.inner, style: .continuous).fill(Color(hex: 0x2F3033)))
        .padding(.bottom, 18)
        .task { try? await Task.sleep(for: .seconds(6)); dismiss() }
    }
}

struct NoticeBar: View {
    let message: String
    let dismiss: () -> Void
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: "info.circle.fill").foregroundStyle(Color(hex: 0xA8C7FA))
            Text(message).lineLimit(2).foregroundStyle(.white)
            Button { dismiss() } label: { Image(systemName: "xmark").foregroundStyle(.white.opacity(0.8)) }.buttonStyle(.plain)
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
        .background(RoundedRectangle(cornerRadius: Radius.inner, style: .continuous).fill(Color(hex: 0x2F3033)))
        .padding(.bottom, 18)
    }
}
