package com.dayline.app.core.domain

/** One line of a note's checklist. */
data class CheckItem(val text: String, val done: Boolean = false)

/** A picture, drawing or voice recording kept with a note (files live in app storage). */
data class NoteMedia(val type: Type, val file: String, val durationMs: Long = 0, val name: String = "") {
    enum class Type { IMAGE, DRAWING, AUDIO }
}

/** A note body split into its rich text (HTML), an optional Keep-style checklist and attachments. */
data class NoteBody(val html: String, val checklist: List<CheckItem> = emptyList(), val media: List<NoteMedia> = emptyList()) {
    val isEmpty: Boolean get() = NoteCodec.plainText(html).isBlank() && checklist.all { it.text.isBlank() } && media.isEmpty()
}

/**
 * Notes are stored as one string: formatted text as HTML, followed (when the note has a checklist)
 * by an HTML comment holding the items. Old plain-text notes still load as-is.
 */
object NoteCodec {
    private const val START = "<!--dl-checklist"
    private const val END = "-->"
    private val tagHint = Regex("<(p|br|b|i|u|s|em|strong|span|ol|ul|li|h[1-6]|div|mark)[\\s>/]", RegexOption.IGNORE_CASE)

    fun isHtml(s: String): Boolean = tagHint.containsMatchIn(s)

    private val hashtag = Regex("(?<![\\p{L}\\p{N}_&#/])#([\\p{L}\\p{N}_-]{1,32})")

    /**
     * Finished #words in [text] — followed by a space or line break (or anywhere when [all]) — as
     * (range to remove, label). Typing "#work " turns it into a tag and takes it out of the text.
     */
    fun completedHashtags(text: String, all: Boolean = false): List<Pair<IntRange, String>> =
        hashtag.findAll(text).mapNotNull { m ->
            val tag = m.groupValues[1]
            if (tag.none { it.isLetter() }) return@mapNotNull null
            val next = text.getOrNull(m.range.last + 1)
            val finished = all || next == ' ' || next == '\u00A0' || next == '\n'
            if (!finished) return@mapNotNull null
            // Take the space *before* the tag, never the character after it: that may be the break
            // between two paragraphs, and removing it would merge them (and shift their alignment).
            val start = if (m.range.first > 0 && text[m.range.first - 1] == ' ') m.range.first - 1 else m.range.first
            (start..m.range.last) to tag.lowercase()
        }.toList()

    /** Labels typed as #words anywhere in the note (lower-case, unique, in order of appearance). */
    fun labels(text: String): List<String> = hashtag.findAll(text)
        .map { it.groupValues[1].lowercase() }
        .filter { tag -> tag.any { it.isLetter() } }
        .distinct()
        .toList()

    /** Labels from a whole note: title, formatted text and checklist. */
    fun labelsOf(title: String, body: String): List<String> = labels(title + "\n" + plainText(body))

    private const val MEDIA = "<!--dl-media"

    /** Attachments stored in their own comment block. */
    fun media(body: String): List<NoteMedia> {
        val i = body.indexOf(MEDIA)
        if (i < 0) return emptyList()
        val end = body.indexOf(END, i).let { if (it < 0) body.length else it }
        return body.substring(i + MEDIA.length, end).lines().mapNotNull { line ->
            val parts = line.trim().split('|')
            if (parts.size < 2) return@mapNotNull null
            val type = runCatching { NoteMedia.Type.valueOf(parts[0]) }.getOrNull() ?: return@mapNotNull null
            NoteMedia(type, parts[1], parts.getOrNull(2)?.toLongOrNull() ?: 0, parts.getOrNull(3).orEmpty())
        }
    }

    private fun stripMedia(body: String): String {
        val i = body.indexOf(MEDIA)
        if (i < 0) return body
        val end = body.indexOf(END, i).let { if (it < 0) body.length else it + END.length }
        return (body.substring(0, i) + body.substring(end)).trimEnd()
    }

    fun decode(body0: String): NoteBody {
        val media = media(body0)
        val body = stripMedia(body0)
        val i = body.indexOf(START)
        if (i < 0) return NoteBody(body, media = media)
        val end = body.indexOf(END, i + START.length)
        val block = if (end < 0) body.substring(i + START.length) else body.substring(i + START.length, end)
        val items = block.lines().mapNotNull { line ->
            if (line.length < 2 || line[1] != '|') return@mapNotNull null
            CheckItem(line.substring(2), line[0] == '1')
        }
        val rest = body.substring(0, i) + if (end < 0) "" else body.substring(end + END.length)
        return NoteBody(rest.trimEnd(), items, media)
    }

