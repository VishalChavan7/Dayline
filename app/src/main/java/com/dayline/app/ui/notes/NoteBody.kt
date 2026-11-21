package com.dayline.app.ui.notes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
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
import com.dayline.app.core.domain.NoteFormat
import com.mohamedrejeb.richeditor.model.RichTextState
import com.mohamedrejeb.richeditor.ui.BasicRichTextEditor

/**
 * A note's body as an ordered list of blocks: formatted text, checklists that sit between the text
 * exactly where they were made, and quotes, callouts, code blocks, tables and dividers.
 * Stored as one HTML document (see [NoteFormat]); checklist lines stay box-prefixed paragraphs.
 */
@Stable
class NoteBodyModel {
    sealed interface Seg { val key: Long }
    /** A block you type formatted text into (plain text, or inside a quote or callout). */
    sealed interface RichSeg : Seg {
        val rich: RichTextState
        val focus: FocusRequester
    }
    class TextSeg(override val key: Long, override val rich: RichTextState) : RichSeg {
        override val focus = FocusRequester()
    }
    /** A quote ([kind] "quote") or a callout ("callout:tip" …), with formatted text inside. */
    class QuoteSeg(override val key: Long, kind: String, override val rich: RichTextState) : RichSeg {
        override val focus = FocusRequester()
        var kind by mutableStateOf(kind)
        val calloutKind: String? get() = kind.takeIf { it.startsWith("callout:") }?.removePrefix("callout:")
    }
    class CheckSeg(override val key: Long, items: List<EditCheck>) : Seg {
        val items = mutableStateListOf<EditCheck>().apply { addAll(items) }
    }
    class CodeSeg(override val key: Long, text: String, language: String) : Seg {
        var text by mutableStateOf(text)
        var language by mutableStateOf(language)
    }
    class TableSeg(override val key: Long, rows: List<List<String>>, header: Boolean) : Seg {
        val rows = mutableStateListOf<List<String>>().apply { addAll(rows) }
        var header by mutableStateOf(header)
        val columns: Int get() = rows.maxOfOrNull { it.size } ?: 1
    }
    class DividerSeg(override val key: Long) : Seg

    val segments = mutableStateListOf<Seg>()
    /** The formatted-text block the formatting bar acts on (the last one focused). */
    var active by mutableStateOf<RichSeg?>(null)
    /** A text block that should take focus once it's on screen (caret at its end, or start). */
    var focusTarget by mutableStateOf<Long?>(null)
    var focusAtStart = false
    private var nextKey = 1L
    private var nextCheck = 50_000_000L

    init {
        // Never empty: a brand-new note is drawn (and the formatting bar wired) before load() runs.
        segments += newText("")
    }

    private fun richOf(html: String): RichTextState {
        val r = compactRichTextState()
        if (html.isNotBlank()) {
            if (NoteCodec.isHtml(html)) r.setHtml(withExplicitAlignment(html)) else r.setText(html)
        }
        return r
    }

    private fun newText(html: String) = TextSeg(nextKey++, richOf(html))

    private fun newChecks(items: List<CheckItem>) = CheckSeg(nextKey++, items.map { EditCheck(nextCheck++, it.text, it.done) })

    fun load(html: String) {
        segments.clear()
        if (!NoteCodec.isHtml(html)) {
            segments += newText(html)
        } else {
            NoteFormat.split(html).forEach { b ->
                when (b) {
                    is NoteFormat.Block.Rich -> NoteCodec.splitChecklists(b.html).forEach { part ->
                        segments += when (part) {
                            is NoteCodec.BodyPart.Html -> newText(part.html)
                            is NoteCodec.BodyPart.Checks -> newChecks(part.items)
                        }
                    }
                    is NoteFormat.Block.Quote -> segments += QuoteSeg(nextKey++, "quote", richOf(b.html))
                    is NoteFormat.Block.Callout -> segments += QuoteSeg(nextKey++, "callout:" + b.kind, richOf(b.html))
                    is NoteFormat.Block.Code -> segments += CodeSeg(nextKey++, b.text, b.language)
                    is NoteFormat.Block.Table -> segments += TableSeg(nextKey++, b.rows, b.header)
                    NoteFormat.Block.Divider -> segments += DividerSeg(nextKey++)
                }
            }
        }
        ensureText()
        active = segments.firstOrNull { it is RichSeg } as RichSeg?
    }

