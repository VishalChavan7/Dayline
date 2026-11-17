package com.dayline.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.dayline.app.core.common.ReminderSync
import com.dayline.app.di.ApplicationScope
import com.dayline.app.focus.FocusTimerController
import com.dayline.app.notifications.Notifier
import com.dayline.app.notifications.ReminderMaintenance
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import com.dayline.app.sync.SyncScheduler
import com.dayline.app.core.common.AppClock
import com.dayline.app.core.data.repository.HabitRepository
import com.dayline.app.core.data.repository.TaskRepository
import com.dayline.app.widget.WidgetUpdater
import javax.inject.Inject

@HiltAndroidApp
@OptIn(kotlinx.coroutines.FlowPreview::class)
class DaylineApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var reminderSync: ReminderSync
    @Inject lateinit var focusTimer: FocusTimerController
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var tasks: TaskRepository
    @Inject lateinit var habits: HabitRepository
    @Inject lateinit var clock: AppClock
    @Inject lateinit var notes: com.dayline.app.core.data.repository.NoteRepository
    @Inject lateinit var projects: com.dayline.app.core.data.repository.ProjectRepository
    @Inject lateinit var settingsRepo: com.dayline.app.core.data.prefs.SettingsRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        notifier.createChannels()
        ReminderMaintenance.schedulePeriodic(this)
        appScope.launch {
            // Re-plan alarms on every cold start (cheap; covers app updates and OS-cleared alarms).
            runCatching { reminderSync.syncAll() }
            focusTimer.restore()
        }
        // Google sync: pull other devices' changes at launch, then push ours shortly after each change.
        appScope.launch {
            if (settingsRepo.sync.first().enabled) {
                SyncScheduler.schedulePeriodic(this@DaylineApp)
                SyncScheduler.soon(this@DaylineApp, delaySeconds = 3)
            }
            combine(tasks.items, habits.completions, habits.habits, notes.notes, combine(projects.projects, projects.lists, settingsRepo.settings) { a, b, c -> Triple(a, b, c) }) { _, _, _, _, _ -> Unit }
                .drop(1)
                .debounce(4_000)
                .collect {
                    if (settingsRepo.sync.first().enabled) {
                        SyncScheduler.schedulePeriodic(this@DaylineApp)
                        SyncScheduler.soon(this@DaylineApp, delaySeconds = 10)
                    }
                }
        }
        // New versions from GitHub Releases: checked twice a day, downloaded in the background.
        if (!BuildConfig.DEBUG) com.dayline.app.update.UpdateScheduler.schedulePeriodic(this)
        // Widget-picker previews for Android 15+ (once per app version).
        appScope.launch { runCatching { com.dayline.app.widget.WidgetPreviews.publish(this@DaylineApp) } }
        // Keep home-screen widgets in step with the data (debounced so bursts of edits update once).
        appScope.launch {
            combine(tasks.items, habits.completions, habits.habits, focusTimer.state, clock.today) { _, _, _, _, _ -> Unit }
                .debounce(400)
                .collect { runCatching { WidgetUpdater.updateAll(this@DaylineApp) } }
        }
    }
}
