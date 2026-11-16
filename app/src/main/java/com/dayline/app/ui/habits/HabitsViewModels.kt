package com.dayline.app.ui.habits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayline.app.core.common.AppClock
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.prefs.SettingsRepository
import com.dayline.app.core.data.repository.HabitRepository
import com.dayline.app.core.domain.DateRules
import com.dayline.app.core.domain.HabitStats
import com.dayline.app.core.domain.HabitStreakCalculator
import com.dayline.app.core.model.Habit
import com.dayline.app.core.model.HabitCompletion
import com.dayline.app.core.model.HabitFrequency
import com.dayline.app.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class HabitRowState(
    val habit: Habit,
    val stats: HabitStats,
    /** Check-in counts for the current week, in week order. */
    val week: List<Pair<LocalDate, Int>>,
    val todayCount: Int,
    val dueToday: Boolean,
) {
    val doneToday: Boolean get() = HabitStreakCalculator.isDone(habit, todayCount)
}

data class HabitsUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val active: List<HabitRowState> = emptyList(),
    val archived: List<HabitRowState> = emptyList(),
) {
    val dueToday: Int get() = active.count { it.dueToday }
    val doneToday: Int get() = active.count { it.dueToday && it.doneToday }
}

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val repo: HabitRepository,
    settings: SettingsRepository,
    clock: AppClock,
) : ViewModel() {
    val state: StateFlow<HabitsUiState> = combine(repo.habits, repo.completions, settings.settings, clock.today) { habits, completions, s, today ->
        val rows = habits.map { h -> row(h, completions.filter { it.habitId == h.id }, today, s.weekStart) }
        HabitsUiState(false, today, s.weekStart, rows.filter { !it.habit.isArchived }, rows.filter { it.habit.isArchived })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitsUiState())

    fun toggle(habitId: Long, date: LocalDate) = viewModelScope.launch { repo.toggle(habitId, date) }
    fun increment(habitId: Long, date: LocalDate, delta: Int) = viewModelScope.launch { repo.checkIn(habitId, date, delta) }

    companion object {
        fun row(h: Habit, comps: List<HabitCompletion>, today: LocalDate, ws: DayOfWeek): HabitRowState {
            val byDate = comps.associate { it.date to it.count }
            return HabitRowState(
                habit = h,
                stats = HabitStreakCalculator.stats(h, comps, today, ws),
                week = DateRules.weekDays(today, ws).map { it to (byDate[it] ?: 0) },
                todayCount = byDate[today] ?: 0,
                dueToday = HabitStreakCalculator.dueOn(h, today, comps, ws),
            )
        }
    }
}

data class HabitDetailState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val row: HabitRowState? = null,
    val counts: Map<LocalDate, Int> = emptyMap(),
    val consistency30: Float = 0f,
    val consistency90: Float = 0f,
    val month: LocalDate = LocalDate.now().withDayOfMonth(1),
    val missing: Boolean = false,
)

@HiltViewModel
class HabitDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: HabitRepository,
    settings: SettingsRepository,
    clock: AppClock,
) : ViewModel() {
    private val habitId: Long = savedState.get<Long>("id") ?: 0L
    private val month = MutableStateFlow(LocalDate.now().withDayOfMonth(1))

    val state: StateFlow<HabitDetailState> = combine(repo.habits, repo.completions, settings.settings, clock.today, month) { habits, completions, s, today, m ->
        val h = habits.firstOrNull { it.id == habitId } ?: return@combine HabitDetailState(loading = false, missing = true)
        val comps = completions.filter { it.habitId == h.id }
        HabitDetailState(
            loading = false,
            today = today,
            weekStart = s.weekStart,
            row = HabitsViewModel.row(h, comps, today, s.weekStart),
            counts = comps.associate { it.date to it.count },
            consistency30 = HabitStreakCalculator.consistency(h, comps, today.minusDays(29), today, s.weekStart, today),
            consistency90 = HabitStreakCalculator.consistency(h, comps, today.minusDays(89), today, s.weekStart, today),
            month = m,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HabitDetailState())

    fun shiftMonth(delta: Int) = month.update { it.plusMonths(delta.toLong()) }
    fun toggle(date: LocalDate) = viewModelScope.launch { repo.toggle(habitId, date) }
    fun increment(date: LocalDate, delta: Int) = viewModelScope.launch { repo.checkIn(habitId, date, delta) }
    fun archive(archived: Boolean) = viewModelScope.launch { state.value.row?.habit?.let { repo.setArchived(it, archived) } }
    fun delete(onDone: () -> Unit) = viewModelScope.launch { repo.delete(habitId); onDone() }
}

data class HabitEditorState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val habit: Habit = Habit(name = "", color = Palette.itemColors.first(), startDate = LocalDate.now()),
    val error: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class HabitEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: HabitRepository,
    private val time: TimeProvider,
) : ViewModel() {
    private val habitId: Long = savedState.get<Long>("id") ?: 0L
    private val _state = MutableStateFlow(HabitEditorState())
    val state: StateFlow<HabitEditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (habitId == 0L) {
                _state.value = HabitEditorState(loading = false, habit = Habit(name = "", color = Palette.itemColors.random(), startDate = time.today()))
            } else {
                repo.habits.collect { list ->
                    if (_state.value.loading) {
                        val h = list.firstOrNull { it.id == habitId }
                        _state.value = if (h == null) HabitEditorState(loading = false, error = "Habit not found", saved = true) else HabitEditorState(loading = false, isNew = false, habit = h)
                    }
                }
            }
        }
    }

    fun update(transform: (Habit) -> Habit) = _state.update { it.copy(habit = transform(it.habit), error = null) }

    fun save() {
        val h = _state.value.habit
        val error = when {
            h.name.isBlank() -> "Give your habit a name"
            h.frequency == HabitFrequency.SPECIFIC_DAYS && h.weekdays.isEmpty() -> "Pick at least one day"
            h.targetPerDay < 1 -> "Daily target must be at least 1"
            else -> null
        }
        if (error != null) { _state.update { it.copy(error = error) }; return }
        viewModelScope.launch {
            try {
                repo.save(h)
                _state.update { it.copy(saved = true) }
            } catch (e: IllegalArgumentException) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    companion object {
        val suggestedTimes: List<LocalTime> = listOf(LocalTime.of(7, 0), LocalTime.of(12, 30), LocalTime.of(18, 0), LocalTime.of(21, 30))
    }
}
