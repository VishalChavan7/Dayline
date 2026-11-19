package com.dayline.app.notes

import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dayline.app.core.domain.NoteFormat
import com.dayline.app.core.model.Note
import com.dayline.app.ui.notes.NoteBodyModel
import com.dayline.app.ui.notes.NoteExport
import com.dayline.app.ui.notes.cleanUrl
import com.dayline.app.ui.notes.filterNotes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** The phone's note body with quotes, callouts, code, tables and dividers: load, edit, save, and back. */
@RunWith(AndroidJUnit4::class)
class NoteBlocksModelTest {
    private val mixed = "<p>Intro <b>bold</b></p><blockquote><p>Quoted</p></blockquote><div class=\"callout\" data-kind=\"warning\"><p>Careful</p></div>" +
        "<pre><code class=\"language-kotlin\">val a = 1 &lt; 2\n  indented</code></pre><table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2 &amp; 3</td></tr></table><hr><p>Outro</p>"

    private fun kinds(b: NoteBodyModel) = b.segments.map { it::class.simpleName }

    @Test fun loadsEveryBlockAndSavesThemBack() {
        val b = NoteBodyModel().apply { load(mixed) }
        assertEquals(listOf("TextSeg", "QuoteSeg", "QuoteSeg", "CodeSeg", "TableSeg", "DividerSeg", "TextSeg"), kinds(b))
        val out = b.toHtml()
        assertEquals(NoteFormat.split(mixed).map { it::class }, NoteFormat.split(out).map { it::class })
        val code = NoteFormat.split(out).filterIsInstance<NoteFormat.Block.Code>().single()
        assertEquals("val a = 1 < 2\n  indented", code.text)
        assertEquals("kotlin", code.language)
        assertEquals(listOf(listOf("A", "B"), listOf("1", "2 & 3")), NoteFormat.split(out).filterIsInstance<NoteFormat.Block.Table>().single().rows)
        assertEquals("warning", NoteFormat.split(out).filterIsInstance<NoteFormat.Block.Callout>().single().kind)
        // Stable: loading what was saved gives the same HTML.
        assertEquals(out, NoteBodyModel().apply { load(out) }.toHtml())
    }

    @Test fun selectedLinesBecomeAQuoteAndBack() {
        val b = NoteBodyModel().apply { load("<p>one</p><p>two</p><p>three</p>") }
        val t = b.firstText
        val r = t.rich.paragraphRangesForTest()
        t.rich.selection = TextRange(r[1].first + 1)
        b.toggleQuote(t, "quote")
        assertEquals(listOf("TextSeg", "QuoteSeg", "TextSeg"), kinds(b))
        assertTrue(b.toHtml().contains("<blockquote>"))
        val q = b.segments[1] as NoteBodyModel.QuoteSeg
        b.toggleQuote(q, "callout:tip")
        assertTrue(b.toHtml().contains("data-kind=\"tip\""))
        b.toggleQuote(q, "callout:tip")
        assertEquals(listOf("TextSeg"), kinds(b))
        assertEquals(listOf("one", "two", "three"), com.dayline.app.core.domain.NoteCodec.plainText(b.toHtml()).lines())
    }

    @Test fun codeBlockFromLinesAndBack() {
        val b = NoteBodyModel().apply { load("<p>x = 1</p>") }
        b.makeCode(b.firstText)
        val code = b.segments.filterIsInstance<NoteBodyModel.CodeSeg>().single()
        assertEquals("x = 1", code.text)
        code.text = "x = 1\n  y = 2"
        assertTrue(b.toHtml().contains("<pre><code>x = 1\n  y = 2</code></pre>"))
        b.unwrap(code)
        // Back as text lines (the text editor doesn't keep leading spaces).
        assertEquals(listOf("x = 1", "y = 2"), com.dayline.app.core.domain.NoteCodec.plainText(b.toHtml()).lines().map { it.trim() })
    }

    @Test fun tablesInsertEditAndResize() {
        val b = NoteBodyModel().apply { load("<p>before</p><p>after</p>") }
        val t = b.firstText
        t.rich.selection = TextRange(2)
        val table = b.insertTable(t, rows = 2, columns = 2)
        assertEquals(listOf("TextSeg", "TableSeg", "TextSeg"), kinds(b))
        b.setCell(table, 0, 0, "H"); b.setCell(table, 1, 1, "x\ny")
        b.addRow(table, 0); b.addColumn(table, 1)
        assertEquals(listOf(listOf("H", "", ""), listOf("", "", ""), listOf("", "x y", "")), table.rows.toList())
        b.deleteColumn(table, 2); b.deleteRow(table, 1)
        assertEquals(listOf(listOf("H", ""), listOf("", "x y")), table.rows.toList())
        table.header = false
        assertTrue(b.toHtml().contains("<table><tr><td>H</td><td></td></tr><tr><td></td><td>x y</td></tr></table>"))
        b.remove(table)
        assertEquals(listOf("TextSeg"), kinds(b))
        assertEquals("before\nafter", com.dayline.app.core.domain.NoteCodec.plainText(b.toHtml()))
    }

