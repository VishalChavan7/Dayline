package com.dayline.app.ui.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.Dp
import com.dayline.app.core.domain.CheckItem
import com.dayline.app.core.domain.NoteCodec
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor

/**
 * A note's body as an ordered list of blocks: formatted text, and checklists that sit between
 * the text exactly where they were made (shown with the same checklist rows as everywhere else).
 * Stored as one HTML document, with checklist lines kept in place as box-prefixed paragraphs.
 */
@Stable
class NoteBodyModel {
    sealed interface Seg { val key: Long }
    class TextSeg(override val key: Long, val rich: RichTextState) : Seg {
        val focus = FocusRequester()
    }
    class CheckSeg(override val key: Long, items: List<EditCheck>) : Seg {
        val items = mutableStateListOf<EditCheck>().apply { addAll(items) }
    }

    val segments = mutableStateListOf<Seg>()
    /** The text block the formatting bar acts on (the last one focused). */
    var active by mutableStateOf<TextSeg?>(null)
    /** A text block that should take focus once it's on screen (caret at its end, or start). */
    var focusTarget by mutableStateOf<Long?>(null)
    var focusAtStart = false
    private var nextKey = 1L
    private var nextCheck = 50_000_000L

    init {
        // Never empty: a brand-new note is drawn (and the formatting bar wired) before load() runs.
        segments += newText("")
    }

    private fun newText(html: String): TextSeg {
        val r = compactRichTextState()
        if (html.isNotBlank()) {
            if (NoteCodec.isHtml(html)) r.setHtml(withExplicitAlignment(html)) else r.setText(html)
        }
        return TextSeg(nextKey++, r)
    }

    private fun newChecks(items: List<CheckItem>) = CheckSeg(nextKey++, items.map { EditCheck(nextCheck++, it.text, it.done) })

    fun load(html: String) {
        segments.clear()
        if (!NoteCodec.isHtml(html)) {
            segments += newText(html)
        } else {
            NoteCodec.splitChecklists(html).forEach { part ->
                segments += when (part) {
                    is NoteCodec.BodyPart.Html -> newText(part.html)
                    is NoteCodec.BodyPart.Checks -> newChecks(part.items)
                }
            }
        }
        if (segments.none { it is TextSeg }) segments.add(0, newText(""))
        active = segments.firstOrNull { it is TextSeg } as TextSeg?
    }

    val firstText: TextSeg get() = (segments.firstOrNull { it is TextSeg } as TextSeg?) ?: TextSeg(-1, compactRichTextState())
    val activeRich: RichTextState get() = (active ?: firstText).rich

    fun toHtml(): String = segments.joinToString("") { s ->
        when (s) {
            is TextSeg -> if (s.rich.toText().isBlank() && segments.size > 1) "" else s.rich.toHtml()
            is CheckSeg -> NoteCodec.checksHtml(s.items.map { CheckItem(it.text, it.done) })
        }
    }

    fun isBlank(): Boolean = segments.all { s ->
        when (s) {
            is TextSeg -> s.rich.toText().isBlank()
            is CheckSeg -> s.items.all { it.text.isBlank() }
        }
    }

    /** Turns every unfinished "#label" line into a label (used when the note is saved). */
    fun liftTagLines(): List<String> {
        val out = mutableListOf<String>()
        segments.filterIsInstance<TextSeg>().forEach { seg ->
            var ranges = seg.rich.paragraphRanges()
            for (k in ranges.indices.reversed()) {
                seg.rich.liftTagLine(k, ranges)?.let { out += it; ranges = seg.rich.paragraphRanges() }
            }
        }
        return out.distinct()
    }

    /** Reads everything that can change, for undo snapshots. */
    fun signature(): List<Any> = segments.map { s ->
        when (s) {
            is TextSeg -> s.rich.annotatedString
            is CheckSeg -> s.items.toList()
        }
    }

