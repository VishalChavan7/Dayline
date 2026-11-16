package com.dayline.app.screenshots

import com.dayline.app.ui.tasks.TaskListMode
import com.dayline.app.ui.tasks.TaskListUiState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.dayline.app.core.model.ThemeMode
import com.dayline.app.ui.calendar.CalendarActions
import com.dayline.app.ui.calendar.CalendarContent
import com.dayline.app.ui.calendar.CalendarView
import com.dayline.app.ui.common.Formats
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.habits.HabitsActions
import com.dayline.app.ui.habits.HabitsContent
import com.dayline.app.ui.stats.StatsContent
import com.dayline.app.ui.tasks.TaskListActions
import com.dayline.app.ui.tasks.TaskListContent
import com.dayline.app.ui.theme.DaylineTheme
import com.dayline.app.ui.today.TodayActions
import com.dayline.app.ui.today.TodayContent
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the main screens on a range of phone sizes, including the Galaxy S25 (360×780 dp, xxhdpi),
 * the S25 with Samsung's "large" display size (~320 dp wide), a Pixel 9, the S25 Ultra and an unfolded
 * foldable, plus dark mode and 130% font scale. Images are written to app/build/outputs/roborazzi.
 *
 * Run: ./gradlew recordRoborazziDebug   (or testDebugUnitTest to just execute them)
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneSizeScreenshotTest(private val device: Device) {

    data class Device(val name: String, val qualifiers: String, val dark: Boolean = false, val fontScale: Float = 1f) {
        override fun toString() = name
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun devices(): List<Array<Any>> = listOf(
            Device("galaxy_s25", "w360dp-h780dp-xxhdpi"),
            Device("galaxy_s25_dark", "w360dp-h780dp-night-xxhdpi", dark = true),
            Device("galaxy_s25_font130", "w360dp-h780dp-xxhdpi", fontScale = 1.3f),
            Device("galaxy_s25_large_display", "w320dp-h693dp-xxhdpi"),
            Device("pixel_9", "w412dp-h915dp-xxhdpi"),
            Device("galaxy_s25_ultra", "w412dp-h891dp-xxxhdpi"),
            Device("foldable_unfolded", "w673dp-h841dp-xhdpi"),
            Device("small_phone", "w320dp-h640dp-xhdpi"),
            Device("galaxy_s25_landscape", "w780dp-h360dp-land-xxhdpi"),
        ).map { arrayOf<Any>(it) }
    }

    @get:Rule val compose = createComposeRule()

    private fun render(screen: String, scrolled: Boolean = false, infinite: Boolean = false, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers("+${device.qualifiers}")
        RuntimeEnvironment.setFontScale(device.fontScale)
        if (infinite) compose.mainClock.autoAdvance = false
        compose.setContent {
            DaylineTheme(themeMode = if (device.dark) ThemeMode.DARK else ThemeMode.LIGHT, dynamicColor = false) {
                CompositionLocalProvider(LocalFormats provides Formats()) { content() }
            }
        }
        if (infinite) compose.mainClock.advanceTimeBy(1_200) else compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/${device.name}/$screen.png")
        if (scrolled) {
            compose.onAllNodes(hasScrollAction()).onFirst().performTouchInput { swipeUp() }
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/${device.name}/${screen}_scrolled.png")
        }
    }

    @Test fun today() = render("01_today", scrolled = true) { TodayContent(SampleData.todayState(), TodayActions()) }
    @Test fun calendarMonth() = render("02_calendar_month") { CalendarContent(SampleData.calendarState(CalendarView.MONTH), CalendarActions()) }
    @Test fun calendarWeek() = render("03_calendar_week") { CalendarContent(SampleData.calendarState(CalendarView.WEEK), CalendarActions()) }
    @Test fun calendarDay() = render("04_calendar_day") { CalendarContent(SampleData.calendarState(CalendarView.DAY), CalendarActions()) }
    @Test fun calendarAgenda() = render("05_calendar_agenda") { CalendarContent(SampleData.calendarState(CalendarView.AGENDA), CalendarActions()) }
    @Test fun calendarYear() = render("06_calendar_year") { CalendarContent(SampleData.calendarState(CalendarView.YEAR), CalendarActions()) }
    @Test fun upcoming() = render("07_upcoming") { TaskListContent(SampleData.upcomingState(), TaskListActions(onBack = {}, onAdd = {})) }
    @Test fun allTasks() = render("08_all_tasks") { TaskListContent(SampleData.allState(), TaskListActions(onBack = {}, onAdd = {})) }
    @Test fun habits() = render("09_habits") { HabitsContent(SampleData.habitsState(), HabitsActions()) }
    @Test fun stats() = render("10_stats", scrolled = true) { StatsContent(SampleData.statsState(), {}, {}, {}) }
    @Test fun editorNew() = render("11_editor_new") {
        com.dayline.app.ui.tasks.TaskEditorContent(com.dayline.app.ui.tasks.TaskEditorState(loading = false), com.dayline.app.ui.tasks.EditorOptions(), SampleData.today, com.dayline.app.ui.tasks.TaskEditorActions())
    }
    @Test fun editorFilled() = render("12_editor_filled", scrolled = true) {
        com.dayline.app.ui.tasks.TaskEditorContent(SampleData.editorState(), SampleData.editorOptions(), SampleData.today, com.dayline.app.ui.tasks.TaskEditorActions())
    }
    @Test fun quickAdd() = render("13_quick_add") {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            com.dayline.app.ui.quickadd.QuickAddForm(
                text = "Call Sam about the venue", onText = {}, today = SampleData.today, date = SampleData.today, dateExplicit = true,
                onDate = { _, _ -> }, onPickDate = {}, priority = com.dayline.app.core.model.Priority.HIGH, onPriority = {},
                projects = listOf(SampleData.work, SampleData.home), lists = emptyList(), projectId = 1, listId = null,
                onDestination = { _, _ -> }, onSubmit = {}, onDetails = {},
            )
        }
    }
    @Test fun onboarding() = render("14_onboarding", infinite = true) { com.dayline.app.ui.onboarding.OnboardingScreen(onFinish = {}) }
    @Test fun onboardingName() = render("14b_onboarding_name", infinite = true) { com.dayline.app.ui.onboarding.OnboardingScreen(onFinish = {}, initialPage = 1) }
    @Test fun onboardingCapture() = render("14c_onboarding_capture", infinite = true) { com.dayline.app.ui.onboarding.OnboardingScreen(onFinish = {}, initialPage = 2) }
    @Test fun launcherIcon() = render("00_launcher_icon") {
        androidx.compose.foundation.layout.Row(androidx.compose.ui.Modifier.padding(24.dp)) {
            listOf(com.dayline.app.R.mipmap.ic_launcher, com.dayline.app.R.mipmap.ic_launcher_round).forEach { res ->
                androidx.compose.ui.viewinterop.AndroidView({ ctx -> android.widget.ImageView(ctx).apply { setImageResource(res) } }, androidx.compose.ui.Modifier.size(160.dp))
            }
        }
    }
    @Test fun onboardingNotes() = render("14d_onboarding_notes", infinite = true) { com.dayline.app.ui.onboarding.OnboardingScreen(onFinish = {}, initialPage = 3) }
    @Test fun addMenu() = render("15_add_menu") {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
            TodayContent(SampleData.todayState(), TodayActions())
            com.dayline.app.ui.navigation.AddMenuScrim(true) {}
            com.dayline.app.ui.navigation.AddFab(
                visible = true, expanded = true, onTap = {}, onExpandedChange = {}, onAction = {},
                modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomEnd).padding(16.dp),
            )
        }
    }
    @Test fun notesEmpty() = render("19_empty_notes") { com.dayline.app.ui.notes.NotesContent(com.dayline.app.ui.notes.NotesUiState(loading = false), com.dayline.app.ui.notes.NotesActions()) }
    @Test fun inboxEmpty() = render("19_empty_inbox") { TaskListContent(TaskListUiState(loading = false, mode = TaskListMode.INBOX), TaskListActions(onBack = {}, onAdd = {})) }
    @Test fun more() = render("20_more") { com.dayline.app.ui.more.MoreContent(com.dayline.app.ui.more.MoreCounts(upcoming = 4, open = 12, completed = 40, inbox = 2)) {} }
    @Test fun notes() = render("17_notes") { com.dayline.app.ui.notes.NotesContent(SampleData.notesState(), com.dayline.app.ui.notes.NotesActions()) }
    @Test fun noteEditor() = render("18_note_editor") {
        val body = androidx.compose.runtime.remember {
            com.dayline.app.ui.notes.NoteBodyModel().apply {
                load("<h2>Sprint retro</h2><p>What went <b>really</b> well and what we'll <i>change</i> #work #retro</p><p style=\"text-align: center;\">Centred line</p>" +
                    "<p>\u2610 Draft the agenda</p><p>\u2611\uFE0E Book the room</p>" +
                    "<ul><li>Faster reviews</li><li>Fewer flaky tests</li></ul><p><span style=\"background-color: #FFF176; color: #1F1F1F;\">Pair on the release script</span></p>")
            }
        }
        com.dayline.app.ui.notes.NoteEditorContent(
            com.dayline.app.ui.notes.NoteEditorState(
                loading = false,
                note = com.dayline.app.core.model.Note(id = 4, title = "Team notes", color = 6, category = "work,retro"),
            ),
            body, linkedTask = null, linkedProject = "Website relaunch", actions = com.dayline.app.ui.notes.NoteEditorActions(),
        )
    }

    @Test fun navBar() = render("16_nav_bar") {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.BottomCenter) {
            com.dayline.app.ui.components.DaylineNavBar(
                com.dayline.app.ui.navigation.TopLevel.entries.map { com.dayline.app.ui.components.NavItem(it.route, it.label, it.selected, it.unselected) },
                "today", onSelect = {},
            )
        }
    }
}
