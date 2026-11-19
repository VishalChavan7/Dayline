import Foundation

public enum Priority: String, CaseIterable, Codable, Sendable, Comparable {
    case LOW, MEDIUM, HIGH, URGENT
    public var level: Int { switch self { case .LOW: 1; case .MEDIUM: 2; case .HIGH: 3; case .URGENT: 4 } }
    public var label: String { switch self { case .LOW: "Low"; case .MEDIUM: "Medium"; case .HIGH: "High"; case .URGENT: "Urgent" } }
    public static func < (a: Priority, b: Priority) -> Bool { a.level < b.level }
}

public enum RecurrenceFrequency: String, CaseIterable, Codable, Sendable {
    case DAILY, WEEKLY, MONTHLY, YEARLY
    public var label: String { switch self { case .DAILY: "Daily"; case .WEEKLY: "Weekly"; case .MONTHLY: "Monthly"; case .YEARLY: "Yearly" } }
    public var unit: String { switch self { case .DAILY: "day"; case .WEEKLY: "week"; case .MONTHLY: "month"; case .YEARLY: "year" } }
}

public struct RecurrenceRule: Hashable, Sendable {
    public var frequency: RecurrenceFrequency
    public var interval: Int
    public var weekdays: Set<Weekday>
    public var fromCompletion: Bool
    public var endDate: Day?

    public init(frequency: RecurrenceFrequency, interval: Int = 1, weekdays: Set<Weekday> = [], fromCompletion: Bool = false, endDate: Day? = nil) {
        self.frequency = frequency; self.interval = max(1, interval); self.weekdays = weekdays
        self.fromCompletion = fromCompletion; self.endDate = endDate
    }

    public func describe() -> String {
        let base: String
        if frequency == .WEEKLY && !weekdays.isEmpty {
            let days = weekdays.sorted().map(\.short).joined(separator: ", ")
            base = interval == 1 ? "Every \(days)" : "Every \(interval) weeks on \(days)"
        } else if interval == 1 {
            base = frequency.label
        } else {
            base = "Every \(interval) \(frequency.unit)s"
        }
        return fromCompletion ? "\(base) after completion" : base
    }
}

public struct TaskItem: Identifiable, Hashable, Sendable {
    public var id: Int64
    public var title: String
    public var description: String = ""
    public var notes: String = ""
    public var startDate: Day?
    public var startTime: TimeOfDay?
    public var dueDate: Day?
    public var dueTime: TimeOfDay?
    public var priority: Priority = .MEDIUM
    public var category: String?
    public var projectId: Int64?
    public var listId: Int64?
    public var recurrence: RecurrenceRule?
    public var isCompleted = false
    public var completedAt: Date?
    public var progress = 0
    public var sortOrder: Int64 = 0
    public var createdAt = Date(timeIntervalSince1970: 0)
    public var updatedAt = Date(timeIntervalSince1970: 0)

    public init(id: Int64, title: String) { self.id = id; self.title = title }

    /// The date a task is "scheduled" on: due date first, otherwise start date.
    public var scheduledDate: Day? { dueDate ?? startDate }
    public func isOverdue(_ today: Day) -> Bool { !isCompleted && dueDate.map { $0 < today } == true }
}

public struct Subtask: Identifiable, Hashable, Sendable {
    public var id: Int64; public var taskId: Int64; public var title: String; public var isDone = false; public var position = 0
    public init(id: Int64, taskId: Int64, title: String, isDone: Bool = false, position: Int = 0) {
        self.id = id; self.taskId = taskId; self.title = title; self.isDone = isDone; self.position = position
    }
}

public struct Tag: Identifiable, Hashable, Sendable {
    public var id: Int64; public var name: String; public var color: Int32 = 0
    public init(id: Int64, name: String, color: Int32 = 0) { self.id = id; self.name = name; self.color = color }
}

