package com.dayline.app.core.domain

import com.dayline.app.core.model.Habit
import com.dayline.app.core.model.HabitCompletion
import com.dayline.app.core.model.HabitFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitStreakCalculatorTest {
    private val today = LocalDate.of(2026, 11, 12) // Thursday
    private val daily = Habit(id = 1, name = "Read", color = 0, startDate = today.minusDays(30))

    private fun done(vararg daysAgo: Int, habitId: Long = 1, count: Int = 1) =
        daysAgo.map { HabitCompletion(habitId, today.minusDays(it.toLong()), count) }

    @Test
    fun currentStreakCountsConsecutiveDays() {
        val s = HabitStreakCalculator.stats(daily, done(0, 1, 2, 4), today)
        assertEquals(3, s.currentStreak)
    }

    @Test
    fun todayNotDoneDoesNotBreakStreak() {
        val s = HabitStreakCalculator.stats(daily, done(1, 2, 3), today)
        assertEquals(3, s.currentStreak)
    }

    @Test
    fun missedYesterdayBreaksStreak() {
        val s = HabitStreakCalculator.stats(daily, done(0, 2, 3), today)
        assertEquals(1, s.currentStreak)
    }

    @Test
    fun longestStreakFindsBestRun() {
        val s = HabitStreakCalculator.stats(daily, done(0, 1, 5, 6, 7, 8, 10), today)
        assertEquals(4, s.longestStreak)
    }

    @Test
    fun targetPerDayMustBeReached() {
        val water = daily.copy(targetPerDay = 8)
        val partial = HabitStreakCalculator.stats(water, done(1, count = 5), today)
        assertEquals(0, partial.currentStreak)
        val full = HabitStreakCalculator.stats(water, done(1, count = 8), today)
        assertEquals(1, full.currentStreak)
    }

    @Test
    fun specificDaysIgnoreUnscheduledDays() {
        // Mon/Wed/Fri habit; today is Thursday. Done Wed, Mon, previous Fri.
        val mwf = daily.copy(frequency = HabitFrequency.SPECIFIC_DAYS, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val s = HabitStreakCalculator.stats(mwf, done(1, 3, 6), today)
        assertEquals(3, s.currentStreak)
        assertFalse(HabitStreakCalculator.isScheduled(mwf, today))
    }

    @Test
    fun weeklyTargetStreaksInWeeks() {
        val habit = daily.copy(frequency = HabitFrequency.WEEKLY_TARGET, timesPerWeek = 2)
        // This week (Mon 9 - Thu 12): 1 done (in progress). Last week: 2 done. Week before: 2 done.
        val c = listOf(
            HabitCompletion(1, LocalDate.of(2026, 11, 10), 1),
            HabitCompletion(1, LocalDate.of(2026, 11, 3), 1), HabitCompletion(1, LocalDate.of(2026, 11, 5), 1),
            HabitCompletion(1, LocalDate.of(2026, 10, 27), 1), HabitCompletion(1, LocalDate.of(2026, 10, 29), 1),
        )
        val s = HabitStreakCalculator.stats(habit, c, today)
        assertEquals("weeks", s.streakUnit)
        assertEquals(2, s.currentStreak)
        assertEquals(2, s.longestStreak)
    }

    @Test
    fun consistencyIsShareOfScheduledDays() {
        val habit = daily.copy(startDate = today.minusDays(9)) // 10 days incl. today
        val c = done(1, 2, 3, 4, 5) // 5 of the 9 past days; today not done -> excluded
        val value = HabitStreakCalculator.consistency(habit, c, today.minusDays(9), today)
        assertEquals(5f / 9f, value, 0.001f)
    }

    @Test
    fun dueOnHidesWeeklyHabitOnceTargetMet() {
        val habit = daily.copy(frequency = HabitFrequency.WEEKLY_TARGET, timesPerWeek = 1)
        val c = listOf(HabitCompletion(1, LocalDate.of(2026, 11, 10), 1))
        assertFalse(HabitStreakCalculator.dueOn(habit, today, c, DayOfWeek.MONDAY))
        assertTrue(HabitStreakCalculator.dueOn(habit, today, emptyList(), DayOfWeek.MONDAY))
    }

    @Test
    fun noCompletionsBeforeStartDateCount() {
        val habit = daily.copy(startDate = today)
        val s = HabitStreakCalculator.stats(habit, done(1, 2), today)
        assertEquals(0, s.currentStreak)
    }
}
