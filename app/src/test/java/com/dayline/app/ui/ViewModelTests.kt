package com.dayline.app.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.dayline.app.core.common.AppClock
import com.dayline.app.core.domain.FocusTimerState
import com.dayline.app.core.domain.TaskSort
import com.dayline.app.core.model.AppSettings
import com.dayline.app.core.model.DashboardSection
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.Task
import com.dayline.app.core.model.TaskDraft
import com.dayline.app.core.model.TaskItem
import com.dayline.app.testing.MainDispatcherRule
import com.dayline.app.testing.TestEnv
import com.dayline.app.ui.tasks.StatusFilter
import com.dayline.app.ui.tasks.TaskFilter
import com.dayline.app.ui.tasks.TaskListMode
import com.dayline.app.ui.tasks.TaskListViewModel
import com.dayline.app.ui.today.Sources
import com.dayline.app.ui.today.TodayViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(AndroidJUnit4::class)
class ViewModelTests {
    @get:Rule val main = MainDispatcherRule()
    private val today = LocalDate.of(2026, 11, 12)

    private fun item(id: Long, title: String, due: LocalDate? = null, done: Boolean = false, priority: Priority = Priority.MEDIUM, projectId: Long? = null) =
        TaskItem(Task(id = id, title = title, dueDate = due, isCompleted = done, priority = priority, projectId = projectId,
            completedAt = if (done) today.atTime(9, 0).toInstant(ZoneOffset.UTC) else null))

    @Test
    fun todayStateSplitsOverdueTodayAndDeadlines() {
        val items = listOf(
            item(1, "late", today.minusDays(2)),
            item(2, "today low", today, priority = Priority.LOW),
            item(3, "today urgent", today, priority = Priority.URGENT),
            item(4, "done today", today, done = true),
            item(5, "in 3 days", today.plusDays(3)),
            item(6, "next month", today.plusDays(30)),
        )
        val state = TodayViewModel.build(
            Sources(items, emptyList(), emptyList(), emptyList()),
            AppSettings(hiddenSections = setOf(DashboardSection.QUOTE)),
            emptyList(), emptyList(), today, FocusTimerState(), ZoneOffset.UTC, today.atTime(8, 0),
        )
        assertEquals(listOf(1L), state.overdue.map { it.id })
        assertEquals(listOf(3L, 2L), state.todayTasks.map { it.id }) // urgent first
        assertEquals(listOf(4L), state.completedToday.map { it.id })
        assertEquals(listOf(5L), state.deadlines.map { it.id })
        assertEquals(1, state.doneCount)
        assertEquals(4, state.plannedCount)
        assertEquals(0.25f, state.progress, 0.001f)
        assertTrue(DashboardSection.QUOTE !in state.sections)
        assertEquals("Good morning", state.greeting)
    }

    @Test
    fun inboxShowsOnlyUnassignedUndatedOpenTasks() {
        val items = listOf(item(1, "idea"), item(2, "dated", today), item(3, "in project", projectId = 9), item(4, "done", done = true))
        val sections = TaskListViewModel.buildSections(items, TaskListMode.INBOX, TaskFilter(), TaskSort.MANUAL, today, DayOfWeek.MONDAY, ZoneOffset.UTC)
        assertEquals(listOf(1L), sections.flatMap { it.items }.map { it.id })
    }

    @Test
    fun allTasksFiltersByPriorityAndSearch() {
        val items = listOf(item(1, "Email Sam", priority = Priority.HIGH), item(2, "Email Jo", priority = Priority.LOW), item(3, "Gym", priority = Priority.HIGH))
        val f = TaskFilter(status = StatusFilter.OPEN, priorities = setOf(Priority.HIGH), query = "email")
        val ids = TaskListViewModel.buildSections(items, TaskListMode.ALL, f, TaskSort.TITLE, today, DayOfWeek.MONDAY, ZoneOffset.UTC).flatMap { it.items }.map { it.id }
        assertEquals(listOf(1L), ids)
    }

    @Test
    fun taskListViewModelReactsToNewTasks() = runTest {
        val env = TestEnv(backgroundScope)
        val vm = TaskListViewModel(env.tasks, env.projects, env.tags, env.settings, env.time, AppClock(env.time, backgroundScope))
        vm.setMode(TaskListMode.INBOX)
        vm.state.test {
            var s = awaitItem()
            while (s.loading) s = awaitItem()
            assertTrue(s.isEmpty)
            env.tasks.save(TaskDraft(Task(title = "Capture idea")))
            s = awaitItem()
            while (s.isEmpty) s = awaitItem()
            assertEquals("Capture idea", s.sections.single().items.single().task.title)
            cancelAndIgnoreRemainingEvents()
        }
        env.close()
    }
}