public struct Project: Identifiable, Hashable, Sendable {
    public var id: Int64
    public var name: String
    public var description = ""
    public var color: Int32
    public var icon = "folder"
    public var startDate: Day?
    public var deadline: Day?
    public var isArchived = false
    public var sortOrder = 0
    public var createdAt = Date(timeIntervalSince1970: 0)
    public init(id: Int64, name: String, color: Int32) { self.id = id; self.name = name; self.color = color }
}

public struct TaskList: Identifiable, Hashable, Sendable {
    public var id: Int64; public var name: String; public var color: Int32; public var icon = "list"
    public var projectId: Int64?; public var sortOrder = 0
    public init(id: Int64, name: String, color: Int32) { self.id = id; self.name = name; self.color = color }
}

public enum AttachmentKind: String, Sendable { case FILE, LINK }

public struct Attachment: Hashable, Sendable {
    public var taskId: Int64; public var kind: AttachmentKind; public var uri: String; public var displayName: String
    public var mimeType: String?; public var sizeBytes: Int64?; public var addedAt = Date(timeIntervalSince1970: 0)
    public init(taskId: Int64, kind: AttachmentKind, uri: String, displayName: String) {
        self.taskId = taskId; self.kind = kind; self.uri = uri; self.displayName = displayName
    }
}

public enum ReminderType: String, Sendable { case TASK, DEADLINE, CUSTOM }

/// TASK / DEADLINE reminders are `offsetMinutes` before the task's start / due moment.
/// CUSTOM reminders fire at `at` and may exist without a task (pinned reminders).
public struct Reminder: Identifiable, Hashable, Sendable {
    public var id: Int64; public var taskId: Int64?; public var type: ReminderType; public var offsetMinutes = 0
    public var at: DayTime?; public var title = ""; public var isPinned = false; public var isDismissed = false
    public init(id: Int64, taskId: Int64?, type: ReminderType, offsetMinutes: Int = 0, at: DayTime? = nil) {
        self.id = id; self.taskId = taskId; self.type = type; self.offsetMinutes = offsetMinutes; self.at = at
    }
}

public enum HabitFrequency: String, CaseIterable, Sendable {
    case DAILY, SPECIFIC_DAYS, WEEKLY_TARGET
    public var label: String { switch self { case .DAILY: "Every day"; case .SPECIFIC_DAYS: "Specific days"; case .WEEKLY_TARGET: "Times per week" } }
}

public struct Habit: Identifiable, Hashable, Sendable {
    public var id: Int64
    public var name: String
    public var description = ""
    public var icon = "spa"
    public var color: Int32
    public var frequency: HabitFrequency = .DAILY
    public var weekdays: Set<Weekday> = []
    public var timesPerWeek = 3
    /// Check-ins in a day that count as "done" (e.g. 8 glasses of water).
    public var targetPerDay = 1
    public var startDate: Day
    public var reminderTime: TimeOfDay?
    public var isArchived = false
    public var sortOrder = 0
    public var createdAt = Date(timeIntervalSince1970: 0)
    public init(id: Int64, name: String, color: Int32, startDate: Day) { self.id = id; self.name = name; self.color = color; self.startDate = startDate }
}

public struct HabitCompletion: Hashable, Sendable {
    public var habitId: Int64; public var date: Day; public var count: Int
    public init(habitId: Int64, date: Day, count: Int) { self.habitId = habitId; self.date = date; self.count = count }
}

public struct Note: Identifiable, Hashable, Sendable {
    public var id: Int64
    public var title: String
    public var body = ""
    public var category: String?
    public var isPinned = false
    public var taskId: Int64?
    public var projectId: Int64?
    public var color: Int32 = 0
    public var createdAt = Date(timeIntervalSince1970: 0)
    public var updatedAt = Date(timeIntervalSince1970: 0)
    /// Set when archived: out of the main list, still in Archive and search.
    public var archivedAt: Date?
    /// Set when in Trash; stays there until restored or deleted for good.
    public var trashedAt: Date?
    /// Locked notes hide their text until unlocked with Touch ID or the Mac's password.
    public var locked = false
    public init(id: Int64, title: String) { self.id = id; self.title = title }
    public var isArchived: Bool { archivedAt != nil }
    public var isTrashed: Bool { trashedAt != nil }
}

