package com.dayline.app.notifications

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dayline.app.core.model.Habit
import com.dayline.app.core.model.Reminder
import com.dayline.app.core.model.ReminderType
import com.dayline.app.core.model.Task
import com.dayline.app.core.model.TaskDraft
import com.dayline.app.testing.FakeAlarmGateway
import com.dayline.app.testing.TestEnv
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ReminderSchedulerTest {
    private val today = LocalDate.of(2026, 11, 12) // env clock: 10:00 UTC
    private fun millis(dt: LocalDateTime) = dt.toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun scheduler(env: TestEnv, alarms: FakeAlarmGateway) = ReminderScheduler(env.context, env.db, env.settings, env.time, alarms)

    @Test
    fun schedulesFutureDeadlineReminderAtTheRightTime() = runTest {
        val env = TestEnv(backgroundScope)
        val alarms = FakeAlarmGateway()
        val id = env.tasks.save(TaskDraft(Task(title = "Report", dueDate = today, dueTime = LocalTime.of(17, 0)), reminders = listOf(Reminder(type = ReminderType.DEADLINE, offsetMinutes = 30))))
        scheduler(env, alarms).syncTask(id)
        val r = env.db.reminderDao().forTask(id).single()
        assertEquals(millis(today.atTime(16, 30)), alarms.alarms[ReminderScheduler.requestCodeForReminder(r.id)])
        env.close()
    }

    @Test
    fun pastOrCompletedRemindersAreCancelled() = runTest {
        val env = TestEnv(backgroundScope)
        val alarms = FakeAlarmGateway()
        val s = scheduler(env, alarms)
        val past = env.tasks.save(TaskDraft(Task(title = "Earlier", dueDate = today, dueTime = LocalTime.of(8, 0)), reminders = listOf(Reminder(type = ReminderType.DEADLINE))))
        s.syncTask(past)
        assertTrue(alarms.alarms.isEmpty())

        val future = env.tasks.save(TaskDraft(Task(title = "Later", dueDate = today.plusDays(1)), reminders = listOf(Reminder(type = ReminderType.DEADLINE))))
        s.syncTask(future)
        assertEquals(1, alarms.alarms.size)
        env.tasks.setCompleted(future, true)
        s.syncTask(future)
        assertTrue(alarms.alarms.isEmpty())
        env.close()
    }

    @Test
    fun disablingTaskRemindersCancelsAlarms() = runTest {
        val env = TestEnv(backgroundScope)
        val alarms = FakeAlarmGateway()
        val s = scheduler(env, alarms)
        val id = env.tasks.save(TaskDraft(Task(title = "Later", dueDate = today.plusDays(2)), reminders = listOf(Reminder(type = ReminderType.DEADLINE))))
        s.syncAll()
        assertEquals(1, alarms.alarms.size)
        env.settings.setTaskReminders(false)
        s.syncTask(id)
        assertTrue(alarms.alarms.isEmpty())
        env.close()
    }

    @Test
    fun habitReminderMovesToTomorrowOnceDone() = runTest {
        val env = TestEnv(backgroundScope)
        val alarms = FakeAlarmGateway()
        val s = scheduler(env, alarms)
        val h = env.habits.save(Habit(name = "Read", color = 0, startDate = today.minusDays(5), reminderTime = LocalTime.of(20, 0)))
        s.syncHabit(h)
        val code = ReminderScheduler.requestCodeForHabit(h)
        assertEquals(millis(today.atTime(20, 0)), alarms.alarms[code])
        env.habits.toggle(h, today)
        s.syncHabit(h)
        assertEquals(millis(today.plusDays(1).atTime(20, 0)), alarms.alarms[code])
        env.close()
    }

    @Test
    fun snoozeSchedulesTenMinutesFromNow() = runTest {
        val env = TestEnv(backgroundScope)
        val alarms = FakeAlarmGateway()
        scheduler(env, alarms).snooze(42L)
        assertEquals(env.time.nowMillis() + 600_000L, alarms.alarms[ReminderScheduler.requestCodeForReminder(42L)])
        assertFalse(alarms.alarms.isEmpty())
        env.close()
    }
}
