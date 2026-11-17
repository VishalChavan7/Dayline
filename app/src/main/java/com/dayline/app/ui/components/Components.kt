package com.dayline.app.ui.components

import androidx.compose.foundation.layout.ime
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dayline.app.ui.motion.pressBounce
import com.dayline.app.ui.motion.rememberHaptics
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dayline.app.core.model.Priority
import com.dayline.app.ui.theme.DaylineTheme
import com.dayline.app.ui.theme.Dimens
import com.dayline.app.ui.theme.PriorityColors
import com.dayline.app.ui.theme.containerOf
import com.dayline.app.ui.theme.contentOf
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Centres content and caps its width on large screens / unfolded foldables. */
@Composable
fun ResponsiveBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = Dimens.maxContentWidth).fillMaxSize()) { content() }
    }
}

@Composable
fun priorityColor(priority: Priority): Color = PriorityColors.of(priority, DaylineTheme.isDark)

/** Rounded surface used for dashboard and detail sections. Clickable cards squish and spring back when pressed. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DaylineCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Dimens.lg),
    brush: androidx.compose.ui.graphics.Brush? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Dimens.cardRadius)
    val base = if (onClick != null) modifier.fillMaxWidth().pressBounce(0.97f) else modifier.fillMaxWidth()
    val clipped = if (brush != null) base.clip(shape).background(brush) else base.clip(shape).background(color)
    val clickable = if (onClick != null) clipped.combinedClickable(onClick = onClick, onLongClick = onLongClick) else clipped
    Column(clickable.padding(contentPadding), content = content)
}

/** Section with a header that collapses its body; used across the dashboard. */
@Composable
fun CollapsibleCard(
    title: String,
    icon: ImageVector,
    collapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, spring(stiffness = Spring.StiffnessMediumLow), label = "chevron")
    DaylineCard(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (collapsed) "Expand $title" else "Collapse $title", onClick = onToggle)
                .padding(start = Dimens.lg, end = Dimens.sm, top = Dimens.md, bottom = Dimens.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon, accent, size = 32.dp)
            Spacer(Modifier.width(Dimens.md))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (badge != null) {
                CountPill(badge, accent)
                Spacer(Modifier.width(Dimens.xs))
            }
            trailing()
            Icon(Icons.Rounded.ExpandMore, contentDescription = null, modifier = Modifier.rotate(rotation).padding(Dimens.sm))
        }
        AnimatedVisibility(
            visible = !collapsed,
            enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium)) + fadeOut(),
        ) {
            Column(
                Modifier
                    .animateContentSize(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                    .padding(start = Dimens.sm, end = Dimens.sm, bottom = Dimens.md),
                content = content,
            )
        }
    }
}

@Composable
fun CountPill(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
    }
}

/** Small rounded-square icon on a tinted background (Memorigi-style list icons). */
@Composable
fun IconBadge(icon: ImageVector, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.34f))
            .background(color.copy(alpha = if (DaylineTheme.isDark) 0.22f else 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.55f))
    }
}

/** Icon badge for user-coloured items (projects, lists, habits). */
@Composable
fun ItemIconBadge(icon: ImageVector, argb: Int, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val dark = DaylineTheme.isDark
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.34f))
            .background(containerOf(argb, dark)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = contentOf(argb, dark), modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * Circular completion checkbox tinted by priority. Checking it plays an expressive bounce with a
 * burst ring and a crisp haptic; unchecking shrinks the tick away.
 */
@Composable
fun RoundCheck(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    label: String = "",
    enabled: Boolean = true,
) {
    val haptics = rememberHaptics()
    val fill by animateColorAsState(if (checked) color else Color.Transparent, spring(dampingRatio = 1f, stiffness = 900f), label = "fill")
    val tick by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.42f, stiffness = 520f), label = "tick")
    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(checked) {
        if (first) { first = false; return@LaunchedEffect }
        if (checked) {
            launch { burst.snapTo(0f); burst.animateTo(1f, tween(420)) }
            pop.snapTo(0.7f)
            pop.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 420f))
        } else {
            pop.snapTo(0.85f)
            pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 600f))
        }
    }
    Box(
        modifier
            .size(Dimens.minTouch)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Checkbox, onClickLabel = if (checked) "Mark incomplete" else "Mark complete") {
                if (!checked) haptics.tick() else haptics.light()
                onCheckedChange(!checked)
            }
            .semantics {
                contentDescription = label
                stateDescription = if (checked) "Completed" else "Not completed"
            },
        contentAlignment = Alignment.Center,
    ) {
        if (burst.value > 0f && burst.value < 1f) {
            Canvas(Modifier.size(size * 2)) {
                val r = (this.size.minDimension / 4f) * (1f + burst.value * 0.9f)
                drawCircle(color.copy(alpha = (1f - burst.value) * 0.45f), radius = r, style = Stroke(width = 3.dp.toPx() * (1f - burst.value) + 0.5f))
            }
        }
        Box(
            Modifier
                .size(size)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .clip(CircleShape)
                .background(fill)
                .border(2.dp, color.copy(alpha = if (enabled) 1f else 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (tick > 0.01f) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.7f).scale(tick))
            }
        }
    }
}

