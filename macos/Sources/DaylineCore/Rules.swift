import Foundation

// MARK: - Repeats

public struct NextOccurrence: Equatable, Sendable { public var startDate: Day?; public var dueDate: Day? }

public enum RecurrenceEngine {
    public static func step(_ rule: RecurrenceRule, from: Day, anchor: Day? = nil, weekStart: Weekday = .monday) -> Day {
        switch rule.frequency {
        case .DAILY: return from.plusDays(rule.interval)
        case .MONTHLY: return from.plusMonths(rule.interval)
        case .YEARLY: return from.plusYears(rule.interval)
        case .WEEKLY:
            return rule.weekdays.isEmpty ? from.plusWeeks(rule.interval) : nextMatchingWeekday(rule, from, anchor ?? from, weekStart)
        }
    }

    private static func nextMatchingWeekday(_ rule: RecurrenceRule, _ from: Day, _ anchor: Day, _ weekStart: Weekday) -> Day {
        let anchorWeek = DateRules.startOfWeek(anchor, weekStart)
        var c = from.plusDays(1)
        for _ in 0..<(7 * (rule.interval + 1) + 7) {
            let weeks = (DateRules.startOfWeek(c, weekStart) - anchorWeek) / 7
            if rule.weekdays.contains(c.weekday) && weeks % rule.interval == 0 { return c }
            c = c.plusDays(1)
        }
        return from.plusWeeks(rule.interval)
    }

    /// Dates of the next instance of a recurring task completed on `completedOn`, or nil when the series ends.
    public static func nextOccurrence(_ task: TaskItem, completedOn: Day, weekStart: Weekday = .monday) -> NextOccurrence? {
        guard let rule = task.recurrence else { return nil }
        let current = task.dueDate ?? task.startDate
        let next: Day
        if current == nil || rule.fromCompletion {
            next = (rule.frequency == .WEEKLY && !rule.weekdays.isEmpty)
                ? nextMatchingFrom(rule, completedOn, weekStart)
                : step(rule, from: completedOn, anchor: completedOn, weekStart: weekStart)
        } else {
            var n = step(rule, from: current!, anchor: current!, weekStart: weekStart)
            var guardCount = 0
            while !(n > completedOn) && guardCount < 10_000 { n = step(rule, from: n, anchor: current!, weekStart: weekStart); guardCount += 1 }
            next = n
        }
        if let end = rule.endDate, next > end { return nil }
        switch (task.startDate, task.dueDate) {
        case let (s?, d?): return NextOccurrence(startDate: next.minusDays(d - s), dueDate: next)
        case (nil, _?): return NextOccurrence(startDate: nil, dueDate: next)
        case (_?, nil): return NextOccurrence(startDate: next, dueDate: nil)
        default: return NextOccurrence(startDate: nil, dueDate: next)
        }
    }

    private static func nextMatchingFrom(_ rule: RecurrenceRule, _ base: Day, _ weekStart: Weekday) -> Day {
        if rule.interval == 1 {
            var d = base.plusDays(1)
            for _ in 0..<7 { if rule.weekdays.contains(d.weekday) { return d }; d = d.plusDays(1) }
        }
        var d = DateRules.startOfWeek(base, weekStart).plusWeeks(rule.interval)
        for _ in 0..<7 { if rule.weekdays.contains(d.weekday) { return d }; d = d.plusDays(1) }
        return base.plusWeeks(rule.interval)
    }

    /// Future occurrences of a series anchored at `anchor` (excluded) that fall in from...to. For the calendar.
    public static func projected(_ rule: RecurrenceRule, anchor: Day, from: Day, to: Day, weekStart: Weekday = .monday, limit: Int = 400) -> [Day] {
        if rule.fromCompletion { return [] }
        var out: [Day] = []
        var d = step(rule, from: anchor, anchor: anchor, weekStart: weekStart)
        var g = 0
        while !(d > to) && g < 5000 && out.count < limit {
            if let e = rule.endDate, d > e { break }
            if !(d < from) { out.append(d) }
            d = step(rule, from: d, anchor: anchor, weekStart: weekStart)
            g += 1
        }
        return out
    }
}

