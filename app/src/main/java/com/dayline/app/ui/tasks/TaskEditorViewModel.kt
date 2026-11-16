package com.dayline.app.ui.tasks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.prefs.SettingsRepository
import com.dayline.app.core.data.repository.ProjectRepository
import com.dayline.app.core.data.repository.TagRepository
import com.dayline.app.core.data.repository.TaskRepository
import com.dayline.app.core.model.Attachment
import com.dayline.app.core.model.Note
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.Project
import com.dayline.app.core.model.RecurrenceRule
import com.dayline.app.core.model.Reminder
import com.dayline.app.core.model.ReminderType
import com.dayline.app.core.model.Subtask
import com.dayline.app.core.model.Tag
import com.dayline.app.core.model.Task
import com.dayline.app.core.model.TaskDraft
import com.dayline.app.core.model.TaskItem
import com.dayline.app.core.model.TaskList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/** Subtask with a stable UI key (new subtasks have id 0 until saved). */
data class EditableSubtask(val key: Long, val subtask: Subtask)

data class TaskEditorState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val id: Long = 0,
    val title: String = "",
    val description: String = "",
    val notes: String = "",
    val startDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val dueDate: LocalDate? = null,
    val dueTime: LocalTime? = null,
    val priority: Priority = Priority.MEDIUM,
    val category: String = "",
    val projectId: Long? = null,
    val listId: Long? = null,
    val recurrence: RecurrenceRule? = null,
    val isCompleted: Boolean = false,
    val completedAt: Instant? = null,
    val progress: Int = 0,
    val subtasks: List<EditableSubtask> = emptyList(),
    val tagNames: List<String> = emptyList(),
    val dependsOn: List<Task> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val linkedNotes: List<Note> = emptyList(),
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    val sortOrder: Long = 0,
    val error: String? = null,
    val dirty: Boolean = false,
    val finished: Boolean = false,
) {
    val hasDate: Boolean get() = startDate != null || dueDate != null
}

data class EditorOptions(
    val projects: List<Project> = emptyList(),
    val lists: List<TaskList> = emptyList(),
    val categories: List<String> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val candidates: List<TaskItem> = emptyList(),
)

