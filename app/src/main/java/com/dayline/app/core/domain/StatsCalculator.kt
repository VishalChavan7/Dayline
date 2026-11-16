package com.dayline.app.core.domain

import com.dayline.app.core.model.FocusSession
import com.dayline.app.core.model.Habit
import com.dayline.app.core.model.HabitCompletion
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.Project
import com.dayline.app.core.model.Task
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

enum class StatsPeriod(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month") }

data class DayValue(val date: LocalDate, val value: Int)

data class ProjectProgress(
    val projectId: Long,
    val name: String,
    val color: Int,
    val completed: Int,
    val total: Int,
    val focusMinutes: Int,
) {
    val fraction: Float get() = if (total == 0) 0f else completed.toFloat() / total
}

data class StatsReport(
    val period: StatsPeriod,
    val start: LocalDate,
    val end: LocalDate,
    val completed: Int,
    val remaining: Int,
    /** Tasks due in the period that were missed (still open past their due date, or finished late). */
    val overdueInPeriod: Int,
    /** Open tasks overdue right now. */
    val overdueNow: Int,
    val completionRate: Float,
    val focusMinutes: Int,
    val focusSessions: Int,
    val averageSessionMinutes: Int,
    val habitCheckIns: Int,
    val habitConsistency: Float,
    val completedSeries: List<DayValue>,
    val focusSeries: List<DayValue>,
    val overdueSeries: List<DayValue>,
    val priorityDistribution: Map<Priority, Int>,
    val projectProgress: List<ProjectProgress>,
    val previousCompleted: Int,
    val previousFocusMinutes: Int,
    val heatmap: List<DayValue>,
) {
    val completedChange: Int? get() = percentChange(previousCompleted, completed)
    val focusChange: Int? get() = percentChange(previousFocusMinutes, focusMinutes)
    val hasAnyData: Boolean
        get() = completed + remaining + overdueNow + focusMinutes + habitCheckIns > 0 ||
            heatmap.any { it.value > 0 }

    private fun percentChange(prev: Int, now: Int): Int? = if (prev == 0) null else ((now - prev) * 100f / prev).toInt()
}

data class StatsInput(
    val tasks: List<Task>,
    val focusSessions: List<FocusSession>,
    val habits: List<Habit>,
    val habitCompletions: List<HabitCompletion>,
    val projects: List<Project>,
)

/** All analytics are derived from persisted data passed in [StatsInput]; nothing is estimated. */
object StatsCalculator {

    fun range(period: StatsPeriod, anchor: LocalDate, weekStart: DayOfWeek): Pair<LocalDate, LocalDate> = when (period) {
        StatsPeriod.DAY -> anchor to anchor
        StatsPeriod.WEEK -> DateRules.startOfWeek(anchor, weekStart) to DateRules.endOfWeek(anchor, weekStart)
        StatsPeriod.MONTH -> anchor.withDayOfMonth(1) to anchor.withDayOfMonth(anchor.lengthOfMonth())
    }

    private fun previousAnchor(period: StatsPeriod, anchor: LocalDate): LocalDate = when (period) {
        StatsPeriod.DAY -> anchor.minusDays(1)
        StatsPeriod.WEEK -> anchor.minusWeeks(1)
        StatsPeriod.MONTH -> anchor.minusMonths(1)
    }

    fun report(
        input: StatsInput,
        period: StatsPeriod,
        anchor: LocalDate,
        today: LocalDate,
        zone: ZoneId,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): StatsReport {
        val (start, end) = range(period, anchor, weekStart)
        fun Task.completedOn(): LocalDate? = completedAt?.atZone(zone)?.toLocalDate()
        fun FocusSession.day(): LocalDate = startedAt.atZone(zone).toLocalDate()
        fun inRange(d: LocalDate?, s: LocalDate = start, e: LocalDate = end) = d != null && !d.isBefore(s) && !d.isAfter(e)

        val completedTasks = input.tasks.filter { it.isCompleted && inRange(it.completedOn()) }
        val remainingTasks = input.tasks.filter { !it.isCompleted && inRange(it.dueDate) }
        val overdueInPeriod = input.tasks.count { t ->
            val due = t.dueDate
            due != null && inRange(due) && due.isBefore(today) &&
                (!t.isCompleted || (t.completedOn()?.isAfter(due) == true))
        }
        val overdueNow = input.tasks.count { it.isOverdue(today) }
        val denom = completedTasks.size + remainingTasks.size
        val rate = if (denom == 0) 0f else completedTasks.size.toFloat() / denom

        val sessions = input.focusSessions.filter { inRange(it.day()) }
        val focusMinutes = sessions.sumOf { it.focusedMillis } / 60_000L
        val finishedSessions = sessions.filter { it.completed }
        val avg = if (sessions.isEmpty()) 0 else (sessions.sumOf { it.focusedMillis } / sessions.size / 60_000L).toInt()

        val trendStart = if (period == StatsPeriod.DAY) anchor.minusDays(6) else start
        val trendDays = DateRules.daysBetweenInclusive(trendStart, end)
        val completedByDay = input.tasks.mapNotNull { if (it.isCompleted) it.completedOn() else null }.groupingBy { it }.eachCount()
        val focusByDay = input.focusSessions.groupBy { it.day() }.mapValues { (_, v) -> (v.sumOf { it.focusedMillis } / 60_000L).toInt() }

        val completedSeries = trendDays.map { DayValue(it, completedByDay[it] ?: 0) }
        val focusSeries = trendDays.map { DayValue(it, focusByDay[it] ?: 0) }
        val overdueSeries = trendDays.filter { !it.isAfter(today) }.map { day ->
            // Overdue at the start of `day`: due earlier and not yet completed before that day.
            DayValue(day, input.tasks.count { t ->
                val due = t.dueDate
                val doneOn = if (t.isCompleted) t.completedOn() else null
                due != null && due.isBefore(day) && (doneOn == null || !doneOn.isBefore(day))
            })
        }

        val activeHabits = input.habits.filter { !it.isArchived }
        val habitCompletionsInRange = input.habitCompletions.filter { c ->
            inRange(c.date) && activeHabits.firstOrNull { it.id == c.habitId }?.let { HabitStreakCalculator.isDone(it, c.count) } == true
        }
        val consistencies = activeHabits.filter { !it.startDate.isAfter(minOf(end, today)) }.map { h ->
            HabitStreakCalculator.consistency(h, input.habitCompletions.filter { it.habitId == h.id }, start, end, weekStart, today)
        }
        val habitConsistency = if (consistencies.isEmpty()) 0f else consistencies.average().toFloat()

        val inScope = input.tasks.filter { inRange(it.dueDate) || (it.isCompleted && inRange(it.completedOn())) }
        val distribution = Priority.entries.associateWith { p -> inScope.count { it.priority == p } }

        val focusByProject = input.focusSessions.filter { it.projectId != null }
            .groupBy { it.projectId!! }.mapValues { (_, v) -> (v.sumOf { it.focusedMillis } / 60_000L).toInt() }
        val projectProgress = input.projects.filter { !it.isArchived }.map { p ->
            val pts = input.tasks.filter { it.projectId == p.id }
            ProjectProgress(p.id, p.name, p.color, pts.count { it.isCompleted }, pts.size, focusByProject[p.id] ?: 0)
        }.sortedByDescending { it.total }

        val (pStart, pEnd) = range(period, previousAnchor(period, anchor), weekStart)
        val previousCompleted = input.tasks.count { it.isCompleted && inRange(it.completedOn(), pStart, pEnd) }
        val previousFocus = (input.focusSessions.filter { inRange(it.day(), pStart, pEnd) }.sumOf { it.focusedMillis } / 60_000L).toInt()

        val heatStart = DateRules.startOfWeek(minOf(end, today), weekStart).minusWeeks(17)
        val heatmap = DateRules.daysBetweenInclusive(heatStart, DateRules.endOfWeek(minOf(end, today), weekStart))
            .map { DayValue(it, if (it.isAfter(today)) -1 else completedByDay[it] ?: 0) }

        return StatsReport(
            period = period,
            start = start,
            end = end,
            completed = completedTasks.size,
            remaining = remainingTasks.size,
            overdueInPeriod = overdueInPeriod,
            overdueNow = overdueNow,
            completionRate = rate,
            focusMinutes = focusMinutes.toInt(),
            focusSessions = finishedSessions.size,
            averageSessionMinutes = avg,
            habitCheckIns = habitCompletionsInRange.size,
            habitConsistency = habitConsistency,
            completedSeries = completedSeries,
            focusSeries = focusSeries,
            overdueSeries = overdueSeries,
            priorityDistribution = distribution,
            projectProgress = projectProgress,
            previousCompleted = previousCompleted,
            previousFocusMinutes = previousFocus,
            heatmap = heatmap,
        )
    }
}