    val firstText: TextSeg get() = (segments.firstOrNull { it is TextSeg } as TextSeg?) ?: TextSeg(-1, compactRichTextState())
    val activeRich: RichTextState get() = (active ?: firstText).rich

    fun toHtml(): String = segments.joinToString("") { s ->
        when (s) {
            is TextSeg -> if (s.rich.toText().isBlank() && segments.size > 1) "" else s.rich.toHtml()
            is QuoteSeg -> {
                val inner = if (s.rich.toText().isBlank()) "" else s.rich.toHtml()
                s.calloutKind?.let { NoteFormat.blockHtml(NoteFormat.Block.Callout(it, inner)) } ?: NoteFormat.blockHtml(NoteFormat.Block.Quote(inner))
            }
            is CheckSeg -> NoteCodec.checksHtml(s.items.map { CheckItem(it.text, it.done) })
            is CodeSeg -> NoteFormat.blockHtml(NoteFormat.Block.Code(s.text, s.language))
            is TableSeg -> NoteFormat.blockHtml(NoteFormat.Block.Table(s.rows.map { r -> r + List(s.columns - r.size) { "" } }, s.header))
            is DividerSeg -> "<hr>"
        }
    }

    fun isBlank(): Boolean = segments.all { s ->
        when (s) {
            is TextSeg -> s.rich.toText().isBlank()
            is QuoteSeg -> s.rich.toText().isBlank()
            is CheckSeg -> s.items.all { it.text.isBlank() }
            is CodeSeg -> s.text.isBlank()
            is TableSeg -> s.rows.all { r -> r.all { it.isBlank() } }
            is DividerSeg -> false
        }
    }

    /** Turns every unfinished "#label" line into a label (used when the note is saved). */
    fun liftTagLines(): List<String> {
        val out = mutableListOf<String>()
        segments.filterIsInstance<RichSeg>().forEach { seg ->
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
            is RichSeg -> listOf(s.rich.annotatedString, (s as? QuoteSeg)?.kind.orEmpty())
            is CheckSeg -> s.items.toList()
            is CodeSeg -> listOf(s.text, s.language)
            is TableSeg -> listOf(s.rows.toList(), s.header)
            is DividerSeg -> s.key
        }
    }

    /** The paragraphs of [seg] that are selected (or the one with the caret), as block indexes. */
    private fun pickedBlocks(seg: RichSeg): Pair<IntRange, List<MatchResult>>? {
        val rich = seg.rich
        val ranges = rich.paragraphRanges()
        val sel = rich.selection
        val picked = (
            if (sel.collapsed) listOf(ranges.indexOf(rich.paragraphAt(sel.min)))
            else ranges.indices.filter { i -> ranges[i].first < sel.max && ranges[i].last + 1 > sel.min }
            ).filter { it >= 0 }
        val blocks = NoteCodec.blocks(rich.toHtml())
        if (picked.isEmpty() || blocks.isEmpty()) return null
        return picked.first().coerceAtMost(blocks.lastIndex)..picked.last().coerceAtMost(blocks.lastIndex) to blocks
    }

