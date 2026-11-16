package com.dayline.app.ui.onboarding

import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.Image
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dayline.app.ui.components.ProgressRing
import com.dayline.app.ui.motion.pressBounce
import com.dayline.app.ui.motion.rememberHaptics
import com.dayline.app.ui.theme.Dimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val Brand = Color(0xFF4F5BD5)

/**
 * The Dayline mark (sun rising over the day's line, with a check), drawable in stages so it can be
 * animated: [line] grows the horizon, [rise] lifts the sun, [rays] pops the rays, [check] draws the tick.
 */
@Composable
fun DaylineMark(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    checkColor: Color = Brand,
    line: Float = 1f,
    rise: Float = 1f,
    rays: Float = 1f,
    check: Float = 1f,
) {
    Canvas(modifier) {
        val k = size.minDimension / 108f
        // Horizon bar grows from the centre.
        if (line > 0f) {
            val w = 46f * line.coerceIn(0f, 1.2f)
            drawRoundRect(color, Offset((54f - w / 2f) * k, 66f * k), Size(w * k, 6f * k), CornerRadius(3f * k))
        }
        // Sun rises from behind the horizon.
        clipRect(bottom = 66f * k) {
            translate(top = (1f - rise) * 26f * k) {
                drawArc(color, 180f, 180f, true, Offset(35f * k, 43f * k), Size(38f * k, 38f * k))
            }
        }
        // Rays pop out one after another.
        val rayLines = listOf(Offset(54f, 30f) to Offset(54f, 37f), Offset(34.35f, 37.55f) to Offset(39.35f, 42.55f), Offset(73.65f, 37.55f) to Offset(68.65f, 42.55f))
        rayLines.forEachIndexed { i, (a, b) ->
            val p = ((rays * 3f) - i).coerceIn(0f, 1f)
            if (p > 0f) {
                val mid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                scale(0.4f + 0.6f * p, pivot = Offset(mid.x * k, mid.y * k)) {
                    drawLine(color.copy(alpha = 0.9f * p), Offset(a.x * k, a.y * k), Offset(b.x * k, b.y * k), strokeWidth = 3f * k, cap = StrokeCap.Round)
                }
            }
        }
        // Check is "written" on the sun.
        if (check > 0f && rise > 0.6f) {
            val path = Path().apply { moveTo(46f * k, 54f * k); lineTo(51.5f * k, 59f * k); lineTo(62f * k, 48f * k) }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * check.coerceIn(0f, 1f), partial, true)
            translate(top = (1f - rise) * 26f * k) {
                drawPath(partial, checkColor, style = Stroke(width = 4.2f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/**
 * App-opening animation. Picks up from the system splash (same colour and mark) and plays a short,
 * playful sunrise: the line stretches, the sun bounces up, rays pop, the check is written, then
 * the whole thing hops and zooms away to reveal the app.
 */
@Composable
fun LaunchIntro(ready: Boolean, onFinished: () -> Unit) {
    // Starts on the exact frame the system splash shows (the full mark), then plays: the sun dips
    // and springs back up, the rays pop, the check redraws and the word slides in.
    val line = remember { Animatable(1f) }
    val rise = remember { Animatable(1f) }
    val rays = remember { Animatable(1f) }
    val check = remember { Animatable(1f) }
    val hop = remember { Animatable(1f) }
    val word = remember { Animatable(0f) }
    val exit = remember { Animatable(0f) }
    val isReady by rememberUpdatedState(ready)
    val finished by rememberUpdatedState(onFinished)
    LaunchedEffect(Unit) {
        launch { rays.animateTo(0f, tween(160, easing = FastOutSlowInEasing)) }
        launch { check.animateTo(0f, tween(140)) }
        rise.animateTo(0.55f, tween(200, easing = FastOutSlowInEasing))
        rise.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 260f))
        launch { rays.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
        launch { word.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f)) }
        check.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
        hop.animateTo(1.14f, tween(110, easing = FastOutSlowInEasing))
        hop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 500f))
        while (!isReady) delay(30)
        exit.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
        finished()
    }
    // Follows the app's (dynamic) theme; the system splash uses the same primary colour.
    val bg = MaterialTheme.colorScheme.primary
    val fg = MaterialTheme.colorScheme.onPrimary
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exit.value }
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        // Same size and position as the system splash icon (288 dp, no icon background), so the hand-off is seamless.
        DaylineMark(
            Modifier
                .size(288.dp)
                .graphicsLayer {
                    val s = hop.value * (1f + exit.value * 0.6f)
                    scaleX = s; scaleY = s
                },
            color = fg, checkColor = bg,
            line = line.value, rise = rise.value, rays = rays.value, check = check.value,
        )
        Text(
            "Dayline",
            style = MaterialTheme.typography.headlineMedium,
            color = fg,
            modifier = Modifier
                .offset(y = 100.dp)
                .graphicsLayer {
                    alpha = word.value.coerceIn(0f, 1f) * (1f - exit.value)
                    translationY = (1f - word.value) * 24.dp.toPx()
                },
        )
    }
}

