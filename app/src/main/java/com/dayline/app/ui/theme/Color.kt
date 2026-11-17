package com.dayline.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.dayline.app.core.model.Priority

/** Accent choices offered in Settings (and for projects, lists, habits). */
object Palette {
    val accents: List<Int> = listOf(
        0xFF2F7DF6.toInt(), // Blue
        0xFF3E9B4F.toInt(), // Green
        0xFFE0892B.toInt(), // Amber
        0xFFE5484D.toInt(), // Red
        0xFFD6409F.toInt(), // Pink
        0xFF8E4EC6.toInt(), // Violet
    )

    /** Wider set for habits and labels. */
    val itemColors: List<Int> = listOf(0xFF4F5BD5.toInt(), 0xFF0E9F8E.toInt()) + accents + listOf(
        0xFF6E56CF.toInt(), 0xFF00A2C7.toInt(), 0xFFC2410C.toInt(), 0xFF64748B.toInt(),
    )
}

/** Consistent priority colours across every screen, tuned for contrast in light and dark themes. */
object PriorityColors {
    fun of(priority: Priority, dark: Boolean): Color = when (priority) {
        Priority.URGENT -> if (dark) Color(0xFFFF8A8F) else Color(0xFFD92D3A)
        Priority.HIGH -> if (dark) Color(0xFFFFB366) else Color(0xFFD9700A)
        Priority.MEDIUM -> if (dark) Color(0xFF8FB2FF) else Color(0xFF2F6BE0)
        Priority.LOW -> if (dark) Color(0xFF9DB0A3) else Color(0xFF5E7A68)
    }
}

object StatusColors {
    fun overdue(dark: Boolean) = if (dark) Color(0xFFFF8A8F) else Color(0xFFC62A36)
    fun success(dark: Boolean) = if (dark) Color(0xFF7FD69A) else Color(0xFF1E8E4A)
    fun streak(dark: Boolean) = if (dark) Color(0xFFFFB366) else Color(0xFFE0700A)
}

