package com.dayline.app.core.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.dayline.app.core.common.ReminderSync
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.db.AttachmentEntity
import com.dayline.app.core.data.db.Converters
import com.dayline.app.core.data.db.DaylineDatabase
import com.dayline.app.core.data.db.FocusSessionEntity
import com.dayline.app.core.data.db.HabitCompletionEntity
import com.dayline.app.core.data.db.HabitEntity
import com.dayline.app.core.data.db.ListEntity
import com.dayline.app.core.data.db.NoteEntity
import com.dayline.app.core.data.db.ProjectEntity
import com.dayline.app.core.data.db.ReminderEntity
import com.dayline.app.core.data.db.SubtaskEntity
import com.dayline.app.core.data.db.TagEntity
import com.dayline.app.core.data.db.TaskDependencyEntity
import com.dayline.app.core.data.db.TaskEntity
import com.dayline.app.core.data.db.TaskTagCrossRef
import com.dayline.app.core.data.prefs.SettingsRepository
import com.dayline.app.core.model.AttachmentKind
import com.dayline.app.core.model.HabitFrequency
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.RecurrenceFrequency
import com.dayline.app.core.model.ReminderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

data class BackupSummary(val tasks: Int, val projects: Int, val habits: Int, val notes: Int, val focusSessions: Int)

class BackupFormatException(message: String) : Exception(message)

/**
 * Exports every table plus preferences to a single JSON document and restores it.
 * Restore validates the whole file before touching existing data, and keeps a safety copy of the
 * current data in app storage first.
 */
