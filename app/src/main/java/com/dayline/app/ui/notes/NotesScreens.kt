package com.dayline.app.ui.notes

import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.Check

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
import androidx.compose.material.icons.automirrored.rounded.FormatIndentDecrease
import androidx.compose.material.icons.automirrored.rounded.FormatIndentIncrease
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatAlignCenter
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatClear
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatListNumbered
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.StrikethroughS
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.repository.NoteRepository
import com.dayline.app.core.data.repository.ProjectRepository
import com.dayline.app.core.data.repository.TaskRepository
import com.dayline.app.core.domain.CheckItem
import com.dayline.app.core.domain.NoteBody
import com.dayline.app.core.domain.NoteCodec
import com.dayline.app.core.model.Note
import com.dayline.app.core.model.Project
import com.dayline.app.core.model.TaskItem
import com.dayline.app.ui.components.ConfirmDialog
import com.dayline.app.ui.components.DaylineTopBar
import com.dayline.app.ui.components.EmptyState
import com.dayline.app.ui.components.LabeledIconButton
import com.dayline.app.ui.components.ResponsiveBox
import com.dayline.app.ui.components.rememberBarScroll
import com.dayline.app.ui.motion.pressBounce
import com.dayline.app.ui.navigation.AppNavigator
import com.dayline.app.ui.navigation.LocalSnackbar
import com.dayline.app.ui.search.CompactSearchField
import com.dayline.app.ui.theme.DaylineTheme
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import javax.inject.Inject

// ---------------------------------------------------------------- colours

/** Google Keep-style note colours (key stored in [Note.color]; 0 = default). */
object NoteColors {
    data class Swatch(val key: Int, val name: String, val light: Color, val dark: Color)

    val all = listOf(
        Swatch(1, "Coral", Color(0xFFFAAFA8), Color(0xFF77172E)),
        Swatch(2, "Peach", Color(0xFFF39F76), Color(0xFF692B17)),
        Swatch(3, "Sand", Color(0xFFFFF8B8), Color(0xFF7C4A03)),
        Swatch(4, "Mint", Color(0xFFE2F6D3), Color(0xFF264D3B)),
        Swatch(5, "Sage", Color(0xFFB4DDD3), Color(0xFF0C625D)),
        Swatch(6, "Fog", Color(0xFFD4E4ED), Color(0xFF256377)),
        Swatch(7, "Storm", Color(0xFFAECCDC), Color(0xFF284255)),
        Swatch(8, "Dusk", Color(0xFFD3BFDB), Color(0xFF472E5B)),
        Swatch(9, "Blossom", Color(0xFFF6E2DD), Color(0xFF6C394F)),
        Swatch(10, "Clay", Color(0xFFE9E3D4), Color(0xFF4B443A)),
        Swatch(11, "Chalk", Color(0xFFEFEFF1), Color(0xFF232427)),
    )

    fun of(key: Int, dark: Boolean): Color? = all.firstOrNull { it.key == key }?.let { if (dark) it.dark else it.light }
}

@Composable
private fun noteBackground(key: Int): Color = NoteColors.of(key, DaylineTheme.isDark) ?: MaterialTheme.colorScheme.surface

/** Text colours offered in the editor (Samsung Notes-style palette). */
private val textColors = listOf(
    Color(0xFFD93025), Color(0xFFE8710A), Color(0xFFF9AB00), Color(0xFF188038),
    Color(0xFF1A73E8), Color(0xFF9334E6), Color(0xFF795548), Color(0xFF5F6368),
)

/** Ink used on highlighted text. */
private val HighlightInk = Color(0xFF1F1F1F)

/** Highlighter colours. */
private val highlightColors = listOf(
    Color(0xFFFFF176), Color(0xFFA5D6A7), Color(0xFF90CAF9), Color(0xFFF48FB1), Color(0xFFFFCC80),
)

// ---------------------------------------------------------------- list

data class NotesUiState(
    val loading: Boolean = true,
    val notes: List<Note> = emptyList(),
    val total: Int = 0,
    val categories: List<String> = emptyList(),
    val category: String? = null,
    val query: String = "",
    val grid: Boolean = true,
    val labelIcons: Map<String, String> = emptyMap(),
) {
    val pinned: List<Note> get() = notes.filter { it.isPinned }
    val others: List<Note> get() = notes.filter { !it.isPinned }
}

/** Labels stored on a note (comma-separated in [Note.category]). */
fun Note.labels(): List<String> = category.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }

/** Journal entries are notes with this label; they live in Journal, not in Notes. Same rule on the Mac. */
const val JOURNAL_LABEL = "journal"
fun Note.isJournal(): Boolean = JOURNAL_LABEL in labels()

/** Label → icon key, chosen by the user; read wherever label chips are drawn. */
val LocalLabelIcons = androidx.compose.runtime.compositionLocalOf { emptyMap<String, String>() }

@Composable
fun labelIcon(label: String): ImageVector {
    val key = LocalLabelIcons.current[label.lowercase()]
    return if (key == null) Icons.AutoMirrored.Outlined.Label else com.dayline.app.ui.common.IconCatalog.get(key)
}

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repo: NoteRepository,
    private val settings: com.dayline.app.core.data.prefs.SettingsRepository,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)
    private val grid = MutableStateFlow(true)
    val state: StateFlow<NotesUiState> = combine(repo.notes, query, category, grid, settings.labelIcons) { all, q, c, g, icons ->
        val notes = all.filter { !it.isJournal() }
        val filtered = notes.filter { n ->
            (c == null || c in n.labels()) &&
                (q.isBlank() || n.title.contains(q, true) || NoteCodec.plainText(n.body).contains(q, true) || (n.category?.contains(q, true) == true))
        }
        NotesUiState(false, filtered, notes.size, notes.flatMap { it.labels() }.distinct().sorted(), c, q, g, icons)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun setLabelIcon(label: String, icon: String) = viewModelScope.launch { settings.setLabelIcon(label, icon) }
    fun renameLabel(old: String, new: String) = viewModelScope.launch {
        settings.renameLabelIcon(old, new)
        repo.renameLabel(old, new)
        if (category.value == old) category.value = new.trim().lowercase()
    }

    fun setQuery(q: String) { query.value = q }
    fun setCategory(c: String?) { category.value = c }
    fun toggleGrid() { grid.value = !grid.value }
    fun togglePin(n: Note) = viewModelScope.launch { repo.setPinned(n.id, !n.isPinned) }
    fun setColor(n: Note, key: Int) = viewModelScope.launch { repo.save(n.copy(color = key)) }
    fun copy(n: Note) = viewModelScope.launch { repo.save(n.copy(id = 0, isPinned = false)) }
    suspend fun delete(n: Note) = repo.delete(n.id)
    fun restore(n: Note) = viewModelScope.launch { repo.restore(n) }
}

data class NotesActions(
    val onOpen: (Long) -> Unit = {},
    val onNew: () -> Unit = {},
    val onNewKind: (String) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onCategory: (String?) -> Unit = {},
    val onToggleGrid: () -> Unit = {},
    val onMenu: (Note) -> Unit = {},
    val onEditLabel: (String) -> Unit = {},
    val onJournal: () -> Unit = {},
)

