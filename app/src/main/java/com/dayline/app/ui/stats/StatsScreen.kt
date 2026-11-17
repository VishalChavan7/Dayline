package com.dayline.app.ui.stats

import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.dayline.app.ui.components.DaylineTopBar
import com.dayline.app.ui.components.rememberBarScroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.PendingActions
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dayline.app.core.common.AppClock
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.prefs.SettingsRepository
import com.dayline.app.core.data.repository.FocusRepository
import com.dayline.app.core.data.repository.HabitRepository
import com.dayline.app.core.data.repository.ProjectRepository
import com.dayline.app.core.data.repository.TaskRepository
import com.dayline.app.core.domain.HabitStreakCalculator
import com.dayline.app.core.domain.StatsCalculator
import com.dayline.app.core.domain.StatsInput
import com.dayline.app.core.domain.StatsPeriod
import com.dayline.app.core.domain.StatsReport
import com.dayline.app.core.model.Priority
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.components.BarChart
import com.dayline.app.ui.components.CalendarHeatmap
import com.dayline.app.ui.components.DaylineCard
import com.dayline.app.ui.components.DonutChart
import com.dayline.app.ui.components.DonutSlice
import com.dayline.app.ui.components.EmptyState
import com.dayline.app.ui.components.LabeledProgress
import com.dayline.app.ui.components.LineChart
import com.dayline.app.ui.components.ResponsiveBox
import com.dayline.app.ui.components.StatTile
import com.dayline.app.ui.components.priorityColor
import com.dayline.app.ui.navigation.AppNavigator
import com.dayline.app.ui.theme.DaylineTheme
import com.dayline.app.ui.theme.Dimens
import com.dayline.app.ui.theme.StatusColors
import com.dayline.app.ui.theme.contentOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

data class HabitStreakRow(val name: String, val color: Int, val current: Int, val longest: Int, val unit: String)

data class StatsUiState(
    val loading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.WEEK,
    val anchor: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val report: StatsReport? = null,
    val streaks: List<HabitStreakRow> = emptyList(),
)

