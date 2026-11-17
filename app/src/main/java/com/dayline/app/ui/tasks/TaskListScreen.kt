package com.dayline.app.ui.tasks

import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.dayline.app.ui.components.DaylineTopBar
import com.dayline.app.ui.components.BackButton
import com.dayline.app.ui.components.rememberBarScroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Upcoming
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dayline.app.core.domain.TaskSort
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.TaskItem
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.components.EmptyState
import com.dayline.app.ui.components.ReorderableList
import com.dayline.app.ui.components.ResponsiveBox
import com.dayline.app.ui.components.TaskRow
import com.dayline.app.ui.components.priorityColor
import com.dayline.app.ui.navigation.AppNavigator
import com.dayline.app.ui.navigation.LocalQuickAdd
import com.dayline.app.ui.navigation.LocalSnackbar
import com.dayline.app.ui.navigation.Routes
import com.dayline.app.ui.quickadd.QuickAddDefaults
import com.dayline.app.ui.theme.DaylineTheme
import com.dayline.app.ui.theme.Dimens
import com.dayline.app.ui.theme.StatusColors
import kotlinx.coroutines.launch

data class TaskListActions(
    val onBack: (() -> Unit)? = null,
    val onToggle: (Long, Boolean) -> Unit = { _, _ -> },
    val onOpen: (Long) -> Unit = {},
    val onDelete: (Long) -> Unit = {},
    val onMenu: (TaskItem) -> Unit = {},
    val onFilter: ((TaskFilter) -> TaskFilter) -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onSort: (TaskSort) -> Unit = {},
    val onReorder: (List<Long>) -> Unit = {},
    val onAdd: (() -> Unit)? = null,
    val onSearch: () -> Unit = {},
)

@Composable
fun TaskListScreen(mode: TaskListMode, nav: AppNavigator, vm: TaskListViewModel = hiltViewModel()) {
    LaunchedEffect(mode) { vm.setMode(mode) }
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val quickAdd = LocalQuickAdd.current
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<TaskItem?>(null) }
    TaskListContent(
        state = if (state.mode == mode) state else state.copy(loading = true, mode = mode),
        actions = TaskListActions(
            onBack = nav::back,
            onToggle = { id, d -> vm.toggle(id, d) },
            onOpen = nav::task,
            onDelete = { id ->
                scope.launch {
                    val deleted = vm.delete(id) ?: return@launch
                    if (snackbar.showSnackbar("Task deleted", actionLabel = "Undo", withDismissAction = true) == SnackbarResult.ActionPerformed) vm.restore(deleted)
                }
            },
            onMenu = { menuFor = it },
            onFilter = vm::updateFilter,
            onClearFilters = vm::clearFilters,
            onSort = vm::setSort,
            onReorder = vm::reorder,
            onAdd = if (mode == TaskListMode.UPCOMING || mode == TaskListMode.ALL) {
                { quickAdd(QuickAddDefaults()) }
            } else null,
            onSearch = { nav.to(Routes.SEARCH) },
        ),
    )
    menuFor?.let { item -> TaskActionsSheet(item = item, onDismiss = { menuFor = null }, onOpen = { nav.task(item.id) }) }
}