// MARK: - Habit streaks

public struct HabitStats: Equatable, Sendable {
    public var currentStreak: Int
    public var longestStreak: Int
    /// "days" or "weeks"
    public var streakUnit: String
    public var consistency: Double
    public var totalCompletions: Int
}

/// A day counts when check-ins reach the daily target; unscheduled days never break a streak; today not
/// being done yet never breaks the current streak; times-per-week habits streak in weeks.
public enum HabitStreaks {
    public static func isScheduled(_ h: Habit, _ d: Day) -> Bool {
        if d < h.startDate { return false }
        switch h.frequency {
        case .DAILY, .WEEKLY_TARGET: return true
        case .SPECIFIC_DAYS: return h.weekdays.contains(d.weekday)
        }
    }

    public static func isDone(_ h: Habit, _ count: Int) -> Bool { count >= max(1, h.targetPerDay) }

    static func counts(_ c: [HabitCompletion]) -> [Day: Int] { c.reduce(into: [:]) { $0[$1.date, default: 0] += $1.count } }

    public static func stats(_ h: Habit, _ completions: [HabitCompletion], today: Day, weekStart: Weekday = .monday, window: Int = 30) -> HabitStats {
        let by = counts(completions)
        let total = by.filter { !($0.key > today) && isDone(h, $0.value) }.count
        let cons = consistency(h, completions, from: today.minusDays(window - 1), to: today, weekStart: weekStart)
        if h.frequency == .WEEKLY_TARGET {
            return HabitStats(currentStreak: currentWeekly(h, by, today, weekStart), longestStreak: longestWeekly(h, by, today, weekStart),
                              streakUnit: "weeks", consistency: cons, totalCompletions: total)
        }
        return HabitStats(currentStreak: currentDaily(h, by, today), longestStreak: longestDaily(h, by, today),
                          streakUnit: "days", consistency: cons, totalCompletions: total)
    }

    static func currentDaily(_ h: Habit, _ by: [Day: Int], _ today: Day) -> Int {
        var streak = 0, d = today, first = true
        while !(d < h.startDate) {
            if isScheduled(h, d) {
                if isDone(h, by[d] ?? 0) { streak += 1 } else if !first { break }
            }
            first = false
            d = d.minusDays(1)
        }
        return streak
    }

    static func longestDaily(_ h: Habit, _ by: [Day: Int], _ today: Day) -> Int {
        var best = 0, run = 0, d = h.startDate
        while !(d > today) {
            if isScheduled(h, d) {
                if isDone(h, by[d] ?? 0) { run += 1; best = max(best, run) } else if d != today { run = 0 }
            }
            d = d.plusDays(1)
        }
        return best
    }

    static func doneDaysInWeek(_ h: Habit, _ by: [Day: Int], _ ws: Day, _ until: Day) -> Int {
        (0..<7).map { ws.plusDays($0) }.filter { !($0 > until) && !($0 < h.startDate) && isDone(h, by[$0] ?? 0) }.count
    }

    static func weekTarget(_ h: Habit) -> Int { min(7, max(1, h.timesPerWeek)) }

    static func currentWeekly(_ h: Habit, _ by: [Day: Int], _ today: Day, _ wsd: Weekday) -> Int {
        let firstWeek = DateRules.startOfWeek(h.startDate, wsd)
        var week = DateRules.startOfWeek(today, wsd), streak = 0, isCurrent = true
        while !(week < firstWeek) {
            if doneDaysInWeek(h, by, week, today) >= weekTarget(h) { streak += 1 } else if !isCurrent { break }
            isCurrent = false
            week = week.minusWeeks(1)
        }
        return streak
    }

