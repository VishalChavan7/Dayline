package com.dayline.app.core.data.repository

import androidx.room.withTransaction
import com.dayline.app.core.common.ReminderSync
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.db.DaylineDatabase
import com.dayline.app.core.data.db.FocusSessionEntity
import com.dayline.app.core.data.db.HabitCompletionEntity
import com.dayline.app.core.data.db.toEntity
import com.dayline.app.core.data.db.toModel
import com.dayline.app.core.domain.FocusRecord
import com.dayline.app.core.domain.HabitStreakCalculator
import com.dayline.app.core.model.FocusSession
import com.dayline.app.core.model.Habit
import com.dayline.app.core.model.HabitCompletion
import com.dayline.app.core.model.Note
import com.dayline.app.core.model.Project
import com.dayline.app.core.model.Reminder
import com.dayline.app.core.model.ReminderType
import com.dayline.app.core.model.SearchResults
import com.dayline.app.core.model.Tag
import com.dayline.app.core.model.TaskList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProjectRepository @Inject constructor(
    private val db: DaylineDatabase,
    private val time: TimeProvider,
    private val reminderSync: ReminderSync,
) {
    private val dao = db.projectDao()

    val projects: Flow<List<Project>> = dao.observeAll().map { l -> l.map { it.toModel() } }
    val lists: Flow<List<TaskList>> = dao.observeLists().map { l -> l.map { it.toModel() } }

    suspend fun saveProject(project: Project): Long {
        require(project.name.isNotBlank()) { "Project name can't be empty" }
        if (project.startDate != null && project.deadline != null) {
            require(!project.deadline.isBefore(project.startDate)) { "Deadline can't be before the start date" }
        }
        val entity = project.copy(createdAt = if (project.id == 0L) time.now() else project.createdAt).toEntity()
        val rowId = dao.upsert(entity)
        return if (project.id != 0L) project.id else rowId
    }

    /** Deletes a project. Its tasks are either deleted or moved to the Inbox (the foreign key clears the link). */
    suspend fun deleteProject(id: Long, deleteTasks: Boolean) {
        if (deleteTasks) {
            val reminderIds = db.reminderDao().getAll().filter { r -> r.taskId != null && db.taskDao().get(r.taskId)?.projectId == id }.map { it.id }
            reminderSync.cancelReminders(reminderIds)
        }
        db.withTransaction {
            if (deleteTasks) dao.deleteTasksOfProject(id)
            dao.delete(id)
        }
    }

    suspend fun setArchived(project: Project, archived: Boolean) {
        dao.upsert(project.copy(isArchived = archived).toEntity())
    }

    suspend fun saveList(list: TaskList): Long {
        require(list.name.isNotBlank()) { "List name can't be empty" }
        val rowId = dao.upsertList(list.toEntity())
        return if (list.id != 0L) list.id else rowId
    }

    suspend fun deleteList(id: Long) = dao.deleteList(id)
}

@Singleton
class TagRepository @Inject constructor(db: DaylineDatabase) {
    private val dao = db.tagDao()
    val tags: Flow<List<Tag>> = dao.observeAll().map { l -> l.map { it.toModel() } }

    suspend fun rename(tag: Tag, newName: String) {
        val clean = newName.trim().removePrefix("#")
        require(clean.isNotEmpty()) { "Tag name can't be empty" }
        require(dao.findByName(clean)?.let { it.id == tag.id } ?: true) { "A tag with that name already exists" }
        dao.update(com.dayline.app.core.data.db.TagEntity(tag.id, clean, tag.color))
    }

    suspend fun delete(tagId: Long) = dao.delete(tagId)
}

