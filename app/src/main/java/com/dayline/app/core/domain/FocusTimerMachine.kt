package com.dayline.app.core.domain

enum class FocusPhase(val label: String) { FOCUS("Focus"), SHORT_BREAK("Short break"), LONG_BREAK("Long break") }

enum class TimerStatus { IDLE, RUNNING, PAUSED }

data class FocusConfig(
    val focusMinutes: Int = 25,
    val shortBreakMinutes: Int = 5,
    val longBreakMinutes: Int = 15,
    val sessionsBeforeLongBreak: Int = 4,
    val autoStartBreaks: Boolean = false,
    val autoStartFocus: Boolean = false,
    val dailyGoalMinutes: Int = 120,
) {
    fun durationMillis(phase: FocusPhase): Long = 60_000L * when (phase) {
        FocusPhase.FOCUS -> focusMinutes
        FocusPhase.SHORT_BREAK -> shortBreakMinutes
        FocusPhase.LONG_BREAK -> longBreakMinutes
    }.coerceAtLeast(1)
}

/**
 * Timer state is stored as wall-clock timestamps (not ticking counters), so it stays correct when the app is
 * in the background or the process is killed: remaining time is always derived from [endAtMillis].
 */
data class FocusTimerState(
    val status: TimerStatus = TimerStatus.IDLE,
    val phase: FocusPhase = FocusPhase.FOCUS,
    val durationMillis: Long = 25 * 60_000L,
    /** Remaining time while IDLE or PAUSED. */
    val remainingMillis: Long = 25 * 60_000L,
    /** When the running phase will end (wall clock). */
    val endAtMillis: Long = 0L,
    /** When this phase was first started (for the session record). */
    val phaseStartedAtMillis: Long = 0L,
    val completedFocusInCycle: Int = 0,
    val taskId: Long? = null,
    val projectId: Long? = null,
) {
    fun remaining(now: Long): Long = when (status) {
        TimerStatus.RUNNING -> (endAtMillis - now).coerceAtLeast(0L)
        else -> remainingMillis
    }

    /** Time actually spent in the phase so far, excluding pauses. */
    fun elapsed(now: Long): Long = (durationMillis - remaining(now)).coerceAtLeast(0L)

    fun progress(now: Long): Float = if (durationMillis <= 0) 0f else elapsed(now).toFloat() / durationMillis
}

data class FocusRecord(
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val focusedMillis: Long,
    val plannedMillis: Long,
    val taskId: Long?,
    val projectId: Long?,
    val completed: Boolean,
)

data class Transition(val state: FocusTimerState, val record: FocusRecord? = null, val finishedPhase: FocusPhase? = null)

object FocusTimerMachine {
    /** Sessions shorter than this are not recorded when stopped early. */
    const val MIN_RECORDED_MILLIS = 60_000L

    fun idle(config: FocusConfig, phase: FocusPhase = FocusPhase.FOCUS, cycle: Int = 0, taskId: Long? = null, projectId: Long? = null): FocusTimerState {
        val d = config.durationMillis(phase)
        return FocusTimerState(TimerStatus.IDLE, phase, d, d, 0L, 0L, cycle, taskId, projectId)
    }

    fun start(state: FocusTimerState, now: Long): FocusTimerState = when (state.status) {
        TimerStatus.RUNNING -> state
        TimerStatus.PAUSED -> resume(state, now)
        TimerStatus.IDLE -> state.copy(
            status = TimerStatus.RUNNING,
            endAtMillis = now + state.remainingMillis,
            phaseStartedAtMillis = now,
        )
    }

    fun pause(state: FocusTimerState, now: Long): FocusTimerState =
        if (state.status != TimerStatus.RUNNING) state
        else state.copy(status = TimerStatus.PAUSED, remainingMillis = state.remaining(now))

    fun resume(state: FocusTimerState, now: Long): FocusTimerState =
        if (state.status != TimerStatus.PAUSED) state
        else state.copy(status = TimerStatus.RUNNING, endAtMillis = now + state.remainingMillis)

    /** Change the length of an idle timer (custom durations). */
    fun setDuration(state: FocusTimerState, minutes: Int): FocusTimerState {
        if (state.status != TimerStatus.IDLE) return state
        val d = minutes.coerceIn(1, 240) * 60_000L
        return state.copy(durationMillis = d, remainingMillis = d)
    }

    fun isDue(state: FocusTimerState, now: Long): Boolean = state.status == TimerStatus.RUNNING && now >= state.endAtMillis

    /** Called when the running phase reaches zero. Records completed focus phases and moves to the next phase. */
    fun complete(state: FocusTimerState, now: Long, config: FocusConfig): Transition {
        if (state.status != TimerStatus.RUNNING) return Transition(state)
        val endedAt = minOf(now, state.endAtMillis)
        val record = if (state.phase == FocusPhase.FOCUS) {
            FocusRecord(state.phaseStartedAtMillis, endedAt, state.durationMillis, state.durationMillis, state.taskId, state.projectId, completed = true)
        } else null
        val cycle = if (state.phase == FocusPhase.FOCUS) state.completedFocusInCycle + 1 else state.completedFocusInCycle
        val nextPhase = when (state.phase) {
            FocusPhase.FOCUS -> if (cycle % config.sessionsBeforeLongBreak.coerceAtLeast(1) == 0) FocusPhase.LONG_BREAK else FocusPhase.SHORT_BREAK
            FocusPhase.SHORT_BREAK, FocusPhase.LONG_BREAK -> FocusPhase.FOCUS
        }
        val nextCycle = if (state.phase == FocusPhase.LONG_BREAK) 0 else cycle
        var next = idle(config, nextPhase, nextCycle, state.taskId, state.projectId)
        val autoStart = if (nextPhase == FocusPhase.FOCUS) config.autoStartFocus else config.autoStartBreaks
        if (autoStart) next = start(next, endedAt)
        return Transition(next, record, state.phase)
    }

    /** Stop early. A focus phase longer than a minute is recorded as an incomplete session. */
    fun stop(state: FocusTimerState, now: Long, config: FocusConfig): Transition {
        if (state.status == TimerStatus.IDLE) return Transition(idle(config, state.phase, state.completedFocusInCycle, state.taskId, state.projectId))
        val focused = state.elapsed(now)
        val record = if (state.phase == FocusPhase.FOCUS && focused >= MIN_RECORDED_MILLIS) {
            FocusRecord(state.phaseStartedAtMillis, now, focused, state.durationMillis, state.taskId, state.projectId, completed = false)
        } else null
        return Transition(idle(config, FocusPhase.FOCUS, state.completedFocusInCycle, state.taskId, state.projectId), record)
    }

    /** Reset the current phase to its full length without recording anything. */
    fun reset(state: FocusTimerState, config: FocusConfig): FocusTimerState =
        idle(config, state.phase, state.completedFocusInCycle, state.taskId, state.projectId)

    /** Skip to the next phase without recording. */
    fun skip(state: FocusTimerState, config: FocusConfig): FocusTimerState {
        val next = when (state.phase) {
            FocusPhase.FOCUS -> FocusPhase.SHORT_BREAK
            else -> FocusPhase.FOCUS
        }
        return idle(config, next, state.completedFocusInCycle, state.taskId, state.projectId)
    }

    fun switchPhase(state: FocusTimerState, phase: FocusPhase, config: FocusConfig): FocusTimerState =
        if (state.status != TimerStatus.IDLE) state else idle(config, phase, state.completedFocusInCycle, state.taskId, state.projectId)
}
