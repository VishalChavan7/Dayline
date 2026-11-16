package com.dayline.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.delay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.TaskItem
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.theme.DaylineTheme
import com.dayline.app.ui.theme.Dimens
import com.dayline.app.ui.theme.StatusColors
import com.dayline.app.ui.theme.contentOf
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * The task row used everywhere. Swipe right to complete, swipe left to delete (callers show Undo).
 * Long-press opens the caller's contextual actions. All swipe actions are also exposed as accessibility actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskRow(
    item: TaskItem,
    today: LocalDate,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDelete: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    showProject: Boolean = false, // projects and lists were removed from the app
    showDate: Boolean = true,
    dense: Boolean = false,
) {
    val task = item.task
    val formats = LocalFormats.current
    val dark = DaylineTheme.isDark
    val pColor = priorityColor(task.priority)
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    val widthHolder = remember { floatArrayOf(1f) }

    // Optimistic completion: the row animates first, then the change is committed, so the
    // check/strike-through animation is visible before the task moves to another section.
    var shownDone by remember(task.id, task.isCompleted) { mutableStateOf(task.isCompleted) }
    val rowScale = remember { Animatable(1f) }
    val latestDone by androidx.compose.runtime.rememberUpdatedState(task.isCompleted)
    val toggle: (Boolean) -> Unit = { v ->
        if (v && item.isBlocked) {
            // Blocked: never shows as done. A short "no" shake, and the app explains why.
            scope.launch {
                for (x in listOf(14f, -12f, 8f, -5f, 0f)) offset.animateTo(x, tween(55))
            }
            onToggle(v)
        } else {
            shownDone = v
            scope.launch {
                launch {
                    rowScale.snapTo(if (v) 0.965f else 0.985f)
                    rowScale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 420f))
                }
                if (v) delay(420)
                onToggle(v)
                // If the change was refused, put the row back the way it really is.
                delay(1_200)
                shownDone = latestDone
            }
        }
    }
    val strike by animateFloatAsState(if (shownDone) 1f else 0f, spring(dampingRatio = 1f, stiffness = 260f), label = "strike")
    val titleColor by animateColorAsState(
        if (shownDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        spring(dampingRatio = 1f, stiffness = 400f), label = "titleColor",
    )
    val strikeColor = MaterialTheme.colorScheme.onSurfaceVariant
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val actions = buildList {
        add(CustomAccessibilityAction(if (task.isCompleted) "Mark incomplete" else "Complete") { toggle(!task.isCompleted); true })
        if (onDelete != null) add(CustomAccessibilityAction("Delete") { onDelete(); true })
        if (onLongClick != null) add(CustomAccessibilityAction("More actions") { onLongClick(); true })
    }

    Box(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = rowScale.value; scaleY = rowScale.value }
            .clip(RoundedCornerShape(Dimens.rowRadius))
            .onSizeChanged { widthHolder[0] = it.width.toFloat().coerceAtLeast(1f) },
    ) {
        // Swipe backgrounds
        val progress = offset.value / widthHolder[0]
        if (progress != 0f) {
            val completing = progress > 0
            Row(
                Modifier
                    .matchParentSize()
                    .background(if (completing) StatusColors.success(dark).copy(alpha = 0.85f) else MaterialTheme.colorScheme.error)
                    .padding(horizontal = Dimens.xl),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (completing) Arrangement.Start else Arrangement.End,
            ) {
                Icon(
                    if (completing) Icons.Rounded.CheckCircle else Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = if (completing) Color.White else MaterialTheme.colorScheme.onError,
                )
            }
        }
        Row(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = if (progress != 0f) 1f else 0f))
                .pointerInput(onDelete, task.isCompleted) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val w = widthHolder[0]
                            val v = offset.value
                            scope.launch {
                                when {
                                    v > w * 0.28f -> {
                                        offset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f))
                                        toggle(!task.isCompleted)
                                    }
                                    v < -w * 0.32f && onDelete != null -> {
                                        offset.animateTo(-w, tween(180))
                                        onDelete()
                                        offset.snapTo(0f)
                                    }
                                    else -> offset.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 500f))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f, spring()) } },
                    ) { change, dragAmount ->
                        change.consume()
                        val next = offset.value + dragAmount
                        val bounded = if (onDelete == null) next.coerceAtLeast(0f) else next
                        scope.launch { offset.snapTo(bounded) }
                    }
                }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onClickLabel = "Open task")
                .semantics { customActions = actions }
                .padding(end = Dimens.md, top = if (dense) 0.dp else 2.dp, bottom = if (dense) 0.dp else 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundCheck(
                checked = shownDone,
                onCheckedChange = toggle,
                color = pColor,
                label = task.title,
            )
            Column(Modifier.weight(1f).padding(vertical = Dimens.sm)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = titleColor,
                    onTextLayout = { layout = it },
                    modifier = Modifier.drawWithContent {
                        drawContent()
                        val l = layout ?: return@drawWithContent
                        if (strike <= 0.001f) return@drawWithContent
                        // Strike each line in turn, so the line "writes" across the title.
                        val widths = (0 until l.lineCount).map { (l.getLineRight(it) - l.getLineLeft(it)).coerceAtLeast(0f) }
                        var remaining = widths.sum() * strike
                        for (i in 0 until l.lineCount) {
                            if (remaining <= 0f) break
                            val w = minOf(widths[i], remaining)
                            val y = (l.getLineTop(i) + l.getLineBottom(i)) / 2f
                            val x0 = l.getLineLeft(i)
                            drawLine(strikeColor, Offset(x0, y), Offset(x0 + w, y), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
                            remaining -= w
                        }
                    },
                )
                TaskMeta(item, today, showProject, showDate, formats)
            }
            if (!shownDone && (task.priority == Priority.URGENT || task.priority == Priority.HIGH)) {
                Spacer(Modifier.width(Dimens.xs))
                PriorityFlag(task.priority)
            }
        }
    }
}

