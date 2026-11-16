package com.dayline.app.ui.notes

import androidx.compose.material.icons.rounded.Edit
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.dayline.app.core.domain.NoteMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Where note pictures, drawings and recordings are kept (private app storage). */
object NoteMediaStore {
    fun dir(context: Context): File = File(context.filesDir, "note-media").apply { mkdirs() }
    fun file(context: Context, name: String): File = File(dir(context), name)

    /** Copies a picked picture in, scaled down to at most 2048 px on the long side. */
    suspend fun importImage(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 2048) sample *= 2
            val bmp = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            } ?: return@runCatching null
            val rotated = rotateForExif(context, uri, bmp)
            val name = "img-${UUID.randomUUID()}.jpg"
            file(context, name).outputStream().use { rotated.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            name
        }.getOrNull()
    }

    private fun rotateForExif(context: Context, uri: Uri, bmp: Bitmap): Bitmap {
        val deg = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
                    6 -> 90f; 3 -> 180f; 8 -> 270f; else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
        if (deg == 0f) return bmp
        val m = android.graphics.Matrix().apply { postRotate(deg) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    suspend fun saveDrawing(context: Context, bmp: Bitmap, replace: String? = null): String = withContext(Dispatchers.IO) {
        val name = replace ?: "draw-${UUID.randomUUID()}.png"
        file(context, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        BitmapCache.evict(name)
        name
    }

    fun newAudio(context: Context): File = file(context, "voice-${UUID.randomUUID()}.m4a")
}

/** Small decoded-bitmap cache so the notes grid scrolls smoothly. */
private object BitmapCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    fun get(key: String): Bitmap? = cache.get(key)
    fun put(key: String, b: Bitmap) { cache.put(key, b) }
    fun evict(name: String) { cache.snapshot().keys.filter { it.startsWith("$name@") }.forEach { cache.remove(it) } }
}

@Composable
fun rememberNoteBitmap(name: String, maxPx: Int): Bitmap? {
    val context = LocalContext.current
    val key = "$name@$maxPx"
    return produceState(BitmapCache.get(key), key) {
        if (value != null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val f = NoteMediaStore.file(context, name)
                if (!f.exists()) return@runCatching null
                val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(f.path, b)
                var s = 1
                while (maxOf(b.outWidth, b.outHeight) / (s * 2) >= maxPx) s *= 2
                BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
            }.getOrNull()?.also { BitmapCache.put(key, it) }
        }
    }.value
}

/** Pictures and drawings full-width, voice notes as players — shown at the top of a note. */
@Composable
fun NoteMediaBlock(media: List<NoteMedia>, onRemove: (NoteMedia) -> Unit, onEditDrawing: (NoteMedia) -> Unit, modifier: Modifier = Modifier, onRename: (NoteMedia) -> Unit = {}) {
    if (media.isEmpty()) return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        media.filter { it.type != NoteMedia.Type.AUDIO }.forEach { m ->
            Box {
                val bmp = rememberNoteBitmap(m.file, 1600)
                if (bmp != null) {
                    Image(
                        bmp.asImageBitmap(), null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(if (m.type == NoteMedia.Type.DRAWING) Color.White else Color.Transparent)
                            .clickable(enabled = m.type == NoteMedia.Type.DRAWING, onClickLabel = "Edit drawing") { onEditDrawing(m) },
                    )
                } else {
                    Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.BrokenImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Surface(
                    onClick = { onRemove(m) }, shape = CircleShape, color = Color.Black.copy(alpha = 0.45f), contentColor = Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp),
                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, "Remove", Modifier.size(18.dp)) } }
            }
        }
        media.filter { it.type == NoteMedia.Type.AUDIO }.forEach { m -> AudioPlayerRow(m, onRemove = { onRemove(m) }, onRename = onRename) }
    }
}

