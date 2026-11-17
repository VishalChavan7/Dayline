package com.dayline.app.ui.journal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dayline.app.core.common.AppClock
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.repository.NoteRepository
import com.dayline.app.core.domain.NoteCodec
import com.dayline.app.core.model.Note
import com.dayline.app.ui.components.DaylineCard
import com.dayline.app.ui.components.DaylineTopBar
import com.dayline.app.ui.components.EmptyState
import com.dayline.app.ui.components.ResponsiveBox
import com.dayline.app.ui.components.rememberBarScroll
import com.dayline.app.ui.navigation.AppNavigator
import com.dayline.app.ui.navigation.LocalSnackbar
import com.dayline.app.ui.notes.isJournal
import com.dayline.app.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject

/** Writing prompts, shared with the Mac app (same order, so both show the same prompt each day). */
object JournalPrompts {
    val all = listOf(
        "What made you smile today?",
        "Describe a small win from this week.",
        "What's on your mind right now?",
        "Who are you grateful for, and why?",
        "What would make tomorrow great?",
        "Write about a place that feels like home.",
        "What did you learn today?",
        "What are you looking forward to?",
        "Describe today in three words, then explain.",
        "What drained your energy today, and what gave it back?",
        "What's a moment you'd like to remember from this week?",
        "What would you tell yourself a year ago?",
    )
    fun forDay(day: LocalDate, offset: Int = 0): Int = Math.floorMod(day.toEpochDay().toInt() + offset, all.size)
}

/** A journal entry, ready to show. */
data class JournalEntry(val note: Note, val day: LocalDate, val title: String, val preview: String, val words: Int)

data class JournalUiState(
    val loading: Boolean = true,
    val entries: List<JournalEntry> = emptyList(),
    val total: Int = 0,
    val streak: Int = 0,
    val thisYear: Int = 0,
    val words: Int = 0,
    val today: LocalDate = LocalDate.now(),
    val bookmarkedOnly: Boolean = false,
)

