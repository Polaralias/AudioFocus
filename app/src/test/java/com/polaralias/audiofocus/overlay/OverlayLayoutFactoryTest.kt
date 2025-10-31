package com.polaralias.audiofocus.overlay

import android.content.Context
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.polaralias.audiofocus.model.OverlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OverlayLayoutFactoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun fullscreenMaskUsesMatchParentHeight() {
        val params = OverlayLayoutFactory.maskLayoutFor(context, OverlayState.Fullscreen())
        assertNotNull(params)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params!!.height)
    }

    @Test
    fun partialMaskCoversFiveSixthsOfScreen() {
        val state = OverlayState.Partial()
        val params = OverlayLayoutFactory.maskLayoutFor(context, state)
        val displayHeight = context.resources.displayMetrics.heightPixels
        val expected = (displayHeight * state.heightRatio).toInt()
        assertEquals(expected, params!!.height)
    }
}
