package com.polaralias.audiofocus.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPolicyTest {

    // YouTube Policy Tests

    @Test
    fun `youtube fullscreen with visible video shows full overlay`() {
        val state = ContextState(
            app = App.YOUTUBE,
            windowState = WindowState.FULLSCREEN,
            playback = Playback.PLAYING_VIDEO_VISIBLE
        )
        assertEquals(OverlayBehaviour.FULL_SCREEN_OVERLAY, OverlayPolicy.decideOverlay(state))
    }

    @Test
    fun `youtube minimized in-app with visible video shows full overlay`() {
        val state = ContextState(
            app = App.YOUTUBE,
            windowState = WindowState.MINIMIZED_IN_APP,
            playback = Playback.PLAYING_VIDEO_VISIBLE
        )
        assertEquals(OverlayBehaviour.FULL_SCREEN_OVERLAY, OverlayPolicy.decideOverlay(state))
    }

    @Test
    fun `youtube pip with visible video shows full overlay`() {
        val state = ContextState(
            app = App.YOUTUBE,
            windowState = WindowState.PIP,
            playback = Playback.PLAYING_VIDEO_VISIBLE
        )
        assertEquals(OverlayBehaviour.FULL_SCREEN_OVERLAY, OverlayPolicy.decideOverlay(state))
    }

    @Test
    fun `youtube paused shows no overlay`() {
        WindowState.values().forEach { windowState ->
            val state = ContextState(
                app = App.YOUTUBE,
                windowState = windowState,
                playback = Playback.PAUSED
            )
            assertEquals(
                "YouTube paused with $windowState should show no overlay",
                OverlayBehaviour.NONE,
                OverlayPolicy.decideOverlay(state)
            )
        }
    }

    @Test
    fun `youtube stopped shows no overlay`() {
        WindowState.values().forEach { windowState ->
            val state = ContextState(
                app = App.YOUTUBE,
                windowState = windowState,
                playback = Playback.STOPPED
            )
            assertEquals(
                "YouTube stopped with $windowState should show no overlay",
                OverlayBehaviour.NONE,
                OverlayPolicy.decideOverlay(state)
            )
        }
    }

    @Test
    fun `youtube background playback shows no overlay`() {
        WindowState.values().forEach { windowState ->
            val state = ContextState(
                app = App.YOUTUBE,
                windowState = windowState,
                playback = Playback.PLAYING_BACKGROUND
            )
            assertEquals(
                "YouTube background playback with $windowState should show no overlay",
                OverlayBehaviour.NONE,
                OverlayPolicy.decideOverlay(state)
            )
        }
    }

    @Test
    fun `youtube background window state with visible video shows no overlay`() {
        val state = ContextState(
            app = App.YOUTUBE,
            windowState = WindowState.BACKGROUND,
            playback = Playback.PLAYING_VIDEO_VISIBLE
        )
        assertEquals(OverlayBehaviour.NONE, OverlayPolicy.decideOverlay(state))
    }

    @Test
    fun `youtube non-fullscreen video with visible playback shows no overlay`() {
        val state = ContextState(
            app = App.YOUTUBE,
            windowState = WindowState.NON_FULLSCREEN_VIDEO,
            playback = Playback.PLAYING_VIDEO_VISIBLE
        )
        assertEquals(OverlayBehaviour.NONE, OverlayPolicy.decideOverlay(state))
    }

    // YouTube Music Policy Tests

    @Test
    fun `youtube music fullscreen video shows full overlay`() {
        val state = ContextState(
            app = App.YTMUSIC,
            windowState = WindowState.FULLSCREEN,
            playback = Playback.PLAYING_VIDEO_VISIBLE
        )
        assertEquals(OverlayBehaviour.FULL_SCREEN_OVERLAY, OverlayPolicy.decideOverlay(state))
    }

    @Test
    fun `youtube music minimized in-app with video shows partial overlay`() {
        val state = ContextState(
            app = App.YTMUSIC,
            windowState = WindowState.MINIMIZED_IN_APP,
            playback = Playback.PLAYING_VIDEO_VISIBLE
        )
        assertEquals(OverlayBehaviour.PARTIAL_80_PASS_THROUGH, OverlayPolicy.decideOverlay(state))
    }

    @Test
    fun `youtube music non-fullscreen video shows partial overlay`() {
        val state = ContextState(
            app = App.YTMUSIC,
            windowState = WindowState.NON_FULLSCREEN_VIDEO,
            playback = Playback.PLAYING_VIDEO_VISIBLE
        )
        assertEquals(OverlayBehaviour.PARTIAL_80_PASS_THROUGH, OverlayPolicy.decideOverlay(state))
    }

    @Test
    fun `youtube music pip with video shows partial overlay`() {
        val state = ContextState(
            app = App.YTMUSIC,
            windowState = WindowState.PIP,
            playback = Playback.PLAYING_VIDEO_VISIBLE
        )
        assertEquals(OverlayBehaviour.PARTIAL_80_PASS_THROUGH, OverlayPolicy.decideOverlay(state))
    }

    @Test
    fun `youtube music background window shows no overlay`() {
        Playback.values().forEach { playback ->
            val state = ContextState(
                app = App.YTMUSIC,
                windowState = WindowState.BACKGROUND,
                playback = playback
            )
            assertEquals(
                "YouTube Music background with $playback should show no overlay",
                OverlayBehaviour.NONE,
                OverlayPolicy.decideOverlay(state)
            )
        }
    }

    @Test
    fun `youtube music paused shows no overlay`() {
        WindowState.values().forEach { windowState ->
            val state = ContextState(
                app = App.YTMUSIC,
                windowState = windowState,
                playback = Playback.PAUSED
            )
            assertEquals(
                "YouTube Music paused with $windowState should show no overlay",
                OverlayBehaviour.NONE,
                OverlayPolicy.decideOverlay(state)
            )
        }
    }

    @Test
    fun `youtube music stopped shows no overlay`() {
        WindowState.values().forEach { windowState ->
            val state = ContextState(
                app = App.YTMUSIC,
                windowState = windowState,
                playback = Playback.STOPPED
            )
            assertEquals(
                "YouTube Music stopped with $windowState should show no overlay",
                OverlayBehaviour.NONE,
                OverlayPolicy.decideOverlay(state)
            )
        }
    }

    @Test
    fun `youtube music background playback shows no overlay`() {
        WindowState.values().forEach { windowState ->
            val state = ContextState(
                app = App.YTMUSIC,
                windowState = windowState,
                playback = Playback.PLAYING_BACKGROUND
            )
            assertEquals(
                "YouTube Music background playback with $windowState should show no overlay",
                OverlayBehaviour.NONE,
                OverlayPolicy.decideOverlay(state)
            )
        }
    }

    // Other App Tests

    @Test
    fun `other apps never show overlay`() {
        WindowState.values().forEach { windowState ->
            Playback.values().forEach { playback ->
                val state = ContextState(
                    app = App.OTHER,
                    windowState = windowState,
                    playback = playback
                )
                assertEquals(
                    "OTHER app with $windowState and $playback should show no overlay",
                    OverlayBehaviour.NONE,
                    OverlayPolicy.decideOverlay(state)
                )
            }
        }
    }

    // Policy Matrix Validation Test
    // This test explicitly validates the entire policy matrix from the requirements

    @Test
    fun `policy matrix validation for youtube`() {
        val testCases = listOf(
            // YouTube: Fullscreen, minimised in-app, or PiP | Visible video playback | Full-screen overlay
            Triple(WindowState.FULLSCREEN, Playback.PLAYING_VIDEO_VISIBLE, OverlayBehaviour.FULL_SCREEN_OVERLAY),
            Triple(WindowState.MINIMIZED_IN_APP, Playback.PLAYING_VIDEO_VISIBLE, OverlayBehaviour.FULL_SCREEN_OVERLAY),
            Triple(WindowState.PIP, Playback.PLAYING_VIDEO_VISIBLE, OverlayBehaviour.FULL_SCREEN_OVERLAY),

            // YouTube: Any | Paused, stopped | No overlay
            Triple(WindowState.FULLSCREEN, Playback.PAUSED, OverlayBehaviour.NONE),
            Triple(WindowState.FULLSCREEN, Playback.STOPPED, OverlayBehaviour.NONE),
            Triple(WindowState.MINIMIZED_IN_APP, Playback.PAUSED, OverlayBehaviour.NONE),
            Triple(WindowState.MINIMIZED_IN_APP, Playback.STOPPED, OverlayBehaviour.NONE),
            Triple(WindowState.PIP, Playback.PAUSED, OverlayBehaviour.NONE),
            Triple(WindowState.PIP, Playback.STOPPED, OverlayBehaviour.NONE),
            Triple(WindowState.BACKGROUND, Playback.PAUSED, OverlayBehaviour.NONE),
            Triple(WindowState.BACKGROUND, Playback.STOPPED, OverlayBehaviour.NONE),
            Triple(WindowState.NON_FULLSCREEN_VIDEO, Playback.PAUSED, OverlayBehaviour.NONE),
            Triple(WindowState.NON_FULLSCREEN_VIDEO, Playback.STOPPED, OverlayBehaviour.NONE),

            // YouTube: Background | non-visible video playback (background playback) | No overlay
            Triple(WindowState.BACKGROUND, Playback.PLAYING_BACKGROUND, OverlayBehaviour.NONE),
            Triple(WindowState.FULLSCREEN, Playback.PLAYING_BACKGROUND, OverlayBehaviour.NONE),
            Triple(WindowState.MINIMIZED_IN_APP, Playback.PLAYING_BACKGROUND, OverlayBehaviour.NONE),
            Triple(WindowState.PIP, Playback.PLAYING_BACKGROUND, OverlayBehaviour.NONE),
            Triple(WindowState.NON_FULLSCREEN_VIDEO, Playback.PLAYING_BACKGROUND, OverlayBehaviour.NONE),
        )

        testCases.forEach { (windowState, playback, expected) ->
            val state = ContextState(App.YOUTUBE, windowState, playback)
            assertEquals(
                "YouTube with $windowState and $playback should be $expected",
                expected,
                OverlayPolicy.decideOverlay(state)
            )
        }
    }

    @Test
    fun `policy matrix validation for youtube music`() {
        val testCases = listOf(
            // YouTube Music: Fullscreen video | Video playback | Full-screen overlay
            Triple(WindowState.FULLSCREEN, Playback.PLAYING_VIDEO_VISIBLE, OverlayBehaviour.FULL_SCREEN_OVERLAY),

            // YouTube Music: Miniplayer or non-fullscreen video | Video playback | Partial overlay covering bottom 80%
            Triple(WindowState.MINIMIZED_IN_APP, Playback.PLAYING_VIDEO_VISIBLE, OverlayBehaviour.PARTIAL_80_PASS_THROUGH),
            Triple(WindowState.NON_FULLSCREEN_VIDEO, Playback.PLAYING_VIDEO_VISIBLE, OverlayBehaviour.PARTIAL_80_PASS_THROUGH),
            Triple(WindowState.PIP, Playback.PLAYING_VIDEO_VISIBLE, OverlayBehaviour.PARTIAL_80_PASS_THROUGH),

            // YouTube Music: Background | Background playback | No overlay
            Triple(WindowState.BACKGROUND, Playback.PLAYING_BACKGROUND, OverlayBehaviour.NONE),
            Triple(WindowState.BACKGROUND, Playback.PLAYING_VIDEO_VISIBLE, OverlayBehaviour.NONE),
            Triple(WindowState.BACKGROUND, Playback.PAUSED, OverlayBehaviour.NONE),
            Triple(WindowState.BACKGROUND, Playback.STOPPED, OverlayBehaviour.NONE),

            // YouTube Music: Any | Paused, stopped | No overlay
            Triple(WindowState.FULLSCREEN, Playback.PAUSED, OverlayBehaviour.NONE),
            Triple(WindowState.FULLSCREEN, Playback.STOPPED, OverlayBehaviour.NONE),
            Triple(WindowState.MINIMIZED_IN_APP, Playback.PAUSED, OverlayBehaviour.NONE),
            Triple(WindowState.MINIMIZED_IN_APP, Playback.STOPPED, OverlayBehaviour.NONE),
            Triple(WindowState.PIP, Playback.PAUSED, OverlayBehaviour.NONE),
            Triple(WindowState.PIP, Playback.STOPPED, OverlayBehaviour.NONE),
            Triple(WindowState.NON_FULLSCREEN_VIDEO, Playback.PAUSED, OverlayBehaviour.NONE),
            Triple(WindowState.NON_FULLSCREEN_VIDEO, Playback.STOPPED, OverlayBehaviour.NONE),

            // YouTube Music: Any state with background playback | No overlay
            Triple(WindowState.FULLSCREEN, Playback.PLAYING_BACKGROUND, OverlayBehaviour.NONE),
            Triple(WindowState.MINIMIZED_IN_APP, Playback.PLAYING_BACKGROUND, OverlayBehaviour.NONE),
            Triple(WindowState.PIP, Playback.PLAYING_BACKGROUND, OverlayBehaviour.NONE),
            Triple(WindowState.NON_FULLSCREEN_VIDEO, Playback.PLAYING_BACKGROUND, OverlayBehaviour.NONE),
        )

        testCases.forEach { (windowState, playback, expected) ->
            val state = ContextState(App.YTMUSIC, windowState, playback)
            assertEquals(
                "YouTube Music with $windowState and $playback should be $expected",
                expected,
                OverlayPolicy.decideOverlay(state)
            )
        }
    }
}
