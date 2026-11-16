package com.dayline.app.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dayline.app.core.model.Note
import com.dayline.app.core.model.Task
import com.dayline.app.core.model.TaskItem
import com.dayline.app.testing.MainDispatcherRule
import com.dayline.app.testing.TestEnv
import com.dayline.app.ui.common.Formats
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.components.TaskRow
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
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TaskRowAndLabelsTest {
    @get:Rule(order = 0) val main = MainDispatcherRule()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val env = TestEnv(scope)
    @After fun tearDown() = scope.cancel()

    private fun state(s: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, s)

    @Test fun blockedTaskNeverShowsAsCompletedWhenTapped() {
        var asked = 0
        val blocked = TaskItem(Task(id = 2, title = "Send invites"), blockedBy = listOf(Task(id = 1, title = "Book venue")))
        compose.setContent {
            DaylineTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalFormats provides Formats()) {
                    TaskRow(blocked, LocalDate.of(2026, 11, 12), onToggle = { asked++ }, onClick = {})
                }
            }
        }
        compose.onAllNodes(state("Not completed")).onFirst().performClick()
        compose.mainClock.advanceTimeBy(2_500)
        compose.waitForIdle()
        assertEquals(1, asked) // the app is still asked (so it can explain why)…
        assertTrue(compose.onAllNodes(state("Completed")).fetchSemanticsNodes().isEmpty()) // …but it never looks done
    }

    @Test fun renamingALabelUpdatesEveryNote() = runBlocking {
        env.notes.save(Note(title = "A", body = "x", category = "work,home"))
        env.notes.save(Note(title = "B", body = "y", category = "work"))
        env.notes.save(Note(title = "C", body = "z", category = "home"))
        env.notes.renameLabel("work", "job")
        val byTitle = env.notes.notes.first().associate { it.title to it.category }
        assertEquals("job,home", byTitle["A"])
        assertEquals("job", byTitle["B"])
        assertEquals("home", byTitle["C"])
    }
}
