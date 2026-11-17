import SwiftUI
import AppKit
import DaylineCore

/// Everything the app runs, created once.
@MainActor
final class Services: ObservableObject {
    let store: Store
    let nav = Navigator()
    let notesUI = NotesUI()
    let timer: FocusTimer
    let notifier: Notifier
    let sync: SyncService
    let updater = Updater()

    let demo: Bool

    init(store: Store, demo: Bool = false) {
        self.store = store
        self.demo = demo
        timer = FocusTimer(store: store, demo: demo)
        notifier = Notifier(store: store, demo: demo)
        sync = SyncService(store: store, demo: demo)
        store.onChange = { [weak self] in
            self?.notifier.reschedule()
            self?.sync.scheduleSoon()
        }
        timer.onPhaseFinished = { [weak self] p, next in self?.notifier.focusFinished(p, next: next) }
        notifier.openTask = { [weak self] id in self?.nav.open(id) }
        notifier.openFocus = { [weak self] in self?.nav.go(.focus) }
        ServicesLocator.shared = self
    }

    /// "New …" from the toolbar, menus and ⌘N.
    func create(_ kind: CreateKind) {
        switch kind {
        case .task: nav.newTaskDraft = newTaskDraft()
        case .note: nav.go(.notes); notesUI.newNote()
        case .habit: nav.editingHabit = Habit(id: 0, name: "", color: Palette.itemColors[store.data.habits.count % Palette.itemColors.count], startDate: store.today)
        case .project: nav.editingProject = Project(id: 0, name: "", color: Palette.itemColors[store.data.projects.count % Palette.itemColors.count])
        case .list: nav.editingList = TaskList(id: 0, name: "", color: Palette.itemColors[2])
        }
    }

    /// A new task prefilled from where you are (project, list, or today).
    func newTaskDraft() -> TaskDraft {
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

    func launched() {
        guard !demo else { return }
        notifier.reschedule()
        sync.syncIfEnabled()
        updater.checkSoonIfStale()
    }

    func becameActive() {
        guard !demo else { return }
        timer.catchUp()
        sync.scheduleSoon(2)
        updater.checkSoonIfStale()
        objectWillChange.send() // refresh "today" after midnight
    }
}

final class AppDelegate: NSObject, NSApplicationDelegate {
    func applicationShouldTerminateAfterLastWindowClosed(_ sender: NSApplication) -> Bool { false }
}

@main
struct DaylineApp: App {
    @NSApplicationDelegateAdaptor(AppDelegate.self) var delegate
    @StateObject private var services: Services
    @AppStorage("menuBarExtra") private var showMenuBar = true

    init() {
        if let dir = ScreenshotMode.outputDirectory {
            _services = StateObject(wrappedValue: Services(store: Store(fileURL: FileManager.default.temporaryDirectory.appendingPathComponent("dayline-demo.json"), data: DemoData.make()), demo: true))
            ScreenshotMode.scheduled(dir)
        } else {
            _services = StateObject(wrappedValue: Services(store: Store()))
        }
    }

    var body: some Scene {
        Window("Dayline", id: "main") {
            MainWindow()
                .environmentObject(services)
                .environmentObject(services.store)
                .environmentObject(services.nav)
                .environmentObject(services.notesUI)
                .environmentObject(services.timer)
                .environmentObject(services.sync)
                .environmentObject(services.updater)
        }
        .defaultSize(width: 1180, height: 780)
        .commands { DaylineCommands(services: services) }

        Settings {
            SettingsView()
                .environmentObject(services)
                .environmentObject(services.store)
                .environmentObject(services.timer)
                .environmentObject(services.sync)
                .environmentObject(services.updater)
                .themed(services.store)
        }

        MenuBarExtra(isInserted: $showMenuBar) {
            MenuBarPanel()
                .environmentObject(services)
                .environmentObject(services.store)
                .environmentObject(services.nav)
                .environmentObject(services.timer)
                .environmentObject(services.sync)
                .themed(services.store)
        } label: {
            MenuBarLabel(timer: services.timer, store: services.store)
        }
        .menuBarExtraStyle(.window)
    }
}

extension View {
    /// Accent and light/dark from the shared settings.
    func themed(_ store: Store) -> some View {
        tint(Color(argb: store.accentColor))
            .accentColor(Color(argb: store.accentColor))
            .preferredColorScheme(store.themeMode == "DARK" ? .dark : store.themeMode == "LIGHT" ? .light : nil)
    }
}

struct MainWindow: View {
    @EnvironmentObject var services: Services
    @EnvironmentObject var store: Store
    @EnvironmentObject var updater: Updater
    @Environment(\.scenePhase) var phase
    @AppStorage("onboarded") private var onboarded = false