    static func longestWeekly(_ h: Habit, _ by: [Day: Int], _ today: Day, _ wsd: Weekday) -> Int {
        let current = DateRules.startOfWeek(today, wsd)
        var week = DateRules.startOfWeek(h.startDate, wsd), best = 0, run = 0
        while !(week > current) {
            if doneDaysInWeek(h, by, week, today) >= weekTarget(h) { run += 1; best = max(best, run) } else if week != current { run = 0 }
            week = week.plusWeeks(1)
        }
        return best
    }

    /// Share of scheduled days (or weeks) completed in from...to; today counts only once done.
    public static func consistency(_ h: Habit, _ completions: [HabitCompletion], from: Day, to: Day, weekStart: Weekday = .monday, today: Day? = nil) -> Double {
        let today = today ?? to
        let by = counts(completions)
        let start = max(from, h.startDate), end = min(to, today)
        if end < start { return 0 }
        if h.frequency == .WEEKLY_TARGET {
            var week = DateRules.startOfWeek(start, weekStart)
            let last = DateRules.startOfWeek(end, weekStart)
            var met = 0, counted = 0
            while !(week > last) {
                let ok = doneDaysInWeek(h, by, week, end) >= weekTarget(h)
                let inProgress = week == DateRules.startOfWeek(today, weekStart)
                if ok { met += 1; counted += 1 } else if !inProgress { counted += 1 }
                week = week.plusWeeks(1)
            }
            return counted == 0 ? 0 : Double(met) / Double(counted)
        }
        var scheduled = 0, done = 0, d = start
        while !(d > end) {
            if isScheduled(h, d) {
                if isDone(h, by[d] ?? 0) { done += 1; scheduled += 1 } else if d != today { scheduled += 1 }
            }
            d = d.plusDays(1)
        }
        return scheduled == 0 ? 0 : Double(done) / Double(scheduled)
    }

    /// Whether a habit belongs on a day's checklist. Weekly-target habits show every day until met.
    public static func dueOn(_ h: Habit, _ date: Day, _ completions: [HabitCompletion], weekStart: Weekday) -> Bool {
        if h.isArchived || !isScheduled(h, date) { return false }
        if h.frequency != .WEEKLY_TARGET { return true }
        let by = counts(completions)
        let ws = DateRules.startOfWeek(date, weekStart)
        let before = (0..<7).map { ws.plusDays($0) }.filter { $0 < date && isDone(h, by[$0] ?? 0) }.count
        return before < weekTarget(h) || isDone(h, by[date] ?? 0)
    }
}

// MARK: - Quick add

public struct ParsedQuickAdd: Equatable, Sendable {
    public var title: String; public var date: Day?; public var priority: Priority?; public var tags: [String]
}

/// "today", "tomorrow", weekday names, "next week", "!high" / "!1".."!4", "#tag" — only as standalone words.
public enum QuickAddParser {
    static let weekdayNames: [String: Weekday] = Dictionary(uniqueKeysWithValues: Weekday.allCases.flatMap { d in
        [(d.name.lowercased(), d), (String(d.name.lowercased().prefix(3)), d)]
    })
    static let priorityWords: [String: Priority] = [
        "!low": .LOW, "!1": .LOW, "!medium": .MEDIUM, "!med": .MEDIUM, "!2": .MEDIUM,
        "!high": .HIGH, "!3": .HIGH, "!urgent": .URGENT, "!4": .URGENT,
    ]