    /**
     * The selected lines (or the line with the caret) of [seg] are replaced by what [make] builds from
     * them, right where they are: the text block splits around them.
     */
    private fun convertLines(seg: TextSeg, make: (htmls: List<String>, texts: List<String>) -> Seg): Seg? {
        val (picked, blocks) = pickedBlocks(seg) ?: return null
        val html = seg.rich.toHtml()
        val before = html.substring(0, blocks[picked.first].range.first)
        val after = html.substring(blocks[picked.last].range.last + 1)
        val htmls = picked.map { i -> blocks[i].value.replace(LI_OPEN, "<p>").replace(LI_CLOSE, "</p>") }
        val texts = picked.map { i -> NoteCodec.blockText(blocks[i].groupValues[3]).replace(LIST_MARK, "").trimEnd() }
        val at = segments.indexOf(seg)
        if (at < 0) return null
        val made = make(htmls, texts)
        val replacement = buildList {
            if (NoteCodec.blockText(before).isNotEmpty()) add(newText(before))
            add(made)
            if (NoteCodec.blockText(after).isNotEmpty()) add(newText(after))
        }
        segments.removeAt(at)
        segments.addAll(at, replacement)
        tidy()
        return made
    }

    /**
     * The selected lines (or the line with the caret) of [seg] become a checklist right where they
     * are: the text block splits around them.
     */
    fun convertToChecklist(seg: RichSeg) {
        if (seg !is TextSeg) return
        val made = convertLines(seg) { _, texts ->
            newChecks(texts.map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf("") }.map { CheckItem(it) })
        } ?: return
        val i = segments.indexOf(made)
        active = (segments.getOrNull(i + 1) as? RichSeg) ?: segments.filterIsInstance<RichSeg>().lastOrNull()
    }

    /** Wraps the selected lines in a quote or callout (`"quote"`, `"callout:tip"`); in one already, changes or removes it. */
    fun toggleQuote(seg: RichSeg, kind: String) {
        when (seg) {
            is QuoteSeg -> if (seg.kind == kind) unwrap(seg) else seg.kind = kind
            is TextSeg -> {
                val made = convertLines(seg) { htmls, _ -> QuoteSeg(nextKey++, kind, richOf(htmls.joinToString(""))) } as? QuoteSeg ?: return
                focusOn(made, atStart = false)
            }
        }
    }

    /** The selected lines become a code block (plain text). */
    fun makeCode(seg: RichSeg) {
        if (seg is QuoteSeg) { unwrap(seg); return }
        convertLines(seg as TextSeg) { _, texts -> CodeSeg(nextKey++, texts.joinToString("\n"), "") }
    }

    /** A table or divider goes after the line with the caret (splitting the text there). */
    fun insertAfterCaret(seg: RichSeg?, make: () -> Seg): Seg {
        val made = make()
        val target = seg ?: active
        val at = segments.indexOfFirst { it === target }
        if (target !is TextSeg || at < 0) {
            val i = if (at >= 0) at + 1 else segments.size
            segments.add(i, made)
        } else {
            val picked = pickedBlocks(target)
            val html = target.rich.toHtml()
            if (picked == null || target.rich.toText().isBlank()) {
                segments.add(at + 1, made)
            } else {
                val (range, blocks) = picked
                val cut = blocks[range.last].range.last + 1
                val before = html.substring(0, cut)
                val after = html.substring(cut)
                segments.removeAt(at)
                segments.addAll(at, buildList {
                    add(newText(before))
                    add(made)
                    if (NoteCodec.blockText(after).isNotEmpty()) add(newText(after))
                })
            }
        }
        tidy()
        return made
    }

    fun insertTable(seg: RichSeg?, rows: Int = 3, columns: Int = 3): TableSeg =
        insertAfterCaret(seg) { TableSeg(nextKey++, List(rows) { List(columns) { "" } }, true) } as TableSeg

    fun insertDivider(seg: RichSeg?) { insertAfterCaret(seg) { DividerSeg(nextKey++) } }

    /** Takes a block apart back into ordinary text (a divider just goes). */
    fun unwrap(seg: Seg) {
        val at = segments.indexOf(seg)
        if (at < 0) return
        val text: TextSeg? = when (seg) {
            is QuoteSeg -> newText(seg.rich.toHtml())
            // Leading spaces are kept as non-breaking spaces (HTML would collapse them).
            is CodeSeg -> newText(seg.text.split("\n").joinToString("") { line ->
                if (line.isEmpty()) "<br>" else "<p>" + NoteCodec.escape(line).replace(Regex("^ +")) { m -> "&nbsp;".repeat(m.value.length) } + "</p>"
            })
            is TableSeg -> newText(seg.rows.joinToString("") { r -> "<p>" + NoteCodec.escape(r.joinToString(" · ")) + "</p>" })
            else -> null
        }
        segments.removeAt(at)
        if (text != null) segments.add(at, text)
        tidy()
        text?.let { focusOn(it, atStart = false) }
    }

    fun remove(seg: Seg) {
        segments.remove(seg)
        tidy()
    }

    // Tables
    fun setCell(t: TableSeg, row: Int, col: Int, text: String) {
        val r = t.rows.getOrNull(row) ?: return
        t.rows[row] = (r + List((col + 1 - r.size).coerceAtLeast(0)) { "" }).toMutableList().also { it[col] = text.replace("\n", " ") }
    }
    fun addRow(t: TableSeg, after: Int = t.rows.lastIndex) { t.rows.add((after + 1).coerceIn(0, t.rows.size), List(t.columns) { "" }) }
    fun addColumn(t: TableSeg, after: Int = t.columns - 1) {
        val cols = t.columns
        for (i in t.rows.indices) {
            val r = t.rows[i] + List(cols - t.rows[i].size) { "" }
            t.rows[i] = r.toMutableList().apply { add((after + 1).coerceIn(0, size), "") }
        }
    }
    fun deleteRow(t: TableSeg, row: Int) { if (t.rows.size > 1) t.rows.removeAt(row.coerceIn(0, t.rows.lastIndex)) else remove(t) }
    fun deleteColumn(t: TableSeg, col: Int) {
        if (t.columns <= 1) { remove(t); return }
        for (i in t.rows.indices) t.rows[i] = t.rows[i].toMutableList().apply { if (col in indices) removeAt(col) }
    }

    /** Moves the line with the caret up or down within its text block. */
    fun moveLine(seg: RichSeg, up: Boolean) {
        val rich = seg.rich
        val html = rich.toHtml()
        val blocks = NoteCodec.blocks(html)
        val ranges = rich.paragraphRanges()
        val k = ranges.indexOf(rich.paragraphAt(rich.selection.min))
        val j = if (up) k - 1 else k + 1
        if (k !in blocks.indices || j !in blocks.indices) return
        val (a, b) = if (k < j) blocks[k] to blocks[j] else blocks[j] to blocks[k]
        val swapped = html.substring(0, a.range.first) + b.value + html.substring(a.range.last + 1, b.range.first) + a.value + html.substring(b.range.last + 1)
        rich.setHtml(withExplicitAlignment(swapped))
        val now = rich.paragraphRanges()
        now.getOrNull(j)?.let { rich.selection = TextRange((it.last + 1).coerceAtMost(rich.annotatedString.text.length)) }
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
            tidy()
        }
    }

    private fun focusOn(seg: RichSeg, atStart: Boolean) {
        active = seg
        focusAtStart = atStart
        focusTarget = seg.key
    }

    /** Puts the caret at the end of the note, adding a text block after a closing block. */
    fun focusEnd() {
        val last = segments.lastOrNull()
        val target = if (last is TextSeg) last else newText("").also { segments += it }
        focusOn(target, atStart = false)
    }

    /** Carries on writing right below a block (Enter on a checklist's empty last item). */
    fun focusAfter(seg: Seg) {
        val i = segments.indexOf(seg)
        val next = segments.getOrNull(i + 1)
        val target = if (next is TextSeg) next else newText("").also { segments.add(if (i >= 0) i + 1 else segments.size, it) }
        focusOn(target, atStart = true)
    }

    /** Joins neighbouring checklists and text blocks, and keeps a text block to type in. */
    private fun tidy() {
        var i = 0
        while (i < segments.size - 1) {
            val a = segments[i]; val b = segments[i + 1]
            when {
                a is CheckSeg && b is CheckSeg -> { a.items.addAll(b.items); segments.removeAt(i + 1) }
                a is TextSeg && b is TextSeg -> {
                    val html = listOf(a, b).filter { it.rich.toText().isNotBlank() }.joinToString("") { it.rich.toHtml() }
                    val merged = newText(html)
                    segments[i] = merged; segments.removeAt(i + 1)
                    if (active === a || active === b) active = merged
                }
                else -> i++
            }
        }
        ensureText()
        if (active != null && segments.none { it === active }) active = segments.filterIsInstance<RichSeg>().firstOrNull()
    }

    private fun ensureText() {
        if (segments.none { it is TextSeg }) segments.add(0, newText(""))
    }

    /** Every place [query] appears in the note's formatted text (case ignored). */
    data class Match(val seg: RichSeg, val range: TextRange)
    fun find(query: String): List<Match> {
        if (query.isEmpty()) return emptyList()
        return segments.filterIsInstance<RichSeg>().flatMap { seg ->
            val t = seg.rich.annotatedString.text
            buildList {
                var i = t.indexOf(query, ignoreCase = true)
                while (i >= 0) { add(Match(seg, TextRange(i, i + query.length))); i = t.indexOf(query, i + query.length.coerceAtLeast(1), ignoreCase = true) }
            }
        }
    }

    fun select(m: Match) {
        m.seg.rich.selection = m.range
        active = m.seg
        runCatching { m.seg.focus.requestFocus() }
    }

    fun replace(m: Match, with: String) {
        runCatching { m.seg.rich.replaceTextRange(m.range, with) }
    }

    /** Replaces every match; returns how many. */
    fun replaceAll(query: String, with: String): Int {
        val all = find(query)
        all.groupBy { it.seg }.forEach { (_, ms) -> ms.sortedByDescending { it.range.min }.forEach { replace(it, with) } }
        return all.size
    }

    /** Replaces a misspelt word everywhere in the note (whole words only). */
    fun replaceWord(word: String, with: String): Int {
        var n = 0
        val re = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(word) + "(?![\\p{L}\\p{N}])")
        segments.filterIsInstance<RichSeg>().forEach { seg ->
            re.findAll(seg.rich.annotatedString.text).toList().asReversed().forEach { m ->
                runCatching { seg.rich.replaceTextRange(TextRange(m.range.first, m.range.last + 1), with); n++ }
            }
        }
        return n
    }

    /** All the note's text, for statistics and spell checking. */
    fun plainText(): String = NoteCodec.plainText(toHtml())

    private companion object {
        val LIST_MARK = Regex("^(\\d+\\. |[•◦▪] )")
        val LI_OPEN = Regex("^<li(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
        val LI_CLOSE = Regex("</li>$", RegexOption.IGNORE_CASE)
    }
}