@Composable
fun NotesScreen(nav: AppNavigator, vm: NotesViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var menuFor by remember { mutableStateOf<Note?>(null) }
    var iconFor by remember { mutableStateOf<String?>(null) }
    NotesContent(
        state,
        NotesActions(
            onOpen = nav::note,
            onNew = { nav.note(0) },
            onNewKind = { nav.newNote(it) },
            onQuery = vm::setQuery,
            onCategory = vm::setCategory,
            onToggleGrid = vm::toggleGrid,
            onMenu = { menuFor = it },
            onEditLabel = { iconFor = it },
            onJournal = { nav.to(com.dayline.app.ui.navigation.Routes.JOURNAL) },
        ),
    )
    iconFor?.let { label -> LabelIconSheet(label, onDismiss = { iconFor = null }, onRename = { vm.renameLabel(label, it) }) { vm.setLabelIcon(label, it) } }
    menuFor?.let { n ->
        NoteActionsSheet(
            note = n,
            onDismiss = { menuFor = null },
            onPin = { vm.togglePin(n) },
            onColor = { vm.setColor(n, it) },
            onCopy = { vm.copy(n) },
            onDelete = {
                scope.launch {
                    vm.delete(n)
                    if (snackbar.showSnackbar("Note deleted", actionLabel = "Undo", withDismissAction = true) == SnackbarResult.ActionPerformed) vm.restore(n)
                }
            },
        )
    }
}

