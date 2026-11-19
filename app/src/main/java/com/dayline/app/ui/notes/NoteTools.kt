package com.dayline.app.ui.notes

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.dayline.app.core.domain.NoteCodec
import com.dayline.app.core.domain.NoteFormat
import com.dayline.app.core.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/** Sharing, exporting, printing and importing notes. */
object NoteExport {
    enum class Format(val label: String, val ext: String, val mime: String) {
        MARKDOWN("Markdown", "md", "text/markdown"),
        HTML("HTML", "html", "text/html"),
        TEXT("Plain text", "txt", "text/plain"),
    }

    fun fileName(n: Note): String {
        val t = n.title.ifBlank { NoteCodec.plainText(n.body).lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }
            .replace(Regex("[/\\\\:*?\"<>|\\n]"), "-").trim().take(80)
        return t.ifEmpty { "Note" }
    }

    fun render(n: Note, f: Format): String = when (f) {
        Format.MARKDOWN -> NoteFormat.toMarkdown(n.title, n.body)
        Format.HTML -> NoteFormat.toHtmlPage(n.title, n.body)
        Format.TEXT -> (if (n.title.isBlank()) "" else n.title + "\n\n") + NoteCodec.plainText(n.body) + "\n"
    }

    /** Opens the share sheet with the note as a file (Drive, Files, mail, …). */
    suspend fun share(context: Context, n: Note, f: Format) {
        val file = withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            File(dir, fileName(n) + "." + f.ext).apply { writeText(render(n, f)) }
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = f.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, n.title.ifBlank { "Note" })
            if (f != Format.HTML) putExtra(Intent.EXTRA_TEXT, render(n, f))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share note").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Android's print dialog, where "Save as PDF" makes a PDF. */
    fun print(context: Context, n: Note) {
        val activity = context.findActivity() ?: return
        val web = WebView(activity)
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                val pm = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val name = fileName(n)
                pm.print(name, view.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
            }
        }
        web.settings.javaScriptEnabled = false
        web.loadDataWithBaseURL(null, NoteFormat.toHtmlPage(n.title, n.body), "text/html", "utf-8", null)
    }

    /** A note from a Markdown, text or HTML file, or null when it can't be read. */
    fun noteFrom(name: String, text: String): Note? {
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val note = when (ext) {
            "md", "markdown" -> NoteFormat.fromMarkdown(text, base).let { Note(title = it.title, body = it.html) }
            "html", "htm" -> {
                var b = text
                Regex("<body[^>]*>", RegexOption.IGNORE_CASE).find(b)?.let { b = b.substring(it.range.last + 1) }
                b = b.substringBefore("</body>").replace(Regex("<(script|style)[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("\\son\\w+\\s*=\\s*\"[^\"]*\"", RegexOption.IGNORE_CASE), "")
                Note(title = base, body = b.trim())
            }
            else -> Note(title = base, body = text.replace("\r\n", "\n").split('\n').joinToString("") { if (it.isEmpty()) "<br>" else "<p>" + NoteCodec.escape(it) + "</p>" })
        }
        return note.takeIf { it.title.isNotBlank() || NoteCodec.plainText(it.body).isNotBlank() }
    }

    suspend fun read(context: Context, uri: Uri): Pair<String, String>? = withContext(Dispatchers.IO) {
        runCatching {
            val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: uri.lastPathSegment.orEmpty()
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@runCatching null
            if (bytes.size > 20_000_000) return@runCatching null
            name to bytes.toString(Charsets.UTF_8)
        }.getOrNull()
    }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/**
 * Fingerprint, face or screen lock before a locked note shows its text. Locking hides a note in
 * Dayline; it isn't encryption. A phone with no screen lock just opens it.
 */
@Composable
fun rememberDeviceUnlock(onResult: (Boolean) -> Unit): (String) -> Unit {
    val context = LocalContext.current
    val result = rememberUpdatedState(onResult)
    val legacy = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        result.value(r.resultCode == Activity.RESULT_OK)
    }
    return remember(context) {
        { reason: String ->
            val km = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            when {
                !km.isDeviceSecure -> result.value(true)
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    val prompt = android.hardware.biometrics.BiometricPrompt.Builder(context)
                        .setTitle("Unlock note")
                        .setSubtitle(reason)
                        .setAllowedAuthenticators(
                            android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                                android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
                        )
                        .build()
                    val main: Executor = context.mainExecutor
                    prompt.authenticate(CancellationSignal(), main, object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(r: android.hardware.biometrics.BiometricPrompt.AuthenticationResult?) = result.value(true)
                        override fun onAuthenticationError(code: Int, msg: CharSequence?) = result.value(false)
                    })
                }
                else -> {
                    @Suppress("DEPRECATION")
                    val intent = km.createConfirmDeviceCredentialIntent("Unlock note", reason)
                    if (intent == null) result.value(true) else legacy.launch(intent)
                }
            }
        }
    }
}

/** Words the phone's spell checker doesn't know, with its suggestions. */
object NoteSpelling {
    data class Issue(val word: String, val suggestions: List<String>, val count: Int)

    /** null when the phone has no spell checker turned on. */
    suspend fun check(context: Context, text: String): List<Issue>? {
        val tsm = context.getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager ?: return null
        val words = Regex("[\\p{L}][\\p{L}'’]*").findAll(text).map { it.value }.filter { it.length > 1 && !it.any(Char::isDigit) }.toList()
        if (words.isEmpty()) return emptyList()
        val unique = words.distinct().take(400)
        val counts = words.groupingBy { it }.eachCount()
        val out = mutableListOf<Issue>()
        for (chunk in unique.chunked(40)) {
            val res = suggestions(context, tsm, chunk) ?: return if (out.isEmpty()) null else out
            chunk.zip(res).forEach { (w, info) ->
                val looks = (info.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO) != 0
                val known = (info.suggestionsAttributes and SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) != 0
                if (looks && !known) out += Issue(w, (0 until info.suggestionsCount).map { info.getSuggestionAt(it) }.filter { it != w }.take(4), counts[w] ?: 1)
            }
        }
        return out
    }

    private suspend fun suggestions(context: Context, tsm: TextServicesManager, words: List<String>): List<SuggestionsInfo>? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                var session: SpellCheckerSession? = null
                val listener = object : SpellCheckerSession.SpellCheckerSessionListener {
                    @Deprecated("Deprecated in Java")
                    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {
                        session?.close()
                        if (cont.isActive) cont.resume(results?.toList())
                    }
                    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
                        session?.close()
                        if (cont.isActive) cont.resume(null)
                    }
                }
                session = tsm.newSpellCheckerSession(null, Locale.getDefault(), listener, true)
                if (session == null) { cont.resume(null); return@suspendCancellableCoroutine }
                @Suppress("DEPRECATION")
                session.getSuggestions(words.map { TextInfo(it) }.toTypedArray(), 5, true)
                cont.invokeOnCancellation { session.close() }
            }
        }
}
