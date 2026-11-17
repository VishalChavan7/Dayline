package com.dayline.app.ui.more

import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Inbox

import androidx.compose.material3.Scaffold
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.dayline.app.ui.components.DaylineTopBar
import com.dayline.app.ui.components.rememberBarScroll
import com.dayline.app.ui.motion.pressBounce

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dayline.app.core.common.AppClock
import com.dayline.app.core.data.repository.TaskRepository
import com.dayline.app.ui.components.DaylineCard
import com.dayline.app.ui.components.IconBadge
import com.dayline.app.ui.components.ResponsiveBox
import com.dayline.app.ui.navigation.AppNavigator
import com.dayline.app.ui.navigation.Routes
import com.dayline.app.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MoreCounts(val upcoming: Int = 0, val open: Int = 0, val completed: Int = 0, val inbox: Int = 0)

@HiltViewModel
class MoreViewModel @Inject constructor(tasks: TaskRepository, clock: AppClock, private val updates: com.dayline.app.update.UpdateManager) : ViewModel() {
    val update: StateFlow<com.dayline.app.update.UpdateState> = updates.state
    val currentVersion: String get() = updates.currentVersion

    /** "Check for updates": install a ready update, otherwise look for one now. */
    fun onUpdateTile() {
        when (val s = updates.state.value) {
            is com.dayline.app.update.UpdateState.Ready -> updates.install(s.apk)
            is com.dayline.app.update.UpdateState.Checking, is com.dayline.app.update.UpdateState.Downloading -> Unit
            else -> viewModelScope.launch { updates.checkAndDownload(notify = false) }
        }
    }

