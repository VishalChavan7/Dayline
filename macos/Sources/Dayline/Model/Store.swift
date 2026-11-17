import Foundation
import Combine
import DaylineCore

/// The app's single source of truth: all data lives in one `DaylineData` document (the same JSON the phone
/// syncs through Google Drive), saved to Application Support after every change.
@MainActor
final class Store: ObservableObject {
    @Published private(set) var data: DaylineData
    /// Bumps on every change so views and services can react.
    @Published private(set) var revision = 0
    @Published var notice: String?

    private let fileURL: URL
    private var saveTask: Task<Void, Never>?
    var onChange: (() -> Void)?

    init(fileURL: URL? = nil, data: DaylineData? = nil) {
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Dayline", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        self.fileURL = fileURL ?? dir.appendingPathComponent("dayline.json")
        if let data {
            self.data = data
        } else if let raw = try? Data(contentsOf: self.fileURL), let d = try? BackupCodec.decode(raw) {
            self.data = d
        } else {
            self.data = DaylineData()
        }
    }

    static var supportDirectory: URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0].appendingPathComponent("Dayline", isDirectory: true)
    }

    // MARK: Saving

    private func changed() {
        revision += 1
        scheduleSave()
        onChange?()
    }

    private func scheduleSave() {
        saveTask?.cancel()
        let snapshot = data, url = fileURL
        saveTask = Task.detached(priority: .utility) {
            try? await Task.sleep(for: .milliseconds(400))
            if Task.isCancelled { return }
            let bytes = BackupCodec.encode(snapshot)
            try? bytes.write(to: url, options: .atomic)
        }
    }

    func flush() {
        saveTask?.cancel()
        try? BackupCodec.encode(data).write(to: fileURL, options: .atomic)
    }

    /// Replaces everything (restore from a file, or a newer copy from Google Drive). A safety copy is kept first.
    func replaceAll(with new: DaylineData, keepSafetyCopy: Bool = true) {
        if keepSafetyCopy {
            try? BackupCodec.encode(data).write(to: Store.supportDirectory.appendingPathComponent("pre-restore-backup.json"), options: .atomic)
        }
        data = new
        changed()
    }

    func exportJSON() -> Data { BackupCodec.encode(data) }

    var now: Date { Date() }
    var today: Day { Day.today() }

    // MARK: Settings (shared with the phone)

    func setting(_ key: String) -> JSONValue? { data.settings[key] }
    func set(_ key: String, _ value: JSONValue?) {
        if let value { data.settings[key] = value } else { data.settings.removeValue(forKey: key) }
        changed()
    }

    var accentColor: Int32 { Int32(truncatingIfNeeded: setting("accent_color")?.int ?? Int64(Int32(bitPattern: 0xFF2F7DF6))) }
    var themeMode: String { setting("theme_mode")?.string ?? "SYSTEM" }
    var weekStart: Weekday { Weekday(rawValue: Int(setting("week_start")?.int ?? 1).clamped(1, 7)) ?? .monday }
    var dateFormat: String { setting("date_format")?.string ?? "MEDIUM" }
    var timeFormat: String { setting("time_format")?.string ?? "SYSTEM" }
    var defaultPriority: Priority { setting("default_priority")?.string.flatMap(Priority.init(rawValue:)) ?? .MEDIUM }
    var defaultListId: Int64? { setting("default_list")?.int.flatMap { $0 > 0 ? $0 : nil } }
    var taskReminders: Bool { setting("task_reminders")?.bool ?? true }
    var habitReminders: Bool { setting("habit_reminders")?.bool ?? true }
    var focusNotifications: Bool { setting("focus_notifications")?.bool ?? true }
    /// nil = no automatic reminder for new dated tasks.
    var defaultReminderOffset: Int? { let v = setting("default_reminder_offset")?.int ?? 0; return v < 0 ? nil : Int(v) }
    var defaultReminderTime: TimeOfDay { TimeOfDay(seconds: Int(setting("default_reminder_time")?.int ?? 9 * 3600)) }
    var userName: String { setting("user_name")?.string ?? "" }
    var groupTodayByPriority: Bool { setting("group_by_priority")?.bool ?? true }
    var focusConfig: FocusConfig {
        var c = FocusConfig()
        c.focusMinutes = Int(setting("focus_minutes")?.int ?? 25)
        c.shortBreakMinutes = Int(setting("short_break")?.int ?? 5)
        c.longBreakMinutes = Int(setting("long_break")?.int ?? 15)
        c.sessionsBeforeLongBreak = Int(setting("sessions_before_long")?.int ?? 4)
        c.autoStartBreaks = setting("auto_breaks")?.bool ?? false
        c.autoStartFocus = setting("auto_focus")?.bool ?? false
        c.dailyGoalMinutes = Int(setting("focus_goal")?.int ?? 120)
        return c
    }
    var dashboardOrder: [DashboardSection] {
        let saved = (setting("dash_order")?.string ?? "").split(separator: ",").compactMap { DashboardSection(rawValue: String($0)) }
        if saved.isEmpty { return DashboardSection.allCases }
        var result = saved
        for m in DashboardSection.allCases where !result.contains(m) {
            let i = DashboardSection.allCases.firstIndex(of: m)!
            let before = i > 0 ? DashboardSection.allCases[i - 1] : nil
            if let b = before, let idx = result.firstIndex(of: b) { result.insert(m, at: idx + 1) } else { result.append(m) }
        }
        return result
    }
    var hiddenSections: Set<DashboardSection> { Set((setting("dash_hidden")?.string ?? "").split(separator: "|").compactMap { DashboardSection(rawValue: String($0)) }) }
    var collapsedSections: Set<DashboardSection> { Set((setting("dash_collapsed")?.string ?? "").split(separator: "|").compactMap { DashboardSection(rawValue: String($0)) }) }
    func setDashboard(order: [DashboardSection]? = nil, hidden: Set<DashboardSection>? = nil, collapsed: Set<DashboardSection>? = nil) {
        if let order { data.settings["dash_order"] = .string(order.map(\.rawValue).joined(separator: ",")) }
        if let hidden { data.settings["dash_hidden"] = .string(hidden.map(\.rawValue).sorted().joined(separator: "|")) }
        if let collapsed { data.settings["dash_collapsed"] = .string(collapsed.map(\.rawValue).sorted().joined(separator: "|")) }
        changed()
    }

    // MARK: Lookups

    func task(_ id: Int64?) -> TaskItem? { id.flatMap { id in data.tasks.first { $0.id == id } } }
    func project(_ id: Int64?) -> Project? { id.flatMap { id in data.projects.first { $0.id == id } } }
    func list(_ id: Int64?) -> TaskList? { id.flatMap { id in data.lists.first { $0.id == id } } }
    func habit(_ id: Int64?) -> Habit? { id.flatMap { id in data.habits.first { $0.id == id } } }
    func note(_ id: Int64?) -> Note? { id.flatMap { id in data.notes.first { $0.id == id } } }
    func subtasks(of id: Int64) -> [Subtask] { data.subtasks.filter { $0.taskId == id }.sorted { $0.position < $1.position } }
    func tags(of id: Int64) -> [Tag] {
        let ids = Set(data.taskTags.filter { $0.taskId == id }.map(\.tagId))
        return data.tags.filter { ids.contains($0.id) }.sorted { $0.name.localizedLowercase < $1.name.localizedLowercase }
    }
    func reminders(of id: Int64) -> [Reminder] { data.reminders.filter { $0.taskId == id } }
    func attachments(of id: Int64) -> [Attachment] { data.attachments.filter { $0.taskId == id } }
    func dependsOn(_ id: Int64) -> [TaskItem] { data.dependencies.filter { $0.taskId == id }.compactMap { task($0.dependsOnTaskId) } }
    func blockers(of id: Int64) -> [TaskItem] { dependsOn(id).filter { !$0.isCompleted } }
    func completions(of habitId: Int64) -> [HabitCompletion] { data.habitCompletions.filter { $0.habitId == habitId } }
    func count(_ habitId: Int64, on day: Day) -> Int { data.habitCompletions.first { $0.habitId == habitId && $0.date == day }?.count ?? 0 }

    /// Progress 0…1 from subtasks when there are any, otherwise the manual progress.
    func progress(of t: TaskItem) -> Double {
        let s = subtasks(of: t.id)
        if s.isEmpty { return t.isCompleted ? 1 : Double(t.progress) / 100 }
        return Double(s.filter(\.isDone).count) / Double(s.count)
    }

    var openTasks: [TaskItem] { data.tasks.filter { !$0.isCompleted } }
    var activeProjects: [Project] { data.projects.filter { !$0.isArchived }.sorted { ($0.sortOrder, $0.id) < ($1.sortOrder, $1.id) } }
    var activeHabits: [Habit] { data.habits.filter { !$0.isArchived }.sorted { ($0.sortOrder, $0.id) < ($1.sortOrder, $1.id) } }
    var customReminders: [Reminder] { data.reminders.filter { $0.type == .CUSTOM && $0.taskId == nil } }

    // MARK: Tasks

    /// Saves a task with its children. Returns its id.
    @discardableResult
    func save(_ draft: TaskDraft) -> Int64 {
        var t = draft.task
        t.title = t.title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.title.isEmpty else { notice = "A task needs a title."; return 0 }
        let now = self.now
        let id: Int64
        if let i = data.tasks.firstIndex(where: { $0.id == t.id && t.id != 0 }) {
            t.createdAt = data.tasks[i].createdAt
            t.updatedAt = now
            data.tasks[i] = t
            id = t.id
        } else {
            id = data.nextId(data.tasks, \.id)
            t.id = id
            t.createdAt = now; t.updatedAt = now
            if t.sortOrder == 0 { t.sortOrder = (data.tasks.map(\.sortOrder).max() ?? 0) + 1 }
            data.tasks.append(t)
        }
        writeChildren(id, draft)
        changed()
        return id
    }

    private func writeChildren(_ id: Int64, _ d: TaskDraft) {
        // Subtasks: keep ids, add new ones, drop removed.
        let cleaned = d.subtasks.filter { !$0.title.trimmingCharacters(in: .whitespaces).isEmpty }
        data.subtasks.removeAll { $0.taskId == id }
        var next = data.nextId(data.subtasks, \.id)
        for (i, var s) in cleaned.enumerated() {
            if s.id <= 0 || data.subtasks.contains(where: { $0.id == s.id }) { s.id = next; next += 1 }
            s.taskId = id; s.position = i
            data.subtasks.append(s)
        }
        // Tags
        data.taskTags.removeAll { $0.taskId == id }
        var seen = Set<String>()
        for raw in d.tagNames {
            let name = raw.trimmingCharacters(in: .whitespaces).droppingPrefix("#")
            guard !name.isEmpty, seen.insert(name.lowercased()).inserted else { continue }
            data.taskTags.append(TaskTag(taskId: id, tagId: resolveTag(name)))
        }
        // Dependencies without loops
        data.dependencies.removeAll { $0.taskId == id }
        for dep in Set(d.dependsOnIds) where dep != id && !createsCycle(id, dep) && data.tasks.contains(where: { $0.id == dep }) {
            data.dependencies.append(TaskDependency(taskId: id, dependsOnTaskId: dep))
        }
        // Attachments
        data.attachments.removeAll { $0.taskId == id }
        data.attachments += d.attachments.map { var a = $0; a.taskId = id; return a }
        // Reminders
        data.reminders.removeAll { $0.taskId == id }
        var rid = data.nextId(data.reminders, \.id)
        var unique = Set<String>()
        for var r in d.reminders where unique.insert("\(r.type)|\(r.offsetMinutes)|\(r.at?.iso ?? "")").inserted {
            r.id = rid; rid += 1; r.taskId = id
            data.reminders.append(r)
        }
    }

    private func resolveTag(_ name: String) -> Int64 {
        if let t = data.tags.first(where: { $0.name.caseInsensitiveCompare(name) == .orderedSame }) { return t.id }
        let id = data.nextId(data.tags, \.id)
        data.tags.append(Tag(id: id, name: name))
        return id
    }

    private func createsCycle(_ taskId: Int64, _ dependsOn: Int64) -> Bool {
        var graph: [Int64: [Int64]] = [:]
        for d in data.dependencies { graph[d.taskId, default: []].append(d.dependsOnTaskId) }
        var seen = Set<Int64>(), stack = [dependsOn]
        while let cur = stack.popLast() {
            if cur == taskId { return true }
            if seen.insert(cur).inserted { stack += graph[cur] ?? [] }
        }
        return false
    }

    func draft(for id: Int64) -> TaskDraft? {
        guard let t = task(id) else { return nil }
        return TaskDraft(task: t, subtasks: subtasks(of: id), tagNames: tags(of: id).map(\.name), dependsOnIds: dependsOn(id).map(\.id),
                         attachments: attachments(of: id), reminders: reminders(of: id))
    }

    /// Quick add with the phone's shortcuts (dates, !priority, #tags) and the default reminder.
    @discardableResult
    func quickAdd(_ text: String, date: Day? = nil, projectId: Int64? = nil, listId: Int64? = nil, priority: Priority? = nil) -> Int64 {
        let p = QuickAddParser.parse(text, today: today, weekStart: weekStart)
        guard !p.title.isEmpty else { return 0 }
        var t = TaskItem(id: 0, title: p.title)
        t.dueDate = p.date ?? date
        t.priority = p.priority ?? priority ?? defaultPriority
        t.projectId = projectId
        t.listId = listId ?? (projectId == nil ? defaultListId.flatMap { list($0)?.id } : nil)
        var reminders: [Reminder] = []
        if t.dueDate != nil, let off = defaultReminderOffset { reminders.append(Reminder(id: 0, taskId: 0, type: .DEADLINE, offsetMinutes: off)) }
        return save(TaskDraft(task: t, tagNames: p.tags, reminders: reminders))
    }

    /// Completes or reopens. Completing a repeating task archives this one and creates the next occurrence.
    func setCompleted(_ id: Int64, _ completed: Bool) {
        guard let i = data.tasks.firstIndex(where: { $0.id == id }) else { return }
        let t = data.tasks[i]
        guard t.isCompleted != completed else { return }
        if completed, let b = blockers(of: id).first {
            notice = "Finish “\(b.title)” first: “\(t.title)” depends on it."
            return
        }
        let now = self.now
        if !completed {
            data.tasks[i].isCompleted = false; data.tasks[i].completedAt = nil; data.tasks[i].updatedAt = now
            changed(); return
        }
        let next = RecurrenceEngine.nextOccurrence(t, completedOn: today, weekStart: weekStart)
        data.tasks[i].isCompleted = true; data.tasks[i].completedAt = now; data.tasks[i].updatedAt = now
        if let next {
            data.tasks[i].recurrence = nil
            var n = t
            n.id = data.nextId(data.tasks, \.id)
            n.isCompleted = false; n.completedAt = nil; n.progress = 0
            n.startDate = next.startDate; n.dueDate = next.dueDate
            n.createdAt = now; n.updatedAt = now; n.sortOrder = (data.tasks.map(\.sortOrder).max() ?? 0) + 1
            data.tasks.append(n)
            var sid = data.nextId(data.subtasks, \.id)
            for s in subtasks(of: id) { data.subtasks.append(Subtask(id: sid, taskId: n.id, title: s.title, isDone: false, position: s.position)); sid += 1 }
            data.taskTags += data.taskTags.filter { $0.taskId == id }.map { TaskTag(taskId: n.id, tagId: $0.tagId) }
            data.attachments += attachments(of: id).map { var a = $0; a.taskId = n.id; return a }
            data.dependencies += data.dependencies.filter { $0.taskId == id }.map { TaskDependency(taskId: n.id, dependsOnTaskId: $0.dependsOnTaskId) }
            var rid = data.nextId(data.reminders, \.id)
            for r in reminders(of: id) where r.type != .CUSTOM {
                var c = r; c.id = rid; rid += 1; c.taskId = n.id; c.isDismissed = false
                data.reminders.append(c)
            }
        }
        changed()
    }

    /// Deletes a task (and its children); returns a snapshot for Undo.
    @discardableResult
    func deleteTask(_ id: Int64) -> TaskDraft? {
        if let b = blockers(of: id).first, let t = task(id) {
            notice = "“\(t.title)” is waiting on “\(b.title)”. Finish that first, or remove the dependency."
            return nil
        }
        guard let snapshot = draft(for: id) else { return nil }
        removeTaskRows([id])
        changed()
        return snapshot
    }

    private func removeTaskRows(_ ids: Set<Int64>) {
        data.tasks.removeAll { ids.contains($0.id) }
        data.subtasks.removeAll { ids.contains($0.taskId) }
        data.taskTags.removeAll { ids.contains($0.taskId) }
        data.dependencies.removeAll { ids.contains($0.taskId) || ids.contains($0.dependsOnTaskId) }
        data.attachments.removeAll { ids.contains($0.taskId) }
        data.reminders.removeAll { $0.taskId.map(ids.contains) ?? false }
        for i in data.notes.indices where data.notes[i].taskId.map(ids.contains) ?? false { data.notes[i].taskId = nil }
        for i in data.focusSessions.indices where data.focusSessions[i].taskId.map(ids.contains) ?? false { data.focusSessions[i].taskId = nil }
    }

    func restore(_ snapshot: TaskDraft) {
        var d = snapshot
        if data.tasks.contains(where: { $0.id == d.task.id }) { d.task.id = 0 }
        if d.task.id != 0 {
            data.tasks.append(d.task)
            writeChildren(d.task.id, d)
            changed()
        } else {
            save(d)
        }
    }

    func duplicate(_ id: Int64) {
        guard var d = draft(for: id) else { return }
        d.task.id = 0; d.task.title += " (copy)"; d.task.isCompleted = false; d.task.completedAt = nil; d.task.sortOrder = 0
        d.subtasks = d.subtasks.map { var s = $0; s.id = 0; s.isDone = false; return s }
        save(d)
    }

    func update(_ id: Int64, _ change: (inout TaskItem) -> Void) {
        guard let i = data.tasks.firstIndex(where: { $0.id == id }) else { return }
        change(&data.tasks[i])
        data.tasks[i].updatedAt = now
        changed()
    }

    /// Moves to a date keeping the start→due span.
    func reschedule(_ id: Int64, to date: Day?) {
        update(id) { t in
            guard let date else { t.startDate = nil; t.dueDate = nil; t.startTime = nil; t.dueTime = nil; return }
            if let s = t.startDate, let d = t.dueDate { t.startDate = date.minusDays(d - s); t.dueDate = date }
            else if t.dueDate == nil && t.startDate != nil { t.startDate = date }
            else { t.dueDate = date }
        }
    }

    func reorder(_ ids: [Int64]) {
        for (i, id) in ids.enumerated() { if let k = data.tasks.firstIndex(where: { $0.id == id }) { data.tasks[k].sortOrder = Int64(i + 1) } }
        changed()
    }

    func setSubtaskDone(_ id: Int64, _ done: Bool) {
        guard let i = data.subtasks.firstIndex(where: { $0.id == id }) else { return }
        data.subtasks[i].isDone = done
        changed()
    }

    func addSubtask(_ taskId: Int64, _ title: String) {
        let t = title.trimmingCharacters(in: .whitespaces)
        guard !t.isEmpty else { return }
        data.subtasks.append(Subtask(id: data.nextId(data.subtasks, \.id), taskId: taskId, title: t, position: subtasks(of: taskId).count))
        changed()
    }

    // MARK: Projects, lists, tags

    @discardableResult
    func save(_ p: Project) -> Int64 {
        var p = p
        p.name = p.name.trimmingCharacters(in: .whitespaces)
        guard !p.name.isEmpty else { notice = "A project needs a name."; return 0 }
        if let s = p.startDate, let d = p.deadline, d < s { notice = "The deadline can't be before the start date."; return 0 }
        if let i = data.projects.firstIndex(where: { $0.id == p.id && p.id != 0 }) { data.projects[i] = p }
        else { p.id = data.nextId(data.projects, \.id); p.createdAt = now; p.sortOrder = (data.projects.map(\.sortOrder).max() ?? 0) + 1; data.projects.append(p) }
        changed()
        return p.id
    }

    func deleteProject(_ id: Int64, deleteTasks: Bool) {
        if deleteTasks { removeTaskRows(Set(data.tasks.filter { $0.projectId == id }.map(\.id))) }
        for i in data.tasks.indices where data.tasks[i].projectId == id { data.tasks[i].projectId = nil }
        for i in data.lists.indices where data.lists[i].projectId == id { data.lists[i].projectId = nil }
        for i in data.notes.indices where data.notes[i].projectId == id { data.notes[i].projectId = nil }
        for i in data.focusSessions.indices where data.focusSessions[i].projectId == id { data.focusSessions[i].projectId = nil }
        data.projects.removeAll { $0.id == id }
        changed()
    }

    @discardableResult
    func save(_ l: TaskList) -> Int64 {
        var l = l
        l.name = l.name.trimmingCharacters(in: .whitespaces)
        guard !l.name.isEmpty else { notice = "A list needs a name."; return 0 }
        if let i = data.lists.firstIndex(where: { $0.id == l.id && l.id != 0 }) { data.lists[i] = l }
        else { l.id = data.nextId(data.lists, \.id); data.lists.append(l) }
        changed()
        return l.id
    }

    func deleteList(_ id: Int64) {
        for i in data.tasks.indices where data.tasks[i].listId == id { data.tasks[i].listId = nil }
        data.lists.removeAll { $0.id == id }
        changed()
    }

    func renameTag(_ id: Int64, _ name: String) {
        let clean = name.trimmingCharacters(in: .whitespaces).droppingPrefix("#")
        guard !clean.isEmpty else { return }
        if data.tags.contains(where: { $0.id != id && $0.name.caseInsensitiveCompare(clean) == .orderedSame }) { notice = "A tag with that name already exists."; return }
        if let i = data.tags.firstIndex(where: { $0.id == id }) { data.tags[i].name = clean; changed() }
    }

    func deleteTag(_ id: Int64) {
        data.taskTags.removeAll { $0.tagId == id }
        data.tags.removeAll { $0.id == id }
        changed()
    }

    // MARK: Habits

    @discardableResult
    func save(_ h: Habit) -> Int64 {
        var h = h
        h.name = h.name.trimmingCharacters(in: .whitespaces)
        guard !h.name.isEmpty else { notice = "A habit needs a name."; return 0 }
        if h.frequency == .SPECIFIC_DAYS && h.weekdays.isEmpty { notice = "Pick at least one day."; return 0 }
        if let i = data.habits.firstIndex(where: { $0.id == h.id && h.id != 0 }) { data.habits[i] = h }
        else { h.id = data.nextId(data.habits, \.id); h.createdAt = now; h.sortOrder = (data.habits.map(\.sortOrder).max() ?? 0) + 1; data.habits.append(h) }
        changed()
        return h.id
    }

    func deleteHabit(_ id: Int64) {
        data.habitCompletions.removeAll { $0.habitId == id }
        data.habits.removeAll { $0.id == id }
        changed()
    }

    func setArchived(habit id: Int64, _ archived: Bool) {
        guard let i = data.habits.firstIndex(where: { $0.id == id }) else { return }
        data.habits[i].isArchived = archived
        changed()
    }

    /// Adds `delta` check-ins for a day; counts never go below zero.
    func checkIn(_ habitId: Int64, on day: Day, delta: Int) {
        let next = min(999, max(0, count(habitId, on: day) + delta))
        setCount(habitId, day, next)
    }

    /// Done ↔ not done for a day.
    func toggle(_ habitId: Int64, on day: Day) {
        guard let h = habit(habitId) else { return }
        setCount(habitId, day, HabitStreaks.isDone(h, count(habitId, on: day)) ? 0 : max(1, h.targetPerDay))
    }

    private func setCount(_ habitId: Int64, _ day: Day, _ n: Int) {
        data.habitCompletions.removeAll { $0.habitId == habitId && $0.date == day }
        if n > 0 { data.habitCompletions.append(HabitCompletion(habitId: habitId, date: day, count: n)) }
        changed()
    }

    // MARK: Notes

    @discardableResult
    func save(_ n: Note) -> Int64 {
        var n = n
        guard !n.title.trimmingCharacters(in: .whitespaces).isEmpty || !NoteCodec.plainText(n.body).isEmpty else {
            if n.id != 0 { deleteNote(n.id) }
            return 0
        }
        let now = self.now
        if let i = data.notes.firstIndex(where: { $0.id == n.id && n.id != 0 }) {
            if data.notes[i] == n { return n.id }
            n.updatedAt = now
            data.notes[i] = n
        } else {
            n.id = data.nextId(data.notes, \.id); n.createdAt = now; n.updatedAt = now
            data.notes.append(n)
        }
        changed()
        return n.id
    }

    func deleteNote(_ id: Int64) { data.notes.removeAll { $0.id == id }; changed() }

    func setPinned(note id: Int64, _ pinned: Bool) {
        guard let i = data.notes.firstIndex(where: { $0.id == id }) else { return }
        data.notes[i].isPinned = pinned
        changed()
    }

    func labels(of n: Note) -> [String] { (n.category ?? "").split(separator: ",").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty } }
    var allNoteLabels: [String] { Array(Set(data.notes.flatMap(labels(of:)))).sorted() }

    func renameLabel(_ old: String, _ new: String) {
        let from = old.lowercased().trimmingCharacters(in: .whitespaces), to = new.lowercased().trimmingCharacters(in: .whitespaces)
        guard !from.isEmpty, !to.isEmpty, from != to else { return }
        for i in data.notes.indices {
            var l = labels(of: data.notes[i])
            if l.contains(from) {
                l = l.map { $0 == from ? to : $0 }
                var seen = Set<String>()
                data.notes[i].category = l.filter { seen.insert($0).inserted }.joined(separator: ",")
            }
        }
        changed()
    }

    // MARK: Reminders and focus

    func saveCustomReminder(_ r: Reminder) {
        var r = r
        r.type = .CUSTOM
        guard !r.title.trimmingCharacters(in: .whitespaces).isEmpty else { notice = "A reminder needs a title."; return }
        if let i = data.reminders.firstIndex(where: { $0.id == r.id && r.id != 0 }) { data.reminders[i] = r }
        else { r.id = data.nextId(data.reminders, \.id); data.reminders.append(r) }
        changed()
    }

    func dismissReminder(_ id: Int64) {
        guard let i = data.reminders.firstIndex(where: { $0.id == id }) else { return }
        data.reminders[i].isDismissed = true
        changed()
    }

    func deleteReminder(_ id: Int64) { data.reminders.removeAll { $0.id == id }; changed() }

    func record(_ r: FocusRecord) {
        data.focusSessions.append(FocusSession(startedAt: Date(epochMillis: r.startedAtMillis), endedAt: Date(epochMillis: r.endedAtMillis),
                                               focusedMillis: r.focusedMillis, plannedMillis: r.plannedMillis,
                                               taskId: r.taskId.flatMap { task($0)?.id }, projectId: r.projectId.flatMap { project($0)?.id },
                                               completed: r.completed))
        changed()
    }

    func focusMinutes(on day: Day) -> Int {
        Int(data.focusSessions.filter { Day($0.startedAt) == day }.reduce(Int64(0)) { $0 + $1.focusedMillis } / 60_000)
    }

    // MARK: Search

    func search(_ query: String) -> (tasks: [TaskItem], projects: [Project], lists: [TaskList], tags: [Tag], notes: [Note]) {
        let q = query.trimmingCharacters(in: .whitespaces)
        guard !q.isEmpty else { return ([], [], [], [], []) }
        func m(_ s: String?) -> Bool { s?.localizedCaseInsensitiveContains(q) ?? false }
        let tagHits = data.tags.filter { m($0.name) }
        let tagTaskIds = Set(data.taskTags.filter { t in tagHits.contains { $0.id == t.tagId } }.map(\.taskId))
        let tasks = data.tasks.filter { m($0.title) || m($0.description) || m($0.notes) || tagTaskIds.contains($0.id) }
            .sorted { a, b in a.isCompleted != b.isCompleted ? !a.isCompleted : TaskGrouping.chronological(a, b) }
        return (tasks, data.projects.filter { m($0.name) || m($0.description) }, data.lists.filter { m($0.name) }, tagHits,
                data.notes.filter { m($0.title) || m(NoteCodec.plainText($0.body)) || m($0.category) })
    }
}