    public static func parse(_ input: String, today: Day, weekStart: Weekday = .monday) -> ParsedQuickAdd {
        let tokens = input.split(whereSeparator: \.isWhitespace).map(String.init)
        var kept: [String] = [], tags: [String] = []
        var date: Day?, priority: Priority?
        var i = 0
        while i < tokens.count {
            let raw = tokens[i], tok = raw.lowercased()
            let next = i + 1 < tokens.count ? tokens[i + 1].lowercased() : nil
            if let p = priorityWords[tok] { priority = p }
            else if raw.hasPrefix("#") && raw.count > 1 { tags.append(String(raw.dropFirst())) }
            else if date == nil && (tok == "today" || tok == "tod") { date = today }
            else if date == nil && ["tomorrow", "tmr", "tmrw"].contains(tok) { date = today.plusDays(1) }
            else if date == nil && tok == "next" && next == "week" { date = DateRules.startOfWeek(today, weekStart).plusWeeks(1); i += 1 }
            else if date == nil && (tok == "on" || tok == "next"), let n = next, let wd = weekdayNames[n] { date = DateRules.nextWeekday(today, wd); i += 1 }
            else if date == nil, tok.count > 3, let wd = weekdayNames[tok] { date = DateRules.nextWeekday(today, wd) }
            else { kept.append(raw) }
            i += 1
        }
        var seen = Set<String>()
        return ParsedQuickAdd(title: kept.joined(separator: " "), date: date, priority: priority, tags: tags.filter { seen.insert($0).inserted })
    }
}

// MARK: - Reminders

public enum ReminderPlanner {
    public static func triggerTime(_ r: Reminder, task: TaskItem?, defaultTime: TimeOfDay) -> DayTime? {
        switch r.type {
        case .CUSTOM: return r.at
        case .TASK:
            guard let d = task?.startDate ?? task?.dueDate else { return nil }
            return DayTime(day: d, time: task?.startTime ?? task?.dueTime ?? defaultTime).minusMinutes(r.offsetMinutes)
        case .DEADLINE:
            guard let d = task?.dueDate else { return nil }
            return DayTime(day: d, time: task?.dueTime ?? defaultTime).minusMinutes(r.offsetMinutes)
        }
    }

    public static func shouldSchedule(_ r: Reminder, task: TaskItem?, trigger: DayTime?, now: DayTime) -> Bool {
        guard !r.isDismissed, let t = trigger, t > now else { return false }
        if r.taskId != nil && (task == nil || task!.isCompleted) { return false }
        return true
    }

    public static func nextHabitReminder(_ h: Habit, now: DayTime, doneToday: Bool) -> DayTime? {
        guard let time = h.reminderTime, !h.isArchived else { return nil }
        var date = now.day
        for _ in 0..<15 {
            let c = DayTime(day: date, time: time)
            let skipToday = date == now.day && (doneToday || !(c > now))
            if !skipToday && HabitStreaks.isScheduled(h, date) { return c }
            date = date.plusDays(1)
        }
        return nil
    }

    public static func describeOffset(_ m: Int) -> String {
        if m == 0 { return "At time" }
        if m < 60 { return "\(m) min before" }
        if m % 1440 == 0 { return "\(m / 1440) day\(m >= 2880 ? "s" : "") before" }
        if m % 60 == 0 { return "\(m / 60) hour\(m >= 120 ? "s" : "") before" }
        return "\(m / 60)h \(m % 60)m before"
    }

    public static let standardOffsets = [0, 10, 30, 60, 120, 1440]
}

// MARK: - Grouping and sorting

public enum UpcomingBucket: Int, CaseIterable, Sendable {
    case tomorrow, thisWeek, nextWeek, later
    public var label: String { ["Tomorrow", "This week", "Next week", "Later"][rawValue] }
}

public enum TaskSort: String, CaseIterable, Sendable {
    case manual, dueDate, priority, created, title
    public var label: String {
        switch self { case .manual: "Manual"; case .dueDate: "Due date"; case .priority: "Priority"; case .created: "Recently created"; case .title: "Title" }
    }
}

public enum TaskGrouping {
    public static func bucket(_ d: Day, today: Day, weekStart: Weekday) -> UpcomingBucket? {
        guard d > today else { return nil }
        if d == today.plusDays(1) { return .tomorrow }
        let end = DateRules.endOfWeek(today, weekStart)
        if !(d > end) { return .thisWeek }
        if !(d > end.plusWeeks(1)) { return .nextWeek }
        return .later
    }