    fun encode(html: String, checklist: List<CheckItem>, media: List<NoteMedia> = emptyList()): String {
        val items = checklist.filter { it.text.isNotBlank() }
        var out = html
        if (items.isNotEmpty()) {
            val lines = items.joinToString("\n") { (if (it.done) "1|" else "0|") + it.text.replace("\n", " ").replace(END, "→") }
            out = "$out\n$START\n$lines\n$END"
        }
        if (media.isNotEmpty()) {
            out = "$out\n$MEDIA\n" + media.joinToString("\n") { "${it.type.name}|${it.file}|${it.durationMs}|" + it.name.replace("|", "/").replace("\n", " ").replace("-->", "→") } + "\n$END"
        }
        return out
    }

    /** Readable text for previews, search snippets and notifications. */
    fun plainText(body: String): String {
        val decoded = decode(body)
        var s = decoded.html
        if (isHtml(s)) {
            s = s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("</(p|li|h[1-6]|div)>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]+>"), "")
                .let(::decodeEntities)
        }
        val lines = s.lines().map { it.trimEnd() }.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
        val text = lines.joinToString("\n")
        val list = decoded.checklist.joinToString("\n") { (if (it.done) "☑ " else "☐ ") + it.text }
        val media = decoded.media.joinToString(" ") {
            when (it.type) { NoteMedia.Type.IMAGE -> "🖼 Image"; NoteMedia.Type.DRAWING -> "✏️ Drawing"; NoteMedia.Type.AUDIO -> "🎙 " + it.name.ifBlank { "Voice note" } }
        }
        return listOf(text, list, media).filter { it.isNotBlank() }.joinToString("\n")
    }

    // ------------------------------------------------------------ in-text checklist lines
    //
    // A checklist line can also live inside the formatted text, where it was written: it simply
    // starts with a box character. These helpers edit those lines in the note's HTML, one
    // paragraph ("block") at a time: <p>, <li>, <h1>-<h6>, or a bare <br> for an empty line.

    const val CHECK_OPEN = "\u2610 "
    const val CHECK_DONE = "\u2611\uFE0E "

    private val block = Regex("<(p|li|h[1-6])(\\s[^>]*)?>(.*?)</\\1>|<br\\s*/?>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val leadingTags = Regex("^(\\s*<(?!/|br\\b)[^>]*>)*", RegexOption.IGNORE_CASE)

    /** Length of the check prefix at the start of a paragraph's plain text (0 if none). */
    fun checkPrefix(text: String): Int {
        val box = when {
            text.startsWith("\u2611\uFE0E") -> 2
            text.startsWith("\u2610") || text.startsWith("\u2611") -> 1
            else -> return 0
        }
        return if (text.getOrNull(box) == ' ') box + 1 else box
    }

    fun isDoneLine(text: String) = text.startsWith("\u2611")

    private fun editBlock(html: String, index: Int, edit: (String) -> String): String {
        var n = -1
        return block.replace(html) { m ->
            n++
            if (n != index) return@replace m.value
            if (m.groupValues[1].isEmpty()) {
                // An empty line written as <br>.
                val inner = edit("")
                if (inner.isEmpty()) m.value else "<p>$inner</p>"
            } else {
                val tag = m.groupValues[1]
                "<$tag${m.groupValues[2]}>" + edit(m.groupValues[3]) + "</$tag>"
            }
        }
    }

    /** Splits a block's inner HTML into its opening tags and the rest (which starts with the text). */
    private fun splitLead(inner: String): Pair<String, String> {
        val lead = leadingTags.find(inner)?.value.orEmpty()
        return lead to inner.substring(lead.length)
    }

    /** Turns the given paragraphs into checklist lines; if they all already are, turns them back. */
    fun toggleChecklistLines(html: String, indices: Collection<Int>): String {
        val all = indices.all { i -> lineHasCheck(html, i) }
        var out = html
        indices.forEach { i ->
            out = editBlock(out, i) { inner ->
                val (lead, rest) = splitLead(inner)
                val p = checkPrefix(rest)
                when {
                    all && p > 0 -> lead + rest.substring(p)
                    !all && p == 0 -> lead + CHECK_OPEN + rest
                    else -> inner
                }
            }
        }
        return out
    }

    /** Ticks or unticks one checklist line. */
    fun toggleCheck(html: String, index: Int): String = editBlock(html, index) { inner ->
        val (lead, rest) = splitLead(inner)
        when {
            isDoneLine(rest) -> lead + CHECK_OPEN + rest.substring(checkPrefix(rest))
            rest.startsWith(CHECK_OPEN) -> lead + CHECK_DONE + rest.substring(CHECK_OPEN.length)
            else -> inner
        }
    }

    /** Adds an empty checkbox to one (new, empty) line. */
    fun addCheck(html: String, index: Int): String = editBlock(html, index) { inner ->
        val (lead, rest) = splitLead(inner)
        if (checkPrefix(rest) > 0) inner else lead + CHECK_OPEN + rest.replaceFirst(Regex("^<br\\s*/?>", RegexOption.IGNORE_CASE), "")
    }

    /** Removes the checkbox from one line. */
    fun removeCheck(html: String, index: Int): String = editBlock(html, index) { inner ->
        val (lead, rest) = splitLead(inner)
        val p = checkPrefix(rest)
        if (p == 0) inner else lead + rest.substring(p)
    }

    private fun lineHasCheck(html: String, index: Int): Boolean {
        var n = -1
        block.findAll(html).forEach { m ->
            n++
            if (n == index) return checkPrefix(splitLead(m.groupValues[3]).second) > 0
        }
        return false
    }

    /** A piece of a note body: formatted HTML, or a run of checklist lines that sat in the text. */
    sealed interface BodyPart {
        data class Html(val html: String) : BodyPart
        data class Checks(val items: List<CheckItem>) : BodyPart
    }

    /** Plain text of one block's inner HTML (tags dropped, entities decoded). */
    fun blockText(inner: String): String = inner
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("<[^>]+>"), "")
        .let(::decodeEntities)
        .trim()

    fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Block matches (p/li/h*, or a bare <br> line) in document order. */
    fun blocks(html: String): List<MatchResult> = block.findAll(html).toList()

    /**
     * Splits a note's HTML into formatted parts and checklist runs, in order. Checklist lines are
     * paragraphs that start with a box character (how they're stored inside the text).
     */
    fun splitChecklists(html: String): List<BodyPart> {
        val out = mutableListOf<BodyPart>()
        var cursor = 0
        var run = mutableListOf<CheckItem>()
        fun flushRun() { if (run.isNotEmpty()) { out += BodyPart.Checks(run); run = mutableListOf() } }
        block.findAll(html).forEach { m ->
            val text = blockText(m.groupValues[3])
            val p = checkPrefix(text)
            if (m.groupValues[1].isNotEmpty() && p > 0) {
                val between = html.substring(cursor, m.range.first)
                if (between.isNotBlank()) {
                    flushRun(); out += BodyPart.Html(between)
                }
                run += CheckItem(text.substring(p).trim(), isDoneLine(text))
                cursor = m.range.last + 1
            }
        }
        flushRun()
        val rest = html.substring(cursor)
        if (rest.isNotBlank() || out.isEmpty()) out += BodyPart.Html(rest)
        return out
    }

    /** Checklist lines written back into the text, in place. */
    fun checksHtml(items: List<CheckItem>): String =
        items.joinToString("") { "<p>" + (if (it.done) CHECK_DONE else CHECK_OPEN) + escape(it.text) + "</p>" }

    private val namedEntities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
        "sol" to "/", "bsol" to "\\", "colon" to ":", "semi" to ";", "comma" to ",", "period" to ".",
        "excl" to "!", "quest" to "?", "num" to "#", "dollar" to "$", "percnt" to "%", "commat" to "@",
        "lpar" to "(", "rpar" to ")", "lsqb" to "[", "rsqb" to "]", "lcub" to "{", "rcub" to "}",
        "ast" to "*", "plus" to "+", "equals" to "=", "verbar" to "|", "lowbar" to "_", "hat" to "^",
        "grave" to "`", "tilde" to "~", "hyphen" to "-", "dash" to "-", "ndash" to "–", "mdash" to "—",
        "hellip" to "…", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”", "bull" to "•",
        "middot" to "·", "copy" to "©", "reg" to "®", "trade" to "™", "deg" to "°", "times" to "×",
        "divide" to "÷", "euro" to "€", "pound" to "£", "yen" to "¥", "cent" to "¢", "rarr" to "→",
        "larr" to "←", "uarr" to "↑", "darr" to "↓", "NewLine" to "\n", "Tab" to "\t",
    )
    private val entity = Regex("&(#[xX][0-9a-fA-F]{1,6}|#[0-9]{1,7}|[A-Za-z][A-Za-z0-9]{1,15});")

    /** Turns HTML character references (&amp;sol;, &amp;#47;, &amp;#x2F;…) back into the characters they stand for. */
    fun decodeEntities(text: String): String = entity.replace(text) { m ->
        val code = m.groupValues[1]
        when {
            code.startsWith("#x") || code.startsWith("#X") -> code.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
            code.startsWith("#") -> code.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
            else -> namedEntities[code]
        } ?: m.value
    }
}
