package com.dayline.app.core.domain

import com.dayline.app.core.model.Habit
import com.dayline.app.core.model.Reminder
import com.dayline.app.core.model.ReminderType
import com.dayline.app.core.model.Task
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Pure rules for when reminders fire; the Android scheduler only executes these decisions. */
object ReminderPlanner {

    /** Moment a reminder should fire, or null if it can't be computed (e.g. the task has no date). */
    fun triggerTime(reminder: Reminder, task: Task?, defaultTime: LocalTime): LocalDateTime? = when (reminder.type) {
        ReminderType.CUSTOM -> reminder.at
        ReminderType.TASK -> {
            val date = task?.startDate ?: task?.dueDate
            date?.atTime(task?.startTime ?: task?.dueTime ?: defaultTime)?.minusMinutes(reminder.offsetMinutes.toLong())
        }
        ReminderType.DEADLINE -> {
            val date = task?.dueDate
            date?.atTime(task?.dueTime ?: defaultTime)?.minusMinutes(reminder.offsetMinutes.toLong())
        }
    }

    /** Whether a reminder should be scheduled at all right now. */
    fun shouldSchedule(reminder: Reminder, task: Task?, trigger: LocalDateTime?, now: LocalDateTime): Boolean {
        if (reminder.isDismissed || trigger == null || !trigger.isAfter(now)) return false
        if (reminder.taskId != null && (task == null || task.isCompleted)) return false
        return true
    }

    /** Next time a habit reminder should fire, skipping today if it's already done or the time has passed. */
    fun nextHabitReminder(habit: Habit, now: LocalDateTime, doneToday: Boolean): LocalDateTime? {
        val time = habit.reminderTime ?: return null
        if (habit.isArchived) return null
        var date: LocalDate = now.toLocalDate()
        repeat(15) {
            val candidate = date.atTime(time)
            val skipToday = date == now.toLocalDate() && (doneToday || !candidate.isAfter(now))
            if (!skipToday && HabitStreakCalculator.isScheduled(habit, date)) return candidate
            date = date.plusDays(1)
        }
        return null
    }

    /** Human label for a relative reminder. */
    fun describeOffset(minutes: Int): String = when {
        minutes == 0 -> "At time"
        minutes < 60 -> "$minutes min before"
        minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)} day${if (minutes >= 48 * 60) "s" else ""} before"
        minutes % 60 == 0 -> "${minutes / 60} hour${if (minutes >= 120) "s" else ""} before"
        else -> "${minutes / 60}h ${minutes % 60}m before"
    }

    val standardOffsets = listOf(0, 10, 30, 60, 120, 24 * 60)
}
