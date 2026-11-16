package com.dayline.app.core.domain

import com.dayline.app.core.model.Habit
import com.dayline.app.core.model.HabitCompletion
import com.dayline.app.core.model.HabitFrequency
import java.time.DayOfWeek
import java.time.LocalDate

data class HabitStats(
    val currentStreak: Int,
    val longestStreak: Int,
    /** "days" for daily/specific-day habits, "weeks" for times-per-week habits. */
    val streakUnit: String,
    /** 0f..1f over the evaluated window. */
    val consistency: Float,
    val totalCompletions: Int,
)

/**
 * Streak and consistency rules:
 * - A day counts as done when its check-in count reaches the habit's daily target.
 * - Only scheduled days matter; an unscheduled day never breaks a streak.
 * - Today not being done yet never breaks the current streak (the day isn't over).
 * - Times-per-week habits streak in weeks: a week counts when it reaches the weekly target.
 */
object HabitStreakCalculator {

    fun isScheduled(habit: Habit, date: LocalDate): Boolean {
        if (date.isBefore(habit.startDate)) return false
        return when (habit.frequency) {
            HabitFrequency.DAILY, HabitFrequency.WEEKLY_TARGET -> true
            HabitFrequency.SPECIFIC_DAYS -> date.dayOfWeek in habit.weekdays
        }
    }

    fun isDone(habit: Habit, count: Int): Boolean = count >= habit.targetPerDay.coerceAtLeast(1)

    private fun counts(completions: List<HabitCompletion>): Map<LocalDate, Int> =
        completions.groupBy { it.date }.mapValues { (_, v) -> v.sumOf { it.count } }

    fun stats(
        habit: Habit,
        completions: List<HabitCompletion>,
        today: LocalDate,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
        consistencyWindowDays: Int = 30,
    ): HabitStats {
        val byDate = counts(completions)
        val total = byDate.count { (date, c) -> !date.isAfter(today) && isDone(habit, c) }
        return if (habit.frequency == HabitFrequency.WEEKLY_TARGET) {
            HabitStats(
                currentStreak = currentWeeklyStreak(habit, byDate, today, weekStart),
                longestStreak = longestWeeklyStreak(habit, byDate, today, weekStart),
                streakUnit = "weeks",
                consistency = consistency(habit, completions, today.minusDays(consistencyWindowDays - 1L), today, weekStart),
                totalCompletions = total,
            )
        } else {
            HabitStats(
                currentStreak = currentDailyStreak(habit, byDate, today),
                longestStreak = longestDailyStreak(habit, byDate, today),
                streakUnit = "days",
                consistency = consistency(habit, completions, today.minusDays(consistencyWindowDays - 1L), today, weekStart),
                totalCompletions = total,
            )
        }
    }

    private fun currentDailyStreak(habit: Habit, byDate: Map<LocalDate, Int>, today: LocalDate): Int {
        var streak = 0
        var d = today
        var first = true
        while (!d.isBefore(habit.startDate)) {
            if (isScheduled(habit, d)) {
                val done = isDone(habit, byDate[d] ?: 0)
                if (done) {
                    streak++
                } else if (!first) {
                    break
                }
            }
            first = false
            d = d.minusDays(1)
        }
        return streak
    }

    private fun longestDailyStreak(habit: Habit, byDate: Map<LocalDate, Int>, today: LocalDate): Int {
        var best = 0
        var run = 0
        var d = habit.startDate
        while (!d.isAfter(today)) {
            if (isScheduled(habit, d)) {
                if (isDone(habit, byDate[d] ?: 0)) {
                    run++
                    if (run > best) best = run
                } else if (d != today) {
                    run = 0
                }
            }
            d = d.plusDays(1)
        }
        return best
    }

    private fun doneDaysInWeek(habit: Habit, byDate: Map<LocalDate, Int>, weekStartDate: LocalDate, until: LocalDate): Int =
        (0L..6L).map { weekStartDate.plusDays(it) }
            .count { !it.isAfter(until) && !it.isBefore(habit.startDate) && isDone(habit, byDate[it] ?: 0) }

