package com.dayline.app.core.domain

import com.dayline.app.core.model.RecurrenceFrequency
import com.dayline.app.core.model.RecurrenceRule
import com.dayline.app.core.model.Task
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Result of rolling a recurring task forward: the new start/due dates. */
data class NextOccurrence(val startDate: LocalDate?, val dueDate: LocalDate?)

object RecurrenceEngine {

    /**
     * One step of the rule from [from]. For weekly rules with weekdays, [anchor] defines which weeks are
     * "on" when the interval is larger than one.
     */
    fun step(rule: RecurrenceRule, from: LocalDate, anchor: LocalDate = from, weekStart: DayOfWeek = DayOfWeek.MONDAY): LocalDate =
        when (rule.frequency) {
            RecurrenceFrequency.DAILY -> from.plusDays(rule.interval.toLong())
            RecurrenceFrequency.MONTHLY -> from.plusMonths(rule.interval.toLong())
            RecurrenceFrequency.YEARLY -> from.plusYears(rule.interval.toLong())
            RecurrenceFrequency.WEEKLY ->
                if (rule.weekdays.isEmpty()) {
                    from.plusWeeks(rule.interval.toLong())
                } else {
                    nextMatchingWeekday(rule, from, anchor, weekStart)
                }
        }

    private fun nextMatchingWeekday(rule: RecurrenceRule, from: LocalDate, anchor: LocalDate, weekStart: DayOfWeek): LocalDate {
        val anchorWeek = DateRules.startOfWeek(anchor, weekStart)
        var candidate = from.plusDays(1)
        // A matching day always exists within (interval + 1) weeks.
        repeat(7 * (rule.interval + 1) + 7) {
            val weeks = ChronoUnit.WEEKS.between(anchorWeek, DateRules.startOfWeek(candidate, weekStart))
            if (candidate.dayOfWeek in rule.weekdays && weeks % rule.interval == 0L) return candidate
            candidate = candidate.plusDays(1)
        }
        return from.plusWeeks(rule.interval.toLong())
    }

    /**
     * Computes the dates of the next instance of a recurring [task] completed on [completedOn].
     * Returns null when the task does not repeat or the series has ended.
     *
     * - Fixed schedules skip occurrences that are already in the past, so completing a long-overdue
     *   daily task doesn't create a pile of overdue copies.
     * - "Repeat after completion" measures from [completedOn].
     * - The span between start and due date is preserved.
     */
    fun nextOccurrence(task: Task, completedOn: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): NextOccurrence? {
        val rule = task.recurrence ?: return null
        val current = task.dueDate ?: task.startDate
        val next: LocalDate = if (current == null || rule.fromCompletion) {
            if (rule.frequency == RecurrenceFrequency.WEEKLY && rule.weekdays.isNotEmpty()) {
                nextMatchingFrom(rule, completedOn, weekStart)
            } else {
                step(rule, completedOn, completedOn, weekStart)
            }
        } else {
            var n = step(rule, current, current, weekStart)
            var guard = 0
            while (!n.isAfter(completedOn) && guard < 10_000) {
                n = step(rule, n, current, weekStart)
                guard++
            }
            n
        }
        if (rule.endDate != null && next.isAfter(rule.endDate)) return null

        return when {
            task.dueDate != null && task.startDate != null -> {
                val span = ChronoUnit.DAYS.between(task.startDate, task.dueDate)
                NextOccurrence(startDate = next.minusDays(span), dueDate = next)
            }
            task.dueDate != null -> NextOccurrence(startDate = null, dueDate = next)
            task.startDate != null -> NextOccurrence(startDate = next, dueDate = null)
            else -> NextOccurrence(startDate = null, dueDate = next)
        }
    }

    private fun nextMatchingFrom(rule: RecurrenceRule, base: LocalDate, weekStart: DayOfWeek): LocalDate {
        // With interval 1 this is simply the next selected weekday after the completion day.
        if (rule.interval == 1) {
            var d = base.plusDays(1)
            repeat(7) {
                if (d.dayOfWeek in rule.weekdays) return d
                d = d.plusDays(1)
            }
        }
        // For larger intervals, jump (interval - 1) weeks ahead then find the next selected day.
        val jumped = DateRules.startOfWeek(base, weekStart).plusWeeks(rule.interval.toLong())
        var d = jumped
        repeat(7) {
            if (d.dayOfWeek in rule.weekdays) return d
            d = d.plusDays(1)
        }
        return base.plusWeeks(rule.interval.toLong())
    }

    /**
     * Projects future occurrences of a series anchored at [anchor] (the current instance's date) that fall in
     * [from]..[to]. The anchor itself is excluded. Used to show repeating tasks on the calendar.
     */
    fun projectedOccurrences(
        rule: RecurrenceRule,
        anchor: LocalDate,
        from: LocalDate,
        to: LocalDate,
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
        limit: Int = 400,
    ): List<LocalDate> {
        if (rule.fromCompletion) return emptyList() // can't be known in advance
        val out = mutableListOf<LocalDate>()
        var d = step(rule, anchor, anchor, weekStart)
        var guard = 0
        while (!d.isAfter(to) && guard < 5_000 && out.size < limit) {
            if (rule.endDate != null && d.isAfter(rule.endDate)) break
            if (!d.isBefore(from)) out += d
            d = step(rule, d, anchor, weekStart)
            guard++
        }
        return out
    }
}