/** Google Keep-style notes board: search, labels, pinned and other notes in a staggered grid. */
@Composable
fun NotesContent(state: NotesUiState, actions: NotesActions) {
    val bar = rememberBarScroll()
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DaylineTopBar(
                scrollBehavior = bar,
                title = { Text("Notes") },
                subtitle = { if (!state.loading) Text("${state.total} note${if (state.total == 1) "" else "s"}") },
                actions = {
                    LabeledIconButton(Icons.Rounded.AutoStories, "Journal", actions.onJournal)
                    LabeledIconButton(
                        if (state.grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                        if (state.grid) "List view" else "Grid view",
                        actions.onToggleGrid,
                    )
                },
            )
        },
    ) { inner ->
        ResponsiveBox(Modifier.padding(inner)) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                return@ResponsiveBox
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalLabelIcons provides state.labelIcons) {
                if (state.notes.isEmpty()) {
                    Column(Modifier.fillMaxSize().padding(horizontal = Dimens16)) {
                        CompactSearchField(state.query, actions.onQuery, "Search your notes", Modifier.fillMaxWidth().padding(top = 4.dp))
                        if (state.categories.isNotEmpty()) LabelRow(state, actions, Modifier.padding(top = 10.dp))
                        EmptyState(
                            Icons.Outlined.Lightbulb,
                            if (state.query.isNotBlank() || state.category != null) "No matching notes" else "Notes you add appear here",
                            "Tap New Note to write. Format text, add checklists, colour-code ideas and type #label to organise them.",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    return@CompositionLocalProvider
                }
                BoxWithConstraints {
                    val cols = if (!state.grid) 1 else if (maxWidth > 600.dp) 3 else 2
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(cols),
                        contentPadding = PaddingValues(start = Dimens16, end = Dimens16, top = 4.dp, bottom = 140.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalItemSpacing = 10.dp,
                    ) {
                        item(key = "search", span = StaggeredGridItemSpan.FullLine) {
                            CompactSearchField(state.query, actions.onQuery, "Search your notes", Modifier.fillMaxWidth())
                        }
                        if (state.categories.isNotEmpty()) {
                            item(key = "labels", span = StaggeredGridItemSpan.FullLine) { LabelRow(state, actions) }
                        }
                        if (state.pinned.isNotEmpty()) {
                            item(key = "pinnedLabel", span = StaggeredGridItemSpan.FullLine) { GroupLabel("Pinned") }
                            items(state.pinned, key = { it.id }) { n -> NoteCard(n, Modifier.animateItem(), { actions.onOpen(n.id) }, { actions.onMenu(n) }) }
                            if (state.others.isNotEmpty()) item(key = "othersLabel", span = StaggeredGridItemSpan.FullLine) { GroupLabel("Others") }
                        }
                        items(state.others, key = { it.id }) { n -> NoteCard(n, Modifier.animateItem(), { actions.onOpen(n.id) }, { actions.onMenu(n) }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabelRow(state: NotesUiState, actions: NotesActions, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LabelChip("All", null, state.category == null, onClick = { actions.onCategory(null) })
        state.categories.forEach { c ->
            LabelChip(c, labelIcon(c), state.category == c, onClick = { actions.onCategory(if (state.category == c) null else c) }, onLongClick = { actions.onEditLabel(c) })
        }
    }
}

private val Dimens16 = 16.dp

@Composable
private fun GroupLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun LabelChip(text: String, icon: ImageVector?, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .then(if (selected) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Change icon")
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

/** Pick the icon shown next to a #label everywhere. */
@Composable
fun LabelIconSheet(label: String, onDismiss: () -> Unit, onRemove: (() -> Unit)? = null, onRename: ((String) -> Unit)? = null, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#$label", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (onRemove != null) {
                    androidx.compose.material3.TextButton(onClick = { onRemove(); onDismiss() }) {
                        Icon(Icons.Rounded.Close, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Remove label")
                    }
                }
            }
            if (onRename != null) {
                // Rename the label (applies to every note that has it).
                var name by remember(label) { mutableStateOf(label) }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.replace(",", "").replace("#", "").take(40) },
                        label = { Text("Label name") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    androidx.compose.material3.FilledTonalButton(
                        onClick = { onRename(name.trim()); onDismiss() },
                        enabled = name.isNotBlank() && name.trim().lowercase() != label.lowercase(),
                    ) { Text("Rename") }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Icon", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            val current = LocalLabelIcons.current[label.lowercase()]
            // An even grid: the same number of icons in every row, spread edge to edge.
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cell = 48.dp
                val cols = ((maxWidth + 8.dp) / (cell + 8.dp)).toInt().coerceAtLeast(4)
                val icons = com.dayline.app.ui.common.IconCatalog.icons.toList()
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    icons.chunked(cols).forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            row.forEach { (key, vector) ->
                                val sel = key == current
                                Box(
                                    Modifier
                                        .size(cell)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .clickable(onClickLabel = key) { onPick(key); onDismiss() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(vector, key, tint = if (sel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            repeat(cols - row.size) { Spacer(Modifier.size(cell)) }
                        }
                    }
                }
            }
        }
    }
}

/** A note as Keep shows it: colour, title, formatted preview, checklist and label. */
@Composable
fun NoteCard(note: Note, modifier: Modifier = Modifier, onClick: () -> Unit, onLongClick: () -> Unit, maxLines: Int = 10) {
    val body = remember(note.body) { NoteCodec.decode(note.body) }
    val bg = NoteColors.of(note.color, DaylineTheme.isDark)
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .pressBounce(0.97f)
            .clip(shape)
            .background(bg ?: MaterialTheme.colorScheme.surface)
            .then(if (bg == null) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Note options")
            .padding(14.dp),
    ) {
        body.media.firstOrNull { it.type != com.dayline.app.core.domain.NoteMedia.Type.AUDIO }?.let { m ->
            val bmp = rememberNoteBitmap(m.file, 600)
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bmp.asImageBitmap(), null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 150.dp)
                        .aspectRatio((bmp.width.toFloat() / bmp.height).coerceIn(0.8f, 2.2f))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White),
                )
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(verticalAlignment = Alignment.Top) {
            if (note.title.isNotBlank()) {
                Text(note.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            } else Spacer(Modifier.weight(1f))
            if (note.isPinned) Icon(Icons.Rounded.PushPin, "Pinned", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Tags sit right under the title, as in the editor.
        val labels = note.labels()
        if (labels.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                labels.take(3).forEach { c -> SmallLabel(c) }
                if (labels.size > 3) SmallLabel("+${labels.size - 3}", showIcon = false)
            }
        }
        // Text and in-place checklists, in order (checklists drawn like the note's own list).
        val parts = remember(body.html) { if (NoteCodec.isHtml(body.html)) NoteCodec.splitChecklists(body.html) else listOf(NoteCodec.BodyPart.Html(body.html)) }
        parts.forEach { part ->
            when (part) {
                is NoteCodec.BodyPart.Html -> if (NoteCodec.plainText(part.html).isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    RichPreview(part.html, maxLines)
                }
                is NoteCodec.BodyPart.Checks -> {
                    Spacer(Modifier.height(6.dp))
                    CheckPreviewRows(part.items)
                }
            }
        }
        if (body.checklist.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            val open = body.checklist.filter { !it.done }
            val shown = (open + body.checklist.filter { it.done }).take(6)
            shown.forEach { item ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
                    Icon(if (item.done) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        item.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        textDecoration = if (item.done) TextDecoration.LineThrough else null,
                        color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            if (body.checklist.size > shown.size) Text("+ ${body.checklist.size - shown.size} more", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val voices = body.media.count { it.type == com.dayline.app.core.domain.NoteMedia.Type.AUDIO }
        if (voices > 0) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Mic, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(if (voices == 1) body.media.first { it.type == com.dayline.app.core.domain.NoteMedia.Type.AUDIO }.name.ifBlank { "Voice note" } else "$voices voice notes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SmallLabel(label: String, showIcon: Boolean = true) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showIcon) {
            Icon(labelIcon(label), null, Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** A rich text state with Dayline's compact list indentation. */
fun compactRichTextState(): RichTextState = RichTextState().apply { config.listIndent = 18 }

/** Formatted, read-only preview of a note's text. */
@Composable
fun RichPreview(html: String, maxLines: Int, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface)
    if (!NoteCodec.isHtml(html)) {
        Text(html, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis, modifier = modifier)
        return
    }
    val rich = remember(html) { compactRichTextState().apply { setHtml(withExplicitAlignment(html)) } }
    BasicRichText(rich, modifier, style = style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun NoteActionsSheet(note: Note, onDismiss: () -> Unit, onPin: () -> Unit, onColor: (Int) -> Unit, onCopy: () -> Unit, onDelete: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(note.title.ifBlank { "Note" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(12.dp))
            ColorRow(note.color, Modifier.padding(horizontal = 20.dp)) { onColor(it); onDismiss() }
            Spacer(Modifier.height(8.dp))
            SheetRow(if (note.isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, if (note.isPinned) "Unpin" else "Pin") { onPin(); onDismiss() }
            SheetRow(Icons.Rounded.ContentCopy, "Make a copy") { onCopy(); onDismiss() }
            SheetRow(Icons.Rounded.DeleteOutline, "Delete", MaterialTheme.colorScheme.error) { onDismiss(); onDelete() }
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, tint: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint)
        Spacer(Modifier.width(20.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint)
    }
}

/** Row of round colour swatches (first one = default). */
@Composable
private fun ColorRow(selected: Int, modifier: Modifier = Modifier, onPick: (Int) -> Unit) {
    val dark = DaylineTheme.isDark
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val options = listOf(0 to MaterialTheme.colorScheme.surface) + NoteColors.all.map { it.key to (if (dark) it.dark else it.light) }
        options.forEach { (key, c) ->
            val sel = key == selected
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(c)
                    .border(if (sel) 2.5.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable(onClickLabel = NoteColors.all.firstOrNull { it.key == key }?.name ?: "Default") { onPick(key) },
                contentAlignment = Alignment.Center,
            ) {
                if (key == 0 && !sel) Icon(Icons.Rounded.FormatColorFill, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                if (sel) Text("✓", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// ---------------------------------------------------------------- editor

data class EditCheck(val key: Long, val text: String, val done: Boolean)

data class NoteEditorState(
    val loading: Boolean = true,
    val note: Note = Note(title = ""),
    val html: String = "",
    val checklist: List<EditCheck> = emptyList(),
    val dirty: Boolean = false,
    val closed: Boolean = false,
    /** Whether the note used #labels when it was opened (older notes may have a typed label instead). */
    val hadHashtags: Boolean = false,
    val media: List<com.dayline.app.core.domain.NoteMedia> = emptyList(),
    /** How a brand-new note starts: text, list, image, drawing or audio (consumed once). */
    val startKind: String = "",
)

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: NoteRepository,
    tasks: TaskRepository,
    projects: ProjectRepository,
    private val time: TimeProvider,
    private val settings: com.dayline.app.core.data.prefs.SettingsRepository,
) : ViewModel() {
    private val noteId: Long = savedState.get<Long>("id") ?: 0L
    private val kind: String = savedState.get<String>("kind").orEmpty()
    private val _state = MutableStateFlow(NoteEditorState())
    val labelIcons: StateFlow<Map<String, String>> = settings.labelIcons.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    fun setLabelIcon(label: String, icon: String) = viewModelScope.launch { settings.setLabelIcon(label, icon) }
    /** Renames the label here and on every other note that has it. */
    fun renameLabel(old: String, new: String) {
        val to = new.trim().lowercase()
        if (to.isEmpty() || to == old) return
        _state.update { s ->
            val labels = s.note.labels().map { if (it == old) to else it }.distinct()
            s.copy(note = s.note.copy(category = labels.joinToString(",").ifEmpty { null }), dirty = true)
        }
        viewModelScope.launch { settings.renameLabelIcon(old, to); repo.renameLabel(old, to) }
    }
    val state: StateFlow<NoteEditorState> = _state.asStateFlow()
    val tasks: StateFlow<List<TaskItem>> = tasks.items.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val projects: StateFlow<List<Project>> = projects.projects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private var nextKey = 1L

    init {
        viewModelScope.launch {
            val n = if (noteId == 0L) null else repo.get(noteId)
            val body = NoteCodec.decode(n?.body.orEmpty())
            _state.value = NoteEditorState(
                loading = false,
                note = n ?: Note(
                    title = if (kind.startsWith("journal-")) kind.removePrefix("journal-").toIntOrNull()
                        ?.let { com.dayline.app.ui.journal.JournalPrompts.all.getOrNull(it) }.orEmpty() else "",
                    category = if (kind.startsWith("journal")) JOURNAL_LABEL else null,
                ),
                html = body.html,
                checklist = body.checklist.map { EditCheck(nextKey++, it.text, it.done) }.ifEmpty {
                    if (n == null && kind == "list") listOf(EditCheck(nextKey++, "", false)) else emptyList()
                },
                media = body.media,
                startKind = if (n == null) kind else "",
                closed = noteId != 0L && n == null,
                hadHashtags = n != null && NoteCodec.labelsOf(n.title, n.body).isNotEmpty(),
            )
        }
    }

    fun update(transform: (Note) -> Note) = _state.update { it.copy(note = transform(it.note), dirty = true) }
    fun addCheck(after: Long? = null): Long {
        val key = nextKey++
        _state.update { s ->
            val list = s.checklist.toMutableList()
            val i = after?.let { k -> list.indexOfFirst { it.key == k } } ?: -1
            list.add(if (i >= 0) i + 1 else list.size, EditCheck(key, "", false))
            s.copy(checklist = list, dirty = true)
        }
        return key
    }
    fun editCheck(key: Long, text: String) = _state.update { s -> s.copy(checklist = s.checklist.map { if (it.key == key) it.copy(text = text) else it }, dirty = true) }
    fun toggleCheck(key: Long) = _state.update { s -> s.copy(checklist = s.checklist.map { if (it.key == key) it.copy(done = !it.done) else it }, dirty = true) }
    fun removeCheck(key: Long) = _state.update { s -> s.copy(checklist = s.checklist.filter { it.key != key }, dirty = true) }

    /** Turns written lines into checklist items; returns the key of the last one added. */
    fun addChecks(texts: List<String>): Long? {
        var last: Long? = null
        _state.update { s ->
            val added = texts.map { EditCheck(nextKey++, it, false).also { e -> last = e.key } }
            s.copy(checklist = s.checklist + added, dirty = true)
        }
        return last
    }

    fun addLabels(labels: List<String>) = _state.update { s ->
        val merged = (s.note.labels() + labels.map { it.lowercase() }).distinct()
        s.copy(note = s.note.copy(category = merged.joinToString(",").ifEmpty { null }), dirty = true)
    }

    fun consumeStartKind() = _state.update { it.copy(startKind = "") }
    fun addMedia(m: com.dayline.app.core.domain.NoteMedia) = _state.update { s ->
        val replaced = s.media.any { it.file == m.file }
        s.copy(media = if (replaced) s.media.map { if (it.file == m.file) m else it } else s.media + m, dirty = true)
    }
    fun removeMedia(m: com.dayline.app.core.domain.NoteMedia) = _state.update { s -> s.copy(media = s.media.filter { it.file != m.file }, dirty = true) }

    fun removeLabel(label: String) = _state.update { s ->
        val left = s.note.labels().filter { it != label }
        s.copy(note = s.note.copy(category = left.joinToString(",").ifEmpty { null }), dirty = true)
    }

    /** Saves if anything changed; an empty new note is discarded, like Keep. */
    fun saveAndClose(html: String, htmlChanged: Boolean) = viewModelScope.launch {
        val s = _state.value
        val checklist = s.checklist.map { CheckItem(it.text, it.done) }
        val body = NoteBody(html, checklist, s.media)
        val empty = s.note.title.isBlank() && body.isEmpty
        val encoded = NoteCodec.encode(html, checklist, s.media)
        // Labels are tags kept beside the note (typed as #word, then lifted out of the text).
        val category = s.note.category
        when {
            empty && s.note.id != 0L -> repo.delete(s.note.id)
            empty -> Unit
            s.dirty || htmlChanged || category != s.note.category -> repo.save(s.note.copy(body = encoded, category = category))
        }
        _state.update { it.copy(closed = true) }
    }

    fun delete() = viewModelScope.launch {
        if (noteId != 0L) repo.delete(noteId)
        _state.update { it.copy(closed = true) }
    }
}

/** Everything the editor content can ask for. */
data class NoteEditorActions(
    val onBack: () -> Unit = {},
    val onUpdate: ((Note) -> Note) -> Unit = {},
    val onAddCheck: (Long?) -> Long = { 0L },
    val onEditCheck: (Long, String) -> Unit = { _, _ -> },
    val onToggleCheck: (Long) -> Unit = {},
    val onRemoveCheck: (Long) -> Unit = {},
    val onDelete: () -> Unit = {},
    val onCopy: () -> Unit = {},
    val onPickTask: () -> Unit = {},
    val onPickProject: () -> Unit = {},
    val onEditLabel: (String) -> Unit = {},
    val onAddLabels: (List<String>) -> Unit = {},
    val onAddChecks: (List<String>) -> Long? = { null },
    val onAddMedia: (com.dayline.app.core.domain.NoteMedia) -> Unit = {},
    val onRemoveMedia: (com.dayline.app.core.domain.NoteMedia) -> Unit = {},
    val onStartKindHandled: () -> Unit = {},
)

@OptIn(FlowPreview::class)
@Composable
fun NoteEditorScreen(nav: AppNavigator, vm: NoteEditorViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val projects by vm.projects.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var pickTask by remember { mutableStateOf(false) }
    var pickProject by remember { mutableStateOf(false) }
    val labelIcons by vm.labelIcons.collectAsStateWithLifecycle()
    var iconFor by remember { mutableStateOf<String?>(null) }
    val body = remember { NoteBodyModel() }
    var initialHtml by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.loading) {
        if (!state.loading && initialHtml == null) {
            body.load(state.html)
            initialHtml = body.toHtml()
        }
    }
    LaunchedEffect(state.closed) { if (state.closed) nav.back() }
    fun close() {
        // Unfinished "#label" lines (and a "#label" at the end of the title) become labels.
        val labels = body.liftTagLines().toMutableList()
        titleTag(state.note.title)?.let { (title, label) -> vm.update { it.copy(title = title) }; labels += label }
        if (labels.isNotEmpty()) vm.addLabels(labels)
        val html = if (body.isBlank()) "" else body.toHtml()
        vm.saveAndClose(html, initialHtml != null && html != initialHtml)
    }
    BackHandler { close() }
    androidx.compose.runtime.CompositionLocalProvider(LocalLabelIcons provides labelIcons) {
    NoteEditorContent(
        state = state,
        body = body,
        linkedTask = tasks.firstOrNull { it.id == state.note.taskId }?.task?.title,
        linkedProject = null,
        actions = NoteEditorActions(
            onBack = ::close,
            onUpdate = { vm.update(it) },
            onAddCheck = { vm.addCheck(it) },
            onEditCheck = { k, t -> vm.editCheck(k, t) },
            onToggleCheck = { vm.toggleCheck(it) },
            onRemoveCheck = { vm.removeCheck(it) },
            onDelete = { confirmDelete = true },
            onPickTask = { pickTask = true },
            onPickProject = { pickProject = true },
            onEditLabel = { iconFor = it },
            onAddLabels = { vm.addLabels(it) },
            onAddChecks = { vm.addChecks(it) },
            onAddMedia = { vm.addMedia(it) },
            onRemoveMedia = { vm.removeMedia(it) },
            onStartKindHandled = { vm.consumeStartKind() },
        ),
    )
    iconFor?.let { label -> LabelIconSheet(label, onDismiss = { iconFor = null }, onRemove = { vm.removeLabel(label) }, onRename = { vm.renameLabel(label, it) }) { vm.setLabelIcon(label, it) } }
    }
    if (pickTask) {
        PickerSheet("Link a task", listOf<Pair<Long?, String>>(null to "No task") + tasks.filter { !it.task.isCompleted || it.id == state.note.taskId }.take(60).map { it.id to it.task.title }, onDismiss = { pickTask = false }) { id ->
            vm.update { it.copy(taskId = id) }
        }
    }
    if (pickProject) {
        PickerSheet("Link a project", listOf<Pair<Long?, String>>(null to "No project") + projects.map { it.id to it.name }, onDismiss = { pickProject = false }) { id ->
            vm.update { it.copy(projectId = id) }
        }
    }
    if (confirmDelete) ConfirmDialog("Delete note?", "This note will be permanently removed.", "Delete", onConfirm = { vm.delete() }, onDismiss = { confirmDelete = false })
}

@Composable
private fun PickerSheet(title: String, options: List<Pair<Long?, String>>, onDismiss: () -> Unit, onPick: (Long?) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            options.forEach { (id, label) ->
                Text(
                    label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth().clickable { onPick(id); onDismiss() }.padding(horizontal = 24.dp, vertical = 14.dp),
                )
            }
        }
    }
}

/**
 * Note editor: colour-tinted page, title, rich text body, Keep-style checklist and a Samsung
 * Notes-style formatting bar that sits above the keyboard.
 */
@OptIn(FlowPreview::class)
@Composable
fun NoteEditorContent(state: NoteEditorState, body: NoteBodyModel, linkedTask: String?, linkedProject: String?, actions: NoteEditorActions) {
    val n = state.note
    val bg = noteBackground(n.color)
    var showColors by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var attachMenu by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var drawing by remember { mutableStateOf<String?>(null) } // "" = new drawing, else file to edit
    val haptics = com.dayline.app.ui.motion.rememberHaptics()
    val mediaContext = androidx.compose.ui.platform.LocalContext.current
    val mediaScope = androidx.compose.runtime.rememberCoroutineScope()
    val pickImage = rememberImagePicker { uri ->
        mediaScope.launch {
            NoteMediaStore.importImage(mediaContext, uri)?.let { actions.onAddMedia(com.dayline.app.core.domain.NoteMedia(com.dayline.app.core.domain.NoteMedia.Type.IMAGE, it)) }
        }
    }
    val startFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(state.loading, state.startKind) {
        if (state.loading || state.startKind.isEmpty()) return@LaunchedEffect
        when (state.startKind) {
            "image" -> pickImage()
            "drawing" -> drawing = ""
            "audio" -> recording = true
            "text" -> { kotlinx.coroutines.delay(420); body.focusEnd() }
            else -> if (state.startKind.startsWith("journal")) { kotlinx.coroutines.delay(420); body.focusEnd() }
        }
        actions.onStartKindHandled()
    }

    // Undo / redo over the whole body (text and in-place checklists).
    val history = remember { mutableStateListOf<String>() }
    var cursor by remember { mutableIntStateOf(-1) }
    var restoring by remember { mutableStateOf(false) }
    LaunchedEffect(body, state.loading) {
        if (state.loading) return@LaunchedEffect
        snapshotFlow { body.signature() }.debounce(450).collect {
            if (restoring) { restoring = false; return@collect }
            val h = body.toHtml()
            if (history.getOrNull(cursor) != h) {
                while (history.size > cursor + 1) history.removeAt(history.lastIndex)
                history.add(h)
                cursor = history.lastIndex
            }
        }
    }
    fun jump(to: Int) {
        if (to !in history.indices) return
        cursor = to
        restoring = true
        body.load(history[to])
    }

    Scaffold(
        containerColor = bg,
        topBar = {
            Row(
                Modifier.fillMaxWidth().background(bg).statusBarsPadding().height(64.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Save and go back") }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { jump(cursor - 1) }, enabled = cursor > 0) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo") }
                IconButton(onClick = { jump(cursor + 1) }, enabled = cursor < history.lastIndex) { Icon(Icons.AutoMirrored.Rounded.Redo, "Redo") }
                IconButton(onClick = { actions.onUpdate { it.copy(isPinned = !it.isPinned) } }) {
                    Icon(if (n.isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin, if (n.isPinned) "Unpin" else "Pin")
                }
                Box {
                    IconButton(onClick = { attachMenu = true }) { Icon(Icons.Rounded.AttachFile, "Add picture, drawing or voice note") }
                    DropdownMenu(attachMenu, { attachMenu = false }) {
                        DropdownMenuItem(text = { Text("Image") }, leadingIcon = { Icon(Icons.Rounded.Image, null) }, onClick = { attachMenu = false; pickImage() })
                        DropdownMenuItem(text = { Text("Drawing") }, leadingIcon = { Icon(Icons.Rounded.Draw, null) }, onClick = { attachMenu = false; drawing = "" })
                        DropdownMenuItem(text = { Text("Voice note") }, leadingIcon = { Icon(Icons.Rounded.Mic, null) }, onClick = { attachMenu = false; recording = true })
                    }
                }
                IconButton(onClick = { showColors = true }) { Icon(Icons.Outlined.Palette, "Note colour") }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text(linkedTask ?: "Link a task") }, leadingIcon = { Icon(Icons.Rounded.TaskAlt, null) }, onClick = { menu = false; actions.onPickTask() })
                        if (n.id != 0L) {
                            HorizontalDivider()
                            DropdownMenuItem(text = { Text("Delete", color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; actions.onDelete() })
                        }
                    }
                }
            }
        },
        bottomBar = {
            Column(Modifier.background(bg).navigationBarsPadding().imePadding()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                FormattingBar(body.activeRich, onChecklist = {
                    // The selected lines (or the line with the caret) become a checklist right where they are.
                    val seg = body.active ?: body.firstText
                    body.convertToChecklist(seg)
                    haptics.tick()
                })
            }
        },
    ) { inner ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { LoadingIndicator() }
            return@Scaffold
        }
        ResponsiveBox(Modifier.padding(inner)) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val page = maxHeight
                // Tags live beside the note (tap one to change its icon or remove it).
                val labels = n.labels()
                val scroll = rememberScrollState()
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp)) {
                    val titleStyle = MaterialTheme.typography.headlineSmall.copy(color = MaterialTheme.colorScheme.onSurface)
                    BasicTextField(
                        value = n.title,
                        onValueChange = { v -> actions.onUpdate { it.copy(title = v) } },
                        textStyle = titleStyle,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp),
                        decorationBox = { inner2 ->
                            Box(Modifier.fillMaxWidth()) {
                                if (n.title.isEmpty()) Text("Title", style = titleStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)))
                                inner2()
                            }
                        },
                    )
                    if (labels.isNotEmpty()) {
                        FlowRow(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            labels.forEach { l ->
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                        .clickable(onClickLabel = "Change icon for $l") { actions.onEditLabel(l) }
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(labelIcon(l), null, Modifier.size(15.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(l, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                    if (state.media.isNotEmpty()) {
                        NoteMediaBlock(
                            state.media,
                            onRemove = actions.onRemoveMedia,
                            onEditDrawing = { drawing = it.file },
                            onRename = actions.onAddMedia,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                    }
                    val bodyStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
                    // Text blocks and the checklists made inside them, in order. The text area fills the
                    // page when it's the only block (so alignment uses the page width and taps land in it).
                    val onlyText = body.segments.size == 1 && state.checklist.isEmpty() && state.media.isEmpty()
                    body.segments.forEachIndexed { i, seg ->
                        androidx.compose.runtime.key(seg.key) {
                            when (seg) {
                                is NoteBodyModel.TextSeg -> TextSegmentEditor(
                                    body = body,
                                    seg = seg,
                                    style = bodyStyle,
                                    minHeight = if (onlyText) page * 0.6f else 24.dp,
                                    placeholder = if (i == 0 && body.segments.size == 1) { if (n.labels().isEmpty()) "Note · type #label and press Enter to organise" else "Note" } else null,
                                    onAddLabels = actions.onAddLabels,
                                )
                                is NoteBodyModel.CheckSeg -> {
                                    Spacer(Modifier.height(4.dp))
                                    ChecklistBlock(
                                        seg.items.toList(),
                                        actions.copy(
                                            onAddCheck = { after -> body.addCheck(seg, after) },
                                            onEditCheck = { k, t -> body.editCheck(seg, k, t) },
                                            onToggleCheck = { k -> body.toggleCheck(seg, k) },
                                            onRemoveCheck = { k -> body.removeCheck(seg, k) },
                                        ),
                                        inline = true,
                                        onExit = { body.focusAfter(seg) },
                                    )
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
                    }
                    if (state.checklist.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        ChecklistBlock(state.checklist, actions)
                    }
                    if (linkedTask != null || linkedProject != null) {
                        Spacer(Modifier.height(16.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (linkedTask != null) AssistChip(onClick = actions.onPickTask, label = { Text(linkedTask, maxLines = 1, overflow = TextOverflow.Ellipsis) }, leadingIcon = { Icon(Icons.Rounded.TaskAlt, null, Modifier.size(18.dp)) })
                            if (linkedProject != null) AssistChip(onClick = actions.onPickProject, label = { Text(linkedProject) }, leadingIcon = { Icon(Icons.Rounded.Folder, null, Modifier.size(18.dp)) })
                        }
                    }
                    if (n.id != 0L) {
                        val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                        Text(
                            "Edited ${LocalDateTime.ofInstant(n.updatedAt, ZoneId.systemDefault()).format(fmt)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        )
                    }
                    // Endless page: blank space below the note; tapping it continues writing at the end.
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .height(page * 0.6f)
                            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {
                                body.focusEnd()
                            },
                    )
                }
                ScrollIndicator(scroll, Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(12.dp))
            }
        }
    }
    if (recording) {
        AudioRecorderDialog(onDismiss = { recording = false }) { file, ms ->
            recording = false
            actions.onAddMedia(com.dayline.app.core.domain.NoteMedia(com.dayline.app.core.domain.NoteMedia.Type.AUDIO, file, ms))
        }
    }
    drawing?.let { existing ->
        DrawingDialog(existing.ifEmpty { null }, onDismiss = { drawing = null }) { bmp ->
            drawing = null
            mediaScope.launch {
                val name = NoteMediaStore.saveDrawing(mediaContext, bmp, existing.ifEmpty { null })
                actions.onAddMedia(com.dayline.app.core.domain.NoteMedia(com.dayline.app.core.domain.NoteMedia.Type.DRAWING, name))
            }
        }
    }
    if (showColors) {
        ModalBottomSheet(onDismissRequest = { showColors = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Text("Colour", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(12.dp))
            ColorRow(n.color, Modifier.padding(horizontal = 20.dp)) { key -> actions.onUpdate { it.copy(color = key) }; showColors = false }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ChecklistBlock(items: List<EditCheck>, actions: NoteEditorActions, inline: Boolean = false, onExit: () -> Unit = {}) {
    var showDone by remember { mutableStateOf(true) }
    val open = items.filter { !it.done }
    val done = items.filter { it.done }
    var focusKey by remember { mutableStateOf<Long?>(null) }
    open.forEach { item ->
        CheckRow(item, actions, focus = item.key == focusKey, inline = inline) {
            // Inside the text: Enter on an empty item ends the checklist and carries on writing below.
            if (inline && item.text.isBlank()) { onExit(); actions.onRemoveCheck(item.key) } else focusKey = actions.onAddCheck(item.key)
        }
    }
    if (!inline) Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { focusKey = actions.onAddCheck(open.lastOrNull()?.key ?: items.lastOrNull()?.key) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text("List item", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (done.isNotEmpty()) {
        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Row(Modifier.fillMaxWidth().clickable { showDone = !showDone }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ExpandMore, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text("${done.size} checked item${if (done.size == 1) "" else "s"}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showDone) done.forEach { item -> CheckRow(item, actions, focus = false, inline = inline) {} }
    }
}

@Composable
private fun CheckRow(item: EditCheck, actions: NoteEditorActions, focus: Boolean, inline: Boolean = false, onNext: () -> Unit) {
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    val rowHaptics = com.dayline.app.ui.motion.rememberHaptics()
    LaunchedEffect(focus) { if (focus) runCatching { focusRequester.requestFocus() } }
    val style = MaterialTheme.typography.bodyLarge.copy(
        color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        textDecoration = if (item.done) TextDecoration.LineThrough else null,
    )
    // Box sits flush with the text margin (no 48 dp checkbox padding) like Keep.
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (item.done) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(2.dp, if (item.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, RoundedCornerShape(6.dp))
                .clickable(onClickLabel = if (item.done) "Uncheck" else "Check") { rowHaptics.complete(!item.done); actions.onToggleCheck(item.key) },
            contentAlignment = Alignment.Center,
        ) {
            if (item.done) Icon(androidx.compose.material.icons.Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = item.text,
            onValueChange = { v ->
                if (v.endsWith("\n")) { actions.onEditCheck(item.key, v.trimEnd('\n')); onNext() } else actions.onEditCheck(item.key, v)
            },
            textStyle = style,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.weight(1f).focusRequester(focusRequester)
                .onPreviewKeyEvent { e ->
                    // Backspace on an empty item removes it (checklists inside the text have no ✕).
                    if (inline && item.text.isEmpty() && e.key == androidx.compose.ui.input.key.Key.Backspace && e.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                        actions.onRemoveCheck(item.key); true
                    } else false
                },
        )
        if (!inline) {
            IconButton(onClick = { actions.onRemoveCheck(item.key) }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Close, "Remove item", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else Spacer(Modifier.height(40.dp))
    }
}

// ---------------------------------------------------------------- formatting bar

private enum class TextSize(val label: String, val size: TextUnit, val weight: FontWeight?) {
    TITLE("Title", 26.sp, FontWeight.Bold),
    HEADING("Heading", 21.sp, FontWeight.Bold),
    SUBHEADING("Subheading", 18.sp, FontWeight.SemiBold),
    BODY("Body text", TextUnit.Unspecified, null),
}

private enum class Panel { NONE, SIZE, COLOR, HIGHLIGHT }

/**
 * Samsung Notes-style toolbar: text size, bold, italic, underline, strikethrough, text colour,
 * highlight, bulleted / numbered / check lists, indent, alignment and clear formatting.
 * Size and colour pickers open inline (no pop-up), so the text selection is kept and styles
 * apply to the selected text straight away.
 */
@Composable
private fun FormattingBar(rich: RichTextState, onChecklist: () -> Unit) {
    val span = rich.currentSpanStyle
    val para = rich.currentParagraphStyle
    var panel by remember { mutableStateOf(Panel.NONE) }
    var saved by remember { mutableStateOf(androidx.compose.ui.text.TextRange.Zero) }
    fun open(p: Panel) { saved = rich.selection; panel = if (panel == p) Panel.NONE else p }
    /** Apply to the selection that was active when the picker opened (or to what's typed next). */
    fun apply(remove: List<SpanStyle>, add: SpanStyle?, keepSelection: Boolean = true) {
        val range = if (saved.collapsed) rich.wordAt(saved.min) ?: saved else saved
        if (!range.collapsed) {
            // Re-select the text and use the selection-based calls (the same path as Bold), which
            // repaint at once; the range-based calls only showed up after the next keystroke.
            rich.selection = range
            remove.forEach { rich.removeSpanStyle(it) }
            if (add != null) rich.addSpanStyle(add)
            if (!keepSelection) {
                // The editor stores the colour at once but only repaints on the next keystroke, so
                // reload the text from its own HTML: the colour shows the moment you tap it.
                val html = rich.toHtml()
                rich.setHtml(withExplicitAlignment(html))
                // Keep the text selected, so the selection stays visible after colouring it.
                val len = rich.annotatedString.text.length
                rich.selection = androidx.compose.ui.text.TextRange(range.min.coerceAtMost(len), range.max.coerceAtMost(len))
            }
        } else {
            remove.forEach { rich.removeSpanStyle(it) }
            if (add != null) rich.addSpanStyle(add)
        }
    }
    Column {
        androidx.compose.animation.AnimatedVisibility(visible = panel != Panel.NONE) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (panel) {
                    Panel.SIZE -> TextSize.entries.forEach { t ->
                        Box(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                .clickable {
                                    val sizes = TextSize.entries.filter { it.size != TextUnit.Unspecified }
                                    apply(sizes.map { SpanStyle(fontSize = it.size) } + SpanStyle(fontWeight = FontWeight.SemiBold),
                                        if (t.size == TextUnit.Unspecified) null else SpanStyle(fontSize = t.size, fontWeight = t.weight))
                                    panel = Panel.NONE
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) { Text(t.label, fontSize = if (t.size == TextUnit.Unspecified) 15.sp else (t.size.value * 0.75f).sp, fontWeight = t.weight, maxLines = 1) }
                    }
                    Panel.COLOR -> {
                        ClearSwatch { apply(textColors.map { SpanStyle(color = it) }, null, keepSelection = false); panel = Panel.NONE }
                        textColors.forEach { c -> Swatch(c) { apply(textColors.map { SpanStyle(color = it) }, SpanStyle(color = c), keepSelection = false); panel = Panel.NONE } }
                    }
                    Panel.HIGHLIGHT -> {
                        // Highlighted text is always dark so it stays readable in dark mode too.
                        ClearSwatch { apply(highlightColors.map { SpanStyle(background = it) } + SpanStyle(color = HighlightInk), null, keepSelection = false); panel = Panel.NONE }
                        highlightColors.forEach { c ->
                            Swatch(c) { apply(highlightColors.map { SpanStyle(background = it) }, SpanStyle(background = c, color = HighlightInk), keepSelection = false); panel = Panel.NONE }
                        }
                    }
                    Panel.NONE -> Unit
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormatButton(Icons.Rounded.TextFields, "Text size", panel == Panel.SIZE || span.fontSize != TextUnit.Unspecified) { open(Panel.SIZE) }
            FormatButton(Icons.Rounded.FormatBold, "Bold", span.fontWeight == FontWeight.Bold) { rich.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) }
            FormatButton(Icons.Rounded.FormatItalic, "Italic", span.fontStyle == FontStyle.Italic) { rich.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) }
            FormatButton(Icons.Rounded.FormatUnderlined, "Underline", span.textDecoration?.contains(TextDecoration.Underline) == true) {
                rich.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline))
            }
            FormatButton(Icons.Rounded.StrikethroughS, "Strikethrough", span.textDecoration?.contains(TextDecoration.LineThrough) == true) {
                rich.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.LineThrough))
            }
            FormatButton(Icons.Rounded.FormatColorText, "Text colour", panel == Panel.COLOR, accent = span.color.takeIf { it != Color.Unspecified && it != HighlightInk }) { open(Panel.COLOR) }
            FormatButton(Icons.Rounded.FormatColorFill, "Highlight", panel == Panel.HIGHLIGHT, accent = span.background.takeIf { it != Color.Unspecified }) { open(Panel.HIGHLIGHT) }
            BarDivider()
            FormatButton(Icons.AutoMirrored.Rounded.FormatListBulleted, "Bulleted list", rich.isUnorderedList) { rich.toggleUnorderedList() }
            FormatButton(Icons.Rounded.FormatListNumbered, "Numbered list", rich.isOrderedList) { rich.toggleOrderedList() }
            FormatButton(Icons.Rounded.CheckBox, "Checklist", false, onClick = onChecklist)
            FormatButton(Icons.AutoMirrored.Rounded.FormatIndentDecrease, "Decrease indent", false, enabled = rich.canDecreaseListLevel) { rich.decreaseListLevel() }
            FormatButton(Icons.AutoMirrored.Rounded.FormatIndentIncrease, "Increase indent", false, enabled = rich.canIncreaseListLevel) { rich.increaseListLevel() }
            BarDivider()
            val align = para.textAlign
            FormatButton(Icons.AutoMirrored.Rounded.FormatAlignLeft, "Align left", align == TextAlign.Left || align == TextAlign.Start) {
                rich.removeParagraphStyle(ParagraphStyle(textAlign = TextAlign.Center)); rich.removeParagraphStyle(ParagraphStyle(textAlign = TextAlign.Right))
                rich.addParagraphStyle(ParagraphStyle(textAlign = TextAlign.Left))
            }
            FormatButton(Icons.Rounded.FormatAlignCenter, "Align centre", align == TextAlign.Center) {
                rich.removeParagraphStyle(ParagraphStyle(textAlign = TextAlign.Left)); rich.removeParagraphStyle(ParagraphStyle(textAlign = TextAlign.Right))
                rich.addParagraphStyle(ParagraphStyle(textAlign = TextAlign.Center))
            }
            FormatButton(Icons.AutoMirrored.Rounded.FormatAlignRight, "Align right", align == TextAlign.Right || align == TextAlign.End) {
                rich.removeParagraphStyle(ParagraphStyle(textAlign = TextAlign.Left)); rich.removeParagraphStyle(ParagraphStyle(textAlign = TextAlign.Center))
                rich.addParagraphStyle(ParagraphStyle(textAlign = TextAlign.Right))
            }
            BarDivider()
            FormatButton(Icons.Rounded.FormatClear, "Clear formatting", false) { rich.clearSpanStyles() }
        }
    }
}

@Composable
private fun Swatch(c: Color, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).background(c).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape).clickable(onClick = onClick))
}

@Composable
private fun ClearSwatch(onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape).clickable(onClickLabel = "None", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.FormatClear, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun BarDivider() {
    Box(Modifier.padding(horizontal = 4.dp).size(width = 1.dp, height = 24.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

@Composable
private fun FormatButton(icon: ImageVector, label: String, active: Boolean, enabled: Boolean = true, accent: Color? = null, onClick: () -> Unit) {
    val bg = if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
    Box(
        Modifier
            .size(44.dp)
            .padding(2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(enabled = enabled, onClickLabel = label, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon, contentDescription = label,
            tint = when {
                !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                active -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(22.dp),
        )
        if (accent != null) {
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 5.dp).size(width = 18.dp, height = 3.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        }
    }
}


// ---------------------------------------------------------------- editor helpers

/** The word around [index] (when the caret sits inside or at the end of one). */
internal fun RichTextState.wordAt(index: Int): androidx.compose.ui.text.TextRange? {
    val t = annotatedString.text
    if (t.isEmpty()) return null
    fun isWord(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '-'
    var a = index.coerceIn(0, t.length)
    var b = a
    while (a > 0 && isWord(t[a - 1])) a--
    while (b < t.length && isWord(t[b])) b++
    return if (b > a) androidx.compose.ui.text.TextRange(a, b) else null
}

/** Re-counts every numbered list from 1 (the editor only fixes the neighbours of a removed point). */
internal fun RichTextState.renumberLists() {
    val ok = runCatching {
        RichTextState::class.java.getDeclaredMethod("checkParagraphsType").apply { isAccessible = true }.invoke(this)
    }.isSuccess
    if (!ok) {
        val sel = selection
        setHtml(toHtml())
        selection = androidx.compose.ui.text.TextRange(sel.min.coerceAtMost(annotatedString.text.length))
    }
}

/**
 * The editor keeps each paragraph as its own ParagraphStyle range (lines are not separated by
 * '\n' in its text), so paragraphs are read from those ranges.
 */
internal fun RichTextState.paragraphRanges(): List<IntRange> {
    val t = annotatedString
    val len = t.text.length
    val ranges = t.paragraphStyles.map { it.start until it.end }.distinctBy { it.first }.sortedBy { it.first }
    if (ranges.isEmpty()) {
        // Plain text: fall back to line breaks.
        val out = mutableListOf<IntRange>()
        var start = 0
        t.text.forEachIndexed { i, c -> if (c == '\n') { out += start until i; start = i + 1 } }
        out += start until len
        return out
    }
    return ranges
}

/** The paragraph containing [index] (a caret at a paragraph's very end belongs to it). */
internal fun RichTextState.paragraphAt(index: Int): IntRange {
    val ranges = paragraphRanges()
    return ranges.firstOrNull { index >= it.first && index <= it.last + 1 && (index <= it.last || ranges.none { r -> r.first == index }) }
        ?: ranges.lastOrNull() ?: (0 until 0)
}

/**
 * Thin scroll thumb on the right edge of the note: shows where you are on the endless page while
 * you scroll, then fades away. Drawn over the note, so it takes no space.
 */
@Composable
private fun ScrollIndicator(scroll: androidx.compose.foundation.ScrollState, modifier: Modifier = Modifier) {
    val active = scroll.isScrollInProgress
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        if (active) 1f else 0f,
        androidx.compose.animation.core.tween(if (active) 150 else 900, delayMillis = if (active) 0 else 600),
        label = "scrollThumb",
    )
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    androidx.compose.foundation.Canvas(modifier.graphicsLayer { this.alpha = alpha }) {
        val max = scroll.maxValue
        if (max <= 0 || max == Int.MAX_VALUE) return@Canvas
        val viewport = size.height
        val content = viewport + max
        val pad = 6.dp.toPx()
        val track = viewport - pad * 2
        val thumb = (track * viewport / content).coerceAtLeast(36.dp.toPx())
        val top = pad + (track - thumb) * (scroll.value.toFloat() / max)
        val w = 4.dp.toPx()
        drawRoundRect(
            color.copy(alpha = 0.55f),
            topLeft = androidx.compose.ui.geometry.Offset(size.width - w - 4.dp.toPx(), top),
            size = androidx.compose.ui.geometry.Size(w, thumb),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w / 2),
        )
    }
}


/**
 * New Note button for the Notes tab, drawn by the app root above the nav bar (like the + button):
 * tap for a text note; press and hold and the whole app dims, the button turns dark with an ×,
 * and a menu of note types springs out.
 */
@Composable
fun NoteFabMenu(
    visible: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onNew: () -> Unit,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.activity.compose.BackHandler(enabled = expanded) { onExpandedChange(false) }
    val kinds = listOf(
        Triple("image", "Image", Icons.Rounded.Image),
        Triple("drawing", "Drawing", Icons.Rounded.Draw),
        Triple("audio", "Audio", Icons.Rounded.Mic),
        Triple("list", "List", Icons.Rounded.Checklist),
        Triple("text", "Text", Icons.Rounded.TextFields),
    )
    val haptics = com.dayline.app.ui.motion.rememberHaptics()
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        kinds.forEachIndexed { i, (kind, label, icon) ->
            val order = kinds.size - 1 - i // nearest the button first
            val p = remember { androidx.compose.animation.core.Animatable(0f) }
            LaunchedEffect(expanded) {
                if (expanded) {
                    kotlinx.coroutines.delay(order * 32L)
                    p.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 420f))
                } else p.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 1f, stiffness = 1200f))
            }
            if (p.value > 0.01f || expanded) {
                Surface(
                    onClick = { haptics.light(); onPick(kind) },
                    enabled = expanded,
                    shape = CircleShape,
                    color = if (kind == "text") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.graphicsLayer {
                        val v = p.value
                        alpha = v.coerceIn(0f, 1f)
                        scaleX = 0.5f + 0.5f * v; scaleY = 0.5f + 0.5f * v
                        translationY = (1f - v) * 36.dp.toPx()
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)
                    },
                ) {
                    Row(Modifier.height(52.dp).padding(start = 16.dp, end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                    }
                }
            }
        }
        com.dayline.app.ui.components.PillFab(
            "New Note",
            onClick = { if (expanded) onExpandedChange(false) else onNew() },
            onLongClick = { if (!expanded) onExpandedChange(true) },
            expanded = expanded,
            forceVisible = expanded,
            hidden = !visible,
        )
    }
}

private val BLOCK_TAG = Regex("<(p|li|h[1-6])(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
private val STYLE_ATTR = Regex("style\\s*=\\s*\"", RegexOption.IGNORE_CASE)

/**
 * Gives every paragraph an explicit alignment before loading. Without it the editor carries a
 * centred/right line's alignment over to the paragraph after it when a note is reopened.
 */
internal fun withExplicitAlignment(html: String): String = BLOCK_TAG.replace(html) { m ->
    val tag = m.groupValues[1]
    val attrs = m.groupValues[2]
    when {
        attrs.contains("text-align", ignoreCase = true) -> m.value
        STYLE_ATTR.containsMatchIn(attrs) -> "<$tag" + attrs.replaceFirst(STYLE_ATTR, "style=\"text-align: left; ") + ">"
        else -> "<$tag$attrs style=\"text-align: left;\">"
    }
}


/** A paragraph's plain text (without the trailing separator). */
internal fun RichTextState.paragraphText(r: IntRange): String {
    val t = annotatedString.text
    return t.substring(r.first.coerceIn(0, t.length), (r.last + 1).coerceIn(0, t.length)).trimEnd(' ', '\n')
}

/** Caret position at the end of paragraph [k]'s text. */
internal fun List<IntRange>.lineEnd(state: RichTextState, k: Int): Int {
    val r = getOrNull(k) ?: return state.annotatedString.text.length
    return r.first + state.paragraphText(r).length
}


/** Checklist lines on a note card, drawn like the note's own checklist preview. */
@Composable
private fun CheckPreviewRows(items: List<com.dayline.app.core.domain.CheckItem>) {
    val shown = (items.filter { !it.done } + items.filter { it.done }).take(6)
    shown.forEach { item ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 1.dp)) {
            Icon(if (item.done) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                item.text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.done) TextDecoration.LineThrough else null,
                color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
    if (items.size > shown.size) Text("+ ${items.size - shown.size} more", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
