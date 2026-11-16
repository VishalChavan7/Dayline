package com.dayline.app.core.model

import com.dayline.app.core.domain.FocusConfig
import java.time.DayOfWeek
import java.time.LocalTime

/** A task enriched with everything list rows need. */
data class TaskItem(
    val task: Task,
    val subtasksDone: Int = 0,
    val subtasksTotal: Int = 0,
    val tags: List<Tag> = emptyList(),
    val project: Project? = null,
    val list: TaskList? = null,
    /** Incomplete tasks this one depends on. */
    val blockedBy: List<Task> = emptyList(),
    val reminderCount: Int = 0,
    val attachmentCount: Int = 0,
    val incompleteSubtasks: List<Subtask> = emptyList(),
) {
    val id: Long get() = task.id
    val isBlocked: Boolean get() = blockedBy.isNotEmpty()

    /** Subtasks drive progress when present; otherwise the manual progress value is used. */
    val progressFraction: Float
        get() = when {
            task.isCompleted -> 1f
            subtasksTotal > 0 -> subtasksDone.toFloat() / subtasksTotal
            else -> task.progress / 100f
        }
}

data class TaskDetails(
    val task: Task,
    val subtasks: List<Subtask>,
    val tags: List<Tag>,
    val dependsOn: List<Task>,
    val dependents: List<Task>,
    val attachments: List<Attachment>,
    val reminders: List<Reminder>,
    val notes: List<Note>,
    val project: Project?,
    val list: TaskList?,
)

/** Everything the task editor saves in one transaction. */
data class TaskDraft(
    val task: Task,
    val subtasks: List<Subtask> = emptyList(),
    val tagNames: List<String> = emptyList(),
    val dependsOnIds: List<Long> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
)

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

enum class TimeFormat(val label: String) { SYSTEM("System"), H12("12-hour"), H24("24-hour") }

enum class DateFormatOption(val label: String, val pattern: String) {
    MEDIUM("Nov 12, 2026", "MMM d, yyyy"),
    DAY_MONTH("12 Nov 2026", "d MMM yyyy"),
    NUMERIC_DMY("12/11/2026", "dd/MM/yyyy"),
    NUMERIC_MDY("11/12/2026", "MM/dd/yyyy"),
    ISO("2026-11-12", "yyyy-MM-dd"),
}

enum class DashboardSection(val label: String, val description: String) {
    PROGRESS("Daily progress", "Completion ring and counts"),
    OVERDUE("Overdue", "Tasks past their due date"),
    TODAY("Today's tasks", "Everything planned for today"),
    INBOX("Inbox", "Tasks without a date, project or list"),
    DEADLINES("Upcoming deadlines", "Due in the next 7 days"),
    FOCUS("Focus", "Today's focus time and quick start"),
    WEEKLY("Weekly summary", "Tasks completed this week"),
    QUOTE("Daily quote", "A line to start the day"),
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val accentColor: Int = DEFAULT_ACCENT,
    val defaultPriority: Priority = Priority.MEDIUM,
    val defaultListId: Long? = null,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val dateFormat: DateFormatOption = DateFormatOption.MEDIUM,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    val taskRemindersEnabled: Boolean = true,
    val habitRemindersEnabled: Boolean = true,
    val focusNotificationsEnabled: Boolean = true,
    /** Reminder automatically added to new dated tasks; null = none. */
    val defaultReminderOffset: Int? = 0,
    val defaultReminderTime: LocalTime = LocalTime.of(9, 0),
    val focus: FocusConfig = FocusConfig(),
    val dashboardOrder: List<DashboardSection> = DashboardSection.entries.toList(),
    val hiddenSections: Set<DashboardSection> = emptySet(),
    val collapsedSections: Set<DashboardSection> = emptySet(),
    val groupTodayByPriority: Boolean = true,
    /** How the dashboard greets you; empty until you tell us. */
    val userName: String = "",
) {
    companion object {
        const val DEFAULT_ACCENT: Int = 0xFF2F7DF6.toInt()
    }
}

data class SearchResults(
    val query: String,
    val tasks: List<TaskItem> = emptyList(),
    val projects: List<Project> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val notes: List<Note> = emptyList(),
) {
    val isEmpty: Boolean get() = tasks.isEmpty() && projects.isEmpty() && lists.isEmpty() && tags.isEmpty() && notes.isEmpty()
}