    val counts: StateFlow<MoreCounts> = combine(tasks.items, clock.today) { items, today ->
        MoreCounts(
            upcoming = items.count { !it.task.isCompleted && it.task.scheduledDate?.isAfter(today) == true },
            open = items.count { !it.task.isCompleted },
            completed = items.count { it.task.isCompleted },
            inbox = items.count { !it.task.isCompleted && it.task.scheduledDate == null && it.task.projectId == null && it.task.listId == null },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoreCounts())
}

private data class MoreEntry(val label: String, val caption: String?, val icon: ImageVector, val route: String, val tint: @Composable () -> Color)

@Composable
fun MoreScreen(nav: AppNavigator, vm: MoreViewModel = hiltViewModel()) {
    val counts by vm.counts.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    MoreContent(counts, com.dayline.app.update.updateCaption(update, vm.currentVersion)) {
        if (it == CHECK_UPDATES) vm.onUpdateTile() else nav.to(it)
    }
}

/** Pseudo-route for the "Check for updates" tile (handled in place, not navigated to). */
const val CHECK_UPDATES = "check_updates"

/** The More page (stateless, also used by the screenshot tests). */
@Composable
fun MoreContent(counts: MoreCounts, updateCaption: String? = null, onOpen: (String) -> Unit) {
    val groups = listOf(
        "Writing" to listOf(
            MoreEntry("Journal", "Reflect on your day", Icons.Rounded.AutoStories, Routes.JOURNAL) { MaterialTheme.colorScheme.tertiary },
        ),
        "Tasks" to listOf(
            MoreEntry("Inbox", "${counts.inbox} to sort", Icons.Rounded.Inbox, Routes.INBOX) { MaterialTheme.colorScheme.primary },
            MoreEntry("Upcoming", "${counts.upcoming} scheduled", Icons.Rounded.Upcoming, Routes.UPCOMING) { MaterialTheme.colorScheme.primary },
            MoreEntry("All tasks", "${counts.open} open", Icons.Rounded.TaskAlt, Routes.ALL) { MaterialTheme.colorScheme.primary },
            MoreEntry("Completed", "${counts.completed} done", Icons.Rounded.DoneAll, Routes.COMPLETED) { MaterialTheme.colorScheme.primary },
        ),
        "Productivity" to listOf(
            MoreEntry("Focus timer", null, Icons.Rounded.Timer, Routes.FOCUS) { MaterialTheme.colorScheme.tertiary },
            MoreEntry("Statistics", null, Icons.Rounded.BarChart, Routes.STATS) { MaterialTheme.colorScheme.tertiary },
        ),
        "App" to listOf(
            MoreEntry("Settings", null, Icons.Rounded.Settings, Routes.SETTINGS) { MaterialTheme.colorScheme.onSurfaceVariant },
            MoreEntry("Check for updates", updateCaption, Icons.Rounded.SystemUpdate, CHECK_UPDATES) { MaterialTheme.colorScheme.onSurfaceVariant },
        ),
    )
    val bar = rememberBarScroll()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { DaylineTopBar(title = { Text("More") }, subtitle = { Text("Everything else in Dayline") }, scrollBehavior = bar) },
    ) { inner ->
        ResponsiveBox(Modifier.padding(inner)) {
            // The page is at least as tall as the screen, so the sprout always sits at the very bottom
            // (and simply follows the list when the list is taller than the screen).
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = maxHeight
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).heightIn(min = viewport)
                    .padding(start = Dimens.lg, end = Dimens.lg),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
              Column(verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                groups.forEach { (title, entries) ->
                    androidx.compose.runtime.key(title) {
                        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = Dimens.sm, top = Dimens.sm, bottom = Dimens.sm))
                        DaylineCard(contentPadding = PaddingValues(vertical = Dimens.xs)) {
                            entries.forEach { e ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .pressBounce(0.97f)
                                        .clickable { onOpen(e.route) }
                                        .padding(horizontal = Dimens.lg, vertical = Dimens.md),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    IconBadge(e.icon, e.tint(), size = 40.dp)
                                    Spacer(Modifier.width(Dimens.lg))
                                    Column(Modifier.weight(1f)) {
                                        Text(e.label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        e.caption?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
              }
              Column {
                  Spacer(Modifier.height(56.dp))
                  MoreFooter()
              }
            }
            }
        }
    }
}

/** The end of the More page: a little sprout in a pot, gently swaying, and a kind word. */
@Composable
private fun MoreFooter() {
    val sway = androidx.compose.animation.core.rememberInfiniteTransition(label = "sprout")
    val angle = sway.animateFloat(
        -6f, 6f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1800, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse),
        label = "sway",
    )
    val pot = MaterialTheme.colorScheme.primaryContainer
    val potRim = MaterialTheme.colorScheme.primary
    val leaf = MaterialTheme.colorScheme.tertiary
    val face = MaterialTheme.colorScheme.onPrimaryContainer
    val blush = MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
    Column(Modifier.fillMaxWidth().padding(bottom = Dimens.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        androidx.compose.foundation.Canvas(Modifier.size(96.dp)) {
            val w = size.width; val h = size.height
            // Sprout (sways from its base, drawn first so the pot sits in front).
            val base = androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.56f)
            rotate(angle.value, base) {
                drawLine(leaf, base, androidx.compose.ui.geometry.Offset(w * 0.5f, h * 0.30f), strokeWidth = w * 0.045f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                drawOval(leaf, topLeft = androidx.compose.ui.geometry.Offset(w * 0.24f, h * 0.18f), size = androidx.compose.ui.geometry.Size(w * 0.26f, h * 0.15f))
                drawOval(leaf, topLeft = androidx.compose.ui.geometry.Offset(w * 0.50f, h * 0.12f), size = androidx.compose.ui.geometry.Size(w * 0.28f, h * 0.16f))
            }
            // Pot with a rim.
            val potPath = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * 0.24f, h * 0.58f); lineTo(w * 0.76f, h * 0.58f); lineTo(w * 0.68f, h * 0.94f); lineTo(w * 0.32f, h * 0.94f); close()
            }
            drawPath(potPath, pot)
            drawRoundRect(potRim, topLeft = androidx.compose.ui.geometry.Offset(w * 0.20f, h * 0.52f), size = androidx.compose.ui.geometry.Size(w * 0.60f, h * 0.10f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.05f))
            // A happy little face.
            drawCircle(face, radius = w * 0.028f, center = androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.72f))
            drawCircle(face, radius = w * 0.028f, center = androidx.compose.ui.geometry.Offset(w * 0.58f, h * 0.72f))
            drawCircle(blush, radius = w * 0.045f, center = androidx.compose.ui.geometry.Offset(w * 0.35f, h * 0.79f))
            drawCircle(blush, radius = w * 0.045f, center = androidx.compose.ui.geometry.Offset(w * 0.65f, h * 0.79f))
            drawArc(face, 20f, 140f, false, topLeft = androidx.compose.ui.geometry.Offset(w * 0.44f, h * 0.74f), size = androidx.compose.ui.geometry.Size(w * 0.12f, h * 0.08f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = w * 0.022f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Spacer(Modifier.height(Dimens.sm))
        Text("That's everything!", style = MaterialTheme.typography.titleSmall)
        Text("Small steps still count. You're growing every day.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
