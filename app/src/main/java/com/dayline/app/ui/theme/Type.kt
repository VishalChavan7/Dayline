package com.dayline.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.dayline.app.R

/** Google Sans Flex (OFL) — the typeface of Material 3 Expressive — bundled as static instances. */
val SansFlex = FontFamily(
    Font(R.font.gsf_regular, FontWeight.Normal),
    Font(R.font.gsf_medium, FontWeight.Medium),
    Font(R.font.gsf_semibold, FontWeight.SemiBold),
    Font(R.font.gsf_display, FontWeight.Bold),
    Font(R.font.gsf_display_heavy, FontWeight.ExtraBold),
)

/** Rounded, heavier display cut used for headlines (expressive "emphasized" styles). */
val SansFlexDisplay = FontFamily(
    Font(R.font.gsf_display, FontWeight.Bold),
    Font(R.font.gsf_display_heavy, FontWeight.ExtraBold),
    Font(R.font.gsf_semibold, FontWeight.SemiBold),
    Font(R.font.gsf_medium, FontWeight.Medium),
    Font(R.font.gsf_regular, FontWeight.Normal),
)

private fun style(size: Int, line: Int, weight: FontWeight, family: FontFamily = SansFlex, tracking: Double = 0.0) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

val DaylineTypography = Typography(
    displayLarge = style(57, 64, FontWeight.ExtraBold, SansFlexDisplay, -0.02),
    displayMedium = style(45, 52, FontWeight.ExtraBold, SansFlexDisplay, -0.015),
    displaySmall = style(36, 44, FontWeight.Bold, SansFlexDisplay, -0.01),
    headlineLarge = style(32, 40, FontWeight.Bold, SansFlexDisplay, -0.01),
    headlineMedium = style(28, 36, FontWeight.Bold, SansFlexDisplay, -0.005),
    headlineSmall = style(24, 32, FontWeight.Bold, SansFlexDisplay),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(16, 24, FontWeight.SemiBold, tracking = 0.005),
    titleSmall = style(14, 20, FontWeight.SemiBold, tracking = 0.005),
    bodyLarge = style(16, 24, FontWeight.Normal, tracking = 0.01),
    bodyMedium = style(14, 20, FontWeight.Normal, tracking = 0.01),
    bodySmall = style(12, 16, FontWeight.Normal, tracking = 0.02),
    labelLarge = style(14, 20, FontWeight.Medium, tracking = 0.01),
    labelMedium = style(12, 16, FontWeight.Medium, tracking = 0.03),
    labelSmall = style(11, 16, FontWeight.Medium, tracking = 0.04),
)