@Composable
private fun TaskMeta(item: TaskItem, today: LocalDate, showProject: Boolean, showDate: Boolean, formats: com.dayline.app.ui.common.Formats) {
    val task = item.task
    val dark = DaylineTheme.isDark
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val hasAny = (showDate && task.scheduledDate != null) || task.dueTime != null || task.recurrence != null || item.subtasksTotal > 0 ||
        (showProject && (item.project != null || item.list != null)) || item.tags.isNotEmpty() || item.isBlocked || item.attachmentCount > 0
    if (!hasAny) return
    FlowRow(
        Modifier.padding(top = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val date = task.scheduledDate
        if (showDate && date != null) {
            val overdue = task.isOverdue(today)
            MetaChip(
                formats.relative(date, today) + (task.dueTime?.let { " ${formats.time(it)}" } ?: ""),
                icon = Icons.Rounded.Event,
                color = if (overdue) StatusColors.overdue(dark) else muted,
            )
        } else if (task.dueTime != null) {
            MetaChip(formats.time(task.dueTime), color = muted)
        }
        if (item.isBlocked) MetaChip("Blocked", icon = Icons.Rounded.Lock, color = MaterialTheme.colorScheme.error)
        if (task.recurrence != null) MetaChip("", icon = Icons.Rounded.Repeat, color = muted)
        if (item.subtasksTotal > 0) MetaChip("${item.subtasksDone}/${item.subtasksTotal}", icon = Icons.Rounded.Checklist, color = muted)
        if (item.reminderCount > 0 && !task.isCompleted) MetaChip("", icon = Icons.Rounded.Notifications, color = muted)
        if (item.attachmentCount > 0) MetaChip("${item.attachmentCount}", icon = Icons.Rounded.AttachFile, color = muted)
        if (showProject) {
            val p = item.project
            val l = item.list
            if (p != null || l != null) {
                val argb = p?.color ?: l!!.color
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(contentOf(argb, dark)))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        listOfNotNull(p?.name, l?.name).joinToString(" › "),
                        style = MaterialTheme.typography.labelMedium,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item.tags.take(3).forEach { tag -> MetaChip("#${tag.name}", color = MaterialTheme.colorScheme.tertiary) }
        if (item.tags.size > 3) MetaChip("+${item.tags.size - 3}", color = muted)
    }
}

/** Minimal check-off row for a subtask shown inline on the dashboard. */
@Composable
fun SubtaskLine(title: String, done: Boolean, color: Color, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        RoundCheck(done, onToggle, color, size = 18.dp, label = title)
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textDecoration = if (done) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
