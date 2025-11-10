package com.polaralias.audiofocus.overlay

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions

class OverlayControllerTest {

    private lateinit var mockApplier: OverlayApplier
    private lateinit var controller: OverlayController

    @Before
    fun setup() {
        mockApplier = mock()
        controller = OverlayController(mockApplier)
    }

    @Test
    fun `update shows full screen overlay when policy decides full screen`() {
        val state = ContextState(
            app = App.YOUTUBE,
            windowState = WindowState.FULLSCREEN,
            playback = Playback.PLAYING_VIDEO_VISIBLE
        )

        controller.update(state)

        verify(mockApplier).showFullScreenOverlay()
        verifyNoMoreInteractions(mockApplier)
    }

    @Test
    fun `update shows partial overlay when policy decides partial`() {
        val state = ContextState(
            app = App.YTMUSIC,
            windowState = WindowState.MINIMIZED_IN_APP,
            playback = Playback.PLAYING_VIDEO_VISIBLE
        )

        controller.update(state)

        verify(mockApplier).showPartialOverlayBottom80PassThrough()
        verifyNoMoreInteractions(mockApplier)
    }

    @Test
    fun `update hides overlay when policy decides none`() {
        val state = ContextState(
            app = App.YOUTUBE,
            windowState = WindowState.FULLSCREEN,
            playback = Playback.PAUSED
        )

        controller.update(state)

        verify(mockApplier).hideOverlay()
        verifyNoMoreInteractions(mockApplier)
    }

    @Test
    fun `update does not call applier when behaviour unchanged`() {
        val state1 = ContextState(
            app = App.YOUTUBE,
            windowState = WindowState.FULLSCREEN,
            playback = Playback.PLAYING_VIDEO_VISIBLE
        )
        val state2 = ContextState(
            app = App.YOUTUBE,
            windowState = WindowState.PIP,
            playback = Playback.PLAYING_VIDEO_VISIBLE
        )

        controller.update(state1)
        controller.update(state2)

        // Both states result in FULL_SCREEN_OVERLAY, so applier should be called only once
        verify(mockApplier, times(1)).showFullScreenOverlay()
        verifyNoMoreInteractions(mockApplier)
    }

    @Test
    fun `update calls applier when behaviour changes`() {
        val state1 = ContextState(
            app = App.YOUTUBE,
            windowState = WindowState.FULLSCREEN,
            playback = Playback.PLAYING_VIDEO_VISIBLE
        )
        val state2 = ContextState(
            app = App.YOUTUBE,
            windowState = WindowState.FULLSCREEN,
            playback = Playback.PAUSED
        )

        controller.update(state1)
        controller.update(state2)

        verify(mockApplier).showFullScreenOverlay()
        verify(mockApplier).hideOverlay()
        verifyNoMoreInteractions(mockApplier)
    }

    @Test
    fun `mapPackageToApp returns YOUTUBE for youtube package`() {
        val app = OverlayController.mapPackageToApp("com.google.android.youtube")
        assertEquals(App.YOUTUBE, app)
    }

    @Test
    fun `mapPackageToApp returns YTMUSIC for youtube music package`() {
        val app = OverlayController.mapPackageToApp("com.google.android.apps.youtube.music")
        assertEquals(App.YTMUSIC, app)
    }

    @Test
    fun `mapPackageToApp returns OTHER for unknown package`() {
        assertEquals(App.OTHER, OverlayController.mapPackageToApp("com.spotify.music"))
        assertEquals(App.OTHER, OverlayController.mapPackageToApp("com.example.app"))
        assertEquals(App.OTHER, OverlayController.mapPackageToApp(null))
    }

    @Test
    fun `package constants match expected values`() {
        assertEquals("com.google.android.youtube", OverlayController.YOUTUBE_PACKAGE)
        assertEquals("com.google.android.apps.youtube.music", OverlayController.YTMUSIC_PACKAGE)
    }
}
