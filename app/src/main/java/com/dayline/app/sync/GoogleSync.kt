package com.dayline.app.sync

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dayline.app.core.common.TimeProvider
import com.dayline.app.core.data.backup.BackupManager
import com.dayline.app.core.data.prefs.SettingsRepository
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** What the sync UI shows. */
sealed interface SyncStatus {
    data object Idle : SyncStatus
    data object Syncing : SyncStatus
    data class Done(val message: String) : SyncStatus
    data class Failed(val message: String) : SyncStatus
}

/** Outcome of one sync round. */
enum class SyncOutcome { UP_TO_DATE, UPLOADED, DOWNLOADED, MERGED, CONFLICT_KEPT_LOCAL, NEEDS_SIGN_IN, FAILED }

/**
 * Keeps all Dayline data in step across your devices through your own Google Drive.
 *
 * The whole database (tasks, projects, lists, habits, notes, focus history and settings) is stored as
 * one private file in Drive's hidden app folder — only Dayline can see it, and it doesn't use your
 * visible Drive. Each round merges this device with the cloud copy record by record (see [SyncMerge]),
 * so tasks, notes and habits changed on different devices between syncs are all kept, then uploads the
 * result. While the app is open it syncs every few seconds after a change and polls for the other
 * device's changes.
 */
