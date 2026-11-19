package com.dayline.app.core.data.repository

import androidx.room.withTransaction
import com.dayline.app.core.common.ReminderSync
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.db.AttachmentEntity
import com.dayline.app.core.data.db.DaylineDatabase
import com.dayline.app.core.data.db.ListEntity
import com.dayline.app.core.data.db.ProjectEntity
import com.dayline.app.core.data.db.ReminderEntity
import com.dayline.app.core.data.db.SubtaskEntity
import com.dayline.app.core.data.db.TagEntity
import com.dayline.app.core.data.db.TaskDependencyEntity
import com.dayline.app.core.data.db.TaskEntity
import com.dayline.app.core.data.db.TaskTagCrossRef
import com.dayline.app.core.data.db.toEntity
import com.dayline.app.core.data.db.toModel
import com.dayline.app.core.data.prefs.SettingsRepository
import com.dayline.app.core.domain.RecurrenceEngine
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.Reminder
import com.dayline.app.core.model.ReminderType
import com.dayline.app.core.model.Subtask
import com.dayline.app.core.model.Task
import com.dayline.app.core.model.TaskDetails
import com.dayline.app.core.model.TaskDraft
import com.dayline.app.core.model.TaskItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class CompletionResult(val nextOccurrenceId: Long?, val blocked: Boolean = false)

/** Messages for the user when an action on a task is refused (shown as a snackbar or toast). */
object TaskNotices {
    val messages = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 8)
}

/** Snapshot that allows undoing a delete. */
data class DeletedTask(val draft: TaskDraft)

