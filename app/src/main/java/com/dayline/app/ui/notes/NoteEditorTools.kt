package com.dayline.app.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dayline.app.core.domain.NoteCodec
import com.dayline.app.core.domain.NoteFormat
import com.dayline.app.core.model.Note
import com.mohamedrejeb.richeditor.model.RichTextState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** What the formatting bar's block buttons do. */
data class BlockTools(
    val onQuote: (String) -> Unit = {},
    val onCode: () -> Unit = {},
    val onTable: () -> Unit = {},
    val onDivider: () -> Unit = {},
    val onLink: () -> Unit = {},
    val onWikiLink: () -> Unit = {},
    val onMoveLine: (Boolean) -> Unit = {},
)

/** Find (and replace) in the note, above the formatting bar. */
@Composable
internal fun FindBar(body: NoteBodyModel, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var with by remember { mutableStateOf("") }
    var replacing by remember { mutableStateOf(false) }
    var index by remember { mutableIntStateOf(0) }
    var version by remember { mutableIntStateOf(0) }
    val matches = remember(query, version, body.signature()) { body.find(query) }
    fun go(i: Int) {
        if (matches.isEmpty()) return
        index = ((i % matches.size) + matches.size) % matches.size
        body.select(matches[index])
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query, { query = it; index = 0 },
                placeholder = { Text("Find in note") }, singleLine = true, modifier = Modifier.weight(1f),
                supportingText = null,
                trailingIcon = { if (query.isNotEmpty()) Text(if (matches.isEmpty()) "0" else "${index + 1}/${matches.size}", style = MaterialTheme.typography.labelMedium) },
            )
            IconButton(onClick = { go(index - 1) }, enabled = matches.isNotEmpty()) { Icon(Icons.Rounded.KeyboardArrowUp, "Previous") }
            IconButton(onClick = { go(index + 1) }, enabled = matches.isNotEmpty()) { Icon(Icons.Rounded.KeyboardArrowDown, "Next") }
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close find") }
        }
        if (replacing) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                OutlinedTextField(with, { with = it }, placeholder = { Text("Replace with") }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    matches.getOrNull(index)?.let { body.replace(it, with); version++ }
                }, enabled = matches.isNotEmpty()) { Text("Replace") }
                TextButton(onClick = { body.replaceAll(query, with); version++; index = 0 }, enabled = matches.isNotEmpty()) { Text("All") }
            }
        } else {
            TextButton(onClick = { replacing = true }) { Text("Replace…") }
        }
    }
}

