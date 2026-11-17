import Foundation
import CryptoKit

public struct BackupFormatError: LocalizedError {
    public let message: String
    public var errorDescription: String? { message }
}

/// Reads and writes Dayline's JSON document — the same file the phone exports and keeps in Google Drive
/// ("formatVersion" 1). Field names, units and validation rules mirror the Android BackupManager.
public enum BackupCodec {
    public static let appId = "dayline"
    public static let formatVersion = 1

    // MARK: Writing

    public static func encode(_ d: DaylineData, exportedAt: Date = Date()) -> Data {
        var root: [String: Any] = [
            "app": appId,
            "formatVersion": formatVersion,
            "exportedAt": isoInstant(exportedAt),
        ]
        root["projects"] = d.projects.map { e in obj([
            "id": e.id, "name": e.name, "description": e.description, "color": e.color, "icon": e.icon,
            "startDate": e.startDate?.epochDay, "deadline": e.deadline?.epochDay, "isArchived": e.isArchived,
            "sortOrder": e.sortOrder, "createdAt": e.createdAt.epochMillis,
        ]) }
        root["lists"] = d.lists.map { e in obj([
            "id": e.id, "name": e.name, "color": e.color, "icon": e.icon, "projectId": e.projectId, "sortOrder": e.sortOrder,
        ]) }
        root["tasks"] = d.tasks.map { e in obj([
            "id": e.id, "title": e.title, "description": e.description, "notes": e.notes,
            "startDate": e.startDate?.epochDay, "startTime": e.startTime?.seconds,
            "dueDate": e.dueDate?.epochDay, "dueTime": e.dueTime?.seconds,
            "priority": e.priority.rawValue, "category": e.category, "projectId": e.projectId, "listId": e.listId,
            "recurrenceFrequency": e.recurrence?.frequency.rawValue, "recurrenceInterval": e.recurrence?.interval ?? 1,
            "recurrenceWeekdays": Weekday.mask(e.recurrence?.weekdays ?? []),
            "recurrenceFromCompletion": e.recurrence?.fromCompletion ?? false,
            "recurrenceEndDate": e.recurrence?.endDate?.epochDay, "isCompleted": e.isCompleted,
            "completedAt": e.completedAt?.epochMillis, "progress": e.progress, "sortOrder": e.sortOrder,
            "createdAt": e.createdAt.epochMillis, "updatedAt": e.updatedAt.epochMillis,
        ]) }
        root["subtasks"] = d.subtasks.map { e in obj(["id": e.id, "taskId": e.taskId, "title": e.title, "isDone": e.isDone, "position": e.position]) }
        root["tags"] = d.tags.map { e in obj(["id": e.id, "name": e.name, "color": e.color]) }
        root["taskTags"] = d.taskTags.map { e in obj(["taskId": e.taskId, "tagId": e.tagId]) }
        root["dependencies"] = d.dependencies.map { e in obj(["taskId": e.taskId, "dependsOnTaskId": e.dependsOnTaskId]) }
        root["attachments"] = d.attachments.map { e in obj([
            "taskId": e.taskId, "kind": e.kind.rawValue, "uri": e.uri, "displayName": e.displayName,
            "mimeType": e.mimeType, "sizeBytes": e.sizeBytes, "addedAt": e.addedAt.epochMillis,
        ]) }
        root["reminders"] = d.reminders.map { e in obj([
            "id": e.id, "taskId": e.taskId, "type": e.type.rawValue, "offsetMinutes": e.offsetMinutes,
            "at": e.at?.iso, "title": e.title, "isPinned": e.isPinned, "isDismissed": e.isDismissed,
        ]) }
        root["habits"] = d.habits.map { e in obj([
            "id": e.id, "name": e.name, "description": e.description, "icon": e.icon, "color": e.color,
            "frequency": e.frequency.rawValue, "weekdays": Weekday.mask(e.weekdays), "timesPerWeek": e.timesPerWeek,
            "targetPerDay": e.targetPerDay, "startDate": e.startDate.epochDay, "reminderTime": e.reminderTime?.seconds,
            "isArchived": e.isArchived, "sortOrder": e.sortOrder, "createdAt": e.createdAt.epochMillis,
        ]) }
        root["habitCompletions"] = d.habitCompletions.map { e in obj(["habitId": e.habitId, "date": e.date.epochDay, "count": e.count]) }
        root["notes"] = d.notes.map { e in obj([
            "id": e.id, "title": e.title, "body": e.body, "category": e.category, "isPinned": e.isPinned,
            "taskId": e.taskId, "projectId": e.projectId, "color": e.color,
            "createdAt": e.createdAt.epochMillis, "updatedAt": e.updatedAt.epochMillis,
        ]) }
        root["focusSessions"] = d.focusSessions.map { e in obj([
            "startedAt": e.startedAt.epochMillis, "endedAt": e.endedAt.epochMillis, "focusedMillis": e.focusedMillis,
            "plannedMillis": e.plannedMillis, "taskId": e.taskId, "projectId": e.projectId, "completed": e.completed,
        ]) }
        root["settings"] = d.settings.mapValues(\.any)
        return (try? JSONSerialization.data(withJSONObject: root, options: [.prettyPrinted, .sortedKeys])) ?? Data()
    }