private data class Page(val title: String, val body: String)

private val pages = listOf(
    Page("Welcome to Dayline", "Tasks, calendar, habits, focus and notes — your whole day in one calm place."),
    Page("What should we call you?", "Dayline greets you by name each day. You can change it any time in Settings."),
    Page("Capture in a tap", "Tap + to add a task. Press and hold + for notes, habits and focus sessions."),
    Page("Notes, your way", "Tap New Note to write. Press and hold it to start with an image, a drawing, a voice note or a list."),
    Page("Swipe to get it done", "Tap the circle or swipe right to complete. Swipe left to delete — you can always undo."),
    Page("Build streaks, find focus", "Check in on habits, run a focus timer and add Dayline widgets to your home screen."),
    Page("Never miss a thing", "Reminders arrive right on time. Allow notifications so Dayline can nudge you."),
)

/** First-run tutorial. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit, onName: (String) -> Unit = {}, initialPage: Int = 0) {
    var name by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    val pager = rememberPagerState(initialPage = initialPage) { pages.size }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val last = pager.currentPage == pages.lastIndex
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(pager) {
        // Leaving the name page (by swipe or Next): drop focus and the keyboard straight away, so no
        // cursor, handle or keyboard is left behind on the following pages.
        androidx.compose.runtime.snapshotFlow { pager.isScrollInProgress || pager.currentPage != 1 }.collect { leaving ->
            if (leaving) { focus.clearFocus(force = true); keyboard?.hide() }
        }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onFinish() }
    fun saveName() { if (name.isNotBlank()) onName(name) }
    fun finish() {
        saveName()
        haptics.success()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else onFinish()
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = Dimens.sm), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (!last) TextButton(onClick = { saveName(); onFinish() }) { Text("Skip") }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { index ->
            val art: @Composable (Modifier) -> Unit = { m ->
                Box(
                    m.graphicsLayer {
                        // Parallax: the illustration trails the text slightly (read in the layer, no recomposition).
                        val pageOffset = (pager.currentPage - index) + pager.currentPageOffsetFraction
                        translationX = pageOffset * size.width * 0.35f
                        val off = kotlin.math.abs(pageOffset).coerceAtMost(1f)
                        val s = 1f - off * 0.15f
                        scaleX = s; scaleY = s
                        // Fully faded by the time a neighbour is off-screen, so no art bleeds onto the next page.
                        alpha = (1f - off * 1.6f).coerceIn(0f, 1f)
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    // Short screens (landscape, small phones, large font) shrink the art instead of clipping it.
                    ScaleToFit {
                        when (index) {
                            0 -> WelcomeArt()
                            1 -> NameArt(name) { name = it }
                            2 -> CaptureArt()
                            3 -> NotesHoldArt()
                            4 -> SwipeArt()
                            5 -> HabitsArt()
                            else -> ReminderArt()
                        }
                    }
                }
            }
            val copy: @Composable () -> Unit = {
                Text(pages[index].title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(Dimens.md))
                Text(
                    pages[index].body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 420.dp),
                )
            }
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                if (maxWidth > maxHeight * 1.3f && maxHeight < 480.dp) {
                    // Landscape: art beside the copy, so neither has to squeeze into a 200dp-tall strip.
                    Row(
                        Modifier.fillMaxSize().padding(horizontal = Dimens.xxl),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Dimens.xxl),
                    ) {
                        art(Modifier.weight(1f).fillMaxHeight().padding(vertical = Dimens.sm))
                        Column(
                            Modifier.weight(1f).verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) { copy() }
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = Dimens.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        art(Modifier.weight(1f, fill = false).heightIn(min = 160.dp, max = 300.dp).fillMaxWidth())
                        Spacer(Modifier.height(Dimens.xxl))
                        copy()
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Dimens.xxl, vertical = Dimens.xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                pages.indices.forEach { i ->
                    val selected = i == pager.currentPage
                    val w by animateDpAsState(if (selected) 24.dp else 8.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "dot")
                    val c by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "dotColor")
                    Box(Modifier.size(w, 8.dp).clip(CircleShape).background(c))
                }
            }
            Button(
                onClick = {
                    haptics.light()
                    if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1, animationSpec = spring(dampingRatio = 0.85f, stiffness = 300f)) }
                },
                modifier = Modifier.height(52.dp).pressBounce(),
            ) {
                Text(if (last) "Get started" else "Next", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun NameArt(name: String, onName: (String) -> Unit) {
    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "👋"
    val pop = remember { Animatable(1f) }
    LaunchedEffect(initial) {
        pop.snapTo(0.85f)
        pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth()) {
        Box(
            Modifier.size(112.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }.clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            CenteredGlyph(initial, MaterialTheme.colorScheme.onPrimaryContainer, 52.sp)
        }
        Spacer(Modifier.height(Dimens.xl))
        androidx.compose.material3.OutlinedTextField(
            value = name,
            onValueChange = { onName(it.take(40)) },
            placeholder = { Text("Your first name") },
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Draws [text] centred on its ink bounds (not the line box), so letters sit dead-centre in a circle. */
@Composable
private fun CenteredGlyph(text: String, color: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.TextUnit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val px = with(density) { size.toPx() }
    val paint = remember(px, color) {
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = px
            this.color = color.toArgb()
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            textAlign = android.graphics.Paint.Align.LEFT
        }
    }
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val b = android.graphics.Rect()
        paint.getTextBounds(text, 0, text.length, b)
        drawContext.canvas.nativeCanvas.drawText(text, center.x - b.exactCenterX(), center.y - b.exactCenterY(), paint)
    }
}

