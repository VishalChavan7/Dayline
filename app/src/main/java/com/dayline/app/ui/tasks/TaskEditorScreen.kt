package com.dayline.app.ui.tasks

import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import com.dayline.app.ui.components.BackButton
import com.dayline.app.ui.components.DaylineCard
import com.dayline.app.ui.components.ResponsiveBox
import com.dayline.app.ui.components.SegmentedPills
import com.dayline.app.ui.motion.pressBounce
import com.dayline.app.ui.motion.rememberHaptics

import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.dayline.app.ui.components.DaylineTopBar
import com.dayline.app.ui.components.rememberBarScroll

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Patterns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dayline.app.core.domain.DateRules
import com.dayline.app.core.domain.ReminderPlanner
import com.dayline.app.core.model.Attachment
import com.dayline.app.core.model.AttachmentKind
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.RecurrenceFrequency
import com.dayline.app.core.model.RecurrenceRule
import com.dayline.app.core.model.Reminder
import com.dayline.app.core.model.ReminderType
import com.dayline.app.core.model.Task
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.components.ConfirmDialog
import com.dayline.app.ui.components.DatePickerDialogHost
import com.dayline.app.ui.components.RoundCheck
import com.dayline.app.ui.components.TimePickerDialogHost
import com.dayline.app.ui.components.priorityColor
import com.dayline.app.ui.navigation.AppNavigator
import com.dayline.app.ui.navigation.LocalSnackbar
import com.dayline.app.ui.navigation.Routes
import com.dayline.app.ui.theme.Dimens
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Everything the editor content can ask for; the screen wires these to the view model and dialogs. */
data class TaskEditorActions(
    val update: ((TaskEditorState) -> TaskEditorState) -> Unit = {},
    val onBack: () -> Unit = {},
    val onSave: () -> Unit = {},
    val onDuplicate: () -> Unit = {},
    val onFocus: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onSetCompleted: (Boolean) -> Unit = {},
    val onPickDate: (String) -> Unit = {},
    val onPickTime: (String) -> Unit = {},
    val onRecurrence: () -> Unit = {},
    val onReminder: () -> Unit = {},
    val onRemoveReminder: (Reminder) -> Unit = {},
    val onDestination: () -> Unit = {},
    val onAddTag: (String) -> Unit = {},
    val onRemoveTag: (String) -> Unit = {},
    val onAddSubtask: (String) -> Unit = {},
    val onEditSubtask: (Long, String) -> Unit = { _, _ -> },
    val onToggleSubtask: (Long) -> Unit = {},
    val onMoveSubtask: (Long, Int) -> Unit = { _, _ -> },
    val onRemoveSubtask: (Long) -> Unit = {},
    val onAddDependency: () -> Unit = {},
    val onRemoveDependency: (Task) -> Unit = {},
    val onAttachFile: () -> Unit = {},
    val onAddLink: () -> Unit = {},
    val onOpenAttachment: (Attachment) -> Unit = {},
    val onRemoveAttachment: (Attachment) -> Unit = {},
    val onOpenNote: (Long) -> Unit = {},
)