@Composable
fun TaskListContent(state: TaskListUiState, actions: TaskListActions) {
    val scroll = rememberBarScroll()
    var reordering by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(state.filter.query.isNotEmpty()) }
    val formats = LocalFormats.current
    val mode = state.mode
    val canReorder = mode == TaskListMode.INBOX && state.sort == TaskSort.MANUAL && state.count > 1

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DaylineTopBar(
                title = { Text(mode.title, maxLines = 1) },
                subtitle = {
                    if (!state.loading) {
                        Text(if (mode == TaskListMode.COMPLETED) "${state.count} completed" else "${state.count} task${if (state.count == 1) "" else "s"}")
                    }
                },
                navigationIcon = actions.onBack?.let { back -> { BackButton(back) } },
                actions = {
                    if (mode == TaskListMode.ALL || mode == TaskListMode.COMPLETED) {
                        IconButton(onClick = { searching = !searching; if (!searching) actions.onFilter { it.copy(query = "") } }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Search in list")
                        }
                    } else {
                        IconButton(onClick = actions.onSearch) { Icon(Icons.Rounded.Search, contentDescription = "Search") }
                    }
                    if (mode == TaskListMode.ALL) {
                        IconButton(onClick = { showFilters = true }) {
                            BadgedBox(badge = { if (state.filter.activeCount > 0) Badge { Text("${state.filter.activeCount}") } }) {
                                Icon(Icons.Rounded.FilterList, contentDescription = "Filters")
                            }
                        }
                    }
                    if (mode == TaskListMode.ALL || mode == TaskListMode.INBOX) {
                        Box {
                            IconButton(onClick = { showSort = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort") }
                            DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                                TaskSort.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.label) },
                                        trailingIcon = { if (state.sort == s) Icon(Icons.Rounded.Check, contentDescription = "Selected") },
                                        onClick = { actions.onSort(s); showSort = false },
                                    )
                                }
                            }
                        }
                    }
                    if (canReorder) {
                        IconButton(onClick = { reordering = !reordering }) {
                            Icon(if (reordering) Icons.Rounded.Check else Icons.Rounded.SwapVert, contentDescription = if (reordering) "Done reordering" else "Reorder")
                        }
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { inner ->
        ResponsiveBox(Modifier.padding(inner)) {
            Column(Modifier.fillMaxSize()) {
                if (searching) {
                    com.dayline.app.ui.search.CompactSearchField(
                        value = state.filter.query,
                        onValueChange = { q -> actions.onFilter { it.copy(query = q) } },
                        placeholder = if (mode == TaskListMode.COMPLETED) "Search completed tasks" else "Search tasks",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.lg, vertical = Dimens.xs),
                    )
                }
                if (mode == TaskListMode.ALL && state.filter.activeCount > 0) ActiveFiltersRow(state, actions)
                when {
                    state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                    state.isEmpty -> EmptyForMode(state, actions)
                    reordering && canReorder -> ReorderableList(
                        items = state.sections.flatMap { it.items },
                        key = { it.id },
                        onReorder = { list -> actions.onReorder(list.map { it.id }) },
                        contentPadding = PaddingValues(start = Dimens.sm, end = Dimens.sm, bottom = 120.dp),
                    ) { item, handle, _ ->
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.weight(1f)) {
                                TaskRow(item, state.today, onToggle = { actions.onToggle(item.id, it) }, onClick = {})
                            }
                            Icon(Icons.Rounded.DragIndicator, contentDescription = "Drag to reorder", modifier = handle.padding(Dimens.md))
                        }
                    }
                    else -> LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = Dimens.sm, end = Dimens.sm, bottom = 120.dp),
                    ) {
                        state.sections.forEach { section ->
                            val header = section.title ?: section.date?.let { formats.relative(it, state.today) }
                            if (header != null) {
                                item(key = "h-$header") {
                                    Row(Modifier.padding(start = Dimens.md, end = Dimens.md, top = Dimens.lg, bottom = Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            header,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (section.isWarning) StatusColors.overdue(DaylineTheme.isDark) else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text("${section.items.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            items(section.items, key = { it.id }) { item ->
                                TaskRow(
                                    item = item,
                                    today = state.today,
                                    onToggle = { actions.onToggle(item.id, it) },
                                    onClick = { actions.onOpen(item.id) },
                                    onDelete = { actions.onDelete(item.id) },
                                    onLongClick = { actions.onMenu(item) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showFilters) FilterSheet(state, actions) { showFilters = false }
}

@Composable
private fun ActiveFiltersRow(state: TaskListUiState, actions: TaskListActions) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.lg), verticalAlignment = Alignment.CenterVertically) {
        Text("${state.filter.activeCount} filter${if (state.filter.activeCount > 1) "s" else ""} active", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        TextButton(onClick = actions.onClearFilters) { Text("Clear") }
    }
}

@Composable
private fun EmptyForMode(state: TaskListUiState, actions: TaskListActions) {
    val filtered = state.filter.activeCount > 0 || state.filter.query.isNotBlank()
    when {
        filtered -> EmptyState(Icons.Rounded.FilterList, "No matching tasks", "Try different filters or search words.") {
            TextButton(onClick = actions.onClearFilters) { Text("Clear filters") }
        }
        state.mode == TaskListMode.INBOX -> EmptyState(Icons.Rounded.Inbox, "Inbox zero", "Tasks without a date land here. Capture one with Add task.")
        state.mode == TaskListMode.UPCOMING -> EmptyState(Icons.Rounded.Upcoming, "Nothing upcoming", "Tasks with future dates will appear here, grouped by when they're due.")
        state.mode == TaskListMode.COMPLETED -> EmptyState(Icons.Rounded.DoneAll, "No completed tasks yet", "Finished tasks are kept here so you can review or reopen them.")
        else -> EmptyState(Icons.Rounded.TaskAlt, "No tasks yet", "Add your first task to get started.")
    }
}

@Composable
private fun FilterSheet(state: TaskListUiState, actions: TaskListActions, onDismiss: () -> Unit) {
    val f = state.filter
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.xl).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Filters", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = actions.onClearFilters) { Text("Reset") }
            }
            FilterGroup("Status") {
                StatusFilter.entries.forEach { s -> FilterChip(f.status == s, { actions.onFilter { it.copy(status = s) } }, { Text(s.label) }) }
            }
            FilterGroup("Priority") {
                Priority.entries.reversed().forEach { p ->
                    FilterChip(
                        selected = p in f.priorities,
                        onClick = { actions.onFilter { it.copy(priorities = if (p in it.priorities) it.priorities - p else it.priorities + p) } },
                        label = { Text(p.label) },
                        leadingIcon = { Icon(Icons.Rounded.Flag, null, tint = priorityColor(p), modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            FilterGroup("Date") {
                DateFilter.entries.forEach { d -> FilterChip(f.date == d, { actions.onFilter { it.copy(date = d) } }, { Text(d.label) }) }
            }
            if (false && state.projects.isNotEmpty()) { // projects were removed
                FilterGroup("Project") {
                    state.projects.forEach { p ->
                        FilterChip(f.projectId == p.id, { actions.onFilter { it.copy(projectId = if (it.projectId == p.id) null else p.id) } }, { Text(p.name) })
                    }
                }
            }
            if (state.categories.isNotEmpty()) {
                FilterGroup("Category") {
                    state.categories.forEach { c ->
                        FilterChip(f.category == c, { actions.onFilter { it.copy(category = if (it.category == c) null else c) } }, { Text(c) })
                    }
                }
            }
            if (state.tags.isNotEmpty()) {
                FilterGroup("Tags") {
                    state.tags.forEach { t ->
                        FilterChip(t.id in f.tagIds, { actions.onFilter { it.copy(tagIds = if (t.id in it.tagIds) it.tagIds - t.id else it.tagIds + t.id) } }, { Text("#${t.name}") })
                    }
                }
            }
            Spacer(Modifier.height(Dimens.xl))
        }
    }
}

@Composable
private fun FilterGroup(title: String, content: @Composable () -> Unit) {
    Spacer(Modifier.height(Dimens.md))
    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(Dimens.xs))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) { content() }
}
