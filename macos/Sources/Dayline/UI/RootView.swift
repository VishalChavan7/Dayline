import SwiftUI
import DaylineCore

/// The same places as the phone: the four main tabs, then everything from its More page.
enum Destination: Hashable, CaseIterable {
    case today, calendar, notes, journal, habits
    case inbox, upcoming, all, completed
    case focus, stats

    var title: String {
        switch self {
        case .today: "Today"; case .calendar: "Calendar"; case .notes: "Notes"; case .journal: "Journal"; case .habits: "Habits"
        case .inbox: "Inbox"; case .upcoming: "Upcoming"; case .all: "All Tasks"; case .completed: "Completed"
        case .focus: "Focus Timer"; case .stats: "Statistics"
        }
    }
    var symbol: String {
        switch self {
        case .today: "sun.max.fill"; case .calendar: "calendar"; case .notes: "note.text"; case .journal: "book.closed.fill"; case .habits: "figure.mind.and.body"
        case .inbox: "tray.fill"; case .upcoming: "calendar.badge.clock"; case .all: "checkmark.circle"; case .completed: "checklist.checked"
        case .focus: "timer"; case .stats: "chart.bar.fill"
        }
    }
    static let main: [Destination] = [.today, .calendar, .notes, .journal, .habits]
    static let tasks: [Destination] = [.inbox, .upcoming, .all, .completed]
    static let productivity: [Destination] = [.focus, .stats]
    static let ordered: [Destination] = main + tasks + productivity
}

enum CreateKind: String, CaseIterable, Identifiable {
    case task, note, journal, habit
    var id: String { rawValue }
    var label: String { self == .journal ? "New Journal Entry" : "New " + rawValue.capitalized }
    var symbol: String {
        switch self { case .task: "checkmark.circle"; case .note: "square.and.pencil"; case .journal: "book.closed"; case .habit: "figure.mind.and.body" }
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
        switch destination { case .notes: .note; case .journal: .journal; case .habits: .habit; default: .task }
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
            case .journal: JournalView(ui: services.journalUI)
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
    @Environment(\.controlActiveState) private var windowState
    /// The row that looks selected. It moves the moment you click, and the page follows on the next frame,
    /// so the highlight never waits for a heavy page to draw.
    @State private var shown: Destination = .today

    var body: some View {
        let counts = badges()
        List {
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
        .onAppear { shown = nav.destination ?? .today }
        .onChange(of: nav.destination) { _, d in if let d, d != shown { shown = d } }
    }

    func row(_ d: Destination, _ badge: Int?) -> some View {
        let selected = shown == d
        let strong = selected && windowState == .key
        return Button {
            guard shown != d || !nav.search.isEmpty else { return }
            var t = Transaction(); t.disablesAnimations = true
            withTransaction(t) { shown = d }
            DispatchQueue.main.async { nav.go(d) }
        } label: {
            HStack(spacing: 11) {
                Image(systemName: d.symbol)
                    .font(.system(size: 24 * 0.48, weight: .semibold))
                    .foregroundStyle(strong ? scheme.onAccent : scheme.primary)
                    .frame(width: 24, height: 24)
                    .background(RoundedRectangle(cornerRadius: 24 * 0.3, style: .continuous)
                        .fill(strong ? scheme.onAccent.opacity(0.22) : scheme.primary.opacity(0.15)))
                Text(d.title).font(.system(size: 13.5, weight: selected ? .semibold : .regular))
                    .foregroundStyle(strong ? scheme.onAccent : Color.primary)
                    .lineLimit(1)
                Spacer(minLength: 4)
                if let badge, badge > 0 {
                    Text("\(badge)").font(.system(size: 11.5, weight: .semibold)).monospacedDigit()
                        .foregroundStyle(strong ? scheme.onAccent : Color.secondary)
                }
            }
            .padding(.vertical, 4).padding(.horizontal, 8)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background { if selected { highlight(strong) } }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .listRowInsets(EdgeInsets(top: 1, leading: 0, bottom: 1, trailing: 0))
        .listRowBackground(Color.clear)
    }

    /// The app's theme colour (or gradient), not the Mac's system accent. Softer while the window is in the background.
    @ViewBuilder func highlight(_ strong: Bool) -> some View {
        let shape = RoundedRectangle(cornerRadius: Radius.small + 1, style: .continuous)
        if !strong {
            shape.fill(scheme.primary.opacity(0.14))
        } else if let g = scheme.brandStrong {
            shape.fill(LinearGradient(colors: g, startPoint: .leading, endPoint: .trailing))
        } else {
            shape.fill(scheme.accent)
        }
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
