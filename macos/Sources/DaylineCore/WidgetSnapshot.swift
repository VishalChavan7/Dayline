import Foundation

/// What the desktop widgets show. The app writes it after every change; the widgets only read it
/// (they can't open the app's data). Taps in a widget are queued as `WidgetAction`s for the app.
public struct WidgetSnapshot: Codable, Sendable, Equatable {
    public struct TaskRow: Codable, Sendable, Equatable, Identifiable {
        public var id: Int64
        public var title: String
        public var done: Bool
        /// "11:00 AM", "Yesterday", … (already formatted by the app).
        public var when: String?
        /// 0 = low … 3 = urgent.
        public var priority: Int
        public var overdue: Bool
        public init(id: Int64, title: String, done: Bool, when: String?, priority: Int, overdue: Bool) {
            self.id = id; self.title = title; self.done = done; self.when = when; self.priority = priority; self.overdue = overdue
        }
    }
    public struct HabitRow: Codable, Sendable, Equatable, Identifiable {
        public var id: Int64
        public var name: String
        public var symbol: String
        public var color: UInt32
        public var count: Int
        public var target: Int
        public var done: Bool
        public var streak: Int
        public init(id: Int64, name: String, symbol: String, color: UInt32, count: Int, target: Int, done: Bool, streak: Int) {
            self.id = id; self.name = name; self.symbol = symbol; self.color = color; self.count = count; self.target = target; self.done = done; self.streak = streak
        }
    }
    public struct Focus: Codable, Sendable, Equatable {
        public var running: Bool = false
        public var paused: Bool = false
        public var phase: String = "Focus"
        public var endsAt: Date?
        public var remainingSeconds: Int = 25 * 60
        public var minutesToday: Int = 0
        public var goalMinutes: Int = 120
        public init() {}
    }

    public var generatedAt = Date()
    public var weekday = ""
    public var dateText = ""
    public var tasks: [TaskRow] = []
    public var habits: [HabitRow] = []
    public var done = 0
    public var total = 0
    public var next: TaskRow?
    public var focus = Focus()
    /// Theme colours as RGB (the end colour only for gradient themes).
    public var accent: UInt32 = 0x2F7DF6
    public var accentEnd: UInt32?
    public var monochrome = false
    public init() {}
}

public struct WidgetAction: Codable, Sendable, Equatable {
    public enum Kind: String, Codable, Sendable { case task, habit, focus }
    public var kind: Kind
    public var id: Int64
    public var at: Date
    public init(kind: Kind, id: Int64, at: Date = Date()) { self.kind = kind; self.id = id; self.at = at }
}

/// Files shared between the app and its widgets, in ~/Library/Application Support/Dayline.
public enum WidgetFiles {
    /// The real home folder (a sandboxed widget's own home points into its container).
    public static var realHome: String {
        if let pw = getpwuid(getuid()), let dir = pw.pointee.pw_dir { return String(cString: dir) }
        return NSHomeDirectory()
    }
    public static var directory: URL {
        URL(fileURLWithPath: realHome).appendingPathComponent("Library/Application Support/Dayline", isDirectory: true)
    }
    public static var snapshotURL: URL { directory.appendingPathComponent("widget.json") }
    public static var actionsURL: URL { directory.appendingPathComponent("widget-actions.json") }

    private static func encoder() -> JSONEncoder { let e = JSONEncoder(); e.dateEncodingStrategy = .secondsSince1970; return e }
    private static func decoder() -> JSONDecoder { let d = JSONDecoder(); d.dateDecodingStrategy = .secondsSince1970; return d }

    public static func readSnapshot() -> WidgetSnapshot? {
        guard let data = try? Data(contentsOf: snapshotURL) else { return nil }
        return try? decoder().decode(WidgetSnapshot.self, from: data)
    }

    public static func write(_ s: WidgetSnapshot) {
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        if let data = try? encoder().encode(s) { try? data.write(to: snapshotURL, options: .atomic) }
    }

    public static func readActions() -> [WidgetAction] {
        guard let data = try? Data(contentsOf: actionsURL) else { return [] }
        return (try? decoder().decode([WidgetAction].self, from: data)) ?? []
    }

    public static func append(_ a: WidgetAction) {
        var all = readActions()
        all.append(a)
        if let data = try? encoder().encode(all) { try? data.write(to: actionsURL, options: .atomic) }
    }

    /// Takes the queued actions and clears the queue.
    public static func takeActions() -> [WidgetAction] {
        let all = readActions()
        if !all.isEmpty { try? FileManager.default.removeItem(at: actionsURL) }
        return all
    }
}