@HiltViewModel
class TaskEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val tasks: TaskRepository,
    projects: ProjectRepository,
    tags: TagRepository,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val taskId: Long = savedState.get<Long>("id") ?: 0L
    private val argDate: LocalDate? = savedState.get<Long>("date")?.takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
    private val argProject: Long? = savedState.get<Long>("project")?.takeIf { it > 0 }
    private val argList: Long? = savedState.get<Long>("list")?.takeIf { it > 0 }
    private var nextKey = -1L

    private val _state = MutableStateFlow(TaskEditorState())
    val state: StateFlow<TaskEditorState> = _state.asStateFlow()

    val options: StateFlow<EditorOptions> = combine(projects.projects, projects.lists, tasks.categories, tags.tags, tasks.items) { p, l, c, t, items ->
        EditorOptions(p.filter { !it.isArchived }, l, c, t, items.filter { !it.task.isCompleted && it.id != taskId })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EditorOptions())

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val s = settings.current()
        if (taskId == 0L) {
            val reminders = if (argDate != null && s.defaultReminderOffset != null) {
                listOf(Reminder(type = ReminderType.DEADLINE, offsetMinutes = s.defaultReminderOffset))
            } else emptyList()
            _state.value = TaskEditorState(
                loading = false, isNew = true, dueDate = argDate, priority = s.defaultPriority,
                projectId = argProject, listId = argList ?: if (argProject == null) s.defaultListId else null, reminders = reminders,
            )
            return
        }
        val d = tasks.getDetails(taskId)
        if (d == null) {
            _state.value = TaskEditorState(loading = false, error = "This task no longer exists.", finished = true)
            return
        }
        val t = d.task
        _state.value = TaskEditorState(
            loading = false, isNew = false, id = t.id, title = t.title, description = t.description, notes = t.notes,
            startDate = t.startDate, startTime = t.startTime, dueDate = t.dueDate, dueTime = t.dueTime, priority = t.priority,
            category = t.category.orEmpty(), projectId = t.projectId, listId = t.listId, recurrence = t.recurrence,
            isCompleted = t.isCompleted, completedAt = t.completedAt, progress = t.progress,
            subtasks = d.subtasks.map { EditableSubtask(it.id, it) }, tagNames = d.tags.map { it.name }, dependsOn = d.dependsOn,
            attachments = d.attachments, reminders = d.reminders, linkedNotes = d.notes, createdAt = t.createdAt, updatedAt = t.updatedAt,
            sortOrder = t.sortOrder,
        )
    }

    fun update(transform: (TaskEditorState) -> TaskEditorState) = _state.update { transform(it).copy(dirty = true, error = null) }

    // Subtasks
    fun addSubtask(title: String) {
        if (title.isBlank()) return
        update { it.copy(subtasks = it.subtasks + EditableSubtask(nextKey--, Subtask(taskId = it.id, title = title.trim()))) }
    }
    fun editSubtask(key: Long, title: String) = update { s -> s.copy(subtasks = s.subtasks.map { if (it.key == key) it.copy(subtask = it.subtask.copy(title = title)) else it }) }
    fun toggleSubtask(key: Long) = update { s -> s.copy(subtasks = s.subtasks.map { if (it.key == key) it.copy(subtask = it.subtask.copy(isDone = !it.subtask.isDone)) else it }) }
    fun removeSubtask(key: Long) = update { s -> s.copy(subtasks = s.subtasks.filter { it.key != key }) }
    fun moveSubtask(key: Long, delta: Int) = update { s ->
        val list = s.subtasks.toMutableList()
        val i = list.indexOfFirst { it.key == key }
        val j = (i + delta).coerceIn(0, list.lastIndex)
        if (i >= 0 && i != j) list.add(j, list.removeAt(i))
        s.copy(subtasks = list)
    }

    // Tags
    fun addTag(name: String) {
        val clean = name.trim().removePrefix("#").replace(" ", "-")
        if (clean.isEmpty()) return
        update { s -> if (s.tagNames.any { it.equals(clean, true) }) s else s.copy(tagNames = s.tagNames + clean) }
    }
    fun removeTag(name: String) = update { s -> s.copy(tagNames = s.tagNames.filterNot { it == name }) }

    // Reminders, attachments, dependencies
    fun addReminder(r: Reminder) = update { s ->
        if (s.reminders.any { it.type == r.type && it.offsetMinutes == r.offsetMinutes && it.at == r.at }) s else s.copy(reminders = s.reminders + r)
    }
    fun removeReminder(r: Reminder) = update { s -> s.copy(reminders = s.reminders - r) }
    fun addAttachment(a: Attachment) = update { s -> s.copy(attachments = s.attachments + a) }
    fun removeAttachment(a: Attachment) = update { s -> s.copy(attachments = s.attachments - a) }
    fun setDependencies(list: List<Task>) = update { it.copy(dependsOn = list) }

    /** Validates and saves, then closes the editor. */
    fun save() = viewModelScope.launch {
        if (persist()) _state.update { it.copy(finished = true, dirty = false) }
    }

    private suspend fun persist(): Boolean {
        val s = _state.value
        val error = validate(s)
        if (error != null) {
            _state.update { it.copy(error = error) }
            return false
        }
        return try {
            tasks.save(
                TaskDraft(
                    task = Task(
                        id = s.id, title = s.title.trim(), description = s.description.trim(), notes = s.notes.trim(),
                        startDate = s.startDate, startTime = s.startTime?.takeIf { s.startDate != null },
                        dueDate = s.dueDate, dueTime = s.dueTime?.takeIf { s.dueDate != null },
                        priority = s.priority, category = s.category.trim().ifEmpty { null },
                        projectId = s.projectId, listId = s.listId, recurrence = s.recurrence,
                        isCompleted = s.isCompleted, completedAt = s.completedAt, progress = s.progress,
                        sortOrder = s.sortOrder, createdAt = s.createdAt ?: Instant.EPOCH,
                    ),
                    subtasks = s.subtasks.map { it.subtask },
                    tagNames = s.tagNames,
                    dependsOnIds = s.dependsOn.map { it.id },
                    attachments = s.attachments,
                    reminders = s.reminders,
                ),
            )
            true
        } catch (e: IllegalArgumentException) {
            _state.update { it.copy(error = e.message ?: "Couldn't save the task") }
            false
        }
    }

    /** Completing from the editor saves pending edits first, then runs the normal completion (incl. recurrence). */
    fun setCompleted(done: Boolean) {
        if (_state.value.isNew) { update { it.copy(isCompleted = done) }; return }
        viewModelScope.launch {
            if (_state.value.dirty && !persist()) return@launch
            val result = tasks.setCompleted(taskId, done)
            if (!result.blocked) _state.update { it.copy(finished = true, dirty = false) }
        }
    }

    fun delete() = viewModelScope.launch {
        // Refused (and explained) while the task is blocked; the editor stays open then.
        if (taskId != 0L && tasks.delete(taskId) == null && tasks.blockersOf(taskId).isNotEmpty()) return@launch
        _state.update { it.copy(finished = true, dirty = false) }
    }

    fun duplicate() = viewModelScope.launch {
        if (taskId != 0L) tasks.duplicate(taskId)
        _state.update { it.copy(finished = true) }
    }

    suspend fun defaultTime(): LocalTime = settings.settings.first().defaultReminderTime

    companion object {
        fun validate(s: TaskEditorState): String? = when {
            s.title.isBlank() -> "Give the task a title"
            s.title.length > 300 -> "Title is too long (300 characters max)"
            s.startDate != null && s.dueDate != null && s.dueDate.isBefore(s.startDate) -> "The due date can't be before the start date"
            s.startDate != null && s.dueDate != null && s.startDate == s.dueDate && s.startTime != null && s.dueTime != null &&
                s.dueTime.isBefore(s.startTime) -> "The due time can't be before the start time"
            s.reminders.any { it.type == ReminderType.DEADLINE } && s.dueDate == null -> "Deadline reminders need a due date"
            s.reminders.any { it.type == ReminderType.TASK } && !s.hasDate -> "Task reminders need a start or due date"
            s.recurrence?.endDate?.let { end -> s.dueDate != null && end.isBefore(s.dueDate) } == true -> "The repeat end date is before the due date"
            else -> null
        }
    }
}
