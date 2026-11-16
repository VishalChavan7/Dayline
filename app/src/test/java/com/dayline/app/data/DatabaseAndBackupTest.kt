package com.dayline.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dayline.app.core.data.backup.BackupFormatException
import com.dayline.app.core.data.backup.BackupManager
import com.dayline.app.core.data.db.Converters
import com.dayline.app.core.model.Habit
import com.dayline.app.core.model.HabitFrequency
import com.dayline.app.core.model.Note
import com.dayline.app.core.model.Project
import com.dayline.app.core.model.Reminder
import com.dayline.app.core.model.ReminderType
import com.dayline.app.core.model.Subtask
import com.dayline.app.core.model.Task
import com.dayline.app.core.model.TaskDraft
import com.dayline.app.core.model.TaskList
import com.dayline.app.testing.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@RunWith(AndroidJUnit4::class)
class DatabaseAndBackupTest {
    private val today = LocalDate.of(2026, 11, 12)

    @Test
    fun convertersRoundTrip() {
        val c = Converters()
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY, DayOfWeek.SUNDAY)
        assertEquals(days, c.intToWeekdays(c.weekdaysToInt(days)))
        assertEquals(today, c.longToLocalDate(c.localDateToLong(today)))
        assertEquals(LocalTime.of(17, 45), c.intToLocalTime(c.localTimeToInt(LocalTime.of(17, 45))))
        val dt = LocalDateTime.of(2026, 1, 2, 3, 4)
        assertEquals(dt, c.stringToLocalDateTime(c.localDateTimeToString(dt)))
    }

    @Test
    fun deletingTaskCascadesToChildren() = runTest {
        val env = TestEnv(backgroundScope)
        val id = env.tasks.save(
            TaskDraft(
                Task(title = "Parent", dueDate = today),
                subtasks = listOf(Subtask(taskId = 0, title = "child")),
                tagNames = listOf("x"),
                reminders = listOf(Reminder(type = ReminderType.DEADLINE)),
            ),
        )
        env.tasks.delete(id)
        assertTrue(env.db.taskDao().getAllSubtasks().isEmpty())
        assertTrue(env.db.reminderDao().getAll().isEmpty())
        assertTrue(env.db.tagDao().getCrossRefs().isEmpty())
        assertEquals("Tags themselves are kept", 1, env.db.tagDao().getAll().size)
        env.close()
    }

    @Test
    fun deletingTagOrListNeverDeletesTasks() = runTest {
        val env = TestEnv(backgroundScope)
        val listId = env.projects.saveList(TaskList(name = "Errands", color = 0))
        val id = env.tasks.save(TaskDraft(Task(title = "Buy milk", listId = listId), tagNames = listOf("home")))
        env.tags.delete(env.db.tagDao().getAll().single().id)
        env.projects.deleteList(listId)
        val t = env.tasks.getDetails(id)!!
        assertEquals(null, t.task.listId)
        assertTrue(t.tags.isEmpty())
        env.close()
    }

    @Test
    fun deletingNoteLinkedTaskKeepsNote() = runTest {
        val env = TestEnv(backgroundScope)
        val id = env.tasks.save(TaskDraft(Task(title = "Research")))
        val noteId = env.notes.save(Note(title = "Sources", taskId = id))
        env.tasks.delete(id)
        assertEquals(null, env.notes.get(noteId)!!.taskId)
        env.close()
    }

    @Test
    fun backupRoundTripRestoresAllData() = runTest {
        val env = TestEnv(backgroundScope)
        val p = env.projects.saveProject(Project(name = "Home", color = 0xFF112233.toInt(), deadline = today.plusDays(30)))
        val l = env.projects.saveList(TaskList(name = "Garden", color = 0, projectId = p))
        val a = env.tasks.save(TaskDraft(Task(title = "Plant tulips", dueDate = today, projectId = p, listId = l), subtasks = listOf(Subtask(taskId = 0, title = "Buy bulbs")), tagNames = listOf("garden")))
        env.tasks.save(TaskDraft(Task(title = "Water lawn"), dependsOnIds = listOf(a)))
        val h = env.habits.save(Habit(name = "Stretch", color = 0, startDate = today.minusDays(3), frequency = HabitFrequency.SPECIFIC_DAYS, weekdays = setOf(DayOfWeek.MONDAY)))
        env.habits.checkIn(h, today, 1)
        env.notes.save(Note(title = "Ideas", body = "Raised beds", isPinned = true, projectId = p))
        env.settings.setFocusConfig(env.settings.current().focus.copy(focusMinutes = 50))

        val backup = BackupManager(env.context, env.db, env.settings, env.sync, env.time)
        val json = backup.exportJson()
        assertEquals(2, backup.inspect(json).tasks)

        // Wipe and restore.
        env.tasks.items.first().forEach { env.tasks.delete(it.id) }
        env.projects.deleteProject(p, deleteTasks = true)
        env.settings.setFocusConfig(env.settings.current().focus.copy(focusMinutes = 25))
        val summary = backup.importJson(json)

        assertEquals(2, summary.tasks)
        val items = env.tasks.items.first()
        val plant = items.first { it.task.title == "Plant tulips" }
        assertEquals("Home", plant.project?.name)
        assertEquals("Garden", plant.list?.name)
        assertEquals(1, plant.subtasksTotal)
        assertEquals(listOf("garden"), plant.tags.map { it.name })
        assertEquals(listOf(plant.id), items.first { it.task.title == "Water lawn" }.blockedBy.map { it.id })
        assertEquals(1, env.habits.completions.first().size)
        assertEquals(setOf(DayOfWeek.MONDAY), env.habits.habits.first().single().weekdays)
        assertTrue(env.notes.notes.first().single().isPinned)
        assertEquals(50, env.settings.current().focus.focusMinutes)
        assertTrue("Alarms are rebuilt after a restore", env.sync.syncAllCount > 0)
        env.close()
    }

    @Test
    fun invalidBackupIsRejectedWithoutTouchingData() = runTest {
        val env = TestEnv(backgroundScope)
        env.tasks.save(TaskDraft(Task(title = "Keep me")))
        val backup = BackupManager(env.context, env.db, env.settings, env.sync, env.time)
        val bad = runCatching { backup.importJson("{\"app\":\"other\"}") }
        assertTrue(bad.exceptionOrNull() is BackupFormatException)
        val notJson = runCatching { backup.importJson("hello") }
        assertTrue(notJson.exceptionOrNull() is BackupFormatException)
        assertEquals(1, env.tasks.items.first().size)
        env.close()
    }
}
