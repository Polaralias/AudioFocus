package com.polaralias.audiofocus.policy

import android.media.session.PlaybackState
import com.polaralias.audiofocus.data.OverlayPreferences
import com.polaralias.audiofocus.model.OverlayState
import com.polaralias.audiofocus.window.AppWindowInfo
import com.polaralias.audiofocus.window.PlayMode
import com.polaralias.audiofocus.window.WindowInfo
import com.polaralias.audiofocus.window.WindowState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PolicyEngineTest {
    private val playingState = PlaybackState.Builder()
        .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
        .setActions(
            PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SEEK_TO
        )
        .build()

    private val pausedState = PlaybackState.Builder()
        .setState(PlaybackState.STATE_PAUSED, 0L, 0f)
        .build()

    private val prefs = OverlayPreferences()

    @Test
    fun youtubePlayingInFullscreenShowsOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.youtube",
            playbackState = playingState,
            metadata = null,
            windowInfo = windowInfoFor(
                pkg = "com.google.android.youtube",
                state = WindowState.FULLSCREEN
            ),
            preferences = prefs
        )

        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.Fullscreen, result)
    }

    @Test
    fun youtubePiPShowsOverlayEvenWithoutSurfaceSignal() {
        val input = PolicyInput(
            packageName = "com.google.android.youtube",
            playbackState = playingState,
            metadata = null,
            windowInfo = windowInfoFor(
                pkg = "com.google.android.youtube",
                state = WindowState.PICTURE_IN_PICTURE,
                videoSurfaceFraction = 0f,
                playMode = PlayMode.VIDEO,
            ),
            preferences = prefs,
        )

        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.Fullscreen, result)
    }

    @Test
    fun youtubeMiniplayerUsesRelaxedSignal() {
        val input = PolicyInput(
            packageName = "com.google.android.youtube",
            playbackState = playingState,
            metadata = null,
            windowInfo = windowInfoFor(
                pkg = "com.google.android.youtube",
                state = WindowState.MINIMIZED_IN_APP,
                videoSurfaceFraction = 0f,
                playMode = PlayMode.VIDEO,
            ),
            preferences = prefs,
        )

        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.Fullscreen, result)
    }

    @Test
    fun youtubeInBackgroundHidesOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.youtube",
            playbackState = playingState,
            metadata = null,
            windowInfo = WindowInfo.Empty,
            preferences = prefs
        )

        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.None, result)
    }

    @Test
    fun youtubePausedHidesOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.youtube",
            playbackState = pausedState,
            metadata = null,
            windowInfo = windowInfoFor(
                pkg = "com.google.android.youtube",
                state = WindowState.FULLSCREEN
            ),
            preferences = prefs
        )

        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.None, result)
    }

    @Test
    fun youtubeMusicFullscreenVideoShowsFullscreenOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.apps.youtube.music",
            playbackState = playingState,
            metadata = null,
            windowInfo = windowInfoFor(
                pkg = "com.google.android.apps.youtube.music",
                state = WindowState.FULLSCREEN,
                videoSurfaceFraction = 0.5f,
                playMode = PlayMode.VIDEO,
            ),
            preferences = prefs
        )

        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.Fullscreen, result)
    }

    @Test
    fun youtubeMusicMiniplayerVideoShowsFullscreenOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.apps.youtube.music",
            playbackState = playingState,
            metadata = null,
            windowInfo = windowInfoFor(
                pkg = "com.google.android.apps.youtube.music",
                state = WindowState.MINIMIZED_IN_APP,
                videoSurfaceFraction = 0.5f,
                playMode = PlayMode.VIDEO,
            ),
            preferences = prefs
        )

        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.Fullscreen, result)
    }

    @Test
    fun youtubeMusicPiPVideoShowsFullscreenOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.apps.youtube.music",
            playbackState = playingState,
            metadata = null,
            windowInfo = windowInfoFor(
                pkg = "com.google.android.apps.youtube.music",
                state = WindowState.PICTURE_IN_PICTURE,
                videoSurfaceFraction = 0.15f,
                playMode = PlayMode.VIDEO,
            ),
            preferences = prefs
        )

        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.Fullscreen, result)
    }

    @Test
    fun youtubeMusicVideoSelectionOverridesSurface() {
        val input = PolicyInput(
            packageName = "com.google.android.apps.youtube.music",
            playbackState = playingState,
            metadata = null,
            windowInfo = windowInfoFor(
                pkg = "com.google.android.apps.youtube.music",
                state = WindowState.MINIMIZED_IN_APP,
                videoSurfaceFraction = 0f,
                playMode = PlayMode.AUDIO,
                selectedMode = PlayMode.VIDEO,
            ),
            preferences = prefs
        )

        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.Fullscreen, result)
    }

    @Test
    fun youtubeMusicAudioSelectionWithoutSurfaceHidesOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.apps.youtube.music",
            playbackState = playingState,
            metadata = null,
            windowInfo = windowInfoFor(
                pkg = "com.google.android.apps.youtube.music",
                state = WindowState.MINIMIZED_IN_APP,
                videoSurfaceFraction = 0f,
                playMode = PlayMode.AUDIO,
                selectedMode = PlayMode.AUDIO,
            ),
            preferences = prefs
        )

        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.None, result)
    }

    @Test
    fun youtubeMusicTinySurfaceWithoutSelectionHidesOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.apps.youtube.music",
            playbackState = playingState,
            metadata = null,
            windowInfo = windowInfoFor(
                pkg = "com.google.android.apps.youtube.music",
                state = WindowState.MINIMIZED_IN_APP,
                videoSurfaceFraction = 0.005f,
                playMode = PlayMode.AUDIO,
                selectedMode = null,
            ),
            preferences = prefs
        )

        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.None, result)
    }

    @Test
    fun youtubeMusicBackgroundHidesOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.apps.youtube.music",
            playbackState = playingState,
            metadata = null,
            windowInfo = WindowInfo.Empty,
            preferences = prefs
        )

        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.None, result)
    }

    @Test
    fun youtubeWithoutVisibleVideoSurfaceHidesOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.youtube",
            playbackState = playingState,
            metadata = null,
            windowInfo = windowInfoFor(
                pkg = "com.google.android.youtube",
                state = WindowState.FULLSCREEN,
                videoSurfaceFraction = 0f,
                playMode = PlayMode.AUDIO,
            ),
            preferences = prefs
        )

        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.None, result)
    }

    @Test
    fun youtubeMusicVideoWithoutVisibleSurfaceHidesOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.apps.youtube.music",
            playbackState = playingState,
            metadata = null,
            windowInfo = windowInfoFor(
                pkg = "com.google.android.apps.youtube.music",
                state = WindowState.MINIMIZED_IN_APP,
                videoSurfaceFraction = 0f,
                playMode = PlayMode.AUDIO,
                selectedMode = null,
            ),
            preferences = prefs
        )

        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.None, result)
    }

    @Test
    fun disabledPreferencePreventsOverlay() {
        val disabledPrefs = OverlayPreferences(enableYouTube = false)
        val input = PolicyInput(
            packageName = "com.google.android.youtube",
            playbackState = playingState,
            metadata = null,
            windowInfo = windowInfoFor(
                pkg = "com.google.android.youtube",
                state = WindowState.FULLSCREEN
            ),
            preferences = disabledPrefs
        )

        assertEquals(OverlayState.None, PolicyEngine.compute(input))
    }

    private fun windowInfoFor(
        pkg: String,
        state: WindowState,
        videoSurfaceFraction: Float = 0.5f,
        playMode: PlayMode = if (videoSurfaceFraction > 0f) PlayMode.VIDEO else PlayMode.AUDIO,
        selectedMode: PlayMode? = null,
    ): WindowInfo {
        return WindowInfo(
            focusedPackage = pkg,
            appWindows = mapOf(
                pkg to AppWindowInfo(
                    packageName = pkg,
                    state = state,
                    videoSurfaceFraction = videoSurfaceFraction,
                    playMode = playMode,
                    selectedMode = selectedMode,
                )
            )
        )
    }
}
