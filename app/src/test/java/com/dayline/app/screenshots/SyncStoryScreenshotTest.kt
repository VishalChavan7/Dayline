package com.dayline.app.screenshots

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.dayline.app.core.model.HabitCompletion
import com.dayline.app.core.model.Note
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.Task
import com.dayline.app.core.model.TaskItem
import com.dayline.app.core.model.ThemeMode
import com.dayline.app.ui.common.Formats
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.habits.HabitsActions
import com.dayline.app.ui.habits.HabitsContent
import com.dayline.app.ui.habits.HabitsUiState
import com.dayline.app.ui.habits.HabitsViewModel
import com.dayline.app.ui.journal.JournalContent
import com.dayline.app.ui.journal.JournalEntry
import com.dayline.app.ui.journal.JournalUiState
import com.dayline.app.ui.theme.DaylineTheme
import com.dayline.app.ui.today.TodayActions
import com.dayline.app.ui.today.TodayContent
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.ZoneOffset

/**
 * Phone frames for the README's sync video: the same moments the Mac records with `Dayline --story`
 * (a task added on the Mac, a habit ticked on the phone, a journal entry written on the Mac).
 * Written to app/build/outputs/roborazzi/story/.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SyncStoryScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private val today = SampleData.today
    private val newTask = TaskItem(Task(id = 50, title = "Call the bank about the card", priority = Priority.MEDIUM))

    private fun inbox(withNew: Boolean) = com.dayline.app.ui.tasks.TaskListUiState(
        loading = false, mode = com.dayline.app.ui.tasks.TaskListMode.INBOX, today = today,
        sections = com.dayline.app.ui.tasks.TaskListViewModel.buildSections(
            SampleData.items + if (withNew) listOf(newTask) else emptyList(), com.dayline.app.ui.tasks.TaskListMode.INBOX,
            com.dayline.app.ui.tasks.TaskFilter(), com.dayline.app.core.domain.TaskSort.MANUAL, today, DayOfWeek.MONDAY, ZoneOffset.UTC),
    )

    private fun shot(name: String, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("+w360dp-h780dp-xxhdpi")
        compose.setContent {
            DaylineTheme(themeMode = ThemeMode.LIGHT, dynamicColor = false) {
                CompositionLocalProvider(LocalFormats provides Formats()) { content() }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/story/$name.png")
    }

    private fun habitsState(ticked: Boolean): HabitsUiState {
        val comps = SampleData.completions + if (ticked) listOf(HabitCompletion(4, today, 1)) else emptyList()
        return HabitsUiState(
            loading = false, today = today, weekStart = DayOfWeek.MONDAY,
            active = SampleData.habits.map { HabitsViewModel.row(it, comps.filter { c -> c.habitId == it.id }, today, DayOfWeek.MONDAY) },
        )
    }

    @Test fun todayBefore() = shot("today-before") { TodayContent(SampleData.todayState(), TodayActions()) }

    @Test fun todayAfter() = shot("today-after") {
        val s = SampleData.todayState()
        // End of the story: the new task sits in the Inbox (no date) and the Journal habit is ticked.
        TodayContent(s.copy(habits = s.habits.map { if (it.habit.id == 4L) it.copy(count = 1, done = true) else it }), TodayActions())
    }

    @Test fun inboxBefore() = shot("inbox-before") { com.dayline.app.ui.tasks.TaskListContent(inbox(false), com.dayline.app.ui.tasks.TaskListActions(onBack = {}, onAdd = {})) }

    @Test fun inboxAfter() = shot("inbox-after") { com.dayline.app.ui.tasks.TaskListContent(inbox(true), com.dayline.app.ui.tasks.TaskListActions(onBack = {}, onAdd = {})) }

    @Test fun habitsBefore() = shot("habits-before") { HabitsContent(habitsState(false), HabitsActions()) }

    @Test fun habitsAfter() = shot("habits-after") { HabitsContent(habitsState(true), HabitsActions()) }

    @Test fun journal() = shot("journal") {
        fun entry(id: Long, title: String, text: String, daysAgo: Long, hour: Int = 21) = JournalEntry(
            Note(id = id, title = title, body = "<p>$text</p>", category = "journal",
                createdAt = today.minusDays(daysAgo).atTime(hour, 0).toInstant(ZoneOffset.UTC)),
            today.minusDays(daysAgo), title, text, text.split(" ").size,
        )
        val entries = listOf(
            entry(32, "What made you smile today?", "Coffee with Sam after the design review. We finally agreed on the landing page, and the walk back in the sun felt like a small holiday.", 0, 9),
            entry(30, "A quiet Sunday", "Slept in, cleaned the balcony and potted the basil. Read forty pages and kept the phone away until lunch.", 4),
            entry(31, "Describe a small win from this week.", "Shipped the analytics setup two days early. The team noticed, and I noticed I wasn't stressed about it.", 6),
        )
        JournalContent(JournalUiState(loading = false, entries = entries, total = 3, streak = 1, thisYear = 3, words = entries.sumOf { it.words }, today = today))
    }
}
