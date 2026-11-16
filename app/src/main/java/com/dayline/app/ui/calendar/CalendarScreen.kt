package com.dayline.app.ui.calendar

import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.dayline.app.ui.components.DaylineTopBar
import com.dayline.app.ui.components.SegmentedPills
import com.dayline.app.ui.components.rememberBarScroll
import com.dayline.app.ui.navigation.FabDefaults

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dayline.app.core.domain.DateRules
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.components.EmptyState
import com.dayline.app.ui.components.ResponsiveBox
import com.dayline.app.ui.components.TaskRow
import com.dayline.app.ui.components.priorityColor
import com.dayline.app.ui.navigation.AppNavigator
import com.dayline.app.ui.navigation.LocalQuickAdd
import com.dayline.app.ui.quickadd.QuickAddDefaults
import com.dayline.app.ui.theme.DaylineTheme
import com.dayline.app.ui.theme.Dimens
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlin.math.roundToInt

data class CalendarActions(
    val onSelect: (LocalDate) -> Unit = {},
    val onView: (CalendarView) -> Unit = {},
    val onToday: () -> Unit = {},
    val onShift: (Int) -> Unit = {},
    val onToggle: (Long, Boolean) -> Unit = { _, _ -> },
    val onOpen: (Long) -> Unit = {},
    val onReschedule: (Long, LocalDate) -> Unit = { _, _ -> },
    val onAdd: (LocalDate) -> Unit = {},
)

@Composable
fun CalendarScreen(nav: AppNavigator, vm: CalendarViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val quickAdd = LocalQuickAdd.current
    FabDefaults(QuickAddDefaults(date = state.selected))
    CalendarContent(
        state,
        CalendarActions(
            onSelect = vm::select,
            onView = vm::setView,
            onToday = vm::goToday,
            onShift = { vm.shift(it) },
            onToggle = { id, d -> vm.toggle(id, d) },
            onOpen = nav::task,
            onReschedule = { id, d -> vm.reschedule(id, d) },
            onAdd = { d -> quickAdd(QuickAddDefaults(date = d)) },
        ),
    )
}

