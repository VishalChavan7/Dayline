package com.dayline.app.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayline.app.core.common.AppClock
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.prefs.SettingsRepository
import com.dayline.app.core.data.repository.DeletedTask
import com.dayline.app.core.data.repository.FocusRepository
import com.dayline.app.core.data.repository.HabitRepository
import com.dayline.app.core.data.repository.NoteRepository
import com.dayline.app.core.data.repository.ReminderRepository
import com.dayline.app.core.data.repository.TaskRepository
import com.dayline.app.core.domain.DateRules
import com.dayline.app.core.domain.DayValue
import com.dayline.app.core.domain.FocusTimerState
import com.dayline.app.core.domain.HabitStreakCalculator
import com.dayline.app.core.domain.Quote
import com.dayline.app.core.domain.Quotes
import com.dayline.app.core.domain.TaskGrouping
import com.dayline.app.core.model.AppSettings
import com.dayline.app.core.model.DashboardSection
import com.dayline.app.core.model.FocusSession
import com.dayline.app.core.model.Habit
import com.dayline.app.core.model.HabitCompletion
import com.dayline.app.core.model.Note
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.Reminder
import com.dayline.app.core.model.TaskItem
import com.dayline.app.focus.FocusTimerController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

data class HabitToday(val habit: Habit, val count: Int, val done: Boolean, val streak: Int, val streakUnit: String)

/** A project or list as shown on the dashboard. */
data class CollectionSummary(val id: Long, val name: String, val icon: String, val color: Int, val open: Int, val done: Int, val caption: String? = null) {
    val fraction: Float get() = if (open + done == 0) 0f else done.toFloat() / (open + done)
}

data class WeekDayInfo(val date: LocalDate, val total: Int, val completed: Int, val topPriority: Priority?)

data class TodayUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val greeting: String = "",
    val overdue: List<TaskItem> = emptyList(),
    val todayTasks: List<TaskItem> = emptyList(),
    val completedToday: List<TaskItem> = emptyList(),
    val deadlines: List<TaskItem> = emptyList(),
    val habits: List<HabitToday> = emptyList(),
    val focusMinutesToday: Int = 0,
    val focusGoalMinutes: Int = 120,
    val focusSessionsToday: Int = 0,
    val timer: FocusTimerState? = null,
    val week: List<WeekDayInfo> = emptyList(),
    val weekCompleted: List<DayValue> = emptyList(),
    val weekTotal: Int = 0,
    val lastWeekTotal: Int = 0,
    val quote: Quote = Quotes.all.first(),
    val pinnedNotes: List<Note> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val projects: List<CollectionSummary> = emptyList(),
    val inbox: List<TaskItem> = emptyList(),
    val lists: List<CollectionSummary> = emptyList(),
    val sections: List<DashboardSection> = DashboardSection.entries,
    val collapsed: Set<DashboardSection> = emptySet(),
    val groupByPriority: Boolean = true,
) {
    // Daily progress counts today's habits as well as tasks.
    val doneCount: Int get() = completedToday.size + habits.count { it.done }
    val plannedCount: Int get() = completedToday.size + todayTasks.size + overdue.size + habits.size
    val progress: Float get() = if (plannedCount == 0) 0f else doneCount.toFloat() / plannedCount
    val habitsDone: Int get() = habits.count { it.done }
}