@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: DaylineDatabase,
    private val settings: SettingsRepository,
    private val reminderSync: ReminderSync,
    private val time: TimeProvider,
) {
    private val conv = Converters()

    suspend fun exportJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("app", APP_ID)
        root.put("formatVersion", FORMAT_VERSION)
        root.put("exportedAt", time.now().toString())
        root.put("projects", arr(db.projectDao().getAll()) { e ->
            obj("id" to e.id, "name" to e.name, "description" to e.description, "color" to e.color, "icon" to e.icon,
                "startDate" to conv.localDateToLong(e.startDate), "deadline" to conv.localDateToLong(e.deadline),
                "isArchived" to e.isArchived, "sortOrder" to e.sortOrder, "createdAt" to e.createdAt.toEpochMilli())
        })
        root.put("lists", arr(db.projectDao().getLists()) { e ->
            obj("id" to e.id, "name" to e.name, "color" to e.color, "icon" to e.icon, "projectId" to e.projectId, "sortOrder" to e.sortOrder)
        })
        root.put("tasks", arr(db.taskDao().getAll()) { e ->
            obj("id" to e.id, "title" to e.title, "description" to e.description, "notes" to e.notes,
                "startDate" to conv.localDateToLong(e.startDate), "startTime" to conv.localTimeToInt(e.startTime),
                "dueDate" to conv.localDateToLong(e.dueDate), "dueTime" to conv.localTimeToInt(e.dueTime),
                "priority" to e.priority.name, "category" to e.category, "projectId" to e.projectId, "listId" to e.listId,
                "recurrenceFrequency" to e.recurrenceFrequency?.name, "recurrenceInterval" to e.recurrenceInterval,
                "recurrenceWeekdays" to conv.weekdaysToInt(e.recurrenceWeekdays), "recurrenceFromCompletion" to e.recurrenceFromCompletion,
                "recurrenceEndDate" to conv.localDateToLong(e.recurrenceEndDate), "isCompleted" to e.isCompleted,
                "completedAt" to e.completedAt?.toEpochMilli(), "progress" to e.progress, "sortOrder" to e.sortOrder,
                "createdAt" to e.createdAt.toEpochMilli(), "updatedAt" to e.updatedAt.toEpochMilli())
        })
        root.put("subtasks", arr(db.taskDao().getAllSubtasks()) { e ->
            obj("id" to e.id, "taskId" to e.taskId, "title" to e.title, "isDone" to e.isDone, "position" to e.position)
        })
        root.put("tags", arr(db.tagDao().getAll()) { e -> obj("id" to e.id, "name" to e.name, "color" to e.color) })
        root.put("taskTags", arr(db.tagDao().getCrossRefs()) { e -> obj("taskId" to e.taskId, "tagId" to e.tagId) })
        root.put("dependencies", arr(db.taskDao().getDependencies()) { e -> obj("taskId" to e.taskId, "dependsOnTaskId" to e.dependsOnTaskId) })
        root.put("attachments", arr(db.taskDao().getAllAttachments()) { e ->
            obj("taskId" to e.taskId, "kind" to e.kind.name, "uri" to e.uri, "displayName" to e.displayName,
                "mimeType" to e.mimeType, "sizeBytes" to e.sizeBytes, "addedAt" to e.addedAt.toEpochMilli())
        })
        root.put("reminders", arr(db.reminderDao().getAll()) { e ->
            obj("id" to e.id, "taskId" to e.taskId, "type" to e.type.name, "offsetMinutes" to e.offsetMinutes,
                "at" to e.at?.toString(), "title" to e.title, "isPinned" to e.isPinned, "isDismissed" to e.isDismissed)
        })
        root.put("habits", arr(db.habitDao().getAll()) { e ->
            obj("id" to e.id, "name" to e.name, "description" to e.description, "icon" to e.icon, "color" to e.color,
                "frequency" to e.frequency.name, "weekdays" to conv.weekdaysToInt(e.weekdays), "timesPerWeek" to e.timesPerWeek,
                "targetPerDay" to e.targetPerDay, "startDate" to e.startDate.toEpochDay(), "reminderTime" to conv.localTimeToInt(e.reminderTime),
                "isArchived" to e.isArchived, "sortOrder" to e.sortOrder, "createdAt" to e.createdAt.toEpochMilli())
        })
        root.put("habitCompletions", arr(db.habitDao().getCompletions()) { e ->
            obj("habitId" to e.habitId, "date" to e.date.toEpochDay(), "count" to e.count)
        })
        root.put("notes", arr(db.noteDao().getAll()) { e ->
            obj("id" to e.id, "title" to e.title, "body" to e.body, "category" to e.category, "isPinned" to e.isPinned,
                "taskId" to e.taskId, "projectId" to e.projectId, "color" to e.color,
                "createdAt" to e.createdAt.toEpochMilli(), "updatedAt" to e.updatedAt.toEpochMilli())
        })
        root.put("focusSessions", arr(db.focusDao().getAll()) { e ->
            obj("startedAt" to e.startedAt.toEpochMilli(), "endedAt" to e.endedAt.toEpochMilli(), "focusedMillis" to e.focusedMillis,
                "plannedMillis" to e.plannedMillis, "taskId" to e.taskId, "projectId" to e.projectId, "completed" to e.completed)
        })
        val prefs = JSONObject()
        settings.exportPairs().forEach { (k, v) -> prefs.put(k, v) }
        root.put("settings", prefs)
        root.toString(2)
    }

    /** Parses and validates a backup without changing anything. */
    fun inspect(json: String): BackupSummary {
        val root = parseRoot(json)
        return BackupSummary(
            tasks = root.optJSONArray("tasks")?.length() ?: 0,
            projects = root.optJSONArray("projects")?.length() ?: 0,
            habits = root.optJSONArray("habits")?.length() ?: 0,
            notes = root.optJSONArray("notes")?.length() ?: 0,
            focusSessions = root.optJSONArray("focusSessions")?.length() ?: 0,
        )
    }

    private fun parseRoot(json: String): JSONObject {
        val root = try { JSONObject(json) } catch (e: Exception) { throw BackupFormatException("This file isn't a valid backup.") }
        if (root.optString("app") != APP_ID) throw BackupFormatException("This file wasn't created by Dayline.")
        if (root.optInt("formatVersion", 0) > FORMAT_VERSION) throw BackupFormatException("This backup is from a newer version of Dayline.")
        return root
    }

    /** Replaces all data with the backup's contents. */
    suspend fun importJson(json: String): BackupSummary = withContext(Dispatchers.IO) {
        val root = parseRoot(json)
        // Parse everything first so a malformed file can't leave the database half-restored.
        val data = try { parse(root) } catch (e: BackupFormatException) { throw e } catch (e: Exception) {
            throw BackupFormatException("The backup is damaged: ${e.message}")
        }
        // Safety copy of the current data.
        runCatching { File(context.filesDir, "pre-restore-backup.json").writeText(exportJson()) }

        db.withTransaction {
            db.clearAllTablesInTransaction()
            data.projects.forEach { db.projectDao().upsert(it) }
            data.lists.forEach { db.projectDao().upsertList(it) }
            data.tasks.forEach { db.taskDao().insert(it) }
            db.taskDao().upsertSubtasks(data.subtasks)
            data.tags.forEach { db.tagDao().insert(it) }
            db.tagDao().insertCrossRefs(data.taskTags)
            db.taskDao().insertDependencies(data.dependencies)
            db.taskDao().insertAttachments(data.attachments)
            db.reminderDao().insertAll(data.reminders)
            data.habits.forEach { db.habitDao().upsert(it) }
            data.habitCompletions.forEach { db.habitDao().upsertCompletion(it) }
            data.notes.forEach { db.noteDao().upsert(it) }
            data.focus.forEach { db.focusDao().insert(it) }
        }
        root.optJSONObject("settings")?.let { s -> settings.importPairs(s.keys().asSequence().associateWith { k -> s.opt(k) }) }
        reminderSync.syncAll()
        BackupSummary(data.tasks.size, data.projects.size, data.habits.size, data.notes.size, data.focus.size)
    }

    private class Parsed(
        val projects: List<ProjectEntity>, val lists: List<ListEntity>, val tasks: List<TaskEntity>, val subtasks: List<SubtaskEntity>,
        val tags: List<TagEntity>, val taskTags: List<TaskTagCrossRef>, val dependencies: List<TaskDependencyEntity>,
        val attachments: List<AttachmentEntity>, val reminders: List<ReminderEntity>, val habits: List<HabitEntity>,
        val habitCompletions: List<HabitCompletionEntity>, val notes: List<NoteEntity>, val focus: List<FocusSessionEntity>,
    )

    private fun parse(root: JSONObject): Parsed {
        fun date(o: JSONObject, k: String): LocalDate? = o.optLongOrNull(k)?.let(LocalDate::ofEpochDay)
        fun time(o: JSONObject, k: String): LocalTime? = o.optLongOrNull(k)?.let { LocalTime.ofSecondOfDay(it) }
        fun inst(o: JSONObject, k: String): Instant? = o.optLongOrNull(k)?.let(Instant::ofEpochMilli)
        val projects = list(root, "projects") { o ->
            ProjectEntity(o.getLong("id"), o.getString("name"), o.optString("description"), o.getInt("color"), o.optString("icon", "folder"),
                date(o, "startDate"), date(o, "deadline"), o.optBoolean("isArchived"), o.optInt("sortOrder"), inst(o, "createdAt") ?: Instant.EPOCH)
        }
        val projectIds = projects.map { it.id }.toSet()
        val lists = list(root, "lists") { o ->
            ListEntity(o.getLong("id"), o.getString("name"), o.getInt("color"), o.optString("icon", "list"),
                o.optLongOrNull("projectId")?.takeIf { it in projectIds }, o.optInt("sortOrder"))
        }
        val listIds = lists.map { it.id }.toSet()
        val tasks = list(root, "tasks") { o ->
            TaskEntity(
                id = o.getLong("id"), title = o.getString("title"), description = o.optString("description"), notes = o.optString("notes"),
                startDate = date(o, "startDate"), startTime = time(o, "startTime"), dueDate = date(o, "dueDate"), dueTime = time(o, "dueTime"),
                priority = Priority.valueOf(o.optString("priority", "MEDIUM")), category = o.optStringOrNull("category"),
                projectId = o.optLongOrNull("projectId")?.takeIf { it in projectIds }, listId = o.optLongOrNull("listId")?.takeIf { it in listIds },
                recurrenceFrequency = o.optStringOrNull("recurrenceFrequency")?.let { RecurrenceFrequency.valueOf(it) },
                recurrenceInterval = o.optInt("recurrenceInterval", 1), recurrenceWeekdays = conv.intToWeekdays(o.optInt("recurrenceWeekdays")),
                recurrenceFromCompletion = o.optBoolean("recurrenceFromCompletion"), recurrenceEndDate = date(o, "recurrenceEndDate"),
                isCompleted = o.optBoolean("isCompleted"), completedAt = inst(o, "completedAt"), progress = o.optInt("progress"),
                sortOrder = o.optLong("sortOrder"), createdAt = inst(o, "createdAt") ?: Instant.EPOCH, updatedAt = inst(o, "updatedAt") ?: Instant.EPOCH,
            )
        }
        val taskIds = tasks.map { it.id }.toSet()
        val subtasks = list(root, "subtasks") { o ->
            SubtaskEntity(o.getLong("id"), o.getLong("taskId"), o.getString("title"), o.optBoolean("isDone"), o.optInt("position"))
        }.filter { it.taskId in taskIds }
        val tags = list(root, "tags") { o -> TagEntity(o.getLong("id"), o.getString("name"), o.optInt("color")) }
        val tagIds = tags.map { it.id }.toSet()
        val taskTags = list(root, "taskTags") { o -> TaskTagCrossRef(o.getLong("taskId"), o.getLong("tagId")) }
            .filter { it.taskId in taskIds && it.tagId in tagIds }
        val deps = list(root, "dependencies") { o -> TaskDependencyEntity(o.getLong("taskId"), o.getLong("dependsOnTaskId")) }
            .filter { it.taskId in taskIds && it.dependsOnTaskId in taskIds }
        val attachments = list(root, "attachments") { o ->
            AttachmentEntity(0, o.getLong("taskId"), AttachmentKind.valueOf(o.getString("kind")), o.getString("uri"), o.getString("displayName"),
                o.optStringOrNull("mimeType"), o.optLongOrNull("sizeBytes"), inst(o, "addedAt") ?: Instant.EPOCH)
        }.filter { it.taskId in taskIds }
        val reminders = list(root, "reminders") { o ->
            ReminderEntity(o.getLong("id"), o.optLongOrNull("taskId"), ReminderType.valueOf(o.getString("type")), o.optInt("offsetMinutes"),
                o.optStringOrNull("at")?.let(LocalDateTime::parse), o.optString("title"), o.optBoolean("isPinned"), o.optBoolean("isDismissed"))
        }.filter { it.taskId == null || it.taskId in taskIds }
        val habits = list(root, "habits") { o ->
            HabitEntity(o.getLong("id"), o.getString("name"), o.optString("description"), o.optString("icon", "spa"), o.getInt("color"),
                HabitFrequency.valueOf(o.optString("frequency", "DAILY")), conv.intToWeekdays(o.optInt("weekdays")), o.optInt("timesPerWeek", 3),
                o.optInt("targetPerDay", 1), LocalDate.ofEpochDay(o.getLong("startDate")), time(o, "reminderTime"), o.optBoolean("isArchived"),
                o.optInt("sortOrder"), inst(o, "createdAt") ?: Instant.EPOCH)
        }
        val habitIds = habits.map { it.id }.toSet()
        val completions = list(root, "habitCompletions") { o ->
            HabitCompletionEntity(o.getLong("habitId"), LocalDate.ofEpochDay(o.getLong("date")), o.getInt("count"))
        }.filter { it.habitId in habitIds }
        val notes = list(root, "notes") { o ->
            NoteEntity(o.getLong("id"), o.optString("title"), o.optString("body"), o.optStringOrNull("category"), o.optBoolean("isPinned"),
                o.optLongOrNull("taskId")?.takeIf { it in taskIds }, o.optLongOrNull("projectId")?.takeIf { it in projectIds }, o.optInt("color"),
                inst(o, "createdAt") ?: Instant.EPOCH, inst(o, "updatedAt") ?: Instant.EPOCH)
        }
        val focus = list(root, "focusSessions") { o ->
            FocusSessionEntity(0, Instant.ofEpochMilli(o.getLong("startedAt")), Instant.ofEpochMilli(o.getLong("endedAt")), o.getLong("focusedMillis"),
                o.optLong("plannedMillis"), o.optLongOrNull("taskId")?.takeIf { it in taskIds }, o.optLongOrNull("projectId")?.takeIf { it in projectIds },
                o.optBoolean("completed"))
        }
        return Parsed(projects, lists, tasks, subtasks, tags, taskTags, deps, attachments, reminders, habits, completions, notes, focus)
    }

    private fun <T> arr(items: List<T>, map: (T) -> JSONObject): JSONArray = JSONArray().apply { items.forEach { put(map(it)) } }

    private fun obj(vararg pairs: Pair<String, Any?>): JSONObject = JSONObject().apply {
        pairs.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) }
    }

    private fun <T> list(root: JSONObject, key: String, map: (JSONObject) -> T): List<T> {
        val a = root.optJSONArray(key) ?: return emptyList()
        return (0 until a.length()).map { map(a.getJSONObject(it)) }
    }

    companion object {
        const val APP_ID = "dayline"
        const val FORMAT_VERSION = 1
    }
}

private fun JSONObject.optLongOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key)
private fun JSONObject.optStringOrNull(key: String): String? = if (!has(key) || isNull(key)) null else optString(key)

/** Deletes every row, children first, inside the caller's transaction. */
private suspend fun DaylineDatabase.clearAllTablesInTransaction() {
    val sql = listOf(
        "habit_completions", "task_tags", "task_dependencies", "attachments", "reminders", "subtasks",
        "focus_sessions", "notes", "tasks", "lists", "projects", "tags", "habits",
    )
    val w = openHelper.writableDatabase
    sql.forEach { w.execSQL("DELETE FROM $it") }
}