    /**
     * The selected lines (or the line with the caret) of [seg] become a checklist right where they
     * are: the text block splits around them.
     */
    fun convertToChecklist(seg: TextSeg) {
        val rich = seg.rich
        val ranges = rich.paragraphRanges()
        val sel = rich.selection
        val picked = (
            if (sel.collapsed) listOf(ranges.indexOf(rich.paragraphAt(sel.min)))
            else ranges.indices.filter { i -> ranges[i].first < sel.max && ranges[i].last + 1 > sel.min }
            ).filter { it >= 0 }
        val html = rich.toHtml()
        val blocks = NoteCodec.blocks(html)
        if (picked.isEmpty() || blocks.isEmpty()) return
        val first = picked.first().coerceAtMost(blocks.lastIndex)
        val last = picked.last().coerceAtMost(blocks.lastIndex)
        val before = html.substring(0, blocks[first].range.first)
        val after = html.substring(blocks[last].range.last + 1)
        val items = (first..last).map { NoteCodec.blockText(blocks[it].groupValues[3]).replace(LIST_MARK, "").trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("") }
        val at = segments.indexOf(seg)
        if (at < 0) return
        val replacement = buildList {
            if (NoteCodec.blockText(before).isNotEmpty()) add(newText(before))
            add(newChecks(items.map { CheckItem(it) }))
            if (NoteCodec.blockText(after).isNotEmpty()) add(newText(after))
        }
        segments.removeAt(at)
        segments.addAll(at, replacement)
        mergeChecklists()
        ensureText()
        active = (segments.getOrNull(at + replacement.size - 1) as? TextSeg) ?: segments.filterIsInstance<TextSeg>().lastOrNull()
    }

    fun addCheck(seg: CheckSeg, after: Long?): Long {
        val key = nextCheck++
        val i = after?.let { k -> seg.items.indexOfFirst { it.key == k } } ?: -1
        seg.items.add(if (i >= 0) i + 1 else seg.items.size, EditCheck(key, "", false))
        return key
    }

    fun editCheck(seg: CheckSeg, key: Long, text: String) {
        val i = seg.items.indexOfFirst { it.key == key }
        if (i >= 0) seg.items[i] = seg.items[i].copy(text = text)
    }

    fun toggleCheck(seg: CheckSeg, key: Long) {
        val i = seg.items.indexOfFirst { it.key == key }
        if (i >= 0) seg.items[i] = seg.items[i].copy(done = !seg.items[i].done)
    }

    fun removeCheck(seg: CheckSeg, key: Long) {
        seg.items.removeAll { it.key == key }
        if (seg.items.isEmpty()) {
            segments.remove(seg)
            ensureText()
        }
    }

    /** Puts the caret at the end of the note, adding a text block after a closing checklist. */
    fun focusEnd() {
        val last = segments.lastOrNull()
        val target = if (last is TextSeg) last else newText("").also { segments += it }
        focusAtStart = false
        focusTarget = target.key
    }

    /** Carries on writing right below a checklist (Enter on its empty last item). */
    fun focusAfter(seg: Seg) {
        val i = segments.indexOf(seg)
        val next = segments.getOrNull(i + 1)
        val target = if (next is TextSeg) next else newText("").also { segments.add(if (i >= 0) i + 1 else segments.size, it) }
        focusAtStart = true
        focusTarget = target.key
    }

    private fun mergeChecklists() {
        var i = 0
        while (i < segments.size - 1) {
            val a = segments[i]; val b = segments[i + 1]
            if (a is CheckSeg && b is CheckSeg) { a.items.addAll(b.items); segments.removeAt(i + 1) } else i++
        }
    }

    private fun ensureText() {
        if (segments.none { it is TextSeg }) segments.add(0, newText(""))
    }

    private companion object {
        val LIST_MARK = Regex("^(\\d+\\. |[•◦▪] )")
    }
}

/**
 * One formatted-text block of a note: the rich text field plus its typing helpers (#tags, "1. "
 * lists, renumbering) and a selection highlight that stays visible over highlighted (filled) text.
 */
