import SwiftUI
import AppKit
import DaylineCore

/// `Dayline --screenshots <folder>`: opens with sample data, captures every screen in light and dark, then quits.
/// Used to check the design; it never touches your real data.
@MainActor
enum ScreenshotMode {
    static var outputDirectory: URL? {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(of: "--screenshots"), i + 1 < args.count else { return nil }
        return URL(fileURLWithPath: args[i + 1], isDirectory: true)
    }

    static func scheduled(_ dir: URL) {
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(2.5))
            await run(dir)
            NSApp.terminate(nil)
        }
    }

    static var services: Services? {
        // Reach the app's services through the main window's environment owner.
        ServicesLocator.shared
    }

    static func run(_ dir: URL) async {
        guard let s = services, let window = NSApp.windows.first(where: { $0.identifier?.rawValue.contains("main") == true || $0.title == "Dayline" }) else { return }
        window.setFrame(NSRect(x: 60, y: 60, width: 1280, height: 820), display: true)
        NSApp.activate(ignoringOtherApps: true)
        window.makeKeyAndOrderFront(nil)
        try? await Task.sleep(for: .seconds(1.5))
        let shots: [(String, Destination)] = [("today", .today), ("calendar", .calendar), ("notes", .notes), ("habits", .habits), ("inbox", .inbox),
                                              ("upcoming", .upcoming), ("all", .all), ("focus", .focus), ("stats", .stats)]
        for (mode, appearance) in [("light", NSAppearance.Name.aqua), ("dark", NSAppearance.Name.darkAqua)] {
            NSApp.appearance = NSAppearance(named: appearance)
            for (name, d) in shots {
                s.nav.showInspector = false
                s.nav.go(d)
                try? await Task.sleep(for: .seconds(1.2))
                capture(window, dir.appendingPathComponent("\(name)-\(mode).png"))
            }
            // Task details panel
            s.nav.go(.today)
            s.nav.open(s.store.data.tasks.first { $0.title.hasPrefix("Draft launch") }?.id ?? 1)
            try? await Task.sleep(for: .seconds(1.2))
            capture(window, dir.appendingPathComponent("inspector-\(mode).png"))
            s.nav.showInspector = false
            // Note open in the editor
            s.nav.go(.notes)
            if let id = s.store.data.notes.first?.id { s.notesUI.open(id) }
            try? await Task.sleep(for: .seconds(1.5))
            capture(window, dir.appendingPathComponent("note-\(mode).png"))
            // Calendar week (time grid)
            UserDefaults.standard.set("Week", forKey: "calendar.mode")
            s.nav.go(.calendar)
            try? await Task.sleep(for: .seconds(1.2))
            capture(window, dir.appendingPathComponent("calendar-week-\(mode).png"))
            UserDefaults.standard.set("Month", forKey: "calendar.mode")
            // New task sheet
            s.nav.newTaskDraft = TaskDraft(task: TaskItem(id: 0, title: "Book flights for the offsite"))
            try? await Task.sleep(for: .seconds(1.2))
            if let sheet = window.attachedSheet { capture(sheet, dir.appendingPathComponent("newtask-\(mode).png")) }
            s.nav.newTaskDraft = nil
            try? await Task.sleep(for: .seconds(0.8))
        }
    }

    static func capture(_ window: NSWindow, _ url: URL) {
        guard let view = window.contentView?.superview ?? window.contentView else { return }
        let rect = view.bounds
        guard let rep = view.bitmapImageRepForCachingDisplay(in: rect) else { return }
        view.cacheDisplay(in: rect, to: rep)
        try? rep.representation(using: .png, properties: [:])?.write(to: url)
    }
}

@MainActor
enum ServicesLocator { static weak var shared: Services? }

