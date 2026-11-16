package com.dayline.app.testing

import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.dayline.app.core.common.ReminderSync
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.db.DaylineDatabase
import com.dayline.app.core.data.prefs.SettingsRepository
import com.dayline.app.core.data.repository.FocusRepository
import com.dayline.app.core.data.repository.HabitRepository
import com.dayline.app.core.data.repository.NoteRepository
import com.dayline.app.core.data.repository.ProjectRepository
import com.dayline.app.core.data.repository.ReminderRepository
import com.dayline.app.core.data.repository.TagRepository
import com.dayline.app.core.data.repository.TaskRepository
import com.dayline.app.notifications.AlarmGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID

class FakeTimeProvider(var current: Instant = LocalDateTime.of(2026, 11, 12, 10, 0).toInstant(ZoneOffset.UTC)) : TimeProvider {
    override fun now(): Instant = current
    override fun zone(): ZoneId = ZoneOffset.UTC
    fun advanceMinutes(m: Long) { current = current.plusSeconds(m * 60) }
    fun set(dt: LocalDateTime) { current = dt.toInstant(ZoneOffset.UTC) }
}

/** Records what the data layer asked the scheduler to do. */
class FakeReminderSync : ReminderSync {
    val syncedTasks = mutableListOf<Long>()
    val cancelled = mutableListOf<Long>()
    val syncedHabits = mutableListOf<Long>()
    var syncAllCount = 0
    override suspend fun syncTask(taskId: Long) { syncedTasks += taskId }
    override fun cancelReminders(reminderIds: List<Long>) { cancelled += reminderIds }
    override suspend fun syncReminder(reminderId: Long) {}
    override suspend fun syncHabit(habitId: Long) { syncedHabits += habitId }
    override fun cancelHabit(habitId: Long) {}
    override suspend fun syncAll() { syncAllCount++ }
}

class FakeAlarmGateway(var exact: Boolean = true) : AlarmGateway {
    val alarms = mutableMapOf<Int, Long>()
    override fun set(requestCode: Int, triggerAtMillis: Long, intent: Intent) { alarms[requestCode] = triggerAtMillis }
    override fun cancel(requestCode: Int, intent: Intent) { alarms.remove(requestCode) }
    override fun canScheduleExact(): Boolean = exact
}

/** In-memory database + real repositories, wired the same way Hilt wires production. */
class TestEnv(scope: CoroutineScope) {
    val context: Context = ApplicationProvider.getApplicationContext()
    val db: DaylineDatabase = Room.inMemoryDatabaseBuilder(context, DaylineDatabase::class.java).allowMainThreadQueries().build()
    val time = FakeTimeProvider()
    val sync = FakeReminderSync()
    val settings = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { File(context.cacheDir, "test-${UUID.randomUUID()}.preferences_pb") }),
    )
    val tasks = TaskRepository(db, time, sync, settings)
    val projects = ProjectRepository(db, time, sync)
    val tags = TagRepository(db)
    val habits = HabitRepository(db, time, sync)
    val notes = NoteRepository(db, time)
    val reminders = ReminderRepository(db, sync)
    val focus = FocusRepository(db)

    fun close() = db.close()
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {
    override fun starting(description: Description) = Dispatchers.setMain(dispatcher)
    override fun finished(description: Description) = Dispatchers.resetMain()
}
