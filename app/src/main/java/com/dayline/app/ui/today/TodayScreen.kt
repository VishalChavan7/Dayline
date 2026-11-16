package com.dayline.app.ui.today

import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.foundation.layout.heightIn

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.Workspaces
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.dayline.app.ui.components.CountPill
import com.dayline.app.ui.components.DaylineTopBar
import com.dayline.app.ui.components.ItemIconBadge
import com.dayline.app.ui.components.LabeledIconButton
import com.dayline.app.ui.components.ProgressRing
import com.dayline.app.ui.components.rememberBarScroll
import com.dayline.app.ui.motion.pressBounce
import com.dayline.app.ui.navigation.FabDefaults

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dayline.app.core.domain.TimerStatus
import com.dayline.app.core.model.DashboardSection
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.TaskItem
import com.dayline.app.ui.common.IconCatalog
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.components.BarChart
import com.dayline.app.ui.components.CollapsibleCard
import com.dayline.app.ui.components.DaylineCard
import com.dayline.app.ui.components.InlineEmpty
import com.dayline.app.ui.components.ResponsiveBox
import com.dayline.app.ui.components.SubtaskLine
import com.dayline.app.ui.components.TaskRow
import com.dayline.app.ui.components.WavyBar
import com.dayline.app.ui.components.priorityColor
import com.dayline.app.ui.navigation.AppNavigator
import com.dayline.app.ui.navigation.LocalQuickAdd
import com.dayline.app.ui.navigation.LocalSnackbar
import com.dayline.app.ui.navigation.Routes
import com.dayline.app.ui.quickadd.QuickAddDefaults
import com.dayline.app.ui.tasks.TaskActionsSheet
import com.dayline.app.ui.theme.DaylineTheme
import com.dayline.app.ui.theme.Dimens
import com.dayline.app.ui.theme.StatusColors
import com.dayline.app.ui.theme.containerOf
import com.dayline.app.ui.theme.contentOf
import kotlinx.coroutines.launch

/** Callbacks for the dashboard, kept separate so the content can be rendered in previews and screenshot tests. */
data class TodayActions(
    val onToggleTask: (Long, Boolean) -> Unit = { _, _ -> },
    val onToggleSubtask: (Long, Boolean) -> Unit = { _, _ -> },
    val onOpenTask: (Long) -> Unit = {},
    val onDeleteTask: (Long) -> Unit = {},
    val onTaskMenu: (TaskItem) -> Unit = {},
    val onToggleHabit: (Long) -> Unit = {},
    val onIncrementHabit: (Long) -> Unit = {},
    val onCollapse: (DashboardSection, Boolean) -> Unit = { _, _ -> },
    val onNavigate: (String) -> Unit = {},
    val onStartFocus: () -> Unit = {},
    val onOpenNote: (Long) -> Unit = {},
    val onDismissReminder: (Long) -> Unit = {},
    val onQuickAdd: () -> Unit = {},
    val onAddReminder: (String, java.time.LocalDateTime, Boolean) -> Unit = { _, _, _ -> },
)

@Composable
fun TodayScreen(nav: AppNavigator, vm: TodayViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val quickAdd = LocalQuickAdd.current
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<TaskItem?>(null) }

    val actions = TodayActions(
        onToggleTask = { id, done -> vm.toggleTask(id, done) },
        onToggleSubtask = { id, done -> vm.toggleSubtask(id, done) },
        onOpenTask = nav::task,
        onDeleteTask = { id ->
            scope.launch {
                val deleted = vm.deleteTask(id) ?: return@launch
                if (snackbar.showSnackbar("Task deleted", actionLabel = "Undo", withDismissAction = true) == SnackbarResult.ActionPerformed) {
                    vm.restoreTask(deleted)
                }
            }
        },
        onTaskMenu = { menuFor = it },
        onToggleHabit = vm::toggleHabit,
        onIncrementHabit = vm::incrementHabit,
        onCollapse = vm::setCollapsed,
        onNavigate = { r -> if (r == Routes.CALENDAR || r == Routes.HABITS || r == Routes.NOTES) nav.topLevel(r) else nav.to(r) },
        onStartFocus = { vm.startFocus(); nav.to(Routes.FOCUS) },
        onOpenNote = nav::note,
        onDismissReminder = vm::dismissReminder,
        onQuickAdd = { quickAdd(QuickAddDefaults(date = state.today)) },
        onAddReminder = { title, at, pinned -> vm.addReminder(title, at, pinned) },
    )
    FabDefaults(QuickAddDefaults(date = state.today))
    TodayContent(state, actions)
    menuFor?.let { item -> TaskActionsSheet(item = item, onDismiss = { menuFor = null }, onOpen = { nav.task(item.id) }) }
}