/** One-time question for people who installed before names existed. */
@Composable
fun NamePromptDialog(onSave: (String) -> Unit, onSkip: () -> Unit) {
    var name by remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onSkip,
        icon = { Text("👋", style = MaterialTheme.typography.headlineMedium) },
        title = { Text("What should we call you?") },
        text = {
            Column {
                Text("Dayline will greet you by name on your Today page.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Dimens.md))
                androidx.compose.material3.OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true, placeholder = { Text("Your first name") },
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words),
                )
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onSkip) { Text("Not now") } },
    )
}

@Composable
private fun WelcomeArt() {
    val t = rememberInfiniteTransition(label = "welcome")
    val float by t.animateFloat(-6f, 6f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float")
    Box(
        Modifier
            .size(200.dp)
            .graphicsLayer { translationY = float.dp.toPx() }
            .clip(RoundedCornerShape(64.dp))
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        DaylineMark(Modifier.size(170.dp), color = MaterialTheme.colorScheme.onPrimary, checkColor = MaterialTheme.colorScheme.primary)
    }
}

/** Ease-out with a small overshoot: the expressive "bounce" used across the app. */
private fun backOut(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val t = x - 1f
    return 1f + c3 * t * t * t + c1 * t * t
}

@Composable
private fun CaptureArt() = HoldMenuArt(
    listOf(Icons.Rounded.TaskAlt to "New task", Icons.AutoMirrored.Rounded.StickyNote2 to "New note", Icons.Rounded.SelfImprovement to "New habit", Icons.Rounded.Timer to "Focus"),
    pill = null,
)

@Composable
private fun NotesHoldArt() = HoldMenuArt(
    listOf(Icons.Rounded.Image to "Image", Icons.Rounded.Draw to "Drawing", Icons.Rounded.Mic to "Audio", Icons.Rounded.Checklist to "List", Icons.Rounded.TextFields to "Text"),
    pill = "New Note",
)

/** A button being held and its menu springing out; [pill] draws the Notes "New Note" button instead of +. */
@Composable
private fun HoldMenuArt(items: List<Pair<androidx.compose.ui.graphics.vector.ImageVector, String>>, pill: String?) {
    // One linear clock drives a scripted loop that starts and ends at rest (no seam):
    // press → menu springs open → hold → menu folds away smoothly → idle.
    val t = rememberInfiniteTransition(label = "capture")
    val clock = t.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "captureClock")
    fun seg(v: Float, a: Float, b: Float) = ((v - a) / (b - a)).coerceIn(0f, 1f)
    fun smooth(x: Float) = x * x * (3f - 2f * x)
    /** How open the menu is (0..1), eased; read only inside draw/layer lambdas. */
    fun openness(v: Float) = if (v < 0.72f) smooth(seg(v, 0.18f, 0.40f)) else 1f - smooth(seg(v, 0.72f, 0.86f))
    val container = MaterialTheme.colorScheme.primaryContainer
    val primary = MaterialTheme.colorScheme.primary
    Column(Modifier.widthIn(max = 280.dp).fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEachIndexed { i, (icon, label) ->
            val order = items.size - 1 - i // nearest the button first
            Row(
                Modifier
                    .graphicsLayer {
                        val v = clock.value
                        val e = if (v < 0.72f) {
                            // Opening: staggered, with a little expressive overshoot.
                            backOut(seg(v, 0.18f + order * 0.03f, 0.36f + order * 0.03f))
                        } else {
                            // Closing: all together, no overshoot, so the loop ends calmly.
                            1f - smooth(seg(v, 0.72f, 0.84f))
                        }
                        alpha = e.coerceIn(0f, 1f)
                        scaleX = 0.7f + 0.3f * e; scaleY = 0.7f + 0.3f * e
                        translationY = (1f - e) * 18.dp.toPx()
                        transformOrigin = TransformOrigin(1f, 1f)
                    }
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(label, style = MaterialTheme.typography.titleSmall)
            }
        }
        Box(
            Modifier
                .graphicsLayer {
                    // A soft squish while the button is held, then it settles.
                    val press = kotlin.math.sin(seg(clock.value, 0.08f, 0.22f) * Math.PI).toFloat()
                    val s = 1f - 0.1f * press
                    scaleX = s; scaleY = s
                }
                .then(if (pill == null) Modifier.size(68.dp) else Modifier.height(56.dp))
                .clip(RoundedCornerShape(if (pill == null) 22.dp else 20.dp))
                .drawBehind { drawRect(androidx.compose.ui.graphics.lerp(container, primary, openness(clock.value))) }
                .then(if (pill == null) Modifier else Modifier.padding(start = 18.dp, end = 22.dp)),
            contentAlignment = Alignment.Center,
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
           Box(contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Add, null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(30.dp).graphicsLayer { val o = openness(clock.value); rotationZ = 135f * o; alpha = 1f - o },
            )
            Icon(
                Icons.Rounded.Add, null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(30.dp).graphicsLayer { val o = openness(clock.value); rotationZ = 135f * o; alpha = o },
            )
           }
           if (pill != null) {
               Spacer(Modifier.width(8.dp))
               Box {
                   Text(pill, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
                       modifier = Modifier.graphicsLayer { alpha = 1f - openness(clock.value) })
                   Text(pill, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimary,
                       modifier = Modifier.graphicsLayer { alpha = openness(clock.value) })
               }
           }
          }
        }
    }
}


