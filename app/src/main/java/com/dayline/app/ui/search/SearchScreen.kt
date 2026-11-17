package com.dayline.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Tag
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.dayline.app.core.common.AppClock
import com.dayline.app.core.data.repository.SearchRepository
import com.dayline.app.core.data.repository.TaskRepository
import com.dayline.app.core.model.SearchResults
import com.dayline.app.ui.components.EmptyState
import com.dayline.app.ui.components.ResponsiveBox
import com.dayline.app.ui.components.SectionLabel
import com.dayline.app.ui.components.TaskRow
import com.dayline.app.ui.navigation.AppNavigator
import com.dayline.app.ui.theme.Dimens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class SearchScope(val label: String) { ALL("All"), TASKS("Tasks"), TAGS("Tags"), NOTES("Notes") }

data class SearchUiState(val query: String = "", val scope: SearchScope = SearchScope.ALL, val searching: Boolean = false, val results: SearchResults = SearchResults(""), val includeCompleted: Boolean = false)

@HiltViewModel
class SearchViewModel @Inject constructor(private val repo: SearchRepository, private val tasks: TaskRepository, clock: AppClock) : ViewModel() {
    val query = MutableStateFlow("")
    val scope = MutableStateFlow(SearchScope.ALL)
    val includeCompleted = MutableStateFlow(false)
    val today: StateFlow<LocalDate> = clock.today

    /** Re-runs when the query changes (debounced) or when task data changes, so results stay live. */
    private val results = combine(query.debounce(180), tasks.items) { q, _ -> q }.mapLatest { q -> repo.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults(""))

    val state: StateFlow<SearchUiState> = combine(query, scope, results, includeCompleted) { q, s, r, inc ->
        SearchUiState(q, s, q.trim().length >= 2 && r.query != q.trim(), r, inc)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun toggle(id: Long, done: Boolean) = viewModelScope.launch { tasks.setCompleted(id, done) }
}

@Composable
fun SearchScreen(nav: AppNavigator, vm: SearchViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val today by vm.today.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    // Wait for the slide-in to finish before raising the keyboard; resizing mid-transition made it stutter.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(420)
        runCatching { focus.requestFocus() }
    }
    val r = state.results
    val tasks = r.tasks.filter { state.includeCompleted || !it.task.isCompleted }
    val nothing = tasks.isEmpty() && r.tags.isEmpty() && r.notes.isEmpty()
    ResponsiveBox {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.sm, vertical = Dimens.sm), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                CompactSearchField(
                    value = state.query,
                    onValueChange = { vm.query.value = it },
                    placeholder = "Search tasks, projects, notes",
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    onClear = { vm.query.value = "" },
                )
                Spacer(Modifier.width(Dimens.sm))
            }
            LazyRowChips(state, vm)
            when {
                state.query.trim().length < 2 -> EmptyState(Icons.Rounded.Search, "Search everything", "Type at least two letters. Results include task titles, descriptions, notes, tags, projects and lists.")
                state.searching && nothing -> Column(Modifier.fillMaxWidth().padding(Dimens.xxl), horizontalAlignment = Alignment.CenterHorizontally) { LoadingIndicator() }
                nothing || (state.scope == SearchScope.TASKS && tasks.isEmpty()) -> EmptyState(Icons.Rounded.SearchOff, "No results", "Nothing matches “${state.query.trim()}”.")
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 64.dp)) {
                    val show = { s: SearchScope -> state.scope == SearchScope.ALL || state.scope == s }
                    if (show(SearchScope.TASKS) && tasks.isNotEmpty()) {
                        item { SectionLabel("Tasks · ${tasks.size}") }
                        items(tasks, key = { "t${it.id}" }) { item ->
                            Row(Modifier.padding(horizontal = Dimens.sm)) {
                                TaskRow(item, today, onToggle = { vm.toggle(item.id, it) }, onClick = { nav.task(item.id) })
                            }
                        }
                    }
                    if (show(SearchScope.TAGS) && r.tags.isNotEmpty()) {
                        item { SectionLabel("Tags") }
                        items(r.tags, key = { "g${it.id}" }) { t -> ResultRow("#${t.name}", null, { Icon(Icons.Rounded.Tag, null, tint = MaterialTheme.colorScheme.tertiary) }) { vm.query.value = t.name } }
                    }
                    if (show(SearchScope.NOTES) && r.notes.isNotEmpty()) {
                        item { SectionLabel("Notes") }
                        items(r.notes, key = { "n${it.id}" }) { n -> ResultRow(n.title.ifBlank { "Untitled note" }, com.dayline.app.core.domain.NoteCodec.plainText(n.body).replace('\n', ' ').take(80).takeIf { it.isNotBlank() }, { Icon(Icons.AutoMirrored.Rounded.StickyNote2, null, tint = MaterialTheme.colorScheme.secondary) }) { nav.note(n.id) } }
                    }
                }
            }
        }
    }
}

@Composable
private fun LazyRowChips(state: SearchUiState, vm: SearchViewModel) {
    androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = Dimens.lg), horizontalArrangement = Arrangement.spacedBy(Dimens.sm)) {
        items(SearchScope.entries) { s -> FilterChip(state.scope == s, { vm.scope.value = s }, { Text(s.label) }) }
        item { FilterChip(state.includeCompleted, { vm.includeCompleted.value = !state.includeCompleted }, { Text("Include completed") }) }
    }
}

@Composable
private fun ResultRow(title: String, subtitle: String?, leading: @Composable () -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = Dimens.lg, vertical = Dimens.md), verticalAlignment = Alignment.CenterVertically) {
        leading()
        Spacer(Modifier.width(Dimens.lg))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

/** A slim (48 dp) pill search field — lighter than an outlined text field. */
@Composable
fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onClear: () -> Unit = { onValueChange("") },
) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        modifier = modifier,
        decorationBox = { inner ->
            Row(
                Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(start = Dimens.md, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(Dimens.sm))
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    inner()
                }
                if (value.isNotEmpty()) IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Close, "Clear search", modifier = Modifier.size(18.dp)) }
            }
        },
    )
}
