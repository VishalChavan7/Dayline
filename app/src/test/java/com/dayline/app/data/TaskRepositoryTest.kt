package com.dayline.app.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.Project
import com.dayline.app.core.model.RecurrenceFrequency
import com.dayline.app.core.model.RecurrenceRule
import com.dayline.app.core.model.Reminder
import com.dayline.app.core.model.ReminderType
import com.dayline.app.core.model.Subtask
import com.dayline.app.core.model.Task
import com.dayline.app.core.model.TaskDraft
import com.dayline.app.testing.TestEnv
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class TaskRepositoryTest {
    private val today = LocalDate.of(2026, 11, 12)

    private fun draft(title: String = "Write report", due: LocalDate? = today) = TaskDraft(
        task = Task(title = title, dueDate = due, priority = Priority.HIGH),
        subtasks = listOf(Subtask(taskId = 0, title = "Outline"), Subtask(taskId = 0, title = "Draft")),
        tagNames = listOf("work", "Q4"),
        reminders = listOf(Reminder(type = ReminderType.DEADLINE, offsetMinutes = 60)),
    )

    @Test
    fun saveStoresTaskWithChildrenAndSchedulesReminders() = runTest {
        val env = TestEnv(backgroundScope)
        val id = env.tasks.save(draft())
        val item = env.tasks.items.first().single()
        assertEquals("Write report", item.task.title)
        assertEquals(2, item.subtasksTotal)
        assertEquals(listOf("Q4", "work"), item.tags.map { it.name })
        assertEquals(1, item.reminderCount)
        assertTrue(id in env.sync.syncedTasks)
        env.close()
    }

    @Test
    fun blankTitleIsRejected() = runTest {
        val env = TestEnv(backgroundScope)
        val result = runCatching { env.tasks.save(draft(title = "   ")) }
        assertTrue(result.isFailure)
        assertTrue(env.tasks.items.first().isEmpty())
        env.close()
    }

    @Test
    fun editingKeepsSubtaskIdsAndRemovesDeletedOnes() = runTest {
        val env = TestEnv(backgroundScope)
        val id = env.tasks.save(draft())
        val details = env.tasks.getDetails(id)!!
        val kept = details.subtasks.first()
        env.tasks.save(TaskDraft(details.task.copy(title = "Renamed"), subtasks = listOf(kept.copy(isDone = true)), tagNames = listOf("work")))
        val after = env.tasks.getDetails(id)!!
        assertEquals("Renamed", after.task.title)
        assertEquals(listOf(kept.id), after.subtasks.map { it.id })
        assertTrue(after.subtasks.single().isDone)
        assertEquals(listOf("work"), after.tags.map { it.name })
        assertTrue(after.reminders.isEmpty())
        env.close()
    }

    @Test
    fun completingRecurringTaskCreatesNextOccurrence() = runTest {
        val env = TestEnv(backgroundScope)
        val id = env.tasks.save(draft().copy(task = draft().task.copy(recurrence = RecurrenceRule(RecurrenceFrequency.DAILY))))
        env.tasks.setSubtaskDone(env.tasks.getDetails(id)!!.subtasks.first().id, true)

        val result = env.tasks.setCompleted(id, true)

        val nextId = assertNotNullAndGet(result.nextOccurrenceId)
        val done = env.tasks.getDetails(id)!!
        val next = env.tasks.getDetails(nextId)!!
        assertTrue(done.task.isCompleted)
        assertNull("Completed instance stops carrying the rule", done.task.recurrence)
        assertFalse(next.task.isCompleted)
        assertEquals(today.plusDays(1), next.task.dueDate)
        assertEquals(RecurrenceFrequency.DAILY, next.task.recurrence?.frequency)
        assertTrue("Subtasks reset on the new instance", next.subtasks.none { it.isDone })
        assertEquals(2, next.subtasks.size)
        assertEquals(done.tags.map { it.name }.toSet(), next.tags.map { it.name }.toSet())
        assertEquals(1, next.reminders.size)
        env.close()
    }

    @Test
    fun reopeningClearsCompletion() = runTest {
        val env = TestEnv(backgroundScope)
        val id = env.tasks.save(draft())
        env.tasks.setCompleted(id, true)
        env.tasks.setCompleted(id, false)
        val t = env.tasks.getDetails(id)!!.task
        assertFalse(t.isCompleted)
        assertNull(t.completedAt)
        env.close()
    }

    @Test
    fun deleteAndUndoRestoresEverything() = runTest {
        val env = TestEnv(backgroundScope)
        val id = env.tasks.save(draft())
        val deleted = env.tasks.delete(id)!!
        assertTrue(env.tasks.items.first().isEmpty())
        assertTrue(env.sync.cancelled.isNotEmpty())
        env.tasks.restore(deleted)
        val restored = env.tasks.getDetails(id)!!
        assertEquals(2, restored.subtasks.size)
        assertEquals(2, restored.tags.size)
        assertEquals(1, restored.reminders.size)
        env.close()
    }

    @Test
    fun duplicateCopiesContentButNotCompletion() = runTest {
        val env = TestEnv(backgroundScope)
        val id = env.tasks.save(draft())
        env.tasks.setCompleted(id, true)
        val copyId = env.tasks.duplicate(id)!!
        val copy = env.tasks.getDetails(copyId)!!
        assertEquals("Write report (copy)", copy.task.title)
        assertFalse(copy.task.isCompleted)
        assertEquals(2, copy.subtasks.size)
        env.close()
    }

    @Test
    fun blockedTaskCannotBeCompletedOrDeletedUntilItsDependencyIsDone() = runTest {
        val env = TestEnv(backgroundScope)
        val a = env.tasks.save(TaskDraft(Task(title = "Book venue")))
        val b = env.tasks.save(TaskDraft(Task(title = "Send invites"), dependsOnIds = listOf(a)))
        // Blocked: completing and deleting are refused, with a message for the user.
        assertTrue(env.tasks.setCompleted(b, true).blocked)
        assertFalse(env.tasks.getDetails(b)!!.task.isCompleted)
        assertEquals(null, env.tasks.delete(b))
        assertTrue(env.tasks.getDetails(b) != null)
        // Once the dependency is done, both work.
        env.tasks.setCompleted(a, true)
        assertFalse(env.tasks.setCompleted(b, true).blocked)
        assertTrue(env.tasks.getDetails(b)!!.task.isCompleted)
        env.tasks.setCompleted(b, false)
        assertTrue(env.tasks.delete(b) != null)
        env.close()
    }

    @Test
    fun dependenciesBlockAndCyclesAreRejected() = runTest {
        val env = TestEnv(backgroundScope)
        val a = env.tasks.save(TaskDraft(Task(title = "A")))
        val b = env.tasks.save(TaskDraft(Task(title = "B"), dependsOnIds = listOf(a)))
        assertEquals(listOf(a), env.tasks.items.first().first { it.id == b }.blockedBy.map { it.id })
        // A depending on B would create a loop and is ignored.
        env.tasks.save(TaskDraft(env.tasks.getDetails(a)!!.task, dependsOnIds = listOf(b)))
        assertTrue(env.tasks.getDetails(a)!!.dependsOn.isEmpty())
        // Completing A unblocks B.
        env.tasks.setCompleted(a, true)
        assertFalse(env.tasks.items.first().first { it.id == b }.isBlocked)
        env.close()
    }

    @Test
    fun rescheduleKeepsSpanAndReorderPersists() = runTest {
        val env = TestEnv(backgroundScope)
        val id = env.tasks.save(TaskDraft(Task(title = "Trip", startDate = today, dueDate = today.plusDays(2))))
        env.tasks.reschedule(id, today.plusDays(10))
        val t = env.tasks.getDetails(id)!!.task
        assertEquals(today.plusDays(8), t.startDate)
        assertEquals(today.plusDays(10), t.dueDate)

        val x = env.tasks.save(TaskDraft(Task(title = "X")))
        val y = env.tasks.save(TaskDraft(Task(title = "Y")))
        env.tasks.reorder(listOf(y, x, id))
        assertEquals(listOf(y, x, id), env.tasks.items.first().sortedBy { it.task.sortOrder }.map { it.id })
        env.close()
    }

    @Test
    fun quickAddAppliesDefaultReminderOnlyForDatedTasks() = runTest {
        val env = TestEnv(backgroundScope)
        val dated = env.tasks.quickAdd("Pay rent", today, Priority.URGENT, null, null)
        val undated = env.tasks.quickAdd("Someday idea", null, Priority.LOW, null, null)
        assertEquals(1, env.tasks.getDetails(dated)!!.reminders.size)
        assertEquals(0, env.tasks.getDetails(undated)!!.reminders.size)
        env.close()
    }

    @Test
    fun deletingProjectCanMoveTasksToInbox() = runTest {
        val env = TestEnv(backgroundScope)
        val p = env.projects.saveProject(Project(name = "Launch", color = 0))
        val t = env.tasks.save(TaskDraft(Task(title = "Ship it", projectId = p)))
        env.projects.deleteProject(p, deleteTasks = false)
        val task = env.tasks.getDetails(t)!!.task
        assertNull(task.projectId)

        val p2 = env.projects.saveProject(Project(name = "Old", color = 0))
        val t2 = env.tasks.save(TaskDraft(Task(title = "Gone", projectId = p2)))
        env.projects.deleteProject(p2, deleteTasks = true)
        assertNull(env.tasks.getDetails(t2))
        env.close()
    }

    private fun assertNotNullAndGet(v: Long?): Long { assertNotNull(v); return v!! }
}