/**
 * One formatted-text block of a note: the rich text field plus its typing helpers (#tags, "1. "
 * lists, renumbering) and a selection highlight that stays visible over highlighted (filled) text.
 */
@Composable
internal fun TextSegmentEditor(
    body: NoteBodyModel,
    seg: NoteBodyModel.RichSeg,
    style: TextStyle,
    minHeight: Dp,
    placeholder: String?,
    onAddLabels: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
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
    // Keep the line you're typing on in view, with room to spare above the keyboard and toolbar:
    // after each keystroke, caret move, and whenever the keyboard opens or changes height.
    val bring = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val ime by rememberUpdatedState(WindowInsets.ime.getBottom(density))
    val room = with(density) { 48.dp.toPx() }
    LaunchedEffect(rich, focused) {
        if (!focused) return@LaunchedEffect
        snapshotFlow { Triple(rich.selection.max, layout, ime) }.collectLatest { (caret, l, _) ->
            l ?: return@collectLatest
            withFrameNanos { } // let the page resize for the keyboard first
            val c = l.getCursorRect(caret.coerceIn(0, l.layoutInput.text.length))
            runCatching { bring.bringIntoView(Rect(c.left, c.top - room / 2, c.right, c.bottom + room)) }
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
        readOnly = readOnly,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .bringIntoViewRequester(bring)
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
