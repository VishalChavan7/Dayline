package com.dayline.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dayline.app.core.common.ReminderSync
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.db.DaylineDatabase
import com.dayline.app.core.data.db.toModel
import com.dayline.app.core.data.prefs.SettingsRepository
import com.dayline.app.core.domain.HabitStreakCalculator
import com.dayline.app.core.domain.ReminderPlanner
import com.dayline.app.core.model.Reminder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Abstraction over AlarmManager so scheduling decisions can be tested without the platform. */
interface AlarmGateway {
    fun set(requestCode: Int, triggerAtMillis: Long, intent: Intent)
    fun cancel(requestCode: Int, intent: Intent)
    fun canScheduleExact(): Boolean
}

class AndroidAlarmGateway @Inject constructor(@ApplicationContext private val context: Context) : AlarmGateway {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    private fun pending(requestCode: Int, intent: Intent, flags: Int) =
        PendingIntent.getBroadcast(context, requestCode, intent, flags or PendingIntent.FLAG_IMMUTABLE)

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager?.canScheduleExactAlarms() == true

    override fun set(requestCode: Int, triggerAtMillis: Long, intent: Intent) {
        val am = alarmManager ?: return
        val pi = pending(requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        try {
            if (canScheduleExact()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                // Without the exact-alarm permission Android may deliver a few minutes late, but still in Doze.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    override fun cancel(requestCode: Int, intent: Intent) {
        val pi = PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pi != null) {
            alarmManager?.cancel(pi)
            pi.cancel()
        }
    }
}

/**
 * Turns persisted reminders into platform alarms. Every decision about *when* comes from [ReminderPlanner];
 * this class only reads data, applies user settings and talks to [AlarmGateway].
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: DaylineDatabase,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
    private val alarms: AlarmGateway,
) : ReminderSync {

    private fun reminderIntent(reminderId: Long) = Intent(context, ReminderReceiver::class.java).apply {
        action = ReminderReceiver.ACTION_REMINDER
        putExtra(ReminderReceiver.EXTRA_ID, reminderId)
    }

    private fun habitIntent(habitId: Long) = Intent(context, ReminderReceiver::class.java).apply {
        action = ReminderReceiver.ACTION_HABIT
        putExtra(ReminderReceiver.EXTRA_ID, habitId)
    }

    private fun focusIntent() = Intent(context, ReminderReceiver::class.java).apply { action = ReminderReceiver.ACTION_FOCUS_END }

    private fun toMillis(t: LocalDateTime): Long = t.atZone(time.zone()).toInstant().toEpochMilli()

    /** Returns the trigger time actually scheduled, or null if the reminder was cancelled. */
    suspend fun plan(reminder: Reminder): LocalDateTime? {
        val s = settings.current()
        val task = reminder.taskId?.let { db.taskDao().get(it)?.toModel() }
        val trigger = ReminderPlanner.triggerTime(reminder, task, s.defaultReminderTime)
        val enabled = s.taskRemindersEnabled
        return if (enabled && ReminderPlanner.shouldSchedule(reminder, task, trigger, time.localNow())) {
            alarms.set(requestCodeForReminder(reminder.id), toMillis(trigger!!), reminderIntent(reminder.id))
            trigger
        } else {
            alarms.cancel(requestCodeForReminder(reminder.id), reminderIntent(reminder.id))
            null
        }
    }

    override suspend fun syncTask(taskId: Long) {
        db.reminderDao().forTask(taskId).forEach { plan(it.toModel()) }
    }

    override suspend fun syncReminder(reminderId: Long) {
        val r = db.reminderDao().get(reminderId)?.toModel()
        if (r == null) cancelReminders(listOf(reminderId)) else plan(r)
    }

    override fun cancelReminders(reminderIds: List<Long>) {
        reminderIds.forEach { alarms.cancel(requestCodeForReminder(it), reminderIntent(it)) }
    }

    /** Schedules the next nudge for a habit, skipping today once it's done. */
    override suspend fun syncHabit(habitId: Long) {
        val habit = db.habitDao().get(habitId)?.toModel()
        val s = settings.current()
        if (habit == null || !s.habitRemindersEnabled) {
            cancelHabit(habitId); return
        }
        val now = time.localNow()
        val todayCount = db.habitDao().getCompletion(habitId, now.toLocalDate())?.count ?: 0
        val next = ReminderPlanner.nextHabitReminder(habit, now, HabitStreakCalculator.isDone(habit, todayCount))
        if (next == null) cancelHabit(habitId)
        else alarms.set(requestCodeForHabit(habitId), toMillis(next), habitIntent(habitId))
    }

    override fun cancelHabit(habitId: Long) = alarms.cancel(requestCodeForHabit(habitId), habitIntent(habitId))

    override suspend fun syncAll() {
        db.reminderDao().getAll().forEach { plan(it.toModel()) }
        db.habitDao().getAll().forEach { syncHabit(it.id) }
    }

    /** Snooze: fire this reminder again in [minutes], regardless of its original time. */
    fun snooze(reminderId: Long, minutes: Int = 10) {
        alarms.set(requestCodeForReminder(reminderId), time.nowMillis() + minutes * 60_000L, reminderIntent(reminderId).apply {
            putExtra(ReminderReceiver.EXTRA_SNOOZED, true)
        })
    }

    private fun countdownIntent(endAtMillis: Long) = Intent(context, ReminderReceiver::class.java).apply {
        action = ReminderReceiver.ACTION_FOCUS_COUNTDOWN
        putExtra(ReminderReceiver.EXTRA_END, endAtMillis)
    }

    /** Ends the running phase and, three seconds before, buzzes once a second as a heads-up. */
    fun scheduleFocusEnd(endAtMillis: Long) {
        alarms.set(FOCUS_REQUEST_CODE, endAtMillis, focusIntent())
        alarms.set(FOCUS_COUNTDOWN_CODE, endAtMillis - 3_000L, countdownIntent(endAtMillis))
    }

    private fun widgetIntent() = Intent(context, ReminderReceiver::class.java).apply { action = ReminderReceiver.ACTION_WIDGET_REFRESH }

    /** Redraws the focus widget when a long session drops below one hour (its digits switch from h/m to m/s). */
    fun scheduleWidgetRefresh(atMillis: Long) = alarms.set(WIDGET_REFRESH_CODE, atMillis, widgetIntent())
    fun cancelWidgetRefresh() = alarms.cancel(WIDGET_REFRESH_CODE, widgetIntent())

    fun cancelFocusEnd() {
        alarms.cancel(FOCUS_REQUEST_CODE, focusIntent())
        alarms.cancel(FOCUS_COUNTDOWN_CODE, countdownIntent(0L))
    }

    fun canScheduleExact(): Boolean = alarms.canScheduleExact()

    companion object {
        const val FOCUS_REQUEST_CODE = 2_000_000_001
        const val FOCUS_COUNTDOWN_CODE = 2_000_000_002
        const val WIDGET_REFRESH_CODE = 2_000_000_003
        fun requestCodeForReminder(id: Long): Int = (id % 1_000_000_000L).toInt()
        fun requestCodeForHabit(id: Long): Int = 1_000_000_000 + (id % 900_000_000L).toInt()
    }
}
