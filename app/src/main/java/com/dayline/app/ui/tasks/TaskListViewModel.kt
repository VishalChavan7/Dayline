package com.dayline.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayline.app.core.common.AppClock
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.prefs.SettingsRepository
import com.dayline.app.core.data.repository.DeletedTask
import com.dayline.app.core.data.repository.ProjectRepository
import com.dayline.app.core.data.repository.TagRepository
import com.dayline.app.core.data.repository.TaskRepository
import com.dayline.app.core.domain.DateRules
import com.dayline.app.core.domain.TaskGrouping
import com.dayline.app.core.domain.TaskSort
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.Project
import com.dayline.app.core.model.Tag
import com.dayline.app.core.model.TaskItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

enum class TaskListMode(val title: String) {
    INBOX("Inbox"),
    UPCOMING("Upcoming"),
    ALL("All tasks"),
    COMPLETED("Completed"),
}

enum class StatusFilter(val label: String) { OPEN("Open"), COMPLETED("Completed"), ANY("All") }

enum class DateFilter(val label: String) { ANY("Any date"), TODAY("Today"), OVERDUE("Overdue"), THIS_WEEK("This week"), NO_DATE("No date") }

data class TaskFilter(
    val status: StatusFilter = StatusFilter.OPEN,
    val priorities: Set<Priority> = emptySet(),
    val date: DateFilter = DateFilter.ANY,
    val projectId: Long? = null,
    val category: String? = null,
    val tagIds: Set<Long> = emptySet(),
    val query: String = "",
) {
    val activeCount: Int
        get() = listOf(status != StatusFilter.OPEN, priorities.isNotEmpty(), date != DateFilter.ANY, projectId != null, category != null, tagIds.isNotEmpty())
            .count { it }
}

data class TaskSection(val title: String?, val items: List<TaskItem>, val isWarning: Boolean = false, val date: LocalDate? = null)

data class TaskListUiState(
    val loading: Boolean = true,
    val mode: TaskListMode = TaskListMode.ALL,
    val today: LocalDate = LocalDate.now(),
    val sections: List<TaskSection> = emptyList(),
    val filter: TaskFilter = TaskFilter(),
    val sort: TaskSort = TaskSort.DUE_DATE,
    val projects: List<Project> = emptyList(),
    val categories: List<String> = emptyList(),
    val tags: List<Tag> = emptyList(),
) {
    val isEmpty: Boolean get() = sections.all { it.items.isEmpty() }
    val count: Int get() = sections.sumOf { it.items.size }
}

private data class ListInputs(val items: List<TaskItem>, val projects: List<Project>, val categories: List<String>, val tags: List<Tag>)

