package com.dayline.app.core.domain

import com.dayline.app.core.domain.NoteFormat.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrored by the NoteFormat section of macos/Sources/DaylineChecks/main.swift — keep the vectors in step. */
class NoteFormatTest {

    private val mixed = "<p>Intro <b>bold</b></p>" +
        "<blockquote><p>Quoted</p></blockquote>" +
        "<div class=\"callout\" data-kind=\"warning\"><p>Careful</p></div>" +
        "<pre><code class=\"language-kotlin\">val a = 1 &lt; 2\nprintln(a)</code></pre>" +
        "<table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2 &amp; 3</td></tr></table>" +
        "<hr>" +
        "<p>Outro</p>"

    @Test fun splitFindsEveryBlockInOrder() {
        val b = NoteFormat.split(mixed)
        assertEquals(
            listOf(
                Block.Rich("<p>Intro <b>bold</b></p>"),
                Block.Quote("<p>Quoted</p>"),
                Block.Callout("warning", "<p>Careful</p>"),
                Block.Code("val a = 1 < 2\nprintln(a)", "kotlin"),
                Block.Table(listOf(listOf("A", "B"), listOf("1", "2 & 3")), header = true),
                Block.Divider,
                Block.Rich("<p>Outro</p>"),
            ),
            b,
        )
    }

    @Test fun joinThenSplitIsStable() {
        val once = NoteFormat.join(NoteFormat.split(mixed))
        assertEquals(once, NoteFormat.join(NoteFormat.split(once)))
        assertEquals(NoteFormat.split(mixed), NoteFormat.split(once))
    }

    @Test fun nestedQuotesAndPlainDivsStayWhole() {
        val html = "<blockquote><p>a</p><blockquote><p>b</p></blockquote></blockquote><div><p>plain div</p></div>"
        val b = NoteFormat.split(html)
        assertEquals(2, b.size)
        assertEquals(Block.Quote("<p>a</p><blockquote><p>b</p></blockquote>"), b[0])
        assertEquals(Block.Rich("<div><p>plain div</p></div>"), b[1])
    }

    @Test fun unclosedBlockIsLeftAsText() {
        val html = "<p>a</p><blockquote><p>never closed</p>"
        assertEquals(listOf(Block.Rich(html)), NoteFormat.split(html))
    }

    @Test fun unknownCalloutKindFallsBackToNote() {
        assertEquals(Block.Callout("note", "<p>x</p>"), NoteFormat.split("<div class=\"callout\" data-kind=\"weird\"><p>x</p></div>")[0])
    }

    @Test fun codeEscapesOnTheWayOut() {
        val h = NoteFormat.blockHtml(Block.Code("<b>&</b>", ""))
        assertEquals("<pre><code>&lt;b&gt;&amp;&lt;/b&gt;</code></pre>", h)
        assertEquals(Block.Code("<b>&</b>", ""), NoteFormat.split(h)[0])
    }

    @Test fun raggedTablesArePadded() {
        val t = NoteFormat.split("<table><tr><td>a</td></tr><tr><td>b</td><td>c</td></tr></table>")[0] as Block.Table
        assertEquals(listOf(listOf("a", ""), listOf("b", "c")), t.rows)
        assertFalse(t.header)
    }

    @Test fun emptyNoteIsOneEmptyRichBlock() {
        assertEquals(listOf(Block.Rich("")), NoteFormat.split(""))
    }

    @Test fun headingsFromTagsAndPhoneSpans() {
        val html = "<h1>One</h1><p><span style=\"font-size: 21.0px;\"><b>Two</b></span></p>" +
            "<p><span style=\"font-size: 18.0px; font-weight: 600;\">Three</span></p>" +
            "<p><span style=\"font-size: 1.12em;\"><b>Four</b></span></p><p>Body</p><h6>Six</h6>" +
            "<p><span style=\"font-size: 12.0px;\">small</span></p>"
        assertEquals(
            listOf(
                NoteFormat.Heading(1, "One"), NoteFormat.Heading(2, "Two"), NoteFormat.Heading(3, "Three"),
                NoteFormat.Heading(4, "Four"), NoteFormat.Heading(6, "Six"),
            ),
            NoteFormat.headings(html),
        )
    }

    @Test fun nestedTags() {
        assertEquals(listOf("work", "work/meetings", "work/meetings/q4"), NoteFormat.tagPath("work/meetings/q4"))
        assertTrue(NoteFormat.tagMatches("work/meetings", "work"))
        assertTrue(NoteFormat.tagMatches("work", "work"))
        assertFalse(NoteFormat.tagMatches("workshop", "work"))
        assertEquals(listOf("work/meetings", "home"), NoteCodec.labels("See #work/meetings and #home, not a#b or &#39;"))
    }

    @Test fun wikiLinks() {
        assertEquals(listOf("Plan", "Ideas 2026"), NoteFormat.wikiLinks("[[Plan]] and [[ Ideas 2026 |alias]] and [[plan]] and [[]] [[a\nb]]"))
        assertTrue(NoteFormat.linksTo("see [[My Note]]", "my note"))
        assertFalse(NoteFormat.linksTo("see [[My Note]]", ""))
    }

