package com.dayline.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayline.app.core.data.prefs.SettingsRepository
import com.dayline.app.sync.GoogleSync
import com.dayline.app.sync.SyncOutcome
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Pull down on a main screen to sync with your other devices right away. */
@HiltViewModel
class SyncRefreshViewModel @Inject constructor(
    private val sync: GoogleSync,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 1)

    fun refresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        viewModelScope.launch {
            val started = System.currentTimeMillis()
            val message = if (!settings.sync.first().enabled) {
                "Turn on Google sync in Settings to keep your devices in step."
            } else when (sync.syncNow()) {
                SyncOutcome.DOWNLOADED, SyncOutcome.MERGED -> "Updated from your other device"
                SyncOutcome.UPLOADED -> "Your changes are synced"
                SyncOutcome.NEEDS_SIGN_IN -> "Sign in again in Settings → Google sync."
                SyncOutcome.FAILED -> (sync.status.value as? com.dayline.app.sync.SyncStatus.Failed)?.message ?: "Couldn't sync right now."
                else -> null
            }
            // Let the indicator finish its motion even when the sync is instant.
            val elapsed = System.currentTimeMillis() - started
            if (elapsed < 700) delay(700 - elapsed)
            _refreshing.value = false
            message?.let { messages.tryEmit(it) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncRefresh(enabled: Boolean, modifier: Modifier = Modifier, onMessage: suspend (String) -> Unit = {}, content: @Composable () -> Unit) {
    val vm: SyncRefreshViewModel = hiltViewModel()
    val refreshing by vm.refreshing.collectAsState()
    androidx.compose.runtime.LaunchedEffect(vm) { vm.messages.collect { onMessage(it) } }
    val state = rememberPullToRefreshState()
    // One layout for every screen (switching layouts would reset navigation); the gesture is just switched off.
    Box(modifier.pullToRefresh(isRefreshing = refreshing, state = state, enabled = enabled, onRefresh = vm::refresh)) {
        content()
        PullToRefreshDefaults.LoadingIndicator(
            state = state,
            isRefreshing = refreshing,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}