    public static func chronological(_ a: TaskItem, _ b: TaskItem) -> Bool {
        let da = a.scheduledDate?.epochDay ?? Int.max, db = b.scheduledDate?.epochDay ?? Int.max
        if da != db { return da < db }
        let ta = (a.dueTime ?? a.startTime ?? .endOfDay).seconds, tb = (b.dueTime ?? b.startTime ?? .endOfDay).seconds
        if ta != tb { return ta < tb }
        if a.priority != b.priority { return a.priority > b.priority }
        return a.sortOrder < b.sortOrder
    }

    public static func sort(_ tasks: [TaskItem], _ s: TaskSort) -> [TaskItem] {
        switch s {
        case .manual: return tasks.sorted { ($0.sortOrder, $0.id) < ($1.sortOrder, $1.id) }
        case .dueDate: return tasks.sorted(by: chronological)
        case .priority: return tasks.sorted { $0.priority != $1.priority ? $0.priority > $1.priority : chronological($0, $1) }
        case .created: return tasks.sorted { $0.createdAt > $1.createdAt }
        case .title: return tasks.sorted { $0.title.localizedLowercase < $1.title.localizedLowercase }
        }
    }

    /// Tasks on a given day: due that day, or starting that day when there's no due date.
    public static func onDay(_ tasks: [TaskItem], _ day: Day) -> [TaskItem] {
        tasks.filter { $0.dueDate == day || ($0.dueDate == nil && $0.startDate == day) }
    }
}

// MARK: - Daily quote

public struct Quote: Sendable { public let text: String; public let author: String }

public enum Quotes {
    public static let all: [Quote] = [
        Quote(text: "Well begun is half done.", author: "Aristotle"),
        Quote(text: "It does not matter how slowly you go as long as you do not stop.", author: "Confucius"),
        Quote(text: "Lost time is never found again.", author: "Benjamin Franklin"),
        Quote(text: "While we are postponing, life speeds by.", author: "Seneca"),
        Quote(text: "A journey of a thousand miles begins with a single step.", author: "Lao Tzu"),
        Quote(text: "Do what you can, with what you have, where you are.", author: "Theodore Roosevelt"),
        Quote(text: "Well done is better than well said.", author: "Benjamin Franklin"),
        Quote(text: "Begin at once to live.", author: "Seneca"),
        Quote(text: "Quality is not an act, it is a habit.", author: "Aristotle"),
        Quote(text: "The secret of getting ahead is getting started.", author: "Mark Twain"),
        Quote(text: "Simplicity is the ultimate sophistication.", author: "Leonardo da Vinci"),
        Quote(text: "He who has a why to live can bear almost any how.", author: "Friedrich Nietzsche"),
        Quote(text: "Knowing is not enough; we must apply.", author: "Johann Wolfgang von Goethe"),
        Quote(text: "Energy and persistence conquer all things.", author: "Benjamin Franklin"),
        Quote(text: "First say to yourself what you would be; then do what you have to do.", author: "Epictetus"),
        Quote(text: "You may delay, but time will not.", author: "Benjamin Franklin"),
        Quote(text: "Waste no more time arguing what a good man should be. Be one.", author: "Marcus Aurelius"),
        Quote(text: "The impediment to action advances action.", author: "Marcus Aurelius"),
        Quote(text: "What we fear doing most is usually what we most need to do.", author: "Ralph Waldo Emerson"),
        Quote(text: "Patience and perseverance have a magical effect.", author: "John Quincy Adams"),
        Quote(text: "Luck is what happens when preparation meets opportunity.", author: "Seneca"),
    ]
    public static func of(_ day: Day) -> Quote { all[((day.epochDay % all.count) + all.count) % all.count] }
}
