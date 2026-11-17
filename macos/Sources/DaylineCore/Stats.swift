import Foundation

public enum StatsPeriod: String, CaseIterable, Sendable {
    case day, week, month
    public var label: String { switch self { case .day: "Day"; case .week: "Week"; case .month: "Month" } }
}

public struct DayValue: Hashable, Sendable { public var date: Day; public var value: Int }

public struct ProjectProgress: Hashable, Sendable {
    public var projectId: Int64, name: String, color: Int32, completed: Int, total: Int, focusMinutes: Int
    public var fraction: Double { total == 0 ? 0 : Double(completed) / Double(total) }
}

public struct StatsReport: Sendable {
    public var period: StatsPeriod, start: Day, end: Day
    public var completed: Int, remaining: Int, overdueInPeriod: Int, overdueNow: Int
    public var completionRate: Double
    public var focusMinutes: Int, focusSessions: Int, averageSessionMinutes: Int
    public var habitCheckIns: Int, habitConsistency: Double
    public var completedSeries: [DayValue], focusSeries: [DayValue], overdueSeries: [DayValue]
    public var priorityDistribution: [Priority: Int]
    public var projectProgress: [ProjectProgress]
    public var previousCompleted: Int, previousFocusMinutes: Int
    public var heatmap: [DayValue]

    public var completedChange: Int? { pct(previousCompleted, completed) }
    public var focusChange: Int? { pct(previousFocusMinutes, focusMinutes) }
    public var hasAnyData: Bool { completed + remaining + overdueNow + focusMinutes + habitCheckIns > 0 || heatmap.contains { $0.value > 0 } }
    private func pct(_ prev: Int, _ now: Int) -> Int? { prev == 0 ? nil : Int(Double(now - prev) * 100 / Double(prev)) }
}

/// Analytics derived only from stored data. Mirrors the phone's StatsCalculator.
public enum StatsCalculator {
    public static func range(_ p: StatsPeriod, _ anchor: Day, _ ws: Weekday) -> (Day, Day) {
        switch p {
        case .day: return (anchor, anchor)
        case .week: return (DateRules.startOfWeek(anchor, ws), DateRules.endOfWeek(anchor, ws))
        case .month: return (anchor.firstOfMonth, anchor.lastOfMonth)
        }
    }

    static func previous(_ p: StatsPeriod, _ a: Day) -> Day {
        switch p { case .day: a.minusDays(1); case .week: a.minusWeeks(1); case .month: a.minusMonths(1) }
    }

