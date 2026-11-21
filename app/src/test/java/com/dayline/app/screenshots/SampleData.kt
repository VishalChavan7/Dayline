package com.dayline.app.screenshots

import com.dayline.app.core.domain.DayValue
import com.dayline.app.core.domain.FocusTimerState
import com.dayline.app.core.domain.HabitStreakCalculator
import com.dayline.app.core.domain.Quotes
import com.dayline.app.core.domain.StatsCalculator
import com.dayline.app.core.domain.StatsInput
import com.dayline.app.core.domain.StatsPeriod
import com.dayline.app.core.domain.TaskSort
import com.dayline.app.core.model.FocusSession
import com.dayline.app.core.model.Habit
import com.dayline.app.core.model.HabitCompletion
import com.dayline.app.core.model.HabitFrequency
import com.dayline.app.core.model.Note
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.Project
import com.dayline.app.core.model.RecurrenceFrequency
import com.dayline.app.core.model.RecurrenceRule
import com.dayline.app.core.model.Reminder
import com.dayline.app.core.model.ReminderType
import com.dayline.app.core.model.Subtask
import com.dayline.app.core.model.Tag
import com.dayline.app.core.model.Task
import com.dayline.app.core.model.TaskItem
import com.dayline.app.ui.calendar.CalendarUiState
import com.dayline.app.ui.calendar.CalendarView
import com.dayline.app.ui.calendar.CalendarViewModel
import com.dayline.app.ui.habits.HabitsUiState
import com.dayline.app.ui.habits.HabitsViewModel
import com.dayline.app.ui.stats.HabitStreakRow
import com.dayline.app.ui.stats.StatsUiState
import com.dayline.app.ui.tasks.TaskFilter
import com.dayline.app.ui.tasks.TaskListMode
import com.dayline.app.ui.tasks.TaskListUiState
import com.dayline.app.ui.tasks.TaskListViewModel
import com.dayline.app.ui.today.HabitToday
import com.dayline.app.ui.today.TodayUiState
import com.dayline.app.ui.today.WeekDayInfo
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Test-only fixtures used to render screens at different sizes. Never shipped in the app. */
object SampleData {
    val today: LocalDate = LocalDate.of(2026, 11, 12)
    private fun at(d: LocalDate, h: Int = 9): Instant = d.atTime(h, 0).toInstant(ZoneOffset.UTC)

    val work = Project(id = 1, name = "Website relaunch", color = 0xFF2F7DF6.toInt(), icon = "rocket", deadline = today.plusDays(18))
    val home = Project(id = 2, name = "Home", color = 0xFF3E9B4F.toInt(), icon = "home")
    private val tagWork = Tag(1, "work")
    private val tagErrand = Tag(2, "errands")

    val items: List<TaskItem> = listOf(
        TaskItem(Task(id = 1, title = "Send the Q4 budget to finance", dueDate = today.minusDays(1), priority = Priority.URGENT, projectId = 1), project = work, tags = listOf(tagWork)),
        TaskItem(Task(id = 2, title = "Review landing page copy with the design team", dueDate = today, dueTime = LocalTime.of(11, 30), priority = Priority.HIGH, projectId = 1),
            project = work, subtasksDone = 1, subtasksTotal = 3,
            incompleteSubtasks = listOf(Subtask(21, 2, "Hero headline"), Subtask(22, 2, "Pricing section"))),
        TaskItem(Task(id = 3, title = "Book dentist appointment", dueDate = today, priority = Priority.MEDIUM), tags = listOf(tagErrand), reminderCount = 1),
        TaskItem(Task(id = 4, title = "Water the plants", dueDate = today, priority = Priority.LOW, projectId = 2,
            recurrence = RecurrenceRule(RecurrenceFrequency.DAILY)), project = home),
        TaskItem(Task(id = 5, title = "Morning run", dueDate = today, priority = Priority.MEDIUM, isCompleted = true, completedAt = at(today, 7))),
        TaskItem(Task(id = 6, title = "Prepare sprint demo", dueDate = today.plusDays(1), dueTime = LocalTime.of(15, 0), priority = Priority.HIGH, projectId = 1), project = work),
        TaskItem(Task(id = 7, title = "Renew car insurance", dueDate = today.plusDays(4), priority = Priority.URGENT), attachmentCount = 1),
        TaskItem(Task(id = 8, title = "Plan weekend hike", dueDate = today.plusDays(9), priority = Priority.LOW, projectId = 2), project = home),
        TaskItem(Task(id = 9, title = "Publish release notes", dueDate = today.plusDays(2), priority = Priority.MEDIUM, projectId = 1), project = work,
            blockedBy = listOf(Task(id = 6, title = "Prepare sprint demo"))),
        TaskItem(Task(id = 10, title = "Read 'Deep Work' chapter 3", priority = Priority.LOW)),
    ) + (1..12).map { i ->
        TaskItem(Task(id = 100L + i, title = "Done task $i", isCompleted = true, dueDate = today.minusDays(i % 6L), completedAt = at(today.minusDays(i % 6L)), priority = Priority.entries[i % 4]))
    }

