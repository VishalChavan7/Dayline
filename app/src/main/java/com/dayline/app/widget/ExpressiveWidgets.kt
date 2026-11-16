package com.dayline.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.text.FontWeight
import androidx.glance.unit.ColorProvider
import com.dayline.app.R
import com.dayline.app.core.domain.TimerStatus
import kotlinx.coroutines.flow.first
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

/*
 * Five Material 3 Expressive widgets. Each one is a single expressive shape (cookie, flower, sunny
 * burst, clover, pill) floating on the wallpaper, tinted with the dynamic theme, with one number
 * that matters in the middle.
 */

// ---------------------------------------------------------------- shared

/** A shape from res/drawable tinted with [tint], centred and kept square, with content on top. */
@Composable
private fun ShapeWidget(shape: Int, tint: ColorProvider, modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    Box(GlanceModifier.fillMaxSize().then(modifier), contentAlignment = Alignment.Center) {
        Image(
            ImageProvider(shape), contentDescription = null,
            // Android 12+: the shape is already filled with the wallpaper (dynamic) colour.
            colorFilter = if (android.os.Build.VERSION.SDK_INT >= 31) null else ColorFilter.tint(tint),
            contentScale = ContentScale.Fit,
            modifier = GlanceModifier.fillMaxSize(),
        )
        content()
    }
}

/** Text scale for a square shape widget: the shape's side in dp, divided into readable steps. */
@Composable
private fun side(): Float {
    val s = LocalSize.current
    return minOf(s.width.value, s.height.value).coerceAtLeast(80f)
}

private fun bold(size: Float, color: ColorProvider) =
    TextStyle(fontSize = size.sp, color = color, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)

private fun medium(size: Float, color: ColorProvider) =
    TextStyle(fontSize = size.sp, color = color, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)

// ---------------------------------------------------------------- 1. Next up (pill)

class NextUpWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = context.widgetDeps()
        val i0 = deps.tasks().items.first()
        val d0 = deps.time().today()
        provideContent {
            val items by deps.tasks().items.collectAsState(i0)
            val today by deps.clock().today.collectAsState(d0)
            val pending by WidgetCompletions.pending.collectAsState()
            val rows = remember(items, today) { buildTaskRows(items, today) }
            WithArt(deps, "next_up") { GlanceTheme { NextUp(rows.firstOrNull(), rows.size, pending) } }
        }
    }
}

@Composable
private fun NextUp(next: WidgetTask?, count: Int, pending: Set<Long>) {
    val c = GlanceTheme.colors
    val h = LocalSize.current.height
    val pill = (h / 2).coerceAtMost(48.dp)
    val completing = next != null && next.id in pending
    Box(GlanceModifier.fillMaxSize().cornerRadius(pill).background(c.primaryContainer)) {
    WidgetArtLayer()
    Row(
        GlanceModifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val btn = (h - 16.dp).coerceIn(36.dp, 56.dp)
        Box(
            GlanceModifier.size(btn).cornerRadius(btn / 2).background(c.primary)
                .clickable(if (next == null) quickAdd() else actionRunCallback<ToggleTaskAction>(actionParametersOf(TaskIdKey to next.id))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                when {
                    next == null -> R.drawable.ic_w_add
                    completing -> R.drawable.ic_w_check
                    else -> R.drawable.ic_w_circle
                },
                c.onPrimary, 22.dp,
            )
        }
        Spacer(GlanceModifier.width(12.dp))
        Column(
            GlanceModifier.defaultWeight().clickable(if (next == null) openRoute("today") else openRoute("task/${next.id}")),
        ) {
            Text(
                when {
                    next == null -> "All clear"
                    next.overdue -> "Overdue"
                    else -> "Next up" + (next.meta?.let { " · $it" } ?: "")
                },
                style = TextStyle(fontSize = 12.sp, color = if (next?.overdue == true) c.error else c.onPrimaryContainer, fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Text(
                next?.title ?: "Nothing left for today",
                style = TextStyle(
                    fontSize = 16.sp, color = c.onPrimaryContainer, fontWeight = FontWeight.Bold,
                    textDecoration = if (completing) androidx.glance.text.TextDecoration.LineThrough else null,
                ),
                maxLines = 1,
            )
        }
        if (count > 1) {
            Box(
                GlanceModifier.height(28.dp).cornerRadius(14.dp).background(c.surface).padding(horizontal = 10.dp).clickable(openRoute("today")),
                contentAlignment = Alignment.Center,
            ) { Text("+${count - 1}", style = TextStyle(fontSize = 12.sp, color = c.onSurface, fontWeight = FontWeight.Bold)) }
            Spacer(GlanceModifier.width(8.dp))
        }
    }
    }
}

class NextUpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextUpWidget()
}

// ---------------------------------------------------------------- 2. Habit flower (streak)

class HabitFlowerWidget : GlanceAppWidget() {
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
            GlanceTheme { HabitFlower(model.second, model.first.count { it.done }, model.first.size) }
        }
    }
}

@Composable
private fun HabitFlower(best: Int, done: Int, due: Int) {
    val c = GlanceTheme.colors
    val k = side() / 150f
    ShapeWidget(R.drawable.shape_flower, c.primaryContainer, GlanceModifier.clickable(openRoute("habits"))) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(R.drawable.ic_w_flame, c.primary, (22f * k).dp)
            Text("$best", style = bold(32f * k, c.onPrimaryContainer))
            Text(if (due == 0) "day streak" else "$done/$due today", style = medium(11f * k, c.onPrimaryContainer), maxLines = 1)
        }
    }
}

class HabitFlowerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitFlowerWidget()
}
