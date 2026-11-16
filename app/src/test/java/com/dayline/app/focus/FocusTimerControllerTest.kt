package com.dayline.app.focus

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dayline.app.core.domain.FocusPhase
import com.dayline.app.core.domain.TimerStatus
import com.dayline.app.notifications.Notifier
import com.dayline.app.notifications.ReminderScheduler
import com.dayline.app.testing.FakeAlarmGateway
import com.dayline.app.testing.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusTimerControllerTest {

    @Test
    fun runningTimerIsBackedByAlarmAndRecordsSessionWhenDue() = runTest {
        val env = TestEnv(backgroundScope)
        val alarms = FakeAlarmGateway()
        val scheduler = ReminderScheduler(env.context, env.db, env.settings, env.time, alarms)
        val controller = FocusTimerController(env.settings, env.focus, scheduler, Notifier(env.context), env.time, backgroundScope)
        controller.restore()

        controller.start().join()
        val state = controller.state.value
        assertEquals(TimerStatus.RUNNING, state.status)
        assertEquals(state.endAtMillis, alarms.alarms[ReminderScheduler.FOCUS_REQUEST_CODE])

        // App in background: 26 minutes pass, then the alarm fires.
        env.time.advanceMinutes(26)
        controller.checkDue()
        controller.checkDue() // second call (e.g. from the UI) must be a no-op

        val sessions = env.focus.sessions.first()
        assertEquals(1, sessions.size)
        assertEquals(25, sessions.single().focusedMinutes)
        assertTrue(sessions.single().completed)
        assertEquals(FocusPhase.SHORT_BREAK, controller.state.value.phase)
        assertNull(alarms.alarms[ReminderScheduler.FOCUS_REQUEST_CODE])
        env.close()
    }

    @Test
    fun stateSurvivesProcessDeath() = runTest {
        val env = TestEnv(backgroundScope)
        val alarms = FakeAlarmGateway()
        val scheduler = ReminderScheduler(env.context, env.db, env.settings, env.time, alarms)
        val first = FocusTimerController(env.settings, env.focus, scheduler, Notifier(env.context), env.time, backgroundScope)
        first.restore()
        first.start().join()
        env.time.advanceMinutes(5)
        first.pause().join()

        // A new controller instance (new process) restores the paused timer from storage.
        val second = FocusTimerController(env.settings, env.focus, scheduler, Notifier(env.context), env.time, backgroundScope)
        second.restore()
        assertEquals(TimerStatus.PAUSED, second.state.value.status)
        assertEquals(20 * 60_000L, second.state.value.remaining(env.time.nowMillis()))
        env.close()
    }

    @Test
    fun stoppingEarlyRecordsPartialSession() = runTest {
        val env = TestEnv(backgroundScope)
        val scheduler = ReminderScheduler(env.context, env.db, env.settings, env.time, FakeAlarmGateway())
        val c = FocusTimerController(env.settings, env.focus, scheduler, Notifier(env.context), env.time, backgroundScope)
        c.restore()
        c.attach(taskId = null, projectId = null).join()
        c.start().join()
        env.time.advanceMinutes(12)
        c.stop().join()
        val s = env.focus.sessions.first().single()
        assertEquals(12, s.focusedMinutes)
        assertEquals(false, s.completed)
        assertEquals(TimerStatus.IDLE, c.state.value.status)
        env.close()
    }
}