    private static func obj(_ pairs: [String: Any?]) -> [String: Any] {
        pairs.mapValues { $0 ?? NSNull() }
    }

    static func isoInstant(_ d: Date) -> String {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f.string(from: d)
    }

    // MARK: Reading

    public static func decode(_ data: Data) throws -> DaylineData {
        guard let any = try? JSONSerialization.jsonObject(with: data), let root = any as? [String: Any] else {
            throw BackupFormatError(message: "This file isn't a valid backup.")
        }
        guard root["app"] as? String == appId else { throw BackupFormatError(message: "This file wasn't created by Dayline.") }
        if (root["formatVersion"] as? NSNumber)?.intValue ?? 0 > formatVersion {
            throw BackupFormatError(message: "This backup is from a newer version of Dayline.")
        }
        do { return try parse(root) } catch let e as BackupFormatError { throw e } catch {
            throw BackupFormatError(message: "The backup is damaged: \(error.localizedDescription)")
        }
    }

    /// Summary for the restore confirmation.
    public static func summary(_ d: DaylineData) -> String {
        "\(d.tasks.count) tasks, \(d.habits.count) habits, \(d.notes.count) notes, \(d.projects.count) projects"
    }

    private struct Missing: LocalizedError {
        let key: String
        var errorDescription: String? { "missing \(key)" }
    }

    private struct Obj {
        let o: [String: Any]
        func has(_ k: String) -> Bool { if let v = o[k], !(v is NSNull) { return true } else { return false } }
        func long(_ k: String) -> Int64? { has(k) ? ((o[k] as? NSNumber)?.int64Value ?? Int64((o[k] as? String) ?? "")) : nil }
        func int(_ k: String, _ def: Int = 0) -> Int { long(k).map { Int($0) } ?? def }
        func color(_ k: String) -> Int32 { Int32(truncatingIfNeeded: long(k) ?? 0) }
        func bool(_ k: String) -> Bool { (o[k] as? NSNumber)?.boolValue ?? ((o[k] as? String) == "true") }
        func str(_ k: String) -> String? { has(k) ? (o[k] as? String ?? "\(o[k]!)") : nil }
        func string(_ k: String, _ def: String = "") -> String { str(k) ?? def }
        func day(_ k: String) -> Day? { long(k).map { Day(epochDay: Int($0)) } }
        func time(_ k: String) -> TimeOfDay? { long(k).map { TimeOfDay(seconds: Int($0)) } }
        func instant(_ k: String) -> Date? { long(k).map(Date.init(epochMillis:)) }
        func req(_ k: String) throws -> Int64 { guard let v = long(k) else { throw Missing(key: k) }; return v }
        func reqStr(_ k: String) throws -> String { guard let v = str(k) else { throw Missing(key: k) }; return v }
    }

