package com.polaralias.audiofocus.overlay

import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayAnimatorTest {

    @Test
    fun `showImmediate sets view visible with alpha 1 without animation`() {
        val context = RuntimeEnvironment.getApplication()
        val view = View(context)
        view.visibility = View.GONE
        view.alpha = 0f

        OverlayAnimator.showImmediate(view)

        assertEquals("View should be visible immediately", View.VISIBLE, view.visibility)
        assertEquals("View alpha should be 1.0 immediately", 1f, view.alpha, 0.01f)
    }

    @Test
    fun `hideImmediate sets view gone with alpha 0 without animation`() {
        val context = RuntimeEnvironment.getApplication()
        val view = View(context)
        view.visibility = View.VISIBLE
        view.alpha = 1f

        OverlayAnimator.hideImmediate(view)

        assertEquals("View should be gone immediately", View.GONE, view.visibility)
        assertEquals("View alpha should be 0 immediately", 0f, view.alpha, 0.01f)
    }

    @Test
    fun `showImmediate cancels any ongoing animations`() {
        val context = RuntimeEnvironment.getApplication()
        val view = View(context)
        view.visibility = View.GONE
        view.alpha = 0.5f

        view.animate().alpha(1f).setDuration(200)

        OverlayAnimator.showImmediate(view)

        assertEquals("View should be visible", View.VISIBLE, view.visibility)
        assertEquals("View alpha should be 1.0", 1f, view.alpha, 0.01f)
    }

    @Test
    fun `hideImmediate cancels any ongoing animations`() {
        val context = RuntimeEnvironment.getApplication()
        val view = View(context)
        view.visibility = View.VISIBLE
        view.alpha = 0.5f

        view.animate().alpha(0f).setDuration(200)

        OverlayAnimator.hideImmediate(view)

        assertEquals("View should be gone", View.GONE, view.visibility)
        assertEquals("View alpha should be 0", 0f, view.alpha, 0.01f)
    }

    @Test
    fun `showImmediate from various initial states works correctly`() {
        val context = RuntimeEnvironment.getApplication()
        val view = View(context)

        view.visibility = View.GONE
        view.alpha = 0f
        OverlayAnimator.showImmediate(view)
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(1f, view.alpha, 0.01f)

        view.visibility = View.INVISIBLE
        view.alpha = 0.5f
        OverlayAnimator.showImmediate(view)
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals(1f, view.alpha, 0.01f)
    }

    @Test
    fun `hideImmediate from various initial states works correctly`() {
        val context = RuntimeEnvironment.getApplication()
        val view = View(context)

        view.visibility = View.VISIBLE
        view.alpha = 1f
        OverlayAnimator.hideImmediate(view)
        assertEquals(View.GONE, view.visibility)
        assertEquals(0f, view.alpha, 0.01f)

        view.visibility = View.VISIBLE
        view.alpha = 0.5f
        OverlayAnimator.hideImmediate(view)
        assertEquals(View.GONE, view.visibility)
        assertEquals(0f, view.alpha, 0.01f)
    }
}
