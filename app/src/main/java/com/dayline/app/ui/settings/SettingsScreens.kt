package com.dayline.app.ui.settings

import androidx.compose.material.icons.rounded.Done

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.SwitchAccount
import androidx.compose.material.icons.automirrored.rounded.Logout

import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.dayline.app.ui.components.DaylineTopBar
import com.dayline.app.ui.components.rememberBarScroll

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dayline.app.BuildConfig
import com.dayline.app.core.data.backup.BackupFormatException
import com.dayline.app.core.data.backup.BackupSummary
import com.dayline.app.core.domain.ReminderPlanner
import com.dayline.app.core.model.DateFormatOption
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.Tag
import com.dayline.app.core.model.ThemeMode
import com.dayline.app.core.model.TimeFormat
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.components.ColorSwatches
import com.dayline.app.ui.components.ConfirmDialog
import com.dayline.app.ui.components.DaylineCard
import com.dayline.app.ui.components.EmptyState
import com.dayline.app.ui.components.ResponsiveBox
import com.dayline.app.ui.components.TimePickerDialogHost
import com.dayline.app.ui.habits.Stepper
import com.dayline.app.ui.navigation.AppNavigator
import com.dayline.app.ui.navigation.LocalSnackbar
import com.dayline.app.ui.navigation.Routes
import com.dayline.app.ui.theme.Dimens
import com.dayline.app.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(nav: AppNavigator, vm: SettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val formats = LocalFormats.current
    var pickTime by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<Pair<String, BackupSummary>?>(null) }
    var busy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            val ok = runCatching {
                val json = vm.exportJson()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } ?: error("Couldn't open file") }
            }.isSuccess
            busy = false
            snackbar.showSnackbar(if (ok) "Backup saved" else "Couldn't save the backup")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            try {
                val json = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } } ?: error("Couldn't read file")
                pendingImport = json to vm.inspect(json)
            } catch (e: BackupFormatException) {
                snackbar.showSnackbar(e.message ?: "Not a valid backup")
            } catch (e: Exception) {
                snackbar.showSnackbar("Couldn't read that file")
            }
        }
    }

    val bar = rememberBarScroll()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DaylineTopBar(
                scrollBehavior = bar,
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { inner ->
        val s = settings
        if (s == null) { Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { LoadingIndicator() }; return@Scaffold }
        ResponsiveBox(Modifier.padding(inner)) {
            LazyColumn(contentPadding = PaddingValues(start = Dimens.lg, end = Dimens.lg, bottom = 64.dp), verticalArrangement = Arrangement.spacedBy(Dimens.md)) {
                item {
                    SettingsGroup("You") {
                        var name by remember(s.userName) { mutableStateOf(s.userName) }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it.take(40) },
                            label = { Text("Your name") },
                            supportingText = { Text("Used to greet you on Today") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && name != s.userName) vm.setName(name) },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { vm.setName(name) }),
                        )
                    }
                }
                item {
                    SettingsGroup("Appearance") {
                        Label("Theme")
                        com.dayline.app.ui.components.SegmentedPills(ThemeMode.entries, s.themeMode, { it.label }, { vm.setTheme(it) })
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            SwitchRow("Dynamic colour", "Use colours from your wallpaper", s.dynamicColor, vm::setDynamic)
                        }
                        val dyn = s.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                        Label(if (dyn) "Theme colour (turns off dynamic colour)" else "Theme colour")
                        ThemeSwatches(if (dyn && s.accentColor != com.dayline.app.ui.theme.ThemeStyles.DYNAMIC_GRADIENT) -1 else s.accentColor, vm::setAccent)
                    }
                }
                item { WidgetArtGroup(vm) }
                item {
                    SettingsGroup("Tasks") {
                        Label("Default priority")
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                            Priority.entries.forEach { p -> FilterChip(s.defaultPriority == p, { vm.setDefaultPriority(p) }, { Text(p.label) }) }
                        }
                        ChoiceRow("Week starts on", formats.weekdayFull(s.weekStart), listOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.SUNDAY, java.time.DayOfWeek.SATURDAY).map { formats.weekdayFull(it) to it }) { vm.setWeekStart(it) }
                        ChoiceRow("Date format", LocalDate.now().format(DateTimeFormatter.ofPattern(s.dateFormat.pattern)),
                            DateFormatOption.entries.map { LocalDate.now().format(DateTimeFormatter.ofPattern(it.pattern)) to it }) { vm.setDateFormat(it) }
                        ChoiceRow("Time format", s.timeFormat.label, TimeFormat.entries.map { it.label to it }) { vm.setTimeFormat(it) }
                        NavRow("Tags", "Rename or delete tags") { nav.to(Routes.TAGS) }
                    }
                }
                item {
                    SettingsGroup("Notifications & reminders") {
                        NavRow("System notification settings", "Channels, sounds and permission") {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !vm.canScheduleExact()) {
                            NavRow("Allow precise reminders", "Without this, Android may deliver reminders a few minutes late") {
                                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
                            }
                        }
                        SwitchRow("Task & deadline reminders", null, s.taskRemindersEnabled, vm::setTaskReminders)
                        SwitchRow("Habit reminders", null, s.habitRemindersEnabled, vm::setHabitReminders)
                        SwitchRow("Focus session alerts", "Ongoing timer and session-complete notifications", s.focusNotificationsEnabled, vm::setFocusNotifications)
                        ChoiceRow(
                            "Default reminder for dated tasks",
                            s.defaultReminderOffset?.let { ReminderPlanner.describeOffset(it) } ?: "None",
                            listOf<Pair<String, Int?>>("None" to null) + ReminderPlanner.standardOffsets.map { ReminderPlanner.describeOffset(it) to it },
                        ) { vm.setDefaultReminderOffset(it) }
                        NavRow("Time for all-day tasks", formats.time(s.defaultReminderTime)) { pickTime = true }
                    }
                }
                item {
                    SettingsGroup("Focus timer") {
                        val f = s.focus
                        Stepper("Focus minutes", f.focusMinutes, 5..120) { vm.setFocus(f.copy(focusMinutes = it)) }
                        Stepper("Short break", f.shortBreakMinutes, 1..30) { vm.setFocus(f.copy(shortBreakMinutes = it)) }
                        Stepper("Long break", f.longBreakMinutes, 5..60) { vm.setFocus(f.copy(longBreakMinutes = it)) }
                        Stepper("Sessions before long break", f.sessionsBeforeLongBreak, 2..8) { vm.setFocus(f.copy(sessionsBeforeLongBreak = it)) }
                        Stepper("Daily goal (×15 min)", f.dailyGoalMinutes / 15, 1..32) { vm.setFocus(f.copy(dailyGoalMinutes = it * 15)) }
                        SwitchRow("Auto-start breaks", null, f.autoStartBreaks) { vm.setFocus(f.copy(autoStartBreaks = it)) }
                        SwitchRow("Auto-start next focus", null, f.autoStartFocus) { vm.setFocus(f.copy(autoStartFocus = it)) }
                    }
                }
                item {
                    SettingsGroup("Dashboard") {
                        NavRow("Customize Today", "Show, hide and reorder sections") { nav.to(Routes.DASHBOARD_SETTINGS) }
                        SwitchRow("Group today's tasks by priority", null, s.groupTodayByPriority, vm::setGroupByPriority)
                    }
                }
                item {
                    GoogleSyncGroup(vm)
                }
                item {
                    SettingsGroup("Data") {
                        NavRow("Export backup", "Save all tasks, habits, notes and settings as a JSON file") {
                            exportLauncher.launch("dayline-backup-${LocalDate.now()}.json")
                        }
                        NavRow("Restore from backup", "Replaces everything on this device") { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                        if (busy) LoadingIndicator(Modifier.padding(Dimens.sm))
                        Text(
                            "Dayline works fully offline. Your data stays on this device unless you export it.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Dimens.sm),
                        )
                    }
                }
                item {
                    SettingsGroup("About") {
                        InfoRow("Version", BuildConfig.VERSION_NAME)
                        InfoRow("Typeface", "Google Sans Flex (SIL Open Font License)")
                        NavRow("Re-sync reminders", "Rebuild all scheduled alarms now") { vm.resync(); scope.launch { snackbar.showSnackbar("Reminders re-synced") } }
                    }
                }
            }
        }
    }
    if (pickTime) settings?.let { s -> TimePickerDialogHost(s.defaultReminderTime, formats.is24Hour, onDismiss = { pickTime = false }) { vm.setDefaultReminderTime(it) } }
    pendingImport?.let { (json, summary) ->
        ConfirmDialog(
            title = "Restore this backup?",
            message = "It contains ${summary.tasks} tasks, ${summary.habits} habits and ${summary.notes} notes. " +
                "Everything currently in Dayline will be replaced. A safety copy of your current data is kept on the device.",
            confirmLabel = "Restore",
            onConfirm = {
                scope.launch {
                    busy = true
                    val result = runCatching { vm.importJson(json) }
                    busy = false
                    snackbar.showSnackbar(result.fold({ "Restored ${it.tasks} tasks" }, { (it as? BackupFormatException)?.message ?: "Restore failed; nothing was changed" }))
                }
            },
            onDismiss = { pendingImport = null },
        )
    }
}