@Composable
private fun SwipeArt() {
    // Scripted loop that begins and ends at rest: swipe right → tick → card springs back →
    // hold → the done card fades out and back in as a fresh task → idle.
    val t = rememberInfiniteTransition(label = "swipe")
    val clock = t.animateFloat(0f, 1f, infiniteRepeatable(tween(3400, easing = LinearEasing)), label = "swipeClock")
    fun seg(v: Float, a: Float, b: Float) = ((v - a) / (b - a)).coerceIn(0f, 1f)
    fun smooth(x: Float) = x * x * (3f - 2f * x)
    val done by remember { androidx.compose.runtime.derivedStateOf { clock.value in 0.40f..0.86f } }
    val green = Color(0xFF2E9D5B)
    Column(Modifier.widthIn(max = 300.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("Plan the week", "Call the dentist", "Buy flowers").forEachIndexed { i, title ->
            val active = i == 1
            // How far the card is pulled right (0..1): out with an ease, back with a springy settle.
            fun pull(v: Float) = if (v < 0.40f) smooth(seg(v, 0.12f, 0.40f)) else 1f - backOut(seg(v, 0.40f, 0.56f))
            // Whole row fades out and back in to reset the demo without a jump.
            fun fade(v: Float) = when {
                v < 0.78f -> 1f
                v < 0.86f -> 1f - smooth(seg(v, 0.78f, 0.86f))
                else -> smooth(seg(v, 0.86f, 0.95f))
            }
            Box(
                Modifier.fillMaxWidth().height(60.dp)
                    .graphicsLayer { if (active) alpha = fade(clock.value) }
                    .clip(RoundedCornerShape(20.dp))
                    .drawBehind { if (active) drawRect(green.copy(alpha = 0.85f * pull(clock.value).coerceIn(0f, 1f))) },
            ) {
                if (active) {
                    Icon(
                        Icons.Rounded.CheckCircle, null, tint = Color.White,
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 16.dp).graphicsLayer { alpha = pull(clock.value).coerceIn(0f, 1f) },
                    )
                }
                val isDone = active && done
                Row(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { if (active) translationX = pull(clock.value) * 84.dp.toPx() }
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(if (isDone) green else Color.Transparent).border(2.dp, if (isDone) green else MaterialTheme.colorScheme.outline, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) { if (isDone) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                        textDecoration = if (isDone) TextDecoration.LineThrough else null,
                    )
                }
            }
        }
    }
}


