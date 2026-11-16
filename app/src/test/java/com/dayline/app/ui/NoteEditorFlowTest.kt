package com.dayline.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dayline.app.core.domain.NoteCodec
import com.dayline.app.core.model.Note
import com.dayline.app.testing.MainDispatcherRule
import com.dayline.app.testing.TestEnv
import com.dayline.app.ui.common.Formats
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.navigation.AppNavigator
import com.dayline.app.ui.notes.NoteBodyModel
import com.dayline.app.ui.notes.NoteEditorScreen
import com.dayline.app.ui.notes.NoteEditorViewModel
import com.dayline.app.ui.theme.DaylineTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real note editor (real view model, in-memory database) the way you use it on the
 * phone: open a new note of every kind, write, make a checklist mid-note, save, reopen.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NoteEditorFlowTest {
    @get:Rule(order = 0) val main = MainDispatcherRule()
    @get:Rule(order = 1) val compose = createComposeRule()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val env = TestEnv(scope)

    // The database is left open: the screen's flows may still be reading while the test winds down.
    @After fun tearDown() = scope.cancel()

    private fun vm(id: Long = 0, kind: String = "") = NoteEditorViewModel(
        SavedStateHandle(mapOf("id" to id, "kind" to kind)), env.notes, env.tasks, env.projects, env.time, env.settings,
    )

    private fun open(vm: NoteEditorViewModel) {
        compose.setContent {
            DaylineTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalFormats provides Formats()) {
                    NoteEditorScreen(AppNavigator(rememberNavController()), vm)
                }
            }
        }
        compose.waitUntil(5_000) { !vm.state.value.loading }
        compose.waitForIdle()
    }

    @Test fun newNoteOpensWithoutCrashing() {
        open(vm())
        compose.onAllNodesWithText("Title").fetchSemanticsNodes().let { assertTrue(it.isNotEmpty()) }
    }

    @Test fun newTextNoteOpens() = open(vm(kind = "text"))
    @Test fun newListNoteOpens() = open(vm(kind = "list"))
    @Test fun newDrawingNoteOpens() = open(vm(kind = "drawing"))
    @Test fun newAudioNoteOpens() = open(vm(kind = "audio"))
    @Test fun newImageNoteOpens() = open(vm(kind = "image"))

    @Test fun writeConvertToChecklistSaveAndReopen() {
        val editor = vm()
        open(editor)
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Groceries")
        fields[1].performTextInput("Milk")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Checklist").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Save and go back").performClick()
        compose.waitUntil(5_000) { editor.state.value.closed }

        val saved = runBlocking { env.notes.notes.first() }.single()
        assertEquals("Groceries", saved.title)
        val parts = NoteCodec.splitChecklists(saved.body)
        assertTrue("checklist stored in place: ${saved.body}", parts.any { it is NoteCodec.BodyPart.Checks && it.items.single().text == "Milk" })
    }

    @Test fun reopenNoteWithChecklistInTheMiddle() {
        val id = runBlocking {
            env.notes.save(Note(title = "Trip", body = "<p>Pack</p><p>☐ Passport</p><p>${NoteCodec.CHECK_DONE}Charger</p><p>Leave at 8</p>"))
        }
        open(vm(id))
        assertTrue(compose.onAllNodesWithText("Passport").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("Leave at 8", substring = true).fetchSemanticsNodes().isNotEmpty())
    }

    @Test fun bodyModelRoundTripsAndConverts() {
        compose.setContent { }
        compose.runOnIdle {
            val body = NoteBodyModel()
            assertEquals(1, body.segments.size) // usable before load()
            body.load("<p>One</p><p>Two</p><p>Three</p>")
            val seg = body.firstText
            seg.rich.selection = androidx.compose.ui.text.TextRange(5) // inside "Two"
            body.convertToChecklist(seg)
            val kinds = body.segments.map { it::class.simpleName }
            assertEquals(listOf("TextSeg", "CheckSeg", "TextSeg"), kinds)
            val html = body.toHtml()
            val parts = NoteCodec.splitChecklists(html)
            assertEquals(listOf("Two"), (parts[1] as NoteCodec.BodyPart.Checks).items.map { it.text })
            val check = body.segments[1] as NoteBodyModel.CheckSeg
            body.removeCheck(check, check.items.single().key)
            assertTrue(body.segments.none { it is NoteBodyModel.CheckSeg })
        }
    }

    @Test fun labelIsTheWholeLineAndIsMadeOnEnter() {
        val editor = vm()
        open(editor)
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Shopping")
        fields[1].performTextInput("Milk and eggs")
        fields[1].performTextInput("\n#grocery list")
        compose.waitForIdle()
        // Still typing: no label yet, the text stays.
        assertTrue(editor.state.value.note.category.isNullOrEmpty())
        fields[1].performTextInput("\n")
        compose.waitForIdle()
        assertEquals("grocery list", editor.state.value.note.category)
        compose.onNodeWithContentDescription("Save and go back").performClick()
        compose.waitUntil(5_000) { editor.state.value.closed }
        val saved = runBlocking { env.notes.notes.first() }.single()
        assertTrue("tag text removed: ${saved.body}", !saved.body.contains("#grocery"))
        assertTrue(saved.body.contains("Milk and eggs"))
    }

    @Test fun unfinishedLabelLineBecomesALabelOnSave() {
        val editor = vm()
        open(editor)
        val fields = compose.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Call mum #family")
        fields[1].performTextInput("Sunday afternoon #weekend plans")
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Save and go back").performClick()
        compose.waitUntil(5_000) { editor.state.value.closed }
        val saved = runBlocking { env.notes.notes.first() }.single()
        assertEquals("Call mum", saved.title)
        assertEquals(setOf("weekend plans", "family"), saved.category.orEmpty().split(',').toSet())
        assertTrue(saved.body.contains("Sunday afternoon") && !saved.body.contains("#weekend"))
    }

    @Test fun checklistInsideTextHasNoAddRowOrRemoveButtons() {
        val id = runBlocking {
            env.notes.save(Note(title = "Trip", body = "<p>Pack</p><p>\u2610 Passport</p><p>Leave at 8</p>"))
        }
        open(vm(id))
        assertTrue(compose.onAllNodesWithText("Passport").fetchSemanticsNodes().isNotEmpty())
        assertTrue(compose.onAllNodesWithText("List item").fetchSemanticsNodes().isEmpty())
        assertTrue(compose.onAllNodes(androidx.compose.ui.test.hasContentDescription("Remove item")).fetchSemanticsNodes().isEmpty())
    }
}
