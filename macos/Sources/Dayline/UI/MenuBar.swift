import SwiftUI
import AppKit
import DaylineCore

/// Menu bar icon: shows the focus countdown while a session runs.
struct MenuBarLabel: View {
    @ObservedObject var timer: FocusTimer
    @ObservedObject var store: Store
    var body: some View {
        if timer.state.status == .RUNNING {
            Text("\(Image(systemName: timer.state.phase == .FOCUS ? "timer" : "cup.and.saucer")) \(timer.remainingText)").monospacedDigit()
        } else {
            let left = TaskGrouping.onDay(store.openTasks, store.today).count + store.openTasks.filter { $0.isOverdue(store.today) }.count
            Image(systemName: left > 0 ? "sun.max.fill" : "sun.max")
        }
    }
}

/// The menu bar window: today's tasks and habits, quick add, and the focus timer — without opening the app.
struct MenuBarPanel: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var nav: Navigator
    @EnvironmentObject var timer: FocusTimer
    @Environment(\.openWindow) var openWindow

    var body: some View {
        let today = store.today
        let tasks = (store.openTasks.filter { $0.isOverdue(today) } + TaskGrouping.onDay(store.openTasks, today).filter { !$0.isOverdue(today) })
            .sorted { a, b in a.priority != b.priority ? a.priority > b.priority : TaskGrouping.chronological(a, b) }
        let habits = store.activeHabits.filter { HabitStreaks.dueOn($0, today, store.completions(of: $0.id), weekStart: store.weekStart) }
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Today").font(.headline)
                Text(Date().formatted(.dateTime.weekday(.wide).month().day())).foregroundStyle(.secondary)
                Spacer()
                Button { open(.today) } label: { Image(systemName: "macwindow") }.buttonStyle(.borderless).help("Open Dayline")
            }
            QuickAddField(date: today, placeholder: "Add a task for today")
            if tasks.isEmpty {
                Text("All clear for today.").foregroundStyle(.secondary).padding(.vertical, 4)
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 6) {
                        ForEach(tasks.prefix(12)) { t in TaskRow(task: t, showProject: true, showDate: t.isOverdue(today)) }
                    }
                }
                .frame(maxHeight: 260)
            }
            if !habits.isEmpty {
                Divider()
                FlowRow(spacing: 6) { ForEach(habits) { HabitChip(habit: $0, day: today) } }
            }
            Divider()
            HStack(spacing: 10) {
                ZStack {
                    Ring(progress: timer.progress, lineWidth: 4, color: timer.state.phase == .FOCUS ? .orange : .teal)
                    Image(systemName: "timer").font(.caption)
                }.frame(width: 30, height: 30)
                VStack(alignment: .leading, spacing: 0) {
                    Text(timer.state.status == .IDLE ? "Focus" : timer.state.phase.label).font(.callout.weight(.semibold))
                    Text(timer.state.status == .IDLE ? "\(Formats.minutes(store.focusMinutes(on: today))) today" : timer.remainingText).font(.caption.monospacedDigit()).foregroundStyle(.secondary)
                }
                Spacer()
                if timer.state.status != .IDLE { Button { timer.stop() } label: { Image(systemName: "stop.fill") }.help("Stop") }
                Button { timer.toggle() } label: { Image(systemName: timer.state.status == .RUNNING ? "pause.fill" : "play.fill") }
                    .help(timer.state.status == .RUNNING ? "Pause" : "Start focus")
            }
            .buttonStyle(.borderless)
            HStack {
                Button("Open Dayline") { open(.today) }
                Spacer()
                Button("Quit") { NSApp.terminate(nil) }
            }
            .buttonStyle(.borderless).font(.callout)
        }
        .padding(14)
        .frame(width: 340)
    }

    func open(_ d: Destination) {
        nav.go(d)
        openWindow(id: "main")
        NSApp.activate(ignoringOtherApps: true)
    }
}

struct Onboarding: View {
    @EnvironmentObject var store: Store
    @EnvironmentObject var sync: SyncService
    @EnvironmentObject var services: Services
    @Environment(\.dismiss) var dismiss
    @State private var name = ""
    @State private var page = 0

    var body: some View {
        VStack(spacing: 18) {
            Image(nsImage: NSApp.applicationIconImage).resizable().frame(width: 88, height: 88)
            if page == 0 {
                Text("Welcome to Dayline").font(.largeTitle.weight(.bold))
                Text("Tasks, calendar, habits, focus and notes — the same Dayline you use on your phone, now on your Mac.")
                    .multilineTextAlignment(.center).foregroundStyle(.secondary).frame(maxWidth: 380)
                TextField("What should we call you?", text: $name).textFieldStyle(.roundedBorder).frame(width: 260).onSubmit { page = 1 }
                Button("Continue") { if !name.trimmingCharacters(in: .whitespaces).isEmpty { store.set("user_name", .string(name)) }; page = 1 }
                    .buttonStyle(.borderedProminent).keyboardShortcut(.defaultAction)
            } else {
                Text("Bring your phone's data").font(.title.weight(.bold))
                Text("Connect the Google account you use for Dayline on your phone. Your tasks, habits and notes sync both ways through a private file in your own Google Drive.")
                    .multilineTextAlignment(.center).foregroundStyle(.secondary).frame(maxWidth: 400)
                if sync.enabled {
                    Label(sync.statusText, systemImage: "checkmark.icloud").foregroundStyle(StatusColor.success)
                } else {
                    Button { sync.connect() } label: { Label("Connect Google Account", systemImage: "person.crop.circle.badge.checkmark") }
                        .buttonStyle(.borderedProminent).controlSize(.large)
                    if case .failed(let m) = sync.status { Text(m).font(.caption).foregroundStyle(StatusColor.overdue) }
                }
                Button(sync.enabled ? "Start Using Dayline" : "Skip for Now") { services.notifier.requestPermission(); dismiss() }
                    .keyboardShortcut(.defaultAction)
            }
        }
        .padding(36)
        .frame(width: 520)
        .onAppear { name = store.userName }
    }
}