/// Realistic sample data for screenshots.
enum DemoData {
    static func make() -> DaylineData {
        var d = DaylineData()
        let today = Day.today()
        let now = Date()
        func c(_ hex: UInt32) -> Int32 { Int32(bitPattern: hex) }
        var id: Int64 = 0
        func task(_ title: String, due: Int?, _ p: Priority = .MEDIUM, project: Int64? = nil, list: Int64? = nil, time: TimeOfDay? = nil, done: Bool = false, doneDaysAgo: Int = 0) -> TaskItem {
            id += 1
            var t = TaskItem(id: id, title: title)
            t.dueDate = due.map { today.plusDays($0) }; t.priority = p; t.dueTime = time
            t.isCompleted = done; t.completedAt = done ? now.addingTimeInterval(Double(-doneDaysAgo * 86400 - 3600)) : nil
            t.createdAt = now.addingTimeInterval(-86400 * 10); t.updatedAt = t.createdAt; t.sortOrder = id
            return t
        }
        d.tasks = [
            task("Draft launch announcement", due: 0, .HIGH, project: 1, time: TimeOfDay(hour: 11, minute: 0)),
            task("Review homepage mockups", due: 0, .URGENT, project: 1, list: 1),
            task("Call the plumber about the kitchen tap", due: 0, .MEDIUM, project: 2, time: TimeOfDay(hour: 16, minute: 30)),
            task("Renew car insurance", due: -2, .HIGH),
            task("Pick up dry cleaning", due: 0, .LOW, done: true),
            task("Morning run", due: 0, .MEDIUM, project: 3, done: true),
            task("Write blog post on habits", due: 1, .MEDIUM, project: 1),
            task("Book dentist appointment", due: 3, .LOW),
            task("Prepare quarterly review slides", due: 5, .HIGH),
            task("Plan weekend hike", due: 9, .LOW, project: 3),
            task("Oat milk, berries, spinach", due: nil, .LOW, list: 2),
            task("Look into standing desks", due: nil),
            task("Reply to Priya about the offsite", due: nil, .MEDIUM),
            task("Fix footer links", due: -5, .MEDIUM, project: 1, done: true, doneDaysAgo: 4),
            task("Set up analytics", due: -3, .MEDIUM, project: 1, done: true, doneDaysAgo: 2),
            task("Water the plants", due: 2, .LOW, project: 2),
        ]
        d.tasks[1].description = "Hero, pricing and FAQ sections"
        d.tasks[15].recurrence = RecurrenceRule(frequency: .WEEKLY, weekdays: [.monday, .thursday])
        d.tasks[2].recurrence = nil
        for k in 1...9 { d.tasks.append(task("Small task \(k)", due: -k, .LOW, done: true, doneDaysAgo: k % 6)) }
        d.subtasks = [Subtask(id: 1, taskId: 1, title: "Outline key points", isDone: true), Subtask(id: 2, taskId: 1, title: "Write first draft", position: 1),
                      Subtask(id: 3, taskId: 1, title: "Get feedback from Sam", position: 2)]
        d.tags = [Tag(id: 1, name: "work"), Tag(id: 2, name: "errands"), Tag(id: 3, name: "home")]
        d.taskTags = [TaskTag(taskId: 1, tagId: 1), TaskTag(taskId: 2, tagId: 1), TaskTag(taskId: 3, tagId: 3), TaskTag(taskId: 4, tagId: 2), TaskTag(taskId: 5, tagId: 2), TaskTag(taskId: 8, tagId: 2)]
        d.reminders = [Reminder(id: 1, taskId: 1, type: .DEADLINE, offsetMinutes: 30)]
        d.dependencies = [TaskDependency(taskId: 7, dependsOnTaskId: 1)]
        var h1 = Habit(id: 1, name: "Read 20 pages", color: c(0xFF4F5BD5), startDate: today.minusDays(60)); h1.icon = "book"
        var h2 = Habit(id: 2, name: "Drink water", color: c(0xFF00A2C7), startDate: today.minusDays(60)); h2.icon = "water"; h2.targetPerDay = 8
        var h3 = Habit(id: 3, name: "Workout", color: c(0xFFE0892B), startDate: today.minusDays(60)); h3.icon = "fitness"; h3.frequency = .WEEKLY_TARGET; h3.timesPerWeek = 3
        var h4 = Habit(id: 4, name: "Meditate", color: c(0xFF0E9F8E), startDate: today.minusDays(60)); h4.icon = "meditate"; h4.reminderTime = TimeOfDay(hour: 7, minute: 30)
        d.habits = [h1, h2, h3, h4]
        for back in 0..<120 {
            let day = today.minusDays(back)
            if back % 9 != 4 && back > 0 { d.habitCompletions.append(HabitCompletion(habitId: 1, date: day, count: 1)) }
            d.habitCompletions.append(HabitCompletion(habitId: 2, date: day, count: back == 0 ? 5 : (back % 4 == 0 ? 6 : 8)))
            if [1, 3, 5].contains(day.weekday.rawValue) && back > 0 { d.habitCompletions.append(HabitCompletion(habitId: 3, date: day, count: 1)) }
            if back % 3 != 0 { d.habitCompletions.append(HabitCompletion(habitId: 4, date: day, count: 1)) }
        }
        var n1 = Note(id: 1, title: "Launch checklist"); n1.isPinned = true; n1.category = "work"
        n1.body = NoteCodec.encode("<h2>Before launch</h2><p>Make sure the <b>pricing page</b> and <i>FAQ</i> are final.</p><p>☐ Test checkout on mobile</p><p>☑︎ Update social images</p>")
        n1.createdAt = now.addingTimeInterval(-86400 * 3); n1.updatedAt = now.addingTimeInterval(-3600)
        var n2 = Note(id: 2, title: "Book ideas"); n2.color = 3; n2.category = "reading"
        n2.body = NoteCodec.encode("<p>The Overstory, Project Hail Mary, Piranesi</p>", checklist: [CheckItem(text: "Ask Sam for recommendations"), CheckItem(text: "Library card", done: true)])
        n2.updatedAt = now.addingTimeInterval(-86400)
        var n3 = Note(id: 3, title: "Weekend"); n3.color = 4
        n3.body = "<p>Farmers market Saturday morning, then the coastal trail if the weather holds.</p>"; n3.updatedAt = now.addingTimeInterval(-86400 * 2)
        var n4 = Note(id: 4, title: ""); n4.body = "<p>Gift idea for mum: a good pair of gardening gloves #home</p>"; n4.category = "home"; n4.updatedAt = now.addingTimeInterval(-86400 * 5)
        d.notes = [n1, n2, n3, n4]
        for back in 0..<20 {
            let minutes = [50, 25, 75, 0, 100, 25, 50][back % 7]
            guard minutes > 0 else { continue }
            let start = today.minusDays(back).date(at: TimeOfDay(hour: 10, minute: 0))
            d.focusSessions.append(FocusSession(startedAt: start, endedAt: start.addingTimeInterval(Double(minutes * 60)), focusedMillis: Int64(minutes * 60_000),
                                                plannedMillis: Int64(minutes * 60_000), taskId: 1, projectId: nil, completed: true))
        }
        d.settings = ["user_name": .string("Vishal"), "accent_color": .int(Int64(Int32(bitPattern: 0xFF2F7DF6)))]
        return d
    }
}
