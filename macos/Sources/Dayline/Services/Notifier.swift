import Foundation
import AppKit
import UserNotifications
import DaylineCore

/// Schedules reminders as macOS notifications (tasks, custom reminders, habits, focus timer).
@MainActor
final class Notifier: NSObject, UNUserNotificationCenterDelegate {
    private let store: Store
    private var pending: Task<Void, Never>?
    var openTask: ((Int64) -> Void)?
    var openFocus: (() -> Void)?
    private let demo: Bool
    private var center: UNUserNotificationCenter? { demo || Bundle.main.bundleIdentifier == nil ? nil : UNUserNotificationCenter.current() }

    init(store: Store, demo: Bool = false) {
        self.store = store
        self.demo = demo
        super.init()
        guard let c = center else { return }
        c.delegate = self
        let complete = UNNotificationAction(identifier: "COMPLETE", title: "Complete")
        let snooze = UNNotificationAction(identifier: "SNOOZE", title: "Snooze 10 min")
        let done = UNNotificationAction(identifier: "HABIT_DONE", title: "Done")
        c.setNotificationCategories([
            UNNotificationCategory(identifier: "TASK", actions: [complete, snooze], intentIdentifiers: []),
            UNNotificationCategory(identifier: "REMINDER", actions: [snooze], intentIdentifiers: []),
            UNNotificationCategory(identifier: "HABIT", actions: [done], intentIdentifiers: []),
        ])
    }

    func requestPermission() {
        center?.requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    func authorizationStatus() async -> UNAuthorizationStatus {
        guard let c = center else { return .denied }
        return await c.notificationSettings().authorizationStatus
    }

    /// Rebuilds the queue of upcoming notifications shortly after data changes.
    func reschedule() {
        pending?.cancel()
        pending = Task { [weak self] in
            try? await Task.sleep(for: .seconds(1))
            guard !Task.isCancelled else { return }
            await self?.rebuild()
        }
    }

    private func rebuild() async {
        guard let c = center else { return }
        let now = DayTime(Date())
        let horizon = DayTime(day: now.day.plusDays(14), time: .endOfDay)
        var items: [(DayTime, UNMutableNotificationContent, String)] = []
        let data = store.data

        if store.taskReminders {
            let tasks = Dictionary(uniqueKeysWithValues: data.tasks.map { ($0.id, $0) })
            for r in data.reminders {
                let task = r.taskId.flatMap { tasks[$0] }
                guard let at = ReminderPlanner.triggerTime(r, task: task, defaultTime: store.defaultReminderTime),
                      ReminderPlanner.shouldSchedule(r, task: task, trigger: at, now: now), at < horizon else { continue }
                let content = UNMutableNotificationContent()
                if let t = task {
                    content.title = t.title
                    var parts: [String] = []
                    if let due = Formats(store: store).due(t) { parts.append("Due \(due)") }
                    if let p = store.project(t.projectId) { parts.append(p.name) }
                    content.body = parts.joined(separator: " · ")
                    content.categoryIdentifier = "TASK"
                    content.userInfo = ["taskId": t.id]
                } else {
                    content.title = r.title.isEmpty ? "Reminder" : r.title
                    content.categoryIdentifier = "REMINDER"
                    content.userInfo = ["reminderId": r.id]
                }
                content.sound = .default
                items.append((at, content, "dl.r.\(r.id)"))
            }
        }
        if store.habitReminders {
            for h in data.habits where h.reminderTime != nil && !h.isArchived {
                let done = HabitStreaks.isDone(h, store.count(h.id, on: now.day))
                var from = now
                for i in 0..<3 {
                    guard let at = ReminderPlanner.nextHabitReminder(h, now: from, doneToday: i == 0 && done), at < horizon else { break }
                    let content = UNMutableNotificationContent()
                    content.title = h.name
                    content.body = h.targetPerDay > 1 ? "Time for today's \(h.targetPerDay) check-ins" : "Time to keep your streak going"
                    content.categoryIdentifier = "HABIT"
                    content.userInfo = ["habitId": h.id, "day": at.day.epochDay]
                    content.sound = .default
                    items.append((at, content, "dl.h.\(h.id).\(at.day.epochDay)"))
                    from = DayTime(day: at.day, time: .endOfDay)
                }
            }
        }
        items.sort { $0.0 < $1.0 }
        let requests = items.prefix(60).map { at, content, id -> UNNotificationRequest in
            let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute, .second], from: at.date)
            return UNNotificationRequest(identifier: id, content: content, trigger: UNCalendarNotificationTrigger(dateMatching: comps, repeats: false))
        }
        let existing = await c.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix("dl.r.") || $0.hasPrefix("dl.h.") }
        c.removePendingNotificationRequests(withIdentifiers: existing)
        for r in requests { try? await c.add(r) }
    }

    func focusFinished(_ phase: FocusPhase, next: FocusTimerState) {
        guard store.focusNotifications, let c = center else { return }
        let content = UNMutableNotificationContent()
        content.title = phase == .FOCUS ? "Focus session complete" : "Break's over"
        content.body = phase == .FOCUS ? "Nice work. Time for a \(next.phase == .LONG_BREAK ? "long" : "short") break." : "Ready for another focus session?"
        content.sound = .default
        content.userInfo = ["focus": true]
        c.add(UNNotificationRequest(identifier: "dl.focus.\(UUID().uuidString)", content: content, trigger: nil))
    }

    // MARK: Delegate

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification) async -> UNNotificationPresentationOptions {
        [.banner, .sound, .list]
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse) async {
        let info = response.notification.request.content.userInfo
        let action = response.actionIdentifier
        let content = response.notification.request.content
        let taskId = (info["taskId"] as? NSNumber)?.int64Value
        let habitId = (info["habitId"] as? NSNumber)?.int64Value
        let day = (info["day"] as? NSNumber)?.intValue
        let isFocus = info["focus"] != nil
        await MainActor.run {
            switch action {
            case "COMPLETE":
                if let taskId { store.setCompleted(taskId, true) }
            case "SNOOZE":
                let c = UNMutableNotificationContent()
                c.title = content.title; c.body = content.body; c.categoryIdentifier = content.categoryIdentifier
                c.userInfo = content.userInfo; c.sound = .default
                center.add(UNNotificationRequest(identifier: "dl.snooze.\(UUID().uuidString)", content: c,
                                                 trigger: UNTimeIntervalNotificationTrigger(timeInterval: 600, repeats: false)))
            case "HABIT_DONE":
                if let habitId, let h = store.habit(habitId) {
                    let d = day.map(Day.init(epochDay:)) ?? store.today
                    if !HabitStreaks.isDone(h, store.count(habitId, on: d)) { store.checkIn(habitId, on: d, delta: max(1, h.targetPerDay) - store.count(habitId, on: d)) }
                }
            default:
                NSApp.activate(ignoringOtherApps: true)
                if let taskId { openTask?(taskId) } else if isFocus { openFocus?() }
            }
        }
    }
}
