package com.polaralias.audiofocus.policy

import android.media.session.PlaybackState
import com.polaralias.audiofocus.data.OverlayPreferences
import com.polaralias.audiofocus.model.OverlayState
import com.polaralias.audiofocus.window.WindowInfo
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

    @Test
    fun youtubePlayingShowsFullscreenOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.youtube",
            playbackState = playingState,
            metadata = null,
            windowInfo = WindowInfo.Empty,
            preferences = OverlayPreferences()
        )
        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.Fullscreen(maskAlpha = OverlayPreferences().dimAmount), result)
    }

    @Test
    fun youtubePausedHidesOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.youtube",
            playbackState = pausedState,
            metadata = null,
            windowInfo = WindowInfo.Empty,
            preferences = OverlayPreferences()
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
            windowInfo = WindowInfo(isFullscreen = true, hasLikelyVideoSurface = true),
            preferences = OverlayPreferences()
        )
        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.Fullscreen(maskAlpha = OverlayPreferences().dimAmount), result)
    }

    @Test
    fun youtubeMusicMiniPlayerShowsPartialOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.apps.youtube.music",
            playbackState = playingState,
            metadata = null,
            windowInfo = WindowInfo(isFullscreen = false, hasLikelyVideoSurface = true),
            preferences = OverlayPreferences()
        )
        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.Partial(maskAlpha = OverlayPreferences().dimAmount), result)
    }

    @Test
    fun youtubeMusicAudioOnlyHidesOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.apps.youtube.music",
            playbackState = playingState,
            metadata = null,
            windowInfo = WindowInfo(isFullscreen = false, hasLikelyVideoSurface = false),
            preferences = OverlayPreferences()
        )
        val result = PolicyEngine.compute(input)
        assertEquals(OverlayState.None, result)
    }

    @Test
    fun disabledPreferencePreventsOverlay() {
        val input = PolicyInput(
            packageName = "com.google.android.youtube",
            playbackState = playingState,
            metadata = null,
            windowInfo = WindowInfo.Empty,
            preferences = OverlayPreferences(enableYouTube = false)
        )
        assertEquals(OverlayState.None, PolicyEngine.compute(input))
    }
}