struct TaskDraft: Equatable {
    var task: TaskItem
    var subtasks: [Subtask] = []
    var tagNames: [String] = []
    var dependsOnIds: [Int64] = []
    var attachments: [Attachment] = []
    var reminders: [Reminder] = []
}

enum DashboardSection: String, CaseIterable, Identifiable {
    case PROGRESS, OVERDUE, TODAY, INBOX, DEADLINES, FOCUS, WEEKLY, QUOTE
    var id: String { rawValue }
    var label: String {
        switch self {
        case .PROGRESS: "Daily progress"; case .OVERDUE: "Overdue"; case .TODAY: "Today's tasks"; case .INBOX: "Inbox"
        case .DEADLINES: "Upcoming deadlines"; case .FOCUS: "Focus"; case .WEEKLY: "Weekly summary"; case .QUOTE: "Daily quote"
        }
    }
    var detail: String {
        switch self {
        case .PROGRESS: "Completion ring and counts"; case .OVERDUE: "Tasks past their due date"; case .TODAY: "Everything planned for today"
        case .INBOX: "Tasks without a date, project or list"; case .DEADLINES: "Due in the next 7 days"; case .FOCUS: "Today's focus time and quick start"
        case .WEEKLY: "Tasks completed this week"; case .QUOTE: "A line to start the day"
        }
    }
}

extension Comparable {
    func clamped(_ lo: Self, _ hi: Self) -> Self { min(max(self, lo), hi) }
}

extension String {
    func droppingPrefix(_ p: String) -> String { hasPrefix(p) ? String(dropFirst(p.count)) : self }
}