    public static func report(_ data: DaylineData, period: StatsPeriod, anchor: Day, today: Day, weekStart ws: Weekday = .monday) -> StatsReport {
        let (start, end) = range(period, anchor, ws)
        func inRange(_ d: Day?, _ s: Day, _ e: Day) -> Bool { guard let d else { return false }; return !(d < s) && !(d > e) }
        func completedOn(_ t: TaskItem) -> Day? { t.completedAt.map { Day($0) } }
        func dayOf(_ f: FocusSession) -> Day { Day(f.startedAt) }
        let tasks = data.tasks

        let completedTasks = tasks.filter { $0.isCompleted && inRange(completedOn($0), start, end) }
        let remaining = tasks.filter { !$0.isCompleted && inRange($0.dueDate, start, end) }
        let overdueInPeriod = tasks.filter { t in
            guard let due = t.dueDate, inRange(due, start, end), due < today else { return false }
            return !t.isCompleted || (completedOn(t).map { $0 > due } ?? false)
        }.count
        let overdueNow = tasks.filter { $0.isOverdue(today) }.count
        let denom = completedTasks.count + remaining.count
        let rate = denom == 0 ? 0 : Double(completedTasks.count) / Double(denom)

        let sessions = data.focusSessions.filter { inRange(dayOf($0), start, end) }
        let focusMillis = sessions.reduce(Int64(0)) { $0 + $1.focusedMillis }
        let avg = sessions.isEmpty ? 0 : Int(focusMillis / Int64(sessions.count) / 60_000)

        let trendStart = period == .day ? anchor.minusDays(6) : start
        let trendDays = DateRules.daysBetweenInclusive(trendStart, end)
        var completedByDay: [Day: Int] = [:]
        for t in tasks where t.isCompleted { if let d = completedOn(t) { completedByDay[d, default: 0] += 1 } }
        var focusByDay: [Day: Int64] = [:]
        for f in data.focusSessions { focusByDay[dayOf(f), default: 0] += f.focusedMillis }

        let completedSeries = trendDays.map { DayValue(date: $0, value: completedByDay[$0] ?? 0) }
        let focusSeries = trendDays.map { DayValue(date: $0, value: Int((focusByDay[$0] ?? 0) / 60_000)) }
        let overdueSeries = trendDays.filter { !($0 > today) }.map { day in
            DayValue(date: day, value: tasks.filter { t in
                guard let due = t.dueDate, due < day else { return false }
                let doneOn = t.isCompleted ? completedOn(t) : nil
                return doneOn == nil || !(doneOn! < day)
            }.count)
        }

        let active = data.habits.filter { !$0.isArchived }
        let activeById = Dictionary(uniqueKeysWithValues: active.map { ($0.id, $0) })
        let checkIns = data.habitCompletions.filter { c in inRange(c.date, start, end) && (activeById[c.habitId].map { HabitStreaks.isDone($0, c.count) } ?? false) }.count
        let cons = active.filter { !($0.startDate > min(end, today)) }.map { h in
            HabitStreaks.consistency(h, data.habitCompletions.filter { $0.habitId == h.id }, from: start, to: end, weekStart: ws, today: today)
        }
        let habitConsistency = cons.isEmpty ? 0 : cons.reduce(0, +) / Double(cons.count)

        let inScope = tasks.filter { inRange($0.dueDate, start, end) || ($0.isCompleted && inRange(completedOn($0), start, end)) }
        var dist: [Priority: Int] = [:]
        for p in Priority.allCases { dist[p] = inScope.filter { $0.priority == p }.count }

        var focusByProject: [Int64: Int64] = [:]
        for f in data.focusSessions { if let p = f.projectId { focusByProject[p, default: 0] += f.focusedMillis } }
        let projectProgress = data.projects.filter { !$0.isArchived }.map { p in
            let pts = tasks.filter { $0.projectId == p.id }
            return ProjectProgress(projectId: p.id, name: p.name, color: p.color, completed: pts.filter(\.isCompleted).count, total: pts.count,
                                   focusMinutes: Int((focusByProject[p.id] ?? 0) / 60_000))
        }.sorted { $0.total > $1.total }

        let (ps, pe) = range(period, previous(period, anchor), ws)
        let prevCompleted = tasks.filter { $0.isCompleted && inRange(completedOn($0), ps, pe) }.count
        let prevFocus = Int(data.focusSessions.filter { inRange(dayOf($0), ps, pe) }.reduce(Int64(0)) { $0 + $1.focusedMillis } / 60_000)

        let last = min(end, today)
        let heatStart = DateRules.startOfWeek(last, ws).minusWeeks(17)
        let heatmap = DateRules.daysBetweenInclusive(heatStart, DateRules.endOfWeek(last, ws)).map {
            DayValue(date: $0, value: $0 > today ? -1 : completedByDay[$0] ?? 0)
        }

        return StatsReport(period: period, start: start, end: end, completed: completedTasks.count, remaining: remaining.count,
                           overdueInPeriod: overdueInPeriod, overdueNow: overdueNow, completionRate: rate,
                           focusMinutes: Int(focusMillis / 60_000), focusSessions: sessions.filter(\.completed).count, averageSessionMinutes: avg,
                           habitCheckIns: checkIns, habitConsistency: habitConsistency, completedSeries: completedSeries,
                           focusSeries: focusSeries, overdueSeries: overdueSeries, priorityDistribution: dist,
                           projectProgress: projectProgress, previousCompleted: prevCompleted, previousFocusMinutes: prevFocus, heatmap: heatmap)
    }
}