private fun hsl(color: Int): FloatArray = FloatArray(3).also { ColorUtils.colorToHSL(color, it) }
private fun tone(h: Float, s: Float, l: Float): Color = Color(ColorUtils.HSLToColor(floatArrayOf((h + 360f) % 360f, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))

/**
 * Builds a full Material colour scheme from a single accent ("seed") colour. It keeps the accent's hue, derives
 * secondary/tertiary from neighbouring hues, and tints the neutral surfaces very lightly, which mirrors how
 * Material You schemes feel without needing the platform's dynamic palette.
 */
fun schemeFromSeed(seed: Int, dark: Boolean): ColorScheme {
    val (h, sRaw, _) = hsl(seed).let { Triple(it[0], it[1], it[2]) }
    val s = sRaw.coerceIn(0.35f, 0.85f)
    val ns = 0.06f // neutral saturation
    val vs = 0.10f // neutral-variant saturation
    return if (!dark) {
        lightColorScheme(
            primary = tone(h, s, 0.42f),
            onPrimary = Color.White,
            primaryContainer = tone(h, s, 0.90f),
            onPrimaryContainer = tone(h, s, 0.16f),
            inversePrimary = tone(h, s, 0.80f),
            secondary = tone(h + 12f, s * 0.35f, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = tone(h + 12f, s * 0.45f, 0.90f),
            onSecondaryContainer = tone(h + 12f, s * 0.4f, 0.15f),
            tertiary = tone(h + 60f, s * 0.6f, 0.40f),
            onTertiary = Color.White,
            tertiaryContainer = tone(h + 60f, s * 0.7f, 0.90f),
            onTertiaryContainer = tone(h + 60f, s * 0.6f, 0.15f),
            background = tone(h, ns, 0.985f),
            onBackground = tone(h, ns, 0.11f),
            surface = tone(h, ns, 0.985f),
            onSurface = tone(h, ns, 0.11f),
            surfaceVariant = tone(h, vs, 0.90f),
            onSurfaceVariant = tone(h, vs, 0.30f),
            surfaceTint = tone(h, s, 0.42f),
            inverseSurface = tone(h, ns, 0.19f),
            inverseOnSurface = tone(h, ns, 0.95f),
            outline = tone(h, vs, 0.50f),
            outlineVariant = tone(h, vs, 0.82f),
            surfaceBright = tone(h, ns, 0.985f),
            surfaceDim = tone(h, ns, 0.87f),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = tone(h, ns, 0.965f),
            surfaceContainer = tone(h, ns, 0.945f),
            surfaceContainerHigh = tone(h, ns, 0.925f),
            surfaceContainerHighest = tone(h, ns, 0.90f),
        )
    } else {
        darkColorScheme(
            primary = tone(h, s, 0.78f),
            onPrimary = tone(h, s, 0.18f),
            primaryContainer = tone(h, s * 0.8f, 0.30f),
            onPrimaryContainer = tone(h, s, 0.90f),
            inversePrimary = tone(h, s, 0.42f),
            secondary = tone(h + 12f, s * 0.35f, 0.78f),
            onSecondary = tone(h + 12f, s * 0.35f, 0.18f),
            secondaryContainer = tone(h + 12f, s * 0.3f, 0.28f),
            onSecondaryContainer = tone(h + 12f, s * 0.4f, 0.90f),
            tertiary = tone(h + 60f, s * 0.6f, 0.78f),
            onTertiary = tone(h + 60f, s * 0.6f, 0.18f),
            tertiaryContainer = tone(h + 60f, s * 0.5f, 0.28f),
            onTertiaryContainer = tone(h + 60f, s * 0.6f, 0.90f),
            background = tone(h, ns, 0.065f),
            onBackground = tone(h, ns, 0.90f),
            surface = tone(h, ns, 0.065f),
            onSurface = tone(h, ns, 0.90f),
            surfaceVariant = tone(h, vs, 0.30f),
            onSurfaceVariant = tone(h, vs, 0.80f),
            surfaceTint = tone(h, s, 0.78f),
            inverseSurface = tone(h, ns, 0.90f),
            inverseOnSurface = tone(h, ns, 0.15f),
            outline = tone(h, vs, 0.58f),
            outlineVariant = tone(h, vs, 0.30f),
            surfaceBright = tone(h, ns, 0.24f),
            surfaceDim = tone(h, ns, 0.065f),
            surfaceContainerLowest = tone(h, ns, 0.04f),
            surfaceContainerLow = tone(h, ns, 0.10f),
            surfaceContainer = tone(h, ns, 0.12f),
            surfaceContainerHigh = tone(h, ns, 0.16f),
            surfaceContainerHighest = tone(h, ns, 0.20f),
        )
    }
}

/** Soft container colour derived from an item colour (projects, habits, lists). */
fun containerOf(color: Int, dark: Boolean): Color {
    val c = hsl(color)
    return if (dark) tone(c[0], c[1] * 0.55f, 0.24f) else tone(c[0], c[1] * 0.7f, 0.92f)
}

/** Readable foreground version of an item colour on the current surface. */
fun contentOf(color: Int, dark: Boolean): Color {
    val c = hsl(color)
    return if (dark) tone(c[0], c[1], 0.76f) else tone(c[0], c[1], 0.38f)
}

fun Color.argb(): Int = toArgb()


/**
 * Theme choices beyond a single accent colour. They're stored in the same "accent" setting using
 * values that can't be real colours (fully transparent), so older settings keep working.
 */
object ThemeStyles {
    const val MONOCHROME = 0x00000101
    const val DYNAMIC_GRADIENT = 0x00000201

    data class Gradient(val id: Int, val name: String, val start: Color, val end: Color)

    val gradients: List<Gradient> = listOf(
        Gradient(0x00000301, "Sunset", Color(0xFFF2545B), Color(0xFFF7A23B)),
        Gradient(0x00000302, "Ocean", Color(0xFF2F6FED), Color(0xFF2BB6D9)),
        Gradient(0x00000303, "Aurora", Color(0xFF7B3FF2), Color(0xFFE040B5)),
        Gradient(0x00000304, "Forest", Color(0xFF1F7A4D), Color(0xFF8BC34A)),
        Gradient(0x00000305, "Dusk", Color(0xFF3D3B8E), Color(0xFFE0607E)),
    )

    fun gradient(id: Int): Gradient? = gradients.firstOrNull { it.id == id }
}

/** Black, greys and white. */
fun monochromeScheme(dark: Boolean): ColorScheme {
    val base = schemeFromSeed(0xFF808080.toInt(), dark)
    return if (!dark) base.copy(
        primary = Color(0xFF151515), onPrimary = Color.White,
        primaryContainer = Color(0xFFE4E4E4), onPrimaryContainer = Color(0xFF151515),
        secondary = Color(0xFF5C5C5C), onSecondary = Color.White,
        secondaryContainer = Color(0xFFEDEDED), onSecondaryContainer = Color(0xFF1E1E1E),
        tertiary = Color(0xFF3A3A3A), onTertiary = Color.White,
        tertiaryContainer = Color(0xFFDADADA), onTertiaryContainer = Color(0xFF111111),
        surfaceTint = Color(0xFF151515), inversePrimary = Color(0xFFD0D0D0),
    ) else base.copy(
        primary = Color(0xFFF2F2F2), onPrimary = Color(0xFF111111),
        primaryContainer = Color(0xFF3A3A3A), onPrimaryContainer = Color(0xFFF2F2F2),
        secondary = Color(0xFFBDBDBD), onSecondary = Color(0xFF1A1A1A),
        secondaryContainer = Color(0xFF2E2E2E), onSecondaryContainer = Color(0xFFE6E6E6),
        tertiary = Color(0xFFD6D6D6), onTertiary = Color(0xFF111111),
        tertiaryContainer = Color(0xFF444444), onTertiaryContainer = Color(0xFFF0F0F0),
        surfaceTint = Color(0xFFF2F2F2), inversePrimary = Color(0xFF333333),
    )
}
