package com.polaralias.audiofocus.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.test.core.app.ApplicationProvider
import com.polaralias.audiofocus.model.OverlayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayLayoutFactoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun fullscreenMaskUsesMatchParentHeight() {
        val params = OverlayLayoutFactory.maskLayoutFor(context, OverlayState.Fullscreen)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.height)
    }

    @Test
    fun maskLayoutUsesMatchParentWidth() {
        val params = OverlayLayoutFactory.maskLayoutFor(context, OverlayState.Fullscreen)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.width)
    }

    @Test
    fun controlsLayoutIsCentered() {
        val params = OverlayLayoutFactory.controlsLayout()
        assertNotNull(params)
        assertEquals(Gravity.CENTER, params.gravity)
    }

    @Test
    fun controlsLayoutHasMatchParentWidth() {
        val params = OverlayLayoutFactory.controlsLayout()
        assertNotNull(params)
        assertEquals(WindowManager.LayoutParams.MATCH_PARENT, params.width)
    }

    @Test
    fun controlsLayoutHasWrapContentHeight() {
        val params = OverlayLayoutFactory.controlsLayout()
        assertNotNull(params)
        assertEquals(WindowManager.LayoutParams.WRAP_CONTENT, params.height)
    }

    @Test
    fun maskLayoutUsesOpaquePixelFormat() {
        val params = OverlayLayoutFactory.maskLayoutFor(context, OverlayState.Fullscreen)
        assertEquals(PixelFormat.OPAQUE, params.format)
    }

    @Test
    fun maskLayoutIncludesLayoutNoLimitsFlag() {
        val params = OverlayLayoutFactory.maskLayoutFor(context, OverlayState.Fullscreen)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0)
    }

    @Test
    fun fullscreenMaskBlocksTouches() {
        val params = OverlayLayoutFactory.maskLayoutFor(context, OverlayState.Fullscreen)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE == 0)
    }

    @Test
    fun hiddenMaskAllowsTouchPassthrough() {
        val params = OverlayLayoutFactory.maskLayoutFor(context, OverlayState.None)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
    }

    @Test
    fun controlsLayoutWatchesOutsideTouchEvents() {
        val params = OverlayLayoutFactory.controlsLayout()
        assertNotNull(params)
        assertTrue(params!!.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH != 0)
    }
}
