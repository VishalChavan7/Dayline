package com.dayline.app.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel @Inject constructor(private val updates: UpdateManager) : ViewModel() {
    val state: StateFlow<UpdateState> = updates.state
    val currentVersion: String get() = updates.currentVersion

    /** Versions the user answered "Later" to, for as long as the app stays open. */
    var dismissed by mutableStateOf(emptySet<String>())
        private set

    init {
        updates.checkSoonIfStale()
    }

    fun check() = viewModelScope.launch { updates.checkAndDownload(notify = false) }
    fun later(version: String) { dismissed = dismissed + version }
    fun install(ready: UpdateState.Ready) { updates.install(ready.apk) }
}

/** One-line status for the "Check for updates" tile. */
fun updateCaption(state: UpdateState, current: String): String = when (state) {
    UpdateState.Idle -> "Version $current"
    UpdateState.Checking -> "Checking…"
    UpdateState.UpToDate -> "Version $current · up to date"
    is UpdateState.Downloading -> "Downloading ${state.release.version} · ${(state.progress * 100).toInt()}%"
    is UpdateState.Ready -> "Version ${state.release.version} is ready · tap to install"
    is UpdateState.Failed -> state.message
}

/**
 * "Update available" popup, shown over the app whenever a downloaded update is waiting and hasn't
 * been put off this session. [force] shows it even after "Later" (opened from the notification).
 */
@Composable
fun UpdatePrompt(vm: UpdateViewModel, force: Boolean, onForceHandled: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ready = state as? UpdateState.Ready ?: run { if (force) onForceHandled(); return }
    if (!force && ready.release.version in vm.dismissed) return
    val notes = UpdateManager.summary(ready.release.notes)
    AlertDialog(
        onDismissRequest = { vm.later(ready.release.version); onForceHandled() },
        icon = { Icon(Icons.Rounded.SystemUpdate, null) },
        title = { Text("Update available") },
        text = {
            Column {
                Text("Dayline ${ready.release.version} is downloaded and ready to install. You're on ${vm.currentVersion}.")
                if (notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text("What's new", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { Button(onClick = { vm.install(ready); vm.later(ready.release.version); onForceHandled() }) { Text("Update now") } },
        dismissButton = { TextButton(onClick = { vm.later(ready.release.version); onForceHandled() }) { Text("Later") } },
    )
}
