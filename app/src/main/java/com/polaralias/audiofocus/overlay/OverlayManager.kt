package com.polaralias.audiofocus.overlay

import com.polaralias.audiofocus.state.PlaybackActivity
import com.polaralias.audiofocus.state.PlaybackContentType
import com.polaralias.audiofocus.state.PlaybackSnapshot
import com.polaralias.audiofocus.state.SupportedApp
import com.polaralias.audiofocus.state.WindowMode
import com.polaralias.audiofocus.state.WindowSnapshot

sealed class OverlayCommand {
    object Hide : OverlayCommand()
    data class Show(val mode: OverlayMode) : OverlayCommand()
}

enum class OverlayMode {
    FULL,
    PARTIAL,
}

class OverlayManager {
    fun evaluate(
        windowSnapshot: WindowSnapshot?,
        playbackSnapshot: PlaybackSnapshot?,
        manualPause: Boolean,
    ): OverlayCommand {
        if (manualPause) {
            return OverlayCommand.Hide
        }

        val activeWindow = windowSnapshot ?: return OverlayCommand.Hide
        val playback = playbackSnapshot ?: return OverlayCommand.Hide

        if (activeWindow.app != playback.app) {
            return OverlayCommand.Hide
        }

        val isVideoPlaying = playback.activity == PlaybackActivity.PLAYING &&
            playback.contentType == PlaybackContentType.VIDEO
        if (!isVideoPlaying) {
            return OverlayCommand.Hide
        }

        return when (activeWindow.app) {
            SupportedApp.YOUTUBE -> evaluateYouTube(activeWindow.mode)
            SupportedApp.YOUTUBE_MUSIC -> evaluateYouTubeMusic(activeWindow.mode)
        }
    }

    private fun evaluateYouTube(mode: WindowMode): OverlayCommand {
        return when (mode) {
            WindowMode.FULLSCREEN,
            WindowMode.MINIMIZED,
            WindowMode.PICTURE_IN_PICTURE -> OverlayCommand.Show(OverlayMode.FULL)
            WindowMode.UNKNOWN -> OverlayCommand.Hide
        }
    }

    private fun evaluateYouTubeMusic(mode: WindowMode): OverlayCommand {
        return when (mode) {
            WindowMode.FULLSCREEN -> OverlayCommand.Show(OverlayMode.FULL)
            WindowMode.MINIMIZED,
            WindowMode.PICTURE_IN_PICTURE -> OverlayCommand.Show(OverlayMode.PARTIAL)
            WindowMode.UNKNOWN -> OverlayCommand.Hide
        }
    }
}