@Singleton
class HabitRepository @Inject constructor(
    private val db: DaylineDatabase,
    private val time: TimeProvider,
    private val reminderSync: ReminderSync,
) {
    private val dao = db.habitDao()

    val habits: Flow<List<Habit>> = dao.observeAll().map { l -> l.map { it.toModel() } }
    val completions: Flow<List<HabitCompletion>> = dao.observeCompletions().map { l -> l.map { it.toModel() } }

    suspend fun save(habit: Habit): Long {
        require(habit.name.isNotBlank()) { "Habit name can't be empty" }
        if (habit.frequency == com.dayline.app.core.model.HabitFrequency.SPECIFIC_DAYS) {
            require(habit.weekdays.isNotEmpty()) { "Pick at least one day" }
        }
        val entity = habit.copy(createdAt = if (habit.id == 0L) time.now() else habit.createdAt).toEntity()
        val rowId = dao.upsert(entity)
        val id = if (habit.id != 0L) habit.id else rowId
        reminderSync.syncHabit(id)
        return id
    }

    suspend fun delete(id: Long) {
        reminderSync.cancelHabit(id)
        dao.delete(id)
    }

    suspend fun setArchived(habit: Habit, archived: Boolean) {
        dao.upsert(habit.copy(isArchived = archived).toEntity())
        reminderSync.syncHabit(habit.id)
    }

    /** Adds [delta] check-ins for a day (e.g. +1 glass of water). Counts never go below zero. */
    suspend fun checkIn(habitId: Long, date: LocalDate, delta: Int) {
        val current = dao.getCompletion(habitId, date)?.count ?: 0
        val next = (current + delta).coerceIn(0, 999)
        if (next == 0) dao.deleteCompletion(habitId, date) else dao.upsertCompletion(HabitCompletionEntity(habitId, date, next))
        reminderSync.syncHabit(habitId)
    }

    /** Sets the day's count to the habit's target (used by the notification action). */
    suspend fun markDone(habitId: Long, date: LocalDate) {
        val habit = dao.get(habitId)?.toModel() ?: return
        val current = dao.getCompletion(habitId, date)?.count ?: 0
        if (current < habit.targetPerDay) dao.upsertCompletion(HabitCompletionEntity(habitId, date, habit.targetPerDay.coerceAtLeast(1)))
        reminderSync.syncHabit(habitId)
    }

    /** One-tap toggle: marks the day fully done, or clears it if already done. */
    suspend fun toggle(habitId: Long, date: LocalDate) {
        val habit = dao.get(habitId)?.toModel() ?: return
        val current = dao.getCompletion(habitId, date)?.count ?: 0
        if (HabitStreakCalculator.isDone(habit, current)) dao.deleteCompletion(habitId, date)
        else dao.upsertCompletion(HabitCompletionEntity(habitId, date, habit.targetPerDay.coerceAtLeast(1)))
        reminderSync.syncHabit(habitId)
    }
}

@Singleton
class NoteRepository @Inject constructor(private val db: DaylineDatabase, private val time: TimeProvider) {
    private val dao = db.noteDao()
    /** Every note, including archived and trashed ones (the notes screen sorts them into views). */
    val allNotes: Flow<List<Note>> = dao.observeAll().map { l -> l.map { it.toModel() } }
    /** Notes that are in use: not archived, not in Trash. */
    val notes: Flow<List<Note>> = allNotes.map { l -> l.filter { it.archivedAt == null && it.trashedAt == null } }

    suspend fun get(id: Long): Note? = dao.get(id)?.toModel()

    suspend fun save(note: Note): Long {
        require(note.title.isNotBlank() || note.body.isNotBlank()) { "Note is empty" }
        val now = time.now()
        // Like Keep, a note may have no title; lists and previews fall back to its text.
        val entity = note.copy(
            createdAt = if (note.id == 0L) now else note.createdAt,
            updatedAt = now,
        ).toEntity()
        val rowId = dao.upsert(entity)
        return if (note.id != 0L) note.id else rowId
    }

