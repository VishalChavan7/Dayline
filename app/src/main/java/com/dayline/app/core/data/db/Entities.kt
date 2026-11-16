package com.dayline.app.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.dayline.app.core.model.AttachmentKind
import com.dayline.app.core.model.HabitFrequency
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.RecurrenceFrequency
import com.dayline.app.core.model.ReminderType
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val color: Int,
    val icon: String,
    val startDate: LocalDate?,
    val deadline: LocalDate?,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAt: Instant,
)

@Entity(
    tableName = "lists",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("projectId")],
)
data class ListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val icon: String,
    val projectId: Long?,
    val sortOrder: Int,
)

@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(ListEntity::class, ["id"], ["listId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("projectId"), Index("listId"), Index("dueDate"), Index("isCompleted")],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val description: String,
    val notes: String,
    val startDate: LocalDate?,
    val startTime: LocalTime?,
    val dueDate: LocalDate?,
    val dueTime: LocalTime?,
    val priority: Priority,
    val category: String?,
    val projectId: Long?,
    val listId: Long?,
    val recurrenceFrequency: RecurrenceFrequency?,
    val recurrenceInterval: Int,
    val recurrenceWeekdays: Set<DayOfWeek>,
    val recurrenceFromCompletion: Boolean,
    val recurrenceEndDate: LocalDate?,
    val isCompleted: Boolean,
    val completedAt: Instant?,
    val progress: Int,
    val sortOrder: Long,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "subtasks",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId")],
)
data class SubtaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val title: String,
    val isDone: Boolean,
    val position: Int,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
)

@Entity(
    tableName = "task_tags",
    primaryKeys = ["taskId", "tagId"],
    foreignKeys = [
        ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TagEntity::class, ["id"], ["tagId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("tagId")],
)
data class TaskTagCrossRef(val taskId: Long, val tagId: Long)

@Entity(
    tableName = "task_dependencies",
    primaryKeys = ["taskId", "dependsOnTaskId"],
    foreignKeys = [
        ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(TaskEntity::class, ["id"], ["dependsOnTaskId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("dependsOnTaskId")],
)
data class TaskDependencyEntity(val taskId: Long, val dependsOnTaskId: Long)

@Entity(
    tableName = "attachments",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId")],
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val kind: AttachmentKind,
    val uri: String,
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val addedAt: Instant,
)

@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("taskId")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long?,
    val type: ReminderType,
    val offsetMinutes: Int,
    val at: LocalDateTime?,
    val title: String,
    val isPinned: Boolean,
    val isDismissed: Boolean,
)

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val icon: String,
    val color: Int,
    val frequency: HabitFrequency,
    val weekdays: Set<DayOfWeek>,
    val timesPerWeek: Int,
    val targetPerDay: Int,
    val startDate: LocalDate,
    val reminderTime: LocalTime?,
    val isArchived: Boolean,
    val sortOrder: Int,
    val createdAt: Instant,
)

@Entity(
    tableName = "habit_completions",
    primaryKeys = ["habitId", "date"],
    foreignKeys = [ForeignKey(HabitEntity::class, ["id"], ["habitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("date")],
)
data class HabitCompletionEntity(val habitId: Long, val date: LocalDate, val count: Int)

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("taskId"), Index("projectId")],
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val category: String?,
    val isPinned: Boolean,
    val taskId: Long?,
    val projectId: Long?,
    val color: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
)

@Entity(
    tableName = "focus_sessions",
    foreignKeys = [
        ForeignKey(TaskEntity::class, ["id"], ["taskId"], onDelete = ForeignKey.SET_NULL),
        ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("taskId"), Index("projectId"), Index("startedAt")],
)
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Instant,
    val endedAt: Instant,
    val focusedMillis: Long,
    val plannedMillis: Long,
    val taskId: Long?,
    val projectId: Long?,
    val completed: Boolean,
)