@Composable
internal fun TextSegmentEditor(
    body: NoteBodyModel,
    seg: NoteBodyModel.TextSeg,
    style: TextStyle,
    minHeight: Dp,
    placeholder: String?,
    onAddLabels: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rich = seg.rich
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var focused by remember { mutableStateOf(false) }
    val selectionColor = LocalTextSelectionColors.current.backgroundColor

    // Labels: "#" then anything up to the end of that line (spaces allowed). The label is made when
    // you press Enter (or leave the note), so it never jumps in while you're still typing it.
    LaunchedEffect(rich) {
        var count = rich.paragraphRanges().size
        snapshotFlow { rich.annotatedString.text }.collect {
            val ranges = rich.paragraphRanges()
            val grew = ranges.size > count
            count = ranges.size
            val sel = rich.selection
            if (!grew || !sel.collapsed) return@collect
            val k = ranges.indexOf(rich.paragraphAt(sel.min))
            if (k <= 0) return@collect
            val label = rich.liftTagLine(k - 1, ranges) ?: return@collect
            onAddLabels(listOf(label))
            count = rich.paragraphRanges().size
        }
    }
    // Typing "1. " or "- " at the start of a line starts a numbered / bulleted list.
    LaunchedEffect(rich) {
        snapshotFlow { rich.annotatedString.text to rich.selection }.collect { (text, sel) ->
            if (!sel.collapsed || rich.isOrderedList || rich.isUnorderedList) return@collect
            val caret = sel.min
            val start = rich.paragraphAt(caret).first
            if (start > caret || caret > text.length) return@collect
            val typed = text.substring(start, caret)
            val ordered = typed == "1. " || typed == "1) "
            val bullet = typed == "- " || typed == "* " || typed == "• "
            if (!ordered && !bullet) return@collect
            runCatching { rich.removeTextRange(TextRange(start, caret)) }
            rich.selection = TextRange(start)
            if (ordered) rich.toggleOrderedList() else rich.toggleUnorderedList()
        }
    }
    // Numbered lists stay 1, 2, 3… when a point in the middle is removed.
    LaunchedEffect(rich) {
        var lines = rich.paragraphRanges().size
        snapshotFlow { rich.annotatedString.text }.collect { text ->
            val now = rich.paragraphRanges().size
            if (now < lines && Regex("(^|\\s)\\d+\\. ").containsMatchIn(text)) rich.renumberLists()
            lines = now
        }
    }
    // Asked to take focus (e.g. tapping the blank page below the note).
    LaunchedEffect(body.focusTarget) {
        if (body.focusTarget != seg.key) return@LaunchedEffect
        rich.selection = TextRange(if (body.focusAtStart) 0 else rich.annotatedString.text.length)
        runCatching { seg.focus.requestFocus() }
        body.focusTarget = null
    }

    BasicRichTextEditor(
        state = rich,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .focusRequester(seg.focus)
            .onFocusChanged { f ->
                focused = f.isFocused
                if (f.isFocused) body.active = seg
            }
            .drawWithContent {
                drawContent()
                // Highlighted (filled) text paints over the normal selection colour, so draw the
                // selection on top while it covers filled text: the selected part stays visible.
                val l = layout ?: return@drawWithContent
                val sel = rich.selection
                if (!focused || sel.collapsed) return@drawWithContent
                val filled = rich.annotatedString.spanStyles.any { r ->
                    r.item.background != androidx.compose.ui.graphics.Color.Unspecified &&
                        r.item.background.alpha > 0f && r.start < sel.max && r.end > sel.min
                }
                if (!filled) return@drawWithContent
                val end = sel.max.coerceAtMost(l.layoutInput.text.length)
                val start = sel.min.coerceAtMost(end)
                drawPath(l.getPathForRange(start, end), selectionColor)
            },
        textStyle = style,
        onTextLayout = { layout = it },
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        decorationBox = { inner ->
            Box(Modifier.fillMaxWidth()) {
                if (placeholder != null && rich.annotatedString.text.isEmpty()) {
                    Text(placeholder, style = style.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)))
                }
                inner()
            }
        },
    )
}


/** "#" at a word start followed by the rest of the line: the label typed at the end of a line. */
internal val LINE_TAG = Regex("(?<![\\p{L}\\p{N}_&/#])#([^#\\n]*\\p{L}[^#\\n]*)$")

/** A "#label" at the end of a title (made when the note is saved). */
internal fun titleTag(title: String): Pair<String, String>? {
    val m = LINE_TAG.find(title.trimEnd()) ?: return null
    val label = m.groupValues[1].trim().lowercase().take(40)
    if (label.isEmpty()) return null
    return title.substring(0, m.range.first).trimEnd() to label
}

/**
 * If paragraph [k] ends with "#label", removes it from the text (the whole line when that's all it
 * held) and returns the label.
 */
internal fun RichTextState.liftTagLine(k: Int, ranges: List<IntRange> = paragraphRanges()): String? {
    val r = ranges.getOrNull(k) ?: return null
    val text = paragraphText(r)
    val m = LINE_TAG.find(text) ?: return null
    val label = m.groupValues[1].trim().lowercase().take(40)
    if (label.isEmpty()) return null
    val sel = selection
    val wholeLine = text.substring(0, m.range.first).isBlank()
    val from: Int
    val to: Int
    if (wholeLine) {
        from = r.first
        to = ranges.getOrNull(k + 1)?.first ?: (r.first + text.length)
    } else {
        from = r.first + m.range.first - (if (text.getOrNull(m.range.first - 1) == ' ') 1 else 0)
        to = r.first + text.length
    }
    if (to <= from) return null
    runCatching { removeTextRange(TextRange(from, to)) }
    val removed = to - from
    val caret = when {
        sel.min >= to -> sel.min - removed
        sel.min > from -> from
        else -> sel.min
    }.coerceIn(0, annotatedString.text.length)
    selection = TextRange(caret)
    return label
}
