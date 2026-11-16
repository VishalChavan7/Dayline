package com.dayline.app.notifications

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Three short pulses, one per second, for the last three seconds of a focus or break phase.
 * Uses the alarm usage so it is felt even when the screen is off or the app is in the background.
 */
object FocusCountdown {
    private const val PULSE = 160L

    fun buzz(context: Context, remainingMillis: Long) {
        // Pulses land at 3, 2 and 1 seconds before the end; skip any that have already passed.
        val pulsesAt = listOf(3_000L, 2_000L, 1_000L).filter { it <= remainingMillis + 250L }
        if (pulsesAt.isEmpty()) return
        val timings = mutableListOf<Long>()
        val amps = mutableListOf<Int>()
        var cursor = 0L
        pulsesAt.forEach { before ->
            val at = (remainingMillis - before).coerceAtLeast(cursor)
            timings += at - cursor; amps += 0
            timings += PULSE; amps += 255
            cursor = at + PULSE
        }
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (!vibrator.hasVibrator()) return
        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings.toLongArray(), amps.toIntArray(), -1)
        } else {
            VibrationEffect.createWaveform(timings.toLongArray(), -1)
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
            }
        }
    }
}
