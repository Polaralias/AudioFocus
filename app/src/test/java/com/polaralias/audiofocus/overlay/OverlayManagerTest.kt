package com.polaralias.audiofocus.overlay

import com.polaralias.audiofocus.state.PlaybackActivity
import com.polaralias.audiofocus.state.PlaybackContentType
import com.polaralias.audiofocus.state.PlaybackSnapshot
import com.polaralias.audiofocus.state.SupportedApp
import com.polaralias.audiofocus.state.WindowSnapshot
import com.polaralias.audiofocus.state.WindowState
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayManagerTest {
    private val manager = OverlayManager()

    private val youtubeVideoPlayback = PlaybackSnapshot(
        app = SupportedApp.YOUTUBE,
        activity = PlaybackActivity.PLAYING,
        contentType = PlaybackContentType.VIDEO,
    )
    private val youtubeMusicVideoPlayback = PlaybackSnapshot(
        app = SupportedApp.YOUTUBE_MUSIC,
        activity = PlaybackActivity.PLAYING,
        contentType = PlaybackContentType.VIDEO,
    )

    @Test
    fun `manual pause hides overlay even during video playback`() {
        val command = manager.evaluate(
            windowSnapshot = WindowSnapshot(SupportedApp.YOUTUBE, WindowState.FULLSCREEN),
            playbackSnapshot = youtubeVideoPlayback,
            manualPause = true,
        )

        assertEquals(OverlayCommand.Hide, command)
    }

    @Test
    fun `missing window or playback snapshot hides overlay`() {
        val windowCommand = manager.evaluate(
            windowSnapshot = null,
            playbackSnapshot = youtubeVideoPlayback,
            manualPause = false,
        )
        val playbackCommand = manager.evaluate(
            windowSnapshot = WindowSnapshot(SupportedApp.YOUTUBE, WindowState.FULLSCREEN),
            playbackSnapshot = null,
            manualPause = false,
        )

        assertEquals(OverlayCommand.Hide, windowCommand)
        assertEquals(OverlayCommand.Hide, playbackCommand)
    }

    @Test
    fun `window playback app mismatch hides overlay`() {
        val youtubeWindowCommand = manager.evaluate(
            windowSnapshot = WindowSnapshot(SupportedApp.YOUTUBE, WindowState.FULLSCREEN),
            playbackSnapshot = youtubeMusicVideoPlayback,
            manualPause = false,
        )
        val musicWindowCommand = manager.evaluate(
            windowSnapshot = WindowSnapshot(SupportedApp.YOUTUBE_MUSIC, WindowState.FULLSCREEN),
            playbackSnapshot = youtubeVideoPlayback,
            manualPause = false,
        )

        assertEquals(OverlayCommand.Hide, youtubeWindowCommand)
        assertEquals(OverlayCommand.Hide, musicWindowCommand)
    }

    @Test
    fun `paused or stopped playback hides overlay`() {
        PlaybackActivity.values()
            .filter { it != PlaybackActivity.PLAYING }
            .forEach { activity ->
                val command = manager.evaluate(
                    windowSnapshot = WindowSnapshot(SupportedApp.YOUTUBE, WindowState.FULLSCREEN),
                    playbackSnapshot = youtubeVideoPlayback.copy(activity = activity),
                    manualPause = false,
                )

                assertEquals("Activity $activity should hide overlay", OverlayCommand.Hide, command)
            }
    }

    @Test
    fun `non video content hides overlay`() {
        listOf(PlaybackContentType.AUDIO_ONLY, PlaybackContentType.UNKNOWN)
            .forEach { contentType ->
                val command = manager.evaluate(
                    windowSnapshot = WindowSnapshot(SupportedApp.YOUTUBE, WindowState.FULLSCREEN),
                    playbackSnapshot = youtubeVideoPlayback.copy(contentType = contentType),
                    manualPause = false,
                )

                assertEquals("Content $contentType should hide overlay", OverlayCommand.Hide, command)
            }
    }

    @Test
    fun `youtube window states follow full overlay policy`() {
        val expectations = mapOf(
            WindowState.FULLSCREEN to OverlayCommand.Show(OverlayMode.FULL),
            WindowState.MINIMIZED to OverlayCommand.Show(OverlayMode.FULL),
            WindowState.PICTURE_IN_PICTURE to OverlayCommand.Show(OverlayMode.FULL),
            WindowState.BACKGROUND to OverlayCommand.Hide,
            WindowState.UNKNOWN to OverlayCommand.Hide,
        )

        expectations.forEach { (state, expected) ->
            val command = manager.evaluate(
                windowSnapshot = WindowSnapshot(SupportedApp.YOUTUBE, state),
                playbackSnapshot = youtubeVideoPlayback,
                manualPause = false,
            )

            assertEquals("State $state should map to $expected", expected, command)
        }
    }

    @Test
    fun `youtube music window states follow overlay policy matrix`() {
        val expectations = mapOf(
            WindowState.FULLSCREEN to OverlayCommand.Show(OverlayMode.FULL),
            WindowState.MINIMIZED to OverlayCommand.Show(OverlayMode.FULL),
            WindowState.PICTURE_IN_PICTURE to OverlayCommand.Show(OverlayMode.FULL),
            WindowState.BACKGROUND to OverlayCommand.Hide,
            WindowState.UNKNOWN to OverlayCommand.Hide,
        )

        expectations.forEach { (state, expected) ->
            val command = manager.evaluate(
                windowSnapshot = WindowSnapshot(SupportedApp.YOUTUBE_MUSIC, state),
                playbackSnapshot = youtubeMusicVideoPlayback,
                manualPause = false,
            )

            assertEquals("State $state should map to $expected", expected, command)
        }
    }

    @Test
    fun `youtube music audio only playback never shows overlay`() {
        WindowState.values().forEach { windowState ->
            val command = manager.evaluate(
                windowSnapshot = WindowSnapshot(SupportedApp.YOUTUBE_MUSIC, windowState),
                playbackSnapshot = PlaybackSnapshot(
                    app = SupportedApp.YOUTUBE_MUSIC,
                    activity = PlaybackActivity.PLAYING,
                    contentType = PlaybackContentType.AUDIO_ONLY,
                ),
                manualPause = false,
            )

            assertEquals(
                "YouTube Music audio-only with $windowState should hide overlay",
                OverlayCommand.Hide,
                command
            )
        }
    }

    @Test
    fun `all edge case combinations hide overlay correctly`() {
        val edgeCases = listOf(
            Triple(true, WindowSnapshot(SupportedApp.YOUTUBE, WindowState.FULLSCREEN), youtubeVideoPlayback),
            Triple(false, null, youtubeVideoPlayback),
            Triple(false, WindowSnapshot(SupportedApp.YOUTUBE, WindowState.FULLSCREEN), null),
            Triple(false, WindowSnapshot(SupportedApp.YOUTUBE, WindowState.FULLSCREEN), youtubeMusicVideoPlayback),
            Triple(false, WindowSnapshot(SupportedApp.YOUTUBE, WindowState.FULLSCREEN),
                youtubeVideoPlayback.copy(activity = PlaybackActivity.PAUSED)),
            Triple(false, WindowSnapshot(SupportedApp.YOUTUBE, WindowState.FULLSCREEN),
                youtubeVideoPlayback.copy(contentType = PlaybackContentType.UNKNOWN)),
            Triple(false, WindowSnapshot(SupportedApp.YOUTUBE, WindowState.UNKNOWN), youtubeVideoPlayback),
        )

        edgeCases.forEach { (manualPause, window, playback) ->
            val command = manager.evaluate(window, playback, manualPause)
            assertEquals(
                "Edge case should hide overlay: manualPause=$manualPause, window=$window, playback=$playback",
                OverlayCommand.Hide,
                command
            )
        }
    }
}