@Composable
fun TodayContent(state: TodayUiState, actions: TodayActions) {
    val formats = LocalFormats.current
    val bar = rememberBarScroll()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DaylineTopBar(
                title = { Text(if (state.loading) "" else formats.weekdayFull(state.today.dayOfWeek)) },
                subtitle = { if (!state.loading) Text("${state.greeting} · ${formats.date(state.today)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    LabeledIconButton(Icons.Rounded.Search, "Search", { actions.onNavigate(Routes.SEARCH) })
                    LabeledIconButton(Icons.Rounded.Tune, "Customize dashboard", { actions.onNavigate(Routes.DASHBOARD_SETTINGS) })
                },
                scrollBehavior = bar,
            )
        },
    ) { inner ->
        ResponsiveBox(Modifier.padding(inner)) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                return@ResponsiveBox
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = Dimens.xs, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
            ) {
                state.sections.forEach { section ->
                    when (section) {
                        DashboardSection.PROGRESS -> item(key = "progress", contentType = "card") { ProgressCard(state) }
                        DashboardSection.OVERDUE -> if (state.overdue.isNotEmpty()) item(key = "overdue", contentType = "tasks") { OverdueCard(state, actions) }
                        DashboardSection.TODAY -> item(key = "today", contentType = "tasks") { TodayTasksCard(state, actions) }
                        DashboardSection.INBOX -> item(key = "inbox", contentType = "tasks") { InboxCard(state, actions) }
                        DashboardSection.DEADLINES -> item(key = "deadlines", contentType = "tasks") { DeadlinesCard(state, actions) }
                        DashboardSection.FOCUS -> item(key = "focus", contentType = "card") { FocusCard(state, actions) }
                        DashboardSection.WEEKLY -> item(key = "weekly", contentType = "card") { WeeklyCard(state, actions) }
                        DashboardSection.QUOTE -> item(key = "quote", contentType = "card") { QuoteCard(state) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(state: TodayUiState) {
    val pct = (state.progress * 100).toInt()
    val dark = DaylineTheme.isDark
    DaylineCard(color = MaterialTheme.colorScheme.primaryContainer, brush = com.dayline.app.ui.theme.brandContainerBrush()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(
                progress = state.progress,
                modifier = Modifier.size(96.dp),
                strokeWidth = 11.dp,
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
            ) {
                Text("$pct%", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(Dimens.lg))
            Column(Modifier.weight(1f)) {
                val headline = when {
                    state.plannedCount == 0 -> "A clear day"
                    state.doneCount == state.plannedCount -> "All done for today"
                    else -> "${state.doneCount} of ${state.plannedCount} done"
                }
                Text(headline, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.height(2.dp))
                val remaining = state.todayTasks.size + state.overdue.size
                val parts = buildList {
                    if (remaining > 0) add("$remaining task${if (remaining == 1) "" else "s"} left")
                    val habitsLeft = state.habits.count { !it.done }
                    if (habitsLeft > 0) add("$habitsLeft habit${if (habitsLeft == 1) "" else "s"} to go")
                    if (state.overdue.isNotEmpty()) add("${state.overdue.size} overdue")
                    if (state.plannedCount == 0) add("Nothing scheduled yet")
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                Spacer(Modifier.height(Dimens.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.md)) {
                    MiniStat(Icons.Rounded.Timer, LocalFormats.current.minutes(state.focusMinutesToday))
                    if (state.habits.isNotEmpty()) MiniStat(Icons.Rounded.SelfImprovement, "${state.habitsDone}/${state.habits.size}")
                    if (state.overdue.isNotEmpty()) MiniStat(Icons.Rounded.WarningAmber, "${state.overdue.size}", StatusColors.overdue(dark))
                }
            }
        }
    }
}

@Composable
private fun MiniStat(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

@Composable
private fun TaskRows(items: List<TaskItem>, state: TodayUiState, actions: TodayActions, showDate: Boolean, showSubtasks: Boolean = false) {
    items.forEach { item ->
        TaskRow(
            item = item,
            today = state.today,
            onToggle = { actions.onToggleTask(item.id, it) },
            onClick = { actions.onOpenTask(item.id) },
            onDelete = { actions.onDeleteTask(item.id) },
            onLongClick = { actions.onTaskMenu(item) },
            showDate = showDate,
        )
        if (showSubtasks && !item.task.isCompleted) {
            item.incompleteSubtasks.take(3).forEach { s ->
                SubtaskLine(s.title, s.isDone, priorityColor(item.task.priority), { actions.onToggleSubtask(s.id, it) })
            }
        }
    }
}

@Composable
private fun OverdueCard(state: TodayUiState, actions: TodayActions) {
    val c = StatusColors.overdue(DaylineTheme.isDark)
    val s = DashboardSection.OVERDUE
    CollapsibleCard(
        title = "Overdue",
        icon = Icons.Rounded.WarningAmber,
        collapsed = s in state.collapsed,
        onToggle = { actions.onCollapse(s, s !in state.collapsed) },
        badge = state.overdue.size.toString(),
        accent = c,
    ) {
        TaskRows(state.overdue, state, actions, showDate = true)
    }
}

@Composable
private fun TodayTasksCard(state: TodayUiState, actions: TodayActions) {
    val s = DashboardSection.TODAY
    var showDone by remember { mutableStateOf(false) }
    CollapsibleCard(
        title = "Today",
        icon = Icons.Rounded.WbSunny,
        collapsed = s in state.collapsed,
        onToggle = { actions.onCollapse(s, s !in state.collapsed) },
        badge = state.todayTasks.size.takeIf { it > 0 }?.toString(),
    ) {
        if (state.todayTasks.isEmpty()) {
            InlineEmpty(if (state.completedToday.isEmpty()) "Nothing planned for today. Add a task or enjoy the calm." else "Everything planned for today is done.")
        } else if (state.groupByPriority) {
            Priority.entries.reversed().forEach { p ->
                val group = state.todayTasks.filter { it.task.priority == p }
                if (group.isNotEmpty()) {
                    Row(Modifier.padding(start = Dimens.md, top = Dimens.sm, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(priorityColor(p)))
                        Spacer(Modifier.width(Dimens.sm))
                        Text(p.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = priorityColor(p))
                    }
                    TaskRows(group, state, actions, showDate = false, showSubtasks = true)
                }
            }
        } else {
            TaskRows(state.todayTasks, state, actions, showDate = false, showSubtasks = true)
        }
        TextButton(onClick = actions.onQuickAdd, modifier = Modifier.padding(start = Dimens.xs)) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Dimens.xs))
            Text("Add to today")
        }
        if (state.completedToday.isNotEmpty()) {
            TextButton(onClick = { showDone = !showDone }, modifier = Modifier.padding(start = Dimens.xs)) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Dimens.xs))
                Text(if (showDone) "Hide completed" else "Completed today (${state.completedToday.size})")
            }
            if (showDone) TaskRows(state.completedToday, state, actions, showDate = false)
        }
    }
}