@Composable
fun TaskEditorScreen(nav: AppNavigator, vm: TaskEditorViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val options by vm.options.collectAsStateWithLifecycle()
    val formats = LocalFormats.current
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    var showDiscard by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var datePicker by remember { mutableStateOf<String?>(null) } // "start" | "due"
    var timePicker by remember { mutableStateOf<String?>(null) }
    var showRecurrence by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    var showLink by remember { mutableStateOf(false) }
    var showDeps by remember { mutableStateOf(false) }
    var showDestination by remember { mutableStateOf(false) }

    LaunchedEffect(state.finished) {
        if (state.finished) {
            haptics.success()
            nav.back()
        }
    }
    BackHandler(enabled = state.dirty) { showDiscard = true }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            var name = uri.lastPathSegment ?: "Attachment"
            var size: Long? = null
            runCatching {
                context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) {
                        val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val si = c.getColumnIndex(OpenableColumns.SIZE)
                        if (ni >= 0) name = c.getString(ni) ?: name
                        if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                    }
                }
            }
            vm.addAttachment(Attachment(taskId = state.id, kind = AttachmentKind.FILE, uri = uri.toString(), displayName = name,
                mimeType = context.contentResolver.getType(uri), sizeBytes = size, addedAt = Instant.now()))
        }
    }

    fun openAttachment(a: Attachment) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                if (a.kind == AttachmentKind.FILE) {
                    setDataAndType(Uri.parse(a.uri), a.mimeType ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else data = Uri.parse(if (a.uri.startsWith("http")) a.uri else "https://${a.uri}")
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            scope.launch { snackbar.showSnackbar("No app on this phone can open that.") }
        } catch (e: SecurityException) {
            scope.launch { snackbar.showSnackbar("Access to this file was revoked. Remove and attach it again.") }
        }
    }

    TaskEditorContent(
        state = state,
        options = options,
        today = today,
        actions = TaskEditorActions(
            update = { t -> vm.update(t) },
            onBack = { if (state.dirty) showDiscard = true else nav.back() },
            onSave = { vm.save() },
            onDuplicate = { vm.duplicate() },
            onFocus = { nav.to("focus?task=${state.id}") },
            onDelete = { showDelete = true },
            onSetCompleted = { vm.setCompleted(it) },
            onPickDate = { datePicker = it },
            onPickTime = { timePicker = it },
            onRecurrence = { showRecurrence = true },
            onReminder = { showReminder = true },
            onRemoveReminder = { vm.removeReminder(it) },
            onDestination = { showDestination = true },
            onAddTag = vm::addTag,
            onRemoveTag = vm::removeTag,
            onAddSubtask = { vm.addSubtask(it) },
            onEditSubtask = { k, t -> vm.editSubtask(k, t) },
            onToggleSubtask = { vm.toggleSubtask(it) },
            onMoveSubtask = { k, d -> vm.moveSubtask(k, d) },
            onRemoveSubtask = { vm.removeSubtask(it) },
            onAddDependency = { showDeps = true },
            onRemoveDependency = { t -> vm.setDependencies(state.dependsOn - t) },
            onAttachFile = { filePicker.launch(arrayOf("*/*")) },
            onAddLink = { showLink = true },
            onOpenAttachment = ::openAttachment,
            onRemoveAttachment = { vm.removeAttachment(it) },
            onOpenNote = nav::note,
        ),
    )

    // Dialogs
    datePicker?.let { which ->
        DatePickerDialogHost(
            initial = (if (which == "start") state.startDate else state.dueDate) ?: today,
            onDismiss = { datePicker = null },
        ) { d -> vm.update { if (which == "start") it.copy(startDate = d) else it.copy(dueDate = d) } }
    }
    timePicker?.let { which ->
        TimePickerDialogHost(
            initial = if (which == "start") state.startTime else state.dueTime,
            is24Hour = formats.is24Hour,
            onDismiss = { timePicker = null },
        ) { t -> vm.update { if (which == "start") it.copy(startTime = t) else it.copy(dueTime = t) } }
    }
    if (showRecurrence) RecurrenceDialog(state.recurrence, onDismiss = { showRecurrence = false }) { rule -> vm.update { it.copy(recurrence = rule) } }
    if (showReminder) ReminderDialog(state.hasDate, state.dueDate != null, onDismiss = { showReminder = false }, onAdd = vm::addReminder)
    if (showLink) LinkDialog(onDismiss = { showLink = false }) { url, name ->
        vm.addAttachment(Attachment(taskId = state.id, kind = AttachmentKind.LINK, uri = url, displayName = name.ifBlank { url }, addedAt = Instant.now()))
    }
    if (showDeps) DependencyPicker(options.candidates.map { it.task }, state.dependsOn, onDismiss = { showDeps = false }, onDone = vm::setDependencies)
    if (showDestination) {
        ModalBottomSheet(onDismissRequest = { showDestination = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = Dimens.xxl)) {
                Text("Move to", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = Dimens.xl, vertical = Dimens.sm))
                MoveTargets(options.projects, options.lists) { p, l ->
                    vm.update { it.copy(projectId = p, listId = l) }
                    showDestination = false
                }
            }
        }
    }
    if (showDiscard) ConfirmDialog("Discard changes?", "Your edits to this task haven't been saved.", "Discard", onConfirm = { nav.back() }, onDismiss = { showDiscard = false })
    if (showDelete) ConfirmDialog("Delete task?", "This removes the task, its subtasks, reminders and attachments.", "Delete", onConfirm = { vm.delete() }, onDismiss = { showDelete = false })
}

