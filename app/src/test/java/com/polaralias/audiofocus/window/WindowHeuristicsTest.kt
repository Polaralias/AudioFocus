package com.polaralias.audiofocus.window

import android.content.Context
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
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

    @Before
    fun resetInferenceStore() {
        WindowInferenceStore.resetForTests()
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
    fun evaluate_infersPiPWindowFromTitleOnColdStart() {
        val heuristics = WindowHeuristics(context)

        val pipWindowWithoutRoot = mock<AccessibilityWindowInfo> {
            on { id } doReturn(42)
            on { type } doReturn(TYPE_PINNED)
            on { isActive } doReturn(true)
            on { root } doReturn(null)
            on { title } doReturn("YouTube")
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
        assertEquals("com.google.android.youtube", result.focusedPackage)
    }

    @Test
    fun evaluate_reusesPiPMappingAcrossInstances() {
        val heuristicsWithRoot = WindowHeuristics(context)

        val rootNode = AccessibilityNodeInfo.obtain().apply {
            packageName = "com.google.android.youtube"
        }

        val pipWindowWithRoot = mock<AccessibilityWindowInfo> {
            on { id } doReturn(77)
            on { type } doReturn(TYPE_PINNED)
            on { isActive } doReturn(false)
            on { root } doReturn(rootNode)
        }
        whenever(pipWindowWithRoot.getBoundsInScreen(any())).thenAnswer { invocation ->
            val rect = invocation.arguments[0] as Rect
            rect.set(0, 0, 200, 200)
            null
        }
        heuristicsWithRoot.evaluate(listOf(pipWindowWithRoot), metrics)

        val coldStartHeuristics = WindowHeuristics(context)
        val pipWindowWithoutRoot = mock<AccessibilityWindowInfo> {
            on { id } doReturn(77)
            on { type } doReturn(TYPE_PINNED)
            on { isActive } doReturn(false)
            on { root } doReturn(null)
        }
        whenever(pipWindowWithoutRoot.getBoundsInScreen(any())).thenAnswer { invocation ->
            val rect = invocation.arguments[0] as Rect
            rect.set(0, 0, 200, 200)
            null
        }

        val result = coldStartHeuristics.evaluate(listOf(pipWindowWithoutRoot), metrics)
        val entry = result.appWindows["com.google.android.youtube"]

        assertNotNull(entry)
        entry!!
        assertEquals(WindowState.PICTURE_IN_PICTURE, entry.state)
        assertTrue(entry.hasVisibleVideoSurface)
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

    @Test
    fun evaluate_keepsSnapshotAcrossTransientSystemUi() {
        var now = 0L
        val heuristics = WindowHeuristics(context) { now }

        val youtubeWindow = createWindow(
            id = 100,
            packageName = "com.google.android.youtube",
            bounds = Rect(0, 0, metrics.widthPixels, metrics.heightPixels),
            isActive = true,
        )

        val initial = heuristics.evaluate(listOf(youtubeWindow), metrics)
        assertNotNull(initial.appWindows["com.google.android.youtube"])

        val systemUiWindow = createWindow(
            id = 200,
            packageName = "com.android.systemui",
            type = AccessibilityWindowInfo.TYPE_SYSTEM,
            title = "Notification shade",
        )

        now += 32
        val cached = heuristics.evaluate(listOf(systemUiWindow), metrics)
        assertNotEquals(WindowInfo.Empty, cached)
        assertNotNull(cached.appWindows["com.google.android.youtube"])

        now += 800
        val expired = heuristics.evaluate(listOf(systemUiWindow), metrics)
        assertEquals(WindowInfo.Empty, expired)
    }

    @Test
    fun evaluate_clearsSnapshotWhenBackgroundDetected() {
        var now = 0L
        val heuristics = WindowHeuristics(context) { now }

        val visibleWindow = createWindow(
            id = 101,
            packageName = "com.google.android.youtube",
            bounds = Rect(0, 0, metrics.widthPixels, metrics.heightPixels),
            isActive = true,
        )
        heuristics.evaluate(listOf(visibleWindow), metrics)

        val backgroundWindow = createWindow(
            id = 102,
            packageName = "com.google.android.youtube",
            bounds = Rect(0, 0, 1, 1),
            isActive = true,
        )

        val backgroundResult = heuristics.evaluate(listOf(backgroundWindow), metrics)
        assertEquals(WindowInfo.Empty, backgroundResult)

        val systemUiWindow = createWindow(
            id = 201,
            packageName = "com.android.systemui",
            type = AccessibilityWindowInfo.TYPE_SYSTEM,
            title = "Volume",
        )
        now += 10
        val afterBackground = heuristics.evaluate(listOf(systemUiWindow), metrics)
        assertEquals(WindowInfo.Empty, afterBackground)
    }

    private fun createWindow(
        id: Int,
        packageName: String,
        type: Int = AccessibilityWindowInfo.TYPE_APPLICATION,
        bounds: Rect = Rect(0, 0, metrics.widthPixels, metrics.heightPixels),
        isActive: Boolean = false,
        title: String? = null,
    ): AccessibilityWindowInfo {
        val window = mock<AccessibilityWindowInfo> {
            on { this.id } doReturn id
            on { this.type } doReturn type
            on { this.isActive } doReturn isActive
            on { this.title } doReturn title
            on { this.root } doAnswer {
                AccessibilityNodeInfo.obtain().apply { this.packageName = packageName }
            }
        }
        whenever(window.getBoundsInScreen(any())).thenAnswer { invocation ->
            val rect = invocation.arguments[0] as Rect
            rect.set(bounds)
            null
        }
        return window
    }
}
