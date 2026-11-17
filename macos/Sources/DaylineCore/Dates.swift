import Foundation

/// Day of the week, numbered like java.time.DayOfWeek (Monday = 1 … Sunday = 7).
public enum Weekday: Int, CaseIterable, Codable, Sendable, Comparable {
    case monday = 1, tuesday, wednesday, thursday, friday, saturday, sunday

    public static func < (a: Weekday, b: Weekday) -> Bool { a.rawValue < b.rawValue }

    public var short: String { ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"][rawValue - 1] }
    public var letter: String { ["M", "T", "W", "T", "F", "S", "S"][rawValue - 1] }
    public var name: String { ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"][rawValue - 1] }

    public func plus(_ days: Int) -> Weekday { Weekday(rawValue: ((rawValue - 1 + days) % 7 + 7) % 7 + 1)! }

    /// Bit mask used in the sync file (bit 0 = Monday).
    public static func mask(_ days: Set<Weekday>) -> Int { days.reduce(0) { $0 | (1 << ($1.rawValue - 1)) } }
    public static func from(mask: Int) -> Set<Weekday> { Set(allCases.filter { mask & (1 << ($0.rawValue - 1)) != 0 }) }
}

/// A calendar date without a time zone, stored as days since 1970-01-01 (like java.time.LocalDate.toEpochDay).
public struct Day: Hashable, Comparable, Codable, Sendable, CustomStringConvertible {
    public var epochDay: Int

    public init(epochDay: Int) { self.epochDay = epochDay }

    public init(year: Int, month: Int, day: Int) {
        // Days from civil (Howard Hinnant's algorithm).
        let y = month <= 2 ? year - 1 : year
        let era = (y >= 0 ? y : y - 399) / 400
        let yoe = y - era * 400
        let mp = (month + 9) % 12
        let doy = (153 * mp + 2) / 5 + day - 1
        let doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        epochDay = era * 146097 + doe - 719468
    }

    /// (year, month 1-12, day 1-31)
    public var ymd: (year: Int, month: Int, day: Int) {
        let z = epochDay + 719468
        let era = (z >= 0 ? z : z - 146096) / 146097
        let doe = z - era * 146097
        let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        let doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        let mp = (5 * doy + 2) / 153
        let d = doy - (153 * mp + 2) / 5 + 1
        let m = mp < 10 ? mp + 3 : mp - 9
        return (yoe + era * 400 + (m <= 2 ? 1 : 0), m, d)
    }

    public var year: Int { ymd.year }
    public var month: Int { ymd.month }
    public var dayOfMonth: Int { ymd.day }
    public var weekday: Weekday { Weekday(rawValue: ((epochDay + 3) % 7 + 7) % 7 + 1)! }

    public static func isLeap(_ y: Int) -> Bool { (y % 4 == 0 && y % 100 != 0) || y % 400 == 0 }
    public static func length(year: Int, month: Int) -> Int {
        switch month {
        case 2: return isLeap(year) ? 29 : 28
        case 4, 6, 9, 11: return 30
        default: return 31
        }
    }
    public var lengthOfMonth: Int { let p = ymd; return Day.length(year: p.year, month: p.month) }
    public var firstOfMonth: Day { let p = ymd; return Day(year: p.year, month: p.month, day: 1) }
    public var lastOfMonth: Day { let p = ymd; return Day(year: p.year, month: p.month, day: Day.length(year: p.year, month: p.month)) }
    public var dayOfYear: Int { epochDay - Day(year: year, month: 1, day: 1).epochDay + 1 }

    public func plusDays(_ n: Int) -> Day { Day(epochDay: epochDay + n) }
    public func minusDays(_ n: Int) -> Day { Day(epochDay: epochDay - n) }
    public func plusWeeks(_ n: Int) -> Day { plusDays(7 * n) }
    public func minusWeeks(_ n: Int) -> Day { plusDays(-7 * n) }
    /// Like LocalDate.plusMonths: the day is clamped to the end of the target month.
    public func plusMonths(_ n: Int) -> Day {
        let p = ymd
        let total = p.year * 12 + (p.month - 1) + n
        let y = Int((Double(total) / 12).rounded(.down))
        let m = total - y * 12 + 1
        return Day(year: y, month: m, day: min(p.day, Day.length(year: y, month: m)))
    }
    public func minusMonths(_ n: Int) -> Day { plusMonths(-n) }
    public func plusYears(_ n: Int) -> Day { plusMonths(12 * n) }

    public func withDayOfMonth(_ d: Int) -> Day { let p = ymd; return Day(year: p.year, month: p.month, day: min(max(1, d), Day.length(year: p.year, month: p.month))) }

    public static func < (a: Day, b: Day) -> Bool { a.epochDay < b.epochDay }
    public static func - (a: Day, b: Day) -> Int { a.epochDay - b.epochDay }

    public var description: String { let p = ymd; return String(format: "%04d-%02d-%02d", p.year, p.month, p.day) }

    /// Parses "yyyy-MM-dd".
    public init?(iso: String) {
        let parts = iso.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3, (1...12).contains(parts[1]), (1...31).contains(parts[2]) else { return nil }
        self.init(year: parts[0], month: parts[1], day: parts[2])
    }

    // MARK: Foundation bridges (local time zone)

    public init(_ date: Date, calendar: Calendar = .current) {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        self.init(year: c.year!, month: c.month!, day: c.day!)
    }

    public func date(at time: TimeOfDay = TimeOfDay(seconds: 0), calendar: Calendar = .current) -> Date {
        let p = ymd
        var c = DateComponents()
        c.year = p.year; c.month = p.month; c.day = p.day
        c.hour = time.hour; c.minute = time.minute; c.second = time.second
        return calendar.date(from: c) ?? Date(timeIntervalSince1970: TimeInterval(epochDay * 86400))
    }

    /// Pins "today" (only used to record the demo video, so phone and Mac show the same day).
    public static var fixedToday: Day?
    public static func today(calendar: Calendar = .current) -> Day { fixedToday ?? Day(Date(), calendar: calendar) }
}

/// A wall-clock time, stored as seconds since midnight (like LocalTime.toSecondOfDay).
public struct TimeOfDay: Hashable, Comparable, Codable, Sendable {
    public var seconds: Int
    public init(seconds: Int) { self.seconds = Swift.max(0, Swift.min(86399, seconds)) }
    public init(hour: Int, minute: Int, second: Int = 0) { self.init(seconds: hour * 3600 + minute * 60 + second) }
    public var hour: Int { seconds / 3600 }
    public var minute: Int { seconds % 3600 / 60 }
    public var second: Int { seconds % 60 }
    public static func < (a: TimeOfDay, b: TimeOfDay) -> Bool { a.seconds < b.seconds }
    public static let endOfDay = TimeOfDay(seconds: 86399)
    public static let nine = TimeOfDay(hour: 9, minute: 0)

    public init(_ date: Date, calendar: Calendar = .current) {
        let c = calendar.dateComponents([.hour, .minute, .second], from: date)
        self.init(hour: c.hour ?? 0, minute: c.minute ?? 0, second: c.second ?? 0)
    }
}

/// A date and time without a zone (java.time.LocalDateTime), used by custom reminders.
public struct DayTime: Hashable, Comparable, Sendable {
    public var day: Day
    public var time: TimeOfDay
    public init(day: Day, time: TimeOfDay) { self.day = day; self.time = time }
    public static func < (a: DayTime, b: DayTime) -> Bool { (a.day, a.time) < (b.day, b.time) }

    public func minusMinutes(_ m: Int) -> DayTime {
        let total = day.epochDay * 86400 + time.seconds - m * 60
        let d = Int((Double(total) / 86400).rounded(.down))
        return DayTime(day: Day(epochDay: d), time: TimeOfDay(seconds: total - d * 86400))
    }

    /// ISO text the way LocalDateTime.toString() writes it ("2026-11-17T09:00", seconds only when non-zero).
    public var iso: String {
        let base = "\(day)T" + String(format: "%02d:%02d", time.hour, time.minute)
        return time.second == 0 ? base : base + String(format: ":%02d", time.second)
    }

    public init?(iso: String) {
        let halves = iso.split(separator: "T")
        guard halves.count == 2, let d = Day(iso: String(halves[0])) else { return nil }
        let clock = halves[1].split(separator: ":").map { Double($0) ?? 0 }
        guard clock.count >= 2 else { return nil }
        day = d
        time = TimeOfDay(hour: Int(clock[0]), minute: Int(clock[1]), second: clock.count > 2 ? Int(clock[2]) : 0)
    }

    public var date: Date { day.date(at: time) }
    public init(_ date: Date) { day = Day(date); time = TimeOfDay(date) }
}

/// Calendar helpers that respect the chosen first day of the week.
public enum DateRules {
    public static func startOfWeek(_ d: Day, _ weekStart: Weekday) -> Day {
        let back = ((d.weekday.rawValue - weekStart.rawValue) % 7 + 7) % 7
        return d.minusDays(back)
    }
    public static func endOfWeek(_ d: Day, _ weekStart: Weekday) -> Day { startOfWeek(d, weekStart).plusDays(6) }
    public static func weekDays(_ d: Day, _ weekStart: Weekday) -> [Day] { let s = startOfWeek(d, weekStart); return (0..<7).map { s.plusDays($0) } }
    public static func orderedWeekdays(_ weekStart: Weekday) -> [Weekday] { (0..<7).map { weekStart.plus($0) } }
    /// 6×7 grid covering the month, starting at the week start.
    public static func monthGrid(_ month: Day, _ weekStart: Weekday) -> [Day] {
        let s = startOfWeek(month.firstOfMonth, weekStart)
        return (0..<42).map { s.plusDays($0) }
    }
    public static func daysBetweenInclusive(_ a: Day, _ b: Day) -> [Day] { b < a ? [] : (0...(b - a)).map { a.plusDays($0) } }
    /// Next date strictly after `from` on `day`.
    public static func nextWeekday(_ from: Day, _ day: Weekday) -> Day {
        var n = ((day.rawValue - from.weekday.rawValue) % 7 + 7) % 7
        if n == 0 { n = 7 }
        return from.plusDays(n)
    }
}

public extension Date {
    var epochMillis: Int64 { Int64((timeIntervalSince1970 * 1000).rounded()) }
    init(epochMillis: Int64) { self.init(timeIntervalSince1970: TimeInterval(epochMillis) / 1000) }
}
