package com.dayline.app.ui.habits

import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.dayline.app.ui.components.DaylineTopBar
import com.dayline.app.ui.components.rememberBarScroll

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Percent
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dayline.app.core.domain.DateRules
import com.dayline.app.core.domain.HabitStreakCalculator
import com.dayline.app.core.model.HabitFrequency
import com.dayline.app.ui.common.IconCatalog
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.components.ColorSwatches
import com.dayline.app.ui.components.ConfirmDialog
import com.dayline.app.ui.components.DaylineCard
import com.dayline.app.ui.components.EmptyState
import com.dayline.app.ui.components.IconPicker
import com.dayline.app.ui.components.ItemIconBadge
import com.dayline.app.ui.components.ResponsiveBox
import com.dayline.app.ui.components.SectionLabel
import com.dayline.app.ui.components.StatTile
import com.dayline.app.ui.components.TimePickerDialogHost
import com.dayline.app.ui.navigation.AppNavigator
import com.dayline.app.ui.theme.DaylineTheme
import com.dayline.app.ui.theme.Dimens
import com.dayline.app.ui.theme.Palette
import com.dayline.app.ui.theme.StatusColors
import com.dayline.app.ui.theme.containerOf
import com.dayline.app.ui.theme.contentOf
import java.time.LocalDate

data class HabitsActions(
    val onToggle: (Long, LocalDate) -> Unit = { _, _ -> },
    val onIncrement: (Long, LocalDate, Int) -> Unit = { _, _, _ -> },
    val onOpen: (Long) -> Unit = {},
    val onNew: () -> Unit = {},
)

@Composable
fun HabitsScreen(nav: AppNavigator, vm: HabitsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    HabitsContent(state, HabitsActions(onToggle = { id, d -> vm.toggle(id, d) }, onIncrement = { id, d, n -> vm.increment(id, d, n) }, onOpen = nav::habit, onNew = { nav.editHabit(0) }))
}

