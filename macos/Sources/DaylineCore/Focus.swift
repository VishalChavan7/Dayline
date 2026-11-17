import Foundation

public enum FocusPhase: String, CaseIterable, Codable, Sendable {
    case FOCUS, SHORT_BREAK, LONG_BREAK
    public var label: String { switch self { case .FOCUS: "Focus"; case .SHORT_BREAK: "Short break"; case .LONG_BREAK: "Long break" } }
}

public enum TimerStatus: String, Codable, Sendable { case IDLE, RUNNING, PAUSED }

public struct FocusConfig: Equatable, Sendable {
    public var focusMinutes = 25, shortBreakMinutes = 5, longBreakMinutes = 15, sessionsBeforeLongBreak = 4
    public var autoStartBreaks = false, autoStartFocus = false, dailyGoalMinutes = 120
    public init() {}
    public func durationMillis(_ p: FocusPhase) -> Int64 {
        let m: Int
        switch p { case .FOCUS: m = focusMinutes; case .SHORT_BREAK: m = shortBreakMinutes; case .LONG_BREAK: m = longBreakMinutes }
        return 60_000 * Int64(max(1, m))
    }
}

/// Timer state is kept as wall-clock timestamps, so it stays right while the app is in the background.
public struct FocusTimerState: Equatable, Codable, Sendable {
    public var status: TimerStatus = .IDLE
    public var phase: FocusPhase = .FOCUS
    public var durationMillis: Int64 = 25 * 60_000
    public var remainingMillis: Int64 = 25 * 60_000
    public var endAtMillis: Int64 = 0
    public var phaseStartedAtMillis: Int64 = 0
    public var completedFocusInCycle = 0
    public var taskId: Int64?
    public var projectId: Int64?
    public init() {}

    public func remaining(_ now: Int64) -> Int64 { status == .RUNNING ? max(0, endAtMillis - now) : remainingMillis }
    public func elapsed(_ now: Int64) -> Int64 { max(0, durationMillis - remaining(now)) }
    public func progress(_ now: Int64) -> Double { durationMillis <= 0 ? 0 : Double(elapsed(now)) / Double(durationMillis) }
}

public struct FocusRecord: Equatable, Sendable {
    public var startedAtMillis: Int64, endedAtMillis: Int64, focusedMillis: Int64, plannedMillis: Int64
    public var taskId: Int64?, projectId: Int64?, completed: Bool
}

public struct FocusTransition: Sendable { public var state: FocusTimerState; public var record: FocusRecord?; public var finishedPhase: FocusPhase? }

public enum FocusTimerMachine {
    public static let minRecordedMillis: Int64 = 60_000

    public static func idle(_ c: FocusConfig, phase: FocusPhase = .FOCUS, cycle: Int = 0, taskId: Int64? = nil, projectId: Int64? = nil) -> FocusTimerState {
        var s = FocusTimerState()
        s.phase = phase; s.durationMillis = c.durationMillis(phase); s.remainingMillis = s.durationMillis
        s.completedFocusInCycle = cycle; s.taskId = taskId; s.projectId = projectId
        return s
    }

    public static func start(_ s: FocusTimerState, now: Int64) -> FocusTimerState {
        switch s.status {
        case .RUNNING: return s
        case .PAUSED: return resume(s, now: now)
        case .IDLE:
            var n = s; n.status = .RUNNING; n.endAtMillis = now + s.remainingMillis; n.phaseStartedAtMillis = now
            return n
        }
    }

    public static func pause(_ s: FocusTimerState, now: Int64) -> FocusTimerState {
        guard s.status == .RUNNING else { return s }
        var n = s; n.status = .PAUSED; n.remainingMillis = s.remaining(now); return n
    }

    public static func resume(_ s: FocusTimerState, now: Int64) -> FocusTimerState {
        guard s.status == .PAUSED else { return s }
        var n = s; n.status = .RUNNING; n.endAtMillis = now + s.remainingMillis; return n
    }

    public static func setDuration(_ s: FocusTimerState, minutes: Int) -> FocusTimerState {
        guard s.status == .IDLE else { return s }
        var n = s; n.durationMillis = Int64(min(240, max(1, minutes))) * 60_000; n.remainingMillis = n.durationMillis; return n
    }

    public static func isDue(_ s: FocusTimerState, now: Int64) -> Bool { s.status == .RUNNING && now >= s.endAtMillis }

    public static func complete(_ s: FocusTimerState, now: Int64, config c: FocusConfig) -> FocusTransition {
        guard s.status == .RUNNING else { return FocusTransition(state: s, record: nil, finishedPhase: nil) }
        let endedAt = min(now, s.endAtMillis)
        let record = s.phase == .FOCUS
            ? FocusRecord(startedAtMillis: s.phaseStartedAtMillis, endedAtMillis: endedAt, focusedMillis: s.durationMillis,
                          plannedMillis: s.durationMillis, taskId: s.taskId, projectId: s.projectId, completed: true)
            : nil
        let cycle = s.phase == .FOCUS ? s.completedFocusInCycle + 1 : s.completedFocusInCycle
        let nextPhase: FocusPhase = s.phase == .FOCUS ? (cycle % max(1, c.sessionsBeforeLongBreak) == 0 ? .LONG_BREAK : .SHORT_BREAK) : .FOCUS
        let nextCycle = s.phase == .LONG_BREAK ? 0 : cycle
        var next = idle(c, phase: nextPhase, cycle: nextCycle, taskId: s.taskId, projectId: s.projectId)
        if nextPhase == .FOCUS ? c.autoStartFocus : c.autoStartBreaks { next = start(next, now: endedAt) }
        return FocusTransition(state: next, record: record, finishedPhase: s.phase)
    }

    public static func stop(_ s: FocusTimerState, now: Int64, config c: FocusConfig) -> FocusTransition {
        if s.status == .IDLE { return FocusTransition(state: idle(c, phase: s.phase, cycle: s.completedFocusInCycle, taskId: s.taskId, projectId: s.projectId), record: nil, finishedPhase: nil) }
        let focused = s.elapsed(now)
        let record = s.phase == .FOCUS && focused >= minRecordedMillis
            ? FocusRecord(startedAtMillis: s.phaseStartedAtMillis, endedAtMillis: now, focusedMillis: focused, plannedMillis: s.durationMillis,
                          taskId: s.taskId, projectId: s.projectId, completed: false)
            : nil
        return FocusTransition(state: idle(c, phase: .FOCUS, cycle: s.completedFocusInCycle, taskId: s.taskId, projectId: s.projectId), record: record, finishedPhase: nil)
    }

    public static func reset(_ s: FocusTimerState, config c: FocusConfig) -> FocusTimerState {
        idle(c, phase: s.phase, cycle: s.completedFocusInCycle, taskId: s.taskId, projectId: s.projectId)
    }

    public static func skip(_ s: FocusTimerState, config c: FocusConfig) -> FocusTimerState {
        idle(c, phase: s.phase == .FOCUS ? .SHORT_BREAK : .FOCUS, cycle: s.completedFocusInCycle, taskId: s.taskId, projectId: s.projectId)
    }

    public static func switchPhase(_ s: FocusTimerState, to p: FocusPhase, config c: FocusConfig) -> FocusTimerState {
        s.status != .IDLE ? s : idle(c, phase: p, cycle: s.completedFocusInCycle, taskId: s.taskId, projectId: s.projectId)
    }
}
