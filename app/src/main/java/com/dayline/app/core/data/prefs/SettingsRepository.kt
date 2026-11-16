package com.dayline.app.core.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.dayline.app.core.domain.FocusConfig
import com.dayline.app.core.domain.FocusPhase
import com.dayline.app.core.domain.FocusTimerState
import com.dayline.app.core.domain.TimerStatus
import com.dayline.app.core.model.AppSettings
import com.dayline.app.core.model.DashboardSection
import com.dayline.app.core.model.DateFormatOption
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.ThemeMode
import com.dayline.app.core.model.TimeFormat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

private inline fun <reified T : Enum<T>> enumOr(value: String?, default: T): T =
    value?.let { v -> enumValues<T>().firstOrNull { it.name == v } } ?: default

@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {

    private object Keys {
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val accent = intPreferencesKey("accent_color")
        val defaultPriority = stringPreferencesKey("default_priority")
        val defaultList = longPreferencesKey("default_list")
        val weekStart = intPreferencesKey("week_start")
        val dateFormat = stringPreferencesKey("date_format")
        val timeFormat = stringPreferencesKey("time_format")
        val taskReminders = booleanPreferencesKey("task_reminders")
        val habitReminders = booleanPreferencesKey("habit_reminders")
        val focusNotifications = booleanPreferencesKey("focus_notifications")
        val reminderOffset = intPreferencesKey("default_reminder_offset")
        val reminderTime = intPreferencesKey("default_reminder_time")
        val focusMinutes = intPreferencesKey("focus_minutes")
        val shortBreak = intPreferencesKey("short_break")
        val longBreak = intPreferencesKey("long_break")
        val sessionsBeforeLong = intPreferencesKey("sessions_before_long")
        val autoBreaks = booleanPreferencesKey("auto_breaks")
        val autoFocus = booleanPreferencesKey("auto_focus")
        val focusGoal = intPreferencesKey("focus_goal")
        val dashOrder = stringPreferencesKey("dash_order")
        val dashHidden = stringSetPreferencesKey("dash_hidden")
        val dashCollapsed = stringSetPreferencesKey("dash_collapsed")
        val groupByPriority = booleanPreferencesKey("group_by_priority")
        val notifPrompted = booleanPreferencesKey("notification_prompt_shown")
        val onboarded = booleanPreferencesKey("onboarding_done")
        val userName = stringPreferencesKey("user_name")
        val nameAsked = booleanPreferencesKey("name_asked")
        val labelIcons = stringPreferencesKey("label_icons")
        val widgetArt = stringPreferencesKey("widget_art")
        val syncEnabled = booleanPreferencesKey("sync_enabled")
        val syncAccount = stringPreferencesKey("sync_account")
        val syncLastAt = longPreferencesKey("sync_last_at")
        val syncLocalHash = stringPreferencesKey("sync_local_hash")
        val syncRemoteVersion = stringPreferencesKey("sync_remote_version")
        val syncError = stringPreferencesKey("sync_error")

        // Focus timer runtime state
        val tStatus = stringPreferencesKey("timer_status")
        val tPhase = stringPreferencesKey("timer_phase")
        val tDuration = longPreferencesKey("timer_duration")
        val tRemaining = longPreferencesKey("timer_remaining")
        val tEnd = longPreferencesKey("timer_end")
        val tStarted = longPreferencesKey("timer_started")
        val tCycle = intPreferencesKey("timer_cycle")
        val tTask = longPreferencesKey("timer_task")
        val tProject = longPreferencesKey("timer_project")
    }

    val settings: Flow<AppSettings> = store.data.map { p -> read(p) }.distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    /** Sections added in an app update slot in after the section that precedes them by default. */
    private fun withNewSections(saved: List<DashboardSection>): List<DashboardSection> {
        val result = saved.toMutableList()
        DashboardSection.entries.filter { it !in saved }.forEach { m ->
            val before = DashboardSection.entries.getOrNull(m.ordinal - 1)
            val idx = result.indexOf(before)
            result.add(if (idx >= 0) idx + 1 else result.size, m)
        }
        return result
    }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        val order = p[Keys.dashOrder]?.split(',')?.mapNotNull { n -> DashboardSection.entries.firstOrNull { it.name == n } }
            ?.let { saved -> withNewSections(saved) }
            ?: d.dashboardOrder
        return AppSettings(
            themeMode = enumOr(p[Keys.theme], d.themeMode),
            dynamicColor = p[Keys.dynamic] ?: d.dynamicColor,
            accentColor = p[Keys.accent] ?: d.accentColor,
            defaultPriority = enumOr(p[Keys.defaultPriority], d.defaultPriority),
            defaultListId = p[Keys.defaultList]?.takeIf { it > 0 },
            weekStart = p[Keys.weekStart]?.let { DayOfWeek.of(it.coerceIn(1, 7)) } ?: d.weekStart,
            dateFormat = enumOr(p[Keys.dateFormat], d.dateFormat),
            timeFormat = enumOr(p[Keys.timeFormat], d.timeFormat),
            taskRemindersEnabled = p[Keys.taskReminders] ?: d.taskRemindersEnabled,
            habitRemindersEnabled = p[Keys.habitReminders] ?: d.habitRemindersEnabled,
            focusNotificationsEnabled = p[Keys.focusNotifications] ?: d.focusNotificationsEnabled,
            defaultReminderOffset = p[Keys.reminderOffset].let { if (it == null) d.defaultReminderOffset else if (it < 0) null else it },
            defaultReminderTime = p[Keys.reminderTime]?.let { LocalTime.ofSecondOfDay(it.toLong()) } ?: d.defaultReminderTime,
            focus = FocusConfig(
                focusMinutes = p[Keys.focusMinutes] ?: d.focus.focusMinutes,
                shortBreakMinutes = p[Keys.shortBreak] ?: d.focus.shortBreakMinutes,
                longBreakMinutes = p[Keys.longBreak] ?: d.focus.longBreakMinutes,
                sessionsBeforeLongBreak = p[Keys.sessionsBeforeLong] ?: d.focus.sessionsBeforeLongBreak,
                autoStartBreaks = p[Keys.autoBreaks] ?: d.focus.autoStartBreaks,
                autoStartFocus = p[Keys.autoFocus] ?: d.focus.autoStartFocus,
                dailyGoalMinutes = p[Keys.focusGoal] ?: d.focus.dailyGoalMinutes,
            ),
            dashboardOrder = order,
            hiddenSections = p[Keys.dashHidden]?.mapNotNull { n -> DashboardSection.entries.firstOrNull { it.name == n } }?.toSet() ?: d.hiddenSections,
            collapsedSections = p[Keys.dashCollapsed]?.mapNotNull { n -> DashboardSection.entries.firstOrNull { it.name == n } }?.toSet() ?: d.collapsedSections,
            groupTodayByPriority = p[Keys.groupByPriority] ?: d.groupTodayByPriority,
            userName = p[Keys.userName].orEmpty(),
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) = store.edit { it[Keys.theme] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[Keys.dynamic] = enabled }
    suspend fun setAccentColor(color: Int) = store.edit { it[Keys.accent] = color; it[Keys.dynamic] = false }
    suspend fun setDefaultPriority(p: Priority) = store.edit { it[Keys.defaultPriority] = p.name }
    suspend fun setDefaultList(id: Long?) = store.edit { it[Keys.defaultList] = id ?: -1L }
    suspend fun setWeekStart(day: DayOfWeek) = store.edit { it[Keys.weekStart] = day.value }
    suspend fun setDateFormat(f: DateFormatOption) = store.edit { it[Keys.dateFormat] = f.name }
    suspend fun setTimeFormat(f: TimeFormat) = store.edit { it[Keys.timeFormat] = f.name }
    suspend fun setTaskReminders(enabled: Boolean) = store.edit { it[Keys.taskReminders] = enabled }
    suspend fun setHabitReminders(enabled: Boolean) = store.edit { it[Keys.habitReminders] = enabled }
    suspend fun setFocusNotifications(enabled: Boolean) = store.edit { it[Keys.focusNotifications] = enabled }
    suspend fun setDefaultReminderOffset(minutes: Int?) = store.edit { it[Keys.reminderOffset] = minutes ?: -1 }
    suspend fun setDefaultReminderTime(time: LocalTime) = store.edit { it[Keys.reminderTime] = time.toSecondOfDay() }
    suspend fun setGroupTodayByPriority(enabled: Boolean) = store.edit { it[Keys.groupByPriority] = enabled }

    suspend fun setFocusConfig(c: FocusConfig) = store.edit {
        it[Keys.focusMinutes] = c.focusMinutes.coerceIn(1, 240)
        it[Keys.shortBreak] = c.shortBreakMinutes.coerceIn(1, 60)
        it[Keys.longBreak] = c.longBreakMinutes.coerceIn(1, 90)
        it[Keys.sessionsBeforeLong] = c.sessionsBeforeLongBreak.coerceIn(1, 12)
        it[Keys.autoBreaks] = c.autoStartBreaks
        it[Keys.autoFocus] = c.autoStartFocus
        it[Keys.focusGoal] = c.dailyGoalMinutes.coerceIn(5, 960)
    }

    suspend fun setDashboardOrder(order: List<DashboardSection>) = store.edit { it[Keys.dashOrder] = order.joinToString(",") { s -> s.name } }
    suspend fun setSectionHidden(section: DashboardSection, hidden: Boolean) = store.edit {
        val cur = it[Keys.dashHidden].orEmpty()
        it[Keys.dashHidden] = if (hidden) cur + section.name else cur - section.name
    }
    suspend fun setSectionCollapsed(section: DashboardSection, collapsed: Boolean) = store.edit {
        val cur = it[Keys.dashCollapsed].orEmpty()
        it[Keys.dashCollapsed] = if (collapsed) cur + section.name else cur - section.name
    }
    suspend fun resetDashboard() = store.edit {
        it.remove(Keys.dashOrder); it.remove(Keys.dashHidden); it.remove(Keys.dashCollapsed)
    }

    val notificationPromptShown: Flow<Boolean> = store.data.map { it[Keys.notifPrompted] ?: false }.distinctUntilChanged()
    suspend fun markNotificationPromptShown() = store.edit { it[Keys.notifPrompted] = true }

    /** First-run tutorial. Existing installs that already answered the notification prompt skip it. */
    val onboardingDone: Flow<Boolean> = store.data.map { it[Keys.onboarded] ?: (it[Keys.notifPrompted] ?: false) }.distinctUntilChanged()
    suspend fun markOnboardingDone() = store.edit { it[Keys.onboarded] = true }

    // ---- Name used in greetings ----
    /** True once we've asked for a name (answered or skipped), so we never nag. */
    val nameAsked: Flow<Boolean> = store.data.map { it[Keys.nameAsked] ?: false }.distinctUntilChanged()
    suspend fun setUserName(name: String) = store.edit {
        it[Keys.userName] = name.trim().take(40)
        it[Keys.nameAsked] = true
    }
    suspend fun markNameAsked() = store.edit { it[Keys.nameAsked] = true }

    // ---- Note label icons ("work" -> "folder") ----
    val labelIcons: Flow<Map<String, String>> = store.data.map { p ->
        p[Keys.labelIcons].orEmpty().split('\u0002').mapNotNull { e ->
            val parts = e.split('\u0001')
            if (parts.size == 2 && parts[0].isNotBlank()) parts[0] to parts[1] else null
        }.toMap()
    }.distinctUntilChanged()
    suspend fun setLabelIcon(label: String, icon: String) = store.edit { p ->
        val map = p[Keys.labelIcons].orEmpty().split('\u0002').mapNotNull { e ->
            val parts = e.split('\u0001'); if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap().toMutableMap()
        map[label.lowercase()] = icon
        p[Keys.labelIcons] = map.entries.joinToString("\u0002") { "${it.key}\u0001${it.value}" }
    }

    /** Keeps a label's icon when the label is renamed. */
    suspend fun renameLabelIcon(old: String, new: String) = store.edit { p ->
        val map = p[Keys.labelIcons].orEmpty().split('\u0002').mapNotNull { e ->
            val parts = e.split('\u0001'); if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap().toMutableMap()
        map.remove(old.lowercase())?.let { map[new.trim().lowercase()] = it }
        p[Keys.labelIcons] = map.entries.joinToString("\u0002") { "${it.key}\u0001${it.value}" }
    }

    // ---- Widget background art (widget -> style; "none" turns it off) ----
    val widgetArt: Flow<Map<String, String>> = store.data.map { p ->
        WidgetArt.defaults + p[Keys.widgetArt].orEmpty().split(',').mapNotNull { e ->
            val parts = e.split('=')
            if (parts.size == 2 && parts[0].isNotBlank()) parts[0] to parts[1] else null
        }.toMap()
    }.distinctUntilChanged()
    suspend fun setWidgetArt(widget: String, style: String) = store.edit { p ->
        val map = p[Keys.widgetArt].orEmpty().split(',').mapNotNull { e ->
            val parts = e.split('='); if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap().toMutableMap()
        map[widget] = style
        p[Keys.widgetArt] = map.entries.joinToString(",") { "${it.key}=${it.value}" }
    }

    // ---- Google Drive sync bookkeeping ----
    data class SyncPrefs(
        val enabled: Boolean = false,
        val account: String = "",
        val lastAt: Long = 0L,
        val localHash: String = "",
        val remoteVersion: String = "",
        val error: String = "",
    )
    val sync: Flow<SyncPrefs> = store.data.map { p ->
        SyncPrefs(
            p[Keys.syncEnabled] ?: false, p[Keys.syncAccount].orEmpty(), p[Keys.syncLastAt] ?: 0L,
            p[Keys.syncLocalHash].orEmpty(), p[Keys.syncRemoteVersion].orEmpty(), p[Keys.syncError].orEmpty(),
        )
    }.distinctUntilChanged()
    suspend fun setSyncEnabled(enabled: Boolean, account: String = "") = store.edit {
        it[Keys.syncEnabled] = enabled
        if (account.isNotBlank()) it[Keys.syncAccount] = account
        if (!enabled) { it.remove(Keys.syncLocalHash); it.remove(Keys.syncRemoteVersion); it.remove(Keys.syncAccount) }
    }
    suspend fun recordSync(localHash: String, remoteVersion: String, at: Long) = store.edit {
        it[Keys.syncLocalHash] = localHash
        it[Keys.syncRemoteVersion] = remoteVersion
        it[Keys.syncLastAt] = at
        it.remove(Keys.syncError)
    }
    suspend fun recordSyncError(message: String) = store.edit { it[Keys.syncError] = message }

    // ---- Focus timer persistence (survives process death) ----

    val timerState: Flow<FocusTimerState?> = store.data.map { p ->
        val status = p[Keys.tStatus] ?: return@map null
        FocusTimerState(
            status = enumOr(status, TimerStatus.IDLE),
            phase = enumOr(p[Keys.tPhase], FocusPhase.FOCUS),
            durationMillis = p[Keys.tDuration] ?: 0L,
            remainingMillis = p[Keys.tRemaining] ?: 0L,
            endAtMillis = p[Keys.tEnd] ?: 0L,
            phaseStartedAtMillis = p[Keys.tStarted] ?: 0L,
            completedFocusInCycle = p[Keys.tCycle] ?: 0,
            taskId = p[Keys.tTask]?.takeIf { it > 0 },
            projectId = p[Keys.tProject]?.takeIf { it > 0 },
        )
    }.distinctUntilChanged()

    suspend fun saveTimerState(s: FocusTimerState) = store.edit {
        it[Keys.tStatus] = s.status.name
        it[Keys.tPhase] = s.phase.name
        it[Keys.tDuration] = s.durationMillis
        it[Keys.tRemaining] = s.remainingMillis
        it[Keys.tEnd] = s.endAtMillis
        it[Keys.tStarted] = s.phaseStartedAtMillis
        it[Keys.tCycle] = s.completedFocusInCycle
        it[Keys.tTask] = s.taskId ?: -1L
        it[Keys.tProject] = s.projectId ?: -1L
    }

    /** Raw snapshot of all preferences for backups. */
    suspend fun exportPairs(): Map<String, Any> = store.data.first().asMap()
        // Device-only state (timer, sync bookkeeping, first-run flags) never travels in backups or sync.
        .filterKeys { !it.name.startsWith("timer_") && !it.name.startsWith("sync_") && it.name !in setOf("notification_prompt_shown", "onboarding_done", "name_asked") }
        .mapKeys { it.key.name }
        .mapValues { (_, v) -> if (v is Set<*>) v.joinToString("|") else v }

    suspend fun importPairs(values: Map<String, Any?>) = store.edit { prefs ->
        fun str(k: String) = values[k]?.toString()
        fun int(k: String) = (values[k] as? Number)?.toInt() ?: str(k)?.toIntOrNull()
        fun bool(k: String) = values[k] as? Boolean ?: str(k)?.toBooleanStrictOrNull()
        fun long(k: String) = (values[k] as? Number)?.toLong() ?: str(k)?.toLongOrNull()
        str("theme_mode")?.let { prefs[Keys.theme] = it }
        str("user_name")?.let { prefs[Keys.userName] = it }
        str("label_icons")?.let { prefs[Keys.labelIcons] = it }
        str("widget_art")?.let { prefs[Keys.widgetArt] = it }
        bool("dynamic_color")?.let { prefs[Keys.dynamic] = it }
        int("accent_color")?.let { prefs[Keys.accent] = it }
        str("default_priority")?.let { prefs[Keys.defaultPriority] = it }
        long("default_list")?.let { prefs[Keys.defaultList] = it }
        int("week_start")?.let { prefs[Keys.weekStart] = it }
        str("date_format")?.let { prefs[Keys.dateFormat] = it }
        str("time_format")?.let { prefs[Keys.timeFormat] = it }
        bool("task_reminders")?.let { prefs[Keys.taskReminders] = it }
        bool("habit_reminders")?.let { prefs[Keys.habitReminders] = it }
        bool("focus_notifications")?.let { prefs[Keys.focusNotifications] = it }
        int("default_reminder_offset")?.let { prefs[Keys.reminderOffset] = it }
        int("default_reminder_time")?.let { prefs[Keys.reminderTime] = it }
        int("focus_minutes")?.let { prefs[Keys.focusMinutes] = it }
        int("short_break")?.let { prefs[Keys.shortBreak] = it }
        int("long_break")?.let { prefs[Keys.longBreak] = it }
        int("sessions_before_long")?.let { prefs[Keys.sessionsBeforeLong] = it }
        bool("auto_breaks")?.let { prefs[Keys.autoBreaks] = it }
        bool("auto_focus")?.let { prefs[Keys.autoFocus] = it }
        int("focus_goal")?.let { prefs[Keys.focusGoal] = it }
        str("dash_order")?.let { prefs[Keys.dashOrder] = it }
        str("dash_hidden")?.let { prefs[Keys.dashHidden] = it.split('|').filter(String::isNotBlank).toSet() }
        str("dash_collapsed")?.let { prefs[Keys.dashCollapsed] = it.split('|').filter(String::isNotBlank).toSet() }
        bool("group_by_priority")?.let { prefs[Keys.groupByPriority] = it }
    }
}


/** Background art for home-screen widgets: the choices and each widget's default. */
object WidgetArt {
    val styles = listOf("waves", "hills", "rings", "blobs", "leaves", "confetti")
    /** Widget key to label shown in Settings. */
    val widgets = listOf("tasks" to "Today's tasks", "habits" to "Habit streaks", "focus" to "Focus timer", "progress" to "Daily progress", "next_up" to "Next up")
    val defaults = mapOf("tasks" to "waves", "habits" to "leaves", "focus" to "rings", "progress" to "hills", "next_up" to "confetti")
}
