package com.dayline.app.core.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Calendar helpers that respect the user's chosen first day of the week. */
object DateRules {
    fun startOfWeek(date: LocalDate, weekStart: DayOfWeek): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(weekStart))

    fun endOfWeek(date: LocalDate, weekStart: DayOfWeek): LocalDate = startOfWeek(date, weekStart).plusDays(6)

    fun weekDays(date: LocalDate, weekStart: DayOfWeek): List<LocalDate> {
        val start = startOfWeek(date, weekStart)
        return (0L..6L).map { start.plusDays(it) }
    }

    /** Ordered days of the week starting at [weekStart]. */
    fun orderedWeekdays(weekStart: DayOfWeek): List<DayOfWeek> = (0L..6L).map { weekStart.plus(it) }

    /** 6x7 grid of dates covering the month of [month], beginning at the week start. */
    fun monthGrid(month: LocalDate, weekStart: DayOfWeek): List<LocalDate> {
        val first = month.withDayOfMonth(1)
        val gridStart = startOfWeek(first, weekStart)
        return (0L until 42L).map { gridStart.plusDays(it) }
    }

    fun daysBetweenInclusive(start: LocalDate, end: LocalDate): List<LocalDate> {
        if (end.isBefore(start)) return emptyList()
        val n = ChronoUnit.DAYS.between(start, end)
        return (0L..n).map { start.plusDays(it) }
    }

    /** Next date strictly after [from] that falls on [day]. */
    fun nextWeekday(from: LocalDate, day: DayOfWeek): LocalDate = from.with(TemporalAdjusters.next(day))
}
