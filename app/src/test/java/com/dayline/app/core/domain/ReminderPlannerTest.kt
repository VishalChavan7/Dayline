package com.dayline.app.core.domain

import com.dayline.app.core.model.Habit
import com.dayline.app.core.model.HabitFrequency
import com.dayline.app.core.model.Reminder
import com.dayline.app.core.model.ReminderType
import com.dayline.app.core.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ReminderPlannerTest {
    private val day = LocalDate.of(2026, 11, 12)
    private val nine = LocalTime.of(9, 0)

    @Test
    fun deadlineReminderUsesDueTimeMinusOffset() {
        val task = Task(id = 1, title = "Report", dueDate = day, dueTime = LocalTime.of(17, 0))
        val r = Reminder(taskId = 1, type = ReminderType.DEADLINE, offsetMinutes = 60)
        assertEquals(day.atTime(16, 0), ReminderPlanner.triggerTime(r, task, nine))
    }

    @Test
    fun allDayTaskFallsBackToDefaultTime() {
        val task = Task(id = 1, title = "Pay rent", dueDate = day)
        val r = Reminder(taskId = 1, type = ReminderType.DEADLINE, offsetMinutes = 24 * 60)
        assertEquals(day.minusDays(1).atTime(9, 0), ReminderPlanner.triggerTime(r, task, nine))
    }

    @Test
    fun taskReminderUsesStartDateAndTime() {
        val task = Task(id = 1, title = "Gym", startDate = day, startTime = LocalTime.of(7, 30), dueDate = day.plusDays(1))
        val r = Reminder(taskId = 1, type = ReminderType.TASK, offsetMinutes = 0)
        assertEquals(day.atTime(7, 30), ReminderPlanner.triggerTime(r, task, nine))
    }

    @Test
    fun undatedTaskCannotBeReminded() {
        val r = Reminder(taskId = 1, type = ReminderType.DEADLINE)
        assertNull(ReminderPlanner.triggerTime(r, Task(id = 1, title = "x"), nine))
    }

    @Test
    fun doesNotScheduleForCompletedTasksOrPastTimes() {
        val now = day.atTime(12, 0)
        val task = Task(id = 1, title = "x", dueDate = day, dueTime = LocalTime.of(15, 0))
        val r = Reminder(taskId = 1, type = ReminderType.DEADLINE)
        assertTrue(ReminderPlanner.shouldSchedule(r, task, day.atTime(15, 0), now))
        assertFalse(ReminderPlanner.shouldSchedule(r, task.copy(isCompleted = true), day.atTime(15, 0), now))
        assertFalse(ReminderPlanner.shouldSchedule(r, task, day.atTime(11, 0), now))
        assertFalse(ReminderPlanner.shouldSchedule(r.copy(isDismissed = true), task, day.atTime(15, 0), now))
    }

    @Test
    fun customStandaloneReminder() {
        val at = LocalDateTime.of(2026, 11, 20, 8, 15)
        val r = Reminder(type = ReminderType.CUSTOM, at = at, title = "Call bank")
        assertEquals(at, ReminderPlanner.triggerTime(r, null, nine))
        assertTrue(ReminderPlanner.shouldSchedule(r, null, at, day.atTime(0, 0)))
    }

    @Test
    fun habitReminderSkipsTodayWhenDoneOrPassed() {
        val habit = Habit(name = "Stretch", color = 0, startDate = day.minusDays(3), reminderTime = LocalTime.of(20, 0))
        assertEquals(day.atTime(20, 0), ReminderPlanner.nextHabitReminder(habit, day.atTime(10, 0), doneToday = false))
        assertEquals(day.plusDays(1).atTime(20, 0), ReminderPlanner.nextHabitReminder(habit, day.atTime(10, 0), doneToday = true))
        assertEquals(day.plusDays(1).atTime(20, 0), ReminderPlanner.nextHabitReminder(habit, day.atTime(21, 0), doneToday = false))
    }

    @Test
    fun habitReminderRespectsSpecificDays() {
        // day is Thursday; only Mondays
        val habit = Habit(name = "Plan week", color = 0, startDate = day.minusDays(10), reminderTime = LocalTime.of(8, 0),
            frequency = HabitFrequency.SPECIFIC_DAYS, weekdays = setOf(DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 11, 16).atTime(8, 0), ReminderPlanner.nextHabitReminder(habit, day.atTime(9, 0), false))
    }

    @Test
    fun describesOffsets() {
        assertEquals("At time", ReminderPlanner.describeOffset(0))
        assertEquals("10 min before", ReminderPlanner.describeOffset(10))
        assertEquals("1 hour before", ReminderPlanner.describeOffset(60))
        assertEquals("1 day before", ReminderPlanner.describeOffset(1440))
    }
}
