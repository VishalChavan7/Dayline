import SwiftUI
import AppKit
import DaylineCore
import DaylineEditor

/// `Dayline --screenshots <folder>`: opens with sample data, captures every screen in light and dark, then quits.
/// Used to check the design; it never touches your real data.
@MainActor
enum ScreenshotMode {
    static var outputDirectory: URL? {
        let args = CommandLine.arguments
        guard let i = args.firstIndex(where: { $0 == "--screenshots" || $0 == "--story" }), i + 1 < args.count else { return nil }
        return URL(fileURLWithPath: args[i + 1], isDirectory: true)
    }
    /// `--story`: the frames for the phone + Mac sync video, using the same sample data as the phone's screenshots.
    static var story: Bool { CommandLine.arguments.contains("--story") }

    static func scheduled(_ dir: URL) {
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(2.5))
            if story { await runStory(dir) } else { await run(dir) }
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
        let shots: [(String, Destination)] = [("today", .today), ("calendar", .calendar), ("notes", .notes), ("journal", .journal), ("habits", .habits), ("inbox", .inbox),
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
            // A brand-new note, typed into like a person would: the text must be readable.
            s.notesUI.newNote()
            try? await Task.sleep(for: .seconds(1.0))
            if let tv = findTextView(window.contentView) {
                window.makeFirstResponder(tv)
                tv.insertText("Typed into a new note.", replacementRange: tv.selectedRange())
                try? await Task.sleep(for: .seconds(1.6))   // the note saves and gets its id
                let tv2 = findTextView(window.contentView) ?? tv
                if tv2 !== tv { window.makeFirstResponder(tv2) }
                tv2.insertText(" More words after saving.", replacementRange: NSRange(location: (tv2.string as NSString).length, length: 0))
                try? await Task.sleep(for: .seconds(1.0))
                report(dir, "newnote-\(mode)", tv2)
            }
            capture(window, dir.appendingPathComponent("newnote-\(mode).png"))
            // Text pasted from another app (white, big) must come out in the note's own ink.
            if let tv = findTextView(window.contentView) as? NoteTextView {
                let pb = NSPasteboard(name: NSPasteboard.Name("dayline-shots-\(mode)"))
                pb.clearContents()
                let white = NSAttributedString(string: " Pasted white text.", attributes: [.foregroundColor: NSColor.white, .font: NSFont.boldSystemFont(ofSize: 28)])
                pb.setData(white.rtf(from: NSRange(location: 0, length: white.length)), forType: .rtf)
                tv.setSelectedRange(NSRange(location: (tv.string as NSString).length, length: 0))
                _ = tv.readSelection(from: pb, type: .rtf)
                try? await Task.sleep(for: .seconds(0.8))
                report(dir, "paste-\(mode)", tv)
                pb.releaseGlobally()
            }
            // Every kind of block, with the table of contents open.
            var showcase = Note(id: 0, title: "Launch plan")
            showcase.category = "work/launch"
            showcase.body = "<p><span style=\"font-size: 21.0px;\"><b>Goals</b></span></p><p>Ship the <b>new editor</b> with <span style=\"color: rgba(26, 115, 232, 1.0);\">colours</span> and <span style=\"color: rgba(31, 31, 31, 1.0); background: rgba(255, 241, 118, 1.0);\">highlights</span>. See [[Release notes]].</p>"
                + "<ul><li>Bulleted item<ul><li>Nested item</li></ul></li></ul><ol><li>First</li><li>Second</li></ol><p>\u{2610} Write the changelog</p><p>\u{2611}\u{FE0E} Test both themes</p>"
                + "<blockquote><p>Simple things should be simple.</p></blockquote><div class=\"callout\" data-kind=\"tip\"><p>Press Tab in a table to move to the next cell.</p></div>"
                + "<pre><code class=\"language-swift\">let note = Note(title: \"Hello\")\nprint(note)</code></pre>"
                + "<table><tr><th>Platform</th><th>Version</th></tr><tr><td>Android</td><td>1.1.4</td></tr><tr><td>Mac</td><td>1.3.2</td></tr></table><hr><p><span style=\"font-size: 18.0px; font-weight: 600;\">Next</span></p><p>Done.</p>"
            let sid = s.store.save(showcase)
            s.notesUI.layout = 3
            s.notesUI.filter = .all
            s.notesUI.open(sid)
            s.notesUI.panel = .contents
            try? await Task.sleep(for: .seconds(1.6))
            capture(window, dir.appendingPathComponent("noteblocks-\(mode).png"))
            if let tv = findTextView(window.contentView) { report(dir, "noteblocks-\(mode)", tv) }
            s.notesUI.panel = nil
            // A locked note, and the Trash.
            s.store.setLocked(note: sid, true)
            s.notesUI.open(sid)
            try? await Task.sleep(for: .seconds(1.0))
            capture(window, dir.appendingPathComponent("notelocked-\(mode).png"))
            s.store.setLocked(note: sid, false)
            s.store.trashNote(sid)
            s.notesUI.filter = .trash
            s.notesUI.open(sid)
            try? await Task.sleep(for: .seconds(1.2))
            capture(window, dir.appendingPathComponent("notetrash-\(mode).png"))
            s.store.deleteNote(sid)
            s.notesUI.filter = .all
            s.notesUI.close()
            // Journal entry open
            s.nav.go(.journal)
            if let id = s.store.journalEntries.first?.id { s.journalUI.open(id) }
            try? await Task.sleep(for: .seconds(1.2))
            capture(window, dir.appendingPathComponent("journal-open-\(mode).png"))
            s.journalUI.selected = nil
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

    /// Before/after frames: a task added on the Mac, a habit ticked on the phone, a journal entry written on the Mac.
    static func runStory(_ dir: URL) async {
        guard let s = services, let window = NSApp.windows.first(where: { $0.identifier?.rawValue.contains("main") == true || $0.title == "Dayline" }) else { return }
        window.setFrame(NSRect(x: 60, y: 60, width: 1280, height: 820), display: true)
        NSApp.activate(ignoringOtherApps: true)
        window.makeKeyAndOrderFront(nil)
        NSApp.appearance = NSAppearance(named: .aqua)
        try? await Task.sleep(for: .seconds(1.5))
        func shot(_ name: String) async {
            try? await Task.sleep(for: .seconds(1.2))
            capture(window, dir.appendingPathComponent("story-\(name).png"))
        }
        s.nav.go(.today); await shot("today-before")
        s.store.quickAdd("Call the bank about the card")
        await shot("today-after")
        s.nav.go(.habits); await shot("habits-before")
        s.store.toggle(4, on: s.store.today)
        await shot("habits-after")
        s.nav.go(.today); await shot("today-final")
        var n = Note(id: 0, title: "What made you smile today?")
        n.category = "journal"
        n.body = NoteCodec.encode("<p>Coffee with Sam after the design review. We finally agreed on the landing page, and the walk back in the sun felt like a small holiday.</p>")
        let id = s.store.save(n)
        if var saved = s.store.note(id), let day = Day.fixedToday {
            saved.createdAt = day.date(at: TimeOfDay(hour: 9, minute: 0)); s.store.save(saved)
        }
        s.nav.go(.journal); s.journalUI.open(id); await shot("journal")
    }

    static func findTextView(_ v: NSView?) -> NSTextView? {
        guard let v else { return nil }
        if let tv = v as? NoteTextView { return tv }
        for sub in v.subviews { if let f = findTextView(sub) { return f } }
        return nil
    }

    /// Writes the colours the note text really draws with, so a test can fail on unreadable text.
    static func report(_ dir: URL, _ name: String, _ tv: NSTextView) {
        var lines = ["text: \(tv.string)"]
        func describe(_ c: Any?) -> String {
            guard let c = c as? NSColor else { return "none" }
            var out = "\(c)"
            tv.effectiveAppearance.performAsCurrentDrawingAppearance {
                if let rgb = c.usingColorSpace(.sRGB) { out += String(format: " → rgb(%.2f, %.2f, %.2f)", rgb.redComponent, rgb.greenComponent, rgb.blueComponent) }
            }
            return out
        }
        let s = tv.textStorage!
        var i = 0
        while i < s.length {
            var r = NSRange()
            let c = s.attribute(.foregroundColor, at: i, effectiveRange: &r)
            lines.append("run \(r.location)+\(r.length): \(describe(c))")
            i = r.location + max(1, r.length)
        }
        lines.append("typing: \(describe(tv.typingAttributes[.foregroundColor]))")
        lines.append("appearance: \(tv.effectiveAppearance.name.rawValue)")
        try? lines.joined(separator: "\n").write(to: dir.appendingPathComponent("\(name).txt"), atomically: true, encoding: .utf8)
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
        func entry(_ id: Int64, _ title: String, _ text: String, daysAgo: Int, bookmarked: Bool = false) -> Note {
            var n = Note(id: id, title: title); n.category = "journal"; n.isPinned = bookmarked
            n.body = NoteCodec.encode("<p>\(text)</p>")
            n.createdAt = now.addingTimeInterval(Double(-daysAgo * 86400 - 1800)); n.updatedAt = n.createdAt
            return n
        }
        d.notes = [n1, n2, n3, n4,
                   entry(10, "What made you smile today?", "Coffee with Sam after the design review. We finally agreed on the homepage, and the walk back in the sun felt like a small holiday.", daysAgo: 0, bookmarked: true),
                   entry(11, "A quiet Sunday", "Slept in, cleaned the balcony and potted the basil. Read forty pages of Piranesi. No screens until lunch — I want more days like this.", daysAgo: 2),
                   entry(12, "Describe a small win from this week.", "Shipped the analytics setup two days early. The team noticed, and I noticed that I wasn't stressed about it.", daysAgo: 3),
                   entry(13, "Trail run", "First run on the coastal trail since summer. Legs were heavy but the view at the lighthouse made up for it.", daysAgo: 9)]
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

extension DemoData {
    /// The same tasks, habits and notes as the phone's screenshot fixtures (Thursday 12 November 2026).
    static func story() -> DaylineData {
        var d = DaylineData()
        let today = Day(year: 2026, month: 11, day: 12)
        let base = today.date(at: TimeOfDay(hour: 9, minute: 0))
        func at(_ day: Day, _ h: Int = 9) -> Date { day.date(at: TimeOfDay(hour: h, minute: 0)) }
        func t(_ id: Int64, _ title: String, due: Day?, _ p: Priority, time: TimeOfDay? = nil, done: Bool = false, doneAt: Date? = nil) -> TaskItem {
            var x = TaskItem(id: id, title: title)
            x.dueDate = due; x.priority = p; x.dueTime = time; x.isCompleted = done; x.completedAt = doneAt
            x.createdAt = base.addingTimeInterval(-86400 * 20); x.updatedAt = x.createdAt; x.sortOrder = id
            return x
        }
        d.tasks = [
            t(1, "Send the Q4 budget to finance", due: today.minusDays(1), .URGENT),
            t(2, "Review landing page copy with the design team", due: today, .HIGH, time: TimeOfDay(hour: 11, minute: 30)),
            t(3, "Book dentist appointment", due: today, .MEDIUM),
            t(4, "Water the plants", due: today, .LOW),
            t(5, "Morning run", due: today, .MEDIUM, done: true, doneAt: at(today, 7)),
            t(6, "Prepare sprint demo", due: today.plusDays(1), .HIGH, time: TimeOfDay(hour: 15, minute: 0)),
            t(7, "Renew car insurance", due: today.plusDays(4), .URGENT),
            t(8, "Plan weekend hike", due: today.plusDays(9), .LOW),
            t(9, "Publish release notes", due: today.plusDays(2), .MEDIUM),
            t(10, "Read 'Deep Work' chapter 3", due: nil, .LOW),
        ] + (1...12).map { i in
            // Like the phone's story state: only "Morning run" was finished today.
            let day = today.minusDays(i % 6 == 0 ? 6 : i % 6)
            return t(Int64(100 + i), "Done task \(i)", due: day, Priority.allCases[i % 4], done: true, doneAt: at(day))
        }
        d.tasks[3].recurrence = RecurrenceRule(frequency: .DAILY)
        d.subtasks = [Subtask(id: 21, taskId: 2, title: "Hero headline"), Subtask(id: 22, taskId: 2, title: "Pricing section", position: 1),
                      Subtask(id: 23, taskId: 2, title: "Tone of voice", isDone: true, position: 2)]
        d.tags = [Tag(id: 1, name: "work"), Tag(id: 2, name: "errands")]
        d.taskTags = [TaskTag(taskId: 1, tagId: 1), TaskTag(taskId: 3, tagId: 2)]
        d.reminders = [Reminder(id: 1, taskId: 3, type: .DEADLINE, offsetMinutes: 30)]
        d.dependencies = [TaskDependency(taskId: 9, dependsOnTaskId: 6)]
        func c(_ hex: UInt32) -> Int32 { Int32(bitPattern: hex) }
        var h1 = Habit(id: 1, name: "Meditate", color: c(0xFF8E4EC6), startDate: today.minusDays(40)); h1.icon = "meditate"
        var h2 = Habit(id: 2, name: "Drink water", color: c(0xFF00A2C7), startDate: today.minusDays(20)); h2.icon = "water"; h2.targetPerDay = 8
        var h3 = Habit(id: 3, name: "Strength training", color: c(0xFFE0892B), startDate: today.minusDays(60)); h3.icon = "fitness"; h3.frequency = .WEEKLY_TARGET; h3.timesPerWeek = 3
        var h4 = Habit(id: 4, name: "Journal", color: c(0xFF3E9B4F), startDate: today.minusDays(30)); h4.icon = "book"; h4.frequency = .SPECIFIC_DAYS
        h4.weekdays = [.monday, .wednesday, .thursday, .friday]
        d.habits = [h1, h2, h3, h4]
        for back in 0...12 {
            let day = today.minusDays(back)
            if back != 3 { d.habitCompletions.append(HabitCompletion(habitId: 1, date: day, count: 1)) }
            d.habitCompletions.append(HabitCompletion(habitId: 2, date: day, count: back == 0 ? 5 : 8))
            if back % 2 == 1 { d.habitCompletions.append(HabitCompletion(habitId: 3, date: day, count: 1)) }
            if (1...2).contains(back) { d.habitCompletions.append(HabitCompletion(habitId: 4, date: day, count: 1)) }
        }
        for back in 0...6 {
            let start = at(today.minusDays(back), 10)
            let m = 25 + back * 7
            d.focusSessions.append(FocusSession(startedAt: start, endedAt: start.addingTimeInterval(3600), focusedMillis: Int64(m * 60_000),
                                                plannedMillis: 25 * 60_000, taskId: nil, projectId: nil, completed: true))
        }
        func journal(_ id: Int64, _ title: String, _ text: String, _ daysAgo: Int) -> Note {
            var n = Note(id: id, title: title); n.category = "journal"
            n.body = NoteCodec.encode("<p>\(text)</p>")
            n.createdAt = at(today.minusDays(daysAgo), 21); n.updatedAt = n.createdAt
            return n
        }
        var n1 = Note(id: 1, title: "Launch checklist"); n1.isPinned = true; n1.color = 3; n1.category = "work"
        n1.body = NoteCodec.encode("<p>Before we flip the switch:</p>", checklist: [CheckItem(text: "DNS cut-over", done: true), CheckItem(text: "Analytics events"), CheckItem(text: "Redirect map")])
        n1.updatedAt = base
        d.notes = [n1,
                   journal(30, "A quiet Sunday", "Slept in, cleaned the balcony and potted the basil. Read forty pages and kept the phone away until lunch.", 4),
                   journal(31, "Describe a small win from this week.", "Shipped the analytics setup two days early. The team noticed, and I noticed I wasn't stressed about it.", 6)]
        d.settings = ["user_name": .string("Vishal"), "accent_color": .int(Int64(Int32(bitPattern: 0xFF2F7DF6)))]
        return d
    }
}
