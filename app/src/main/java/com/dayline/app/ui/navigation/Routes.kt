package com.dayline.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import java.time.LocalDate

object Routes {
    const val TODAY = "today"
    const val CALENDAR = "calendar"
    const val INBOX = "inbox"
    const val HABITS = "habits"
    const val MORE = "more"
    const val UPCOMING = "upcoming"
    const val ALL = "all"
    const val COMPLETED = "completed"
    const val STATS = "stats"
    const val FOCUS = "focus"
    const val FOCUS_PATTERN = "focus?task={task}"
    const val NOTES = "notes"
    const val NOTE = "note/{id}?kind={kind}"
    const val SETTINGS = "settings"
    const val DASHBOARD_SETTINGS = "settings/dashboard"
    const val TAGS = "tags"
    const val SEARCH = "search"
    const val TASK = "task/{id}?date={date}&project={project}&list={list}"
    const val HABIT = "habit/{id}"
    const val HABIT_EDIT = "habit/edit/{id}"
}

enum class TopLevel(val route: String, val label: String, val selected: ImageVector, val unselected: ImageVector) {
    TODAY(Routes.TODAY, "Today", Icons.Rounded.WbSunny, Icons.Outlined.WbSunny),
    CALENDAR(Routes.CALENDAR, "Calendar", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarMonth),
    NOTES(Routes.NOTES, "Notes", Icons.AutoMirrored.Rounded.StickyNote2, Icons.AutoMirrored.Outlined.StickyNote2),
    HABITS(Routes.HABITS, "Habits", Icons.Rounded.SelfImprovement, Icons.Outlined.SelfImprovement),
    MORE(Routes.MORE, "More", Icons.Rounded.MoreHoriz, Icons.Outlined.MoreHoriz),
}

/** Thin navigation facade handed to screens. */
class AppNavigator(private val nav: NavHostController) {
    fun back() { nav.popBackStack() }
    fun to(route: String) = nav.navigate(route) { launchSingleTop = true }

    fun topLevel(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    fun task(id: Long) = to("task/$id")
    fun newTask(date: LocalDate? = null, projectId: Long? = null, listId: Long? = null) =
        to("task/0?date=${date?.toEpochDay() ?: Long.MIN_VALUE}&project=${projectId ?: -1}&list=${listId ?: -1}")
    fun habit(id: Long) = to("habit/$id")
    fun editHabit(id: Long) = to("habit/edit/$id")
    fun note(id: Long) = to("note/$id")
    /** A new note that starts as [kind]: text, list, image, drawing or audio. */
    fun newNote(kind: String) = to("note/0?kind=$kind")
}
