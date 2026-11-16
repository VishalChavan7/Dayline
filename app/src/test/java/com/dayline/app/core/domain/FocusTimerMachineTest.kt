package com.dayline.app.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusTimerMachineTest {
    private val config = FocusConfig(focusMinutes = 25, shortBreakMinutes = 5, longBreakMinutes = 15, sessionsBeforeLongBreak = 2)
    private val min = 60_000L

    @Test
    fun startComputesEndFromWallClock() {
        val s = FocusTimerMachine.start(FocusTimerMachine.idle(config), now = 1_000L)
        assertEquals(TimerStatus.RUNNING, s.status)
        assertEquals(1_000L + 25 * min, s.endAtMillis)
        assertEquals(10 * min, s.remaining(1_000L + 15 * min))
    }

    @Test
    fun pauseFreezesRemainingAndResumeShiftsEnd() {
        var s = FocusTimerMachine.start(FocusTimerMachine.idle(config), 0L)
        s = FocusTimerMachine.pause(s, 10 * min)
        assertEquals(15 * min, s.remaining(999 * min)) // time passing while paused doesn't matter
        s = FocusTimerMachine.resume(s, 100 * min)
        assertEquals(100 * min + 15 * min, s.endAtMillis)
    }

    @Test
    fun completingFocusRecordsSessionAndMovesToShortBreak() {
        val running = FocusTimerMachine.start(FocusTimerMachine.idle(config, taskId = 7), 0L)
        assertTrue(FocusTimerMachine.isDue(running, 25 * min))
        val t = FocusTimerMachine.complete(running, 26 * min, config)
        assertNotNull(t.record)
        assertEquals(25 * min, t.record!!.focusedMillis)
        assertEquals(25 * min, t.record!!.endedAtMillis) // capped at scheduled end, not when we noticed
        assertEquals(7L, t.record!!.taskId)
        assertTrue(t.record!!.completed)
        assertEquals(FocusPhase.SHORT_BREAK, t.state.phase)
        assertEquals(TimerStatus.IDLE, t.state.status)
    }

    @Test
    fun longBreakAfterConfiguredNumberOfSessions() {
        var s = FocusTimerMachine.start(FocusTimerMachine.idle(config), 0L)
        s = FocusTimerMachine.complete(s, 25 * min, config).state // -> short break, cycle 1
        s = FocusTimerMachine.complete(FocusTimerMachine.start(s, 0L), 5 * min, config).state // -> focus
        s = FocusTimerMachine.complete(FocusTimerMachine.start(s, 0L), 25 * min, config).state // -> long break, cycle 2
        assertEquals(FocusPhase.LONG_BREAK, s.phase)
        s = FocusTimerMachine.complete(FocusTimerMachine.start(s, 0L), 15 * min, config).state
        assertEquals(FocusPhase.FOCUS, s.phase)
        assertEquals(0, s.completedFocusInCycle)
    }

    @Test
    fun breaksAreNotRecorded() {
        val brk = FocusTimerMachine.start(FocusTimerMachine.idle(config, FocusPhase.SHORT_BREAK), 0L)
        assertNull(FocusTimerMachine.complete(brk, 5 * min, config).record)
    }

    @Test
    fun stoppingEarlyRecordsPartialFocusOverAMinute() {
        val running = FocusTimerMachine.start(FocusTimerMachine.idle(config), 0L)
        val t = FocusTimerMachine.stop(running, 10 * min, config)
        assertEquals(10 * min, t.record!!.focusedMillis)
        assertFalse(t.record!!.completed)
        assertEquals(TimerStatus.IDLE, t.state.status)
        assertNull(FocusTimerMachine.stop(FocusTimerMachine.start(FocusTimerMachine.idle(config), 0L), 30_000L, config).record)
    }

    @Test
    fun autoStartBreakStartsAtScheduledEnd() {
        val auto = config.copy(autoStartBreaks = true)
        val running = FocusTimerMachine.start(FocusTimerMachine.idle(auto), 0L)
        val next = FocusTimerMachine.complete(running, 27 * min, auto).state
        assertEquals(TimerStatus.RUNNING, next.status)
        assertEquals(25 * min + 5 * min, next.endAtMillis)
    }

    @Test
    fun customDurationOnlyWhenIdle() {
        val s = FocusTimerMachine.setDuration(FocusTimerMachine.idle(config), 50)
        assertEquals(50 * min, s.durationMillis)
        val running = FocusTimerMachine.start(s, 0L)
        assertEquals(running, FocusTimerMachine.setDuration(running, 10))
    }
}