@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val tasks: TaskRepository,
    projects: ProjectRepository,
    tags: TagRepository,
    settings: SettingsRepository,
    private val time: TimeProvider,
    clock: AppClock,
) : ViewModel() {
    private val mode = MutableStateFlow(TaskListMode.ALL)
    private val filter = MutableStateFlow(TaskFilter())
    private val sort = MutableStateFlow(TaskSort.DUE_DATE)

    private val inputs = combine(tasks.items, projects.projects, tasks.categories, tags.tags) { a, b, c, d -> ListInputs(a, b, c, d) }

    val state: StateFlow<TaskListUiState> = combine(inputs, mode, filter, sort, combine(clock.today, settings.settings) { t, s -> t to s.weekStart }) { inp, m, f, s, (today, ws) ->
        TaskListUiState(
            loading = false,
            mode = m,
            today = today,
            sections = buildSections(inp.items, m, f, s, today, ws, time.zone()),
            filter = f,
            sort = s,
            projects = inp.projects,
            categories = inp.categories,
            tags = inp.tags,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskListUiState())

    fun setMode(m: TaskListMode) {
        if (mode.value == m && state.value.mode == m) return
        mode.value = m
        filter.value = TaskFilter(status = if (m == TaskListMode.COMPLETED) StatusFilter.COMPLETED else StatusFilter.OPEN)
        sort.value = if (m == TaskListMode.INBOX) TaskSort.MANUAL else TaskSort.DUE_DATE
    }

    fun updateFilter(transform: (TaskFilter) -> TaskFilter) = filter.update(transform)
    fun clearFilters() = filter.update { TaskFilter(status = if (mode.value == TaskListMode.COMPLETED) StatusFilter.COMPLETED else StatusFilter.OPEN, query = it.query) }
    fun setSort(s: TaskSort) { sort.value = s }

    fun toggle(id: Long, done: Boolean) = viewModelScope.launch { tasks.setCompleted(id, done) }
    suspend fun delete(id: Long): DeletedTask? = tasks.delete(id)
    fun restore(d: DeletedTask) = viewModelScope.launch { tasks.restore(d) }
    fun reorder(ids: List<Long>) = viewModelScope.launch { tasks.reorder(ids) }

    companion object {
        fun applyFilter(items: List<TaskItem>, f: TaskFilter, today: LocalDate, weekStart: DayOfWeek): List<TaskItem> = items.filter { i ->
            val t = i.task
            val statusOk = when (f.status) {
                StatusFilter.OPEN -> !t.isCompleted
                StatusFilter.COMPLETED -> t.isCompleted
                StatusFilter.ANY -> true
            }
            val d = t.scheduledDate
            val dateOk = when (f.date) {
                DateFilter.ANY -> true
                DateFilter.TODAY -> d == today
                DateFilter.OVERDUE -> t.isOverdue(today)
                DateFilter.THIS_WEEK -> d != null && !d.isBefore(DateRules.startOfWeek(today, weekStart)) && !d.isAfter(DateRules.endOfWeek(today, weekStart))
                DateFilter.NO_DATE -> d == null
            }
            val q = f.query.trim().lowercase()
            val queryOk = q.isEmpty() || t.title.lowercase().contains(q) || t.description.lowercase().contains(q) ||
                i.tags.any { it.name.lowercase().contains(q) } || (t.category?.lowercase()?.contains(q) == true)
            statusOk && dateOk && queryOk &&
                (f.priorities.isEmpty() || t.priority in f.priorities) &&
                (f.projectId == null || t.projectId == f.projectId) &&
                (f.category == null || t.category == f.category) &&
                (f.tagIds.isEmpty() || i.tags.any { it.id in f.tagIds })
        }

        fun sortItems(items: List<TaskItem>, sort: TaskSort): List<TaskItem> {
            val byId = items.associateBy { it.id }
            return TaskGrouping.sort(items.map { it.task }, sort).mapNotNull { byId[it.id] }
        }

        fun buildSections(
            all: List<TaskItem>, mode: TaskListMode, f: TaskFilter, sort: TaskSort, today: LocalDate, weekStart: DayOfWeek, zone: ZoneId,
        ): List<TaskSection> = when (mode) {
            TaskListMode.INBOX -> {
                val inbox = all.filter { !it.task.isCompleted && it.task.projectId == null && it.task.listId == null && it.task.scheduledDate == null }
                listOf(TaskSection(null, sortItems(applyFilter(inbox, f.copy(status = StatusFilter.OPEN), today, weekStart), sort)))
            }
            TaskListMode.UPCOMING -> {
                val filtered = applyFilter(all, f.copy(status = StatusFilter.OPEN), today, weekStart)
                val byId = filtered.associateBy { it.id }
                TaskGrouping.groupUpcoming(filtered.map { it.task }, today, weekStart).map { (bucket, list) ->
                    TaskSection(bucket.label, list.mapNotNull { byId[it.id] })
                }
            }
            TaskListMode.ALL -> {
                val filtered = applyFilter(all, f, today, weekStart)
                if (sort == TaskSort.DUE_DATE) {
                    val sorted = sortItems(filtered, sort)
                    val overdue = sorted.filter { it.task.isOverdue(today) }
                    val dated = sorted.filter { !it.task.isOverdue(today) && it.task.scheduledDate != null }
                    val undated = sorted.filter { it.task.scheduledDate == null }
                    listOfNotNull(
                        overdue.takeIf { it.isNotEmpty() }?.let { TaskSection("Overdue", it, isWarning = true) },
                        dated.takeIf { it.isNotEmpty() }?.let { TaskSection("Scheduled", it) },
                        undated.takeIf { it.isNotEmpty() }?.let { TaskSection("No date", it) },
                    )
                } else {
                    listOf(TaskSection(null, sortItems(filtered, sort)))
                }
            }
            TaskListMode.COMPLETED -> {
                val done = applyFilter(all, f.copy(status = StatusFilter.COMPLETED), today, weekStart)
                    .sortedByDescending { it.task.completedAt }
                done.groupBy { it.task.completedAt?.atZone(zone)?.toLocalDate() }
                    .map { (date, list) -> TaskSection(if (date == null) "Earlier" else null, list, date = date) }
            }
        }
    }
}
