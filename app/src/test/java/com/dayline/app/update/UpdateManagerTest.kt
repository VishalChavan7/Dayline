package com.dayline.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManagerTest {
    @Test fun newerVersionsAreDetected() {
        assertTrue(UpdateManager.isNewer("1.1.0", "1.0.1"))
        assertTrue(UpdateManager.isNewer("v1.0.2", "1.0.1"))
        assertTrue(UpdateManager.isNewer("1.10.0", "1.9.9"))
        assertTrue(UpdateManager.isNewer("2.0", "1.9.9"))
    }

    @Test fun sameOrOlderVersionsAreNotUpdates() {
        assertFalse(UpdateManager.isNewer("1.0.1", "1.0.1"))
        assertFalse(UpdateManager.isNewer("1.0.0", "1.0.1"))
        assertFalse(UpdateManager.isNewer("1.0", "1.0.0"))
        assertFalse(UpdateManager.isNewer("1.1.0", "1.1.0-debug"))
    }

    @Test fun releaseNotesBecomePlainLines() {
        val md = """
            A small fix-up release.

            ## Fixed
            - **Tutorial titles** are readable in dark theme.
            - Uses `onSurface` for text.

            ## Install or update
            Download the APK.

            SHA-256 of the APK: `abc`
        """.trimIndent()
        assertEquals(
            "A small fix-up release.\nTutorial titles are readable in dark theme.\nUses onSurface for text.",
            UpdateManager.summary(md),
        )
    }
}
