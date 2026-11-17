package com.dayline.app.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
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
    val apkUrl: String,
    val apkSize: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    /** Downloaded, verified and ready: one tap opens Android's installer. */
    data class Ready(val release: ReleaseInfo, val apk: File) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Keeps Dayline up to date from the project's GitHub Releases.
 *
 * Checks the latest release, downloads its APK in the background, verifies that it is Dayline signed
 * with the same key and newer than this install, then offers a one-tap update (notification + popup).
 * Android always shows its own installer confirmation; nothing installs silently.
 */
@Singleton
class UpdateManager @Inject constructor(@ApplicationContext private val context: Context) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private val mutex = Mutex()
    private val prefs get() = context.getSharedPreferences("updates", Context.MODE_PRIVATE)

    val currentVersion: String = BuildConfig.VERSION_NAME.substringBefore('-')

    /** Debug builds have a different package id, so a release APK can't update them. */
    val supported: Boolean get() = !BuildConfig.DEBUG

    init {
        restoreReady()
    }

    /**
     * Looks for a newer release and downloads it. [notify] posts a notification when an update is
     * ready (used by the background check); the in-app popup handles the foreground case.
     */
    suspend fun checkAndDownload(notify: Boolean): UpdateState = mutex.withLock {
        (_state.value as? UpdateState.Ready)?.let { if (it.apk.exists()) { if (notify) notifyReady(it.release); return@withLock it } }
        _state.value = UpdateState.Checking
        val result = runCatching {
            val release = withContext(Dispatchers.IO) { fetchLatest() }
            prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            if (release == null || !isNewer(release.version, currentVersion)) {
                cleanup()
                UpdateState.UpToDate
            } else if (!supported) {
                UpdateState.Failed("Version ${release.version} is out. Developer builds can't update themselves; install it from GitHub.")
            } else {
                val apk = withContext(Dispatchers.IO) { download(release) }
                verify(apk)
                save(release, apk)
                if (notify) notifyReady(release)
                UpdateState.Ready(release, apk)
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

    /** Intent for Android's installer with the downloaded APK (one tap to "Update"). */
    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun install(apk: File) = runCatching { context.startActivity(installIntent(apk)) }.isSuccess

    // ---------------------------------------------------------------- GitHub

    private fun fetchLatest(): ReleaseInfo? {
        val json = get("https://api.github.com/repos/$REPO/releases/latest")
        val root = JSONObject(json)
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val assets = root.optJSONArray("assets") ?: return null
        val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) } ?: return null
        val tag = root.optString("tag_name").removePrefix("v").removePrefix("V")
        return ReleaseInfo(
            version = tag,
            title = root.optString("name").ifBlank { "Dayline $tag" },
            notes = root.optString("body"),
            pageUrl = root.optString("html_url"),
            apkUrl = apk.getString("browser_download_url"),
            apkSize = apk.optLong("size", -1L),
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

    private fun download(release: ReleaseInfo): File {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(dir, "Dayline-${release.version}.apk")
        if (target.exists() && (release.apkSize <= 0 || target.length() == release.apkSize)) return target
        dir.listFiles()?.forEach { it.delete() }
        val part = File(dir, target.name + ".part")
        // GitHub redirects asset downloads (https → https) to its storage host; followed automatically.
        val c = (URL(release.apkUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("User-Agent", "Dayline/$currentVersion")
        }
        try {
            if (c.responseCode !in 200..299) error("Download failed (${c.responseCode}).")
            val total = release.apkSize.takeIf { it > 0 } ?: c.contentLengthLong
            c.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        read += n
                        if (total > 0) _state.value = UpdateState.Downloading(release, (read.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally { c.disconnect() }
        if (release.apkSize > 0 && part.length() != release.apkSize) { part.delete(); error("The download was incomplete. Try again.") }
        if (!part.renameTo(target)) error("Couldn't save the update.")
        return target
    }

    /** The APK must be Dayline, newer than this install, and signed with the same key. */
    private fun verify(apk: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.path, flags)
        val installed = pm.getPackageInfo(context.packageName, flags)
        val ok = archive != null && archive.packageName == context.packageName &&
            versionCode(archive) > versionCode(installed) && signers(archive) == signers(installed) && signers(installed).isNotEmpty()
        if (!ok) { apk.delete(); error("The downloaded update couldn't be verified, so it was discarded.") }
    }

    private fun versionCode(p: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong()

    private fun signers(p: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) p.signingInfo?.apkContentsSigners else @Suppress("DEPRECATION") p.signatures
        return sigs.orEmpty().map { java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { b -> "%02x".format(b) } }.toSet()
    }

    // ---------------------------------------------------------------- persistence

    private fun save(release: ReleaseInfo, apk: File) {
        prefs.edit()
            .putString(KEY_VERSION, release.version).putString(KEY_TITLE, release.title).putString(KEY_NOTES, release.notes)
            .putString(KEY_PAGE, release.pageUrl).putString(KEY_URL, release.apkUrl).putLong(KEY_SIZE, release.apkSize)
            .putString(KEY_APK, apk.path)
            .apply()
    }

    private fun restoreReady() {
        val version = prefs.getString(KEY_VERSION, null) ?: return
        val apk = File(prefs.getString(KEY_APK, null) ?: return)
        if (!isNewer(version, currentVersion) || !apk.exists()) { cleanup(); return }
        _state.value = UpdateState.Ready(
            ReleaseInfo(
                version, prefs.getString(KEY_TITLE, null) ?: "Dayline $version", prefs.getString(KEY_NOTES, null).orEmpty(),
                prefs.getString(KEY_PAGE, null).orEmpty(), prefs.getString(KEY_URL, null).orEmpty(), prefs.getLong(KEY_SIZE, -1L),
            ),
            apk,
        )
    }

    /** Forget a downloaded update once this install is as new (e.g. right after updating). */
    private fun cleanup() {
        File(context.cacheDir, "updates").listFiles()?.forEach { it.delete() }
        prefs.edit().remove(KEY_VERSION).remove(KEY_APK).remove(KEY_TITLE).remove(KEY_NOTES).remove(KEY_PAGE).remove(KEY_URL).remove(KEY_SIZE).apply()
    }

    // ---------------------------------------------------------------- notification

    private fun notifyReady(release: ReleaseInfo) {
        if (prefs.getString(KEY_NOTIFIED, null) == release.version) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a new version of Dayline is ready to install"
            },
        )
        val open = PendingIntent.getActivity(
            context, 7001,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, ROUTE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val apk = File(prefs.getString(KEY_APK, null) ?: return)
        val install = runCatching {
            PendingIntent.getActivity(context, 7002, installIntent(apk), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }.getOrNull() ?: open
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_dayline)
            .setContentTitle("Dayline ${release.version} is ready")
            .setContentText("Tap Update to install the new version.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary(release.notes).ifBlank { "Tap Update to install the new version." }))
            .setContentIntent(open)
            .addAction(0, "Update", install)
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
        private const val KEY_URL = "url"
        private const val KEY_SIZE = "size"
        private const val KEY_APK = "apk"
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
        return when (updates.checkAndDownload(notify = true)) {
            is UpdateState.Failed -> if (runAttemptCount < 2) Result.retry() else Result.success()
            else -> Result.success()
        }
    }
}
