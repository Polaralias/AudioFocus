package com.polaralias.audiofocus.service

import com.polaralias.audiofocus.data.OverlayFillMode
import com.polaralias.audiofocus.data.OverlayPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayColorAnalyzerTest {

    @Test
    fun `solid color container is fully opaque`() {
        val overlayColor = argb(0xFF, 0x12, 0x34, 0x56)
        val preferences = OverlayPreferences(
            fillMode = OverlayFillMode.SOLID_COLOR,
            overlayColor = overlayColor
        )

        val scheme = OverlayColorAnalyzer.fallbackFor(preferences)

        assertEquals(0xFF, alphaOf(scheme.containerColor))
        assertEquals(alphaOf(overlayColor), alphaOf(scheme.containerColor))
    }

    private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
        return ((alpha and 0xFF) shl 24) or
            ((red and 0xFF) shl 16) or
            ((green and 0xFF) shl 8) or
            (blue and 0xFF)
    }

    private fun alphaOf(color: Int): Int = color ushr 24 and 0xFF
}