@Composable
private fun DeadlinesCard(state: TodayUiState, actions: TodayActions) {
    val s = DashboardSection.DEADLINES
    CollapsibleCard(
        title = "Upcoming deadlines",
        icon = Icons.Rounded.Upcoming,
        collapsed = s in state.collapsed,
        onToggle = { actions.onCollapse(s, s !in state.collapsed) },
        badge = state.deadlines.size.takeIf { it > 0 }?.toString(),
        accent = MaterialTheme.colorScheme.tertiary,
        trailing = { TextButton(onClick = { actions.onNavigate(Routes.UPCOMING) }) { Text("All") } },
    ) {
        if (state.deadlines.isEmpty()) InlineEmpty("No deadlines in the next 7 days.")
        else TaskRows(state.deadlines.take(6), state, actions, showDate = true)
    }
}

@Composable
private fun FocusCard(state: TodayUiState, actions: TodayActions) {
    val s = DashboardSection.FOCUS
    val formats = LocalFormats.current
    val running = state.timer?.status == TimerStatus.RUNNING
    CollapsibleCard(
        title = "Focus",
        icon = Icons.Rounded.Timer,
        collapsed = s in state.collapsed,
        onToggle = { actions.onCollapse(s, s !in state.collapsed) },
        accent = MaterialTheme.colorScheme.tertiary,
    ) {
        Column(Modifier.padding(horizontal = Dimens.sm)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formats.minutes(state.focusMinutesToday), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(Dimens.sm))
                Text("of ${formats.minutes(state.focusGoalMinutes)} goal", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(Dimens.sm))
            WavyBar(state.focusMinutesToday.toFloat() / state.focusGoalMinutes.coerceAtLeast(1), color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(Dimens.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when {
                        running -> "${state.timer?.phase?.label} in progress"
                        state.focusSessionsToday > 0 -> "${state.focusSessionsToday} session${if (state.focusSessionsToday > 1) "s" else ""} completed"
                        else -> "No sessions yet today"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = { if (running) actions.onNavigate(Routes.FOCUS) else actions.onStartFocus() }) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Dimens.xs))
                    Text(if (running) "Open" else "Start")
                }
            }
        }
    }
}

