package com.polaralias.audiofocus.overlay

import com.polaralias.audiofocus.state.PlaybackActivity
import com.polaralias.audiofocus.state.PlaybackContentType
import com.polaralias.audiofocus.state.PlaybackSnapshot
import com.polaralias.audiofocus.state.SupportedApp
import com.polaralias.audiofocus.state.WindowSnapshot
import com.polaralias.audiofocus.state.WindowState
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayManagerTest {
    private val manager = OverlayManager()

    @Test
    fun `manual pause hides overlay`() {
        val command = manager.evaluate(
            windowSnapshot = WindowSnapshot(SupportedApp.YOUTUBE, WindowState.FULLSCREEN),
            playbackSnapshot = PlaybackSnapshot(
                SupportedApp.YOUTUBE,
                PlaybackActivity.PLAYING,
                PlaybackContentType.VIDEO,
            ),
            manualPause = true,
        )

        assertEquals(OverlayCommand.Hide, command)
    }

    @Test
    fun `video playback in fullscreen youtube shows full overlay`() {
        val command = manager.evaluate(
            windowSnapshot = WindowSnapshot(SupportedApp.YOUTUBE, WindowState.FULLSCREEN),
            playbackSnapshot = PlaybackSnapshot(
                SupportedApp.YOUTUBE,
                PlaybackActivity.PLAYING,
                PlaybackContentType.VIDEO,
            ),
            manualPause = false,
        )

        assertEquals(OverlayCommand.Show(OverlayMode.FULL), command)
    }

    @Test
    fun `youtube music miniplayer returns partial overlay`() {
        val command = manager.evaluate(
            windowSnapshot = WindowSnapshot(SupportedApp.YOUTUBE_MUSIC, WindowState.MINIMIZED),
            playbackSnapshot = PlaybackSnapshot(
                SupportedApp.YOUTUBE_MUSIC,
                PlaybackActivity.PLAYING,
                PlaybackContentType.VIDEO,
            ),
            manualPause = false,
        )

        assertEquals(OverlayCommand.Show(OverlayMode.PARTIAL), command)
    }

    @Test
    fun `audio playback hides overlay`() {
        val command = manager.evaluate(
            windowSnapshot = WindowSnapshot(SupportedApp.YOUTUBE, WindowState.FULLSCREEN),
            playbackSnapshot = PlaybackSnapshot(
                SupportedApp.YOUTUBE,
                PlaybackActivity.PLAYING,
                PlaybackContentType.AUDIO_ONLY,
            ),
            manualPause = false,
        )

        assertEquals(OverlayCommand.Hide, command)
    }

    @Test
    fun `mismatched app windows do not show overlay`() {
        val command = manager.evaluate(
            windowSnapshot = WindowSnapshot(SupportedApp.YOUTUBE, WindowState.FULLSCREEN),
            playbackSnapshot = PlaybackSnapshot(
                SupportedApp.YOUTUBE_MUSIC,
                PlaybackActivity.PLAYING,
                PlaybackContentType.VIDEO,
            ),
            manualPause = false,
        )

        assertEquals(OverlayCommand.Hide, command)
    }
}
