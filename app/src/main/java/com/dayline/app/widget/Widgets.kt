package com.dayline.app.widget

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.dayline.app.MainActivity
import com.dayline.app.R
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.prefs.SettingsRepository
import com.dayline.app.core.data.repository.HabitRepository
import com.dayline.app.core.data.repository.TaskRepository
import com.dayline.app.core.domain.FocusPhase
import com.dayline.app.core.domain.FocusTimerState
import com.dayline.app.core.domain.HabitStreakCalculator
import com.dayline.app.core.domain.TimerStatus
import com.dayline.app.core.model.Priority
import com.dayline.app.focus.FocusTimerController
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

// ---------------------------------------------------------------- data access

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun tasks(): TaskRepository
    fun habits(): HabitRepository
    fun focus(): FocusTimerController
    fun settings(): SettingsRepository
    fun time(): TimeProvider
    fun clock(): com.dayline.app.core.common.AppClock
    fun focusSessions(): com.dayline.app.core.data.repository.FocusRepository
}

internal fun Context.widgetDeps(): WidgetEntryPoint = EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)

/** Refreshes every Dayline widget; called whenever tasks, habits or the timer change. */
object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        runCatching { TasksWidget().updateAll(context) }
        runCatching { HabitsWidget().updateAll(context) }
        runCatching { FocusWidget().updateAll(context) }
        runCatching { ProgressWidget().updateAll(context) }
        runCatching { NextUpWidget().updateAll(context) }
        runCatching { HabitFlowerWidget().updateAll(context) }
    }

    suspend fun refreshTaskWidgets(context: Context) {
        runCatching { TasksWidget().updateAll(context) }
        runCatching { NextUpWidget().updateAll(context) }
    }
}

internal val RouteKey = ActionParameters.Key<String>(MainActivity.EXTRA_ROUTE)
internal val TaskIdKey = ActionParameters.Key<Long>("taskId")
private val HabitIdKey = ActionParameters.Key<Long>("habitId")

internal fun openRoute(route: String) = actionStartActivity<MainActivity>(actionParametersOf(RouteKey to route))

/** Opens the add-task sheet on top of the home screen, without launching the full app. */
internal fun quickAdd() = actionStartActivity<com.dayline.app.ui.quickadd.QuickAddActivity>()

internal fun style(size: TextUnit, color: ColorProvider, bold: Boolean = false) =
    TextStyle(fontSize = size, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium)

// ---------------------------------------------------------------- actions

/** Tasks ticked on the widget, shown as checked + struck through for a beat before they leave the list. */
object WidgetCompletions {
    val pending = kotlinx.coroutines.flow.MutableStateFlow<Set<Long>>(emptySet())
}

class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[TaskIdKey] ?: return
        // Same click as ticking a task in the app.
        com.dayline.app.ui.motion.Haptics(context).background()
        WidgetCompletions.pending.value = WidgetCompletions.pending.value + id
        // The widget that was tapped (task list or "Next up") redraws from the pending set.
        WidgetUpdater.refreshTaskWidgets(context)
        kotlinx.coroutines.delay(650)
        val result = context.widgetDeps().tasks().setCompleted(id, true)
        WidgetCompletions.pending.value = WidgetCompletions.pending.value - id
        if (result.blocked) {
            val blocker = context.widgetDeps().tasks().blockersOf(id).firstOrNull()?.title
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                android.widget.Toast.makeText(context, "Blocked: finish “$blocker” first", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
        WidgetUpdater.updateAll(context)
    }
}

class CheckHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HabitIdKey] ?: return
        val deps = context.widgetDeps()
        val habit = deps.habits().habits.first().firstOrNull { it.id == id } ?: return
        com.dayline.app.ui.motion.Haptics(context).background()
        val today = deps.time().today()
        if (habit.targetPerDay > 1) deps.habits().checkIn(id, today, 1) else deps.habits().toggle(id, today)
        WidgetUpdater.updateAll(context)
    }
}

class FocusToggleAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val focus = context.widgetDeps().focus()
        focus.restore()
        when (focus.state.value.status) {
            TimerStatus.RUNNING -> focus.pause()
            TimerStatus.PAUSED -> focus.resume()
            TimerStatus.IDLE -> focus.start()
        }.join()
        WidgetUpdater.updateAll(context)
    }
}

class FocusSkipAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val focus = context.widgetDeps().focus()
        focus.restore()
        focus.skip().join()
        WidgetUpdater.updateAll(context)
    }
}

class FocusResetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val focus = context.widgetDeps().focus()
        focus.restore()
        focus.reset().join()
        WidgetUpdater.updateAll(context)
    }
}

// ---------------------------------------------------------------- shared pieces

@Composable
private fun WidgetCard(modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    Box(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(24.dp)
            .background(GlanceTheme.colors.widgetBackground),
    ) {
        WidgetArtLayer()
        Box(GlanceModifier.fillMaxSize().padding(12.dp).then(modifier)) { content() }
    }
}

/** The widget's chosen background art (translucent shapes in the wallpaper colours), if any. */
@Composable
internal fun WidgetArtLayer() {
    val art = LocalWidgetArt.current
    if (art != 0) {
        Image(ImageProvider(art), contentDescription = null, contentScale = androidx.glance.layout.ContentScale.Crop, modifier = GlanceModifier.fillMaxSize())
    }
}

internal val LocalWidgetArt = androidx.compose.runtime.staticCompositionLocalOf { 0 }

internal fun artRes(style: String?): Int = when (style) {
    "waves" -> R.drawable.widget_art_waves
    "hills" -> R.drawable.widget_art_hills
    "rings" -> R.drawable.widget_art_rings
    "blobs" -> R.drawable.widget_art_blobs
    "leaves" -> R.drawable.widget_art_leaves
    "confetti" -> R.drawable.widget_art_confetti
    else -> 0
}

/** Provides [widget]'s background art (from Settings) to everything inside. */
@Composable
internal fun WithArt(deps: WidgetEntryPoint, widget: String, content: @Composable () -> Unit) {
    val art by deps.settings().widgetArt.collectAsState(com.dayline.app.core.data.prefs.WidgetArt.defaults)
    androidx.compose.runtime.CompositionLocalProvider(LocalWidgetArt provides artRes(art[widget])) { content() }
}

@Composable
internal fun Icon(res: Int, tint: ColorProvider, size: androidx.compose.ui.unit.Dp = 20.dp, modifier: GlanceModifier = GlanceModifier) {
    Image(ImageProvider(res), contentDescription = null, colorFilter = ColorFilter.tint(tint), modifier = modifier.size(size))
}

private fun priorityTint(p: Priority): ColorProvider = ColorProvider(
    when (p) {
        Priority.URGENT -> Color(0xFFE5484D)
        Priority.HIGH -> Color(0xFFF08C00)
        Priority.MEDIUM -> Color(0xFF4F5BD5)
        Priority.LOW -> Color(0xFF8A8F98)
    },
)

// ---------------------------------------------------------------- 4x3 today's tasks

data class WidgetTask(val id: Long, val title: String, val meta: String?, val overdue: Boolean, val priority: Priority, val completing: Boolean = false)

class TasksWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = context.widgetDeps()
        val initialItems = deps.tasks().items.first()
        val initialDay = deps.time().today()
        provideContent {
            // Collect live data so the widget follows changes made in the app (and vice versa)
            // for as long as the widget session is alive.
            val items by deps.tasks().items.collectAsState(initialItems)
            val today by deps.clock().today.collectAsState(initialDay)
            val pending by WidgetCompletions.pending.collectAsState()
            val rows = remember(items, today, pending) { buildTaskRows(items, today).map { it.copy(completing = it.id in pending) } }
            val header = today.dayOfWeek.getDisplayName(JTextStyle.FULL, Locale.getDefault())
            val date = today.format(DateTimeFormatter.ofPattern("MMM d"))
            WithArt(deps, "tasks") { GlanceTheme { TasksContent(header, date, rows) } }
        }
    }
}

internal fun buildTaskRows(all: List<com.dayline.app.core.model.TaskItem>, today: java.time.LocalDate): List<WidgetTask> {
    val items = all.filter { !it.task.isCompleted }
    val timeFmt = DateTimeFormatter.ofPattern("H:mm")
    val overdue = items.filter { it.task.isOverdue(today) }.sortedBy { it.task.dueDate }
    val dueToday = items.filter { !it.task.isOverdue(today) && (it.task.dueDate == today || (it.task.dueDate == null && it.task.startDate == today)) }
        .sortedWith(compareBy({ -it.task.priority.level }, { it.task.dueTime }))
    return overdue.map { WidgetTask(it.id, it.task.title, "Overdue", true, it.task.priority) } +
        dueToday.map { WidgetTask(it.id, it.task.title, it.task.dueTime?.format(timeFmt), false, it.task.priority) }
}