@Singleton
class GoogleSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backup: BackupManager,
    private val settings: SettingsRepository,
    private val time: TimeProvider,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    val authorizationRequest: AuthorizationRequest = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_APPDATA), Scope("email")))
        .build()

    /** Starts sign-in from the Settings screen. Returns a result that may need the account picker. */
    fun authorize(activity: Activity): Task<AuthorizationResult> = Identity.getAuthorizationClient(activity).authorize(authorizationRequest)

    /** Sign-in for a specific account the user picked (used to switch accounts). */
    fun authorize(activity: Activity, account: android.accounts.Account): Task<AuthorizationResult> =
        Identity.getAuthorizationClient(activity).authorize(
            AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_APPDATA), Scope("email")))
                .setAccount(account)
                .build(),
        )

    /** The system "Choose an account" screen, limited to Google accounts. */
    fun chooseAccountIntent(current: String?): Intent = android.accounts.AccountManager.newChooseAccountIntent(
        current?.takeIf { it.contains('@') }?.let { android.accounts.Account(it, GOOGLE_ACCOUNT_TYPE) },
        null, arrayOf(GOOGLE_ACCOUNT_TYPE), "Choose the Google account to sync Dayline with", null, null, null,
    )

    fun resultFromIntent(data: Intent?): AuthorizationResult = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)

    /** Called once the user has granted access in the picker. */
    suspend fun connect(result: AuthorizationResult): SyncOutcome {
        val token = result.accessToken ?: return fail("Google didn't return access. Please try again.")
        val email = runCatching { withContext(Dispatchers.IO) { userEmail(token) } }.getOrNull().orEmpty()
        settings.setSyncEnabled(true, email.ifBlank { "Google account" })
        return syncNow(token)
    }

    /**
     * Stops syncing and gives Dayline's Drive access back to Google, so the next "Connect" can pick
     * any account. The copy already in Drive is left where it is.
     */
    suspend fun disconnect() {
        val account = settings.sync.first().account
        SyncScheduler.cancel(context)
        settings.setSyncEnabled(false)
        baseFile().delete()
        _status.value = SyncStatus.Idle
        if (account.contains('@')) {
            runCatching {
                Identity.getAuthorizationClient(context).revokeAccess(
                    com.google.android.gms.auth.api.identity.RevokeAccessRequest.builder()
                        .setAccount(android.accounts.Account(account, GOOGLE_ACCOUNT_TYPE))
                        .setScopes(listOf(Scope(DRIVE_APPDATA), Scope("email")))
                        .build(),
                ).await()
            }
        }
    }

    /** Switches to another account: forget the old one's sync state, then connect the new one. */
    suspend fun switchTo(result: AuthorizationResult): SyncOutcome {
        disconnect()
        return connect(result)
    }

    /** Silent token for background syncs; null when the user must sign in again. */
    private suspend fun silentToken(): String? = try {
        val r = Identity.getAuthorizationClient(context).authorize(authorizationRequest).await()
        if (r.hasResolution()) null else r.accessToken
    } catch (e: ApiException) {
        throw SyncException(explain(e))
    }

    suspend fun syncNow(token: String? = null): SyncOutcome = mutex.withLock {
        val prefs = settings.sync.first()
        if (!prefs.enabled) return@withLock SyncOutcome.UP_TO_DATE
        _status.value = SyncStatus.Syncing
        try {
            val access = token ?: silentToken() ?: run {
                settings.recordSyncError("Sign in again to keep syncing.")
                _status.value = SyncStatus.Failed("Sign in again to keep syncing.")
                return@withLock SyncOutcome.NEEDS_SIGN_IN
            }
            val outcome = withContext(Dispatchers.IO) { round(access, prefs) }
            _status.value = SyncStatus.Done(
                when (outcome) {
                    SyncOutcome.UPLOADED -> "Backed up to Google Drive"
                    SyncOutcome.DOWNLOADED -> "Updated from your other device"
                    SyncOutcome.MERGED -> "Merged changes from your other device"
                    SyncOutcome.CONFLICT_KEPT_LOCAL -> "Both devices changed — kept this one; the other copy is saved"
                    else -> "Up to date"
                },
            )
            outcome
        } catch (e: Exception) {
            val msg = (e as? SyncException)?.message ?: (e as? ApiException)?.let(::explain) ?: "Sync failed: ${e.message ?: e.javaClass.simpleName}"
            settings.recordSyncError(msg)
            _status.value = SyncStatus.Failed(msg)
            SyncOutcome.FAILED
        }
    }

    private suspend fun round(token: String, prefs: SettingsRepository.SyncPrefs): SyncOutcome {
        val drive = Drive(token)
        val local = backup.exportJson()
        val localHash = fingerprint(local)
        val remote = drive.find(FILE_NAME)
        val now = time.nowMillis()
        if (remote == null) {
            val created = drive.create(FILE_NAME, local)
            baseFile().writeText(local)
            settings.recordSync(localHash, created.version, now)
            return SyncOutcome.UPLOADED
        }
        val localChanged = localHash != prefs.localHash
        val remoteChanged = remote.version != prefs.remoteVersion
        if (!localChanged && !remoteChanged) {
            settings.recordSync(localHash, remote.version, now)
            return SyncOutcome.UP_TO_DATE
        }
        // Merge record by record against the last synced copy, so edits from both devices are kept.
        val base = baseFile().takeIf { it.exists() && prefs.remoteVersion.isNotBlank() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
        val remoteContent = if (remoteChanged || base == null) drive.download(remote.id) else base.toString()
        val localJson = JSONObject(local)
        val remoteJson = runCatching { JSONObject(remoteContent) }.getOrElse { throw SyncException("The copy in Google Drive is damaged.") }
        val merged = SyncMerge.merge(base, localJson, remoteJson, time.now().toString())
        val mergedCanon = SyncMerge.canon(withoutStamp(merged))
        var outcome = SyncOutcome.UP_TO_DATE
        if (mergedCanon != SyncMerge.canon(withoutStamp(localJson))) {
            backup.importJson(merged.toString(), keepSafetyCopy = false)
            outcome = SyncOutcome.DOWNLOADED
        }
        var version = remote.version
        if (mergedCanon != SyncMerge.canon(withoutStamp(remoteJson))) {
            version = drive.update(remote.id, merged.toString(2)).version
            outcome = if (outcome == SyncOutcome.DOWNLOADED) SyncOutcome.MERGED else SyncOutcome.UPLOADED
        }
        baseFile().writeText(merged.toString())
        settings.recordSync(fingerprint(backup.exportJson()), version, now)
        return outcome
    }

    private fun withoutStamp(o: JSONObject) = JSONObject(o.toString()).apply { remove("exportedAt") }

    /** The last copy both devices agreed on (the merge's common base). */
    private fun baseFile() = File(context.filesDir, "sync-base.json")

    /** The cloud copy set aside during the last conflict, if any. */
    fun conflictCopy(): File? = conflictFile().takeIf { it.exists() && it.length() > 0 }

    suspend fun restoreConflictCopy() {
        val f = conflictCopy() ?: return
        backup.importJson(f.readText())
        f.delete()
        syncNow()
    }

    private fun conflictFile() = File(context.filesDir, "sync-conflict-copy.json")

    private fun fingerprint(json: String): String {
        val canonical = runCatching { JSONObject(json).apply { remove("exportedAt") }.toString() }.getOrDefault(json)
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private suspend fun fail(message: String): SyncOutcome {
        settings.recordSyncError(message)
        _status.value = SyncStatus.Failed(message)
        return SyncOutcome.FAILED
    }

    private fun explain(e: ApiException): String = when (e.statusCode) {
        CommonStatusCodes.DEVELOPER_ERROR ->
            "Google sign-in isn't available in this version of Dayline. Please update the app."
        CommonStatusCodes.NETWORK_ERROR -> "No connection — will try again later."
        CommonStatusCodes.SIGN_IN_REQUIRED -> "Sign in again to keep syncing."
        else -> "Google sign-in failed (${e.statusCode})."
    }

    private fun userEmail(token: String): String {
        val c = (URL("https://www.googleapis.com/oauth2/v3/userinfo").openConnection() as HttpURLConnection)
        c.setRequestProperty("Authorization", "Bearer $token")
        return c.use { JSONObject(it.inputStream.bufferedReader().readText()).optString("email") }
    }

    class SyncException(message: String) : Exception(message)

    /** Minimal Drive v3 client for one file in the private app-data folder. */
    private class Drive(private val token: String) {
        data class RemoteFile(val id: String, val version: String)

        fun find(name: String): RemoteFile? {
            val q = URLEncoder.encode("name = '$name' and trashed = false", "UTF-8")
            val json = request("GET", "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id,version,modifiedTime)")
            val files = JSONObject(json).optJSONArray("files") ?: return null
            if (files.length() == 0) return null
            val f = files.getJSONObject(0)
            return RemoteFile(f.getString("id"), f.optString("version", f.optString("modifiedTime")))
        }

        fun download(id: String): String = request("GET", "https://www.googleapis.com/drive/v3/files/$id?alt=media")

        fun create(name: String, content: String): RemoteFile {
            val boundary = "dayline${System.nanoTime()}"
            val meta = JSONObject().put("name", name).put("parents", org.json.JSONArray().put("appDataFolder")).toString()
            val body = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n" +
                "--$boundary\r\nContent-Type: application/json\r\n\r\n$content\r\n--$boundary--"
            val json = request("POST", "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id,version", body, "multipart/related; boundary=$boundary")
            val o = JSONObject(json)
            return RemoteFile(o.getString("id"), o.optString("version"))
        }

        fun update(id: String, content: String): RemoteFile {
            val json = request("PATCH", "https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media&fields=id,version", content, "application/json")
            val o = JSONObject(json)
            return RemoteFile(o.getString("id"), o.optString("version"))
        }

        private fun request(method: String, url: String, body: String? = null, contentType: String? = null): String {
            val c = URL(url).openConnection() as HttpURLConnection
            if (method == "PATCH") {
                // HttpURLConnection has no PATCH; Google APIs accept the override header.
                c.requestMethod = "POST"
                c.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", contentType ?: "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
            }
            return c.use { conn ->
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                    throw SyncException(
                        when (code) {
                            401 -> "Google sign-in expired — reconnect in Settings."
                            403 -> if (err.contains("accessNotConfigured") || err.contains("has not been used")) "Google Drive sync is temporarily unavailable. Please try again later." else "Google Drive refused access ($code)."
                            else -> "Google Drive error $code."
                        },
                    )
                }
                conn.inputStream.bufferedReader().readText()
            }
        }
    }

    companion object {
        const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        private const val FILE_NAME = "dayline-sync.json"
    }
}

private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T = try { block(this) } finally { disconnect() }

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

/** Background sync: hourly, plus a quick run shortly after you change something. */
object SyncScheduler {
    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS).setConstraints(network).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("google-sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }

    fun soon(context: Context, delaySeconds: Long = 20) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(network)
            .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("google-sync-soon", ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork("google-sync")
        WorkManager.getInstance(context).cancelUniqueWork("google-sync-soon")
    }
}

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val sync: GoogleSync,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (sync.syncNow()) {
        SyncOutcome.FAILED -> if (runAttemptCount < 3) Result.retry() else Result.failure()
        else -> Result.success()
    }
}
