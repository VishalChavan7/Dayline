package com.dayline.app.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteCodecTest {
    @Test fun roundTripsHtmlAndChecklist() {
        val items = listOf(CheckItem("Milk", false), CheckItem("Eggs", true))
        val body = NoteCodec.encode("<p><b>Shopping</b></p>", items)
        val decoded = NoteCodec.decode(body)
        assertEquals("<p><b>Shopping</b></p>", decoded.html)
        assertEquals(items, decoded.checklist)
    }

    @Test fun plainNotesLoadUnchanged() {
        val decoded = NoteCodec.decode("Call Sam\nabout venue")
        assertEquals("Call Sam\nabout venue", decoded.html)
        assertTrue(decoded.checklist.isEmpty())
        assertFalse(NoteCodec.isHtml(decoded.html))
    }

    @Test fun plainTextStripsTagsAndShowsChecklist() {
        val body = NoteCodec.encode("<p>Hello &amp; <i>welcome</i></p><p>Line 2</p>", listOf(CheckItem("Task", true)))
        assertEquals("Hello & welcome\nLine 2\n☑ Task", NoteCodec.plainText(body))
    }

    @Test fun blankChecklistItemsAreDropped() {
        assertEquals("<p>x</p>", NoteCodec.encode("<p>x</p>", listOf(CheckItem("  "))))
        assertTrue(NoteBody("<p></p>", listOf(CheckItem(""))).isEmpty)
    }

    @Test fun hashtagsBecomeLabels() {
        assertEquals(listOf("work", "ideas"), NoteCodec.labels("Plan #Work stuff #ideas and #work again, not #42 or a&#39;b"))
        val body = NoteCodec.encode("<p style=\"color: #1F1F1F\">Buy #groceries</p>", listOf(CheckItem("milk #home")))
        assertEquals(listOf("trip", "groceries", "home"), NoteCodec.labelsOf("Weekend #trip", body))
    }

    @Test fun mediaRoundTrips() {
        val media = listOf(NoteMedia(NoteMedia.Type.IMAGE, "a.jpg"), NoteMedia(NoteMedia.Type.AUDIO, "b.m4a", 4200))
        val body = NoteCodec.encode("<p>x</p>", listOf(CheckItem("milk")), media)
        val d = NoteCodec.decode(body)
        assertEquals("<p>x</p>", d.html)
        assertEquals(listOf(CheckItem("milk")), d.checklist)
        assertEquals(media, d.media)
        assertTrue(!NoteBody("", media = media).isEmpty)
    }

    @Test fun checkPrefixes() {
        assertEquals(2, NoteCodec.checkPrefix("\u2610 Two"))
        assertEquals(3, NoteCodec.checkPrefix("${NoteCodec.CHECK_DONE}Two"))
        assertEquals(0, NoteCodec.checkPrefix("Two"))
    }

    @Test fun checklistRunsSplitInPlace() {
        val html = "<p>Intro</p><p>\u2610 Milk</p><p>${NoteCodec.CHECK_DONE}Eggs &amp; ham</p><p>Outro</p>"
        val parts = NoteCodec.splitChecklists(html)
        assertEquals(3, parts.size)
        assertEquals(NoteCodec.BodyPart.Html("<p>Intro</p>"), parts[0])
        assertEquals(NoteCodec.BodyPart.Checks(listOf(CheckItem("Milk"), CheckItem("Eggs & ham", true))), parts[1])
        assertEquals(NoteCodec.BodyPart.Html("<p>Outro</p>"), parts[2])
        assertEquals("<p>\u2610 Milk</p><p>${NoteCodec.CHECK_DONE}Eggs &amp; ham</p>", NoteCodec.checksHtml((parts[1] as NoteCodec.BodyPart.Checks).items))
    }

    @Test fun voiceNoteNamesRoundTrip() {
        val media = listOf(NoteMedia(NoteMedia.Type.AUDIO, "v.m4a", 3000, "Standup | ideas"))
        val d = NoteCodec.decode(NoteCodec.encode("<p>x</p>", emptyList(), media))
        assertEquals("Standup / ideas", d.media.single().name)
    }

    @Test fun entitiesAreDecodedForSearchAndPreviews() {
        val body = "<p>Ideas &sol; plans &amp; more &#47; &#x2F; &lpar;draft&rpar; &hellip;</p>"
        assertEquals("Ideas / plans & more / / (draft) …", NoteCodec.plainText(body))
        assertEquals("a & b", NoteCodec.decodeEntities("a &amp; b"))
        assertEquals("&unknown;", NoteCodec.decodeEntities("&unknown;"))
    }
}
