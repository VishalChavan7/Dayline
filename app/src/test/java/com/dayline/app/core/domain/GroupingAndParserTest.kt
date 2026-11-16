package com.dayline.app.core.domain

import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class GroupingAndParserTest {
    private val wed = LocalDate.of(2026, 11, 11) // Wednesday

    @Test
    fun upcomingBuckets() {
        val ws = DayOfWeek.MONDAY
        assertNull(TaskGrouping.upcomingBucket(wed, wed, ws))
        assertEquals(UpcomingBucket.TOMORROW, TaskGrouping.upcomingBucket(wed.plusDays(1), wed, ws))
        assertEquals(UpcomingBucket.THIS_WEEK, TaskGrouping.upcomingBucket(wed.plusDays(4), wed, ws)) // Sunday
        assertEquals(UpcomingBucket.NEXT_WEEK, TaskGrouping.upcomingBucket(wed.plusDays(5), wed, ws)) // next Monday
        assertEquals(UpcomingBucket.LATER, TaskGrouping.upcomingBucket(wed.plusDays(12), wed, ws))
    }

    @Test
    fun upcomingRespectsSundayWeekStart() {
        // With Sunday starts, Sunday Nov 15 is already next week.
        assertEquals(UpcomingBucket.NEXT_WEEK, TaskGrouping.upcomingBucket(wed.plusDays(4), wed, DayOfWeek.SUNDAY))
    }

    @Test
    fun groupUpcomingSkipsCompletedAndUndated() {
        val tasks = listOf(
            Task(id = 1, title = "a", dueDate = wed.plusDays(1)),
            Task(id = 2, title = "b", dueDate = wed.plusDays(1), isCompleted = true),
            Task(id = 3, title = "c"),
            Task(id = 4, title = "d", startDate = wed.plusDays(20)),
        )
        val g = TaskGrouping.groupUpcoming(tasks, wed, DayOfWeek.MONDAY)
        assertEquals(listOf(1L), g[UpcomingBucket.TOMORROW]?.map { it.id })
        assertEquals(listOf(4L), g[UpcomingBucket.LATER]?.map { it.id })
        assertEquals(2, g.size)
    }

    @Test
    fun sortByPriorityThenDate() {
        val tasks = listOf(
            Task(id = 1, title = "a", priority = Priority.LOW, dueDate = wed),
            Task(id = 2, title = "b", priority = Priority.URGENT, dueDate = wed.plusDays(2)),
            Task(id = 3, title = "c", priority = Priority.URGENT, dueDate = wed),
        )
        assertEquals(listOf(3L, 2L, 1L), TaskGrouping.sort(tasks, TaskSort.PRIORITY).map { it.id })
    }

    @Test
    fun parsesQuickAddShortcuts() {
        val p = QuickAddParser.parse("Call mom tomorrow !high #family @Personal", wed)
        assertEquals("Call mom", p.title)
        assertEquals(wed.plusDays(1), p.date)
        assertEquals(Priority.HIGH, p.priority)
        assertEquals(listOf("family"), p.tags)
        assertEquals("Personal", p.listName)
    }

    @Test
    fun parsesWeekdaysAndNumericPriority() {
        val p = QuickAddParser.parse("Submit invoice friday !4", wed)
        assertEquals("Submit invoice", p.title)
        assertEquals(LocalDate.of(2026, 11, 13), p.date)
        assertEquals(Priority.URGENT, p.priority)
        val nw = QuickAddParser.parse("Plan sprint next week", wed)
        assertEquals("Plan sprint", nw.title)
        assertEquals(LocalDate.of(2026, 11, 16), nw.date)
    }

    @Test
    fun plainTitlesAreUntouched() {
        val p = QuickAddParser.parse("Buy milk", wed)
        assertEquals("Buy milk", p.title)
        assertNull(p.date)
        assertNull(p.priority)
    }
}