@Composable
fun CalendarContent(state: CalendarUiState, actions: CalendarActions) {
    val formats = LocalFormats.current
    // The calendar header stays put: collapsing it re-measured the month grid on every scroll frame.
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DaylineTopBar(
                title = {
                    Text(
                        when (state.view) {
                            CalendarView.YEAR -> state.selected.year.toString()
                            CalendarView.DAY -> formats.relative(state.selected, state.today)
                            else -> formats.monthYear(state.selected)
                        },
                        maxLines = 1,
                    )
                },
                subtitle = {
                    val n = state.on(state.selected).size
                    Text(
                        if (state.selected == state.today) "Today · $n task${if (n == 1) "" else "s"}" else "${formats.relative(state.selected, state.today)} · $n task${if (n == 1) "" else "s"}",
                        maxLines = 1,
                    )
                },
                actions = {
                    IconButton(onClick = { actions.onShift(-1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Previous") }
                    IconButton(onClick = { actions.onShift(1) }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Next") }
                    IconButton(onClick = actions.onToday) { Icon(Icons.Rounded.Today, contentDescription = "Jump to today") }
                },
            )
        },
    ) { inner ->
        ResponsiveBox(Modifier.padding(inner)) {
            Column(Modifier.fillMaxSize()) {
                ViewSwitcher(state.view, actions.onView)
                if (state.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                } else {
                    Box(
                        Modifier.fillMaxSize().pointerInput(state.view) {
                            // Month view pages itself (smooth, finger-following); other views keep the swipe.
                            if (state.view == CalendarView.MONTH) return@pointerInput
                            var total = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { total = 0f },
                                onDragEnd = { if (total > 120f) actions.onShift(-1) else if (total < -120f) actions.onShift(1) },
                            ) { _, dx -> total += dx }
                        },
                    ) {
                        when (state.view) {
                            CalendarView.MONTH -> MonthView(state, actions)
                            CalendarView.WEEK -> WeekView(state, actions)
                            CalendarView.DAY -> DayView(state, actions)
                            CalendarView.AGENDA -> AgendaView(state, actions)
                            CalendarView.YEAR -> YearView(state, actions)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader(state: CalendarUiState) {
    val formats = LocalFormats.current
    Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.sm, vertical = Dimens.xs)) {
        DateRules.orderedWeekdays(state.weekStart).forEach { d ->
            Text(
                formats.weekdayShort(d),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

private val PagerBase: java.time.YearMonth = java.time.YearMonth.of(2000, 1)
private const val PagerMonths = 12 * 200

@Composable
private fun MonthView(state: CalendarUiState, actions: CalendarActions) {
    val selectedMonth = java.time.YearMonth.from(state.selected)
    fun pageOf(m: java.time.YearMonth) = java.time.temporal.ChronoUnit.MONTHS.between(PagerBase, m).toInt().coerceIn(0, PagerMonths - 1)
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = pageOf(selectedMonth)) { PagerMonths }
    val latest by androidx.compose.runtime.rememberUpdatedState(state)
    // Swiped to another month → select it (today if it's this month, else the 1st).
    LaunchedEffect(pager) {
        androidx.compose.runtime.snapshotFlow { pager.settledPage }.collect { page ->
            val m = PagerBase.plusMonths(page.toLong())
            val cur = java.time.YearMonth.from(latest.selected)
            if (m != cur) actions.onSelect(if (m == java.time.YearMonth.from(latest.today)) latest.today else m.atDay(1))
        }
    }
    // Arrows / "today" changed the month → glide there.
    LaunchedEffect(selectedMonth) {
        val target = pageOf(selectedMonth)
        if (pager.currentPage != target && !pager.isScrollInProgress) {
            if (kotlin.math.abs(pager.currentPage - target) > 2) pager.scrollToPage(target)
            else pager.animateScrollToPage(target, animationSpec = androidx.compose.animation.core.tween(380, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0f, 0f, 1f)))
        }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        item(key = "grid", contentType = "grid") {
            WeekdayHeader(state)
            androidx.compose.foundation.pager.HorizontalPager(
                state = pager,
                beyondViewportPageCount = 1,
                key = { it },
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val m = PagerBase.plusMonths(page.toLong())
                val grid = remember(m, state.weekStart) { DateRules.monthGrid(m.atDay(1), state.weekStart) }
                Column(Modifier.padding(horizontal = Dimens.sm)) {
                    grid.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                MonthCell(
                                    date = date,
                                    inMonth = date.month == m.month,
                                    selected = date == state.selected,
                                    isToday = date == state.today,
                                    entries = state.on(date),
                                    hasFocus = state.focus[date].orEmpty().isNotEmpty(),
                                    onClick = { actions.onSelect(date) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
        item(key = "dayHeader") { DayAgendaHeader(state.selected, state, actions) }
        val entries = state.on(state.selected)
        if (entries.isEmpty()) {
            item(key = "none") { NoTasksForDay(state.selected, actions) }
        } else {
            items(entries, key = { "${it.item.id}-${it.date}-${it.isProjection}" }) { e -> EntryRow(e, state, actions) }
        }
    }
}

@Composable
private fun MonthCell(
    date: LocalDate, inMonth: Boolean, selected: Boolean, isToday: Boolean, entries: List<CalendarEntry>, hasFocus: Boolean,
    onClick: () -> Unit, modifier: Modifier,
) {
    val open = entries.filter { !it.item.task.isCompleted }
    val desc = "${date.dayOfMonth}, ${entries.size} task${if (entries.size == 1) "" else "s"}"
    Column(
        modifier
            .height(64.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .semantics { contentDescription = desc }
            .padding(top = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isToday || selected) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isToday -> MaterialTheme.colorScheme.onPrimary
                    selected -> MaterialTheme.colorScheme.onPrimaryContainer
                    inMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                },
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            open.take(3).forEach { e ->
                Box(Modifier.size(6.dp).clip(CircleShape).background(priorityColor(e.item.task.priority).copy(alpha = if (e.isProjection) 0.45f else 1f)))
            }
            if (open.isEmpty() && entries.isNotEmpty()) Box(Modifier.size(6.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape))
            if (hasFocus) Box(Modifier.size(6.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.tertiary))
        }
        if (open.size > 3) Text("+${open.size - 3}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayAgendaHeader(date: LocalDate, state: CalendarUiState, actions: CalendarActions) {
    val formats = LocalFormats.current
    Row(Modifier.fillMaxWidth().padding(start = Dimens.lg, end = Dimens.sm, top = Dimens.lg), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(formats.relative(date, state.today), style = MaterialTheme.typography.titleLarge)
            val focus = state.focus[date].orEmpty().sumOf { it.minutes }
            val n = state.on(date).size
            Text(
                listOfNotNull("$n task${if (n == 1) "" else "s"}", if (focus > 0) "${formats.minutes(focus)} focused" else null).joinToString(" · "),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NoTasksForDay(date: LocalDate, actions: CalendarActions) {
    Text(
        "Nothing scheduled. Tap + to plan something.",
        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.lg, vertical = Dimens.lg),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun EntryRow(e: CalendarEntry, state: CalendarUiState, actions: CalendarActions) {
    Box(Modifier.padding(horizontal = Dimens.sm)) {
        if (e.isProjection) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Dimens.rowRadius)).clickable { actions.onOpen(e.item.id) }.padding(horizontal = Dimens.md, vertical = Dimens.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Repeat, contentDescription = "Repeats", tint = priorityColor(e.item.task.priority), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Dimens.md))
                Column {
                    Text(e.item.task.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Repeats · ${e.item.task.recurrence?.describe().orEmpty()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            TaskRow(e.item, state.today, onToggle = { actions.onToggle(e.item.id, it) }, onClick = { actions.onOpen(e.item.id) }, showDate = false)
        }
    }
}

/** Seven columns; long-press a task and drag it sideways to another day to reschedule. */
@Composable
private fun WeekView(state: CalendarUiState, actions: CalendarActions) {
    val days = DateRules.weekDays(state.selected, state.weekStart)
    val formats = LocalFormats.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 120.dp)) {
        Text(
            "Long-press a task and drag it to another day to reschedule.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimens.lg, vertical = Dimens.xs),
        )
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Dimens.xs)) {
            val colWidth = maxWidth / 7
            val colPx = with(LocalDensity.current) { colWidth.toPx() }
            Row(Modifier.fillMaxWidth()) {
                days.forEach { d ->
                    val isToday = d == state.today
                    val selected = d == state.selected
                    Column(
                        Modifier
                            .width(colWidth)
                            .heightIn(min = 420.dp)
                            .padding(horizontal = 1.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow)
                            .clickable { actions.onSelect(d) }
                            .padding(vertical = Dimens.xs),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(formats.weekdayNarrow(d.dayOfWeek), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Box(Modifier.size(26.dp).clip(CircleShape).background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent), contentAlignment = Alignment.Center) {
                            Text("${d.dayOfMonth}", style = MaterialTheme.typography.labelLarge, color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                        Spacer(Modifier.height(4.dp))
                        state.focus[d].orEmpty().takeIf { it.isNotEmpty() }?.let { blocks ->
                            WeekChip("${blocks.sumOf { it.minutes }}m", MaterialTheme.colorScheme.tertiary, icon = true)
                        }
                        state.on(d).forEach { e -> DraggableWeekChip(e, d, colPx, actions) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DraggableWeekChip(e: CalendarEntry, date: LocalDate, colPx: Float, actions: CalendarActions) {
    var dx by remember { mutableFloatStateOf(0f) }
    var dy by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val canDrag = !e.isProjection && !e.item.task.isCompleted
    Box(
        Modifier
            .zIndex(if (dragging) 10f else 0f)
            .offset { IntOffset(dx.roundToInt(), dy.roundToInt()) }
            .graphicsLayer { if (dragging) { scaleX = 1.08f; scaleY = 1.08f } }
            .then(
                if (canDrag) Modifier.pointerInput(e.item.id, date) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            val shift = (dx / colPx).roundToInt()
                            if (shift != 0) actions.onReschedule(e.item.id, date.plusDays(shift.toLong()))
                            dx = 0f; dy = 0f; dragging = false
                        },
                        onDragCancel = { dx = 0f; dy = 0f; dragging = false },
                    ) { change, amount -> change.consume(); dx += amount.x; dy += amount.y }
                } else Modifier,
            )
            .clickable { actions.onOpen(e.item.id) },
    ) {
        WeekChip(
            e.item.task.title,
            priorityColor(e.item.task.priority),
            done = e.item.task.isCompleted,
            faded = e.isProjection,
        )
    }
}

@Composable
private fun WeekChip(text: String, color: Color, done: Boolean = false, faded: Boolean = false, icon: Boolean = false) {
    val dark = DaylineTheme.isDark
    Row(
        Modifier
            .padding(horizontal = 1.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = if (faded) 0.10f else if (dark) 0.28f else 0.16f))
            .padding(horizontal = 2.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon) Icon(Icons.Rounded.Timer, contentDescription = "Focus", tint = color, modifier = Modifier.size(10.dp))
        Text(
            text,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 3,
            // Narrow phones give each day ~44dp: break long words at syllables ("Pub-lish") instead of
            // chopping them at an arbitrary letter ("Publis/h").
            style = androidx.compose.ui.text.TextStyle(
                hyphens = androidx.compose.ui.text.style.Hyphens.Auto,
                lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph,
            ),
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (done || faded) 0.55f else 1f),
            textDecoration = if (done) TextDecoration.LineThrough else null,
        )
    }
}

private val HourHeight: Dp = 60.dp

/** All-day tasks on top, then a 24-hour timeline with timed tasks and focus sessions. */
@Composable
private fun DayView(state: CalendarUiState, actions: CalendarActions) {
    val formats = LocalFormats.current
    val date = state.selected
    val entries = state.on(date)
    val allDay = entries.filter { it.time == null }
    val timed = entries.filter { it.time != null }
    val focus = state.focus[date].orEmpty()
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(date) { scroll.scrollTo(with(density) { (HourHeight * 7).toPx().toInt() }) }

    Column(Modifier.fillMaxSize()) {
        DayAgendaHeader(date, state, actions)
        if (allDay.isNotEmpty()) {
            Text("All day", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = Dimens.lg, top = Dimens.sm))
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                allDay.forEach { EntryRow(it, state, actions) }
            }
            HorizontalDivider(Modifier.padding(vertical = Dimens.xs))
        }
        Box(Modifier.fillMaxSize().verticalScroll(scroll)) {
            Column {
                (0..23).forEach { h ->
                    Row(Modifier.height(HourHeight)) {
                        Text(
                            formats.time(LocalTime.of(h, 0)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(64.dp).padding(start = Dimens.sm),
                            maxLines = 1,
                        )
                        HorizontalDivider(Modifier.padding(top = 7.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
                Spacer(Modifier.height(120.dp))
            }
            val left = 68.dp
            focus.forEach { f ->
                val top = HourHeight * (f.start.toSecondOfDay() / 3600f)
                val h = (HourHeight * ((f.end.toSecondOfDay() - f.start.toSecondOfDay()) / 3600f)).coerceAtLeast(22.dp)
                Box(
                    Modifier
                        .padding(start = left, end = Dimens.lg)
                        .offset(y = top + 8.dp)
                        .fillMaxWidth()
                        .height(h)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = Dimens.sm, vertical = 2.dp),
                ) {
                    Text("${f.label} · ${f.minutes}m", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 1)
                }
            }
            timed.forEachIndexed { index, e ->
                val t = e.item.task
                val start = t.startTime?.takeIf { t.startDate == date || t.dueDate == null } ?: e.time!!
                val end = if (t.startTime != null && t.dueTime != null && t.startDate == t.dueDate) t.dueTime else start.plusMinutes(45)
                val top = HourHeight * (start.toSecondOfDay() / 3600f)
                val h = (HourHeight * ((end.toSecondOfDay() - start.toSecondOfDay()).coerceAtLeast(1800) / 3600f))
                val c = priorityColor(t.priority)
                Row(
                    Modifier
                        .padding(start = left + 12.dp * (index % 2), end = Dimens.lg)
                        .offset(y = top + 8.dp)
                        .fillMaxWidth()
                        .height(h)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.copy(alpha = if (DaylineTheme.isDark) 0.30f else 0.16f))
                        .clickable { actions.onOpen(e.item.id) }
                        .padding(start = 0.dp),
                ) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(c))
                    Column(Modifier.padding(horizontal = Dimens.sm, vertical = 4.dp)) {
                        Text(t.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textDecoration = if (t.isCompleted) TextDecoration.LineThrough else null)
                        Text(formats.time(start), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (date == state.today) {
                val now = LocalTime.now()
                Box(
                    Modifier
                        .padding(start = left - 6.dp)
                        .offset(y = HourHeight * (now.toSecondOfDay() / 3600f) + 8.dp)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.error),
                )
            }
        }
    }
}

@Composable
private fun AgendaView(state: CalendarUiState, actions: CalendarActions) {
    val formats = LocalFormats.current
    val days = state.entries.keys.filter { !it.isBefore(state.selected) }.sorted()
    if (days.isEmpty()) {
        EmptyState(Icons.Rounded.EventAvailable, "A clear horizon", "Nothing scheduled in the next two months.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        days.forEach { d ->
            item(key = "h$d") {
                Row(Modifier.fillMaxWidth().padding(start = Dimens.lg, end = Dimens.lg, top = Dimens.lg, bottom = Dimens.xs), verticalAlignment = Alignment.Bottom) {
                    Text(formats.relative(d, state.today), style = MaterialTheme.typography.titleMedium,
                        color = if (d == state.today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Text(formats.date(d), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.focus[d]?.takeIf { it.isNotEmpty() }?.let { blocks ->
                item(key = "f$d") {
                    Row(Modifier.padding(horizontal = Dimens.xl, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Dimens.sm))
                        Text("Focused ${formats.minutes(blocks.sumOf { it.minutes })} in ${blocks.size} session${if (blocks.size > 1) "s" else ""}",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(state.on(d), key = { "${it.item.id}-${it.date}-${it.isProjection}" }) { e -> EntryRow(e, state, actions) }
        }
    }
}

@Composable
private fun YearView(state: CalendarUiState, actions: CalendarActions) {
    val formats = LocalFormats.current
    val max = (state.yearCounts.values.maxOrNull() ?: 0).coerceAtLeast(1)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = when {
            maxWidth >= 840.dp -> 4
            maxWidth >= 560.dp -> 3
            else -> 2
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(cols),
            contentPadding = PaddingValues(start = Dimens.md, end = Dimens.md, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.md),
            verticalArrangement = Arrangement.spacedBy(Dimens.md),
        ) {
            items((1..12).toList()) { m ->
                val ym = YearMonth.of(state.selected.year, m)
                val first = ym.atDay(1)
                Column(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .clickable { actions.onSelect(first); actions.onView(CalendarView.MONTH) }
                        .padding(Dimens.sm),
                ) {
                    Text(formats.monthShort(ym.month), style = MaterialTheme.typography.titleSmall,
                        color = if (state.today.year == ym.year && state.today.month == ym.month) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                    DateRules.monthGrid(first, state.weekStart).chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { d ->
                                val inMonth = d.month == ym.month
                                val count = if (inMonth) state.yearCounts[d] ?: 0 else 0
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f).padding(1.dp).clip(CircleShape)
                                        .background(
                                            when {
                                                !inMonth -> Color.Transparent
                                                d == state.today -> MaterialTheme.colorScheme.primary
                                                count > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f + 0.5f * count / max)
                                                else -> Color.Transparent
                                            },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (inMonth) Text("${d.dayOfMonth}", fontSize = 8.sp, lineHeight = 8.sp,
                                        color = if (d == state.today) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** View switcher: equal-width segments with a gliding thumb; always fits, even at 320 dp. */
@Composable
private fun ViewSwitcher(current: CalendarView, onView: (CalendarView) -> Unit) {
    SegmentedPills(
        options = listOf(CalendarView.MONTH, CalendarView.WEEK, CalendarView.YEAR),
        selected = current,
        label = { it.label },
        onSelect = onView,
        modifier = Modifier.padding(start = Dimens.lg, end = Dimens.lg, top = Dimens.xs, bottom = Dimens.lg),
    )
}