private fun formatMs(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun AudioPlayerRow(m: NoteMedia, onRemove: () -> Unit, onRename: (NoteMedia) -> Unit) {
    var renaming by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var pos by remember { mutableLongStateOf(0L) }
    val duration = m.durationMs.takeIf { it > 0 } ?: remember(m.file) {
        runCatching {
            MediaMetadataRetriever().run {
                setDataSource(NoteMediaStore.file(context, m.file).path)
                extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull().also { release() }
            }
        }.getOrNull() ?: 0L
    }
    DisposableEffect(m.file) { onDispose { player?.release(); player = null } }
    LaunchedEffect(playing) {
        while (playing) {
            pos = player?.currentPosition?.toLong() ?: 0L
            delay(100)
        }
    }
    fun toggle() {
        val p = player ?: runCatching {
            MediaPlayer().apply {
                setDataSource(NoteMediaStore.file(context, m.file).path)
                setOnCompletionListener { playing = false; pos = 0 }
                prepare()
            }
        }.getOrNull()?.also { player = it } ?: return
        if (playing) { p.pause(); playing = false } else { p.start(); playing = true }
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(onClick = ::toggle, shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play") }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            // Tap the name to rename the recording.
            Row(
                Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClickLabel = "Rename voice note") { renaming = true },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(m.name.ifBlank { "Voice note" }, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Rounded.Edit, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(progress = { if (duration > 0) (pos.toFloat() / duration).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.width(12.dp))
        Text(formatMs(if (playing || pos > 0) pos else duration), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, "Remove voice note", Modifier.size(18.dp)) }
    }
    if (renaming) {
        var text by remember { mutableStateOf(m.name.ifBlank { "Voice note" }) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename voice note") },
            text = {
                androidx.compose.material3.OutlinedTextField(
                    value = text, onValueChange = { text = it.take(60) }, singleLine = true,
                    shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { onRename(m.copy(name = text.trim())); renaming = false }) { Text("Save") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}

// ---------------------------------------------------------------- recorder

/** Records a voice note; returns the saved file name and its length. */
@Composable
fun AudioRecorderDialog(onDismiss: () -> Unit, onDone: (String, Long) -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> granted = ok; if (!ok) onDismiss() }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.RECORD_AUDIO) }
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var file by remember { mutableStateOf<File?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var level by remember { mutableFloatStateOf(0f) }
    fun stopAndRelease(): Long {
        val r = recorder ?: return 0
        recorder = null
        runCatching { r.stop() }
        r.release()
        return System.currentTimeMillis() - startedAt
    }
    fun start() {
        val f = NoteMediaStore.newAudio(context)
        val r = (if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(f.path)
        }
        runCatching { r.prepare(); r.start() }.onFailure { r.release(); return }
        file = f; recorder = r; startedAt = System.currentTimeMillis()
    }
    LaunchedEffect(granted) { if (granted && recorder == null && file == null) start() }
    LaunchedEffect(recorder) {
        while (recorder != null) {
            elapsed = System.currentTimeMillis() - startedAt
            level = ((recorder?.maxAmplitude ?: 0) / 12_000f).coerceIn(0f, 1f)
            delay(80)
        }
    }
    DisposableEffect(Unit) { onDispose { if (recorder != null) { stopAndRelease(); file?.delete() } } }
    val pulse by animateFloatAsState(1f + level * 0.35f, tween(80), label = "level")
    Dialog(onDismissRequest = { stopAndRelease(); file?.delete(); onDismiss() }) {
        Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (recorder != null) "Recording…" else "Voice note", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(24.dp))
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(120.dp)) {
                    Box(Modifier.size(96.dp * pulse).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)))
                    Surface(
                        onClick = {
                            val ms = stopAndRelease()
                            val f = file
                            if (f != null && ms > 600) onDone(f.name, ms) else { f?.delete(); onDismiss() }
                        },
                        shape = CircleShape, color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(80.dp),
                    ) { Box(contentAlignment = Alignment.Center) { Icon(if (recorder != null) Icons.Rounded.Stop else Icons.Rounded.Mic, "Stop and save", Modifier.size(36.dp)) } }
                }
                Spacer(Modifier.height(16.dp))
                Text(formatMs(elapsed), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text("Tap stop to save it to the note", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { stopAndRelease(); file?.delete(); onDismiss() }) { Text("Cancel") }
            }
        }
    }
}

// ---------------------------------------------------------------- drawing

private data class InkStroke(val points: List<Offset>, val color: Color, val width: Float, val eraser: Boolean)

