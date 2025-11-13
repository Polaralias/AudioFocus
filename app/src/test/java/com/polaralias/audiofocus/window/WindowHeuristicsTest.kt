package com.polaralias.audiofocus.window

import android.content.Context
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WindowHeuristicsTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val metrics = DisplayMetrics().apply {
        widthPixels = 1080
        heightPixels = 1920
    }

    @Test
    fun evaluate_includesPiPWindowWithoutRootUsingCachedPackage() {
        val heuristics = WindowHeuristics(context)

        val rootNode = AccessibilityNodeInfo.obtain().apply {
            packageName = "com.google.android.youtube"
        }

        val pipWindowTypePinned = 4 // Matches WindowHeuristics.determineWindowState PiP type check

        val pipWindowWithRoot = mock<AccessibilityWindowInfo> {
            on { id } doReturn(1)
            on { type } doReturn(pipWindowTypePinned)
            on { isActive } doReturn(false)
            on { root } doReturn(rootNode)
        }
        whenever(pipWindowWithRoot.getBoundsInScreen(any())).thenAnswer { invocation ->
            val rect = invocation.arguments[0] as Rect
            rect.set(0, 0, 200, 200)
            null
        }

        heuristics.evaluate(listOf(pipWindowWithRoot), metrics)

        val pipWindowWithoutRoot = mock<AccessibilityWindowInfo> {
            on { id } doReturn(1)
            on { type } doReturn(pipWindowTypePinned)
            on { isActive } doReturn(false)
            on { root } doReturn(null)
        }
        whenever(pipWindowWithoutRoot.getBoundsInScreen(any())).thenAnswer { invocation ->
            val rect = invocation.arguments[0] as Rect
            rect.set(0, 0, 200, 200)
            null
        }

        val result = heuristics.evaluate(listOf(pipWindowWithoutRoot), metrics)
        val entry = result.appWindows["com.google.android.youtube"]

        assertNotNull(entry)
        entry!!
        assertEquals(WindowState.PICTURE_IN_PICTURE, entry.state)
        assertTrue(entry.hasVisibleVideoSurface)
    }
}
