package com.dayline.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import com.dayline.app.BuildConfig
import com.dayline.app.R

/**
 * Widget-picker previews.
 *
 * - Android 15+: "generated" previews published at runtime (what Pixel and other launchers prefer).
 * - Android 12–14: the scalable `previewLayout` in each widget's XML.
 * - Anything else, or a launcher that can't draw layout previews: the `previewImage` picture.
 *
 * The system rate-limits [AppWidgetManager.setWidgetPreview], so previews are published once per app
 * version and retried on a later launch if any call was refused.
 */
object WidgetPreviews {
    val all: List<Pair<Class<*>, Int>> = listOf(
        TasksWidgetReceiver::class.java to R.layout.preview_tasks,
        HabitsWidgetReceiver::class.java to R.layout.preview_habits,
        FocusWidgetReceiver::class.java to R.layout.preview_focus,
        ProgressWidgetReceiver::class.java to R.layout.preview_progress,
        QuickAddWidgetReceiver::class.java to R.layout.preview_quickadd,
        NextUpWidgetReceiver::class.java to R.layout.preview_next_up,
        HabitFlowerWidgetReceiver::class.java to R.layout.preview_habit_flower,
    )

    private const val PREFS = "widget_previews"
    private const val KEY = "published_version"

    fun publish(context: Context) {
        if (Build.VERSION.SDK_INT < 35) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getInt(KEY, -1) == BuildConfig.VERSION_CODE) return
        val manager = AppWidgetManager.getInstance(context) ?: return
        val allOk = all.all { (receiver, layout) ->
            runCatching {
                manager.setWidgetPreview(
                    ComponentName(context, receiver),
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                    RemoteViews(context.packageName, layout),
                )
            }.getOrDefault(false)
        }
        if (allOk) prefs.edit().putInt(KEY, BuildConfig.VERSION_CODE).apply()
    }
}