    private static func list<T>(_ root: [String: Any], _ key: String, _ map: (Obj) throws -> T) rethrows -> [T] {
        guard let a = root[key] as? [Any] else { return [] }
        return try a.compactMap { $0 as? [String: Any] }.map { try map(Obj(o: $0)) }
    }

    private static func parse(_ root: [String: Any]) throws -> DaylineData {
        var d = DaylineData()
        let epoch = Date(timeIntervalSince1970: 0)
        d.projects = try list(root, "projects") { o in
            var p = Project(id: try o.req("id"), name: try o.reqStr("name"), color: o.color("color"))
            p.description = o.string("description"); p.icon = o.string("icon", "folder")
            p.startDate = o.day("startDate"); p.deadline = o.day("deadline"); p.isArchived = o.bool("isArchived")
            p.sortOrder = o.int("sortOrder"); p.createdAt = o.instant("createdAt") ?? epoch
            return p
        }
        let projectIds = Set(d.projects.map(\.id))
        d.lists = try list(root, "lists") { o in
            var l = TaskList(id: try o.req("id"), name: try o.reqStr("name"), color: o.color("color"))
            l.icon = o.string("icon", "list"); l.projectId = o.long("projectId").flatMap { projectIds.contains($0) ? $0 : nil }
            l.sortOrder = o.int("sortOrder")
            return l
        }
        let listIds = Set(d.lists.map(\.id))
        d.tasks = try list(root, "tasks") { o in
            var t = TaskItem(id: try o.req("id"), title: try o.reqStr("title"))
            t.description = o.string("description"); t.notes = o.string("notes")
            t.startDate = o.day("startDate"); t.startTime = o.time("startTime"); t.dueDate = o.day("dueDate"); t.dueTime = o.time("dueTime")
            t.priority = Priority(rawValue: o.string("priority", "MEDIUM")) ?? .MEDIUM
            t.category = o.str("category")
            t.projectId = o.long("projectId").flatMap { projectIds.contains($0) ? $0 : nil }
            t.listId = o.long("listId").flatMap { listIds.contains($0) ? $0 : nil }
            if let f = o.str("recurrenceFrequency").flatMap(RecurrenceFrequency.init(rawValue:)) {
                t.recurrence = RecurrenceRule(frequency: f, interval: o.int("recurrenceInterval", 1),
                                              weekdays: Weekday.from(mask: o.int("recurrenceWeekdays")),
                                              fromCompletion: o.bool("recurrenceFromCompletion"), endDate: o.day("recurrenceEndDate"))
            }
            t.isCompleted = o.bool("isCompleted"); t.completedAt = o.instant("completedAt"); t.progress = o.int("progress")
            t.sortOrder = o.long("sortOrder") ?? 0; t.createdAt = o.instant("createdAt") ?? epoch; t.updatedAt = o.instant("updatedAt") ?? epoch
            return t
        }
        let taskIds = Set(d.tasks.map(\.id))
        d.subtasks = try list(root, "subtasks") { o in
            Subtask(id: try o.req("id"), taskId: try o.req("taskId"), title: try o.reqStr("title"), isDone: o.bool("isDone"), position: o.int("position"))
        }.filter { taskIds.contains($0.taskId) }
        d.tags = try list(root, "tags") { o in Tag(id: try o.req("id"), name: try o.reqStr("name"), color: o.color("color")) }
        let tagIds = Set(d.tags.map(\.id))
        d.taskTags = try list(root, "taskTags") { o in TaskTag(taskId: try o.req("taskId"), tagId: try o.req("tagId")) }
            .filter { taskIds.contains($0.taskId) && tagIds.contains($0.tagId) }
        d.dependencies = try list(root, "dependencies") { o in TaskDependency(taskId: try o.req("taskId"), dependsOnTaskId: try o.req("dependsOnTaskId")) }
            .filter { taskIds.contains($0.taskId) && taskIds.contains($0.dependsOnTaskId) }
        d.attachments = try list(root, "attachments") { o in
            guard let kind = AttachmentKind(rawValue: try o.reqStr("kind")) else { throw Missing(key: "kind") }
            var a = Attachment(taskId: try o.req("taskId"), kind: kind, uri: try o.reqStr("uri"), displayName: try o.reqStr("displayName"))
            a.mimeType = o.str("mimeType"); a.sizeBytes = o.long("sizeBytes"); a.addedAt = o.instant("addedAt") ?? epoch
            return a
        }.filter { taskIds.contains($0.taskId) }
        d.reminders = try list(root, "reminders") { o in
            guard let type = ReminderType(rawValue: try o.reqStr("type")) else { throw Missing(key: "type") }
            var r = Reminder(id: try o.req("id"), taskId: o.long("taskId"), type: type, offsetMinutes: o.int("offsetMinutes"),
                             at: o.str("at").flatMap(DayTime.init(iso:)))
            r.title = o.string("title"); r.isPinned = o.bool("isPinned"); r.isDismissed = o.bool("isDismissed")
            return r
        }.filter { $0.taskId == nil || taskIds.contains($0.taskId!) }
        d.habits = try list(root, "habits") { o in
            var h = Habit(id: try o.req("id"), name: try o.reqStr("name"), color: o.color("color"), startDate: Day(epochDay: Int(try o.req("startDate"))))
            h.description = o.string("description"); h.icon = o.string("icon", "spa")
            h.frequency = HabitFrequency(rawValue: o.string("frequency", "DAILY")) ?? .DAILY
            h.weekdays = Weekday.from(mask: o.int("weekdays")); h.timesPerWeek = o.int("timesPerWeek", 3)
            h.targetPerDay = o.int("targetPerDay", 1); h.reminderTime = o.time("reminderTime"); h.isArchived = o.bool("isArchived")
            h.sortOrder = o.int("sortOrder"); h.createdAt = o.instant("createdAt") ?? epoch
            return h
        }
        let habitIds = Set(d.habits.map(\.id))
        d.habitCompletions = try list(root, "habitCompletions") { o in
            HabitCompletion(habitId: try o.req("habitId"), date: Day(epochDay: Int(try o.req("date"))), count: Int(try o.req("count")))
        }.filter { habitIds.contains($0.habitId) }
        d.notes = try list(root, "notes") { o in
            var n = Note(id: try o.req("id"), title: o.string("title"))
            n.body = o.string("body"); n.category = o.str("category"); n.isPinned = o.bool("isPinned")
            n.taskId = o.long("taskId").flatMap { taskIds.contains($0) ? $0 : nil }
            n.projectId = o.long("projectId").flatMap { projectIds.contains($0) ? $0 : nil }
            n.color = o.color("color"); n.createdAt = o.instant("createdAt") ?? epoch; n.updatedAt = o.instant("updatedAt") ?? epoch
            return n
        }
        d.focusSessions = try list(root, "focusSessions") { o in
            FocusSession(startedAt: Date(epochMillis: try o.req("startedAt")), endedAt: Date(epochMillis: try o.req("endedAt")),
                         focusedMillis: try o.req("focusedMillis"), plannedMillis: o.long("plannedMillis") ?? 0,
                         taskId: o.long("taskId").flatMap { taskIds.contains($0) ? $0 : nil },
                         projectId: o.long("projectId").flatMap { projectIds.contains($0) ? $0 : nil }, completed: o.bool("completed"))
        }
        if let s = root["settings"] as? [String: Any] { d.settings = s.mapValues(JSONValue.init(any:)) }
        return d
    }

    /// Stable fingerprint of the data (ignores the export time), used to tell whether anything changed.
    public static func fingerprint(_ d: DaylineData) -> String {
        let data = encode(d, exportedAt: Date(timeIntervalSince1970: 0))
        return SHA256.hash(data: data).map { String(format: "%02x", $0) }.joined()
    }
}