/** Stateless editor layout: rendered by the screen and by the phone-size screenshot tests. */
@Composable
fun TaskEditorContent(state: TaskEditorState, options: EditorOptions, today: LocalDate, actions: TaskEditorActions) {
    val formats = LocalFormats.current
    val update = actions.update
    var showMenu by remember { mutableStateOf(false) }
    val bar = rememberBarScroll()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DaylineTopBar(
                scrollBehavior = bar,
                title = { Text(if (state.isNew) "New task" else "Edit task") },
                navigationIcon = { BackButton(actions.onBack) },
                actions = {
                    if (!state.isNew) {
                        Box {
                            IconButton(onClick = { showMenu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More options") }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(text = { Text("Duplicate") }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) }, onClick = { showMenu = false; actions.onDuplicate() })
                                DropdownMenuItem(text = { Text("Start focus on this task") }, leadingIcon = { Icon(Icons.Rounded.PlayCircle, null) }, onClick = { showMenu = false; actions.onFocus() })
                                DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) }, onClick = { showMenu = false; actions.onDelete() })
                            }
                        }
                    }
                    Button(
                        onClick = actions.onSave,
                        enabled = !state.loading,
                        modifier = Modifier.padding(end = Dimens.sm).height(40.dp).pressBounce(),
                        contentPadding = PaddingValues(horizontal = Dimens.lg),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Dimens.xs))
                        Text("Save", maxLines = 1)
                    }
                },
            )
        },
    ) { inner ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        ResponsiveBox(Modifier.padding(inner)) {
            LazyColumn(
                Modifier.fillMaxSize().imePadding(),
                contentPadding = PaddingValues(start = Dimens.lg, end = Dimens.lg, bottom = 64.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
            ) {
                item(key = "title") {
                    DaylineCard(contentPadding = PaddingValues(start = Dimens.xs, end = Dimens.sm, top = Dimens.xs, bottom = Dimens.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (!state.isNew) {
                                RoundCheck(state.isCompleted, { actions.onSetCompleted(it) }, priorityColor(state.priority), size = 26.dp, label = "Completed")
                            }
                            Column(Modifier.weight(1f)) {
                                PlainField(
                                    value = state.title,
                                    onValueChange = { v -> update { it.copy(title = v) } },
                                    placeholder = "What needs to be done?",
                                    style = MaterialTheme.typography.titleLarge,
                                    autoFocus = state.isNew,
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(horizontal = Dimens.md), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        PlainField(
                            value = state.description,
                            onValueChange = { v -> update { it.copy(description = v) } },
                            placeholder = "Add a description",
                            style = MaterialTheme.typography.bodyLarge,
                            singleLine = false,
                        )
                    }
                    state.error?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = Dimens.md, top = Dimens.xs))
                    }
                }

                item(key = "schedule") {
                    EditorCard("Schedule") {
                        EditorRow(Icons.Rounded.Event, "Due", state.dueDate?.let { formats.relative(it, today) }, onClick = { actions.onPickDate("due") },
                            onClear = if (state.dueDate != null) { { update { it.copy(dueDate = null, dueTime = null) } } } else null)
                        FlowRow(
                            Modifier.fillMaxWidth().padding(start = Dimens.sm, end = Dimens.sm, bottom = Dimens.xs),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                            verticalArrangement = Arrangement.spacedBy(0.dp),
                        ) {
                            listOf("Today" to today, "Tomorrow" to today.plusDays(1), "Next week" to DateRules.startOfWeek(today, formats.weekStart).plusWeeks(1)).forEach { (label, d) ->
                                FilterChip(selected = state.dueDate == d, onClick = { update { it.copy(dueDate = d) } }, label = { Text(label, maxLines = 1) })
                            }
                        }
                        if (state.dueDate != null) {
                            EditorRow(Icons.Rounded.AccessTime, "Time", state.dueTime?.let(formats::time), onClick = { actions.onPickTime("due") },
                                onClear = if (state.dueTime != null) { { update { it.copy(dueTime = null) } } } else null, indent = true)
                        }
                        EditorRow(Icons.Rounded.EventAvailable, "Start", state.startDate?.let { formats.relative(it, today) }, onClick = { actions.onPickDate("start") },
                            onClear = if (state.startDate != null) { { update { it.copy(startDate = null, startTime = null) } } } else null)
                        if (state.startDate != null) {
                            EditorRow(Icons.Rounded.AccessTime, "Time", state.startTime?.let(formats::time), onClick = { actions.onPickTime("start") },
                                onClear = if (state.startTime != null) { { update { it.copy(startTime = null) } } } else null, indent = true)
                        }
                        EditorRow(Icons.Rounded.Repeat, "Repeat", state.recurrence?.describe(), onClick = actions.onRecurrence,
                            onClear = if (state.recurrence != null) { { update { it.copy(recurrence = null) } } } else null)
                        EditorRow(Icons.Rounded.Notifications, "Reminders", if (state.reminders.isEmpty()) null else "${state.reminders.size}", onClick = actions.onReminder)
                        if (state.reminders.isNotEmpty()) {
                            FlowRow(Modifier.fillMaxWidth().padding(horizontal = Dimens.sm), horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                                state.reminders.forEach { r ->
                                    val text = when (r.type) {
                                        ReminderType.CUSTOM -> r.at?.let { formats.dateTime(it.toLocalDate(), it.toLocalTime(), today) } ?: "Custom"
                                        ReminderType.DEADLINE -> ReminderPlanner.describeOffset(r.offsetMinutes) + " due"
                                        ReminderType.TASK -> ReminderPlanner.describeOffset(r.offsetMinutes) + " start"
                                    }
                                    InputChip(selected = false, onClick = { actions.onRemoveReminder(r) }, label = { Text(text, maxLines = 1) },
                                        trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Remove reminder", modifier = Modifier.size(16.dp)) })
                                }
                            }
                        }
                    }
                }

                item(key = "details") {
                    EditorCard("Details") {
                        Text("Priority", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Dimens.sm, bottom = Dimens.sm))
                        val pColors = Priority.entries.associateWith { priorityColor(it) }
                        SegmentedPills(
                            options = Priority.entries,
                            selected = state.priority,
                            label = { it.label },
                            onSelect = { p -> update { it.copy(priority = p) } },
                            selectedColor = { pColors[it] },
                        )
                        // Category and tags were removed from the editor (existing values are kept).
                        if (false) {
                            Spacer(Modifier.height(Dimens.sm))
                            CategoryField(state.category, options.categories) { v -> update { it.copy(category = v) } }
                            TagsEditor(state.tagNames, options.tags.map { it.name }, onAdd = actions.onAddTag, onRemove = actions.onRemoveTag)
                        }
                    }
                }

                if (false) item(key = "subtasks") { // subtasks removed from the editor
                    EditorCard("Subtasks") {
                        state.subtasks.forEachIndexed { index, es ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = es.subtask.isDone, onCheckedChange = { actions.onToggleSubtask(es.key) })
                                PlainField(
                                    value = es.subtask.title,
                                    onValueChange = { actions.onEditSubtask(es.key, it) },
                                    placeholder = "Subtask",
                                    style = MaterialTheme.typography.bodyLarge.copy(textDecoration = if (es.subtask.isDone) TextDecoration.LineThrough else null),
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                )
                                if (index > 0) IconButton(onClick = { actions.onMoveSubtask(es.key, -1) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.KeyboardArrowUp, "Move up", modifier = Modifier.size(20.dp)) }
                                IconButton(onClick = { actions.onRemoveSubtask(es.key) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Close, "Remove subtask", modifier = Modifier.size(18.dp)) }
                            }
                        }
                        AddLineField("Add subtask") { actions.onAddSubtask(it) }
                        if (state.subtasks.isEmpty()) {
                            Spacer(Modifier.height(Dimens.xs))
                            Text("Progress · ${state.progress}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Dimens.sm))
                            Slider(
                                value = state.progress.toFloat(),
                                onValueChange = { v -> update { it.copy(progress = (v / 5).toInt() * 5) } },
                                valueRange = 0f..100f,
                                modifier = Modifier.padding(horizontal = Dimens.sm),
                            )
                        } else {
                            val done = state.subtasks.count { it.subtask.isDone }
                            Text("$done of ${state.subtasks.size} done", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Dimens.sm, top = Dimens.xs))
                        }
                    }
                }

                item(key = "dependencies") {
                    EditorCard("Depends on") {
                        if (state.dependsOn.isEmpty()) {
                            Text("This task isn't waiting on anything.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Dimens.sm, vertical = Dimens.xs))
                        }
                        state.dependsOn.forEach { t ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = Dimens.sm)) {
                                Icon(if (t.isCompleted) Icons.Rounded.Check else Icons.Rounded.Lock, contentDescription = null,
                                    tint = if (t.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(Dimens.sm))
                                Text(t.title, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    textDecoration = if (t.isCompleted) TextDecoration.LineThrough else null)
                                IconButton(onClick = { actions.onRemoveDependency(t) }) { Icon(Icons.Rounded.Close, "Remove dependency", modifier = Modifier.size(18.dp)) }
                            }
                        }
                        if (state.dependsOn.any { !it.isCompleted }) {
                            Text("Blocked until these are done.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(start = Dimens.sm))
                        }
                        TextButton(onClick = actions.onAddDependency) { Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(Dimens.xs)); Text("Add dependency") }
                    }
                }

                item(key = "attachments") {
                    EditorCard("Attachments & links") {
                        state.attachments.forEach { a ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { actions.onOpenAttachment(a) }.padding(vertical = Dimens.xs),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(if (a.kind == AttachmentKind.LINK) Icons.Rounded.Link else Icons.Rounded.AttachFile, contentDescription = null, modifier = Modifier.padding(horizontal = Dimens.sm))
                                Column(Modifier.weight(1f)) {
                                    Text(a.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        if (a.kind == AttachmentKind.LINK) a.uri else listOfNotNull(a.mimeType, a.sizeBytes?.let { humanSize(it) }).joinToString(" · "),
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                IconButton(onClick = { actions.onRemoveAttachment(a) }) { Icon(Icons.Rounded.Close, "Remove", modifier = Modifier.size(18.dp)) }
                            }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                            OutlinedButton(onClick = actions.onAttachFile) { Icon(Icons.Rounded.AttachFile, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(Dimens.xs)); Text("Attach file", maxLines = 1) }
                            OutlinedButton(onClick = actions.onAddLink) { Icon(Icons.Rounded.Link, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(Dimens.xs)); Text("Add link", maxLines = 1) }
                        }
                    }
                }

                if (false) item(key = "notes") { // task notes removed from the editor
                    EditorCard("Notes") {
                        OutlinedTextField(
                            value = state.notes,
                            onValueChange = { v -> update { it.copy(notes = v) } },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
                            placeholder = { Text("Personal notes for this task") },
                            shape = RoundedCornerShape(16.dp),
                        )
                        state.linkedNotes.forEach { n ->
                            Row(Modifier.fillMaxWidth().clickable { actions.onOpenNote(n.id) }.padding(vertical = Dimens.sm), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Rounded.StickyNote2, null, modifier = Modifier.padding(horizontal = Dimens.sm))
                                Text(n.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }

                if (!state.isNew) {
                    item(key = "meta") {
                        val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                        val zone = ZoneId.systemDefault()
                        Column(Modifier.padding(horizontal = Dimens.md)) {
                            state.createdAt?.let { Text("Created ${LocalDateTime.ofInstant(it, zone).format(fmt)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            state.updatedAt?.let { Text("Modified ${LocalDateTime.ofInstant(it, zone).format(fmt)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            state.completedAt?.let { Text("Completed ${LocalDateTime.ofInstant(it, zone).format(fmt)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
}

@Composable
private fun EditorCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    DaylineCard(contentPadding = PaddingValues(start = Dimens.sm, end = Dimens.sm, top = Dimens.md, bottom = Dimens.sm)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = Dimens.sm, bottom = Dimens.xs))
        content()
    }
}

@Composable
private fun EditorRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String?,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
    indent: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick, onClickLabel = "Change $label")
            .padding(start = if (indent) 28.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Dimens.sm).size(22.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, modifier = Modifier.padding(end = Dimens.sm))
        Text(
            value ?: "None",
            style = MaterialTheme.typography.bodyLarge,
            color = if (value == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        if (onClear != null) IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Close, contentDescription = "Clear $label", modifier = Modifier.size(18.dp)) }
        else Spacer(Modifier.width(Dimens.md))
    }
}

@Composable
private fun ChipLine(text: String, onRemove: () -> Unit) {
    Row(Modifier.padding(start = 40.dp)) {
        InputChip(selected = false, onClick = onRemove, label = { Text(text) }, trailingIcon = { Icon(Icons.Rounded.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp)) })
    }
}

/** Borderless text field for titles and inline editing. */
@Composable
fun PlainField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    autoFocus: Boolean = false,
) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(420) // after the screen has slid in, so the keyboard doesn't fight the transition
        runCatching { focus.requestFocus() }
    }
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth().focusRequester(focus),
        placeholder = { Text(placeholder, style = style) },
        textStyle = style,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun AddLineField(placeholder: String, onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = Dimens.md))
        PlainField(
            value = text,
            onValueChange = { text = it },
            placeholder = placeholder,
            style = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        if (text.isNotBlank()) IconButton(onClick = { onAdd(text); text = "" }) { Icon(Icons.Rounded.Check, contentDescription = "Add") }
    }
}

@Composable
private fun CategoryField(value: String, suggestions: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val matches = suggestions.filter { it.contains(value, ignoreCase = true) && !it.equals(value, ignoreCase = true) }.take(6)
    Box {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); expanded = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = Dimens.xs, vertical = Dimens.xs),
            label = { Text("Category") },
            leadingIcon = { Icon(Icons.Rounded.Category, null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
        DropdownMenu(expanded = expanded && matches.isNotEmpty(), onDismissRequest = { expanded = false }) {
            matches.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { onChange(m); expanded = false }) }
        }
    }
}

