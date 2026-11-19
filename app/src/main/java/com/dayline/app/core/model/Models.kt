package com.dayline.app.core.model

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Four task priority levels, ordered from least to most important. */
enum class Priority(val level: Int, val label: String) {
    LOW(1, "Low"),
    MEDIUM(2, "Medium"),
    HIGH(3, "High"),
    URGENT(4, "Urgent"),
}

enum class RecurrenceFrequency(val label: String, val unit: String) {
    DAILY("Daily", "day"),
    WEEKLY("Weekly", "week"),
    MONTHLY("Monthly", "month"),
    YEARLY("Yearly", "year"),
}

/**
 * A repeat rule. "Custom" schedules are expressed with [interval] > 1 and/or [weekdays].
 * When [fromCompletion] is true, the next occurrence is measured from the day the task was completed.
 */
data class RecurrenceRule(
    val frequency: RecurrenceFrequency,
    val interval: Int = 1,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val fromCompletion: Boolean = false,
    val endDate: LocalDate? = null,
) {
    init {
        require(interval >= 1) { "Interval must be at least 1" }
    }

    fun describe(): String {
        val base = when {
            frequency == RecurrenceFrequency.WEEKLY && weekdays.isNotEmpty() -> {
                val days = weekdays.sortedBy { it.value }.joinToString(", ") { it.shortName() }
                if (interval == 1) "Every $days" else "Every $interval weeks on $days"
            }
            interval == 1 -> frequency.label
            else -> "Every $interval ${frequency.unit}s"
        }
        return if (fromCompletion) "$base after completion" else base
    }
}

fun DayOfWeek.shortName(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }

data class Task(
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val notes: String = "",
    val startDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val priority: Priority = Priority.MEDIUM,
    val category: String? = null,
    val projectId: Long? = null,
    val listId: Long? = null,
    val recurrence: RecurrenceRule? = null,
    val isCompleted: Boolean = false,
    val completedAt: Instant? = null,
    /** Manual progress (0..100). Ignored for display when the task has subtasks. */
    val progress: Int = 0,
    val sortOrder: Long = 0,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
) {
    /** The date a task is "scheduled" on: due date first, otherwise start date. */
    val scheduledDate: LocalDate? get() = dueDate ?: startDate

    fun isOverdue(today: LocalDate): Boolean = !isCompleted && dueDate != null && dueDate.isBefore(today)
}

data class Subtask(
    val id: Long = 0,
    val taskId: Long,
    val title: String,
    val isDone: Boolean = false,
    val position: Int = 0,
)

data class Tag(val id: Long = 0, val name: String, val color: Int = 0)

data class Project(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val color: Int,
    val icon: String = "folder",
    val startDate: LocalDate? = null,
    val deadline: LocalDate? = null,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Instant = Instant.EPOCH,
)

data class TaskList(
    val id: Long = 0,
    val name: String,
    val color: Int,
    val icon: String = "list",
    val projectId: Long? = null,
    val sortOrder: Int = 0,
)

enum class AttachmentKind { FILE, LINK }

data class Attachment(
    val id: Long = 0,
    val taskId: Long,
    val kind: AttachmentKind,
    val uri: String,
    val displayName: String,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,
    val addedAt: Instant = Instant.EPOCH,
)

enum class ReminderType { TASK, DEADLINE, CUSTOM }

/**
 * TASK and DEADLINE reminders are relative to the task's start / due moment ([offsetMinutes] before it).
 * CUSTOM reminders fire at an absolute [at] time and may exist without a task (pinned reminders).
 */
data class Reminder(
    val id: Long = 0,
    val taskId: Long? = null,
    val type: ReminderType,
    val offsetMinutes: Int = 0,
    val at: LocalDateTime? = null,
    val title: String = "",
    val isPinned: Boolean = false,
    val isDismissed: Boolean = false,
)

data class Note(
    val id: Long = 0,
    val title: String,
    val body: String = "",
    val category: String? = null,
    val isPinned: Boolean = false,
    val taskId: Long? = null,
    val projectId: Long? = null,
    val color: Int = 0,
    val createdAt: Instant = Instant.EPOCH,
    val updatedAt: Instant = Instant.EPOCH,
    /** Set when the note is archived: out of the main list, still searchable from Archive. */
    val archivedAt: Instant? = null,
    /** Set when the note is in Trash; it stays there until restored or deleted for good. */
    val trashedAt: Instant? = null,
    /** Locked notes hide their text until the person unlocks with their fingerprint, face or screen lock. */
    val locked: Boolean = false,
) {
    val isArchived get() = archivedAt != null
    val isTrashed get() = trashedAt != null
}

enum class HabitFrequency(val label: String) {
    DAILY("Every day"),
    SPECIFIC_DAYS("Specific days"),
    WEEKLY_TARGET("Times per week"),
}

data class Habit(
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val icon: String = "spa",
    val color: Int,
    val frequency: HabitFrequency = HabitFrequency.DAILY,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val timesPerWeek: Int = 3,
    /** How many check-ins in a day count as "done" (e.g. 8 glasses of water). */
    val targetPerDay: Int = 1,
    val startDate: LocalDate,
    val reminderTime: LocalTime? = null,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Instant = Instant.EPOCH,
)

data class HabitCompletion(val habitId: Long, val date: LocalDate, val count: Int)

data class FocusSession(
    val id: Long = 0,
    val startedAt: Instant,
    val endedAt: Instant,
    val focusedMillis: Long,
    val plannedMillis: Long,
    val taskId: Long? = null,
    val projectId: Long? = null,
    val completed: Boolean,
) {
    val focusedMinutes: Int get() = (focusedMillis / 60_000L).toInt()
}
