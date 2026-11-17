import Foundation
import AppKit
import Combine
import DaylineCore

/// Runs the focus timer. State is stored as wall-clock times (like the phone), so it survives sleep and relaunch.
@MainActor
final class FocusTimer: ObservableObject {
    static weak var shared: FocusTimer?

    @Published private(set) var state: FocusTimerState
    @Published private(set) var now = Date().epochMillis
    private let store: Store
    private var ticker: Timer?
    var onPhaseFinished: ((FocusPhase, FocusTimerState) -> Void)?
    private let key: String

    init(store: Store, demo: Bool = false) {
        self.store = store
        key = demo ? "demo.focus.timer.state" : "focus.timer.state"
        if !demo, let raw = UserDefaults.standard.data(forKey: key), let s = try? JSONDecoder().decode(FocusTimerState.self, from: raw) {
            state = s
        } else {
            state = FocusTimerMachine.idle(store.focusConfig)
        }
        FocusTimer.shared = self
        catchUp()
        updateTicker()
    }

    var config: FocusConfig { store.focusConfig }

    var remaining: Int64 { state.remaining(now) }
    var remainingText: String {
        let s = Int((remaining + 999) / 1000)
        return String(format: "%d:%02d", s / 60, s % 60)
    }
    var progress: Double { state.progress(now) }

    private func set(_ s: FocusTimerState) {
        state = s
        if let raw = try? JSONEncoder().encode(s) { UserDefaults.standard.set(raw, forKey: key) }
        updateTicker()
    }

    private func updateTicker() {
        if state.status == .RUNNING {
            if ticker == nil {
                let t = Timer(timeInterval: 0.25, repeats: true) { [weak self] _ in Task { @MainActor in self?.tick() } }
                RunLoop.main.add(t, forMode: .common)
                ticker = t
            }
        } else {
            ticker?.invalidate(); ticker = nil
        }
        now = Date().epochMillis
    }

    private func tick() {
        now = Date().epochMillis
        if FocusTimerMachine.isDue(state, now: now) { finish() }
    }

    /// After sleep or relaunch, finish a phase that ended meanwhile.
    func catchUp() { if FocusTimerMachine.isDue(state, now: Date().epochMillis) { finish() } }

    private func finish() {
        let t = FocusTimerMachine.complete(state, now: Date().epochMillis, config: config)
        if let r = t.record { store.record(r) }
        set(t.state)
        if let p = t.finishedPhase {
            NSSound(named: "Glass")?.play()
            onPhaseFinished?(p, t.state)
        }
    }

    // MARK: Controls

    func start(taskId: Int64? = nil) {
        var s = state
        if s.status == .IDLE {
            if let taskId { s.taskId = taskId; s.projectId = store.task(taskId)?.projectId }
        }
        set(FocusTimerMachine.start(s, now: Date().epochMillis))
    }

    func pause() { set(FocusTimerMachine.pause(state, now: Date().epochMillis)) }
    func toggle() { state.status == .RUNNING ? pause() : start() }

    func stop() {
        let t = FocusTimerMachine.stop(state, now: Date().epochMillis, config: config)
        if let r = t.record { store.record(r) }
        set(t.state)
    }

    func reset() { set(FocusTimerMachine.reset(state, config: config)) }
    func skip() { set(FocusTimerMachine.skip(state, config: config)) }
    func switchPhase(_ p: FocusPhase) { set(FocusTimerMachine.switchPhase(state, to: p, config: config)) }
    func setMinutes(_ m: Int) { set(FocusTimerMachine.setDuration(state, minutes: m)) }

    func link(taskId: Int64?) {
        var s = state
        s.taskId = taskId
        s.projectId = taskId.flatMap { store.task($0)?.projectId }
        set(s)
    }

    /// Settings changed: an idle timer picks up the new lengths.
    func configChanged() { if state.status == .IDLE { set(FocusTimerMachine.reset(state, config: config)) } }
}
