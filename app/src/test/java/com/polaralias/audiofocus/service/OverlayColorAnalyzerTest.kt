package com.polaralias.audiofocus.service

import android.graphics.Color
import com.polaralias.audiofocus.data.OverlayFillMode
import com.polaralias.audiofocus.data.OverlayPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayColorAnalyzerTest {

    @Test
    fun `solid color container is fully opaque`() {
        val overlayColor = Color.argb(0xFF, 0x12, 0x34, 0x56)
        val preferences = OverlayPreferences(
            fillMode = OverlayFillMode.SOLID_COLOR,
            overlayColor = overlayColor
        )

        val scheme = OverlayColorAnalyzer.fallbackFor(preferences)

        assertEquals(0xFF, Color.alpha(scheme.containerColor))
        assertEquals(Color.alpha(overlayColor), Color.alpha(scheme.containerColor))
    }
}
