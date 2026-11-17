package com.dayline.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.dayline.app.ui.motion.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AddAction(val label: String, val icon: ImageVector) {
    TASK("New task", Icons.Rounded.TaskAlt),
    NOTE("New note", Icons.AutoMirrored.Rounded.StickyNote2),
    HABIT("New habit", Icons.Rounded.SelfImprovement),
    FOCUS("Focus session", Icons.Rounded.Timer),
}

/** Dims the screen behind the open add menu; tapping it closes the menu. */
@Composable
fun AddMenuScrim(expanded: Boolean, onDismiss: () -> Unit) {
    val alpha by animateFloatAsState(if (expanded) 0.42f else 0f, tween(220), label = "scrim")
    if (alpha > 0.001f) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .background(Color.Black)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close menu", onClick = onDismiss),
        )
    }
}

/**
 * Add button. Tap = new task (Quick Add). Press and hold = the button bounces and a menu of
 * other things to create springs out above it.
 */
@Composable
fun AddFab(
    visible: Boolean,
    expanded: Boolean,
    onTap: () -> Unit,
    onExpandedChange: (Boolean) -> Unit,
    onAction: (AddAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    // Slides away instead of scaling, so the icon is never drawn larger than its native size (stays crisp).
    val show by animateFloatAsState(
        if (visible || expanded) 1f else 0f,
        tween(durationMillis = 650, easing = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
        label = "fabShow",
    )
    val hop = remember { Animatable(0f) }
    val rotation by animateFloatAsState(if (expanded) 135f else 0f, spring(dampingRatio = 0.55f, stiffness = 380f), label = "fabRot")
    val scale = remember { Animatable(1f) }
    BackHandler(enabled = expanded) { onExpandedChange(false) }

    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val actions = AddAction.entries
        actions.reversed().forEachIndexed { revIndex, action ->
            // Items nearest the button appear first.
            val order = actions.size - 1 - revIndex
            MenuItem(action, expanded, order) {
                haptics.light()
                onExpandedChange(false)
                onAction(action)
            }
        }
        Box(
            Modifier
                .padding(top = 4.dp)
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    alpha = show.coerceIn(0f, 1f)
                    translationY = (1f - show) * 120.dp.toPx() + hop.value * 14.dp.toPx()
                }
                .size(68.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(if (expanded) androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary) else com.dayline.app.ui.theme.brandContainerBrush())
                .semantics {
                    role = Role.Button
                    contentDescription = if (expanded) "Close add menu" else "Add task. Press and hold for more"
                    onLongClick(label = "More options") { onExpandedChange(true); true }
                }
                .pointerInput(expanded, visible) {
                    if (!visible && !expanded) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            scope.launch { scale.animateTo(0.88f, spring(dampingRatio = 1f, stiffness = 1500f)) }
                            tryAwaitRelease()
                            scope.launch { scale.animateTo(1f, spring(dampingRatio = 1f, stiffness = 600f)) }
                        },
                        onTap = {
                            if (expanded) onExpandedChange(false) else { haptics.light(); onTap() }
                        },
                        onLongPress = {
                            if (!expanded) {
                                haptics.heavy()
                                // Expressive bounce: a little hop that springs back down.
                                scope.launch {
                                    hop.animateTo(-1f, spring(dampingRatio = 1f, stiffness = 1400f))
                                    hop.animateTo(0f, spring(dampingRatio = 0.32f, stiffness = 420f))
                                }
                                onExpandedChange(true)
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Add,
                contentDescription = null,
                tint = if (expanded) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(30.dp).graphicsLayer { rotationZ = rotation },
            )
        }
    }
}

@Composable
private fun MenuItem(action: AddAction, expanded: Boolean, order: Int, onClick: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            delay(order * 32L)
            progress.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 420f))
        } else {
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 1200f))
        }
    }
    if (progress.value <= 0.01f && !expanded) return
    Surface(
        onClick = onClick,
        enabled = expanded,
        shape = CircleShape,
        color = if (action == AddAction.TASK) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier
            .graphicsLayer {
                val p = progress.value
                alpha = p.coerceIn(0f, 1f)
                scaleX = 0.5f + 0.5f * p
                scaleY = 0.5f + 0.5f * p
                translationY = (1f - p) * 36.dp.toPx()
                transformOrigin = TransformOrigin(1f, 1f)
            },
    ) {
        Row(Modifier.height(52.dp).padding(start = 16.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(action.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text(action.label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
        }
    }
}
