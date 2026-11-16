package com.dayline.app.core.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/** Injectable clock so time-dependent logic is testable. */
interface TimeProvider {
    fun now(): Instant
    fun zone(): ZoneId
    fun today(): LocalDate = LocalDate.ofInstant(now(), zone())
    fun localNow(): LocalDateTime = LocalDateTime.ofInstant(now(), zone())
    fun nowMillis(): Long = now().toEpochMilli()
}

class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun now(): Instant = Instant.now()
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

/** Bridge from the data layer to the platform scheduler (alarms, notifications). */
interface ReminderSync {
    /** Re-plan every reminder belonging to a task (call after any change to the task or its reminders). */
    suspend fun syncTask(taskId: Long)

    /** Cancel alarms for reminders that are about to be deleted. */
    fun cancelReminders(reminderIds: List<Long>)

    suspend fun syncReminder(reminderId: Long)
    suspend fun syncHabit(habitId: Long)
    fun cancelHabit(habitId: Long)

    /** Re-plan everything (after boot, restore, time-zone change or settings change). */
    suspend fun syncAll()
}