@Composable
fun PriorityFlag(priority: Priority, modifier: Modifier = Modifier, showLabel: Boolean = false) {
    val c = priorityColor(priority)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Flag, contentDescription = "${priority.label} priority", tint = c, modifier = Modifier.size(16.dp))
        if (showLabel) {
            Spacer(Modifier.width(Dimens.xs))
            Text(priority.label, style = MaterialTheme.typography.labelMedium, color = c)
        }
    }
}

/** Compact chip used for metadata (dates, project, tags) on rows. */
@Composable
fun MetaChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    container: Color = Color.Transparent,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(container)
            .padding(horizontal = if (container == Color.Transparent) 0.dp else 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Empty-screen message. Fills the space it is given and centres itself in it (both ways), so it sits
 * in the optical middle of any page. Inside lazy lists, pass Modifier.fillParentMaxSize().
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    /** Where the icon + text are centred, as a fraction of the window height. */
    centerAt: Float = 0.55f,
    action: (@Composable () -> Unit)? = null,
) {
    // Every empty state sits at the same spot on screen: its icon + text are centred on the
    // window's middle, whatever header, search field or keyboard is above or below it.
    val windowHeight = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height
    val ime = androidx.compose.foundation.layout.WindowInsets.ime
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.ui.layout.Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            Column(
                Modifier.padding(horizontal = Dimens.xxxl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(36.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(44.dp))
                }
                Spacer(Modifier.height(Dimens.xl))
                Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Dimens.sm))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            if (action != null) Box(Modifier.padding(top = Dimens.xl)) { action() }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val main = measurables[0].measure(loose)
        val act = measurables.getOrNull(1)?.measure(loose)
        val w = if (constraints.hasBoundedWidth) constraints.maxWidth else main.width
        val h = if (constraints.hasBoundedHeight) constraints.maxHeight else main.height + (act?.height ?: 0)
        layout(w, h) {
            val imeBottom = ime.getBottom(density)
            val top = coordinates?.positionInWindow()?.y ?: 0f
            // The icon's centre sits at the same height on every page (text flows below it), at the
            // spot where a typical icon + title + two-line message is centred on screen.
            var target = if (windowHeight > 0) windowHeight * centerAt - 70.dp.toPx() - top - 48.dp.toPx() else (h - main.height) / 2f
            // The keyboard: only if it would cover the text, ride up with it. This runs in the
            // placement step and follows the keyboard's own animation frame by frame (no jank).
            if (windowHeight > 0 && imeBottom > 0) {
                val keyboardTop = windowHeight - imeBottom - 16.dp.toPx()
                val bottomInWindow = top + target + main.height
                if (bottomInWindow > keyboardTop) target -= bottomInWindow - keyboardTop
            }
            val y = target.toInt().coerceAtLeast(0)
            main.place((w - main.width) / 2, y)
            act?.place((w - act.width) / 2, y + main.height)
        }
    }
}

/** Compact inline empty message inside cards. */
@Composable
fun InlineEmpty(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(horizontal = Dimens.sm, vertical = Dimens.md),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Animated ring with content in the middle (daily progress, focus timer). */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 10.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessLow), label = "ring")
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
            drawArc(trackColor, -90f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            if (animated > 0f) {
                drawArc(color, -90f, 360f * animated, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/** Expressive wavy progress bar. */
@Composable
fun WavyBar(progress: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), spring(stiffness = Spring.StiffnessLow), label = "bar")
    LinearWavyProgressIndicator(
        progress = { animated },
        modifier = modifier.fillMaxWidth(),
        color = color,
        trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    )
}

@Composable
fun StatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.primary,
    caption: String? = null,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(Dimens.rowRadius))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(Dimens.md),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(Dimens.sm))
        }
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.labelSmall, color = accent, maxLines = 1)
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = { onConfirm(); onDismiss() }) {
                Text(confirmLabel, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Material date picker in a dialog; dates are handled as LocalDate (UTC millis internally). */
@Composable
fun DatePickerDialogHost(initial: LocalDate?, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = (initial ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@Composable
fun TimePickerDialogHost(initial: LocalTime?, is24Hour: Boolean, onDismiss: () -> Unit, onPicked: (LocalTime) -> Unit) {
    val start = initial ?: LocalTime.of(9, 0)
    val state = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPicked(LocalTime.of(state.hour, state.minute)); onDismiss() }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
    )
}

/** Row of colour swatches. */
@Composable
fun ColorSwatches(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Dimens.sm), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        colors.forEach { c ->
            val isSel = c == selected
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(if (isSel) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable(role = Role.RadioButton, onClickLabel = "Select colour") { onSelect(c) }
                    .semantics { stateDescription = if (isSel) "Selected" else "" },
                contentAlignment = Alignment.Center,
            ) {
                if (isSel) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun IconPicker(icons: List<Pair<String, ImageVector>>, selected: String, color: Int, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(Dimens.sm), verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        icons.forEach { (key, vector) ->
            val isSel = key == selected
            Surface(
                onClick = { onSelect(key) },
                shape = RoundedCornerShape(14.dp),
                color = if (isSel) containerOf(color, DaylineTheme.isDark) else MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(vector, contentDescription = key, tint = if (isSel) contentOf(color, DaylineTheme.isDark) else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = Dimens.lg, vertical = Dimens.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** Icon button with a guaranteed accessible label. */
@Composable
fun LabeledIconButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    IconButton(onClick = onClick, modifier = modifier) { Icon(icon, contentDescription = label, tint = tint) }
}
