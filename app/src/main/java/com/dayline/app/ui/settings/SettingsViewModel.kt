package com.dayline.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayline.app.core.common.ReminderSync
import com.dayline.app.core.data.backup.BackupManager
import com.dayline.app.core.data.backup.BackupSummary
import com.dayline.app.core.data.prefs.SettingsRepository
import com.dayline.app.core.data.repository.ProjectRepository
import com.dayline.app.core.data.repository.TagRepository
import com.dayline.app.core.domain.FocusConfig
import com.dayline.app.core.model.AppSettings
import com.dayline.app.core.model.DashboardSection
import com.dayline.app.core.model.DateFormatOption
import com.dayline.app.core.model.Priority
import com.dayline.app.core.model.Tag
import com.dayline.app.core.model.TaskList
import com.dayline.app.core.model.ThemeMode
import com.dayline.app.core.model.TimeFormat
import com.dayline.app.focus.FocusTimerController
import com.dayline.app.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val reminderSync: ReminderSync,
    private val scheduler: ReminderScheduler,
    private val focus: FocusTimerController,
    private val backup: BackupManager,
    projects: ProjectRepository,
    private val tagRepo: TagRepository,
    val googleSync: com.dayline.app.sync.GoogleSync,
) : ViewModel() {
    val syncPrefs: StateFlow<SettingsRepository.SyncPrefs> = repo.sync.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsRepository.SyncPrefs())
    val syncStatus: StateFlow<com.dayline.app.sync.SyncStatus> = googleSync.status
    fun connectSync(result: com.google.android.gms.auth.api.identity.AuthorizationResult) = viewModelScope.launch { googleSync.connect(result) }
    fun syncNow() = viewModelScope.launch { googleSync.syncNow() }
    fun disconnectSync() = viewModelScope.launch { googleSync.disconnect() }
    fun switchSync(result: com.google.android.gms.auth.api.identity.AuthorizationResult) = viewModelScope.launch { googleSync.switchTo(result) }
    fun restoreConflict() = viewModelScope.launch { googleSync.restoreConflictCopy() }
    fun syncError(message: String) = viewModelScope.launch { repo.recordSyncError(message) }
    val settings: StateFlow<AppSettings?> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val lists: StateFlow<List<TaskList>> = projects.lists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val tags: StateFlow<List<Tag>> = tagRepo.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun canScheduleExact(): Boolean = scheduler.canScheduleExact()

    fun setTheme(m: ThemeMode) = viewModelScope.launch { repo.setThemeMode(m) }
    val widgetArt: StateFlow<Map<String, String>> = repo.widgetArt.stateIn(viewModelScope, SharingStarted.Eagerly, com.dayline.app.core.data.prefs.WidgetArt.defaults)
    fun setWidgetArt(widget: String, style: String) = viewModelScope.launch { repo.setWidgetArt(widget, style) }
    fun setName(n: String) = viewModelScope.launch { repo.setUserName(n) }
    fun setDynamic(b: Boolean) = viewModelScope.launch { repo.setDynamicColor(b) }
    fun setAccent(c: Int) = viewModelScope.launch { repo.setAccentColor(c) }
    fun setDefaultPriority(p: Priority) = viewModelScope.launch { repo.setDefaultPriority(p) }
    fun setDefaultList(id: Long?) = viewModelScope.launch { repo.setDefaultList(id) }
    fun setWeekStart(d: DayOfWeek) = viewModelScope.launch { repo.setWeekStart(d) }
    fun setDateFormat(f: DateFormatOption) = viewModelScope.launch { repo.setDateFormat(f) }
    fun setTimeFormat(f: TimeFormat) = viewModelScope.launch { repo.setTimeFormat(f) }

    fun setTaskReminders(b: Boolean) = viewModelScope.launch { repo.setTaskReminders(b); reminderSync.syncAll() }
    fun setHabitReminders(b: Boolean) = viewModelScope.launch { repo.setHabitReminders(b); reminderSync.syncAll() }
    fun setFocusNotifications(b: Boolean) = viewModelScope.launch { repo.setFocusNotifications(b) }
    fun setDefaultReminderOffset(m: Int?) = viewModelScope.launch { repo.setDefaultReminderOffset(m) }
    fun setDefaultReminderTime(t: LocalTime) = viewModelScope.launch { repo.setDefaultReminderTime(t); reminderSync.syncAll() }
    fun resync() = viewModelScope.launch { reminderSync.syncAll() }

    fun setFocus(c: FocusConfig) = viewModelScope.launch { repo.setFocusConfig(c); focus.applyConfig() }

    // Dashboard customization
    fun setGroupByPriority(b: Boolean) = viewModelScope.launch { repo.setGroupTodayByPriority(b) }
    fun setHidden(s: DashboardSection, hidden: Boolean) = viewModelScope.launch { repo.setSectionHidden(s, hidden) }
    fun move(s: DashboardSection, delta: Int) = viewModelScope.launch {
        val order = settings.value?.dashboardOrder?.toMutableList() ?: return@launch
        val i = order.indexOf(s)
        val j = (i + delta).coerceIn(0, order.lastIndex)
        if (i < 0 || i == j) return@launch
        order.add(j, order.removeAt(i))
        repo.setDashboardOrder(order)
    }
    fun setOrder(order: List<DashboardSection>) = viewModelScope.launch { repo.setDashboardOrder(order) }
    fun resetDashboard() = viewModelScope.launch { repo.resetDashboard() }

    // Tags
    suspend fun renameTag(tag: Tag, name: String): String? = try { tagRepo.rename(tag, name); null } catch (e: IllegalArgumentException) { e.message }
    fun deleteTag(id: Long) = viewModelScope.launch { tagRepo.delete(id) }

    // Backup
    suspend fun exportJson(): String = backup.exportJson()
    fun inspect(json: String): BackupSummary = backup.inspect(json)
    suspend fun importJson(json: String): BackupSummary = backup.importJson(json)
}
