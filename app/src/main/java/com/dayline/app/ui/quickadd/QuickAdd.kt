package com.dayline.app.ui.quickadd

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dayline.app.core.common.AppClock
import com.dayline.app.core.data.prefs.SettingsRepository
import com.dayline.app.core.data.repository.TaskRepository
import com.dayline.app.core.domain.DateRules
import com.dayline.app.core.domain.QuickAddParser
import com.dayline.app.core.model.AppSettings
import com.dayline.app.core.model.Priority
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.components.DatePickerDialogHost
import com.dayline.app.ui.components.priorityColor
import com.dayline.app.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class QuickAddDefaults(
    val date: LocalDate? = null,
    val projectId: Long? = null,
    val listId: Long? = null,
)

@HiltViewModel
class QuickAddViewModel @Inject constructor(
    private val tasks: TaskRepository,
    settings: SettingsRepository,
    clock: AppClock,
    @com.dayline.app.di.ApplicationScope private val appScope: kotlinx.coroutines.CoroutineScope,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val today: StateFlow<LocalDate> = clock.today

    /**
     * Creates the task. Explicit chip choices win over typed shortcuts; typed shortcuts ("tomorrow", "!high",
     * "#tag") fill in anything not chosen.
     */
    fun add(text: String, date: LocalDate?, dateExplicit: Boolean, priority: Priority?, onDone: (String) -> Unit) {
        val parsed = QuickAddParser.parse(text, today.value, settings.value.weekStart)
        if (parsed.title.isBlank()) return
        // App scope: the save must finish even if the sheet (or the quick-add window) closes first.
        appScope.launch {
            tasks.quickAdd(
                title = parsed.title,
                date = if (dateExplicit) date else parsed.date ?: date,
                priority = priority ?: parsed.priority ?: settings.value.defaultPriority,
                projectId = null,
                listId = null,
                tagNames = parsed.tags,
            )
            onDone(parsed.title)
        }
    }
}

@Composable
fun QuickAddSheet(
    defaults: QuickAddDefaults,
    onDismiss: () -> Unit,
    onOpenFullEditor: (QuickAddDefaults) -> Unit,
    onAdded: (String) -> Unit = {},
    scrimColor: Color = androidx.compose.material3.BottomSheetDefaults.ScrimColor,
    vm: QuickAddViewModel = hiltViewModel(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val today by vm.today.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var text by rememberSaveable { mutableStateOf("") }
    var date by remember { mutableStateOf(defaults.date) }
    var dateExplicit by remember { mutableStateOf(defaults.date != null) }
    var priority by remember { mutableStateOf<Priority?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    fun close(after: () -> Unit = {}) {
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss(); after() }
    }

    fun submit() {
        if (text.isBlank()) return
        // The view model outlives the sheet, so the save finishes after the sheet slides away.
        vm.add(text, date, dateExplicit, priority) { title -> onAdded(title) }
        text = ""
        close()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, scrimColor = scrimColor) {
        QuickAddForm(
            text = text,
            onText = { text = it },
            today = today,
            date = date,
            dateExplicit = dateExplicit,
            onDate = { d, explicit -> date = d; dateExplicit = explicit },
            onPickDate = { showDatePicker = true },
            priority = priority,
            onPriority = { priority = it },
            onSubmit = ::submit,
            onDetails = { close { onOpenFullEditor(defaults.copy(date = date)) } },
            focus = focus,
        )
    }
    if (showDatePicker) {
        DatePickerDialogHost(initial = date ?: today, onDismiss = { showDatePicker = false }) { picked -> date = picked; dateExplicit = true }
    }
}

/** Stateless body of the quick add sheet (also rendered by screenshot tests). */
@Composable
fun QuickAddForm(
    text: String,
    onText: (String) -> Unit,
    today: LocalDate,
    date: LocalDate?,
    dateExplicit: Boolean,
    onDate: (LocalDate?, Boolean) -> Unit,
    onPickDate: () -> Unit,
    priority: Priority?,
    onPriority: (Priority) -> Unit,
    onSubmit: () -> Unit,
    onDetails: () -> Unit,
    focus: FocusRequester = remember { FocusRequester() },
) {
    val formats = LocalFormats.current
    var showPriority by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(bottom = Dimens.lg),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = Dimens.xl, end = Dimens.sm), verticalAlignment = Alignment.CenterVertically) {
            Text("New task", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = onDetails) {
                Icon(Icons.Rounded.OpenInFull, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Dimens.xs))
                Text("Details")
            }
        }
        Spacer(Modifier.height(Dimens.sm))
        Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.lg), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = text,
                onValueChange = onText,
                modifier = Modifier.weight(1f).focusRequester(focus),
                placeholder = { Text("What do you need to do?", maxLines = 1) },
                shape = RoundedCornerShape(20.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
                maxLines = 3,
            )
            Spacer(Modifier.width(Dimens.sm))
            val sendScale by animateFloatAsState(if (text.isNotBlank()) 1f else 0.86f, spring(dampingRatio = 0.45f, stiffness = 500f), label = "send")
            FilledIconButton(
                onClick = onSubmit,
                enabled = text.isNotBlank(),
                modifier = Modifier.size(52.dp).graphicsLayer { scaleX = sendScale; scaleY = sendScale },
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Add task")
            }
        }
        Spacer(Modifier.height(Dimens.md))
        // Three compact chips that wrap onto a second line on narrow screens or large fonts.
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.lg),
            horizontalArrangement = Arrangement.spacedBy(Dimens.sm),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            val options = listOf(
                "Today" to today,
                "Tomorrow" to today.plusDays(1),
                "Next week" to DateRules.startOfWeek(today, formats.weekStart).plusWeeks(1),
            )
            var showDates by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = dateExplicit && date != null,
                    onClick = { showDates = true },
                    label = { Text(if (dateExplicit && date != null) formats.relative(date, today) else "Date", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(18.dp)) },
                )
                DropdownMenu(expanded = showDates, onDismissRequest = { showDates = false }) {
                    options.forEach { (label, d) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            trailingIcon = { Text(formats.monthDay(d), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) },
                            onClick = { onDate(d, true); showDates = false },
                        )
                    }
                    DropdownMenuItem(text = { Text("Pick a date…") }, leadingIcon = { Icon(Icons.Rounded.Event, null) }, onClick = { showDates = false; onPickDate() })
                    if (dateExplicit && date != null) {
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text("No date") }, onClick = { onDate(null, false); showDates = false })
                    }
                }
            }
            Box {
                FilterChip(
                    selected = priority != null,
                    onClick = { showPriority = true },
                    label = { Text(priority?.label ?: "Priority", maxLines = 1) },
                    leadingIcon = { Icon(Icons.Rounded.Flag, contentDescription = null, tint = priority?.let { priorityColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) },
                )
                DropdownMenu(expanded = showPriority, onDismissRequest = { showPriority = false }) {
                    Priority.entries.reversed().forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.label) },
                            leadingIcon = { Icon(Icons.Rounded.Flag, contentDescription = null, tint = priorityColor(p)) },
                            onClick = { onPriority(p); showPriority = false },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(Dimens.sm))
        Text(
            "Tip: type “tomorrow”, “!high” or “#tag” as you write.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Dimens.xl),
        )
    }
}
