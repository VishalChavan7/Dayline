import Foundation
import AppKit
import WidgetKit
import DaylineCore

/// Keeps the desktop widgets current: writes what they show after every change, and applies
/// the ticks and taps you make in them (queued by the widgets, because only the app may change your data).
@MainActor
final class WidgetBridge {
    private let store: Store
    private let timer: FocusTimer
    private let demo: Bool
    private var pending: Task<Void, Never>?
    private var poll: Timer?
    private var last: WidgetSnapshot?
    var onFocusAction: (() -> Void)?

    init(store: Store, timer: FocusTimer, demo: Bool) {
        self.store = store
        self.timer = timer
        self.demo = demo
        guard !demo else { return }
        // Widget taps arrive as a small queue file; look for it often (it's a single file check).
        let t = Timer(timeInterval: 1.5, repeats: true) { [weak self] _ in Task { @MainActor in self?.applyQueued() } }
        RunLoop.main.add(t, forMode: .common)
        poll = t
    }

    /// Something changed: refresh the widgets shortly.
    func changed() {
        guard !demo else { return }
        pending?.cancel()
        pending = Task { [weak self] in
            try? await Task.sleep(for: .milliseconds(600))
            guard !Task.isCancelled else { return }
            self?.publish()
        }
    }

    func publish() {
        guard !demo else { return }
        var s = snapshot()
        if var prev = last {
            prev.generatedAt = s.generatedAt; prev.focus.endsAt = s.focus.endsAt
            if prev == s { return }
        }
        s.generatedAt = Date()
        last = s
        WidgetFiles.write(s)
        WidgetCenter.shared.reloadAllTimelines()
    }

    func applyQueued() {
        let actions = WidgetFiles.takeActions()
        guard !actions.isEmpty else { return }
        let today = store.today
        for a in actions {
            switch a.kind {
            case .task:
                if let t = store.task(a.id) { store.setCompleted(a.id, !t.isCompleted) }
            case .habit:
                if let h = store.habit(a.id) {
                    let done = HabitStreaks.isDone(h, store.count(h.id, on: today))
                    if h.targetPerDay > 1 && !done { store.checkIn(h.id, on: today, delta: 1) } else { store.toggle(h.id, on: today) }
                }
            case .focus:
                timer.toggle()
            }
        }
        last = nil
        publish()
    }

    private func rank(_ p: Priority) -> Int {
        switch p { case .URGENT: 3; case .HIGH: 2; case .MEDIUM: 1; case .LOW: 0 }
    }

    /// What the widgets show right now.
    func snapshot() -> WidgetSnapshot {
        var s = WidgetSnapshot()
        let today = store.today
        let f = Formats(store: store)
        s.weekday = today.weekday.name
        s.dateText = f.date(today, relative: false)
        func row(_ t: TaskItem) -> WidgetSnapshot.TaskRow {
            let overdue = !t.isCompleted && t.isOverdue(today)
            let time = (t.dueTime ?? t.startTime).map { f.time($0) }
            let when: String? = overdue ? t.scheduledDate.map { f.date($0) } : time
            return .init(id: t.id, title: t.title, done: t.isCompleted, when: when, priority: rank(t.priority), overdue: overdue)
        }
        let open = store.openTasks
        let overdue = open.filter { $0.isOverdue(today) }.sorted(by: TaskGrouping.chronological)
        let todays = TaskGrouping.onDay(open, today).filter { !$0.isOverdue(today) }
            .sorted { a, b in a.priority != b.priority ? a.priority > b.priority : TaskGrouping.chronological(a, b) }
        let doneToday = store.data.tasks.filter { t in t.isCompleted && t.completedAt.map { Day($0) == today } ?? false }
        s.tasks = (overdue + todays).map(row) + doneToday.map(row)
        let habits = store.activeHabits.filter { HabitStreaks.dueOn($0, today, store.completions(of: $0.id), weekStart: store.weekStart) }
        s.habits = habits.map { h in
            let n = store.count(h.id, on: today)
            let st = HabitStreaks.stats(h, store.completions(of: h.id), today: today, weekStart: store.weekStart)
            return .init(id: h.id, name: h.name, symbol: Icons.symbol(h.icon), color: UInt32(bitPattern: h.color) & 0xFFFFFF,
                         count: n, target: max(1, h.targetPerDay), done: HabitStreaks.isDone(h, n), streak: st.currentStreak)
        }
        s.done = doneToday.count + s.habits.filter(\.done).count
        s.total = overdue.count + todays.count + doneToday.count + s.habits.count
        // Next: the earliest timed task still to come today, else the most urgent open one.
        let nowTime = TimeOfDay(Date())
        let timed = todays.filter { ($0.dueTime ?? $0.startTime).map { $0 >= nowTime } ?? false }
            .sorted { ($0.dueTime ?? $0.startTime)! < ($1.dueTime ?? $1.startTime)! }
        s.next = (timed.first ?? overdue.first ?? todays.first).map(row)
        // Focus
        let st = timer.state
        s.focus.running = st.status == .RUNNING
        s.focus.paused = st.status == .PAUSED
        s.focus.phase = st.phase.label
        let remaining = Int((timer.remaining + 999) / 1000)
        s.focus.remainingSeconds = max(0, remaining)
        s.focus.endsAt = s.focus.running ? Date().addingTimeInterval(Double(remaining)) : nil
        s.focus.minutesToday = store.focusMinutes(on: today)
        s.focus.goalMinutes = store.focusConfig.dailyGoalMinutes
        // Theme
        let theme = store.accentColor
        if theme == ThemeStyles.monochrome { s.monochrome = true; s.accent = 0x3A3A3A }
        else if let g = ThemeStyles.gradient(theme) { s.accent = g.start; s.accentEnd = g.end }
        else if theme == ThemeStyles.dynamic { s.accent = UInt32(bitPattern: ThemeStyles.systemAccent) & 0xFFFFFF }
        else if (UInt32(bitPattern: theme) >> 24) != 0 { s.accent = UInt32(bitPattern: theme) & 0xFFFFFF }
        s.generatedAt = Date()
        return s
    }
}