private val inkColors = listOf(Color(0xFF1F1F1F), Color(0xFFD93025), Color(0xFF1A73E8), Color(0xFF188038), Color(0xFFF9AB00), Color(0xFF9334E6))

/** Full-screen sketch pad (white paper, pens, eraser, undo); saves as a PNG on the note. */
@Composable
fun DrawingDialog(existing: String?, onDismiss: () -> Unit, onDone: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<InkStroke>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var color by remember { mutableStateOf(inkColors.first()) }
    var width by remember { mutableFloatStateOf(8f) }
    var eraser by remember { mutableStateOf(false) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val background = existing?.let { rememberNoteBitmap(it, 2048) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Discard") }
                Text("Drawing", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex) }, enabled = strokes.isNotEmpty()) { Icon(Icons.AutoMirrored.Rounded.Undo, "Undo") }
                IconButton(onClick = { strokes.clear() }, enabled = strokes.isNotEmpty()) { Icon(Icons.Rounded.Delete, "Clear") }
                Button(onClick = {
                    if (canvasSize.width > 0) onDone(render(strokes, canvasSize, background)) else onDismiss()
                }, modifier = Modifier.padding(end = 8.dp)) { Text("Done") }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color.White)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                    .onSizeChanged { canvasSize = it },
            ) {
                if (background != null) Image(background.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(color, width, eraser) {
                            detectDragGestures(
                                onDragStart = { current = listOf(it) },
                                onDrag = { change, _ -> current = current + change.position },
                                onDragEnd = { if (current.size > 1) strokes.add(InkStroke(current, color, width, eraser)); current = emptyList() },
                                onDragCancel = { current = emptyList() },
                            )
                        }
                        .pointerInput(color, width, eraser) {
                            detectTapGestures { p -> strokes.add(InkStroke(listOf(p, p + Offset(0.1f, 0.1f)), color, width, eraser)) }
                        },
                ) {
                    (strokes + listOfNotNull(current.takeIf { it.size > 1 }?.let { InkStroke(it, color, width, eraser) })).forEach { s ->
                        val path = Path().apply { s.points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                        drawPath(path, if (s.eraser) Color.White else s.color, style = Stroke(width = if (s.eraser) s.width * 3 else s.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                inkColors.forEach { c ->
                    val sel = !eraser && c == color
                    Box(
                        Modifier.size(if (sel) 36.dp else 30.dp).clip(CircleShape).background(c)
                            .border(if (sel) 3.dp else 0.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable { color = c; eraser = false },
                    )
                }
                Spacer(Modifier.weight(1f))
                listOf(4f, 8f, 16f).forEach { w ->
                    Box(
                        Modifier.size(34.dp).clip(CircleShape)
                            .background(if (width == w) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable { width = w },
                        contentAlignment = Alignment.Center,
                    ) { Box(Modifier.size((w / 1.4f + 3).dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface)) }
                }
                Box(
                    Modifier.clip(RoundedCornerShape(12.dp))
                        .background(if (eraser) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .clickable { eraser = !eraser }.padding(horizontal = 10.dp, vertical = 8.dp),
                ) { Text("Eraser", style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

private fun render(strokes: List<InkStroke>, size: IntSize, background: Bitmap?): Bitmap {
    val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(bmp)
    c.drawColor(android.graphics.Color.WHITE)
    if (background != null) {
        val scale = minOf(size.width / background.width.toFloat(), size.height / background.height.toFloat())
        val w = background.width * scale; val h = background.height * scale
        val l = (size.width - w) / 2; val t = (size.height - h) / 2
        c.drawBitmap(background, null, android.graphics.RectF(l, t, l + w, t + h), null)
    }
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE; strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND
    }
    strokes.forEach { s ->
        paint.color = if (s.eraser) android.graphics.Color.WHITE else s.color.toArgb()
        paint.strokeWidth = if (s.eraser) s.width * 3 else s.width
        val p = android.graphics.Path()
        s.points.forEachIndexed { i, o -> if (i == 0) p.moveTo(o.x, o.y) else p.lineTo(o.x, o.y) }
        c.drawPath(p, paint)
    }
    return bmp
}

/** Pick a photo from the gallery (no storage permission needed). */
@Composable
fun rememberImagePicker(onPicked: (Uri) -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onPicked(uri) }
    return { launcher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}