@Composable
fun HabitsContent(state: HabitsUiState, actions0: HabitsActions) {
    val haptics = com.dayline.app.ui.motion.rememberHaptics()
    // Same "done" vibration as completing a task.
    val actions = actions0.copy(
        onToggle = { id, d ->
            val row = state.active.firstOrNull { it.habit.id == id }
            val count = row?.week?.firstOrNull { it.first == d }?.second ?: if (d == state.today) row?.todayCount ?: 0 else 0
            val wasDone = row != null && com.dayline.app.core.domain.HabitStreakCalculator.isDone(row.habit, count)
            haptics.complete(!wasDone)
            actions0.onToggle(id, d)
        },
        onIncrement = { id, d, n -> haptics.complete(n > 0); actions0.onIncrement(id, d, n) },
    )
    val scroll = rememberBarScroll()
    var showArchived by remember { mutableStateOf(false) }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DaylineTopBar(
                title = { Text("Habits") },
                subtitle = {
                    if (!state.loading) Text(if (state.dueToday > 0) "${state.doneToday} of ${state.dueToday} done today" else "Nothing due today")
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            com.dayline.app.ui.components.PillFab("New Habit", actions.onNew)
        },
    ) { inner ->
        ResponsiveBox(Modifier.padding(inner)) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                state.active.isEmpty() && state.archived.isEmpty() -> EmptyState(
                    Icons.Rounded.SelfImprovement, "Build a habit", "Small daily actions add up. Create a habit and check in each day to grow your streak.",
                    lift = com.dayline.app.ui.components.EMPTY_PAGE_LIFT,
                ) { TextButton(onClick = actions.onNew) { Text("Create your first habit") } }
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = Dimens.lg, end = Dimens.lg, bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(Dimens.md),
                ) {
                    item { WeekHeader(state) }
                    items(state.active, key = { it.habit.id }) { row -> HabitCard(row, state, actions) }
                    if (state.archived.isNotEmpty()) {
                        item {
                            TextButton(onClick = { showArchived = !showArchived }) {
                                Text(if (showArchived) "Hide archived" else "Archived (${state.archived.size})")
                            }
                        }
                        if (showArchived) items(state.archived, key = { "a${it.habit.id}" }) { row -> HabitCard(row, state, actions) }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekHeader(state: HabitsUiState) {
    val formats = LocalFormats.current
    Row(Modifier.fillMaxWidth().padding(start = 56.dp, end = Dimens.md)) {
        Spacer(Modifier.weight(1f))
        DateRules.weekDays(state.today, state.weekStart).forEach { d ->
            Text(
                formats.weekdayNarrow(d.dayOfWeek),
                modifier = Modifier.width(30.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = if (d == state.today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HabitCard(row: HabitRowState, state: HabitsUiState, actions: HabitsActions) {
    val h = row.habit
    val dark = DaylineTheme.isDark
    val accent = contentOf(h.color, dark)
    DaylineCard(onClick = { actions.onOpen(h.id) }, contentPadding = PaddingValues(start = Dimens.md, end = Dimens.md, top = Dimens.lg, bottom = Dimens.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemIconBadge(IconCatalog.get(h.icon), h.color, size = 46.dp)
            Spacer(Modifier.width(Dimens.md))
            Column(Modifier.weight(1f)) {
                Text(h.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = if (row.stats.currentStreak > 0) StatusColors.streak(dark) else MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp))
                    Text(
                        " ${streakText(row.stats.currentStreak, row.stats.streakUnit)} · ${frequencyLabel(row, withTarget = h.targetPerDay == 1)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (!h.isArchived) {
                if (h.targetPerDay > 1) {
                    CounterButton(row, accent) { delta -> actions.onIncrement(h.id, state.today, delta) }
                } else {
                    CheckButton(row.doneToday, h.color) { actions.onToggle(h.id, state.today) }
                }
            }
        }
        Spacer(Modifier.height(Dimens.md))
        Row(Modifier.fillMaxWidth().padding(start = 56.dp)) {
            Spacer(Modifier.weight(1f))
            // A circle for every day of the week — past, today and upcoming — so the week reads at a glance.
            row.week.forEach { (date, count) ->
                val done = HabitStreakCalculator.isDone(h, count)
                val scheduled = HabitStreakCalculator.isScheduled(h, date)
                val future = date.isAfter(state.today)
                Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    done -> accent
                                    count > 0 -> containerOf(h.color, dark)
                                    else -> Color.Transparent
                                },
                            )
                            .border(
                                1.5.dp,
                                when {
                                    done -> Color.Transparent
                                    future || !scheduled -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                                    else -> MaterialTheme.colorScheme.outlineVariant
                                },
                                CircleShape,
                            )
                            .clickable(enabled = !future && !h.isArchived) { actions.onToggle(h.id, date) }
                            .semantics { contentDescription = "${date.dayOfWeek}: ${if (done) "done" else "not done"}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (done) Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.surface, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

/** "1 day", "3 days", "1 week"… */
fun streakText(n: Int, unit: String): String = "$n ${if (n == 1) unit.removeSuffix("s") else unit}"

private fun frequencyLabel(row: HabitRowState, withTarget: Boolean = true): String {
    val h = row.habit
    val base = when (h.frequency) {
        HabitFrequency.DAILY -> "Daily"
        HabitFrequency.WEEKLY_TARGET -> "${h.timesPerWeek}× a week"
        HabitFrequency.SPECIFIC_DAYS -> h.weekdays.sortedBy { it.value }.joinToString(" ") { it.name.take(2).lowercase().replaceFirstChar(Char::uppercase) }
    }
    return if (withTarget && h.targetPerDay > 1) "$base · ${h.targetPerDay}/day" else base
}

@Composable
private fun CheckButton(done: Boolean, color: Int, onClick: () -> Unit) {
    val dark = DaylineTheme.isDark
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (done) contentOf(color, dark) else containerOf(color, dark),
        modifier = Modifier.size(48.dp).semantics { contentDescription = if (done) "Done today, tap to undo" else "Check in for today" },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = if (done) MaterialTheme.colorScheme.surface else contentOf(color, dark))
        }
    }
}

@Composable
private fun CounterButton(row: HabitRowState, accent: Color, onDelta: (Int) -> Unit) {
    // Compact on narrow phones so the habit name keeps its room (320dp screens otherwise cut "Drink water").
    val compact = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360 ||
        androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.15f
    run {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onDelta(-1) }, enabled = row.todayCount > 0, modifier = if (compact) Modifier.size(36.dp) else Modifier) {
                Icon(Icons.Rounded.Remove, contentDescription = "Remove one")
            }
            Text("${row.todayCount}/${row.habit.targetPerDay}", style = MaterialTheme.typography.titleSmall, color = accent)
            if (compact) Spacer(Modifier.width(4.dp))
            FilledTonalIconButton(onClick = { onDelta(1) }, modifier = if (compact) Modifier.size(40.dp) else Modifier) {
                Icon(Icons.Rounded.Add, contentDescription = "Add one")
            }
        }
    }
}

// ---------------- Habit detail ----------------

@Composable
fun HabitDetailScreen(nav: AppNavigator, vm: HabitDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val detailHaptics = com.dayline.app.ui.motion.rememberHaptics()
    var confirmDelete by remember { mutableStateOf(false) }
    val formats = LocalFormats.current
    LaunchedEffect(state.missing) { if (state.missing) nav.back() }
    val row = state.row
    val bar = rememberBarScroll()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DaylineTopBar(
                scrollBehavior = bar,
                title = { Text(row?.habit?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    if (row != null) {
                        IconButton(onClick = { nav.editHabit(row.habit.id) }) { Icon(Icons.Rounded.Edit, "Edit habit") }
                        IconButton(onClick = { vm.archive(!row.habit.isArchived) }) {
                            Icon(if (row.habit.isArchived) Icons.Rounded.Unarchive else Icons.Rounded.Archive, if (row.habit.isArchived) "Restore habit" else "Archive habit")
                        }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.DeleteOutline, "Delete habit") }
                    }
                },
            )
        },
    ) { inner ->
        if (row == null) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        val h = row.habit
        val dark = DaylineTheme.isDark
        ResponsiveBox(Modifier.padding(inner)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg).padding(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemIconBadge(IconCatalog.get(h.icon), h.color, size = 56.dp)
                    Spacer(Modifier.width(Dimens.md))
                    Column(Modifier.weight(1f)) {
                        Text(frequencyLabel(row), style = MaterialTheme.typography.titleMedium)
                        if (h.description.isNotBlank()) Text(h.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        h.reminderTime?.let {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Notifications, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(" ${formats.time(it)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    StatTile("${row.stats.currentStreak}", "Current streak (${row.stats.streakUnit})", Modifier.weight(1f), Icons.Rounded.LocalFireDepartment, StatusColors.streak(dark))
                    StatTile("${row.stats.longestStreak}", "Longest streak", Modifier.weight(1f), Icons.Rounded.EmojiEvents, contentOf(h.color, dark))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    StatTile("${(state.consistency30 * 100).toInt()}%", "Consistency · 30 days", Modifier.weight(1f), Icons.Rounded.Percent, MaterialTheme.colorScheme.primary)
                    StatTile("${row.stats.totalCompletions}", "Total check-ins", Modifier.weight(1f), Icons.Rounded.TaskAlt, MaterialTheme.colorScheme.tertiary)
                }
                DaylineCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formats.monthYear(state.month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.shiftMonth(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month") }
                        IconButton(onClick = { vm.shiftMonth(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month") }
                    }
                    Row(Modifier.fillMaxWidth()) {
                        DateRules.orderedWeekdays(state.weekStart).forEach { d ->
                            Text(formats.weekdayNarrow(d), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    DateRules.monthGrid(state.month, state.weekStart).chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { d ->
                                val inMonth = d.month == state.month.month
                                val count = state.counts[d] ?: 0
                                val done = HabitStreakCalculator.isDone(h, count)
                                val future = d.isAfter(state.today)
                                Box(Modifier.weight(1f).aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
                                    if (inMonth) {
                                        Box(
                                            Modifier.fillMaxSize().clip(CircleShape)
                                                .background(if (done) contentOf(h.color, dark) else if (count > 0) containerOf(h.color, dark) else Color.Transparent)
                                                .border(if (d == state.today) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                .clickable(enabled = !future && !d.isBefore(h.startDate)) { detailHaptics.complete(!done); vm.toggle(d) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelMedium,
                                                color = when {
                                                    done -> MaterialTheme.colorScheme.surface
                                                    future || !HabitStreakCalculator.isScheduled(h, d) -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                })
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Text("Tap a past day to fix a missed check-in.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Dimens.sm))
                }
                DaylineCard {
                    Text("This week", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(Dimens.sm))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.week.forEach { (d, c) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(formats.weekdayNarrow(d.dayOfWeek), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    Modifier.size(32.dp).clip(RoundedCornerShape(10.dp))
                                        .background(if (HabitStreakCalculator.isDone(h, c)) contentOf(h.color, dark) else MaterialTheme.colorScheme.surfaceContainerHighest),
                                    contentAlignment = Alignment.Center,
                                ) { if (h.targetPerDay > 1 && c > 0) Text("$c", style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                    Spacer(Modifier.height(Dimens.sm))
                    Text("90-day consistency: ${(state.consistency90 * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SectionLabel("Recent history")
                val history = state.counts.filter { it.value > 0 }.toList().sortedByDescending { it.first }.take(14)
                if (history.isEmpty()) Text("No check-ins yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Dimens.lg))
                history.forEach { (d, c) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.lg, vertical = 4.dp)) {
                        Text(formats.relative(d, state.today), modifier = Modifier.weight(1f))
                        Text(if (h.targetPerDay > 1) "$c/${h.targetPerDay}" else "Done", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    if (confirmDelete) ConfirmDialog("Delete habit?", "This deletes the habit and all of its check-in history. Archive it instead to keep the history.", "Delete",
        onConfirm = { vm.delete { nav.back() } }, onDismiss = { confirmDelete = false })
}

// ---------------- Habit editor ----------------

@Composable
fun HabitEditorScreen(nav: AppNavigator, vm: HabitEditorViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val formats = LocalFormats.current
    var pickTime by remember { mutableStateOf(false) }
    LaunchedEffect(state.saved) { if (state.saved) nav.back() }
    val h = state.habit
    val bar = rememberBarScroll()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DaylineTopBar(
                scrollBehavior = bar,
                title = { Text(if (state.isNew) "New habit" else "Edit habit") },
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { TextButton(onClick = vm::save, enabled = !state.loading) { Text("Save") } },
            )
        },
    ) { inner ->
        if (state.loading) { Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { LoadingIndicator() }; return@Scaffold }
        ResponsiveBox(Modifier.padding(inner)) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.lg).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ItemIconBadge(IconCatalog.get(h.icon), h.color, size = 56.dp)
                    Spacer(Modifier.width(Dimens.md))
                    OutlinedTextField(h.name, { v -> vm.update { it.copy(name = v) } }, Modifier.weight(1f), label = { Text("Name") }, singleLine = true,
                        isError = state.error != null && h.name.isBlank(), shape = RoundedCornerShape(16.dp))
                }
                OutlinedTextField(h.description, { v -> vm.update { it.copy(description = v) } }, Modifier.fillMaxWidth(), label = { Text("Description (optional)") }, shape = RoundedCornerShape(16.dp))
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                SectionLabel("How often", Modifier.padding(horizontal = 0.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    HabitFrequency.entries.forEach { f -> FilterChip(h.frequency == f, { vm.update { it.copy(frequency = f) } }, { Text(f.label) }) }
                }
                when (h.frequency) {
                    HabitFrequency.SPECIFIC_DAYS -> FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        DateRules.orderedWeekdays(formats.weekStart).forEach { d ->
                            FilterChip(d in h.weekdays, { vm.update { it.copy(weekdays = if (d in it.weekdays) it.weekdays - d else it.weekdays + d) } }, { Text(formats.weekdayShort(d)) })
                        }
                    }
                    HabitFrequency.WEEKLY_TARGET -> Stepper("Times per week", h.timesPerWeek, 1..7) { n -> vm.update { it.copy(timesPerWeek = n) } }
                    HabitFrequency.DAILY -> Unit
                }
                Stepper("Daily target (check-ins)", h.targetPerDay, 1..30) { n -> vm.update { it.copy(targetPerDay = n) } }

                SectionLabel("Reminder", Modifier.padding(horizontal = 0.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickTime = true }) {
                        Icon(Icons.Rounded.Notifications, null)
                        Spacer(Modifier.width(Dimens.sm))
                        Text(h.reminderTime?.let { formats.time(it) } ?: "Add reminder")
                    }
                    if (h.reminderTime != null) TextButton(onClick = { vm.update { it.copy(reminderTime = null) } }) { Text("Remove") }
                }

                SectionLabel("Colour", Modifier.padding(horizontal = 0.dp))
                ColorSwatches(Palette.itemColors, h.color, { c -> vm.update { it.copy(color = c) } })
                SectionLabel("Icon", Modifier.padding(horizontal = 0.dp))
                IconPicker(IconCatalog.icons, h.icon, h.color, { k -> vm.update { it.copy(icon = k) } })
                Spacer(Modifier.height(Dimens.xxxl))
            }
        }
    }
    if (pickTime) TimePickerDialogHost(h.reminderTime, formats.is24Hour, onDismiss = { pickTime = false }) { t -> vm.update { it.copy(reminderTime = t) } }
}

@Composable
fun Stepper(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - 1).coerceIn(range)) }, enabled = value > range.first) { Icon(Icons.Rounded.Remove, "Decrease $label") }
        Text("$value", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange((value + 1).coerceIn(range)) }, enabled = value < range.last) { Icon(Icons.Rounded.Add, "Increase $label") }
    }
}
