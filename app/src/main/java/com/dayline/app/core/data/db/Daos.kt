package com.dayline.app.core.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks ORDER BY sortOrder, id")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observe(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Query("SELECT * FROM tasks WHERE id IN (:ids)")
    suspend fun getMany(ids: List<Long>): List<TaskEntity>

    @Insert
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE tasks SET sortOrder = :order, updatedAt = :now WHERE id = :id")
    suspend fun setSortOrder(id: Long, order: Long, now: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM tasks")
    suspend fun maxSortOrder(): Long

    @Query("SELECT DISTINCT category FROM tasks WHERE category IS NOT NULL AND category != '' ORDER BY category")
    fun observeCategories(): Flow<List<String>>

    @Query(
        "SELECT * FROM tasks WHERE title LIKE '%' || :q || '%' OR description LIKE '%' || :q || '%' " +
            "OR notes LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%' ORDER BY isCompleted, dueDate IS NULL, dueDate LIMIT 100",
    )
    suspend fun search(q: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE dueDate BETWEEN :from AND :to OR startDate BETWEEN :from AND :to")
    fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<TaskEntity>>

    // Subtasks
    @Query("SELECT * FROM subtasks ORDER BY taskId, position")
    fun observeAllSubtasks(): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM subtasks ORDER BY taskId, position")
    suspend fun getAllSubtasks(): List<SubtaskEntity>

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position")
    fun observeSubtasks(taskId: Long): Flow<List<SubtaskEntity>>

    @Query("SELECT * FROM subtasks WHERE taskId = :taskId ORDER BY position")
    suspend fun getSubtasks(taskId: Long): List<SubtaskEntity>

    @Upsert
    suspend fun upsertSubtasks(items: List<SubtaskEntity>)

    @Insert
    suspend fun insertSubtask(item: SubtaskEntity): Long

    @Query("UPDATE subtasks SET isDone = :done WHERE id = :id")
    suspend fun setSubtaskDone(id: Long, done: Boolean)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId AND id NOT IN (:keep)")
    suspend fun deleteSubtasksExcept(taskId: Long, keep: List<Long>)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    suspend fun deleteSubtasks(taskId: Long)

    // Dependencies
    @Query("SELECT * FROM task_dependencies")
    fun observeDependencies(): Flow<List<TaskDependencyEntity>>

    @Query("SELECT * FROM task_dependencies")
    suspend fun getDependencies(): List<TaskDependencyEntity>

    @Query("SELECT * FROM task_dependencies WHERE taskId = :taskId")
    suspend fun getDependenciesOf(taskId: Long): List<TaskDependencyEntity>

    @Query("DELETE FROM task_dependencies WHERE taskId = :taskId")
    suspend fun clearDependencies(taskId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDependencies(items: List<TaskDependencyEntity>)

    // Attachments
    @Query("SELECT * FROM attachments ORDER BY addedAt")
    fun observeAttachments(): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments ORDER BY addedAt")
    suspend fun getAllAttachments(): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE taskId = :taskId ORDER BY addedAt")
    suspend fun getAttachments(taskId: Long): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE taskId = :taskId")
    suspend fun clearAttachments(taskId: Long)

    @Insert
    suspend fun insertAttachments(items: List<AttachmentEntity>)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM task_tags")
    fun observeCrossRefs(): Flow<List<TaskTagCrossRef>>

    @Query("SELECT * FROM task_tags")
    suspend fun getCrossRefs(): List<TaskTagCrossRef>

    @Query("DELETE FROM task_tags WHERE taskId = :taskId")
    suspend fun clearForTask(taskId: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(items: List<TaskTagCrossRef>)

    @Query("DELETE FROM task_tags WHERE taskId = :taskId AND tagId = :tagId")
    suspend fun removeCrossRef(taskId: Long, tagId: Long)

    @Query("SELECT * FROM tags WHERE name LIKE '%' || :q || '%' ORDER BY name LIMIT 30")
    suspend fun search(q: String): List<TagEntity>
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY isArchived, sortOrder, name COLLATE NOCASE")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects")
    suspend fun getAll(): List<ProjectEntity>

    @Query("SELECT * FROM projects WHERE id = :id")
    fun observe(id: Long): Flow<ProjectEntity?>

    @Upsert
    suspend fun upsert(project: ProjectEntity): Long

    @Query("DELETE FROM projects WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM lists ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeLists(): Flow<List<ListEntity>>

    @Query("SELECT * FROM lists")
    suspend fun getLists(): List<ListEntity>

    @Upsert
    suspend fun upsertList(list: ListEntity): Long

    @Query("DELETE FROM lists WHERE id = :id")
    suspend fun deleteList(id: Long)

    @Query("DELETE FROM tasks WHERE projectId = :projectId")
    suspend fun deleteTasksOfProject(projectId: Long)

    @Query("SELECT * FROM projects WHERE name LIKE '%' || :q || '%' OR description LIKE '%' || :q || '%' LIMIT 30")
    suspend fun search(q: String): List<ProjectEntity>

    @Query("SELECT * FROM lists WHERE name LIKE '%' || :q || '%' LIMIT 30")
    suspend fun searchLists(q: String): List<ListEntity>
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY isArchived, sortOrder, id")
    fun observeAll(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits")
    suspend fun getAll(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id")
    suspend fun get(id: Long): HabitEntity?

    @Upsert
    suspend fun upsert(habit: HabitEntity): Long

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM habit_completions")
    fun observeCompletions(): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions")
    suspend fun getCompletions(): List<HabitCompletionEntity>

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND date = :date")
    suspend fun getCompletion(habitId: Long, date: LocalDate): HabitCompletionEntity?

    @Upsert
    suspend fun upsertCompletion(item: HabitCompletionEntity)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND date = :date")
    suspend fun deleteCompletion(habitId: Long, date: LocalDate)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Upsert
    suspend fun upsert(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE notes SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    @Query("SELECT * FROM notes WHERE title LIKE '%' || :q || '%' OR body LIKE '%' || :q || '%' OR category LIKE '%' || :q || '%' ORDER BY updatedAt DESC LIMIT 50")
    suspend fun search(q: String): List<NoteEntity>
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun get(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE taskId = :taskId")
    suspend fun forTask(taskId: Long): List<ReminderEntity>

    @Upsert
    suspend fun upsert(reminder: ReminderEntity): Long

    @Insert
    suspend fun insertAll(items: List<ReminderEntity>): List<Long>

    @Query("DELETE FROM reminders WHERE taskId = :taskId")
    suspend fun clearForTask(taskId: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE reminders SET isDismissed = 1 WHERE id = :id")
    suspend fun dismiss(id: Long)
}

@Dao
interface FocusDao {
    @Query("SELECT * FROM focus_sessions ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions")
    suspend fun getAll(): List<FocusSessionEntity>

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Delete
    suspend fun delete(session: FocusSessionEntity)
}