@Composable
private fun SettingsGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = Dimens.sm, top = Dimens.sm))
    DaylineCard(contentPadding = PaddingValues(horizontal = Dimens.md, vertical = Dimens.sm)) { content() }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Dimens.xs, top = Dimens.sm, bottom = Dimens.xs))
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Switch) { onChange(!checked) }.padding(horizontal = Dimens.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(Dimens.sm))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun NavRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
    }
}

@Composable
private fun <T> ChoiceRow(title: String, current: String, options: List<Pair<String, T>>, onSelect: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { open = true }.padding(horizontal = Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(current, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        DropdownMenu(open, { open = false }) {
            options.forEach { (label, value) -> DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(value); open = false }) }
        }
    }
}

// ---------------- Dashboard customization ----------------

@Composable
fun DashboardSettingsScreen(nav: AppNavigator, vm: SettingsViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val bar = rememberBarScroll()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DaylineTopBar(
                scrollBehavior = bar,
                title = { Text("Customize Today") },
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = { TextButton(onClick = { vm.resetDashboard() }) { Text("Reset") } },
            )
        },
    ) { inner ->
        val s = settings ?: return@Scaffold
        ResponsiveBox(Modifier.padding(inner)) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    "Drag the handle or use the arrows to reorder. Hidden sections stay out of your dashboard.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Dimens.xl, vertical = Dimens.sm),
                )
                com.dayline.app.ui.components.ReorderableList(
                    items = s.dashboardOrder,
                    key = { it.name },
                    onReorder = vm::setOrder,
                    contentPadding = PaddingValues(horizontal = Dimens.lg, vertical = Dimens.sm),
                ) { section, handle, _ ->
                    val index = s.dashboardOrder.indexOf(section)
                    val hidden = section in s.hiddenSections
                    DaylineCard(Modifier.padding(vertical = 4.dp), contentPadding = PaddingValues(horizontal = Dimens.sm, vertical = Dimens.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.DragIndicator, "Drag to reorder ${section.label}", modifier = handle.padding(Dimens.sm))
                            Column(Modifier.weight(1f)) {
                                Text(section.label, style = MaterialTheme.typography.titleMedium, color = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                                Text(section.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton(onClick = { vm.move(section, -1) }, enabled = index > 0) { Icon(Icons.Rounded.KeyboardArrowUp, "Move ${section.label} up") }
                            IconButton(onClick = { vm.move(section, 1) }, enabled = index < s.dashboardOrder.lastIndex) { Icon(Icons.Rounded.KeyboardArrowDown, "Move ${section.label} down") }
                            IconButton(onClick = { vm.setHidden(section, !hidden) }) {
                                Icon(if (hidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (hidden) "Show ${section.label}" else "Hide ${section.label}")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Tags ----------------

@Composable
fun TagsScreen(nav: AppNavigator, vm: SettingsViewModel = hiltViewModel()) {
    val tags by vm.tags.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Tag?>(null) }
    var deleting by remember { mutableStateOf<Tag?>(null) }
    val bar = rememberBarScroll()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DaylineTopBar(
                scrollBehavior = bar,
                title = { Text("Tags") },
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
    ) { inner ->
        ResponsiveBox(Modifier.padding(inner)) {
            if (tags.isEmpty()) {
                EmptyState(Icons.Rounded.Tag, "No tags yet", "Add tags to tasks (or type #tag in Quick Add) to group related work.")
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 64.dp)) {
                    items(tags, key = { it.id }) { t ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.lg, vertical = Dimens.xs), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Tag, null, tint = MaterialTheme.colorScheme.tertiary)
                            Spacer(Modifier.width(Dimens.md))
                            Text(t.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { editing = t }) { Icon(Icons.Rounded.Edit, "Rename ${t.name}") }
                            IconButton(onClick = { deleting = t }) { Icon(Icons.Rounded.DeleteOutline, "Delete ${t.name}") }
                        }
                        HorizontalDivider(Modifier.padding(start = 56.dp))
                    }
                }
            }
        }
    }
    editing?.let { tag ->
        var name by remember(tag) { mutableStateOf(tag.name) }
        var error by remember(tag) { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Rename tag") },
            text = { OutlinedTextField(name, { name = it; error = null }, singleLine = true, isError = error != null, supportingText = { error?.let { Text(it) } }) },
            confirmButton = { TextButton(onClick = { scope.launch { error = vm.renameTag(tag, name); if (error == null) editing = null } }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
    deleting?.let { tag ->
        ConfirmDialog("Delete #${tag.name}?", "The tag is removed from all tasks. The tasks themselves are kept.", "Delete", onConfirm = { vm.deleteTag(tag.id) }, onDismiss = { deleting = null })
    }
}


/**
 * Google sync: connect a Google account and Dayline keeps a private copy of everything in Drive's
 * hidden app folder, syncing across devices automatically.
 */
@Composable
private fun GoogleSyncGroup(vm: SettingsViewModel) {
    val prefs by vm.syncPrefs.collectAsStateWithLifecycle()
    val status by vm.syncStatus.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    // True while the sign-in flow was started by "Switch account" (the old account is let go first).
    var switching by remember { mutableStateOf(false) }
    var confirmDisconnect by remember { mutableStateOf(false) }
    fun activity(): android.app.Activity? {
        var ctx = context
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
        return ctx as? android.app.Activity
    }
    fun accept(result: com.google.android.gms.auth.api.identity.AuthorizationResult) {
        if (switching) vm.switchSync(result) else vm.connectSync(result)
        switching = false
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) {
            runCatching { vm.googleSync.resultFromIntent(r.data) }.onSuccess { accept(it) }.onFailure { switching = false; vm.syncError("Google sign-in was cancelled.") }
        } else switching = false
    }
    fun handle(task: com.google.android.gms.tasks.Task<com.google.android.gms.auth.api.identity.AuthorizationResult>) {
        task.addOnSuccessListener { result ->
            val pending = result.pendingIntent
            if (result.hasResolution() && pending != null) {
                launcher.launch(androidx.activity.result.IntentSenderRequest.Builder(pending.intentSender).build())
            } else accept(result)
        }.addOnFailureListener { e ->
            switching = false
            val code = (e as? com.google.android.gms.common.api.ApiException)?.statusCode
            vm.syncError(
                if (code == com.google.android.gms.common.api.CommonStatusCodes.DEVELOPER_ERROR)
                    "Google sign-in isn't available in this version of Dayline. Please update the app."
                else "Couldn't reach Google. Check your connection and try again.",
            )
        }
    }
    fun connect() { activity()?.let { handle(vm.googleSync.authorize(it)) } }
    // "Switch account": the system account chooser, then sign-in for exactly that account.
    val chooser = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        val name = r.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_NAME)
        val type = r.data?.getStringExtra(android.accounts.AccountManager.KEY_ACCOUNT_TYPE) ?: com.dayline.app.sync.GoogleSync.GOOGLE_ACCOUNT_TYPE
        val act = activity()
        if (r.resultCode == android.app.Activity.RESULT_OK && name != null && act != null && !name.equals(prefs.account, ignoreCase = true)) {
            switching = true
            handle(vm.googleSync.authorize(act, android.accounts.Account(name, type)))
        }
    }
    fun switchAccount() { runCatching { chooser.launch(vm.googleSync.chooseAccountIntent(prefs.account)) } }
    if (confirmDisconnect) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            icon = { Icon(Icons.Rounded.CloudOff, null) },
            title = { Text("Disconnect Google account?") },
            text = { Text("Dayline stops syncing and gives back its access to ${prefs.account.ifBlank { "this account" }}. Everything stays on this phone, and the copy in Google Drive is kept.") },
            confirmButton = { TextButton(onClick = { confirmDisconnect = false; vm.disconnectSync() }) { Text("Disconnect") } },
            dismissButton = { TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") } },
        )
    }
    SettingsGroup("Google sync") {
        Row(Modifier.fillMaxWidth().padding(vertical = Dimens.sm), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape)
                    .background(if (prefs.enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (prefs.enabled) Icons.Rounded.CloudDone else Icons.Rounded.CloudOff, null,
                    tint = if (prefs.enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Dimens.md))
            Column(Modifier.weight(1f)) {
                Text(if (prefs.enabled) prefs.account.ifBlank { "Google account" } else "Not connected", style = MaterialTheme.typography.titleMedium)
                val line = when {
                    status is com.dayline.app.sync.SyncStatus.Syncing -> "Syncing…"
                    prefs.error.isNotBlank() -> prefs.error
                    status is com.dayline.app.sync.SyncStatus.Done -> (status as com.dayline.app.sync.SyncStatus.Done).message
                    prefs.enabled && prefs.lastAt > 0 -> "Last synced " + android.text.format.DateUtils.getRelativeTimeSpanString(prefs.lastAt)
                    prefs.enabled -> "Waiting for the first sync"
                    else -> "Keep tasks, notes, habits and settings in step across your devices"
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = if (prefs.error.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (status is com.dayline.app.sync.SyncStatus.Syncing) androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        if (prefs.enabled) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
                verticalArrangement = Arrangement.spacedBy(Dimens.xs),
                modifier = Modifier.padding(bottom = Dimens.sm),
            ) {
                if (prefs.error.isNotBlank()) {
                    androidx.compose.material3.FilledTonalButton(onClick = ::connect) { Text("Reconnect") }
                } else {
                    androidx.compose.material3.FilledTonalButton(onClick = vm::syncNow, enabled = status !is com.dayline.app.sync.SyncStatus.Syncing) { Text("Sync now") }
                }
                androidx.compose.material3.OutlinedButton(onClick = ::switchAccount, enabled = status !is com.dayline.app.sync.SyncStatus.Syncing) {
                    Icon(Icons.Rounded.SwitchAccount, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Dimens.sm))
                    Text("Switch account")
                }
                TextButton(onClick = { confirmDisconnect = true }) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(Dimens.sm))
                    Text("Disconnect")
                }
            }
            if (vm.googleSync.conflictCopy() != null) {
                TextButton(onClick = vm::restoreConflict) { Text("Restore the other device's version") }
            }
            Text(
                "Your data is stored privately in your Google Drive (hidden app folder). Syncs automatically after changes and every hour.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Dimens.sm),
            )
        } else {
            androidx.compose.material3.Button(onClick = ::connect, modifier = Modifier.padding(bottom = Dimens.sm)) {
                Icon(Icons.Rounded.Cloud, null, Modifier.size(18.dp))
                Spacer(Modifier.width(Dimens.sm))
                Text("Connect Google account")
            }
        }
    }
}


