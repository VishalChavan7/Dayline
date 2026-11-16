package com.dayline.app.focus

import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.prefs.SettingsRepository
import com.dayline.app.core.data.repository.FocusRepository
import com.dayline.app.core.domain.FocusConfig
import com.dayline.app.core.domain.FocusPhase
import com.dayline.app.core.domain.FocusTimerMachine
import com.dayline.app.core.domain.FocusTimerState
import com.dayline.app.core.domain.TimerStatus
import com.dayline.app.di.ApplicationScope
import com.dayline.app.notifications.Notifier
import com.dayline.app.notifications.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the single focus timer. State is persisted on every change and the end of a running phase is backed by
 * an alarm, so the timer finishes correctly even if the app is backgrounded or killed.
 * All transitions go through a mutex, so the alarm and the UI can both report "time's up" safely.
 */
@Singleton
class FocusTimerController @Inject constructor(
    private val settings: SettingsRepository,
    private val focusRepository: FocusRepository,
    private val scheduler: ReminderScheduler,
    private val notifier: Notifier,
    private val time: TimeProvider,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(FocusTimerMachine.idle(FocusConfig()))
    val state: StateFlow<FocusTimerState> = _state.asStateFlow()
    private var restored = false

    init {
        scope.launch { restore() }
    }

    private suspend fun config(): FocusConfig = settings.current().focus

    /** Loads persisted state; completes a phase that ended while the app wasn't running. */
    suspend fun restore() {
        mutex.withLock {
            if (restored) return@withLock
            val saved = settings.timerState.first()
            val cfg = config()
            _state.value = saved ?: FocusTimerMachine.idle(cfg)
            restored = true
        }
        checkDue()
        refreshSideEffects()
    }

    private suspend fun mutate(block: suspend (FocusTimerState, FocusConfig, Long) -> FocusTimerState) {
        if (!restored) restore()
        mutex.withLock {
            val next = block(_state.value, config(), time.nowMillis())
            _state.value = next
            settings.saveTimerState(next)
        }
        refreshSideEffects()
    }

    fun start() = scope.launch { mutate { s, _, now -> FocusTimerMachine.start(s, now) } }
    fun pause() = scope.launch { mutate { s, _, now -> FocusTimerMachine.pause(s, now) } }
    fun resume() = scope.launch { mutate { s, _, now -> FocusTimerMachine.resume(s, now) } }
    fun reset() = scope.launch { mutate { s, c, _ -> FocusTimerMachine.reset(s, c) } }
    fun skip() = scope.launch { mutate { s, c, _ -> FocusTimerMachine.skip(s, c) } }
    fun setDurationMinutes(minutes: Int) = scope.launch { mutate { s, _, _ -> FocusTimerMachine.setDuration(s, minutes) } }
    fun selectPhase(phase: FocusPhase) = scope.launch { mutate { s, c, _ -> FocusTimerMachine.switchPhase(s, phase, c) } }

    /** Associate the timer with a task and/or project (allowed any time; recorded with the session). */
    fun attach(taskId: Long?, projectId: Long?) = scope.launch { mutate { s, _, _ -> s.copy(taskId = taskId, projectId = projectId) } }

    /** Re-read durations after the user changes focus settings (only affects an idle timer). */
    fun applyConfig() = scope.launch {
        mutate { s, c, _ -> if (s.status == TimerStatus.IDLE) FocusTimerMachine.idle(c, s.phase, s.completedFocusInCycle, s.taskId, s.projectId) else s }
    }

    fun stop() = scope.launch {
        var recorded: com.dayline.app.core.domain.FocusRecord? = null
        mutate { s, c, now ->
            val t = FocusTimerMachine.stop(s, now, c)
            recorded = t.record
            t.state
        }
        recorded?.let { focusRepository.record(it) }
    }

    /** Completes the running phase if its time is up. Safe to call repeatedly from the UI and the alarm. */
    suspend fun checkDue() {
        var finished: FocusPhase? = null
        var record: com.dayline.app.core.domain.FocusRecord? = null
        var nextPhase: FocusPhase = FocusPhase.FOCUS
        mutex.withLock {
            val now = time.nowMillis()
            val s = _state.value
            if (FocusTimerMachine.isDue(s, now)) {
                val t = FocusTimerMachine.complete(s, now, config())
                _state.value = t.state
                settings.saveTimerState(t.state)
                finished = t.finishedPhase
                record = t.record
                nextPhase = t.state.phase
            }
        }
        record?.let { focusRepository.record(it) }
        finished?.let { phase ->
            if (settings.current().focusNotificationsEnabled) {
                if (phase == FocusPhase.FOCUS) notifier.showFocusFinished("Focus session complete", "Nice work. Time for a ${nextPhase.label.lowercase()}.")
                else notifier.showFocusFinished("Break's over", "Ready for the next focus session?")
            }
            refreshSideEffects()
        }
    }

    private suspend fun refreshSideEffects() {
        val s = _state.value
        val notificationsOn = settings.current().focusNotificationsEnabled
        when (s.status) {
            TimerStatus.RUNNING -> {
                scheduler.scheduleFocusEnd(s.endAtMillis)
                val hourMark = s.endAtMillis - 3_599_000L
                if (hourMark > time.nowMillis()) scheduler.scheduleWidgetRefresh(hourMark) else scheduler.cancelWidgetRefresh()
                if (notificationsOn) notifier.showFocusOngoing(s.phase.label, s.endAtMillis, running = true) else notifier.cancelFocusOngoing()
            }
            TimerStatus.PAUSED -> {
                scheduler.cancelFocusEnd()
                scheduler.cancelWidgetRefresh()
                if (notificationsOn) notifier.showFocusOngoing("${s.phase.label} (paused)", null, running = false) else notifier.cancelFocusOngoing()
            }
            TimerStatus.IDLE -> {
                scheduler.cancelFocusEnd()
                scheduler.cancelWidgetRefresh()
                notifier.cancelFocusOngoing()
            }
        }
    }
}
