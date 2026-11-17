package com.dayline.app.ui.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dayline.app.core.common.AppClock
import com.dayline.app.core.common.ApplicationScopeHolder
import com.dayline.app.core.data.repository.DeletedTask
import com.dayline.app.core.data.repository.ProjectRepository
import com.dayline.app.core.data.repository.TaskRepository
import com.dayline.app.core.domain.DateRules
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.Project
import com.dayline.app.core.model.TaskItem
import com.dayline.app.core.model.TaskList
import com.dayline.app.ui.common.IconCatalog
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.components.DatePickerDialogHost
import com.dayline.app.ui.components.ItemIconBadge
import com.dayline.app.ui.components.priorityColor
import com.dayline.app.ui.navigation.LocalSnackbar
import com.dayline.app.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TaskActionsViewModel @Inject constructor(
    private val tasks: TaskRepository,
    projects: ProjectRepository,
    clock: AppClock,
    private val appScope: ApplicationScopeHolder,
) : ViewModel() {
    val projects: StateFlow<List<Project>> = projects.projects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val lists: StateFlow<List<TaskList>> = projects.lists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val today: StateFlow<LocalDate> = clock.today

    // Actions run in the application scope so they complete even though the sheet closes immediately.
    fun toggle(id: Long, done: Boolean) = appScope.launch { tasks.setCompleted(id, done) }
    fun duplicate(id: Long) = appScope.launch { tasks.duplicate(id) }
    fun reschedule(id: Long, date: LocalDate?) = appScope.launch { tasks.reschedule(id, date) }
    fun setPriority(id: Long, p: Priority) = appScope.launch { tasks.setPriority(listOf(id), p) }
    fun move(id: Long, projectId: Long?, listId: Long?) = appScope.launch { tasks.move(listOf(id), projectId, listId) }
    suspend fun delete(id: Long): DeletedTask? = tasks.delete(id)
    fun restore(d: DeletedTask) = appScope.launch { tasks.restore(d) }
}

/** Contextual long-press actions for a task. */
@Composable
fun TaskActionsSheet(item: TaskItem, onDismiss: () -> Unit, onOpen: () -> Unit, vm: TaskActionsViewModel = hiltViewModel()) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val projects by vm.projects.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val formats = LocalFormats.current
    val snackbar = LocalSnackbar.current
    var picking by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf(false) }
    val task = item.task

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = Dimens.lg)) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Dimens.xl),
            )
            Spacer(Modifier.height(Dimens.md))
            if (!moving) {
                Text("Reschedule", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = Dimens.xl))
                FlowRow(Modifier.padding(horizontal = Dimens.xl), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    listOf("Today" to today, "Tomorrow" to today.plusDays(1), "Next week" to DateRules.startOfWeek(today, formats.weekStart).plusWeeks(1))
                        .forEach { (label, d) ->
                            AssistChip(onClick = { vm.reschedule(task.id, d); onDismiss() }, label = { Text(label) }, leadingIcon = { Icon(Icons.Rounded.Event, null, Modifier.size(18.dp)) })
                        }
                    AssistChip(onClick = { picking = true }, label = { Text("Pick date") })
                    if (task.scheduledDate != null) {
                        AssistChip(onClick = { vm.reschedule(task.id, null); onDismiss() }, label = { Text("No date") }, leadingIcon = { Icon(Icons.Rounded.EventBusy, null, Modifier.size(18.dp)) })
                    }
                }
                Spacer(Modifier.height(Dimens.sm))
                Text("Priority", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = Dimens.xl))
                FlowRow(Modifier.padding(horizontal = Dimens.xl), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                    Priority.entries.reversed().forEach { p ->
                        FilterChip(
                            selected = task.priority == p,
                            onClick = { vm.setPriority(task.id, p); onDismiss() },
                            label = { Text(p.label) },
                            leadingIcon = { Icon(Icons.Rounded.Flag, null, tint = priorityColor(p), modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
                Spacer(Modifier.height(Dimens.sm))
                HorizontalDivider(Modifier.padding(vertical = Dimens.sm))
                val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
                ListItem(
                    headlineContent = { Text("Edit details") },
                    leadingContent = { Icon(Icons.Rounded.Edit, null) },
                    modifier = Modifier.clickableRow { onDismiss(); onOpen() },
                    colors = colors,
                )
                ListItem(
                    headlineContent = { Text(if (task.isCompleted) "Mark incomplete" else "Complete") },
                    leadingContent = { Icon(if (task.isCompleted) Icons.Rounded.RadioButtonUnchecked else Icons.Rounded.CheckCircle, null) },
                    modifier = Modifier.clickableRow { vm.toggle(task.id, !task.isCompleted); onDismiss() },
                    colors = colors,
                )
                ListItem(
                    headlineContent = { Text("Duplicate") },
                    leadingContent = { Icon(Icons.Rounded.ContentCopy, null) },
                    modifier = Modifier.clickableRow { vm.duplicate(task.id); onDismiss() },
                    colors = colors,
                )
                ListItem(
                    headlineContent = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingContent = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickableRow {
                        onDismiss()
                        vm.viewModelScope.launch {
                            val deleted = vm.delete(task.id) ?: return@launch
                            if (snackbar.showSnackbar("Task deleted", actionLabel = "Undo", withDismissAction = true) == SnackbarResult.ActionPerformed) vm.restore(deleted)
                        }
                    },
                    colors = colors,
                )
            } else {
                MoveTargets(projects, lists) { projectId, listId ->
                    vm.move(task.id, projectId, listId)
                    onDismiss()
                }
            }
        }
    }
    if (picking) {
        DatePickerDialogHost(initial = task.scheduledDate ?: today, onDismiss = { picking = false }) { d ->
            vm.reschedule(task.id, d)
            onDismiss()
        }
    }
}

@Composable
fun MoveTargets(projects: List<Project>, lists: List<TaskList>, onPick: (Long?, Long?) -> Unit) {
    val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ListItem(headlineContent = { Text("Inbox") }, leadingContent = { Icon(Icons.Rounded.Inbox, null) }, modifier = Modifier.clickableRow { onPick(null, null) }, colors = colors)
    projects.filter { !it.isArchived }.forEach { p ->
        ListItem(
            headlineContent = { Text(p.name) },
            leadingContent = { ItemIconBadge(IconCatalog.get(p.icon), p.color, size = 32.dp) },
            modifier = Modifier.clickableRow { onPick(p.id, null) },
            colors = colors,
        )
        lists.filter { it.projectId == p.id }.forEach { l ->
            ListItem(
                headlineContent = { Text(l.name) },
                modifier = Modifier.padding(start = 40.dp).clickableRow { onPick(p.id, l.id) },
                colors = colors,
            )
        }
    }
    lists.filter { it.projectId == null }.forEach { l ->
        ListItem(
            headlineContent = { Text(l.name) },
            leadingContent = { ItemIconBadge(IconCatalog.get(l.icon), l.color, size = 32.dp) },
            modifier = Modifier.clickableRow { onPick(null, l.id) },
            colors = colors,
        )
    }
}

fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
