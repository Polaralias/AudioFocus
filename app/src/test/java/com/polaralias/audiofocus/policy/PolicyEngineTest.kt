package com.polaralias.audiofocus.policy

import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.polaralias.audiofocus.data.OverlayPreferences
import com.polaralias.audiofocus.model.OverlayState
import com.polaralias.audiofocus.window.AppWindowInfo
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
        assertEquals(OverlayState.Fullscreen(maskAlpha = prefs.dimAmount), result)
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
            metadata = videoMetadata(),
            windowInfo = windowInfoFor(
                pkg = "com.google.android.apps.youtube.music",
                state = WindowState.FULLSCREEN,
                hasVideoSurface = true
            ),
            preferences = prefs
        )

        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.Fullscreen(maskAlpha = prefs.dimAmount), result)
    }

    @Test
    fun youtubeMusicMiniplayerVideoShowsPartialOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.apps.youtube.music",
            playbackState = playingState,
            metadata = videoMetadata(),
            windowInfo = windowInfoFor(
                pkg = "com.google.android.apps.youtube.music",
                state = WindowState.MINIMIZED_IN_APP,
                hasVideoSurface = true
            ),
            preferences = prefs
        )

        val result = PolicyEngine.compute(input)
        assertEquals(
            OverlayState.Partial(maskAlpha = prefs.dimAmount, heightRatio = 0.8f),
            result
        )
    }

    @Test
    fun youtubeMusicAudioOnlyHidesOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.apps.youtube.music",
            playbackState = playingState,
            metadata = audioMetadata(),
            windowInfo = windowInfoFor(
                pkg = "com.google.android.apps.youtube.music",
                state = WindowState.MINIMIZED_IN_APP,
                hasVideoSurface = false
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
            metadata = videoMetadata(),
            windowInfo = WindowInfo.Empty,
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
        hasVideoSurface: Boolean = true,
    ): WindowInfo {
        return WindowInfo(
            focusedPackage = pkg,
            appWindows = mapOf(pkg to AppWindowInfo(pkg, state, hasVideoSurface))
        )
    }

    private fun videoMetadata(): MediaMetadata {
        return MediaMetadata.Builder()
            .putLong(METADATA_KEY_VIDEO_WIDTH, 1920)
            .putLong(METADATA_KEY_VIDEO_HEIGHT, 1080)
            .putLong(METADATA_KEY_PRESENTATION_DISPLAY_TYPE, 1)
            .build()
    }

    private fun audioMetadata(): MediaMetadata {
        return MediaMetadata.Builder()
            .putLong(METADATA_KEY_VIDEO_WIDTH, 0)
            .putLong(METADATA_KEY_VIDEO_HEIGHT, 0)
            .putLong(METADATA_KEY_PRESENTATION_DISPLAY_TYPE, 0)
            .build()
    }

    companion object {
        private const val METADATA_KEY_VIDEO_WIDTH = "android.media.metadata.VIDEO_WIDTH"
        private const val METADATA_KEY_VIDEO_HEIGHT = "android.media.metadata.VIDEO_HEIGHT"
        private const val METADATA_KEY_PRESENTATION_DISPLAY_TYPE = "android.media.metadata.PRESENTATION_DISPLAY_TYPE"
    }
}