/** [[Links]] in this note and notes that link here, as chips under the text. */
@OptIn(FlowPreview::class, ExperimentalLayoutApi::class)
@Composable
internal fun NoteLinksRow(body: NoteBodyModel, note: Note, allNotes: List<Note>, onOpen: (String) -> Unit) {
    var links by remember { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(body) {
        snapshotFlow { body.signature() }.debounce(600).collect { links = NoteFormat.wikiLinks(body.plainText()) }
    }
    val backlinks = remember(allNotes, note.title, note.id) { backlinksTo(note, allNotes) }
    if (links.isEmpty() && backlinks.isEmpty()) return
    Spacer(Modifier.height(16.dp))
    if (links.isNotEmpty()) {
        Text("Links", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            links.forEach { t ->
                val exists = allNotes.any { it.trashedAt == null && it.title.trim().equals(t, true) }
                AssistChip(onClick = { onOpen(t) }, label = { Text(if (exists) t else "$t (new)", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(16.dp)) })
            }
        }
    }
    if (backlinks.isNotEmpty()) {
        Text("Linked from", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            backlinks.forEach { b -> SuggestionChip(onClick = { onOpen(b.title) }, label = { Text(b.title.ifBlank { "Untitled" }, maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
        }
    }
}

fun backlinksTo(note: Note, all: List<Note>): List<Note> {
    val title = note.title.trim()
    if (title.isEmpty()) return emptyList()
    return all.filter { it.id != note.id && it.trashedAt == null && !it.locked && NoteFormat.linksTo(it.title + "\n" + NoteCodec.plainText(it.body), title) }
        .sortedByDescending { it.updatedAt }
}

/** Statistics, contents (headings) and links of the note. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NoteInfoSheet(body: NoteBodyModel, note: Note, allNotes: List<Note>, onOpenNote: (String) -> Unit, onDismiss: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    val html = remember { body.toHtml() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        SecondaryTabRow(selectedTabIndex = tab) {
            listOf("Info", "Contents", "Backlinks").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        Column(Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 460.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)) {
            when (tab) {
                0 -> {
                    val s = NoteFormat.stats(note.title, html)
                    val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
                    fun at(i: java.time.Instant) = LocalDateTime.ofInstant(i, ZoneId.systemDefault()).format(fmt)
                    Stat("Words", "${s.words}")
                    Stat("Characters", "${s.characters}")
                    Stat("Without spaces", "${s.charactersNoSpaces}")
                    Stat("Paragraphs", "${s.paragraphs}")
                    Stat("Reading time", if (s.readMinutes == 0) "—" else "${s.readMinutes} min")
                    if (note.id != 0L) {
                        Stat("Created", at(note.createdAt))
                        Stat("Edited", at(note.updatedAt))
                    }
                }
                1 -> {
                    val hs = NoteFormat.headings(html)
                    if (hs.isEmpty()) Text("Headings in the note appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    hs.forEach { h ->
                        Text(
                            h.text, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            style = if (h.level <= 2) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth().clickable {
                                body.find(h.text).firstOrNull()?.let { m -> onDismiss(); body.select(m) }
                            }.padding(start = ((h.level - 1) * 14).dp, top = 8.dp, bottom = 8.dp),
                        )
                    }
                }
                else -> {
                    val back = backlinksTo(note, allNotes)
                    if (note.title.isBlank()) Text("Give the note a title, then link to it from other notes with [[Title]].", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else if (back.isEmpty()) Text("No notes link here yet. Link to this note from another with [[${note.title}]].", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    back.forEach { b ->
                        Text(b.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth().clickable { onOpenNote(b.title) }.padding(vertical = 10.dp))
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Stat(k: String, v: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(k, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(v, fontWeight = FontWeight.Medium)
    }
}

/** Words the spell checker doesn't know; tap a suggestion to fix the word everywhere in the note. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun SpellingSheet(issues: List<NoteSpelling.Issue>, onReplace: (String, String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text("Spelling", style = MaterialTheme.typography.titleLarge)
            Text("${issues.size} word${if (issues.size == 1) "" else "s"} to check", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            issues.forEach { i ->
                Text(if (i.count > 1) "${i.word}  ×${i.count}" else i.word, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (i.suggestions.isEmpty()) Text("No suggestions", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    i.suggestions.forEach { sug -> SuggestionChip(onClick = { onReplace(i.word, sug) }, label = { Text(sug) }) }
                }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** Link the selected text (or add a link) to a web address. */
@Composable
internal fun LinkDialog(rich: RichTextState, onDismiss: () -> Unit) {
    var url by remember { mutableStateOf(rich.selectedLinkUrl.orEmpty()) }
    val clean = remember(url) { cleanUrl(url) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rich.isLink) "Edit link" else "Add link") },
        text = {
            OutlinedTextField(url, { url = it }, placeholder = { Text("https://") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        },
        confirmButton = {
            TextButton(enabled = clean != null, onClick = {
                val u = clean ?: return@TextButton
                runCatching {
                    when {
                        rich.isLink -> rich.updateLink(u)
                        rich.selection.collapsed -> rich.addLink(u.removePrefix("https://").removePrefix("http://"), u)
                        else -> rich.addLinkToSelection(u)
                    }
                }
                onDismiss()
            }) { Text("Apply") }
        },
        dismissButton = {
            Row {
                if (rich.isLink) TextButton(onClick = { runCatching { rich.removeLink() }; onDismiss() }) { Text("Remove") }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** A web address from what was typed ("dayline.app" → https://dayline.app); only web and mail links. */
fun cleanUrl(s0: String): String? {
    val s = s0.trim()
    if (s.isEmpty() || s.contains(' ')) return null
    // "javascript:…", "file:…" and other schemes are never links ("localhost:8080" is a host and port).
    val scheme0 = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(?!\\d)").find(s)?.groupValues?.get(1)?.lowercase()
    if (scheme0 != null && scheme0 !in listOf("http", "https", "mailto")) return null
    val u = if (s.contains("://") || s.startsWith("mailto:")) s else "https://$s"
    val scheme = u.substringBefore(':').lowercase()
    if (scheme !in listOf("http", "https", "mailto")) return null
    if (scheme != "mailto" && u.substringAfter("://").substringBefore('/').isBlank()) return null
    return u
}
