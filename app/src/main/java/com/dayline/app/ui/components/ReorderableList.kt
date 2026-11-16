package com.dayline.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.dayline.app.ui.motion.rememberHaptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Drag-to-reorder state for a LazyColumn. Positions are always derived from the live layout,
 * so the dragged tile stays glued to the finger even as other tiles move out of the way, the
 * list scrolls itself near the edges, and moving the top-most tile doesn't make the list jump.
 */
class DragDropState(private val state: LazyListState, private val scope: CoroutineScope, private val onMove: (Int, Int) -> Unit) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    private var delta by mutableFloatStateOf(0f)
    private var initialOffset by mutableFloatStateOf(0f)
    internal val scrollChannel = Channel<Float>(Channel.CONFLATED)

    private val draggingInfo: LazyListItemInfo?
        get() = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == draggingKey }

    val draggingOffset: Float
        get() = draggingInfo?.let { initialOffset + delta - it.offset } ?: 0f

    fun startAt(y: Float): Boolean {
        val item = state.layoutInfo.visibleItemsInfo.firstOrNull { y.toInt() in it.offset..(it.offset + it.size) } ?: return false
        start(item)
        return true
    }

    fun startKey(key: Any): Boolean {
        val item = state.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key } ?: return false
        start(item)
        return true
    }

    private fun start(item: LazyListItemInfo) {
        draggingKey = item.key
        initialOffset = item.offset.toFloat()
        delta = 0f
    }

    fun end() {
        draggingKey = null
        delta = 0f
        initialOffset = 0f
    }

    /** Returns true when the drag moved the tile to a new position. */
    fun drag(dy: Float): Boolean {
        delta += dy
        val current = draggingInfo ?: return false
        val start = current.offset + draggingOffset
        val end = start + current.size
        val middle = start + (end - start) / 2f
        val target = state.layoutInfo.visibleItemsInfo.firstOrNull { item ->
            middle.toInt() in item.offset..(item.offset + item.size) && item.key != current.key
        }
        var moved = false
        if (target != null) {
            // Keep the list anchored when the first visible tile is involved; otherwise LazyColumn
            // would scroll to follow it and the whole list would jump.
            if (target.index == state.firstVisibleItemIndex || current.index == state.firstVisibleItemIndex) {
                val first = state.firstVisibleItemIndex
                val off = state.firstVisibleItemScrollOffset
                scope.launch { state.scrollToItem(first, off) }
            }
            onMove(current.index, target.index)
            moved = true
        }
        // Auto-scroll when the tile nears the top or bottom edge.
        val viewStart = state.layoutInfo.viewportStartOffset
        val viewEnd = state.layoutInfo.viewportEndOffset
        val over = when {
            end > viewEnd - 40 -> (end - (viewEnd - 40)).coerceAtMost(40f)
            start < viewStart + 40 -> (start - (viewStart + 40)).coerceAtLeast(-40f)
            else -> 0f
        }
        if (over != 0f) scrollChannel.trySend(over)
        return moved
    }
}

/**
 * A lazy list whose items can be reordered by dragging the handle, or by long-pressing anywhere on a
 * tile. [itemContent] receives the modifier to put on the drag handle. The new order is reported
 * once, when the drag ends.
 */
@Composable
fun <T> ReorderableList(
    items: List<T>,
    key: (T) -> Any,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    itemContent: @Composable (item: T, handle: Modifier, dragging: Boolean) -> Unit,
) {
    val haptics = rememberHaptics()
    val working = remember { items.toMutableStateList() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val drag = remember(listState) {
        DragDropState(listState, scope) { from, to ->
            if (from in working.indices && to in working.indices) working.add(to, working.removeAt(from))
        }
    }
    // Pick up external changes (reset, arrow buttons) whenever nothing is being dragged.
    LaunchedEffect(items) { if (drag.draggingKey == null) { working.clear(); working.addAll(items) } }
    LaunchedEffect(drag) {
        for (dy in drag.scrollChannel) {
            if (drag.draggingKey != null) {
                val consumed = listState.scrollBy(dy)
                if (consumed != 0f) drag.drag(0f)
            }
        }
    }
    fun finish() {
        if (drag.draggingKey != null) {
            drag.end()
            onReorder(working.toList())
        }
    }

    LazyColumn(
        modifier
            .fillMaxSize()
            .pointerInput(drag) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset: Offset -> if (drag.startAt(offset.y)) haptics.heavy() },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                ) { change, amount ->
                    change.consume()
                    if (drag.drag(amount.y)) haptics.light()
                }
            },
        state = listState,
        contentPadding = contentPadding,
    ) {
        itemsIndexed(working, key = { _, item -> key(item) }) { _, item ->
            val itemKey = key(item)
            val dragging = itemKey == drag.draggingKey
            val lift by animateFloatAsState(if (dragging) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "lift")
            val handle = Modifier.pointerInput(itemKey) {
                detectDragGestures(
                    onDragStart = { if (drag.startKey(itemKey)) haptics.heavy() },
                    onDragEnd = { finish() },
                    onDragCancel = { finish() },
                ) { change, amount ->
                    change.consume()
                    if (drag.drag(amount.y)) haptics.light()
                }
            }
            val placement = if (dragging) Modifier.zIndex(1f).graphicsLayer {
                translationY = drag.draggingOffset
            } else Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null, placementSpec = spring<IntOffset>(dampingRatio = 0.8f, stiffness = 500f))
            Box(
                placement.graphicsLayer {
                    val s = 1f + 0.03f * lift
                    scaleX = s; scaleY = s
                    shadowElevation = 16f * lift
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
                    clip = false
                },
            ) {
                itemContent(item, handle, dragging)
            }
        }
    }
}

