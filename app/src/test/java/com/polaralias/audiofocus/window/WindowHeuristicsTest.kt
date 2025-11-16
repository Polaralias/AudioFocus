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

private const val TYPE_PINNED = 4 // Matches WindowHeuristics.determineWindowState PiP type check

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

        val pipWindowWithRoot = mock<AccessibilityWindowInfo> {
            on { id } doReturn(1)
            on { type } doReturn(TYPE_PINNED)
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
            on { type } doReturn(TYPE_PINNED)
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
        assertEquals(PlayMode.VIDEO, entry.playMode)
    }

    @Test
    fun evaluate_doesNotOverwriteFocusWithUnsupportedPackage() {
        val heuristics = WindowHeuristics(context)

        val youtubeRoot = AccessibilityNodeInfo.obtain().apply {
            packageName = "com.google.android.youtube"
        }

        val initialPipWindow = mock<AccessibilityWindowInfo> {
            on { id } doReturn(1)
            on { type } doReturn(TYPE_PINNED)
            on { isActive } doReturn(false)
            on { root } doReturn(youtubeRoot)
        }
        whenever(initialPipWindow.getBoundsInScreen(any())).thenAnswer { invocation ->
            val rect = invocation.arguments[0] as Rect
            rect.set(0, 0, 200, 200)
            null
        }

        heuristics.evaluate(listOf(initialPipWindow), metrics)

        val unsupportedRoot = AccessibilityNodeInfo.obtain().apply {
            packageName = "com.example.unsupported"
        }

        val unsupportedWindow = mock<AccessibilityWindowInfo> {
            on { id } doReturn(2)
            on { type } doReturn(1)
            on { isActive } doReturn(true)
            on { root } doReturn(unsupportedRoot)
        }
        whenever(unsupportedWindow.getBoundsInScreen(any())).thenAnswer { invocation ->
            val rect = invocation.arguments[0] as Rect
            rect.set(0, 0, metrics.widthPixels, metrics.heightPixels)
            null
        }

        val pipWindowWithoutRoot = mock<AccessibilityWindowInfo> {
            on { id } doReturn(1)
            on { type } doReturn(TYPE_PINNED)
            on { isActive } doReturn(false)
            on { root } doReturn(null)
        }
        whenever(pipWindowWithoutRoot.getBoundsInScreen(any())).thenAnswer { invocation ->
            val rect = invocation.arguments[0] as Rect
            rect.set(0, 0, 200, 200)
            null
        }

        val result = heuristics.evaluate(listOf(unsupportedWindow, pipWindowWithoutRoot), metrics)

        val entry = result.appWindows["com.google.android.youtube"]

        assertNotNull(entry)
        entry!!
        assertEquals(WindowState.PICTURE_IN_PICTURE, entry.state)
        assertTrue(entry.hasVisibleVideoSurface)
        assertEquals(PlayMode.VIDEO, entry.playMode)
        assertEquals(null, result.focusedPackage)
    }
}