@Composable
private fun WeeklyCard(state: TodayUiState, actions: TodayActions) {
    val s = DashboardSection.WEEKLY
    CollapsibleCard(
        title = "This week",
        icon = Icons.Rounded.BarChart,
        collapsed = s in state.collapsed,
        onToggle = { actions.onCollapse(s, s !in state.collapsed) },
        trailing = { TextButton(onClick = { actions.onNavigate(Routes.STATS) }) { Text("Stats") } },
    ) {
        Column(Modifier.padding(horizontal = Dimens.sm)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${state.weekTotal}", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(Dimens.sm))
                val delta = state.weekTotal - state.lastWeekTotal
                val cmp = when {
                    state.lastWeekTotal == 0 && state.weekTotal == 0 -> "tasks completed"
                    state.lastWeekTotal == 0 -> "tasks completed · first week tracked"
                    delta >= 0 -> "completed · +$delta vs last week"
                    else -> "completed · $delta vs last week"
                }
                Text(cmp, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(Dimens.md))
            BarChart(
                values = state.weekCompleted,
                highlight = state.today,
                description = "Tasks completed each day this week: " + state.weekCompleted.joinToString { "${it.date.dayOfWeek}: ${it.value}" },
            )
        }
    }
}

@Composable
private fun InboxCard(state: TodayUiState, actions: TodayActions) {
    val s = DashboardSection.INBOX
    CollapsibleCard(
        title = "Inbox",
        icon = Icons.Rounded.Inbox,
        collapsed = s in state.collapsed,
        onToggle = { actions.onCollapse(s, s !in state.collapsed) },
        badge = state.inbox.size.takeIf { it > 0 }?.toString(),
        trailing = { TextButton(onClick = { actions.onNavigate(Routes.INBOX) }) { Text("All") } },
    ) {
        if (state.inbox.isEmpty()) InlineEmpty("Inbox zero. Ideas without a date or project land here.")
        else TaskRows(state.inbox.take(5), state, actions, showDate = false)
        if (state.inbox.size > 5) {
            TextButton(onClick = { actions.onNavigate(Routes.INBOX) }, modifier = Modifier.padding(start = Dimens.xs)) { Text("${state.inbox.size - 5} more in Inbox") }
        }
    }
}

@Composable
private fun QuoteCard(state: TodayUiState) {
    DaylineCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
        Icon(Icons.Rounded.FormatQuote, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        Spacer(Modifier.height(Dimens.xs))
        Text(state.quote.text, style = MaterialTheme.typography.titleMedium, fontStyle = FontStyle.Normal, color = MaterialTheme.colorScheme.onTertiaryContainer)
        Spacer(Modifier.height(Dimens.sm))
        Text("— ${state.quote.author}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.75f))
    }
}

@Composable
private fun NewReminderDialog(onDismiss: () -> Unit, onSave: (String, java.time.LocalDateTime, Boolean) -> Unit) {
    val formats = LocalFormats.current
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(java.time.LocalDate.now()) }
    var time by remember { mutableStateOf(java.time.LocalTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0)) }
    var pinned by remember { mutableStateOf(true) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val at = date.atTime(time)
    val valid = title.isNotBlank() && at.isAfter(java.time.LocalDateTime.now())
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                androidx.compose.material3.OutlinedTextField(title, { title = it }, label = { Text("What should we remind you about?") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    androidx.compose.material3.AssistChip(onClick = { pickDate = true }, label = { Text(formats.relative(date, java.time.LocalDate.now())) })
                    androidx.compose.material3.AssistChip(onClick = { pickTime = true }, label = { Text(formats.time(time)) })
                }
                if (!at.isAfter(java.time.LocalDateTime.now())) Text("Pick a time in the future", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Pin to dashboard", modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(pinned, { pinned = it })
                }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onSave(title, at, pinned); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickDate) com.dayline.app.ui.components.DatePickerDialogHost(date, onDismiss = { pickDate = false }) { date = it }
    if (pickTime) com.dayline.app.ui.components.TimePickerDialogHost(time, formats.is24Hour, onDismiss = { pickTime = false }) { time = it }
}