    /** Deletes for good. Notes normally go to Trash first ([trash]). */
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)
    suspend fun archive(id: Long) = time.now().let { dao.setArchived(id, it, it) }
    suspend fun unarchive(id: Long) = dao.setArchived(id, null, time.now())
    suspend fun trash(id: Long) = time.now().let { dao.setTrashed(id, it, it) }
    suspend fun restoreFromTrash(id: Long) = dao.setTrashed(id, null, time.now())
    suspend fun emptyTrash(): Int = dao.emptyTrash()
    suspend fun setLocked(id: Long, locked: Boolean) = dao.setLocked(id, locked, time.now())
    suspend fun restore(note: Note) { dao.upsert(note.toEntity()) }

    /** Renames a label on every note that has it (notes keep their "edited" time). */
    suspend fun renameLabel(old: String, new: String) {
        val from = old.trim().lowercase()
        val to = new.trim().lowercase()
        if (from.isEmpty() || to.isEmpty() || from == to) return
        notes.first().forEach { n ->
            val labels = n.category.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (from in labels) {
                val renamed = labels.map { if (it == from) to else it }.distinct().joinToString(",")
                dao.upsert(n.copy(category = renamed).toEntity())
            }
        }
    }
}

@Singleton
class ReminderRepository @Inject constructor(private val db: DaylineDatabase, private val reminderSync: ReminderSync) {
    private val dao = db.reminderDao()
    val reminders: Flow<List<Reminder>> = dao.observeAll().map { l -> l.map { it.toModel() } }

    /** Standalone reminders (not tied to a task), shown on the dashboard when pinned. */
    val customReminders: Flow<List<Reminder>> = reminders.map { l -> l.filter { it.type == ReminderType.CUSTOM && it.taskId == null } }

    suspend fun saveCustom(reminder: Reminder): Long {
        require(reminder.title.isNotBlank()) { "Reminder needs a title" }
        requireNotNull(reminder.at) { "Pick a date and time" }
        val rowId = dao.upsert(reminder.copy(type = ReminderType.CUSTOM).toEntity())
        val id = if (reminder.id != 0L) reminder.id else rowId
        reminderSync.syncReminder(id)
        return id
    }

    suspend fun dismiss(id: Long) {
        reminderSync.cancelReminders(listOf(id))
        dao.dismiss(id)
    }

    suspend fun delete(id: Long) {
        reminderSync.cancelReminders(listOf(id))
        dao.delete(id)
    }
}

@Singleton
class FocusRepository @Inject constructor(db: DaylineDatabase) {
    private val dao = db.focusDao()
    val sessions: Flow<List<FocusSession>> = dao.observeAll().map { l -> l.map { it.toModel() } }

    suspend fun record(r: FocusRecord): Long = dao.insert(
        FocusSessionEntity(
            startedAt = Instant.ofEpochMilli(r.startedAtMillis),
            endedAt = Instant.ofEpochMilli(r.endedAtMillis),
            focusedMillis = r.focusedMillis,
            plannedMillis = r.plannedMillis,
            taskId = r.taskId,
            projectId = r.projectId,
            completed = r.completed,
        ),
    )

    suspend fun delete(session: FocusSession) = dao.delete(session.toEntity())
}

@Singleton
class SearchRepository @Inject constructor(private val db: DaylineDatabase, private val tasks: TaskRepository) {
    suspend fun search(query: String): SearchResults {
        val q = query.trim()
        if (q.length < 2) return SearchResults(q)
        val taskIds = tasks.search(q).toSet()
        val tagMatches = db.tagDao().search(q).map { it.toModel() }
        val tagIds = tagMatches.map { it.id }.toSet()
        val items = tasks.items.first().filter { it.id in taskIds || it.tags.any { t -> t.id in tagIds } }
            .sortedWith(compareBy({ it.task.isCompleted }, { it.task.dueDate ?: LocalDate.MAX }))
        return SearchResults(
            query = q,
            tasks = items,
            projects = db.projectDao().search(q).map { it.toModel() },
            lists = db.projectDao().searchLists(q).map { it.toModel() },
            tags = tagMatches,
            // Match what you can read, not the formatting markup stored with the note.
            notes = db.noteDao().observeAll().first().map { it.toModel() }.filter { n ->
                (n.title + "\n" + com.dayline.app.core.domain.NoteCodec.plainText(n.body) + "\n" + n.category.orEmpty()).contains(q, ignoreCase = true)
            },
        )
    }
}