internal data class Sources(
    val items: List<TaskItem>,
    val habits: List<Habit>,
    val completions: List<HabitCompletion>,
    val sessions: List<FocusSession>,
    val projects: List<com.dayline.app.core.model.Project> = emptyList(),
    val lists: List<com.dayline.app.core.model.TaskList> = emptyList(),
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val tasks: TaskRepository,
    private val habitRepo: HabitRepository,
    projectRepo: com.dayline.app.core.data.repository.ProjectRepository,
    focusRepo: FocusRepository,
    notes: NoteRepository,
    private val reminderRepo: ReminderRepository,
    private val settingsRepo: SettingsRepository,
    private val focus: FocusTimerController,
    private val time: TimeProvider,
    clock: AppClock,
) : ViewModel() {

    private val sources = combine(
        tasks.items, habitRepo.habits, habitRepo.completions, focusRepo.sessions,
        combine(projectRepo.projects, projectRepo.lists) { p, l -> p to l },
    ) { a, b, c, d, (p, l) -> Sources(a, b, c, d, p, l) }

    val state: StateFlow<TodayUiState> = combine(
        sources, settingsRepo.settings, notes.notes, reminderRepo.customReminders, combine(clock.today, focus.state) { t, f -> t to f },
    ) { src, settings, allNotes, reminders, (today, timer) ->
        build(src, settings, allNotes, reminders, today, timer, time.zone(), time.localNow())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun toggleTask(id: Long, done: Boolean) = viewModelScope.launch { tasks.setCompleted(id, done) }
    fun toggleSubtask(id: Long, done: Boolean) = viewModelScope.launch { tasks.setSubtaskDone(id, done) }
    suspend fun deleteTask(id: Long): DeletedTask? = tasks.delete(id)
    fun restoreTask(d: DeletedTask) = viewModelScope.launch { tasks.restore(d) }
    fun toggleHabit(id: Long) = viewModelScope.launch { habitRepo.toggle(id, time.today()) }
    fun incrementHabit(id: Long) = viewModelScope.launch { habitRepo.checkIn(id, time.today(), 1) }
    fun setCollapsed(section: DashboardSection, collapsed: Boolean) = viewModelScope.launch { settingsRepo.setSectionCollapsed(section, collapsed) }
    fun dismissReminder(id: Long) = viewModelScope.launch { reminderRepo.dismiss(id) }
    fun addReminder(title: String, at: LocalDateTime, pinned: Boolean) = viewModelScope.launch {
        reminderRepo.saveCustom(Reminder(type = com.dayline.app.core.model.ReminderType.CUSTOM, title = title.trim(), at = at, isPinned = pinned))
    }
    fun startFocus() = focus.start()

    companion object {
        fun greetingFor(now: LocalDateTime, name: String = ""): String {
            val base = when (now.hour) {
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                in 17..21 -> "Good evening"
                else -> "Hello, night owl"
            }
            val n = name.trim().substringBefore(' ')
            if (n.isEmpty()) return base
            // A little personality: the line changes with the time of day and rotates through the week.
            val options = when (now.hour) {
                in 5..11 -> listOf("Good morning, $n", "Rise and shine, $n", "Morning, $n, let's go", "Fresh start, $n", "Hey $n, coffee first?")
                in 12..16 -> listOf("Good afternoon, $n", "Keep it rolling, $n", "Halfway there, $n", "Hey $n, nice momentum", "Afternoon, $n")
                in 17..21 -> listOf("Good evening, $n", "Winding down, $n?", "Nice work today, $n", "Evening, $n, almost there", "Hey $n, time to wrap up")
                else -> listOf("Hello, night owl", "Still up, $n?", "Late one, $n", "Burning the midnight oil, $n?", "Rest soon, $n")
            }
            return options[(now.dayOfYear + now.hour / 6) % options.size]
        }

        /** Pure state assembly (also used by tests and screenshot previews). */
        internal fun build(
            src: Sources,
            settings: AppSettings,
            notes: List<Note>,
            reminders: List<Reminder>,
            today: LocalDate,
            timer: FocusTimerState?,
            zone: ZoneId,
            now: LocalDateTime,
        ): TodayUiState {
            val items = src.items
            fun completedOn(i: TaskItem) = i.task.completedAt?.atZone(zone)?.toLocalDate()
            val open = items.filter { !it.task.isCompleted }
            val overdue = open.filter { it.task.isOverdue(today) }.sortedWith(compareBy({ it.task.dueDate }, { -it.task.priority.level }))
            val todayOpen = open.filter { i ->
                val t = i.task
                !t.isOverdue(today) && (t.dueDate == today || (t.dueDate == null && t.startDate == today) ||
                    (t.startDate != null && t.dueDate != null && !today.isBefore(t.startDate) && !today.isAfter(t.dueDate)))
            }.sortedWith(compareBy<TaskItem>({ -it.task.priority.level }, { it.task.dueTime ?: it.task.startTime ?: java.time.LocalTime.MAX }, { it.task.sortOrder }))
            val completedToday = items.filter { it.task.isCompleted && completedOn(it) == today }.sortedByDescending { it.task.completedAt }
            val deadlines = open.filter { i -> i.task.dueDate?.let { it.isAfter(today) && !it.isAfter(today.plusDays(7)) } == true }
                .sortedWith(compareBy({ it.task.dueDate }, { -it.task.priority.level }))

            val habitsToday = src.habits.filter { HabitStreakCalculator.dueOn(it, today, src.completions.filter { c -> c.habitId == it.id }, settings.weekStart) }
                .map { h ->
                    val comps = src.completions.filter { it.habitId == h.id }
                    val count = comps.firstOrNull { it.date == today }?.count ?: 0
                    val stats = HabitStreakCalculator.stats(h, comps, today, settings.weekStart)
                    HabitToday(h, count, HabitStreakCalculator.isDone(h, count), stats.currentStreak, stats.streakUnit)
                }

            val todaySessions = src.sessions.filter { it.startedAt.atZone(zone).toLocalDate() == today }
            val weekDays = DateRules.weekDays(today, settings.weekStart)
            val completedByDay = items.mapNotNull { completedOn(it) }.groupingBy { it }.eachCount()
            val week = weekDays.map { d ->
                val onDay = TaskGrouping.onDay(items.map { it.task }, d)
                WeekDayInfo(d, onDay.size, onDay.count { it.isCompleted }, onDay.filter { !it.isCompleted }.maxByOrNull { it.priority.level }?.priority)
            }
            val weekCompleted = weekDays.map { DayValue(it, completedByDay[it] ?: 0) }
            val lastWeek = weekDays.map { it.minusWeeks(1) }.sumOf { completedByDay[it] ?: 0 }

            val upcomingReminders = reminders.filter { r -> !r.isDismissed && r.at != null && (r.isPinned || r.at.isAfter(now.minusHours(1))) }
                .sortedBy { it.at }.take(4)

            val projects = src.projects.filter { !it.isArchived }.sortedBy { it.sortOrder }.map { p ->
                val pts = items.filter { it.task.projectId == p.id }
                CollectionSummary(p.id, p.name, p.icon, p.color, pts.count { !it.task.isCompleted }, pts.count { it.task.isCompleted })
            }
            val lists = src.lists.sortedWith(compareBy({ it.projectId != null }, { it.sortOrder })).map { l ->
                val lts = items.filter { it.task.listId == l.id }
                CollectionSummary(
                    l.id, l.name, l.icon, l.color, lts.count { !it.task.isCompleted }, lts.count { it.task.isCompleted },
                    caption = src.projects.firstOrNull { it.id == l.projectId }?.name,
                )
            }

            return TodayUiState(
                loading = false,
                projects = projects,
                lists = lists,
                inbox = open.filter { it.task.scheduledDate == null && it.task.projectId == null && it.task.listId == null }
                    .sortedWith(compareBy({ it.task.sortOrder }, { -it.task.priority.level })),
                today = today,
                greeting = greetingFor(now, settings.userName),
                overdue = overdue,
                todayTasks = todayOpen,
                completedToday = completedToday,
                deadlines = deadlines,
                habits = habitsToday,
                focusMinutesToday = (todaySessions.sumOf { it.focusedMillis } / 60_000L).toInt(),
                focusGoalMinutes = settings.focus.dailyGoalMinutes,
                focusSessionsToday = todaySessions.count { it.completed },
                timer = timer,
                week = week,
                weekCompleted = weekCompleted,
                weekTotal = weekCompleted.sumOf { it.value },
                lastWeekTotal = lastWeek,
                quote = Quotes.forDay(today),
                // Dashboard notes: pinned first, then the most recently edited.
                pinnedNotes = (notes.filter { it.isPinned } + notes.filter { !it.isPinned }.sortedByDescending { it.updatedAt }).take(8),
                reminders = upcomingReminders,
                sections = settings.dashboardOrder.filter { it !in settings.hiddenSections },
                collapsed = settings.collapsedSections,
                groupByPriority = settings.groupTodayByPriority,
            )
        }
    }
}
