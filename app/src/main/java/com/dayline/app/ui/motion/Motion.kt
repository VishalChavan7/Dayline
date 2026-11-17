package com.dayline.app.ui.motion

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

/**
 * Material 3 Expressive motion tokens (spring based). Spatial springs overshoot a little for the
 * "bouncy" expressive feel; effects springs never overshoot (used for colour/alpha).
 */
object Motion {
    fun <T> bouncy(): FiniteAnimationSpec<T> = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 500f)
    fun <T> effects(): FiniteAnimationSpec<T> = spring(dampingRatio = 1f, stiffness = 1600f)

    /** iOS-like ease for indicator slides: quick, settles without visible wobble. */
    fun <T> glide(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.86f, stiffness = 420f)
}

/**
 * Expressive press feedback: the element squishes while pressed and springs back with a bounce.
 * It only observes touches (never consumes them), so it composes with any click handler.
 */
fun Modifier.pressBounce(pressedScale: Float = 0.94f): Modifier = composed {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                scope.launch { scale.animateTo(pressedScale, spring(dampingRatio = 1f, stiffness = 1400f)) }
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                scope.launch { scale.animateTo(1f, spring(dampingRatio = 0.42f, stiffness = 520f)) }
            }
        }
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
}

/** Vibration feedback that works the same on Samsung and Pixel devices. */
class Haptics(private val context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private fun play(effect: VibrationEffect) {
        try {
            if (vibrator?.hasVibrator() == true) vibrator.vibrate(effect)
        } catch (_: Exception) {
            // Vibration is best-effort.
        }
    }

    /** Short double tap — "saved / added". */
    fun success() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && vibrator?.areAllPrimitivesSupported(
                VibrationEffect.Composition.PRIMITIVE_CLICK, VibrationEffect.Composition.PRIMITIVE_TICK,
            ) == true
        ) {
            play(
                VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.9f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f, 70)
                    .compose(),
            )
        } else {
            play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK))
        }
    }

    /** Crisp single click — completing a task, toggling. */
    fun tick() = play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))

    /** Softer tick for undo / selection changes. */
    fun light() = play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))

    /** The one "done" feel used everywhere: tasks, habits, checklist items, widgets. */
    fun complete(done: Boolean) = if (done) tick() else light()

    /**
     * Same click, for code running without a visible screen (widget taps, timer alarms). Android
     * silently drops touch vibrations from background apps, so this uses the alarm channel.
     */
    fun background(effect: VibrationEffect = VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)) {
        try {
            if (vibrator?.hasVibrator() != true) return
            if (Build.VERSION.SDK_INT >= 33) {
                vibrator.vibrate(effect, android.os.VibrationAttributes.createForUsage(android.os.VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_ALARM).build())
            }
        } catch (_: Exception) {
        }
    }

    /** Long-press confirmation. */
    fun heavy() = play(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember(context) { Haptics(context.applicationContext) }
}

@Suppress("unused")
private val zero = Offset.Zero