    @Test fun stats() {
        val s = NoteFormat.stats("T", "<p>Hello world, it's me.</p><p>Second line</p>")
        assertEquals(6, s.words)
        assertEquals(2, s.paragraphs)
        assertEquals(1, s.readMinutes)
        assertEquals(0, NoteFormat.stats("", "").words)
        assertEquals(0, NoteFormat.stats("", "").readMinutes)
    }

    @Test fun markdownExport() {
        val body = "<p><span style=\"font-size: 21.0px;\"><b>Plan</b></span></p>" +
            "<p>Some <b>bold</b>, <i>it</i>, <s>gone</s>, <code>x</code>, <mark>hi</mark> and <a href=\"https://a.b\">link</a></p>" +
            "<ul><li>one</li><li>two</li></ul><ol><li>first</li><li>second</li></ol>" +
            "<p>☐ todo</p><p>☑︎ done</p>" +
            "<blockquote><p>q</p></blockquote>" +
            "<div class=\"callout\" data-kind=\"tip\"><p>c</p></div>" +
            "<pre><code class=\"language-sh\">ls -la</code></pre>" +
            "<table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>x|y</td></tr></table><hr>"
        val md = NoteFormat.toMarkdown("Title", NoteCodec.encode(body, listOf(CheckItem("end", true))))
        assertEquals(
            """
            # Title

            ## Plan

            Some **bold**, *it*, ~~gone~~, `x`, ==hi== and [link](https://a.b)

            - one
            - two

            1. first
            2. second

            - [ ] todo
            - [x] done

            > q

            > [!TIP]
            > c

            ```sh
            ls -la
            ```

            | A | B |
            | --- | --- |
            | 1 | x\|y |

            ---

            - [x] end
            """.trimIndent() + "\n",
            md,
        )
    }

    @Test fun markdownImport() {
        val md = """
            # My Title

            Para one
            still one with **b** and `a<b`

            ## Sub
            - a
            - b
            1. x
            2) y
            - [ ] open
            - [x] shut
            > quote
            > more

            > [!WARNING]
            > hot

            ```js
            let a = "<x>";
            ```
            | H1 | H2 |
            |----|:--:|
            | a \| b | c |
            ***
        """.trimIndent()
        val r = NoteFormat.fromMarkdown(md, "fallback")
        assertEquals("My Title", r.title)
        assertEquals(
            "<p>Para one<br>still one with <b>b</b> and <code>a&lt;b</code></p>" +
                "<h2>Sub</h2><ul><li>a</li><li>b</li></ul><ol><li>x</li><li>y</li></ol>" +
                "<p>☐ open</p><p>☑︎ shut</p>" +
                "<blockquote><p>quote<br>more</p></blockquote>" +
                "<div class=\"callout\" data-kind=\"warning\"><p>hot</p></div>" +
                "<pre><code class=\"language-js\">let a = \"&lt;x&gt;\";</code></pre>" +
                "<table><tr><th>H1</th><th>H2</th></tr><tr><td>a | b</td><td>c</td></tr></table><hr>",
            r.html,
        )
        assertEquals("", NoteFormat.fromMarkdown("just text").title)
        assertEquals("f", NoteFormat.fromMarkdown("just text", "f").title)
    }

    @Test fun markdownRoundTripKeepsStructure() {
        val md = NoteFormat.toMarkdown("T", mixed)
        val back = NoteFormat.fromMarkdown(md)
        assertEquals("T", back.title)
        assertEquals(NoteFormat.split(mixed).map { it::class }, NoteFormat.split(back.html).map { it::class })
        assertEquals(md, NoteFormat.toMarkdown(back.title, back.html))
    }

    @Test fun plainTextOfBlocks() {
        assertEquals("Intro bold\nQuoted\nCareful\nval a = 1 < 2\nprintln(a)\nA  B\n1  2 & 3\n\n———\nOutro", NoteCodec.plainText(mixed))
    }

    @Test fun htmlPageEscapesTitle() {
        val p = NoteFormat.toHtmlPage("<x>", "<p>a</p>")
        assertTrue(p.startsWith("<!DOCTYPE html>"))
        assertTrue(p.contains("<h1>&lt;x&gt;</h1><p>a</p>"))
    }

    @Test fun noCrashOnHostileInput() {
        val junk = listOf(
            "<", "<blockquote", "</blockquote>", "<pre><code>", "<table><tr>", "<div class=\"callout\">",
            "[[", "]]", "#", "#/", "```", "|", "| a |\n| --- |", "> [!", "<hr", "&#99999999;", "\u0000",
            "<blockquote>".repeat(200) + "x" + "</blockquote>".repeat(200),
        )
        for (j in junk) {
            NoteFormat.join(NoteFormat.split(j)); NoteFormat.headings(j); NoteFormat.wikiLinks(j)
            NoteFormat.stats(j, j); NoteFormat.toMarkdown(j, j); NoteFormat.fromMarkdown(j); NoteFormat.toHtmlPage(j, j)
            NoteCodec.labels(j); NoteCodec.plainText(j)
        }
    }
}
