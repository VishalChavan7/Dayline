package com.dayline.app.core.domain

/**
 * The parts of a note that are more than formatted text, and everything that reads a note's
 * structure: blocks (quote, callout, code, table, divider), Markdown in and out, nested #tags,
 * [[wiki links]], headings and statistics.
 *
 * A note is stored as HTML. Ordinary text uses what the editor writes (<p>, lists, <b>, <i>, <u>,
 * <s>, <code>, <a>, <mark>, <span style="color: …; background: …; font-size: …">). Blocks are:
 *   <blockquote>…</blockquote>
 *   <div class="callout" data-kind="note|tip|important|warning|caution">…</div>
 *   <pre><code class="language-x">…</code></pre>
 *   <table><tr><th>…</th></tr><tr><td>…</td></tr></table>
 *   <hr>
 * The Mac reads and writes exactly the same (NoteFormat.swift); keep the two in step.
 */
object NoteFormat {

    sealed interface Block {
        /** Ordinary formatted text (it may hold checklist lines). */
        data class Rich(val html: String) : Block
        data class Quote(val html: String) : Block
        data class Callout(val kind: String, val html: String) : Block
        data class Code(val text: String, val language: String = "") : Block
        data class Table(val rows: List<List<String>>, val header: Boolean = true) : Block
        data object Divider : Block
    }

    val CALLOUT_KINDS = listOf("note", "tip", "important", "warning", "caution")

    private val opener = Regex("<(blockquote|div|pre|table|hr)(\\s[^>]*)?/?>", RegexOption.IGNORE_CASE)

