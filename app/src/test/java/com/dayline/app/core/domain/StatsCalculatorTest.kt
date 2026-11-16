package com.dayline.app.core.domain

import com.dayline.app.core.model.FocusSession
import com.dayline.app.core.model.Habit
import com.dayline.app.core.model.HabitCompletion
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.Project
import com.dayline.app.core.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset

class StatsCalculatorTest {
    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 11, 12) // Thursday
    private fun at(d: LocalDate, h: Int = 10) = d.atTime(h, 0).toInstant(zone)

    private fun input(
        tasks: List<Task> = emptyList(),
        focus: List<FocusSession> = emptyList(),
        habits: List<Habit> = emptyList(),
        completions: List<HabitCompletion> = emptyList(),
        projects: List<Project> = emptyList(),
    ) = StatsInput(tasks, focus, habits, completions, projects)

    @Test
    fun emptyDataProducesZeroesNotFakeNumbers() {
        val r = StatsCalculator.report(input(), StatsPeriod.WEEK, today, today, zone)
        assertEquals(0, r.completed)
        assertEquals(0f, r.completionRate, 0f)
        assertFalse(r.hasAnyData)
        assertNull(r.completedChange)
    }

    @Test
    fun dailyCountsCompletedRemainingAndOverdue() {
        val tasks = listOf(
            Task(id = 1, title = "done", dueDate = today, isCompleted = true, completedAt = at(today)),
            Task(id = 2, title = "open", dueDate = today),
            Task(id = 3, title = "late", dueDate = today.minusDays(2)),
        )
        val r = StatsCalculator.report(input(tasks), StatsPeriod.DAY, today, today, zone)
        assertEquals(1, r.completed)
        assertEquals(1, r.remaining)
        assertEquals(1, r.overdueNow)
        assertEquals(0.5f, r.completionRate, 0.001f)
        assertEquals(7, r.completedSeries.size) // daily view shows the last 7 days as a trend
    }

    @Test
    fun weekRangeUsesWeekStart() {
        val (s, e) = StatsCalculator.range(StatsPeriod.WEEK, today, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 8), s)
        assertEquals(LocalDate.of(2026, 11, 14), e)
    }

    @Test
    fun focusTotalsAndAverage() {
        val sessions = listOf(
            FocusSession(startedAt = at(today), endedAt = at(today, 11), focusedMillis = 25 * 60_000L, plannedMillis = 25 * 60_000L, completed = true, projectId = 9),
            FocusSession(startedAt = at(today.minusDays(1)), endedAt = at(today.minusDays(1), 11), focusedMillis = 15 * 60_000L, plannedMillis = 25 * 60_000L, completed = false),
        )
        val r = StatsCalculator.report(input(focus = sessions, projects = listOf(Project(id = 9, name = "P", color = 0))), StatsPeriod.WEEK, today, today, zone)
        assertEquals(40, r.focusMinutes)
        assertEquals(1, r.focusSessions) // only completed sessions count as sessions
        assertEquals(20, r.averageSessionMinutes)
        assertEquals(25, r.projectProgress.first().focusMinutes)
    }

    @Test
    fun comparesWithPreviousPeriod() {
        val tasks = listOf(
            Task(id = 1, title = "a", isCompleted = true, completedAt = at(today)),
            Task(id = 2, title = "b", isCompleted = true, completedAt = at(today)),
            Task(id = 3, title = "c", isCompleted = true, completedAt = at(today.minusWeeks(1))),
        )
        val r = StatsCalculator.report(input(tasks), StatsPeriod.WEEK, today, today, zone)
        assertEquals(2, r.completed)
        assertEquals(1, r.previousCompleted)
        assertEquals(100, r.completedChange)
    }

    @Test
    fun overdueInPeriodIncludesLateCompletions() {
        val mon = LocalDate.of(2026, 11, 9)
        val tasks = listOf(
            Task(id = 1, title = "late done", dueDate = mon, isCompleted = true, completedAt = at(mon.plusDays(1))),
            Task(id = 2, title = "on time", dueDate = mon, isCompleted = true, completedAt = at(mon)),
            Task(id = 3, title = "still open", dueDate = mon.plusDays(1)),
        )
        val r = StatsCalculator.report(input(tasks), StatsPeriod.WEEK, today, today, zone)
        assertEquals(2, r.overdueInPeriod)
    }

    @Test
    fun priorityDistributionAndHabitCheckIns() {
        val tasks = listOf(
            Task(id = 1, title = "u", priority = Priority.URGENT, dueDate = today),
            Task(id = 2, title = "u2", priority = Priority.URGENT, dueDate = today),
            Task(id = 3, title = "l", priority = Priority.LOW, dueDate = today),
        )
        val habit = Habit(id = 5, name = "h", color = 0, startDate = today.minusDays(10))
        val comps = listOf(HabitCompletion(5, today, 1), HabitCompletion(5, today.minusDays(1), 1))
        val r = StatsCalculator.report(input(tasks, habits = listOf(habit), completions = comps), StatsPeriod.WEEK, today, today, zone)
        assertEquals(2, r.priorityDistribution[Priority.URGENT])
        assertEquals(1, r.priorityDistribution[Priority.LOW])
        assertEquals(2, r.habitCheckIns)
        assertTrue(r.habitConsistency > 0f)
    }

    @Test
    fun heatmapMarksFutureDays() {
        val r = StatsCalculator.report(input(), StatsPeriod.MONTH, today, today, zone)
        assertTrue(r.heatmap.any { it.value == -1 })
        assertEquals(0, r.heatmap.size % 7)
    }
}