/** Solid colours, monochrome, gradients and the wallpaper-based "dynamic gradient", in an even grid. */
@Composable
private fun ThemeSwatches(selected: Int, onSelect: (Int) -> Unit) {
    val styles = com.dayline.app.ui.theme.ThemeStyles
    data class Choice(val id: Int, val label: String, val fill: androidx.compose.ui.graphics.Brush)
    val names = listOf("Blue", "Green", "Amber", "Red", "Pink", "Violet")
    val solids = Palette.accents.mapIndexed { i, c -> Choice(c, names.getOrElse(i) { "Colour" }, androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color(c))) } +
        Choice(styles.MONOCHROME, "Mono", androidx.compose.ui.graphics.Brush.linearGradient(
            0f to androidx.compose.ui.graphics.Color(0xFF111111), 0.5f to androidx.compose.ui.graphics.Color(0xFF111111), 0.5f to androidx.compose.ui.graphics.Color(0xFF9E9E9E), 0.75f to androidx.compose.ui.graphics.Color(0xFF9E9E9E), 0.75f to androidx.compose.ui.graphics.Color(0xFFF2F2F2), 1f to androidx.compose.ui.graphics.Color(0xFFF2F2F2),
        ))
    val gradients = styles.gradients.map { g -> Choice(g.id, g.name, androidx.compose.ui.graphics.Brush.linearGradient(listOf(g.start, g.end))) }.toMutableList()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val dark = com.dayline.app.ui.theme.DaylineTheme.isDark
        val dyn = remember(dark) { if (dark) androidx.compose.material3.dynamicDarkColorScheme(ctx) else androidx.compose.material3.dynamicLightColorScheme(ctx) }
        gradients += Choice(styles.DYNAMIC_GRADIENT, "Dynamic", androidx.compose.ui.graphics.Brush.linearGradient(listOf(dyn.primary, dyn.tertiary)))
    }
    @Composable
    fun Grid(title: String, choices: List<Choice>) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = Dimens.xs, top = Dimens.sm, bottom = Dimens.xs))
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cell = 64.dp
            val cols = (maxWidth / cell).toInt().coerceIn(3, 7)
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                choices.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        row.forEach { c ->
                            val isSel = c.id == selected
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(cell)) {
                                Box(
                                    Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(c.fill)
                                        .border(if (isSel) 3.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.CircleShape)
                                        .clickable(onClickLabel = "Use ${c.label}") { onSelect(c.id) },
                                    contentAlignment = Alignment.Center,
                                ) { if (isSel) Icon(androidx.compose.material.icons.Icons.Rounded.Done, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp)) }
                                Spacer(Modifier.height(4.dp))
                                Text(c.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.width(cell)) }
                    }
                }
            }
        }
    }
    Grid("Colours", solids)
    Grid("Gradients", gradients)
}

/** Pick the translucent background art for each home-screen widget. */
@Composable
private fun WidgetArtGroup(vm: SettingsViewModel) {
    val art by vm.widgetArt.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    SettingsGroup("Widget backgrounds") {
        com.dayline.app.core.data.prefs.WidgetArt.widgets.forEach { (key, label) ->
            Label(label)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = Dimens.sm),
                horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            ) {
                (listOf("none") + com.dayline.app.core.data.prefs.WidgetArt.styles).forEach { style ->
                    val selected = (art[key] ?: "none") == style
                    Box(
                        Modifier
                            .size(width = 72.dp, height = 52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .border(if (selected) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                            .clickable(onClickLabel = "Use $style for $label") {
                                vm.setWidgetArt(key, style)
                                scope.launch { kotlinx.coroutines.delay(300); com.dayline.app.widget.WidgetUpdater.updateAll(context) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        val res = com.dayline.app.widget.artRes(style)
                        if (res != 0) {
                            androidx.compose.foundation.Image(
                                androidx.compose.ui.res.painterResource(res), contentDescription = style,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                            )
                        } else Text("None", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
