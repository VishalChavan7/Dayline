package com.dayline.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dayline.app.core.common.ReminderSync
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.db.DaylineDatabase
import com.dayline.app.core.data.db.toModel
import com.dayline.app.core.data.repository.HabitRepository
import com.dayline.app.core.data.repository.TaskRepository
import com.dayline.app.core.model.ReminderType
import com.dayline.app.di.ApplicationScope
import com.dayline.app.focus.FocusTimerController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private fun BroadcastReceiver.runAsync(scope: CoroutineScope, block: suspend () -> Unit) {
    val pending = goAsync()
    scope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** Fires task, habit and focus alarms. */
@AndroidEntryPoint
class ReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var db: DaylineDatabase
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var sync: ReminderSync
    @Inject lateinit var focus: FocusTimerController
    @Inject lateinit var time: TimeProvider
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, -1L)
        runAsync(scope) {
            when (intent.action) {
                ACTION_REMINDER -> fireReminder(id, intent.getBooleanExtra(EXTRA_SNOOZED, false))
                ACTION_HABIT -> fireHabit(id)
                ACTION_FOCUS_END -> focus.checkDue()
                ACTION_WIDGET_REFRESH -> com.dayline.app.widget.WidgetUpdater.updateAll(context)
                ACTION_FOCUS_COUNTDOWN -> {
                    val end = intent.getLongExtra(EXTRA_END, 0L)
                    val s = focus.state.value
                    if (s.status == com.dayline.app.core.domain.TimerStatus.RUNNING && kotlin.math.abs(s.endAtMillis - end) < 1_000L) {
                        FocusCountdown.buzz(context, end - time.nowMillis())
                    }
                }
            }
        }
    }

    private suspend fun fireReminder(reminderId: Long, snoozed: Boolean) {
        val reminder = db.reminderDao().get(reminderId)?.toModel() ?: return
        if (reminder.isDismissed) return
        val task = reminder.taskId?.let { db.taskDao().get(it)?.toModel() }
        if (reminder.taskId != null && (task == null || task.isCompleted)) return
        val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        when {
            task == null -> notifier.showTaskReminder(reminderId, null, reminder.title.ifBlank { "Reminder" }, if (snoozed) "Snoozed reminder" else "It's time.", false)
            else -> {
                val due = task.dueDate
                val text = when (reminder.type) {
                    ReminderType.DEADLINE -> if (due != null) {
                        "Due " + (task.dueTime?.let { "at ${it.format(timeFmt)}" } ?: if (due == time.today()) "today" else "on $due")
                    } else "Deadline reminder"
                    ReminderType.TASK -> task.startTime?.let { "Starts at ${it.format(timeFmt)}" } ?: "Scheduled for today"
                    ReminderType.CUSTOM -> reminder.title.ifBlank { "Reminder" }
                }
                notifier.showTaskReminder(reminderId, task.id, task.title, if (snoozed) "$text · snoozed" else text, reminder.type == ReminderType.DEADLINE)
            }
        }
    }

    private suspend fun fireHabit(habitId: Long) {
        val habit = db.habitDao().get(habitId)?.toModel() ?: return
        notifier.showHabitReminder(habitId, habit.name, habit.description.ifBlank { "Keep your streak going today." })
        sync.syncHabit(habitId) // schedule tomorrow's (or the next scheduled day's) reminder
    }

    companion object {
        const val ACTION_REMINDER = "com.dayline.app.action.REMINDER"
        const val ACTION_HABIT = "com.dayline.app.action.HABIT"
        const val ACTION_FOCUS_END = "com.dayline.app.action.FOCUS_END"
        const val ACTION_FOCUS_COUNTDOWN = "com.dayline.app.action.FOCUS_COUNTDOWN"
        const val ACTION_WIDGET_REFRESH = "com.dayline.app.action.WIDGET_REFRESH"
        const val EXTRA_END = "end"
        const val EXTRA_ID = "id"
        const val EXTRA_SNOOZED = "snoozed"
    }
}

/** Buttons on notifications. */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var focus: FocusTimerController
    @Inject lateinit var time: TimeProvider
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        runAsync(scope) {
            when (intent.action) {
                ACTION_COMPLETE -> tasks.setCompleted(intent.getLongExtra(EXTRA_TASK_ID, -1L), true)
                ACTION_SNOOZE -> scheduler.snooze(intent.getLongExtra(EXTRA_REMINDER_ID, -1L))
                ACTION_HABIT_DONE -> {
                    val habitId = intent.getLongExtra(EXTRA_HABIT_ID, -1L)
                    habits.markDone(habitId, time.today())
                }
                ACTION_FOCUS_PAUSE -> focus.pause()
                ACTION_FOCUS_RESUME -> focus.resume()
                ACTION_FOCUS_STOP -> focus.stop()
            }
            if (notificationId >= 0) notifier.cancel(notificationId)
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.dayline.app.action.COMPLETE_TASK"
        const val ACTION_SNOOZE = "com.dayline.app.action.SNOOZE"
        const val ACTION_HABIT_DONE = "com.dayline.app.action.HABIT_DONE"
        const val ACTION_FOCUS_PAUSE = "com.dayline.app.action.FOCUS_PAUSE"
        const val ACTION_FOCUS_RESUME = "com.dayline.app.action.FOCUS_RESUME"
        const val ACTION_FOCUS_STOP = "com.dayline.app.action.FOCUS_STOP"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_HABIT_ID = "habit_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}

/** Alarms don't survive reboots or clock changes; re-plan everything when they happen. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderMaintenance.runNow(context)
    }
}

object ReminderMaintenance {
    /** Periodic safety net that re-plans alarms (e.g. after the OS dropped them). */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RescheduleWorker>(12, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("reminder-maintenance", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "reschedule-now", ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RescheduleWorker>().build(),
        )
    }
}