    var body: some View {
        RootView()
            .frame(minWidth: 900, minHeight: 600)
            .themed(store)
            .onAppear { services.launched() }
            .onChange(of: phase) { _, p in if p == .active { services.becameActive() } }
            .sheet(isPresented: Binding(get: { !onboarded && !services.demo }, set: { if !$0 { onboarded = true } })) { Onboarding() }
            .alert("Dayline \(readyVersion) is ready", isPresented: $updater.showPrompt) {
                Button("Install and Relaunch") { updater.installAndRelaunch() }
                Button("Later", role: .cancel) {}
            } message: { Text(updateNotes) }
    }

    var readyVersion: String { if case .ready(let v) = updater.state { return v }; return "" }
    var updateNotes: String { "Dayline updates in a few seconds and reopens where you left off." }
}

struct DaylineCommands: Commands {
    let services: Services
    var body: some Commands {
        CommandGroup(replacing: .newItem) {
            Button("New Task") { services.create(.task) }.keyboardShortcut("n")
            Button("New Note") { services.create(.note) }.keyboardShortcut("n", modifiers: [.command, .option])
            Button("New Habit") { services.create(.habit) }.keyboardShortcut("h", modifiers: [.command, .shift])
            Button("New Project") { services.create(.project) }
            Button("New List") { services.create(.list) }
        }
        CommandGroup(after: .appInfo) {
            Button("Check for Updates…") { Task { await services.updater.check(userInitiated: true); if services.updater.state == .upToDate { NSSound.beep() } } }
        }
        CommandMenu("Go") {
            ForEach(Array(Destination.main.enumerated()), id: \.offset) { i, d in
                Button(d.title) { services.nav.go(d) }.keyboardShortcut(KeyEquivalent(Character("\(i + 1)")))
            }
            Divider()
            Button("Completed") { services.nav.go(.completed) }
        }
        CommandMenu("Focus") {
            Button("Start or Pause Timer") { services.timer.toggle() }.keyboardShortcut("f", modifiers: [.command, .shift])
            Button("Stop Timer") { services.timer.stop() }
            Button("Skip to Next Phase") { services.timer.skip() }
        }
        CommandGroup(after: .toolbar) {
            Button("Sync Now") { Task { await services.sync.syncNow() } }.keyboardShortcut("s", modifiers: [.command, .shift])
            Button("Show Task Details") { services.nav.showInspector.toggle() }.keyboardShortcut("i", modifiers: [.command, .option])
        }
    }
}

struct SyncButton: View {
    @EnvironmentObject var sync: SyncService
    var body: some View {
        if sync.enabled {
            Button { Task { await sync.syncNow() } } label: {
                Label("Sync", systemImage: sync.status == .syncing ? "arrow.triangle.2.circlepath" : {
                    if case .failed = sync.status { return "exclamationmark.icloud" }
                    return "checkmark.icloud"
                }())
            }
            .help(sync.statusText)
            .disabled(sync.status == .syncing)
        }
    }
}

struct SyncFooter: View {
    @EnvironmentObject var sync: SyncService
    var body: some View {
        if sync.enabled {
            HStack(spacing: 6) {
                if sync.status == .syncing { ProgressView().controlSize(.small) }
                else if case .failed = sync.status { Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange) }
                else { Image(systemName: "checkmark.icloud").foregroundStyle(.secondary) }
                Text(sync.statusText).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                Spacer()
            }
            .padding(.horizontal, 14).padding(.vertical, 8)
        }
    }
}
