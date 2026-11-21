package com.dayline.app.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dayline.app.BuildConfig
import com.dayline.app.MainActivity
import com.dayline.app.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A release published on GitHub. */
data class ReleaseInfo(
    val version: String,
    val title: String,
    val notes: String,
    val pageUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    /** A newer release is out; Update opens its GitHub page to download it. */
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Tells you when a newer Dayline is published on the project's GitHub Releases.
 *
 * It only reads the release list. Dayline never downloads or installs app files itself: Update opens
 * the release page in your browser, and you download and install the new version from there.
 */
@Singleton
class UpdateManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val mutex = Mutex()
    private val prefs get() = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    val currentVersion: String = BuildConfig.VERSION_NAME.substringBefore('-')

    /** Debug builds have a different package id, so they don't look for updates in the background. */
    val supported: Boolean get() = !BuildConfig.DEBUG

    init {
        // Files left by versions that downloaded updates themselves.
        File(context.cacheDir, "updates").deleteRecursively()
        restoreAvailable()
    }

    /**
     * Looks for a newer release. [notify] posts a notification when one is out (used by the
     * background check); the in-app popup handles the foreground case.
     */
    suspend fun check(notify: Boolean): UpdateState = mutex.withLock {
        _state.value = UpdateState.Checking
        val result = runCatching {
            val release = withContext(Dispatchers.IO) { fetchLatest() }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            if (release == null || !isNewer(release.version, currentVersion)) {
                forget()
                UpdateState.UpToDate
            } else {
                save(release)
                if (notify) notifyAvailable(release)
                UpdateState.Available(release)
            }
        }.getOrElse { e -> UpdateState.Failed(e.message ?: "Couldn't check for updates.") }
        _state.value = result
        result
    }

    /** Runs a background check if the last one was more than [olderThanHours] ago. */
    fun checkSoonIfStale(olderThanHours: Long = 6) {
        if (!supported) return
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last > TimeUnit.HOURS.toMillis(olderThanHours)) UpdateScheduler.now(context)
    }

    /** Opens the release's page in the browser, where the new APK can be downloaded. */
    fun pageIntent(release: ReleaseInfo): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse(release.pageUrl.ifBlank { "https://github.com/$REPO/releases/latest" }))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun open(release: ReleaseInfo) = runCatching { context.startActivity(pageIntent(release)) }.isSuccess

    // ---------------------------------------------------------------- GitHub

    private fun fetchLatest(): ReleaseInfo? {
        val json = get("https://api.github.com/repos/$REPO/releases/latest")
        val root = JSONObject(json)
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val tag = root.optString("tag_name").removePrefix("v").removePrefix("V")
        if (tag.isBlank()) return null
        return ReleaseInfo(
            version = tag,
            title = root.optString("name").ifBlank { "Dayline $tag" },
            notes = root.optString("body"),
            pageUrl = root.optString("html_url"),
        )
    }

    private fun get(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Dayline/$currentVersion")
        }
        try {
            if (c.responseCode == 404) return "{}"
            if (c.responseCode !in 200..299) error("GitHub answered ${c.responseCode}. Try again later.")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    // ---------------------------------------------------------------- persistence

    private fun save(release: ReleaseInfo) {
        prefs.edit()
            .putString(KEY_VERSION, release.version).putString(KEY_TITLE, release.title).putString(KEY_NOTES, release.notes)
            .putString(KEY_PAGE, release.pageUrl)
            .apply()
    }

    private fun restoreAvailable() {
        val version = prefs.getString(KEY_VERSION, null) ?: return
        if (!isNewer(version, currentVersion)) { forget(); return }
        _state.value = UpdateState.Available(
            ReleaseInfo(
                version, prefs.getString(KEY_TITLE, null) ?: "Dayline $version", prefs.getString(KEY_NOTES, null).orEmpty(),
                prefs.getString(KEY_PAGE, null).orEmpty(),
            ),
        )
    }

    /** Forget a found update once this install is as new (e.g. right after updating). */
    private fun forget() {
        prefs.edit().remove(KEY_VERSION).remove(KEY_TITLE).remove(KEY_NOTES).remove(KEY_PAGE)
            .remove("apk").remove("url").remove("size").apply()
    }

    // ---------------------------------------------------------------- notification

    private fun notifyAvailable(release: ReleaseInfo) {
        if (prefs.getString(KEY_NOTIFIED, null) == release.version) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a new version of Dayline is out"
            },
        )
        val open = PendingIntent.getActivity(
            context, 7001,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, ROUTE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val page = PendingIntent.getActivity(context, 7002, pageIntent(release), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_dayline)
            .setContentTitle("Dayline ${release.version} is out")
            .setContentText("Tap Update to download it from GitHub.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary(release.notes).ifBlank { "Tap Update to download it from GitHub." }))
            .setContentIntent(open)
            .addAction(0, "Update", page)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
        prefs.edit().putString(KEY_NOTIFIED, release.version).apply()
    }

    companion object {
        const val REPO = "VishalChavan7/Dayline"
        const val ROUTE = "update"
        const val CHANNEL = "app_updates"
        private const val NOTIFICATION_ID = 7000
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_VERSION = "version"
        private const val KEY_TITLE = "title"
        private const val KEY_NOTES = "notes"
        private const val KEY_PAGE = "page"
        private const val KEY_NOTIFIED = "notified"

        /** "1.10.0" > "1.9.2"; missing parts count as 0. */
        fun isNewer(candidate: String, current: String): Boolean {
            fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val a = parts(candidate); val b = parts(current)
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }; val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        /** Release notes as a few plain lines (no Markdown symbols, no install boilerplate). */
        fun summary(markdown: String, maxLines: Int = 6): String = markdown.lines()
            .map { it.trim() }
            .takeWhile { !it.startsWith("## Install", ignoreCase = true) }
            .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("SHA-256", ignoreCase = true) && !it.startsWith("**Full changelog", ignoreCase = true) }
            .map { it.removePrefix("- ").removePrefix("* ").replace("**", "").replace("`", "") }
            .take(maxLines)
            .joinToString("\n") { if (it.length > 140) it.take(137) + "…" else it }
    }
}

/** Background checks: twice a day on any network, plus an immediate one on demand. */
object UpdateScheduler {
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(12, TimeUnit.HOURS).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("update-check", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun now(context: Context) {
        val request = OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniqueWork("update-check-now", ExistingWorkPolicy.KEEP, request)
    }
}

@HiltWorker
class UpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val updates: UpdateManager,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!updates.supported) return Result.success()
        return when (updates.check(notify = true)) {
            is UpdateState.Failed -> if (runAttemptCount < 2) Result.retry() else Result.success()
            else -> Result.success()
        }
    }
}
