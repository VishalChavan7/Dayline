package com.dayline.app.core.domain

import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.Task
import java.time.DayOfWeek
import java.time.LocalDate

enum class UpcomingBucket(val label: String) {
    TOMORROW("Tomorrow"),
    THIS_WEEK("This week"),
    NEXT_WEEK("Next week"),
    LATER("Later"),
}

enum class TaskSort(val label: String) {
    MANUAL("Manual"),
    DUE_DATE("Due date"),
    PRIORITY("Priority"),
    CREATED("Recently created"),
    TITLE("Title"),
}

object TaskGrouping {

    /** Bucket for a future date, or null if [date] is today or earlier. */
    fun upcomingBucket(date: LocalDate, today: LocalDate, weekStart: DayOfWeek): UpcomingBucket? {
        if (!date.isAfter(today)) return null
        if (date == today.plusDays(1)) return UpcomingBucket.TOMORROW
        val endThisWeek = DateRules.endOfWeek(today, weekStart)
        if (!date.isAfter(endThisWeek)) return UpcomingBucket.THIS_WEEK
        if (!date.isAfter(endThisWeek.plusWeeks(1))) return UpcomingBucket.NEXT_WEEK
        return UpcomingBucket.LATER
    }

    fun groupUpcoming(tasks: List<Task>, today: LocalDate, weekStart: DayOfWeek): Map<UpcomingBucket, List<Task>> {
        val grouped = tasks.filter { !it.isCompleted }
            .mapNotNull { t -> t.scheduledDate?.let { d -> upcomingBucket(d, today, weekStart)?.let { it to t } } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, v) -> v.sortedWith(chronological) }
        return UpcomingBucket.entries.filter { grouped.containsKey(it) }.associateWith { grouped.getValue(it) }
    }

    val chronological: Comparator<Task> = compareBy<Task>(
        { it.scheduledDate ?: LocalDate.MAX },
        { it.dueTime ?: it.startTime ?: java.time.LocalTime.MAX },
        { -it.priority.level },
        { it.sortOrder },
    )

    fun sort(tasks: List<Task>, sort: TaskSort): List<Task> = when (sort) {
        TaskSort.MANUAL -> tasks.sortedWith(compareBy<Task>({ it.sortOrder }, { it.id }))
        TaskSort.DUE_DATE -> tasks.sortedWith(chronological)
        TaskSort.PRIORITY -> tasks.sortedWith(compareBy<Task>({ -it.priority.level }).then(chronological))
        TaskSort.CREATED -> tasks.sortedByDescending { it.createdAt }
        TaskSort.TITLE -> tasks.sortedBy { it.title.lowercase() }
    }

    fun groupByPriority(tasks: List<Task>): Map<Priority, List<Task>> =
        Priority.entries.reversed().associateWith { p -> tasks.filter { it.priority == p } }.filterValues { it.isNotEmpty() }

    /** Tasks belonging on a given day: scheduled that day (due date or start date). */
    fun onDay(tasks: List<Task>, day: LocalDate): List<Task> = tasks.filter { t ->
        t.dueDate == day || (t.dueDate == null && t.startDate == day)
    }

    /** Tasks whose start..due span covers the day (used for multi-day bars). */
    fun spanning(tasks: List<Task>, day: LocalDate): List<Task> = tasks.filter { t ->
        val s = t.startDate ?: t.dueDate
        val e = t.dueDate ?: t.startDate
        s != null && e != null && !day.isBefore(s) && !day.isAfter(e)
    }
}