@Composable
private fun TasksContent(day: String, date: String, rows: List<WidgetTask>) {
    val c = GlanceTheme.colors
    WidgetCard {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(GlanceModifier.defaultWeight().clickable(openRoute("today"))) {
                    Text(day, style = style(20.sp, c.onSurface, bold = true), maxLines = 1)
                    Text(if (rows.isEmpty()) "$date · all clear" else "$date · ${rows.size} to do", style = style(12.sp, c.onSurfaceVariant), maxLines = 1)
                }
                Box(
                    GlanceModifier.size(40.dp).cornerRadius(14.dp).background(c.primary).clickable(quickAdd()),
                    contentAlignment = Alignment.Center,
                ) { Icon(R.drawable.ic_w_add, c.onPrimary, 22.dp) }
            }
            Spacer(GlanceModifier.height(10.dp))
            if (rows.isEmpty()) {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(R.drawable.ic_w_check, c.primary, 36.dp)
                        Spacer(GlanceModifier.height(6.dp))
                        Text("Nothing left for today", style = style(13.sp, c.onSurfaceVariant))
                    }
                }
            } else {
                LazyColumn(GlanceModifier.fillMaxSize()) {
                    items(rows, itemId = { it.id }) { t ->
                        Column {
                            Row(
                                GlanceModifier.fillMaxWidth().cornerRadius(14.dp).background(c.surfaceVariant).padding(start = 4.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    GlanceModifier.size(36.dp).clickable(actionRunCallback<ToggleTaskAction>(actionParametersOf(TaskIdKey to t.id))),
                                    contentAlignment = Alignment.Center,
                                ) { Icon(if (t.completing) R.drawable.ic_w_check else R.drawable.ic_w_circle, if (t.completing) c.primary else priorityTint(t.priority), 22.dp) }
                                Column(GlanceModifier.defaultWeight().clickable(openRoute("task/${t.id}"))) {
                                    Text(
                                        t.title,
                                        style = TextStyle(
                                            fontSize = 14.sp,
                                            color = if (t.completing) c.onSurfaceVariant else c.onSurface,
                                            fontWeight = FontWeight.Medium,
                                            textDecoration = if (t.completing) androidx.glance.text.TextDecoration.LineThrough else null,
                                        ),
                                        maxLines = 1,
                                    )
                                    t.meta?.let {
                                        Text(it, style = style(11.sp, if (t.overdue) ColorProvider(Color(0xFFE5484D)) else c.onSurfaceVariant), maxLines = 1)
                                    }
                                }
                            }
                            Spacer(GlanceModifier.height(6.dp))
                        }
                    }
                }
            }
        }
    }
}

class TasksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}

// ---------------------------------------------------------------- 2x2 habit streaks

data class WidgetHabit(val id: Long, val name: String, val done: Boolean, val streak: Int, val color: Int)

class HabitsWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = context.widgetDeps()
        val weekStart = deps.settings().current().weekStart
        val h0 = deps.habits().habits.first()
        val c0 = deps.habits().completions.first()
        val d0 = deps.time().today()
        provideContent {
            val habits by deps.habits().habits.collectAsState(h0)
            val completions by deps.habits().completions.collectAsState(c0)
            val today by deps.clock().today.collectAsState(d0)
            val model = remember(habits, completions, today) { buildHabitRows(habits, completions, today, weekStart) }
            WithArt(deps, "habits") { GlanceTheme { HabitsContent(model.first, model.second, model.third) } }
        }
    }
}

internal fun buildHabitRows(
    all: List<com.dayline.app.core.model.Habit>,
    completions: List<com.dayline.app.core.model.HabitCompletion>,
    today: java.time.LocalDate,
    weekStart: java.time.DayOfWeek,
): Triple<List<WidgetHabit>, Int, String?> {
    val habits = all.filter { !it.isArchived }
    val streaks = habits.associateWith { h -> HabitStreakCalculator.stats(h, completions.filter { it.habitId == h.id }, today, weekStart).currentStreak }
    val rows = habits.mapNotNull { h ->
        val comps = completions.filter { it.habitId == h.id }
        if (!HabitStreakCalculator.dueOn(h, today, comps, weekStart)) return@mapNotNull null
        val count = comps.firstOrNull { it.date == today }?.count ?: 0
        WidgetHabit(h.id, h.name, HabitStreakCalculator.isDone(h, count), streaks[h] ?: 0, h.color)
    }
    val best = streaks.maxByOrNull { it.value }
    return Triple(rows, best?.value ?: 0, best?.key?.name)
}

