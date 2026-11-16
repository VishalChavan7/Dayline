package com.dayline.app.core.domain

import com.dayline.app.core.model.RecurrenceFrequency
import com.dayline.app.core.model.RecurrenceRule
import com.dayline.app.core.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceEngineTest {
    private val mon = LocalDate.of(2026, 11, 2) // Monday

    private fun task(due: LocalDate?, rule: RecurrenceRule, start: LocalDate? = null) =
        Task(title = "t", dueDate = due, startDate = start, recurrence = rule)

    @Test
    fun dailyAdvancesByInterval() {
        val next = RecurrenceEngine.nextOccurrence(task(mon, RecurrenceRule(RecurrenceFrequency.DAILY, 2)), completedOn = mon)
        assertEquals(mon.plusDays(2), next?.dueDate)
    }

    @Test
    fun completingOverdueDailyTaskSkipsMissedDays() {
        val due = mon.minusDays(5)
        val next = RecurrenceEngine.nextOccurrence(task(due, RecurrenceRule(RecurrenceFrequency.DAILY)), completedOn = mon)
        assertEquals(mon.plusDays(1), next?.dueDate)
    }

    @Test
    fun completingEarlyKeepsSchedule() {
        val due = mon.plusDays(3)
        val next = RecurrenceEngine.nextOccurrence(task(due, RecurrenceRule(RecurrenceFrequency.WEEKLY)), completedOn = mon)
        assertEquals(due.plusWeeks(1), next?.dueDate)
    }

    @Test
    fun weeklyOnSpecificWeekdays() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
        val afterMon = RecurrenceEngine.nextOccurrence(task(mon, rule), completedOn = mon)
        assertEquals(mon.plusDays(3), afterMon?.dueDate) // Thursday
        val thu = mon.plusDays(3)
        val afterThu = RecurrenceEngine.nextOccurrence(task(thu, rule), completedOn = thu)
        assertEquals(mon.plusWeeks(1), afterThu?.dueDate) // next Monday
    }

    @Test
    fun everyOtherWeekOnWeekdaySkipsOffWeeks() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY, interval = 2, weekdays = setOf(DayOfWeek.MONDAY))
        val next = RecurrenceEngine.nextOccurrence(task(mon, rule), completedOn = mon)
        assertEquals(mon.plusWeeks(2), next?.dueDate)
    }

    @Test
    fun monthlyClampsToEndOfMonth() {
        val jan31 = LocalDate.of(2026, 1, 31)
        val next = RecurrenceEngine.nextOccurrence(task(jan31, RecurrenceRule(RecurrenceFrequency.MONTHLY)), completedOn = jan31)
        assertEquals(LocalDate.of(2026, 2, 28), next?.dueDate)
    }

    @Test
    fun yearly() {
        val d = LocalDate.of(2026, 3, 10)
        val next = RecurrenceEngine.nextOccurrence(task(d, RecurrenceRule(RecurrenceFrequency.YEARLY)), completedOn = d)
        assertEquals(LocalDate.of(2027, 3, 10), next?.dueDate)
    }

    @Test
    fun repeatAfterCompletionMeasuresFromCompletionDay() {
        val due = mon
        val doneOn = mon.plusDays(4)
        val rule = RecurrenceRule(RecurrenceFrequency.DAILY, interval = 3, fromCompletion = true)
        val next = RecurrenceEngine.nextOccurrence(task(due, rule), completedOn = doneOn)
        assertEquals(doneOn.plusDays(3), next?.dueDate)
    }

    @Test
    fun preservesStartToDueSpan() {
        val start = mon
        val due = mon.plusDays(2)
        val next = RecurrenceEngine.nextOccurrence(task(due, RecurrenceRule(RecurrenceFrequency.WEEKLY), start), completedOn = due)
        assertEquals(start.plusWeeks(1), next?.startDate)
        assertEquals(due.plusWeeks(1), next?.dueDate)
    }

    @Test
    fun seriesEndsAfterEndDate() {
        val rule = RecurrenceRule(RecurrenceFrequency.DAILY, endDate = mon)
        assertNull(RecurrenceEngine.nextOccurrence(task(mon, rule), completedOn = mon))
    }

    @Test
    fun nonRecurringTaskHasNoNextOccurrence() {
        assertNull(RecurrenceEngine.nextOccurrence(Task(title = "x", dueDate = mon), completedOn = mon))
    }

    @Test
    fun projectsOccurrencesForCalendar() {
        val rule = RecurrenceRule(RecurrenceFrequency.WEEKLY)
        val dates = RecurrenceEngine.projectedOccurrences(rule, mon, mon, mon.plusWeeks(3))
        assertEquals(listOf(mon.plusWeeks(1), mon.plusWeeks(2), mon.plusWeeks(3)), dates)
    }

    @Test
    fun noProjectionForRepeatAfterCompletion() {
        val rule = RecurrenceRule(RecurrenceFrequency.DAILY, fromCompletion = true)
        assertEquals(emptyList<LocalDate>(), RecurrenceEngine.projectedOccurrences(rule, mon, mon, mon.plusDays(10)))
    }
}
