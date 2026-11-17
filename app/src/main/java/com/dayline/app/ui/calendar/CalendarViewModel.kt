package com.dayline.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayline.app.core.common.AppClock
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.prefs.SettingsRepository
import com.dayline.app.core.data.repository.FocusRepository
import com.dayline.app.core.data.repository.TaskRepository
import com.dayline.app.core.domain.DateRules
import com.dayline.app.core.domain.RecurrenceEngine
import com.dayline.app.core.model.FocusSession
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
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

enum class CalendarView(val label: String) { MONTH("Month"), WEEK("Week"), YEAR("Year") }

/** A task placed on a date. Projected entries are future repeats of a recurring task (not yet created). */
data class CalendarEntry(val item: TaskItem, val date: LocalDate, val isProjection: Boolean = false) {
    val time: LocalTime? get() = item.task.dueTime ?: item.task.startTime
}

data class FocusBlock(val date: LocalDate, val start: LocalTime, val end: LocalTime, val minutes: Int, val label: String)

data class CalendarUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val selected: LocalDate = LocalDate.now(),
    val view: CalendarView = CalendarView.MONTH,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val entries: Map<LocalDate, List<CalendarEntry>> = emptyMap(),
    val focus: Map<LocalDate, List<FocusBlock>> = emptyMap(),
    /** Task counts for every day in the selected year (year view heat). */
    val yearCounts: Map<LocalDate, Int> = emptyMap(),
) {
    fun on(date: LocalDate): List<CalendarEntry> = entries[date].orEmpty()
}

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val tasks: TaskRepository,
    focusRepo: FocusRepository,
    settings: SettingsRepository,
    private val time: TimeProvider,
    clock: AppClock,
) : ViewModel() {
    private val selected = MutableStateFlow(time.today())
    private val view = MutableStateFlow(CalendarView.MONTH)

    val state: StateFlow<CalendarUiState> = combine(
        tasks.items, focusRepo.sessions, settings.settings, combine(selected, view) { s, v -> s to v }, clock.today,
    ) { items, sessions, s, (sel, v), today ->
        val ws = s.weekStart
        val (from, to) = rangeFor(v, sel, ws)
        CalendarUiState(
            loading = false,
            today = today,
            selected = sel,
            view = v,
            weekStart = ws,
            entries = buildEntries(items, from, to, ws),
            focus = buildFocus(sessions, from, to, time.zone()),
            yearCounts = if (v == CalendarView.YEAR) buildEntries(items, sel.withDayOfYear(1), sel.withDayOfYear(sel.lengthOfYear()), ws)
                .mapValues { it.value.size } else emptyMap(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUiState())

    fun select(date: LocalDate) { selected.value = date }
    fun setView(v: CalendarView) { view.value = v }
    fun goToday() { selected.value = time.today() }

    fun shift(direction: Int) = selected.update { d ->
        when (view.value) {
            CalendarView.MONTH -> d.plusMonths(direction.toLong()).withDayOfMonth(1).let { if (it.month == time.today().month && it.year == time.today().year) time.today() else it }
            CalendarView.WEEK -> d.plusWeeks(direction.toLong())
            CalendarView.YEAR -> d.plusYears(direction.toLong())
        }
    }

    fun toggle(id: Long, done: Boolean) = viewModelScope.launch { tasks.setCompleted(id, done) }
    fun reschedule(id: Long, date: LocalDate) = viewModelScope.launch { tasks.reschedule(id, date) }

    companion object {
        fun rangeFor(v: CalendarView, sel: LocalDate, ws: DayOfWeek): Pair<LocalDate, LocalDate> = when (v) {
            // Neighbouring months too, so the month you swipe towards already shows its tasks.
            CalendarView.MONTH -> DateRules.monthGrid(sel.minusMonths(1), ws).first() to DateRules.monthGrid(sel.plusMonths(1), ws).last()
            CalendarView.WEEK -> DateRules.startOfWeek(sel, ws) to DateRules.endOfWeek(sel, ws)
            CalendarView.YEAR -> sel.withDayOfYear(1) to sel.withDayOfYear(sel.lengthOfYear())
        }

        fun buildEntries(items: List<TaskItem>, from: LocalDate, to: LocalDate, ws: DayOfWeek): Map<LocalDate, List<CalendarEntry>> {
            val out = mutableListOf<CalendarEntry>()
            items.forEach { item ->
                val d = item.task.scheduledDate ?: return@forEach
                if (!d.isBefore(from) && !d.isAfter(to)) out += CalendarEntry(item, d)
                val rule = item.task.recurrence
                if (rule != null && !item.task.isCompleted) {
                    RecurrenceEngine.projectedOccurrences(rule, d, from, to, ws).forEach { out += CalendarEntry(item, it, isProjection = true) }
                }
            }
            return out.groupBy { it.date }.mapValues { (_, list) ->
                list.sortedWith(compareBy({ it.item.task.isCompleted }, { it.time ?: LocalTime.MIN }, { -it.item.task.priority.level }))
            }
        }

        fun buildFocus(sessions: List<FocusSession>, from: LocalDate, to: LocalDate, zone: ZoneId): Map<LocalDate, List<FocusBlock>> =
            sessions.mapNotNull { s ->
                val start = s.startedAt.atZone(zone)
                val end = s.endedAt.atZone(zone)
                val date = start.toLocalDate()
                if (date.isBefore(from) || date.isAfter(to)) null
                else FocusBlock(date, start.toLocalTime(), if (end.toLocalDate() == date) end.toLocalTime() else LocalTime.MAX, s.focusedMinutes, if (s.completed) "Focus" else "Focus (stopped)")
            }.groupBy { it.date }
    }
}