@Composable
private fun HabitsContent(rows: List<WidgetHabit>, best: Int, bestName: String?) {
    val c = GlanceTheme.colors
    val flame = ColorProvider(Color(0xFFF57C00))
    WidgetCard(GlanceModifier.clickable(openRoute("habits"))) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(R.drawable.ic_w_flame, flame, 26.dp)
                Spacer(GlanceModifier.width(4.dp))
                Text("$best", style = style(30.sp, c.onSurface, bold = true))
            }
            Text(
                if (best > 0 && bestName != null) "day streak · $bestName" else "Start a streak today",
                style = style(11.sp, c.onSurfaceVariant),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            Text("${rows.count { it.done }}/${rows.size} today", style = style(12.sp, c.onSurface), maxLines = 1)
            Spacer(GlanceModifier.height(6.dp))
            Row(GlanceModifier.fillMaxWidth()) {
                val shown = rows.take(if (LocalSize.current.width > 180.dp) 6 else 4)
                if (shown.isEmpty()) Text("No habits due", style = style(11.sp, c.onSurfaceVariant))
                shown.forEach { h ->
                    Box(
                        GlanceModifier
                            .size(30.dp)
                            .cornerRadius(15.dp)
                            .background(if (h.done) ColorProvider(Color(h.color)) else c.surfaceVariant)
                            .clickable(actionRunCallback<CheckHabitAction>(actionParametersOf(HabitIdKey to h.id))),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (h.done) Icon(R.drawable.ic_w_check, ColorProvider(Color.White), 18.dp)
                        else Text(h.name.take(1).uppercase(), style = style(13.sp, c.onSurfaceVariant, bold = true))
                    }
                    Spacer(GlanceModifier.width(5.dp))
                }
            }
        }
    }
}

class HabitsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitsWidget()
}

// ---------------------------------------------------------------- 4x2 focus timer

class FocusWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = context.widgetDeps()
        deps.focus().restore()
        provideContent {
            val state by deps.focus().state.collectAsState()
            WithArt(deps, "focus") { GlanceTheme { FocusContent(context, state, deps.time().nowMillis()) } }
        }
    }
}

@Composable
private fun FocusContent(context: Context, state: FocusTimerState, now: Long) {
    val c = GlanceTheme.colors
    val remaining = state.remaining(now).coerceAtLeast(0L)
    val running = state.status == TimerStatus.RUNNING
    val size = LocalSize.current
    val pad = 12.dp
    val gap = 8.dp
    val innerW = size.width - pad * 2
    val innerH = size.height - pad * 2
    // 60%: the time. 40%: bento controls.
    val timerW = (innerW - gap) * 0.6f
    val controlsW = innerW - gap - timerW

    // Round up like a kitchen timer: a fresh 25-minute session reads 25:00, not 24:59.
    val shownSeconds = (remaining + 999) / 1000
    val hours = shownSeconds >= 3600
    val text = if (hours) "%d:%02d:%02d".format(shownSeconds / 3600, (shownSeconds % 3600) / 60, shownSeconds % 60)
    else "%02d:%02d".format(shownSeconds / 60, shownSeconds % 60)
    val digits = if (android.os.Build.VERSION.SDK_INT >= 31) {
        timerDigits(context, text, hours, running, remaining, timerW.value, innerH.value)
    } else legacyTimerDigits(context, innerH, hours, running, remaining)
    WidgetCard(GlanceModifier.padding(0.dp)) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                GlanceModifier.width(timerW).fillMaxHeight().clickable(openRoute("focus")),
                contentAlignment = Alignment.Center,
            ) { AndroidRemoteViews(digits) }
            Spacer(GlanceModifier.width(gap))
            // Bento: play/pause across the full 40% (top half), reset + next side by side below.
            Column(GlanceModifier.width(controlsW).fillMaxHeight()) {
                Box(
                    GlanceModifier.fillMaxWidth().defaultWeight().cornerRadius(20.dp).background(c.primary).clickable(actionRunCallback<FocusToggleAction>()),
                    contentAlignment = Alignment.Center,
                ) { Icon(if (running) R.drawable.ic_w_pause else R.drawable.ic_w_play, c.onPrimary, 30.dp) }
                Spacer(GlanceModifier.height(gap))
                Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    Box(
                        GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(18.dp).background(c.secondaryContainer).clickable(actionRunCallback<FocusResetAction>()),
                        contentAlignment = Alignment.Center,
                    ) { Icon(R.drawable.ic_w_reset, c.onSecondaryContainer, 22.dp) }
                    Spacer(GlanceModifier.width(gap))
                    Box(
                        GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(18.dp).background(c.secondaryContainer).clickable(actionRunCallback<FocusSkipAction>()),
                        contentAlignment = Alignment.Center,
                    ) { Icon(R.drawable.ic_w_skip, c.onSecondaryContainer, 22.dp) }
                }
            }
        }
    }
}