@HiltViewModel
class StatsViewModel @Inject constructor(
    tasks: TaskRepository,
    focus: FocusRepository,
    habits: HabitRepository,
    projects: ProjectRepository,
    settings: SettingsRepository,
    private val time: TimeProvider,
    clock: AppClock,
) : ViewModel() {
    private val period = MutableStateFlow(StatsPeriod.WEEK)
    private val anchor = MutableStateFlow(time.today())

    private val input = combine(tasks.items, focus.sessions, habits.habits, habits.completions, projects.projects) { t, f, h, c, p ->
        StatsInput(t.map { it.task }, f, h, c, p)
    }

    val state: StateFlow<StatsUiState> = combine(input, period, anchor, settings.settings, clock.today) { inp, per, anc, s, today ->
        StatsUiState(
            loading = false,
            period = per,
            anchor = anc,
            today = today,
            weekStart = s.weekStart,
            report = StatsCalculator.report(inp, per, anc, today, time.zone(), s.weekStart),
            streaks = inp.habits.filter { !it.isArchived }.map { h ->
                val st = HabitStreakCalculator.stats(h, inp.habitCompletions.filter { it.habitId == h.id }, today, s.weekStart)
                HabitStreakRow(h.name, h.color, st.currentStreak, st.longestStreak, st.streakUnit)
            }.sortedByDescending { it.current },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setPeriod(p: StatsPeriod) { period.value = p; anchor.value = time.today() }
    fun shift(direction: Int) = anchor.update {
        when (period.value) {
            StatsPeriod.DAY -> it.plusDays(direction.toLong())
            StatsPeriod.WEEK -> it.plusWeeks(direction.toLong())
            StatsPeriod.MONTH -> it.plusMonths(direction.toLong())
        }
    }
}

@Composable
fun StatsScreen(nav: AppNavigator, vm: StatsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    StatsContent(state, onBack = nav::back, onPeriod = vm::setPeriod, onShift = { vm.shift(it) })
}

@Composable
fun StatsContent(state: StatsUiState, onBack: () -> Unit, onPeriod: (StatsPeriod) -> Unit, onShift: (Int) -> Unit) {
    val formats = LocalFormats.current
    val dark = DaylineTheme.isDark
    val bar = rememberBarScroll()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DaylineTopBar(
                scrollBehavior = bar,
                title = { Text("Statistics") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { inner ->
        val r = state.report
        ResponsiveBox(Modifier.padding(inner)) {
            if (state.loading || r == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                return@ResponsiveBox
            }
            LazyColumn(contentPadding = PaddingValues(start = Dimens.lg, end = Dimens.lg, bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                item {
                    com.dayline.app.ui.components.SegmentedPills(StatsPeriod.entries, state.period, { it.label }, onPeriod)
                    Row(Modifier.fillMaxWidth().padding(top = Dimens.sm), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onShift(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous ${state.period.label.lowercase()}") }
                        Text(
                            when (state.period) {
                                StatsPeriod.DAY -> formats.relative(r.start, state.today)
                                StatsPeriod.WEEK -> "${formats.monthDay(r.start)} – ${formats.monthDay(r.end)}"
                                StatsPeriod.MONTH -> formats.monthYear(r.start)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        IconButton(onClick = { onShift(1) }, enabled = r.end.isBefore(state.today)) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next ${state.period.label.lowercase()}") }
                    }
                }
                if (!r.hasAnyData) {
                    item { EmptyState(icon = Icons.Rounded.Insights, modifier = Modifier.fillParentMaxHeight(1f), title = "No activity yet", message = "Complete tasks, run focus sessions and check in on habits — your real progress will show up here.") }
                    return@LazyColumn
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                            StatTile("${r.completed}", "Completed", Modifier.weight(1f), Icons.Rounded.CheckCircle, StatusColors.success(dark), caption = change(r.completedChange))
                            StatTile("${r.remaining}", "Remaining", Modifier.weight(1f), Icons.Rounded.PendingActions, MaterialTheme.colorScheme.primary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                            StatTile("${(r.completionRate * 100).toInt()}%", "Completion rate", Modifier.weight(1f), Icons.Rounded.Percent, MaterialTheme.colorScheme.primary)
                            StatTile("${if (state.period == StatsPeriod.DAY) r.overdueNow else r.overdueInPeriod}", if (state.period == StatsPeriod.DAY) "Overdue now" else "Missed deadlines",
                                Modifier.weight(1f), Icons.Rounded.WarningAmber, StatusColors.overdue(dark))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                            StatTile(formats.minutes(r.focusMinutes), "Focus time", Modifier.weight(1f), Icons.Rounded.Timer, MaterialTheme.colorScheme.tertiary, caption = change(r.focusChange))
                            StatTile("${r.habitCheckIns}", "Habit check-ins · ${(r.habitConsistency * 100).toInt()}% consistent", Modifier.weight(1f), Icons.Rounded.SelfImprovement, MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                item {
                    ChartCard("Tasks completed", if (state.period == StatsPeriod.DAY) "Last 7 days" else null) {
                        BarChart(r.completedSeries, highlight = state.today, labelEvery = if (r.completedSeries.size > 10) 5 else 1,
                            description = "Tasks completed per day: " + r.completedSeries.joinToString { "${it.date}: ${it.value}" })
                        Spacer(Modifier.height(Dimens.sm))
                        Text("${r.completed} vs ${r.previousCompleted} in the previous ${state.period.label.lowercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    ChartCard("Focus trend", "Minutes per day") {
                        LineChart(r.focusSeries, description = "Focus minutes per day: " + r.focusSeries.joinToString { "${it.date}: ${it.value}" })
                        Spacer(Modifier.height(Dimens.sm))
                        Text("${r.focusSessions} completed session${if (r.focusSessions == 1) "" else "s"} · average ${formats.minutes(r.averageSessionMinutes)}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (r.overdueSeries.any { it.value > 0 }) {
                    item {
                        ChartCard("Overdue trend", "Open overdue tasks at the start of each day") {
                            LineChart(r.overdueSeries, color = StatusColors.overdue(dark), description = "Overdue tasks per day: " + r.overdueSeries.joinToString { "${it.date}: ${it.value}" })
                        }
                    }
                }
                item {
                    ChartCard("Priority distribution", "Tasks due or completed in this period") {
                        val slices = Priority.entries.reversed().map { p -> DonutSlice(p.label, r.priorityDistribution[p] ?: 0, priorityColor(p)) }
                        DonutChart(slices, centerLabel = "${slices.sumOf { it.value }}", centerCaption = "tasks")
                    }
                }
                item {
                    ChartCard("Completion heatmap", "Last 18 weeks") { CalendarHeatmap(r.heatmap, state.weekStart) }
                }
                if (false && r.projectProgress.isNotEmpty()) { // projects were removed
                    item {
                        ChartCard("Project progress", null) {
                            Column(verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                                r.projectProgress.forEach { p ->
                                    LabeledProgress(p.name, "${p.completed}/${p.total}" + if (p.focusMinutes > 0) " · ${formats.minutes(p.focusMinutes)}" else "", p.fraction, contentOf(p.color, dark))
                                }
                            }
                        }
                    }
                }
                if (state.streaks.isNotEmpty()) {
                    item {
                        ChartCard("Habit streaks", null) {
                            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                                state.streaks.forEach { s ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(s.name, modifier = Modifier.weight(1f), maxLines = 1)
                                        Text("${s.current} now · best ${s.longest} ${s.unit}", style = MaterialTheme.typography.labelLarge, color = contentOf(s.color, dark))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun change(pct: Int?): String? = pct?.let { if (it >= 0) "▲ $it% vs previous" else "▼ ${-it}% vs previous" }

@Composable
private fun ChartCard(title: String, subtitle: String?, content: @Composable () -> Unit) {
    DaylineCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Dimens.md))
        content()
    }
}