    val habits = listOf(
        Habit(id = 1, name = "Meditate", color = 0xFF8E4EC6.toInt(), icon = "meditate", startDate = today.minusDays(40)),
        Habit(id = 2, name = "Drink water", color = 0xFF00A2C7.toInt(), icon = "water", targetPerDay = 8, startDate = today.minusDays(20)),
        Habit(id = 3, name = "Strength training", color = 0xFFE0892B.toInt(), icon = "fitness", frequency = HabitFrequency.WEEKLY_TARGET, timesPerWeek = 3, startDate = today.minusDays(60)),
        Habit(id = 4, name = "Journal", color = 0xFF3E9B4F.toInt(), icon = "book", frequency = HabitFrequency.SPECIFIC_DAYS,
            weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), startDate = today.minusDays(30)),
    )
    val completions: List<HabitCompletion> = (0L..12L).flatMap { d ->
        listOfNotNull(
            if (d != 3L) HabitCompletion(1, today.minusDays(d), 1) else null,
            HabitCompletion(2, today.minusDays(d), if (d == 0L) 5 else 8),
            if (d % 2 == 1L) HabitCompletion(3, today.minusDays(d), 1) else null,
            if (d in 1L..2L) HabitCompletion(4, today.minusDays(d), 1) else null,
        )
    }
    val sessions = (0L..6L).map { d ->
        FocusSession(id = d + 1, startedAt = at(today.minusDays(d), 10), endedAt = at(today.minusDays(d), 11), focusedMillis = (25 + d * 7) * 60_000L,
            plannedMillis = 25 * 60_000L, projectId = if (d % 2 == 0L) 1 else null, completed = true)
    }

    fun todayState(): TodayUiState {
        val open = items.filter { !it.task.isCompleted }
        return TodayUiState(
            loading = false,
            today = today,
            greeting = "Good morning",
            overdue = open.filter { it.task.isOverdue(today) },
            todayTasks = open.filter { it.task.dueDate == today },
            completedToday = items.filter { it.id == 5L },
            deadlines = open.filter { it.task.dueDate?.let { d -> d.isAfter(today) && !d.isAfter(today.plusDays(7)) } == true },
            habits = habits.map { h ->
                val c = completions.filter { it.habitId == h.id }
                val count = c.firstOrNull { it.date == today }?.count ?: 0
                val st = HabitStreakCalculator.stats(h, c, today)
                HabitToday(h, count, HabitStreakCalculator.isDone(h, count), st.currentStreak, st.streakUnit)
            },
            focusMinutesToday = 25,
            focusGoalMinutes = 120,
            focusSessionsToday = 1,
            timer = FocusTimerState(),
            week = (0L..6L).map { i -> val d = today.minusDays(3).plusDays(i); WeekDayInfo(d, (i % 3).toInt() + 1, (i % 2).toInt(), Priority.entries[(i % 4).toInt()]) },
            weekCompleted = (0L..6L).map { i -> DayValue(today.minusDays(3).plusDays(i), if (i <= 3) (i * 2 + 1).toInt() else 0) },
            weekTotal = 16,
            lastWeekTotal = 12,
            quote = Quotes.forDay(today),
            pinnedNotes = listOf(Note(id = 1, title = "Launch checklist", body = "DNS, analytics, redirects, OG images", isPinned = true)),
            reminders = listOf(Reminder(id = 1, type = ReminderType.CUSTOM, title = "Call mom", at = today.atTime(18, 30), isPinned = true)),
            projects = listOf(
                com.dayline.app.ui.today.CollectionSummary(1, work.name, work.icon, work.color, 5, 3),
                com.dayline.app.ui.today.CollectionSummary(2, home.name, home.icon, home.color, 2, 6),
                com.dayline.app.ui.today.CollectionSummary(3, "Thesis", "book", 0xFF8E4EC6.toInt(), 9, 1),
            ),
            lists = listOf(
                com.dayline.app.ui.today.CollectionSummary(11, "Groceries", "cart", 0xFF3E9B4F.toInt(), 6, 2),
                com.dayline.app.ui.today.CollectionSummary(12, "Reading list", "book", 0xFFE0892B.toInt(), 4, 9),
                com.dayline.app.ui.today.CollectionSummary(13, "Design QA", "list", 0xFF2F7DF6.toInt(), 3, 1, caption = "Website relaunch"),
            ),
        )
    }

    /** Task editor filled in the way a real task looks. */
    fun editorState(): com.dayline.app.ui.tasks.TaskEditorState = com.dayline.app.ui.tasks.TaskEditorState(
        loading = false,
        isNew = false,
        id = 2,
        title = "Review landing page copy with the design team",
        description = "Focus on the hero and pricing sections.",
        dueDate = today,
        dueTime = LocalTime.of(11, 30),
        startDate = today.minusDays(1),
        priority = Priority.HIGH,
        projectId = 1,
        category = "Marketing",
        recurrence = RecurrenceRule(RecurrenceFrequency.WEEKLY, weekdays = setOf(DayOfWeek.THURSDAY)),
        reminders = listOf(Reminder(id = 1, type = ReminderType.DEADLINE, offsetMinutes = 30), Reminder(id = 2, type = ReminderType.DEADLINE, offsetMinutes = 1440)),
        subtasks = listOf(
            com.dayline.app.ui.tasks.EditableSubtask(1, Subtask(21, 2, "Hero headline", isDone = true)),
            com.dayline.app.ui.tasks.EditableSubtask(2, Subtask(22, 2, "Pricing section")),
        ),
        tagNames = listOf("work", "launch"),
    )

    val notes: List<Note> = listOf(
        Note(id = 1, title = "Launch checklist", isPinned = true, color = 3, category = "work",
            body = com.dayline.app.core.domain.NoteCodec.encode("<p>Before we flip the switch:</p>",
                listOf(com.dayline.app.core.domain.CheckItem("DNS cut-over", true), com.dayline.app.core.domain.CheckItem("Analytics events"), com.dayline.app.core.domain.CheckItem("Redirect map"), com.dayline.app.core.domain.CheckItem("OG images")))),
        Note(id = 2, title = "Book ideas", color = 8, category = "ideas,reading", body = "<p><b>Deep Work</b> — Cal Newport</p><p><i>The Pragmatic Programmer</i></p><p><u>Atomic Habits</u></p>"),
        Note(id = 3, title = "", color = 0, body = "Call the plumber about the kitchen tap before Friday."),
        Note(id = 4, title = "Sprint retro", color = 6, category = "work",
            body = "<h2>Went well</h2><ul><li>Faster reviews</li><li>Fewer flaky tests</li></ul><p><span style=\"background-color: #FFF176; color: #1F1F1F;\">Action: pair on the release script</span></p>"),
        Note(id = 5, title = "Gift ideas", isPinned = true, color = 1, body = "<p>Mum — pottery class</p><p>Sam — climbing shoes</p>"),
        Note(id = 6, title = "Wi-Fi", body = "<p>Guest network: <b>dayline-guest</b></p>"),
    )

    fun notesState() = com.dayline.app.ui.notes.NotesUiState(loading = false, notes = notes, total = notes.size, categories = listOf("work", "ideas"),
        counts = mapOf("pinned" to notes.count { it.isPinned }, "tag:work" to notes.count { "work" in it.category.orEmpty() }, "tag:ideas" to notes.count { "ideas" in it.category.orEmpty() }))

    fun editorOptions() = com.dayline.app.ui.tasks.EditorOptions(projects = listOf(work, home))


    fun upcomingState() = TaskListUiState(
        loading = false, mode = TaskListMode.UPCOMING, today = today,
        sections = TaskListViewModel.buildSections(items, TaskListMode.UPCOMING, TaskFilter(), TaskSort.DUE_DATE, today, DayOfWeek.MONDAY, ZoneOffset.UTC),
    )

    fun allState() = TaskListUiState(
        loading = false, mode = TaskListMode.ALL, today = today,
        sections = TaskListViewModel.buildSections(items, TaskListMode.ALL, TaskFilter(), TaskSort.DUE_DATE, today, DayOfWeek.MONDAY, ZoneOffset.UTC),
        projects = listOf(work, home),
    )

    fun calendarState(view: CalendarView) = CalendarUiState(
        loading = false, today = today, selected = today, view = view, weekStart = DayOfWeek.MONDAY,
        entries = CalendarViewModel.rangeFor(view, today, DayOfWeek.MONDAY).let { (from, to) -> CalendarViewModel.buildEntries(items, from, to, DayOfWeek.MONDAY) },
        focus = CalendarViewModel.buildFocus(sessions, today.minusDays(40), today.plusDays(40), ZoneOffset.UTC),
        yearCounts = if (view == CalendarView.YEAR) CalendarViewModel.buildEntries(items, today.withDayOfYear(1), today.withDayOfYear(today.lengthOfYear()), DayOfWeek.MONDAY).mapValues { it.value.size } else emptyMap(),
    )

    fun habitsState() = HabitsUiState(
        loading = false, today = today, weekStart = DayOfWeek.MONDAY,
        active = habits.map { HabitsViewModel.row(it, completions.filter { c -> c.habitId == it.id }, today, DayOfWeek.MONDAY) },
    )

    fun statsState() = StatsUiState(
        loading = false, period = StatsPeriod.WEEK, anchor = today, today = today, weekStart = DayOfWeek.MONDAY,
        report = StatsCalculator.report(StatsInput(items.map { it.task }, sessions, habits, completions, listOf(work, home)), StatsPeriod.WEEK, today, today, ZoneOffset.UTC),
        streaks = habits.map { HabitStreakRow(it.name, it.color, 5, 12, "days") },
    )
}