    @Test fun dividerAndMoveLine() {
        val b = NoteBodyModel().apply { load("<p>a</p><p>b</p><p>c</p>") }
        val t = b.firstText
        t.rich.selection = TextRange(t.rich.annotatedString.text.length)
        b.moveLine(t, up = true)
        assertEquals("a\nc\nb", com.dayline.app.core.domain.NoteCodec.plainText(b.toHtml()))
        b.insertDivider(b.firstText)
        assertTrue(b.toHtml().contains("<hr>"))
    }

    @Test fun findAndReplace() {
        val b = NoteBodyModel().apply { load("<p>cat and Cat</p><blockquote><p>a cat</p></blockquote>") }
        assertEquals(3, b.find("cat").size)
        assertEquals(3, b.replaceAll("cat", "dog"))
        assertEquals(0, b.find("cat").size)
        assertEquals(3, b.find("dog").size)
        assertEquals(3, b.replaceWord("dog", "fox"))
        assertEquals(3, b.find("fox").size)
    }

    @Test fun listsByView() {
        val zone = ZoneOffset.UTC
        val today = LocalDate.of(2026, 11, 19)
        val now = today.atStartOfDay(zone).toInstant().plusSeconds(3600)
        val old = Instant.parse("2026-01-01T10:00:00Z")
        val notes = listOf(
            Note(id = 1, title = "Live", category = "work/meetings", createdAt = old, updatedAt = now),
            Note(id = 2, title = "Todo", body = "<p>☐ milk</p>", createdAt = old, updatedAt = old),
            Note(id = 3, title = "Archived", archivedAt = now, createdAt = old, updatedAt = old),
            Note(id = 4, title = "Binned", trashedAt = now, createdAt = old, updatedAt = old),
            Note(id = 5, title = "Secret", body = "<p>hidden words</p>", locked = true, isPinned = true, createdAt = old, updatedAt = old),
            Note(id = 6, title = "Diary", category = "journal", createdAt = old, updatedAt = now),
        )
        fun ids(view: String, q: String = "") = filterNotes(notes, view, q, "edited", true, today, zone).map { it.id }
        assertEquals(listOf(5L, 1L, 2L), ids("all"))
        assertEquals(listOf(1L), ids("today"))
        assertEquals(listOf(5L), ids("locked"))
        assertEquals(listOf(5L), ids("pinned"))
        assertEquals(listOf(3L), ids("archive"))
        assertEquals(listOf(4L), ids("trash"))
        assertEquals(listOf(1L), ids("tag:work"))
        assertEquals(listOf(1L), ids("tag:work/meetings"))
        assertEquals(emptyList<Long>(), ids("tag:work/other"))
        assertEquals("A locked note's text isn't searched", emptyList<Long>(), ids("all", "hidden"))
        assertEquals(listOf(5L), ids("all", "secret"))
        assertEquals(listOf(1L, 5L, 2L), filterNotes(notes, "all", "", "title", false, today, zone).map { it.id })
    }

    @Test fun importFiles() {
        val md = NoteExport.noteFrom("Plan.md", "# My plan\n\n- [ ] ship\n\n> quote")!!
        assertEquals("My plan", md.title)
        assertTrue(md.body.contains("<blockquote>"))
        val txt = NoteExport.noteFrom("list.txt", "a\n\nb <x>")!!
        assertEquals("list", txt.title)
        assertEquals("<p>a</p><br><p>b &lt;x&gt;</p>", txt.body)
        val html = NoteExport.noteFrom("page.html", "<html><body onload=\"x()\"><script>alert(1)</script><p onclick=\"y()\">Hi</p></body></html>")!!
        assertEquals("<p>Hi</p>", html.body)
        assertNull(NoteExport.noteFrom(".txt", ""))
    }

    @Test fun webLinks() {
        assertEquals("https://dayline.app", cleanUrl("dayline.app"))
        assertEquals("http://x.com/a?b=1", cleanUrl(" http://x.com/a?b=1 "))
        assertEquals("mailto:me@x.com", cleanUrl("mailto:me@x.com"))
        assertNull(cleanUrl("javascript:alert(1)"))
        assertNull(cleanUrl("JavaScript:alert(1)"))
        assertEquals("https://localhost:8080/x", cleanUrl("localhost:8080/x"))
        assertNull(cleanUrl("file:///etc/passwd"))
        assertNull(cleanUrl("two words"))
        assertNull(cleanUrl("https://"))
    }

    @Test fun hostileHtmlNeverCrashes() {
        listOf("<", "<blockquote>", "<pre><code>", "<table><tr>", "<div class=\"callout\">", "<hr", "&#99999999;",
            "<blockquote>".repeat(50) + "x", "<ul><li><table><tr><td>x</td></tr></table></li></ul>").forEach { h ->
            val b = NoteBodyModel().apply { load(h) }
            NoteBodyModel().apply { load(b.toHtml()) }.toHtml()
        }
    }
}

private fun com.mohamedrejeb.richeditor.model.RichTextState.paragraphRangesForTest(): List<IntRange> {
    val t = annotatedString
    return t.paragraphStyles.map { it.start until it.end }.distinctBy { it.first }.sortedBy { it.first }
}
