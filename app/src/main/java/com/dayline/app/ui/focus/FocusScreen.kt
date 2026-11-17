package com.dayline.app.ui.focus

import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.mutableIntStateOf

import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.dayline.app.ui.components.DaylineTopBar
import com.dayline.app.ui.components.rememberBarScroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dayline.app.core.common.AppClock
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.prefs.SettingsRepository
import com.dayline.app.core.data.repository.FocusRepository
import com.dayline.app.core.data.repository.ProjectRepository
import com.dayline.app.core.data.repository.TaskRepository
import com.dayline.app.core.domain.DateRules
import com.dayline.app.core.domain.DayValue
import com.dayline.app.core.domain.FocusConfig
import com.dayline.app.core.domain.FocusPhase
import com.dayline.app.core.domain.FocusTimerState
import com.dayline.app.core.domain.TimerStatus
import com.dayline.app.core.model.FocusSession
import com.dayline.app.core.model.Project
import com.dayline.app.core.model.TaskItem
import com.dayline.app.focus.FocusTimerController
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.components.BarChart
import com.dayline.app.ui.components.DaylineCard
import com.dayline.app.ui.components.ResponsiveBox
import com.dayline.app.ui.components.SectionLabel
import com.dayline.app.ui.components.StatTile
import com.dayline.app.ui.components.WavyBar
import com.dayline.app.ui.navigation.AppNavigator
import com.dayline.app.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class FocusUiState(
    val timer: FocusTimerState = FocusTimerState(),
    val config: FocusConfig = FocusConfig(),
    val today: LocalDate = LocalDate.now(),
    val todayMinutes: Int = 0,
    val todaySessions: Int = 0,
    val weekMinutes: List<DayValue> = emptyList(),
    val averageMinutes: Int = 0,
    val totalMinutes: Int = 0,
    val recent: List<FocusSession> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val projects: List<Project> = emptyList(),
)

@HiltViewModel
class FocusViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val controller: FocusTimerController,
    focusRepo: FocusRepository,
    tasks: TaskRepository,
    projects: ProjectRepository,
    settings: SettingsRepository,
    private val time: TimeProvider,
    clock: AppClock,
) : ViewModel() {
    init {
        savedState.get<Long>("task")?.takeIf { it > 0 }?.let { taskId ->
            viewModelScope.launch {
                val project = tasks.getDetails(taskId)?.task?.projectId
                if (controller.state.value.status == TimerStatus.IDLE) controller.attach(taskId, project)
            }
        }
    }

    val state: StateFlow<FocusUiState> = combine(
        controller.state, settings.settings, focusRepo.sessions, combine(tasks.items, projects.projects) { t, p -> t to p }, clock.today,
    ) { timer, s, sessions, (items, projectList), today ->
        build(timer, s.focus, sessions, items, projectList, today, time.zone(), s.weekStart)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusUiState())

    fun start() = controller.start()
    fun pause() = controller.pause()
    fun resume() = controller.resume()
    fun stop() = controller.stop()
    fun reset() = controller.reset()
    fun skip() = controller.skip()
    fun phase(p: FocusPhase) = controller.selectPhase(p)
    fun duration(minutes: Int) = controller.setDurationMinutes(minutes)
    fun attach(taskId: Long?, projectId: Long?) = controller.attach(taskId, projectId)
    fun tick() = viewModelScope.launch { controller.checkDue() }
    fun nowMillis() = time.nowMillis()

    companion object {
        fun build(
            timer: FocusTimerState, config: FocusConfig, sessions: List<FocusSession>, items: List<TaskItem>, projects: List<Project>,
            today: LocalDate, zone: ZoneId, weekStart: java.time.DayOfWeek,
        ): FocusUiState {
            fun day(s: FocusSession) = s.startedAt.atZone(zone).toLocalDate()
            val todays = sessions.filter { day(it) == today }
            val week = DateRules.weekDays(today, weekStart).map { d -> DayValue(d, (sessions.filter { day(it) == d }.sumOf { it.focusedMillis } / 60_000L).toInt()) }
            return FocusUiState(
                timer = timer,
                config = config,
                today = today,
                todayMinutes = (todays.sumOf { it.focusedMillis } / 60_000L).toInt(),
                todaySessions = todays.count { it.completed },
                weekMinutes = week,
                averageMinutes = if (sessions.isEmpty()) 0 else (sessions.sumOf { it.focusedMillis } / sessions.size / 60_000L).toInt(),
                totalMinutes = (sessions.sumOf { it.focusedMillis } / 60_000L).toInt(),
                recent = sessions.take(8),
                tasks = items.filter { !it.task.isCompleted },
                projects = projects.filter { !it.isArchived },
            )
        }
    }
}

