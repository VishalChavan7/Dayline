package com.dayline.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dayline.app.MainActivity
import com.dayline.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

object Channels {
    const val TASKS = "task_reminders"
    const val DEADLINES = "deadlines"
    const val HABITS = "habit_reminders"
    const val FOCUS_ONGOING = "focus_timer"
    const val FOCUS_ALERTS = "focus_alerts"
}

/** Builds and posts every notification the app shows. */
@Singleton
class Notifier @Inject constructor(@ApplicationContext private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channels = listOf(
            NotificationChannel(Channels.TASKS, "Task reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders you set on tasks"
            },
            NotificationChannel(Channels.DEADLINES, "Deadlines", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts before a task is due"
            },
            NotificationChannel(Channels.HABITS, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Daily nudges for your habits"
            },
            NotificationChannel(Channels.FOCUS_ONGOING, "Focus timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows the running focus timer"
                setShowBadge(false)
            },
            NotificationChannel(Channels.FOCUS_ALERTS, "Focus session alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "When a focus session or break ends"
            },
        )
        nm.createNotificationChannels(channels)
    }

    fun canPost(): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.areNotificationsEnabled()

    fun contentIntent(route: String? = null, requestCode: Int = 0): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            route?.let { putExtra(MainActivity.EXTRA_ROUTE, it) }
        }
        return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun action(action: String, requestCode: Int, extras: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            this.action = action
            extras()
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun showTaskReminder(reminderId: Long, taskId: Long?, title: String, text: String, isDeadline: Boolean) {
        if (!canPost()) return
        val notificationId = notificationIdForReminder(reminderId)
        val builder = NotificationCompat.Builder(context, if (isDeadline) Channels.DEADLINES else Channels.TASKS)
            .setSmallIcon(R.drawable.ic_stat_dayline)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(taskId?.let { "task/$it" }, notificationId))
        if (taskId != null) {
            builder.addAction(0, "Complete", action(NotificationActionReceiver.ACTION_COMPLETE, notificationId) {
                putExtra(NotificationActionReceiver.EXTRA_TASK_ID, taskId)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
            })
        }
        builder.addAction(0, "Snooze 10 min", action(NotificationActionReceiver.ACTION_SNOOZE, notificationId + 1) {
            putExtra(NotificationActionReceiver.EXTRA_REMINDER_ID, reminderId)
            putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        })
        post(notificationId, builder)
    }

    fun showHabitReminder(habitId: Long, name: String, text: String) {
        if (!canPost()) return
        val id = notificationIdForHabit(habitId)
        val builder = NotificationCompat.Builder(context, Channels.HABITS)
            .setSmallIcon(R.drawable.ic_stat_dayline)
            .setContentTitle(name)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(contentIntent("habits", id))
            .addAction(0, "Mark done", action(NotificationActionReceiver.ACTION_HABIT_DONE, id) {
                putExtra(NotificationActionReceiver.EXTRA_HABIT_ID, habitId)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, id)
            })
        post(id, builder)
    }

    fun showFocusOngoing(title: String, endAtMillis: Long?, running: Boolean) {
        if (!canPost()) return
        val builder = NotificationCompat.Builder(context, Channels.FOCUS_ONGOING)
            .setSmallIcon(R.drawable.ic_stat_dayline)
            .setContentTitle(title)
            .setContentText(if (running) "Stay with it — you've got this." else "Paused")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setContentIntent(contentIntent("focus", FOCUS_NOTIFICATION_ID))
        if (running && endAtMillis != null) {
            builder.setWhen(endAtMillis).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
            builder.addAction(0, "Pause", action(NotificationActionReceiver.ACTION_FOCUS_PAUSE, FOCUS_NOTIFICATION_ID) {})
        } else {
            builder.addAction(0, "Resume", action(NotificationActionReceiver.ACTION_FOCUS_RESUME, FOCUS_NOTIFICATION_ID + 1) {})
        }
        builder.addAction(0, "Stop", action(NotificationActionReceiver.ACTION_FOCUS_STOP, FOCUS_NOTIFICATION_ID + 2) {})
        post(FOCUS_NOTIFICATION_ID, builder)
    }

    fun cancelFocusOngoing() = manager.cancel(FOCUS_NOTIFICATION_ID)

    fun showFocusFinished(title: String, text: String) {
        if (!canPost()) return
        val builder = NotificationCompat.Builder(context, Channels.FOCUS_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_dayline)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent("focus", FOCUS_DONE_NOTIFICATION_ID))
        post(FOCUS_DONE_NOTIFICATION_ID, builder)
    }

    fun cancel(id: Int) = manager.cancel(id)

    private fun post(id: Int, builder: NotificationCompat.Builder) {
        try {
            manager.notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing else to do.
        }
    }

    companion object {
        const val FOCUS_NOTIFICATION_ID = 900_001
        const val FOCUS_DONE_NOTIFICATION_ID = 900_010
        fun notificationIdForReminder(reminderId: Long): Int = (reminderId % 400_000L).toInt() * 2 + 10
        fun notificationIdForHabit(habitId: Long): Int = 850_000 + (habitId % 40_000L).toInt()
    }
}