    private fun weekTarget(habit: Habit) = habit.timesPerWeek.coerceIn(1, 7)

    private fun currentWeeklyStreak(habit: Habit, byDate: Map<LocalDate, Int>, today: LocalDate, weekStart: DayOfWeek): Int {
        val firstWeek = DateRules.startOfWeek(habit.startDate, weekStart)
        var week = DateRules.startOfWeek(today, weekStart)
        var streak = 0
        var isCurrent = true
        while (!week.isBefore(firstWeek)) {
            val met = doneDaysInWeek(habit, byDate, week, today) >= weekTarget(habit)
            if (met) streak++ else if (!isCurrent) break
            isCurrent = false
            week = week.minusWeeks(1)
        }
        return streak
    }

    private fun longestWeeklyStreak(habit: Habit, byDate: Map<LocalDate, Int>, today: LocalDate, weekStart: DayOfWeek): Int {
        val currentWeek = DateRules.startOfWeek(today, weekStart)
        var week = DateRules.startOfWeek(habit.startDate, weekStart)
        var best = 0
        var run = 0
        while (!week.isAfter(currentWeek)) {
            val met = doneDaysInWeek(habit, byDate, week, today) >= weekTarget(habit)
            if (met) {
                run++
                if (run > best) best = run
            } else if (week != currentWeek) {
                run = 0
            }
            week = week.plusWeeks(1)
        }
        return best
    }

    /**
     * Share of scheduled days (or weeks) in [from]..[to] that were completed. Today only counts toward the
     * denominator once it's done, and days before the habit started are ignored.
     */
    fun consistency(
        habit: Habit,
        completions: List<HabitCompletion>,
        from: LocalDate,
        to: LocalDate,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
        today: LocalDate = to,
    ): Float {
        val byDate = counts(completions)
        val start = maxOf(from, habit.startDate)
        val end = minOf(to, today)
        if (end.isBefore(start)) return 0f
        if (habit.frequency == HabitFrequency.WEEKLY_TARGET) {
            var week = DateRules.startOfWeek(start, weekStart)
            val lastWeek = DateRules.startOfWeek(end, weekStart)
            var met = 0
            var counted = 0
            while (!week.isAfter(lastWeek)) {
                val ok = doneDaysInWeek(habit, byDate, week, end) >= weekTarget(habit)
                val inProgress = week == DateRules.startOfWeek(today, weekStart)
                if (ok) { met++; counted++ } else if (!inProgress) counted++
                week = week.plusWeeks(1)
            }
            return if (counted == 0) 0f else met.toFloat() / counted
        }
        var scheduled = 0
        var done = 0
        var d = start
        while (!d.isAfter(end)) {
            if (isScheduled(habit, d)) {
                val isDoneDay = isDone(habit, byDate[d] ?: 0)
                if (isDoneDay) { done++; scheduled++ } else if (d != today) scheduled++
            }
            d = d.plusDays(1)
        }
        return if (scheduled == 0) 0f else done.toFloat() / scheduled
    }

    /** Habits that should appear on a given day's checklist. Weekly-target habits show every day until met. */
    fun dueOn(habit: Habit, date: LocalDate, completions: List<HabitCompletion>, weekStart: DayOfWeek): Boolean {
        if (habit.isArchived || !isScheduled(habit, date)) return false
        if (habit.frequency != HabitFrequency.WEEKLY_TARGET) return true
        val byDate = counts(completions)
        val weekStartDate = DateRules.startOfWeek(date, weekStart)
        val doneBeforeToday = (0L..6L).map { weekStartDate.plusDays(it) }
            .count { it.isBefore(date) && isDone(habit, byDate[it] ?: 0) }
        return doneBeforeToday < weekTarget(habit) || isDone(habit, byDate[date] ?: 0)
    }
}
