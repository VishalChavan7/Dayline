package com.dayline.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dayline.app.core.domain.DateRules
import com.dayline.app.core.domain.DayValue
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.theme.Dimens
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
private fun rememberEntrance(key: Any?): Animatable<Float, *> {
    val a = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { a.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) }
    return a
}

/** Vertical bars with day labels. Highlights [highlight] (usually today). */
@Composable
fun BarChart(
    values: List<DayValue>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    highlight: LocalDate? = null,
    labelEvery: Int = 1,
    valueSuffix: String = "",
    description: String,
) {
    val formats = LocalFormats.current
    val max = (values.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    val anim = rememberEntrance(values)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val muted = color.copy(alpha = 0.45f)
    Column(modifier.semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * 0.56f).coerceAtMost(28.dp.toPx())
            values.forEachIndexed { i, v ->
                val x = slot * i + (slot - barW) / 2f
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2f))
                val h = size.height * (v.value.toFloat() / max) * anim.value
                if (v.value > 0) {
                    drawRoundRect(
                        if (highlight == null || v.date == highlight) color else muted,
                        Offset(x, size.height - h), Size(barW, h.coerceAtLeast(barW)), CornerRadius(barW / 2f),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            values.forEachIndexed { i, v ->
                Text(
                    if (i % labelEvery == 0) (if (values.size <= 7) formats.weekdayNarrow(v.date.dayOfWeek) else v.date.dayOfMonth.toString()) else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = if (v.date == highlight) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Smooth line chart with a soft fill, used for trends. */
@Composable
fun LineChart(
    values: List<DayValue>,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.tertiary,
    description: String,
) {
    val anim = rememberEntrance(values)
    val grid = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.fillMaxWidth().height(120.dp).semantics { contentDescription = description }) {
        if (values.isEmpty()) return@Canvas
        val max = (values.maxOf { it.value }).coerceAtLeast(1).toFloat()
        val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
        for (g in 0..3) {
            val y = size.height * g / 3f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        val pts = values.mapIndexed { i, v -> Offset(stepX * i, size.height - size.height * (v.value / max) * anim.value) }
        val line = Path().apply {
            moveTo(pts.first().x, pts.first().y)
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]
                val p1 = pts[i]
                val cx = (p0.x + p1.x) / 2f
                cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
            }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(pts.last().x, size.height)
            lineTo(pts.first().x, size.height)
            close()
        }
        drawPath(fill, color.copy(alpha = 0.14f))
        drawPath(line, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        pts.lastOrNull()?.let { drawCircle(color, 5.dp.toPx(), it) }
    }
}

data class DonutSlice(val label: String, val value: Int, val color: Color)

@Composable
fun DonutChart(slices: List<DonutSlice>, modifier: Modifier = Modifier, centerLabel: String, centerCaption: String) {
    val total = slices.sumOf { it.value }.coerceAtLeast(1)
    val anim = rememberEntrance(slices)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val desc = slices.joinToString { "${it.label}: ${it.value}" }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth > 340.dp
        val chart: @Composable () -> Unit = {
            Box(Modifier.size(140.dp).semantics { contentDescription = "Priority distribution. $desc" }, contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 22.dp.toPx()
                    val d = size.minDimension - stroke
                    val tl = Offset((size.width - d) / 2, (size.height - d) / 2)
                    drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
                    var start = -90f
                    val gap = if (slices.count { it.value > 0 } > 1) 3f else 0f
                    slices.filter { it.value > 0 }.forEach { s ->
                        val sweep = 360f * s.value / total * anim.value
                        drawArc(s.color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Butt))
                        start += sweep
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(centerLabel, style = MaterialTheme.typography.headlineSmall)
                    Text(centerCaption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val legend: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                slices.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(s.color))
                        Spacer(Modifier.width(Dimens.sm))
                        Text(s.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(72.dp), maxLines = 1)
                        Text("${s.value}", style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) { chart(); legend() }
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { chart(); Spacer(Modifier.height(Dimens.lg)); legend() }
        }
    }
}

/**
 * GitHub-style calendar heatmap: columns are weeks, rows are weekdays in the user's order.
 * Values of -1 mark future days (drawn empty).
 */
@Composable
fun CalendarHeatmap(values: List<DayValue>, weekStart: DayOfWeek, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val formats = LocalFormats.current
    val max = (values.maxOfOrNull { it.value } ?: 0).coerceAtLeast(1)
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val weeks = values.chunked(7)
    val total = values.filter { it.value > 0 }.sumOf { it.value }
    Column(modifier.semantics { contentDescription = "Completion heatmap: $total tasks completed over ${weeks.size} weeks" }) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(end = 6.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                DateRules.orderedWeekdays(weekStart).forEachIndexed { i, d ->
                    Box(Modifier.height(14.dp), contentAlignment = Alignment.CenterStart) {
                        Text(if (i % 2 == 0) formats.weekdayNarrow(d) else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                weeks.forEach { week ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEach { v ->
                            val c = when {
                                v.value < 0 -> Color.Transparent
                                v.value == 0 -> empty
                                else -> color.copy(alpha = 0.25f + 0.75f * (v.value.toFloat() / max))
                            }
                            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(c))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Dimens.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(4.dp))
            listOf(0f, 0.35f, 0.6f, 0.85f, 1f).forEach { a ->
                Box(Modifier.padding(horizontal = 1.dp).size(10.dp).clip(RoundedCornerShape(3.dp)).background(if (a == 0f) empty else color.copy(alpha = a)))
            }
            Spacer(Modifier.width(4.dp))
            Text("More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Horizontal labelled bar (project-wise progress, focus per project). */
@Composable
fun LabeledProgress(label: String, value: String, fraction: Float, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
            Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(10.dp).clip(CircleShape).background(color))
        }
    }
}
