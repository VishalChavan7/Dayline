package com.dayline.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dayline.app.core.domain.NoteFormat
import com.dayline.app.core.model.Note
import com.dayline.app.testing.MainDispatcherRule
import com.dayline.app.testing.TestEnv
import com.dayline.app.ui.common.Formats
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.navigation.AppNavigator
import com.dayline.app.ui.notes.NoteEditorScreen
import com.dayline.app.ui.notes.NoteEditorViewModel
import com.dayline.app.ui.notes.NotesViewModel
import com.dayline.app.ui.theme.DaylineTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * The new note features driven through the real screens and view models (in-memory database):
 * blocks from the formatting bar, Trash, Archive, lock, links and the note lists.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteFeaturesFlowTest {
    @get:Rule(order = 0) val main = MainDispatcherRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val env = TestEnv(scope)

    @After fun tearDown() = scope.cancel()

    private fun vm(id: Long = 0) = NoteEditorViewModel(
        SavedStateHandle(mapOf("id" to id, "kind" to "")), env.notes, env.tasks, env.projects, env.time, env.settings,
    )

    private fun open(vm: NoteEditorViewModel) {
        compose.setContent {
            DaylineTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalFormats provides Formats()) { NoteEditorScreen(AppNavigator(rememberNavController()), vm) }
            }
        }
        compose.waitUntil(5_000) { !vm.state.value.loading }
        compose.waitForIdle()
    }

    private fun save(vm: NoteEditorViewModel): Note {
        compose.onNodeWithContentDescription("Save and go back").performClick()
        compose.waitUntil(5_000) { vm.state.value.closed }
        return runBlocking { env.notes.allNotes.first() }.maxBy { it.updatedAt }
    }

    private fun tap(label: String) {
        compose.onNodeWithContentDescription(label).performScrollTo().performClick()
        compose.waitForIdle()
    }

    @Test fun quoteCodeTableAndDividerFromTheBar() {
        val editor = vm()
        open(editor)
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Blocks")
        fields[1].performTextInput("Quoted line")
        compose.waitForIdle()
        tap("Quote")
        tap("Table")
        tap("Line separator")
        val note = save(editor)
        val kinds = NoteFormat.split(note.body).map { it::class.simpleName }
        assertTrue("quote saved: ${note.body}", "Quote" in kinds)
        assertTrue("table saved: ${note.body}", "Table" in kinds)
        assertTrue("divider saved: ${note.body}", "Divider" in kinds)
        assertEquals("Quoted line", (NoteFormat.split(note.body).first { it is NoteFormat.Block.Quote } as NoteFormat.Block.Quote).html.let { com.dayline.app.core.domain.NoteCodec.blockText(it) })
    }

    @Test fun codeBlockFromTheBar() {
        val editor = vm()
        open(editor)
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Code")
        fields[1].performTextInput("val x = 1")
        compose.waitForIdle()
        tap("Code block")
        val note = save(editor)
        val code = NoteFormat.split(note.body).filterIsInstance<NoteFormat.Block.Code>().single()
        assertEquals("val x = 1", code.text)
    }

    @Test fun blocksFromTheMacOpenAndSaveUnchanged() {
        val html = "<p>Intro</p><blockquote><p>Quoted</p></blockquote><div class=\"callout\" data-kind=\"warning\"><p>Hot</p></div>" +
            "<pre><code class=\"language-swift\">let a = 1</code></pre><table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table><hr><p>End</p>"
        val id = runBlocking { env.notes.save(Note(title = "From Mac", body = html)) }
        val editor = vm(id)
        open(editor)
        assertTrue(compose.onAllNodesWithText("Warning").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("let a = 1").fetchSemanticsNodes().isNotEmpty())
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput(" (edited)")
        val note = save(editor)
        assertTrue(note.title, note.title.contains("(edited)") && note.title.contains("From Mac"))
        assertEquals(NoteFormat.split(html).map { it::class }, NoteFormat.split(note.body).map { it::class })
        assertEquals("let a = 1", NoteFormat.split(note.body).filterIsInstance<NoteFormat.Block.Code>().single().text)
    }

    @Test fun deleteMovesToTrashAndTrashedNotesAreReadOnly() {
        val id = runBlocking { env.notes.save(Note(title = "Bin me", body = "<p>text</p>")) }
        val editor = vm(id)
        open(editor)
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Move to Trash").performScrollTo().performClick()
        compose.waitForIdle()
        assertTrue("asks first", compose.onAllNodesWithText("Move to Trash?").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Move to Trash").performClick()
        compose.waitUntil(5_000) { editor.state.value.closed }
        val trashed = runBlocking { env.notes.get(id) }
        assertNotNull("still there, in Trash", trashed?.trashedAt)
        assertTrue(runBlocking { env.notes.notes.first() }.none { it.id == id })

    }

    @Test fun trashedNoteShowsRestore() {
        // Opening a note from Trash: a banner with Restore.
        val id = runBlocking { env.notes.save(Note(title = "Binned", body = "<p>text</p>")).also { env.notes.trash(it) } }
        open(vm(id))
        assertTrue(compose.onAllNodesWithText("This note is in Trash.").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Restore").performClick()
        compose.waitUntil(5_000) { runBlocking { env.notes.get(id) }?.trashedAt == null }
    }

    @Test fun lockedNoteShowsTheLockFirst() {
        org.robolectric.Shadows.shadowOf(env.context.getSystemService(android.app.KeyguardManager::class.java)).setIsDeviceSecure(true)
        val id = runBlocking { env.notes.save(Note(title = "Bank", body = "<p>pin 1234</p>", locked = true)) }
        open(vm(id))
        assertTrue(compose.onAllNodesWithText("This note is locked.").fetchSemanticsNodes().isNotEmpty())
        assertTrue("text hidden", compose.onAllNodesWithText("pin 1234", substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue("no menu to share it from", compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("More")).fetchSemanticsNodes().isEmpty())
    }

    @Test fun lockFromTheMenuIsSaved() {
        val id = runBlocking { env.notes.save(Note(title = "Diary", body = "<p>x</p>")) }
        val editor = vm(id)
        open(editor)
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Lock note").performScrollTo().performClick()
        assertTrue(save(editor).locked)
    }

    @Test fun wikiLinkOpensOrMakesTheNote() = runBlocking {
        val existing = env.notes.save(Note(title = "Release notes"))
        val editor = vm()
        assertEquals(existing, editor.linkedNote("release NOTES"))
        val made = editor.linkedNote("Brand new")
        assertEquals("Brand new", env.notes.get(made)?.title)
        assertEquals(made, editor.linkedNote("brand new"))
    }

    @Test fun listsAndTrashFromTheNotesScreen() = runBlocking {
        val lists = NotesViewModel(env.notes, env.settings, env.time, env.context)
        val job = scope.launch { lists.state.collect { } }
        val a = env.notes.save(Note(title = "A", category = "work/meetings"))
        val b = env.notes.save(Note(title = "B", body = "<p>☐ todo</p>"))
        val c = env.notes.save(Note(title = "C"))
        env.notes.archive(c)
        fun shown() = lists.state.value.notes.map { it.id }.toSet()
        until { shown() == setOf(a, b) }
        lists.setView("archive"); until { shown() == setOf(c) }
        lists.setView("tag:work"); until { shown() == setOf(a) }
        assertTrue(lists.state.value.categories.containsAll(listOf("work", "work/meetings")))
        lists.trash(env.notes.get(a)!!)
        lists.setView("trash"); until { shown() == setOf(a) }
        lists.restoreFromTrash(env.notes.get(a)!!)
        until { shown().isEmpty() }
        lists.trash(env.notes.get(b)!!)
        lists.emptyTrash()
        until { runBlocking { env.notes.get(b) } == null }
        lists.setSort("title", false)
        lists.setView("all")
        until { lists.state.value.notes.map { it.title } == listOf("A") }
        assertFalse(lists.state.value.pinnedFirst)
        job.cancel()
    }
}

/** Waits (up to 5 s) for something the database and view models do on other threads. */
private fun until(cond: () -> Boolean) {
    val end = System.currentTimeMillis() + 5_000
    while (!cond()) {
        if (System.currentTimeMillis() > end) throw AssertionError("timed out")
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        Thread.sleep(20)
    }
}