@Composable
fun FocusScreen(nav: AppNavigator, vm: FocusViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(vm.nowMillis()) }
    val running = state.timer.status == TimerStatus.RUNNING
    LaunchedEffect(running) {
        while (running) {
            now = vm.nowMillis()
            if (state.timer.remaining(now) <= 0L) vm.tick()
            delay(250)
        }
        now = vm.nowMillis()
    }
    FocusContent(state, now, nav::back, vm)
}

@Composable
private fun FocusContent(state: FocusUiState, now: Long, onBack: () -> Unit, vm: FocusViewModel) {
    val formats = LocalFormats.current
    val t = state.timer
    val remaining = t.remaining(now)
    // Three pulses for the last three seconds while you're watching (the alarm covers the screen-off case).
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val endKey = if (t.status == TimerStatus.RUNNING) t.endAtMillis else -1L
    LaunchedEffect(endKey) {
        if (endKey < 0) return@LaunchedEffect
        val wait = endKey - 3_000L - vm.nowMillis()
        if (wait > 0) delay(wait)
        val left = endKey - vm.nowMillis()
        if (left > 0) com.dayline.app.notifications.FocusCountdown.buzz(ctx, left)
    }
    var pickTarget by remember { mutableStateOf(false) }
    // Every phase uses the app's (dynamic) primary colour, so all start buttons look the same.
    val phaseColor = MaterialTheme.colorScheme.primary
    var customOpen by remember { mutableStateOf(false) }
    val bar = rememberBarScroll()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DaylineTopBar(
                scrollBehavior = bar,
                title = { Text("Focus") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { inner ->
        ResponsiveBox(Modifier.padding(inner)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg).padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    FocusPhase.entries.forEach { p ->
                        FilterChip(selected = t.phase == p, onClick = { vm.phase(p) }, enabled = t.status == TimerStatus.IDLE, label = { Text(p.label) })
                    }
                }
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val ring = min(maxWidth * 0.78f, 300.dp)
                    ExpressiveDial(
                        progress = t.progress(now),
                        running = t.status == TimerStatus.RUNNING,
                        // A new Material shape for every minute of the session.
                        shapeIndex = if (t.status == TimerStatus.IDLE) 0 else ((t.durationMillis - remaining).coerceAtLeast(0L) / 60_000L).toInt(),
                        modifier = Modifier.size(ring),
                        color = phaseColor,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Round up, like a kitchen timer: a fresh 25-minute session reads 25:00, not 24:59.
                            val total = (remaining + 999) / 1000
                            val h = total / 3600
                            val m = (total % 3600) / 60
                            val s = total % 60
                            Text(
                                if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s),
                                style = MaterialTheme.typography.displayLarge.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            Text(
                                when (t.status) {
                                    TimerStatus.RUNNING -> t.phase.label
                                    TimerStatus.PAUSED -> "Paused"
                                    TimerStatus.IDLE -> "Ready · ${t.durationMillis / 60_000} min"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                            )
                            Text("Session ${t.completedFocusInCycle % state.config.sessionsBeforeLongBreak + 1} of ${state.config.sessionsBeforeLongBreak}",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.lg), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalIconButton(onClick = { if (t.status == TimerStatus.IDLE) vm.reset() else vm.stop() }, modifier = Modifier.size(56.dp)) {
                        Icon(if (t.status == TimerStatus.IDLE) Icons.Rounded.Refresh else Icons.Rounded.Stop, contentDescription = if (t.status == TimerStatus.IDLE) "Reset" else "Stop and save")
                    }
                    FilledIconButton(
                        onClick = {
                            when (t.status) {
                                TimerStatus.IDLE -> vm.start()
                                TimerStatus.RUNNING -> vm.pause()
                                TimerStatus.PAUSED -> vm.resume()
                            }
                        },
                        modifier = Modifier.size(88.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = phaseColor),
                    ) {
                        Icon(
                            if (t.status == TimerStatus.RUNNING) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = when (t.status) { TimerStatus.IDLE -> "Start"; TimerStatus.RUNNING -> "Pause"; TimerStatus.PAUSED -> "Resume" },
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    FilledTonalIconButton(onClick = vm::skip, modifier = Modifier.size(56.dp), enabled = t.status == TimerStatus.IDLE) {
                        Icon(Icons.Rounded.SkipNext, contentDescription = "Skip to next phase")
                    }
                }
                if (t.status == TimerStatus.IDLE) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        val presets = listOf(15, 25, 45, 60, 90)
                        presets.forEach { mins ->
                            FilterChip(selected = t.durationMillis == mins * 60_000L, onClick = { vm.duration(mins) }, label = { Text("${mins}m") })
                        }
                        val current = (t.durationMillis / 60_000L).toInt()
                        val isCustom = current !in presets
                        FilterChip(
                            selected = isCustom,
                            onClick = { customOpen = true },
                            label = { Text(if (isCustom) "Custom · ${formatMinutes(current)}" else "Custom") },
                            leadingIcon = { Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp)) },
                        )
                    }
                }
                if (customOpen) {
                    CustomTimeDialog((t.durationMillis / 60_000L).toInt(), onDismiss = { customOpen = false }) { vm.duration(it) }
                }
                val task = state.tasks.firstOrNull { it.id == t.taskId }
                val project = state.projects.firstOrNull { it.id == t.projectId }
                Box {
                    AssistChip(
                        onClick = { pickTarget = true },
                        label = { Text(task?.task?.title ?: project?.name ?: "Link a task", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = { Icon(if (task != null) Icons.Rounded.TaskAlt else Icons.Rounded.Folder, null, Modifier.size(18.dp)) },
                    )
                    DropdownMenu(expanded = pickTarget, onDismissRequest = { pickTarget = false }) {
                        DropdownMenuItem(text = { Text("Nothing") }, onClick = { vm.attach(null, null); pickTarget = false })
                        state.tasks.take(30).forEach { i ->
                            DropdownMenuItem(text = { Text(i.task.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, onClick = { vm.attach(i.id, i.task.projectId); pickTarget = false })
                        }
                    }
                }

                DaylineCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(formats.minutes(state.todayMinutes), style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.width(Dimens.sm))
                        Text("of ${formats.minutes(state.config.dailyGoalMinutes)} daily goal", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                    }
                    Spacer(Modifier.height(Dimens.sm))
                    WavyBar(state.todayMinutes.toFloat() / state.config.dailyGoalMinutes.coerceAtLeast(1))
                    Spacer(Modifier.height(Dimens.lg))
                    BarChart(state.weekMinutes, highlight = state.today, color = MaterialTheme.colorScheme.tertiary,
                        description = "Focus minutes this week: " + state.weekMinutes.joinToString { "${it.date.dayOfWeek}: ${it.value}" })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm), modifier = Modifier.fillMaxWidth()) {
                    StatTile("${state.todaySessions}", "Sessions today", Modifier.weight(1f), Icons.Rounded.Timer)
                    StatTile(formats.minutes(state.averageMinutes), "Average session", Modifier.weight(1f))
                    StatTile(formats.minutes(state.totalMinutes), "All-time focus", Modifier.weight(1f))
                }
                if (state.recent.isNotEmpty()) {
                    SectionLabel("Session history", Modifier.padding(horizontal = 0.dp))
                    state.recent.forEach { s ->
                        val start = s.startedAt.atZone(ZoneId.systemDefault())
                        val title = s.taskId?.let { id -> state.tasks.firstOrNull { it.id == id }?.task?.title }
                            ?: s.projectId?.let { id -> state.projects.firstOrNull { it.id == id }?.name } ?: "Focus"
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(formats.dateTime(start.toLocalDate(), start.toLocalTime(), state.today), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(formats.minutes(s.focusedMinutes) + if (s.completed) "" else " · stopped", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}


private fun formatMinutes(m: Int): String = if (m >= 60) "${m / 60}h ${m % 60}m".replace(" 0m", "") else "${m}m"

/** Pick any session length: hours and minutes steppers plus a slider, 1 minute to 4 hours. */
@Composable
private fun CustomTimeDialog(initial: Int, onDismiss: () -> Unit, onSet: (Int) -> Unit) {
    var minutes by remember { mutableIntStateOf(initial.coerceIn(1, 240)) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom time") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Dimens.md)) {
                    Stepper("h", minutes / 60, onMinus = { minutes = (minutes - 60).coerceAtLeast(1) }, onPlus = { minutes = (minutes + 60).coerceAtMost(240) })
                    Text(":", style = MaterialTheme.typography.displaySmall)
                    Stepper("m", minutes % 60, onMinus = { minutes = (minutes - 1).coerceAtLeast(1) }, onPlus = { minutes = (minutes + 1).coerceAtMost(240) })
                }
                Spacer(Modifier.height(Dimens.md))
                androidx.compose.material3.Slider(
                    value = minutes.toFloat(),
                    onValueChange = { minutes = it.toInt().coerceIn(1, 240) },
                    valueRange = 1f..240f,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    listOf(5, 10, 20, 30, 50, 120).forEach { q -> FilterChip(minutes == q, { minutes = q }, { Text(formatMinutes(q)) }) }
                }
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onSet(minutes); onDismiss() }) { Text("Set ${formatMinutes(minutes)}") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Stepper(unit: String, value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(onClick = onPlus) { Icon(Icons.Rounded.KeyboardArrowUp, "More") }
        Text("%02d".format(value), style = MaterialTheme.typography.displaySmall)
        Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FilledTonalIconButton(onClick = onMinus) { Icon(Icons.Rounded.KeyboardArrowDown, "Less") }
    }
}


/**
 * Material 3 Expressive timer dial: a soft scalloped "cookie" that turns slowly while the timer
 * runs, ringed by a wavy progress track (in the spirit of the Google Clock redesign).
 */
@Composable
private fun ExpressiveDial(
    progress: Float,
    running: Boolean,
    shapeIndex: Int,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit,
) {
    val container = MaterialTheme.colorScheme.primaryContainer
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val strong = com.dayline.app.ui.theme.LocalBrandGradient.current?.strong
    val spin = remember { androidx.compose.animation.core.Animatable(0f) }
    val wave = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        launch {
            while (true) spin.animateTo(spin.value + 360f, androidx.compose.animation.core.tween(60_000, easing = androidx.compose.animation.core.LinearEasing))
        }
        while (true) {
            wave.snapTo(0f)
            wave.animateTo(1f, androidx.compose.animation.core.tween(2_400, easing = androidx.compose.animation.core.LinearEasing))
        }
    }
    // Morph from the previous minute's shape to this minute's.
    var fromShape by remember { mutableIntStateOf(shapeIndex % DialShapes.size) }
    var toShape by remember { mutableIntStateOf(shapeIndex % DialShapes.size) }
    val morph = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(shapeIndex) {
        val next = shapeIndex % DialShapes.size
        if (next == toShape) return@LaunchedEffect
        fromShape = toShape
        toShape = next
        morph.snapTo(0f)
        morph.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 60f))
    }
    val shown by androidx.compose.animation.core.animateFloatAsState(progress, androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 120f), label = "dialProgress")
    // While paused the waves settle flat-ish; while running they breathe.
    val amp by androidx.compose.animation.core.animateFloatAsState(if (running) 1f else 0.35f, androidx.compose.animation.core.tween(600), label = "dialAmp")
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = center
            val stroke = r * 0.075f
            // Scalloped cookie (12 soft lobes) behind the digits.
            val cookieR = r - stroke * 3.3f
            val cookie = androidx.compose.ui.graphics.Path()
            val lobes = 12
            val steps = 240
            for (i in 0..steps) {
                val a = (i.toFloat() / steps) * 2f * Math.PI.toFloat()
                val rr = cookieR * (1f - 0.045f * (1f - kotlin.math.cos(lobes * a)) / 2f * 2f)
                val x = c.x + rr * kotlin.math.cos(a); val y = c.y + rr * kotlin.math.sin(a)
                if (i == 0) cookie.moveTo(x, y) else cookie.lineTo(x, y)
            }
            cookie.close()
            rotate(degrees = spin.value, pivot = c) { drawPath(cookie, container) }
            // Wavy ring: full track, then the progress arc from 12 o'clock.
            val ringR = r - stroke / 2f - r * 0.03f
            val phase = wave.value * 2f * Math.PI.toFloat()
            fun ring(sweep: Float): androidx.compose.ui.graphics.Path {
                val p = androidx.compose.ui.graphics.Path()
                val n = (360 * sweep).toInt().coerceAtLeast(2)
                for (i in 0..n) {
                    val t = i.toFloat() / 360f
                    val a = -Math.PI.toFloat() / 2f + t * 2f * Math.PI.toFloat()
                    val m = morph.value
                    val spinRad = Math.toRadians(spin.value.toDouble()).toFloat()
                    val dFrom = DialShapes[fromShape](a, phase, spinRad)
                    val dTo = DialShapes[toShape](a, phase, spinRad)
                    val rr = ringR * (1f + amp * (dFrom + (dTo - dFrom) * m))
                    val x = c.x + rr * kotlin.math.cos(a); val y = c.y + rr * kotlin.math.sin(a)
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                return p
            }
            val style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
            drawPath(ring(1f), track, style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke * 0.55f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            if (shown > 0.002f) {
                val path = ring(shown.coerceIn(0f, 1f))
                if (strong != null) drawPath(path, androidx.compose.ui.graphics.Brush.sweepGradient(strong + strong.first(), c), style = style)
                else drawPath(path, color, style = style)
            }
        }
        content()
    }
}


/**
 * Material 3 Expressive outlines for the focus ring, as inward offsets of the radius at angle `a`
 * (`phase` animates the waves, `spin` slowly turns the lobed shapes). One per minute, in order.
 */
private val DialShapes: List<(Float, Float, Float) -> Float> = listOf(
    // Wavy circle
    { a, phase, _ -> 0.028f * kotlin.math.sin(16f * a + phase) - 0.028f },
    // Cookie (9 scallops)
    { a, _, spin -> -0.07f * (1f - kotlin.math.cos(9f * (a - spin))) / 2f },
    // Clover (4 leaves)
    { a, _, spin -> -0.13f * Math.pow((1.0 - kotlin.math.abs(kotlin.math.cos(2f * (a - spin)))), 1.3).toFloat() },
    // Sunny (12-point soft burst)
    { a, _, spin -> -0.05f * (1f - kotlin.math.cos(12f * (a - spin))) / 2f },
    // Flower (8 petals)
    { a, _, spin -> -0.10f * Math.pow((1.0 - kotlin.math.abs(kotlin.math.cos(4f * (a - spin)))), 1.6).toFloat() },
    // Soft square (squircle)
    { a, _, spin ->
        val x = kotlin.math.abs(kotlin.math.cos(a - spin + Math.PI.toFloat() / 4f))
        val y = kotlin.math.abs(kotlin.math.sin(a - spin + Math.PI.toFloat() / 4f))
        val r = 1.0 / Math.pow(Math.pow(x.toDouble(), 4.0) + Math.pow(y.toDouble(), 4.0), 0.25)
        (r / Math.pow(2.0, 0.25) - 1.0).toFloat() * 0.9f
    },
    // Pentagon (5 soft corners)
    { a, _, spin -> -0.08f * (1f - kotlin.math.cos(5f * (a - spin))) / 2f },
)
