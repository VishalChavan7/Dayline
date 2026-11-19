package com.dayline.app.core.data.db

import com.dayline.app.core.model.Attachment
import com.dayline.app.core.model.FocusSession
import com.dayline.app.core.model.Habit
import com.dayline.app.core.model.HabitCompletion
import com.dayline.app.core.model.Note
import com.dayline.app.core.model.Project
import com.dayline.app.core.model.RecurrenceRule
import com.dayline.app.core.model.Reminder
import com.dayline.app.core.model.Subtask
import com.dayline.app.core.model.Tag
import com.dayline.app.core.model.Task
import com.dayline.app.core.model.TaskList

fun TaskEntity.toModel() = Task(
    id = id, title = title, description = description, notes = notes,
    startDate = startDate, startTime = startTime, dueDate = dueDate, dueTime = dueTime,
    priority = priority, category = category, projectId = projectId, listId = listId,
    recurrence = recurrenceFrequency?.let {
        RecurrenceRule(it, recurrenceInterval.coerceAtLeast(1), recurrenceWeekdays, recurrenceFromCompletion, recurrenceEndDate)
    },
    isCompleted = isCompleted, completedAt = completedAt, progress = progress, sortOrder = sortOrder,
    createdAt = createdAt, updatedAt = updatedAt,
)

fun Task.toEntity() = TaskEntity(
    id = id, title = title, description = description, notes = notes,
    startDate = startDate, startTime = startTime, dueDate = dueDate, dueTime = dueTime,
    priority = priority, category = category?.takeIf { it.isNotBlank() }, projectId = projectId, listId = listId,
    recurrenceFrequency = recurrence?.frequency, recurrenceInterval = recurrence?.interval ?: 1,
    recurrenceWeekdays = recurrence?.weekdays ?: emptySet(), recurrenceFromCompletion = recurrence?.fromCompletion ?: false,
    recurrenceEndDate = recurrence?.endDate,
    isCompleted = isCompleted, completedAt = completedAt, progress = progress.coerceIn(0, 100), sortOrder = sortOrder,
    createdAt = createdAt, updatedAt = updatedAt,
)

fun SubtaskEntity.toModel() = Subtask(id, taskId, title, isDone, position)
fun Subtask.toEntity(taskId: Long, position: Int) = SubtaskEntity(id, taskId, title.trim(), isDone, position)

fun TagEntity.toModel() = Tag(id, name, color)

fun ProjectEntity.toModel() = Project(id, name, description, color, icon, startDate, deadline, isArchived, sortOrder, createdAt)
fun Project.toEntity() = ProjectEntity(id, name.trim(), description, color, icon, startDate, deadline, isArchived, sortOrder, createdAt)

fun ListEntity.toModel() = TaskList(id, name, color, icon, projectId, sortOrder)
fun TaskList.toEntity() = ListEntity(id, name.trim(), color, icon, projectId, sortOrder)

fun AttachmentEntity.toModel() = Attachment(id, taskId, kind, uri, displayName, mimeType, sizeBytes, addedAt)
fun Attachment.toEntity(taskId: Long) = AttachmentEntity(0, taskId, kind, uri, displayName, mimeType, sizeBytes, addedAt)

fun ReminderEntity.toModel() = Reminder(id, taskId, type, offsetMinutes, at, title, isPinned, isDismissed)
fun Reminder.toEntity() = ReminderEntity(id, taskId, type, offsetMinutes, at, title, isPinned, isDismissed)

fun HabitEntity.toModel() = Habit(
    id, name, description, icon, color, frequency, weekdays, timesPerWeek, targetPerDay, startDate, reminderTime, isArchived, sortOrder, createdAt,
)
fun Habit.toEntity() = HabitEntity(
    id, name.trim(), description, icon, color, frequency, weekdays, timesPerWeek.coerceIn(1, 7), targetPerDay.coerceIn(1, 99),
    startDate, reminderTime, isArchived, sortOrder, createdAt,
)

fun HabitCompletionEntity.toModel() = HabitCompletion(habitId, date, count)

fun NoteEntity.toModel() = Note(id, title, body, category, isPinned, taskId, projectId, color, createdAt, updatedAt, archivedAt, trashedAt, locked)
fun Note.toEntity() = NoteEntity(id, title.trim(), body, category?.takeIf { it.isNotBlank() }, isPinned, taskId, projectId, color, createdAt, updatedAt, archivedAt, trashedAt, locked)

fun FocusSessionEntity.toModel() = FocusSession(id, startedAt, endedAt, focusedMillis, plannedMillis, taskId, projectId, completed)
fun FocusSession.toEntity() = FocusSessionEntity(id, startedAt, endedAt, focusedMillis, plannedMillis, taskId, projectId, completed)
