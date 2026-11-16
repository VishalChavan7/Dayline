package com.dayline.app.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dayline.app.core.model.ThemeMode
import com.dayline.app.ui.theme.DaylineTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Text drawn outside a Surface or Scaffold (onboarding, the launch intro, overlays) takes its colour
 * from LocalContentColor. The theme must set it to on-surface, or that text is black in dark theme.
 */
@RunWith(AndroidJUnit4::class)
class ThemeContentColorTest {
    @get:Rule val compose = createComposeRule()

    private fun contentColorIn(mode: ThemeMode): Pair<Color, Color> {
        var content = Color.Unspecified
        var onSurface = Color.Unspecified
        compose.setContent {
            DaylineTheme(themeMode = mode, dynamicColor = false) {
                content = LocalContentColor.current
                onSurface = MaterialTheme.colorScheme.onSurface
            }
        }
        compose.waitForIdle()
        return content to onSurface
    }

    @Test fun darkThemeTextIsNotBlack() {
        val (content, onSurface) = contentColorIn(ThemeMode.DARK)
        assertEquals(onSurface, content)
        assertNotEquals(Color.Black, content)
    }

    @Test fun lightThemeTextUsesOnSurface() {
        val (content, onSurface) = contentColorIn(ThemeMode.LIGHT)
        assertEquals(onSurface, content)
    }
}
