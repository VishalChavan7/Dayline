package com.dayline.app.ui.common

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.dayline.app.core.model.DateFormatOption
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Date/time formatting that follows the user's Settings (date format, 12/24h, week start). */
@Immutable
data class Formats(
    val dateFormat: DateFormatOption = DateFormatOption.MEDIUM,
    val is24Hour: Boolean = false,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val locale: Locale = Locale.getDefault(),
) {
    private val dateFmt: DateTimeFormatter = DateTimeFormatter.ofPattern(dateFormat.pattern, locale)
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale)
    private val shortDayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d", locale)
    private val monthYearFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", locale)
    private val monthDayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", locale)

    fun date(d: LocalDate): String = d.format(dateFmt)
    fun time(t: LocalTime): String = t.format(timeFmt)
    fun monthYear(d: LocalDate): String = d.format(monthYearFmt)
    fun monthDay(d: LocalDate): String = d.format(monthDayFmt)
    fun weekdayShort(d: DayOfWeek): String = d.getDisplayName(TextStyle.SHORT, locale)
    fun weekdayNarrow(d: DayOfWeek): String = d.getDisplayName(TextStyle.NARROW, locale)
    fun weekdayFull(d: DayOfWeek): String = d.getDisplayName(TextStyle.FULL, locale)
    fun monthShort(month: java.time.Month): String = month.getDisplayName(TextStyle.SHORT, locale)

    /** "Today", "Tomorrow", "Yesterday", weekday within a week, otherwise a short date. */
    fun relative(d: LocalDate, today: LocalDate): String = when {
        d == today -> "Today"
        d == today.plusDays(1) -> "Tomorrow"
        d == today.minusDays(1) -> "Yesterday"
        d.isAfter(today) && d.isBefore(today.plusDays(7)) -> d.getDisplayName()
        d.year == today.year -> d.format(shortDayFmt)
        else -> date(d)
    }

    private fun LocalDate.getDisplayName(): String = dayOfWeek.getDisplayName(TextStyle.FULL, locale)

    fun dateTime(d: LocalDate, t: LocalTime?, today: LocalDate): String =
        relative(d, today) + (t?.let { " · ${time(it)}" } ?: "")

    fun minutes(total: Int): String = when {
        total < 60 -> "${total}m"
        total % 60 == 0 -> "${total / 60}h"
        else -> "${total / 60}h ${total % 60}m"
    }
}

val LocalFormats = staticCompositionLocalOf { Formats() }
