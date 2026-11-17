package com.dayline.app.ui.theme

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Color
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dayline.app.core.model.ThemeMode

/** Centralised spacing and sizing tokens. */
@Immutable
object Dimens {
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 20.dp
    val xxl: Dp = 24.dp
    val xxxl: Dp = 32.dp
    val screenPadding: Dp = 16.dp
    val cardRadius: Dp = 28.dp
    val rowRadius: Dp = 20.dp
    val minTouch: Dp = 48.dp
    /** Content is centred and capped at this width on large screens and unfolded foldables. */
    val maxContentWidth: Dp = 720.dp
}

val DaylineShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

@Immutable
data class DaylineColors(val isDark: Boolean)

val LocalDaylineColors = staticCompositionLocalOf { DaylineColors(isDark = false) }

@Composable
fun isDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun DaylineTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    accentColor: Int = Palette.accents.first(),
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(themeMode)
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val canDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val gradient = ThemeStyles.gradient(accentColor)
    val scheme = remember(dark, useDynamic, accentColor) {
        when {
            accentColor == ThemeStyles.DYNAMIC_GRADIENT && canDynamic -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            accentColor == ThemeStyles.DYNAMIC_GRADIENT -> schemeFromSeed(Palette.accents.first(), dark)
            useDynamic && dark -> dynamicDarkColorScheme(context)
            useDynamic -> dynamicLightColorScheme(context)
            accentColor == ThemeStyles.MONOCHROME -> monochromeScheme(dark)
            gradient != null -> schemeFromSeed(gradient.start.toArgb(), dark)
            else -> schemeFromSeed(accentColor, dark)
        }
    }
    // Gradient themes: vivid ends for strong elements, soft (container-tone) ends for cards.
    val brand = remember(scheme, accentColor, dark) {
        val ends = when {
            accentColor == ThemeStyles.DYNAMIC_GRADIENT -> scheme.primary to scheme.tertiary
            gradient != null && !useDynamic -> gradient.start to gradient.end
            else -> null
        }
        ends?.let { (a, b) ->
            val soft = { c: Color -> if (dark) androidx.compose.ui.graphics.lerp(c, Color.Black, 0.55f) else androidx.compose.ui.graphics.lerp(c, Color.White, 0.78f) }
            BrandGradient(listOf(a, b), listOf(soft(a), soft(b)))
        }
    }
    CompositionLocalProvider(LocalDaylineColors provides DaylineColors(dark), LocalBrandGradient provides brand) {
        MaterialExpressiveTheme(
            colorScheme = scheme,
            motionScheme = MotionScheme.expressive(),
            shapes = DaylineShapes,
            typography = DaylineTypography,
        ) {
            // Text outside a Surface/Scaffold (onboarding, the launch intro, overlays, dialogs drawn in a
            // plain Box) otherwise inherits Compose's default content colour, which is black and becomes
            // unreadable in dark theme. Every screen starts from the theme's on-surface colour instead.
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides scheme.onSurface, content = content)
        }
    }
}

/** Convenience accessors. */
object DaylineTheme {
    val isDark: Boolean @Composable get() = LocalDaylineColors.current.isDark
}


/** The gradient of a gradient theme: [strong] for buttons and rings, [soft] for card backgrounds. */
data class BrandGradient(val strong: List<Color>, val soft: List<Color>)

val LocalBrandGradient = androidx.compose.runtime.staticCompositionLocalOf<BrandGradient?> { null }

/** Background for a primary container: the soft gradient in gradient themes, else the plain colour. */
@Composable
fun brandContainerBrush(): androidx.compose.ui.graphics.Brush =
    LocalBrandGradient.current?.let { androidx.compose.ui.graphics.Brush.linearGradient(it.soft) }
        ?: androidx.compose.ui.graphics.SolidColor(androidx.compose.material3.MaterialTheme.colorScheme.primaryContainer)