@Composable
private fun HabitsArt() {
    val t = rememberInfiniteTransition(label = "habits")
    val p by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart), label = "ring")
    val ring = (p * 1.4f).coerceAtMost(0.72f)
    Row(horizontalArrangement = Arrangement.spacedBy(Dimens.lg), verticalAlignment = Alignment.CenterVertically) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(Triple(Icons.Rounded.SelfImprovement, Color(0xFF7C4DFF), 12), Triple(Icons.AutoMirrored.Rounded.DirectionsRun, Color(0xFFEF6C00), 5), Triple(Icons.AutoMirrored.Rounded.MenuBook, Color(0xFF2E9D5B), 21))
                .forEachIndexed { i, (icon, c, streak) ->
                    val bump = kotlin.math.max(0f, kotlin.math.sin(((p * 3f) - i) * Math.PI).toFloat())
                    Row(
                        Modifier.width(132.dp).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(c.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                            Icon(icon, null, tint = c, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Rounded.LocalFireDepartment, null, tint = Color(0xFFF57C00), modifier = Modifier.size(18.dp).graphicsLayer { val s = 1f + bump * 0.35f; scaleX = s; scaleY = s })
                        Text("$streak", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.width(24.dp))
                    }
                }
        }
        ProgressRing(ring, Modifier.size(120.dp), strokeWidth = 12.dp) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.primary)
                Text("${(ring * 25).toInt()}:00", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ReminderArt() {
    val t = rememberInfiniteTransition(label = "bell")
    val wiggle by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(keyframes { durationMillis = 2200; 0f at 0; 1f at 120; -1f at 240; 0.7f at 360; -0.5f at 480; 0f at 600; 0f at 2200 }),
        label = "wiggle",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Dimens.lg)) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Notifications, null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(60.dp).graphicsLayer { rotationZ = wiggle * 18f; transformOrigin = TransformOrigin(0.5f, 0.1f) },
            )
        }
        NotificationMock(Modifier.offset(y = (-4).dp))
    }
}

@Composable
private fun NotificationMock(modifier: Modifier = Modifier, width: Dp = 300.dp) {
    Row(
        modifier.widthIn(max = width).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
            DaylineMark(Modifier.size(30.dp), color = MaterialTheme.colorScheme.onPrimary, checkColor = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("Dayline · now", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Send the Q4 budget", style = MaterialTheme.typography.titleSmall)
        }
    }
}



/**
 * Lays its content out at natural size and, if that is taller or wider than the space it was given,
 * scales it down uniformly to fit (never up). Keeps illustrations whole on short screens.
 */
@Composable
private fun ScaleToFit(content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content) { measurables, constraints ->
        val loose = androidx.compose.ui.unit.Constraints(maxWidth = constraints.maxWidth)
        val placeable = measurables.first().measure(loose)
        val maxH = if (constraints.hasBoundedHeight) constraints.maxHeight else placeable.height
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else placeable.width
        val scale = minOf(1f, maxH.toFloat() / placeable.height.coerceAtLeast(1), maxW.toFloat() / placeable.width.coerceAtLeast(1))
        val w = (placeable.width * scale).toInt().coerceIn(constraints.minWidth, maxW)
        val h = (placeable.height * scale).toInt().coerceIn(constraints.minHeight, maxH)
        layout(w, h) {
            placeable.placeWithLayer((w - placeable.width) / 2, (h - placeable.height) / 2) {
                scaleX = scale; scaleY = scale
            }
        }
    }
}