/**
 * Android 12+: two crop windows over the same monospace countdown, each half the widget's height,
 * with the digits sized so they fill it (bold). Monospace digits are 0.6 em wide and about 0.72 em
 * tall, which is what the maths below relies on.
 */
@androidx.annotation.RequiresApi(31)
private fun timerDigits(context: Context, text: String, hours: Boolean, running: Boolean, remaining: Long, widthDp: Float, heightDp: Float): RemoteViews {
    val dip = android.util.TypedValue.COMPLEX_UNIT_DIP
    val density = context.resources.displayMetrics.density
    val rowH = heightDp / 2f
    // Measure this phone's own Black-weight digits (the home screen draws them with the same font),
    // so the crop windows line up exactly whatever the system font is.
    val paint = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
        fontFeatureSettings = "tnum"
        textSize = 100f * density
    }
    val digitEm = ('0'..'9').maxOf { paint.measureText(it.toString()) } / (100f * density)
    val colonEm = paint.measureText(":") / (100f * density)
    val bounds = android.graphics.Rect().also { paint.getTextBounds("0123456789", 0, 10, it) }
    val capEm = bounds.height() / (100f * density)
    // Digits fill ~78% of each half (clear headroom so nothing is clipped), never wider than the 60% area.
    val ts = minOf(rowH * 0.78f / capEm, widthDp * 0.92f / (2 * digitEm))
    val digit = digitEm * ts
    val colon = colonEm * ts
    val boxH = ts * 1.4f
    val textW = text.count { it.isDigit() } * digit + text.count { it == ':' } * colon + 4f
    val v = RemoteViews(context.packageName, R.layout.widget_timer_dyn)
    // Which characters each window shows: "MM:SS" → MM | SS, "H:MM:SS" → H | MM.
    val topWidth = if (hours) digit else 2 * digit
    val bottomStart = if (hours) digit + colon else 2 * digit + colon
    // Centre the digits themselves (not the line box) in each half.
    val nudge = ((paint.fontMetrics.descent + paint.fontMetrics.ascent) / 2f + bounds.height() / 2f) / (100f * density) * ts
    listOf(
        Triple(R.id.frame_top, listOf(R.id.chrono_top, R.id.static_top), 0f to topWidth),
        Triple(R.id.frame_bottom, listOf(R.id.chrono_bottom, R.id.static_bottom), bottomStart to 2 * digit),
    ).forEach { (frame, views, range) ->
        v.setViewLayoutWidth(frame, range.second, dip)
        v.setViewLayoutHeight(frame, rowH, dip)
        views.forEach { id ->
            v.setTextViewTextSize(id, dip, ts)
            v.setViewLayoutWidth(id, textW, dip)
            v.setViewLayoutHeight(id, boxH, dip)
            v.setViewLayoutMargin(id, RemoteViews.MARGIN_START, -range.first, dip)
            v.setViewLayoutMargin(id, RemoteViews.MARGIN_TOP, (rowH - boxH) / 2f + nudge, dip)
        }
    }
    if (running) {
        val base = SystemClock.elapsedRealtime() + remaining + 999
        listOf(R.id.chrono_top, R.id.chrono_bottom).forEach { id ->
            v.setChronometer(id, base, null, true)
            v.setChronometerCountDown(id, true)
            v.setViewVisibility(id, android.view.View.VISIBLE)
        }
        listOf(R.id.static_top, R.id.static_bottom).forEach { v.setViewVisibility(it, android.view.View.GONE) }
    } else {
        // Not counting: show the exact time as text (a stopped Chronometer drifts to 24:59).
        listOf(R.id.chrono_top, R.id.chrono_bottom).forEach { id ->
            v.setChronometer(id, SystemClock.elapsedRealtime(), null, false)
            v.setViewVisibility(id, android.view.View.GONE)
        }
        listOf(R.id.static_top, R.id.static_bottom).forEach { id ->
            v.setTextViewText(id, text)
            v.setViewVisibility(id, android.view.View.VISIBLE)
        }
    }
    return v
}

