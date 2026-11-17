import AppIntents
import WidgetKit
import DaylineCore

/// Widget buttons. Each one queues the change for the app (which owns your data and syncs it) and
/// updates what the widget shows straight away.

struct ToggleTaskIntent: AppIntent {
    static var title: LocalizedStringResource = "Complete Task"
    static var description = IntentDescription("Ticks a task in Dayline.")
    static var isDiscoverable = false

    @Parameter(title: "Task") var taskId: Int

    init() {}
    init(id: Int64) { taskId = Int(id) }

    func perform() async throws -> some IntentResult {
        let id = Int64(taskId)
        WidgetFiles.append(WidgetAction(kind: .task, id: id))
        if var s = WidgetFiles.readSnapshot() {
            if let i = s.tasks.firstIndex(where: { $0.id == id }) {
                s.tasks[i].done.toggle()
                s.done += s.tasks[i].done ? 1 : -1
            }
            if s.next?.id == id { s.next?.done.toggle() }
            WidgetFiles.write(s)
        }
        return .result()
    }
}

struct ToggleHabitIntent: AppIntent {
    static var title: LocalizedStringResource = "Check In Habit"
    static var description = IntentDescription("Checks in a habit for today in Dayline.")
    static var isDiscoverable = false

    @Parameter(title: "Habit") var habitId: Int

    init() {}
    init(id: Int64) { habitId = Int(id) }

    func perform() async throws -> some IntentResult {
        let id = Int64(habitId)
        WidgetFiles.append(WidgetAction(kind: .habit, id: id))
        if var s = WidgetFiles.readSnapshot(), let i = s.habits.firstIndex(where: { $0.id == id }) {
            var h = s.habits[i]
            let wasDone = h.done
            if h.target > 1 && !h.done { h.count += 1 } else { h.count = h.done ? 0 : max(1, h.target) }
            h.done = h.count >= max(1, h.target)
            s.habits[i] = h
            if wasDone != h.done { s.done += h.done ? 1 : -1 }
            WidgetFiles.write(s)
        }
        return .result()
    }
}

struct ToggleFocusIntent: AppIntent {
    static var title: LocalizedStringResource = "Start or Pause Focus"
    static var description = IntentDescription("Starts or pauses the Dayline focus timer.")
    static var isDiscoverable = false

    init() {}

    func perform() async throws -> some IntentResult {
        WidgetFiles.append(WidgetAction(kind: .focus, id: 0))
        if var s = WidgetFiles.readSnapshot() {
            if s.focus.running {
                s.focus.running = false; s.focus.paused = true
                if let end = s.focus.endsAt { s.focus.remainingSeconds = max(0, Int(end.timeIntervalSinceNow)) }
                s.focus.endsAt = nil
            } else {
                s.focus.running = true; s.focus.paused = false
                s.focus.endsAt = Date().addingTimeInterval(Double(s.focus.remainingSeconds))
            }
            WidgetFiles.write(s)
        }
        return .result()
    }
}