@Composable
private fun TagsEditor(tags: List<String>, known: List<String>, onAdd: (String) -> Unit, onRemove: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(Modifier.padding(start = Dimens.xs, end = Dimens.xs, top = Dimens.xs, bottom = Dimens.xs)) {
        if (tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                tags.forEach { t ->
                    InputChip(selected = true, onClick = { onRemove(t) }, label = { Text("#$t") }, trailingIcon = { Icon(Icons.Rounded.Close, "Remove tag $t", Modifier.size(16.dp)) })
                }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { v -> if (v.endsWith(" ") || v.endsWith(",")) { onAdd(v.trim(',', ' ')); text = "" } else text = v },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Add tag") },
            leadingIcon = { Icon(Icons.Rounded.Tag, null) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onAdd(text); text = "" }),
        )
        val suggestions = known.filter { k -> text.isNotBlank() && k.contains(text, true) && tags.none { it.equals(k, true) } }.take(5)
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                suggestions.forEach { s -> FilterChip(selected = false, onClick = { onAdd(s); text = "" }, label = { Text("#$s") }) }
            }
        }
    }
}

@Composable
fun RecurrenceDialog(initial: RecurrenceRule?, onDismiss: () -> Unit, onSave: (RecurrenceRule?) -> Unit) {
    val formats = LocalFormats.current
    var freq by remember { mutableStateOf(initial?.frequency) }
    var interval by remember { mutableStateOf((initial?.interval ?: 1).toString()) }
    var weekdays by remember { mutableStateOf(initial?.weekdays ?: emptySet()) }
    var afterCompletion by remember { mutableStateOf(initial?.fromCompletion ?: false) }
    var endDate by remember { mutableStateOf(initial?.endDate) }
    var pickEnd by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Repeat") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.xs)) {
                    FilterChip(freq == null, { freq = null }, { Text("Never") })
                    RecurrenceFrequency.entries.forEach { f -> FilterChip(freq == f, { freq = f }, { Text(f.label) }) }
                }
                if (freq != null) {
                    Spacer(Modifier.height(Dimens.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Every", modifier = Modifier.padding(end = Dimens.sm))
                        OutlinedTextField(
                            value = interval,
                            onValueChange = { v -> interval = v.filter(Char::isDigit).take(3) },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Text(" ${freq!!.unit}${if ((interval.toIntOrNull() ?: 1) > 1) "s" else ""}", modifier = Modifier.padding(start = Dimens.sm))
                    }
                    if (freq == RecurrenceFrequency.WEEKLY) {
                        Spacer(Modifier.height(Dimens.sm))
                        Text("On", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            DateRules.orderedWeekdays(formats.weekStart).forEach { d ->
                                FilterChip(d in weekdays, { weekdays = if (d in weekdays) weekdays - d else weekdays + d }, { Text(formats.weekdayShort(d)) })
                            }
                        }
                    }
                    Spacer(Modifier.height(Dimens.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Repeat after completion")
                            Text("Next date counts from when you finish", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(afterCompletion, { afterCompletion = it })
                    }
                    Spacer(Modifier.height(Dimens.sm))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Ends", modifier = Modifier.weight(1f))
                        TextButton(onClick = { pickEnd = true }) { Text(endDate?.let(formats::date) ?: "Never") }
                        if (endDate != null) IconButton(onClick = { endDate = null }) { Icon(Icons.Rounded.Close, "Clear end date") }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val f = freq
                onSave(f?.let { RecurrenceRule(it, (interval.toIntOrNull() ?: 1).coerceIn(1, 999), if (it == RecurrenceFrequency.WEEKLY) weekdays else emptySet(), afterCompletion, endDate) })
                onDismiss()
            }) { Text("Done") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickEnd) DatePickerDialogHost(endDate, onDismiss = { pickEnd = false }) { endDate = it }
}

@Composable
private fun ReminderDialog(hasDate: Boolean, hasDue: Boolean, onDismiss: () -> Unit, onAdd: (Reminder) -> Unit) {
    var customDate by remember { mutableStateOf<LocalDate?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    val formats = LocalFormats.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add reminder") },
        text = {
            Column {
                if (!hasDate) Text("Set a start or due date to use relative reminders, or pick an exact time.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (hasDue) {
                    ReminderPlanner.standardOffsets.forEach { m ->
                        Text(
                            ReminderPlanner.describeOffset(m) + if (m == 0) " (due)" else " due",
                            modifier = Modifier.fillMaxWidth().clickable { onAdd(Reminder(type = ReminderType.DEADLINE, offsetMinutes = m)); onDismiss() }.padding(vertical = 12.dp),
                        )
                    }
                }
                if (hasDate) {
                    Text("At start time", modifier = Modifier.fillMaxWidth().clickable { onAdd(Reminder(type = ReminderType.TASK, offsetMinutes = 0)); onDismiss() }.padding(vertical = 12.dp))
                }
                HorizontalDivider()
                Text("Pick date & time…", color = MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth().clickable { pickDate = true }.padding(vertical = 12.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
    if (pickDate) DatePickerDialogHost(LocalDate.now(), onDismiss = { pickDate = false }) { customDate = it; pickTime = true }
    if (pickTime) TimePickerDialogHost(null, formats.is24Hour, onDismiss = { pickTime = false }) { t ->
        customDate?.let { onAdd(Reminder(type = ReminderType.CUSTOM, at = it.atTime(t))) }
        onDismiss()
    }
}

@Composable
private fun LinkDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    val valid = Patterns.WEB_URL.matcher(url.trim()).matches()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                OutlinedTextField(url, { url = it }, label = { Text("URL") }, singleLine = true, isError = url.isNotBlank() && !valid,
                    supportingText = { if (url.isNotBlank() && !valid) Text("Enter a valid web address") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(name, { name = it }, label = { Text("Name (optional)") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onAdd(url.trim(), name.trim()); onDismiss() }) { Text("Add") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun DependencyPicker(candidates: List<Task>, selected: List<Task>, onDismiss: () -> Unit, onDone: (List<Task>) -> Unit) {
    var query by remember { mutableStateOf("") }
    var chosen by remember { mutableStateOf(selected.map { it.id }.toSet()) }
    val all = (candidates + selected).distinctBy { it.id }
    val shown = all.filter { query.isBlank() || it.title.contains(query, true) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Depends on", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onDone(all.filter { it.id in chosen }); onDismiss() }) { Text("Done") }
            }
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Search tasks") }, singleLine = true, shape = RoundedCornerShape(20.dp))
            Spacer(Modifier.height(Dimens.sm))
            if (shown.isEmpty()) Text("No other open tasks to link.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Dimens.lg))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(shown, key = { it.id }) { t ->
                    Row(Modifier.fillMaxWidth().clickable { chosen = if (t.id in chosen) chosen - t.id else chosen + t.id }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(t.id in chosen, { chosen = if (t.id in chosen) chosen - t.id else chosen + t.id })
                        Text(t.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(Dimens.xl))
        }
    }
}