/** Android 8–11: fixed-size layouts (no runtime sizing there). */
private fun legacyTimerDigits(context: Context, innerH: androidx.compose.ui.unit.Dp, hours: Boolean, running: Boolean, remaining: Long): RemoteViews {
    val variant = when {
        innerH >= 190.dp -> "xl"
        innerH >= 150.dp -> "l"
        innerH >= 118.dp -> "m"
        else -> "s"
    }
    val layout = when (variant to hours) {
        "xl" to false -> R.layout.widget_timer_xl_ms
        "xl" to true -> R.layout.widget_timer_xl_hm
        "l" to false -> R.layout.widget_timer_l_ms
        "l" to true -> R.layout.widget_timer_l_hm
        "m" to false -> R.layout.widget_timer_m_ms
        "m" to true -> R.layout.widget_timer_m_hm
        "s" to true -> R.layout.widget_timer_s_hm
        else -> R.layout.widget_timer_s_ms
    }
    // While stopped, a little extra keeps the frozen display from reading one second short.
    val base = SystemClock.elapsedRealtime() + remaining + if (running) 999 else 1_500
    return RemoteViews(context.packageName, layout).apply {
        listOf(R.id.chrono_top, R.id.chrono_bottom).forEach { id ->
            setChronometer(id, base, null, running)
            setChronometerCountDown(id, true)
        }
    }
}

class FocusWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FocusWidget()
}

// ---------------------------------------------------------------- 2x2 daily progress

class ProgressWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = context.widgetDeps()
        val zone = deps.time().zone()
        val weekStart = deps.settings().current().weekStart
        val i0 = deps.tasks().items.first()
        val h0 = deps.habits().habits.first()
        val c0 = deps.habits().completions.first()
        val d0 = deps.time().today()
        provideContent {
            val items by deps.tasks().items.collectAsState(i0)
            val habits by deps.habits().habits.collectAsState(h0)
            val completions by deps.habits().completions.collectAsState(c0)
            val today by deps.clock().today.collectAsState(d0)
            val open = items.count { !it.task.isCompleted && (it.task.isOverdue(today) || it.task.dueDate == today || (it.task.dueDate == null && it.task.startDate == today)) }
            val done = items.count { it.task.isCompleted && it.task.completedAt?.atZone(zone)?.toLocalDate() == today }
            // Today's habits count too, like the dashboard.
            val habitRows = remember(habits, completions, today) { buildHabitRows(habits, completions, today, weekStart).first }
            val habitsDone = habitRows.count { it.done }
            WithArt(deps, "progress") { GlanceTheme { ProgressContent(done + habitsDone, open + done + habitRows.size) } }
        }
    }
}

@Composable
private fun ProgressContent(done: Int, total: Int) {
    val c = GlanceTheme.colors
    val pct = if (total == 0) 0 else done * 100 / total
    WidgetCard(GlanceModifier.clickable(openRoute("today"))) {
        Column(GlanceModifier.fillMaxSize()) {
            Text("Today", style = style(13.sp, c.onSurfaceVariant))
            Text("$pct%", style = style(34.sp, c.primary, bold = true))
            Spacer(GlanceModifier.defaultWeight())
            Text(if (total == 0) "Nothing planned" else "$done of $total done", style = style(12.sp, c.onSurface), maxLines = 1)
            Spacer(GlanceModifier.height(6.dp))
            LinearProgressIndicator(
                progress = if (total == 0) 0f else done.toFloat() / total,
                modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                color = c.primary,
                backgroundColor = c.surfaceVariant,
            )
        }
    }
}

class ProgressWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ProgressWidget()
}

// ---------------------------------------------------------------- 1x1 quick add

class QuickAddWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                Box(
                    GlanceModifier.fillMaxSize().appWidgetBackground().cornerRadius(22.dp).background(GlanceTheme.colors.primaryContainer).clickable(quickAdd()),
                    contentAlignment = Alignment.Center,
                ) { Icon(R.drawable.ic_w_add, GlanceTheme.colors.onPrimaryContainer, 30.dp) }
            }
        }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}