@Singleton
class TaskRepository @Inject constructor(
    private val db: DaylineDatabase,
    private val time: TimeProvider,
    private val reminderSync: ReminderSync,
    private val settings: SettingsRepository,
) {
    private val taskDao = db.taskDao()
    private val tagDao = db.tagDao()
    private val projectDao = db.projectDao()
    private val reminderDao = db.reminderDao()
    private val noteDao = db.noteDao()

    /** All tasks with their related data; the single source the task screens derive from. */
    val items: Flow<List<TaskItem>> = run {
        val base = combine(taskDao.observeAll(), taskDao.observeAllSubtasks(), taskDao.observeDependencies()) { t, s, d -> Triple(t, s, d) }
        val meta = combine(tagDao.observeAll(), tagDao.observeCrossRefs(), projectDao.observeAll(), projectDao.observeLists()) { a, b, c, d ->
            Meta(a, b, c, d)
        }
        val extras = combine(reminderDao.observeAll(), taskDao.observeAttachments()) { r, a -> r to a }
        combine(base, meta, extras) { (tasks, subtasks, deps), m, (reminders, attachments) ->
            buildItems(tasks, subtasks, deps, m.tags, m.refs, m.projects, m.lists, reminders, attachments)
        }.distinctUntilChanged()
    }

    private data class Meta(
        val tags: List<TagEntity>,
        val refs: List<TaskTagCrossRef>,
        val projects: List<ProjectEntity>,
        val lists: List<ListEntity>,
    )

    val categories: Flow<List<String>> = taskDao.observeCategories()

    fun observeDetails(taskId: Long): Flow<TaskDetails?> = combine(items, noteDao.observeAll(), reminderDao.observeAll()) { all, notes, reminders ->
        val item = all.firstOrNull { it.id == taskId } ?: return@combine null
        val byId = all.associateBy { it.id }
        TaskDetails(
            task = item.task,
            subtasks = taskDao.getSubtasks(taskId).map { it.toModel() },
            tags = item.tags,
            dependsOn = taskDao.getDependenciesOf(taskId).mapNotNull { byId[it.dependsOnTaskId]?.task },
            dependents = taskDao.getDependencies().filter { it.dependsOnTaskId == taskId }.mapNotNull { byId[it.taskId]?.task },
            attachments = taskDao.getAttachments(taskId).map { it.toModel() },
            reminders = reminders.filter { it.taskId == taskId }.map { it.toModel() },
            notes = notes.filter { it.taskId == taskId && it.trashedAt == null }.map { it.toModel() },
            project = item.project,
            list = item.list,
        )
    }

    suspend fun getDetails(taskId: Long): TaskDetails? = observeDetails(taskId).first()

    /** Creates or updates a task and everything attached to it in one transaction. Returns the task id. */
    suspend fun save(draft: TaskDraft, restoreWithId: Boolean = false): Long {
        val title = draft.task.title.trim()
        require(title.isNotEmpty()) { "Title can't be empty" }
        val now = time.now()
        val id = db.withTransaction {
            val existing = if (draft.task.id != 0L && !restoreWithId) taskDao.get(draft.task.id) else null
            val taskId: Long = if (existing != null) {
                taskDao.update(draft.task.copy(title = title, createdAt = existing.createdAt, updatedAt = now).toEntity())
                existing.id
            } else {
                val createdAt = if (restoreWithId) draft.task.createdAt else now
                val sort = if (draft.task.sortOrder != 0L) draft.task.sortOrder else taskDao.maxSortOrder() + 1
                taskDao.insert(draft.task.copy(id = if (restoreWithId) draft.task.id else 0L, title = title, createdAt = createdAt, updatedAt = now, sortOrder = sort).toEntity())
            }
            writeChildren(taskId, draft)
            taskId
        }
        reminderSync.syncTask(id)
        return id
    }

    private suspend fun writeChildren(taskId: Long, draft: TaskDraft) {
        // Subtasks: keep ids of edited ones, insert new, delete removed.
        val cleaned = draft.subtasks.filter { it.title.isNotBlank() }
        val kept = cleaned.filter { it.id != 0L }.map { it.id }
        taskDao.deleteSubtasksExcept(taskId, kept)
        cleaned.forEachIndexed { index, s ->
            if (s.id == 0L) taskDao.insertSubtask(s.toEntity(taskId, index))
            else taskDao.upsertSubtasks(listOf(s.toEntity(taskId, index)))
        }

        // Tags
        tagDao.clearForTask(taskId)
        val tagIds = draft.tagNames.map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
            .map { name -> resolveTag(name) }
        tagDao.insertCrossRefs(tagIds.map { TaskTagCrossRef(taskId, it) })

        // Dependencies (no self-links, no cycles)
        taskDao.clearDependencies(taskId)
        val allDeps = taskDao.getDependencies()
        val valid = draft.dependsOnIds.distinct().filter { it != taskId && !createsCycle(taskId, it, allDeps) }
        taskDao.insertDependencies(valid.map { TaskDependencyEntity(taskId, it) })

        // Attachments and links
        taskDao.clearAttachments(taskId)
        taskDao.insertAttachments(draft.attachments.map { it.toEntity(taskId) })

        // Reminders: cancel old alarms, then replace
        val oldReminders = reminderDao.forTask(taskId).map { it.id }
        reminderSync.cancelReminders(oldReminders)
        reminderDao.clearForTask(taskId)
        reminderDao.insertAll(draft.reminders.distinctBy { Triple(it.type, it.offsetMinutes, it.at) }.map { it.copy(id = 0, taskId = taskId).toEntity() })
    }

    private suspend fun resolveTag(name: String): Long {
        tagDao.findByName(name)?.let { return it.id }
        val id = tagDao.insert(TagEntity(name = name, color = 0))
        return if (id > 0) id else tagDao.findByName(name)!!.id
    }

    /** True if making [taskId] depend on [dependsOn] would create a loop. */
    private fun createsCycle(taskId: Long, dependsOn: Long, deps: List<TaskDependencyEntity>): Boolean {
        val graph = deps.groupBy({ it.taskId }, { it.dependsOnTaskId })
        val seen = mutableSetOf<Long>()
        val stack = ArrayDeque(listOf(dependsOn))
        while (stack.isNotEmpty()) {
            val cur = stack.removeLast()
            if (cur == taskId) return true
            if (seen.add(cur)) graph[cur]?.let { stack.addAll(it) }
        }
        return false
    }

    suspend fun quickAdd(
        title: String,
        date: LocalDate?,
        priority: Priority,
        projectId: Long?,
        listId: Long?,
        tagNames: List<String> = emptyList(),
    ): Long {
        val s = settings.current()
        val reminders = if (date != null && s.defaultReminderOffset != null) {
            listOf(Reminder(type = ReminderType.DEADLINE, offsetMinutes = s.defaultReminderOffset))
        } else emptyList()
        return save(
            TaskDraft(
                task = Task(title = title, dueDate = date, priority = priority, projectId = projectId, listId = listId),
                tagNames = tagNames,
                reminders = reminders,
            ),
        )
    }

    /**
     * Completes or reopens a task. Completing a recurring task archives this instance (without the rule) and
     * creates the next occurrence carrying the rule, subtasks (reset), tags, links and relative reminders.
     */
    /** The unfinished tasks that [taskId] is waiting on. */
    suspend fun blockersOf(taskId: Long): List<Task> =
        taskDao.getDependenciesOf(taskId).mapNotNull { taskDao.get(it.dependsOnTaskId)?.toModel() }.filter { !it.isCompleted }

    suspend fun setCompleted(taskId: Long, completed: Boolean): CompletionResult {
        val entity = taskDao.get(taskId) ?: return CompletionResult(null)
        val task = entity.toModel()
        if (task.isCompleted == completed) return CompletionResult(null)
        if (completed) {
            // A task that depends on unfinished tasks can't be completed yet.
            val blockers = blockersOf(taskId)
            if (blockers.isNotEmpty()) {
                TaskNotices.messages.tryEmit("Finish “${blockers.first().title}” first: “${task.title}” depends on it.")
                return CompletionResult(null, blocked = true)
            }
        }
        val now = time.now()
        if (!completed) {
            taskDao.update(task.copy(isCompleted = false, completedAt = null, updatedAt = now).toEntity())
            reminderSync.syncTask(taskId)
            return CompletionResult(null)
        }
        val weekStart = settings.current().weekStart
        val next = RecurrenceEngine.nextOccurrence(task, time.today(), weekStart)
        var nextId: Long? = null
        db.withTransaction {
            taskDao.update(task.copy(isCompleted = true, completedAt = now, updatedAt = now, recurrence = if (next != null) null else task.recurrence).toEntity())
            if (next != null) {
                val newTask = task.copy(
                    id = 0, isCompleted = false, completedAt = null, progress = 0,
                    startDate = next.startDate, dueDate = next.dueDate,
                    createdAt = now, updatedAt = now, sortOrder = taskDao.maxSortOrder() + 1,
                )
                val newId = taskDao.insert(newTask.toEntity())
                taskDao.getSubtasks(taskId).forEach { taskDao.insertSubtask(SubtaskEntity(0, newId, it.title, false, it.position)) }
                tagDao.insertCrossRefs(tagDao.getCrossRefs().filter { it.taskId == taskId }.map { TaskTagCrossRef(newId, it.tagId) })
                taskDao.insertAttachments(taskDao.getAttachments(taskId).map { it.copy(id = 0, taskId = newId) })
                taskDao.insertDependencies(taskDao.getDependenciesOf(taskId).map { TaskDependencyEntity(newId, it.dependsOnTaskId) })
                reminderDao.insertAll(
                    reminderDao.forTask(taskId).filter { it.type != ReminderType.CUSTOM }.map { it.copy(id = 0, taskId = newId, isDismissed = false) },
                )
                nextId = newId
            }
        }
        reminderSync.syncTask(taskId)
        nextId?.let { reminderSync.syncTask(it) }
        return CompletionResult(nextId)
    }

    /** Deletes a task and returns a snapshot so the caller can offer Undo. */
    suspend fun delete(taskId: Long): DeletedTask? {
        // A blocked task stays until what it waits on is done (or the dependency is removed).
        val blockers = blockersOf(taskId)
        if (blockers.isNotEmpty()) {
            val title = taskDao.get(taskId)?.toModel()?.title.orEmpty()
            TaskNotices.messages.tryEmit("“$title” is waiting on “${blockers.first().title}”. Finish that first, or remove the dependency.")
            return null
        }
        val details = getDetails(taskId) ?: return null
        val snapshot = DeletedTask(
            TaskDraft(details.task, details.subtasks, details.tags.map { it.name }, details.dependsOn.map { it.id }, details.attachments, details.reminders),
        )
        reminderSync.cancelReminders(details.reminders.map { it.id })
        taskDao.delete(taskId)
        return snapshot
    }

    suspend fun restore(deleted: DeletedTask): Long = save(deleted.draft, restoreWithId = true)

    suspend fun duplicate(taskId: Long): Long? {
        val d = getDetails(taskId) ?: return null
        return save(
            TaskDraft(
                task = d.task.copy(id = 0, title = "${d.task.title} (copy)", isCompleted = false, completedAt = null, sortOrder = 0),
                subtasks = d.subtasks.map { it.copy(id = 0, isDone = false) },
                tagNames = d.tags.map { it.name },
                dependsOnIds = d.dependsOn.map { it.id },
                attachments = d.attachments,
                reminders = d.reminders.map { it.copy(id = 0) },
            ),
        )
    }

    private suspend fun updateTask(taskId: Long, transform: (Task) -> Task) {
        val t = taskDao.get(taskId)?.toModel() ?: return
        taskDao.update(transform(t).copy(updatedAt = time.now()).toEntity())
        reminderSync.syncTask(taskId)
    }

    suspend fun move(taskIds: List<Long>, projectId: Long?, listId: Long?) =
        taskIds.forEach { id -> updateTask(id) { it.copy(projectId = projectId, listId = listId) } }

    suspend fun setPriority(taskIds: List<Long>, priority: Priority) =
        taskIds.forEach { id -> updateTask(id) { it.copy(priority = priority) } }

    /** Moves the task to [date], keeping the start→due span. */
    suspend fun reschedule(taskId: Long, date: LocalDate?) = updateTask(taskId) { t ->
        when {
            date == null -> t.copy(startDate = null, dueDate = null, startTime = null, dueTime = null)
            t.startDate != null && t.dueDate != null -> {
                val span = java.time.temporal.ChronoUnit.DAYS.between(t.startDate, t.dueDate)
                t.copy(startDate = date.minusDays(span), dueDate = date)
            }
            t.dueDate == null && t.startDate != null -> t.copy(startDate = date)
            else -> t.copy(dueDate = date)
        }
    }

    suspend fun reorder(orderedIds: List<Long>) {
        val now = time.nowMillis()
        db.withTransaction { orderedIds.forEachIndexed { i, id -> taskDao.setSortOrder(id, i.toLong() + 1, now) } }
    }

    suspend fun setSubtaskDone(subtaskId: Long, done: Boolean) = taskDao.setSubtaskDone(subtaskId, done)

    suspend fun addSubtask(taskId: Long, title: String) {
        if (title.isBlank()) return
        val pos = taskDao.getSubtasks(taskId).size
        taskDao.insertSubtask(SubtaskEntity(0, taskId, title.trim(), false, pos))
    }

    suspend fun addTag(taskId: Long, name: String) {
        val clean = name.trim().removePrefix("#")
        if (clean.isEmpty()) return
        tagDao.insertCrossRefs(listOf(TaskTagCrossRef(taskId, resolveTag(clean))))
    }

    suspend fun removeTag(taskId: Long, tagId: Long) = tagDao.removeCrossRef(taskId, tagId)

    suspend fun search(query: String): List<Long> = taskDao.search(query.trim()).map { it.id }

    companion object {
        fun buildItems(
            tasks: List<TaskEntity>,
            subtasks: List<SubtaskEntity>,
            deps: List<TaskDependencyEntity>,
            tags: List<TagEntity>,
            refs: List<TaskTagCrossRef>,
            projects: List<ProjectEntity>,
            lists: List<ListEntity>,
            reminders: List<ReminderEntity>,
            attachments: List<AttachmentEntity>,
        ): List<TaskItem> {
            val models = tasks.map { it.toModel() }
            val byId = models.associateBy { it.id }
            val subsByTask = subtasks.groupBy { it.taskId }
            val tagById = tags.associate { it.id to it.toModel() }
            val tagsByTask = refs.groupBy({ it.taskId }, { it.tagId })
            val projectById = projects.associate { it.id to it.toModel() }
            val listById = lists.associate { it.id to it.toModel() }
            val depsByTask = deps.groupBy({ it.taskId }, { it.dependsOnTaskId })
            val remindersByTask = reminders.filter { it.taskId != null && !it.isDismissed }.groupingBy { it.taskId!! }.eachCount()
            val attachmentsByTask = attachments.groupingBy { it.taskId }.eachCount()
            return models.map { t ->
                val subs = subsByTask[t.id].orEmpty()
                TaskItem(
                    task = t,
                    subtasksDone = subs.count { it.isDone },
                    subtasksTotal = subs.size,
                    tags = tagsByTask[t.id].orEmpty().mapNotNull { tagById[it] }.sortedBy { it.name.lowercase() },
                    project = t.projectId?.let { projectById[it] },
                    list = t.listId?.let { listById[it] },
                    blockedBy = depsByTask[t.id].orEmpty().mapNotNull { byId[it] }.filter { !it.isCompleted },
                    reminderCount = remindersByTask[t.id] ?: 0,
                    attachmentCount = attachmentsByTask[t.id] ?: 0,
                    incompleteSubtasks = subs.filter { !it.isDone }.map { it.toModel() },
                )
            }
        }
    }
}

/** Helper so callers can map subtask models. */
fun List<Subtask>.normalized(): List<Subtask> = filter { it.title.isNotBlank() }.mapIndexed { i, s -> s.copy(position = i) }