@HiltViewModel
class JournalViewModel @Inject constructor(
    private val repo: NoteRepository,
    clock: AppClock,
    private val time: TimeProvider,
) : ViewModel() {
    private val bookmarked = MutableStateFlow(false)

    val state: StateFlow<JournalUiState> = combine(repo.notes, clock.today, bookmarked) { notes, today, onlyBookmarked ->
        val zone: ZoneId = time.zone()
        val all = notes.filter { it.isJournal() }.map { n ->
            val text = NoteCodec.plainText(n.body).trim()
            val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val title = n.title.ifBlank { lines.firstOrNull() ?: "Untitled entry" }
            val preview = (if (n.title.isBlank()) lines.drop(1) else lines).joinToString(" ")
            JournalEntry(n, n.createdAt.atZone(zone).toLocalDate(), title, preview, text.split(Regex("\\s+")).count { it.isNotBlank() })
        }.sortedByDescending { it.note.createdAt }
        val days = all.map { it.day }.toSet()
        var streak = 0
        var d = if (today in days) today else today.minusDays(1)
        while (d in days) { streak++; d = d.minusDays(1) }
        JournalUiState(
            loading = false,
            entries = if (onlyBookmarked) all.filter { it.note.isPinned } else all,
            total = all.size,
            streak = streak,
            thisYear = all.count { it.day.year == today.year },
            words = all.sumOf { it.words },
            today = today,
            bookmarkedOnly = onlyBookmarked,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JournalUiState())

    fun setBookmarkedOnly(on: Boolean) { bookmarked.value = on }
    fun toggleBookmark(n: Note) = viewModelScope.launch { repo.setPinned(n.id, !n.isPinned) }
}

@Composable
fun JournalScreen(nav: AppNavigator, vm: JournalViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    JournalContent(
        state,
        onBack = nav::back,
        onOpen = nav::note,
        onNew = { prompt -> nav.newNote(if (prompt == null) "journal" else "journal-$prompt") },
        onBookmarkedOnly = vm::setBookmarkedOnly,
        onToggleBookmark = { n ->
            vm.toggleBookmark(n)
            scope.launch { snackbar.showSnackbar(if (n.isPinned) "Bookmark removed" else "Bookmarked") }
        },
    )
}

/** Apple Journal-style: insights, today's prompt, then entries by month. */
@Composable
fun JournalContent(
    state: JournalUiState,
    onBack: () -> Unit = {},
    onOpen: (Long) -> Unit = {},
    onNew: (Int?) -> Unit = {},
    onBookmarkedOnly: (Boolean) -> Unit = {},
    onToggleBookmark: (Note) -> Unit = {},
) {
    val bar = rememberBarScroll()
    var promptOffset by remember { mutableIntStateOf(0) }
    Scaffold(
        modifier = Modifier.nestedScroll(bar.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            DaylineTopBar(
                scrollBehavior = bar,
                title = { Text("Journal") },
                subtitle = { if (!state.loading) Text("${state.total} entr${if (state.total == 1) "y" else "ies"}") },
                navigationIcon = { com.dayline.app.ui.components.BackButton(onBack) },
                actions = { IconButton(onClick = { onNew(null) }) { Icon(Icons.Rounded.Edit, contentDescription = "New entry") } },
            )
        },
    ) { inner ->
        ResponsiveBox(Modifier.padding(inner)) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                return@ResponsiveBox
            }
            val prompt = JournalPrompts.forDay(state.today, promptOffset)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = Dimens.xs, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(Dimens.md),
            ) {
                item(key = "insights") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        Insight(Icons.Rounded.LocalFireDepartment, "${state.streak}", if (state.streak == 1) "day streak" else "day streak", Modifier.weight(1f))
                        Insight(Icons.AutoMirrored.Rounded.Notes, "${state.thisYear}", "this year", Modifier.weight(1f))
                        Insight(Icons.Rounded.TextFields, compact(state.words), "words", Modifier.weight(1f))
                    }
                }
                item(key = "prompt") {
                    DaylineCard(color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoStories, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            Spacer(Modifier.width(Dimens.sm))
                            Text("Today's prompt", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.weight(1f))
                            IconButton(onClick = { promptOffset++ }) { Icon(Icons.Rounded.Shuffle, contentDescription = "Another prompt", tint = MaterialTheme.colorScheme.onTertiaryContainer) }
                        }
                        Text(JournalPrompts.all[prompt], style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.height(Dimens.md))
                        FilledTonalButton(onClick = { onNew(prompt) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Dimens.xs))
                            Text("Start writing")
                        }
                    }
                }
                item(key = "filters") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
                        FilterChip(!state.bookmarkedOnly, { onBookmarkedOnly(false) }, { Text("All entries") })
                        FilterChip(state.bookmarkedOnly, { onBookmarkedOnly(true) }, { Text("Bookmarked") }, leadingIcon = { Icon(Icons.Rounded.Bookmark, null, Modifier.size(16.dp)) })
                    }
                }
                if (state.entries.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxWidth().height(320.dp)) {
                            EmptyState(
                                Icons.Rounded.AutoStories,
                                if (state.bookmarkedOnly) "No bookmarked entries" else "Start your journal",
                                if (state.bookmarkedOnly) "Long-press an entry to bookmark it." else "Write a few lines about your day. Entries sync with Dayline on your Mac.",
                                centerAt = 0.5f,
                            )
                        }
                    }
                }
                val byMonth = state.entries.groupBy { it.day.withDayOfMonth(1) }
                byMonth.forEach { (month, entries) ->
                    item(key = "m$month") {
                        val label = month.format(DateTimeFormatter.ofPattern(if (month.year == state.today.year) "MMMM" else "MMMM yyyy"))
                        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = Dimens.xs, top = Dimens.sm))
                    }
                    items(entries, key = { it.note.id }) { e -> EntryCard(e, { onOpen(e.note.id) }, { onToggleBookmark(e.note) }) }
                }
            }
        }
    }
}

@Composable
private fun Insight(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    DaylineCard(modifier = modifier, contentPadding = PaddingValues(Dimens.md)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(Dimens.xs))
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun EntryCard(e: JournalEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    DaylineCard(onClick = onClick, onLongClick = onLongClick) {
        Row {
            Column(
                Modifier.width(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer).padding(vertical = Dimens.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(e.day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("${e.day.dayOfMonth}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(Dimens.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(e.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (e.note.isPinned) Icon(Icons.Rounded.Bookmark, contentDescription = "Bookmarked", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
                if (e.preview.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(e.preview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(Dimens.xs))
                Text("${e.words} word${if (e.words == 1) "" else "s"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun compact(n: Int): String = if (n < 1000) "$n" else String.format(Locale.getDefault(), "%.1fk", n / 1000f)