    /** Splits a note's HTML into ordinary text and blocks, in order. Joining them gives the same note back. */
    fun split(html: String): List<Block> {
        val out = mutableListOf<Block>()
        var cursor = 0
        var from = 0
        while (true) {
            val m = opener.find(html, from) ?: break
            val tag = m.groupValues[1].lowercase()
            val attrs = m.groupValues[2]
            if (tag == "div" && !Regex("class\\s*=\\s*\"[^\"]*\\bcallout\\b", RegexOption.IGNORE_CASE).containsMatchIn(attrs)) {
                from = m.range.last + 1; continue
            }
            val end = if (tag == "hr") m.range.last + 1 else closing(html, tag, m.range.last + 1)
            if (end < 0) { from = m.range.last + 1; continue }
            val before = html.substring(cursor, m.range.first)
            if (before.isNotBlank()) out += Block.Rich(before)
            val inner = if (tag == "hr") "" else html.substring(m.range.last + 1, html.lastIndexOf("</", end - 1).coerceAtLeast(m.range.last + 1))
            out += when (tag) {
                "hr" -> Block.Divider
                "blockquote" -> Block.Quote(inner.trim())
                "div" -> Block.Callout(attr(attrs, "data-kind")?.lowercase()?.takeIf { it in CALLOUT_KINDS } ?: "note", inner.trim())
                "pre" -> {
                    val code = Regex("^\\s*<code(\\s[^>]*)?>([\\s\\S]*)</code>\\s*$", RegexOption.IGNORE_CASE).find(inner)
                    val lang = code?.groupValues?.get(1)?.let { attr(it, "class") }?.split(' ')?.firstOrNull { it.startsWith("language-") }?.removePrefix("language-").orEmpty()
                    val raw = code?.groupValues?.get(2) ?: inner
                    Block.Code(NoteCodec.decodeEntities(raw.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n").replace(Regex("<[^>]+>"), "")), lang)
                }
                else -> table(inner)
            }
            cursor = end
            from = end
        }
        val rest = html.substring(cursor)
        if (rest.isNotBlank() || out.isEmpty()) out += Block.Rich(rest)
        return out
    }

    /** Index just past the </tag> that closes the one opened before [start] (same-name nesting counted). */
    private fun closing(html: String, tag: String, start: Int): Int {
        val r = Regex("<(/?)$tag(\\s[^>]*)?>", RegexOption.IGNORE_CASE)
        var depth = 1
        var i = start
        while (true) {
            val m = r.find(html, i) ?: return -1
            if (m.groupValues[1].isEmpty()) depth++ else depth--
            if (depth == 0) return m.range.last + 1
            i = m.range.last + 1
        }
    }

    private fun attr(attrs: String, name: String): String? =
        Regex("$name\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)

    private fun table(inner: String): Block.Table {
        var header = false
        val rows = Regex("<tr(\\s[^>]*)?>([\\s\\S]*?)</tr>", RegexOption.IGNORE_CASE).findAll(inner).mapIndexed { i, row ->
            val cells = Regex("<(t[hd])(\\s[^>]*)?>([\\s\\S]*?)</t[hd]>", RegexOption.IGNORE_CASE).findAll(row.groupValues[2]).map { c ->
                if (i == 0 && c.groupValues[1].equals("th", true)) header = true
                NoteCodec.blockText(c.groupValues[3])
            }.toList()
            cells
        }.filter { it.isNotEmpty() }.toList()
        val width = rows.maxOfOrNull { it.size } ?: 1
        return Block.Table(rows.map { r -> r + List(width - r.size) { "" } }.ifEmpty { listOf(List(width) { "" }) }, header)
    }

    fun join(blocks: List<Block>): String = blocks.joinToString("") { blockHtml(it) }

    fun blockHtml(b: Block): String = when (b) {
        is Block.Rich -> b.html
        is Block.Quote -> "<blockquote>${b.html.ifBlank { "<p></p>" }}</blockquote>"
        is Block.Callout -> "<div class=\"callout\" data-kind=\"${b.kind}\">${b.html.ifBlank { "<p></p>" }}</div>"
        is Block.Code -> "<pre><code" + (if (b.language.isNotBlank()) " class=\"language-${NoteCodec.escape(b.language)}\"" else "") + ">" + NoteCodec.escape(b.text) + "</code></pre>"
        is Block.Divider -> "<hr>"
        is Block.Table -> "<table>" + b.rows.mapIndexed { i, r ->
            val t = if (i == 0 && b.header) "th" else "td"
            "<tr>" + r.joinToString("") { "<$t>${NoteCodec.escape(it)}</$t>" } + "</tr>"
        }.joinToString("") + "</table>"
    }

    // ------------------------------------------------------------------ headings

    data class Heading(val level: Int, val text: String)

    private val emLevels = listOf(2.0 to 1, 1.5 to 2, 1.17 to 3, 1.12 to 4, 0.83 to 5, 0.75 to 6)
    private val pxLevels = listOf(26.0 to 1, 21.0 to 2, 18.0 to 3, 16.5 to 4, 15.5 to 5, 14.5 to 6)

    /** Heading level of a paragraph's inner HTML written as a sized span (how the phone writes headings), or 0. */
    fun spanHeadingLevel(inner: String): Int {
        val m = Regex("^\\s*<span\\s+style=\"[^\"]*font-size:\\s*([0-9.]+)(px|em)", RegexOption.IGNORE_CASE).find(inner) ?: return 0
        val v = m.groupValues[1].toDoubleOrNull() ?: return 0
        val table = if (m.groupValues[2].equals("em", true)) emLevels else pxLevels
        if (m.groupValues[2].equals("px", true) && v < 14.0) return 0
        if (m.groupValues[2].equals("em", true) && v < 0.7) return 0
        return table.minByOrNull { kotlin.math.abs(it.first - v) }!!.second
    }

    /** Headings in reading order: <h1>–<h6>, and paragraphs that are a single sized heading span. */
    fun headings(html: String): List<Heading> {
        val out = mutableListOf<Heading>()
        split(html).forEach { b ->
            val h = when (b) { is Block.Rich -> b.html; is Block.Quote -> b.html; is Block.Callout -> b.html; else -> return@forEach }
            Regex("<(h[1-6]|p)(\\s[^>]*)?>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE).findAll(h).forEach { m ->
                val tag = m.groupValues[1].lowercase()
                val level = if (tag.startsWith("h")) tag[1].digitToInt() else spanHeadingLevel(m.groupValues[3])
                val text = NoteCodec.blockText(m.groupValues[3])
                if (level > 0 && text.isNotBlank()) out += Heading(level, text)
            }
        }
        return out
    }

    // ------------------------------------------------------------------ tags, links, todos, stats

    /** "work/meetings/weekly" → ["work", "work/meetings", "work/meetings/weekly"]. */
    fun tagPath(tag: String): List<String> {
        val parts = tag.split('/').filter { it.isNotBlank() }
        return parts.indices.map { parts.subList(0, it + 1).joinToString("/") }
    }

    /** Whether [tag] is [filter] or sits inside it ("work/meetings" is inside "work"). */
    fun tagMatches(tag: String, filter: String): Boolean = tag == filter || tag.startsWith("$filter/")

    private val wiki = Regex("\\[\\[([^\\[\\]\\n|]{1,120})(?:\\|[^\\]\\n]{0,120})?]]")

    /** Note titles linked with [[Title]] in the text, in order, unique (case kept). */
    fun wikiLinks(text: String): List<String> = wiki.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }.toList()

    /** Whether [text] links to a note called [title]. */
    fun linksTo(text: String, title: String): Boolean = title.isNotBlank() && wikiLinks(text).any { it.equals(title.trim(), ignoreCase = true) }

    /** Whether a note has a checklist line that isn't ticked yet. */
    fun hasOpenTodo(body: String): Boolean {
        val d = NoteCodec.decode(body)
        if (d.checklist.any { !it.done && it.text.isNotBlank() }) return true
        return NoteCodec.plainText(d.html).lines().any { it.trimStart().startsWith("\u2610") }
    }

    data class Stats(val words: Int, val characters: Int, val charactersNoSpaces: Int, val paragraphs: Int, val readMinutes: Int)

    fun stats(title: String, body: String): Stats {
        val text = NoteCodec.plainText(body)
        val words = Regex("[\\p{L}\\p{N}][\\p{L}\\p{N}'’_-]*").findAll(text).count()
        return Stats(
            words = words,
            characters = text.length,
            charactersNoSpaces = text.count { !it.isWhitespace() },
            paragraphs = text.lines().count { it.isNotBlank() },
            readMinutes = if (words == 0) 0 else maxOf(1, (words + 199) / 200),
        )
    }

    // ------------------------------------------------------------------ Markdown

    /** The whole note as Markdown: title as "# Title", then the text, blocks and checklist. */
    fun toMarkdown(title: String, body: String): String {
        val d = NoteCodec.decode(body)
        val sb = StringBuilder()
        if (title.isNotBlank()) sb.append("# ").append(title.trim()).append("\n\n")
        split(d.html).forEach { b ->
            if (b !is Block.Rich && sb.isNotEmpty() && !sb.endsWith("\n\n")) sb.append('\n')
            when (b) {
                is Block.Rich -> sb.append(richToMarkdown(b.html))
                is Block.Quote -> sb.append(richToMarkdown(b.html).trimEnd().lines().joinToString("\n") { if (it.isBlank()) ">" else "> $it" }).append("\n\n")
                is Block.Callout -> {
                    sb.append("> [!").append(b.kind.uppercase()).append("]\n")
                    sb.append(richToMarkdown(b.html).trimEnd().lines().joinToString("\n") { if (it.isBlank()) ">" else "> $it" }).append("\n\n")
                }
                is Block.Code -> sb.append("```").append(b.language).append('\n').append(b.text.trimEnd('\n')).append("\n```\n\n")
                is Block.Divider -> sb.append("---\n\n")
                is Block.Table -> {
                    val rows = b.rows
                    fun line(r: List<String>) = "| " + r.joinToString(" | ") { it.replace("|", "\\|").replace("\n", " ") } + " |"
                    val head = if (b.header) rows.first() else List(rows.first().size) { "" }
                    sb.append(line(head)).append('\n')
                    sb.append("|" + head.joinToString("|") { " --- " } + "|").append('\n')
                    (if (b.header) rows.drop(1) else rows).forEach { sb.append(line(it)).append('\n') }
                    sb.append('\n')
                }
            }
        }
        d.checklist.forEach { sb.append(if (it.done) "- [x] " else "- [ ] ").append(it.text).append('\n') }
        return sb.toString().replace(Regex("\n{3,}"), "\n\n").trim() + "\n"
    }

    private fun richToMarkdown(html: String): String {
        if (!NoteCodec.isHtml(html)) return html.trim() + "\n\n"
        val sb = StringBuilder()
        var ordered = 0
        Regex("<(p|li|h[1-6])(\\s[^>]*)?>([\\s\\S]*?)</\\1>|<(ol|ul)(\\s[^>]*)?>|</(ol|ul)>|<br\\s*/?>", RegexOption.IGNORE_CASE).findAll(html).forEach { m ->
            when {
                m.groupValues[4].isNotEmpty() -> ordered = if (m.groupValues[4].equals("ol", true)) 1 else 0
                m.groupValues[6].isNotEmpty() -> { ordered = 0; sb.append('\n') }
                m.groupValues[1].isEmpty() -> sb.append('\n')
                else -> {
                    val tag = m.groupValues[1].lowercase()
                    val inner = m.groupValues[3]
                    var text = inlineToMarkdown(inner).trim()
                    val box = NoteCodec.checkPrefix(NoteCodec.blockText(inner))
                    if (box > 0) {
                        val done = NoteCodec.isDoneLine(NoteCodec.blockText(inner))
                        text = (if (done) "- [x] " else "- [ ] ") + text.replace(Regex("^[\\u2610\\u2611]\\uFE0E?\\s?"), "")
                        sb.append(text).append('\n'); return@forEach
                    }
                    when {
                        tag == "li" -> { sb.append(if (ordered > 0) "${ordered++}. " else "- ").append(text).append('\n') }
                        tag.startsWith("h") -> sb.append("#".repeat(tag[1].digitToInt())).append(' ').append(text).append("\n\n")
                        else -> {
                            val level = spanHeadingLevel(inner)
                            if (level > 0) sb.append("#".repeat(level)).append(' ').append(text.replace(Regex("^\\*\\*(.*)\\*\\*$"), "$1")).append("\n\n")
                            else sb.append(text).append("\n\n")
                        }
                    }
                }
            }
        }
        return sb.toString()
    }

    private fun inlineToMarkdown(inner: String): String {
        var s = inner
        s = s.replace(Regex("<a\\s[^>]*href=\"([^\"]*)\"[^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE)) { "[" + it.groupValues[2] + "](" + it.groupValues[1] + ")" }
        s = s.replace(Regex("<(b|strong)(\\s[^>]*)?>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)) { "**" + it.groupValues[3] + "**" }
        s = s.replace(Regex("<(i|em)(\\s[^>]*)?>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)) { "*" + it.groupValues[3] + "*" }
        s = s.replace(Regex("<(s|del|strike)(\\s[^>]*)?>([\\s\\S]*?)</\\1>", RegexOption.IGNORE_CASE)) { "~~" + it.groupValues[3] + "~~" }
        s = s.replace(Regex("<code(\\s[^>]*)?>([\\s\\S]*?)</code>", RegexOption.IGNORE_CASE)) { "`" + it.groupValues[2] + "`" }
        s = s.replace(Regex("<mark(\\s[^>]*)?>([\\s\\S]*?)</mark>", RegexOption.IGNORE_CASE)) { "==" + it.groupValues[2] + "==" }
        s = s.replace(Regex("<span\\s+style=\"[^\"]*background[^\"]*\"[^>]*>([\\s\\S]*?)</span>", RegexOption.IGNORE_CASE)) { "==" + it.groupValues[1] + "==" }
        s = s.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "  \n")
        s = s.replace(Regex("</?(span|u|font)(\\s[^>]*)?>", RegexOption.IGNORE_CASE), "")
        s = s.replace(Regex("<[^>]+>"), "")
        return NoteCodec.decodeEntities(s).replace("\u00A0", " ")
    }

    data class Imported(val title: String, val html: String)

    /** Markdown (or plain text) to a note. A leading "# Title" becomes the title. */
    fun fromMarkdown(md: String, fallbackTitle: String = ""): Imported {
        val lines = md.replace("\r\n", "\n").replace('\r', '\n').lines()
        var i = 0
        var title = fallbackTitle
        while (i < lines.size && lines[i].isBlank()) i++
        if (i < lines.size && Regex("^#\\s+\\S").containsMatchIn(lines[i])) { title = lines[i].removePrefix("#").trim(); i++ }
        val out = StringBuilder()
        var para = mutableListOf<String>()
        fun flushPara() {
            if (para.isNotEmpty()) { out.append("<p>").append(para.joinToString("<br>") { inlineFromMarkdown(it) }).append("</p>"); para = mutableListOf() }
        }
        var list: String? = null
        fun closeList() { if (list != null) { out.append("</$list>"); list = null } }
        while (i < lines.size) {
            val line = lines[i]
            val t = line.trim()
            when {
                t.startsWith("```") -> {
                    flushPara(); closeList()
                    val lang = t.removePrefix("```").trim()
                    val code = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) { code += lines[i]; i++ }
                    out.append(blockHtml(Block.Code(code.joinToString("\n"), lang)))
                }
                t.isEmpty() -> { flushPara(); closeList() }
                Regex("^(-{3,}|\\*{3,}|_{3,})$").matches(t) -> { flushPara(); closeList(); out.append("<hr>") }
                Regex("^#{1,6}\\s").containsMatchIn(t) -> {
                    flushPara(); closeList()
                    val level = t.takeWhile { it == '#' }.length
                    out.append("<h$level>").append(inlineFromMarkdown(t.substring(level).trim())).append("</h$level>")
                }
                t.startsWith(">") -> {
                    flushPara(); closeList()
                    val quoted = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith(">")) { quoted += lines[i].trim().removePrefix(">").removePrefix(" "); i++ }
                    i--
                    val kind = Regex("^\\[!(\\w+)]\\s*$").find(quoted.firstOrNull().orEmpty())?.groupValues?.get(1)?.lowercase()
                    if (kind != null) {
                        val inner = fromMarkdown(quoted.drop(1).joinToString("\n")).html
                        out.append(blockHtml(Block.Callout(kind.takeIf { it in CALLOUT_KINDS } ?: "note", inner)))
                    } else out.append(blockHtml(Block.Quote(fromMarkdown(quoted.joinToString("\n")).html)))
                }
                t.startsWith("|") && i + 1 < lines.size && Regex("^\\|?\\s*:?-{3,}").containsMatchIn(lines[i + 1].trim()) -> {
                    flushPara(); closeList()
                    fun cells(s: String) = s.trim().removePrefix("|").removeSuffix("|").split(Regex("(?<!\\\\)\\|")).map { it.trim().replace("\\|", "|") }
                    val rows = mutableListOf(cells(t))
                    i += 2
                    while (i < lines.size && lines[i].trim().startsWith("|")) { rows += cells(lines[i]); i++ }
                    i--
                    val w = rows.maxOf { it.size }
                    out.append(blockHtml(Block.Table(rows.map { r -> r + List(w - r.size) { "" } }, header = rows.first().any { it.isNotBlank() })))
                }
                Regex("^[-*+]\\s+\\[([ xX])]\\s+").containsMatchIn(t) -> {
                    flushPara(); closeList()
                    val m = Regex("^[-*+]\\s+\\[([ xX])]\\s+(.*)$").find(t)!!
                    val done = m.groupValues[1].isNotBlank()
                    out.append("<p>").append(if (done) NoteCodec.CHECK_DONE else NoteCodec.CHECK_OPEN).append(inlineFromMarkdown(m.groupValues[2])).append("</p>")
                }
                Regex("^[-*+]\\s+").containsMatchIn(t) || Regex("^\\d+[.)]\\s+").containsMatchIn(t) -> {
                    flushPara()
                    val kind = if (t[0].isDigit()) "ol" else "ul"
                    if (list != kind) { closeList(); out.append("<$kind>"); list = kind }
                    out.append("<li>").append(inlineFromMarkdown(t.replace(Regex("^([-*+]|\\d+[.)])\\s+"), ""))).append("</li>")
                }
                else -> { closeList(); para += t }
            }
            i++
        }
        flushPara(); closeList()
        return Imported(title, out.toString())
    }

    private fun inlineFromMarkdown(s0: String): String {
        val codes = mutableListOf<String>()
        var s = Regex("`([^`]+)`").replace(s0) { codes += it.groupValues[1]; "\u0000${codes.size - 1}\u0000" }
        s = NoteCodec.escape(s)
        s = s.replace(Regex("\\[([^\\]]+)]\\(([^)\\s]+)\\)")) { "<a href=\"${it.groupValues[2]}\">${it.groupValues[1]}</a>" }
        s = s.replace(Regex("\\*\\*([^*]+)\\*\\*|__([^_]+)__")) { "<b>" + it.groupValues[1].ifEmpty { it.groupValues[2] } + "</b>" }
        s = s.replace(Regex("(?<![*\\w])\\*([^*\\n]+)\\*(?!\\*)|(?<![_\\w])_([^_\\n]+)_(?![_\\w])")) { "<i>" + it.groupValues[1].ifEmpty { it.groupValues[2] } + "</i>" }
        s = s.replace(Regex("~~([^~]+)~~")) { "<s>${it.groupValues[1]}</s>" }
        s = s.replace(Regex("==([^=]+)==")) { "<mark>${it.groupValues[1]}</mark>" }
        s = s.replace(Regex("\u0000(\\d+)\u0000")) { "<code>" + NoteCodec.escape(codes[it.groupValues[1].toInt()]) + "</code>" }
        return s
    }

    /** The note as a standalone HTML page (for export and printing). */
    fun toHtmlPage(title: String, body: String): String {
        val d = NoteCodec.decode(body)
        val checks = d.checklist.joinToString("") { "<p>" + (if (it.done) "☑ " else "☐ ") + NoteCodec.escape(it.text) + "</p>" }
        return """<!DOCTYPE html><html><head><meta charset="utf-8"><title>${NoteCodec.escape(title)}</title><style>
body{font:16px/1.55 -apple-system,Roboto,"Segoe UI",sans-serif;max-width:720px;margin:40px auto;padding:0 24px;color:#1d1d1f}
h1{font-size:30px}blockquote{border-left:3px solid #c7c7cc;margin:12px 0;padding:2px 14px;color:#444}
.callout{background:#eef3fd;border-radius:10px;padding:10px 14px;margin:12px 0}.callout[data-kind=tip]{background:#e8f6ec}
.callout[data-kind=warning],.callout[data-kind=caution]{background:#fdf0e7}.callout[data-kind=important]{background:#f3ecfd}
pre{background:#f5f5f7;border-radius:8px;padding:12px;overflow:auto}code{font-family:ui-monospace,Menlo,monospace;font-size:14px}
table{border-collapse:collapse;margin:12px 0}td,th{border:1px solid #d2d2d7;padding:6px 10px;text-align:left}th{background:#f5f5f7}
hr{border:0;border-top:1px solid #d2d2d7;margin:20px 0}mark{background:#fff59d}
</style></head><body>""" + (if (title.isNotBlank()) "<h1>${NoteCodec.escape(title)}</h1>" else "") + d.html + checks + "</body></html>"
    }
}