public struct FocusSession: Hashable, Sendable {
    public var startedAt: Date; public var endedAt: Date; public var focusedMillis: Int64; public var plannedMillis: Int64
    public var taskId: Int64?; public var projectId: Int64?; public var completed: Bool
    public init(startedAt: Date, endedAt: Date, focusedMillis: Int64, plannedMillis: Int64, taskId: Int64?, projectId: Int64?, completed: Bool) {
        self.startedAt = startedAt; self.endedAt = endedAt; self.focusedMillis = focusedMillis; self.plannedMillis = plannedMillis
        self.taskId = taskId; self.projectId = projectId; self.completed = completed
    }
    public var focusedMinutes: Int { Int(focusedMillis / 60_000) }
}

public struct TaskDependency: Hashable, Sendable {
    public var taskId: Int64; public var dependsOnTaskId: Int64
    public init(taskId: Int64, dependsOnTaskId: Int64) { self.taskId = taskId; self.dependsOnTaskId = dependsOnTaskId }
}

public struct TaskTag: Hashable, Sendable {
    public var taskId: Int64; public var tagId: Int64
    public init(taskId: Int64, tagId: Int64) { self.taskId = taskId; self.tagId = tagId }
}

/// Everything Dayline stores. This is exactly the content of the phone's backup / Google Drive sync file.
public struct DaylineData: Sendable, Equatable {
    public var projects: [Project] = []
    public var lists: [TaskList] = []
    public var tasks: [TaskItem] = []
    public var subtasks: [Subtask] = []
    public var tags: [Tag] = []
    public var taskTags: [TaskTag] = []
    public var dependencies: [TaskDependency] = []
    public var attachments: [Attachment] = []
    public var reminders: [Reminder] = []
    public var habits: [Habit] = []
    public var habitCompletions: [HabitCompletion] = []
    public var notes: [Note] = []
    public var focusSessions: [FocusSession] = []
    /// Shared preferences (accent, week start, focus lengths…). Values are kept as JSON so settings
    /// the Mac doesn't use still travel back to the phone unchanged.
    public var settings: [String: JSONValue] = [:]

    public init() {}

    public var isEmpty: Bool { tasks.isEmpty && habits.isEmpty && notes.isEmpty && focusSessions.isEmpty }

    /// Next free id for a table (ids are shared with the phone, so they keep counting up).
    public func nextId<T>(_ items: [T], _ id: (T) -> Int64) -> Int64 { (items.map(id).max() ?? 0) + 1 }
}

/// A JSON value, used for settings so unknown keys survive a round trip.
public enum JSONValue: Hashable, Sendable {
    case string(String), int(Int64), double(Double), bool(Bool), null

    public var string: String? {
        switch self {
        case .string(let s): return s
        case .int(let i): return String(i)
        case .double(let d): return String(d)
        case .bool(let b): return String(b)
        case .null: return nil
        }
    }
    public var int: Int64? {
        switch self {
        case .int(let i): return i
        case .double(let d): return Int64(d)
        case .string(let s): return Int64(s)
        default: return nil
        }
    }
    public var bool: Bool? {
        switch self {
        case .bool(let b): return b
        case .string(let s): return s == "true" ? true : s == "false" ? false : nil
        default: return nil
        }
    }

    init(any: Any) {
        if any is NSNull { self = .null; return }
        if let n = any as? NSNumber {
            if CFGetTypeID(n) == CFBooleanGetTypeID() { self = .bool(n.boolValue); return }
            if CFNumberIsFloatType(n) { self = .double(n.doubleValue); return }
            self = .int(n.int64Value); return
        }
        if let s = any as? String { self = .string(s); return }
        self = .string(String(describing: any))
    }

    var any: Any {
        switch self {
        case .string(let s): return s
        case .int(let i): return NSNumber(value: i)
        case .double(let d): return NSNumber(value: d)
        case .bool(let b): return NSNumber(value: b)
        case .null: return NSNull()
        }
    }
}
