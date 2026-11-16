package com.dayline.app.ui.quickadd

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dayline.app.MainActivity
import com.dayline.app.core.model.TimeFormat
import com.dayline.app.ui.common.Formats
import com.dayline.app.ui.common.LocalFormats
import com.dayline.app.ui.motion.Haptics
import com.dayline.app.ui.navigation.MainViewModel
import com.dayline.app.ui.theme.DaylineTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * The "add task" sheet on its own, floating over the home screen. Opened from the widgets so adding
 * a task never launches the full app (and never plays the opening animation).
 */
@AndroidEntryPoint
class QuickAddActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the status/navigation bars exactly as they look on the home screen behind us.
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            QuickAddOverlay(
                onClose = { finishQuietly() },
                onOpenEditor = { route ->
                    startActivity(
                        Intent(this, MainActivity::class.java)
                            .putExtra(MainActivity.EXTRA_ROUTE, route)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                    )
                    finishQuietly()
                },
            )
        }
    }

    private fun finishQuietly() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}

@Composable
private fun QuickAddOverlay(onClose: () -> Unit, onOpenEditor: (String) -> Unit, vm: MainViewModel = hiltViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val s = settings ?: return
    val context = LocalContext.current.applicationContext
    DaylineTheme(themeMode = s.themeMode, dynamicColor = s.dynamicColor, accentColor = s.accentColor) {
        val is24 = when (s.timeFormat) {
            TimeFormat.SYSTEM -> DateFormat.is24HourFormat(context)
            TimeFormat.H24 -> true
            TimeFormat.H12 -> false
        }
        CompositionLocalProvider(LocalFormats provides Formats(s.dateFormat, is24, s.weekStart)) {
            QuickAddSheet(
                // Widgets show today's tasks, so tasks added from them land on today.
                defaults = QuickAddDefaults(date = java.time.LocalDate.now()),
                onDismiss = onClose,
                scrimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.12f),
                onOpenFullEditor = { d ->
                    onOpenEditor("task/0?date=${d.date?.toEpochDay() ?: Long.MIN_VALUE}&project=${d.projectId ?: -1}&list=${d.listId ?: -1}")
                },
                onAdded = { title ->
                    Haptics(context).success()
                    Handler(Looper.getMainLooper()).post { Toast.makeText(context, "Added “$title”", Toast.LENGTH_SHORT).show() }
                },
            )
        }
    }
}
