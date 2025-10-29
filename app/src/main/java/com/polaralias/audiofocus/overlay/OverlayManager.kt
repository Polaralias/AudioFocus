package com.polaralias.audiofocus.overlay

import com.polaralias.audiofocus.state.PlaybackActivity
import com.polaralias.audiofocus.state.PlaybackContentType
import com.polaralias.audiofocus.state.PlaybackSnapshot
import com.polaralias.audiofocus.state.SupportedApp
import com.polaralias.audiofocus.state.WindowState
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
            SupportedApp.YOUTUBE -> evaluateYouTube(activeWindow.state)
            SupportedApp.YOUTUBE_MUSIC -> evaluateYouTubeMusic(activeWindow.state)
        }
    }

    private fun evaluateYouTube(state: WindowState): OverlayCommand {
        return when (state) {
            WindowState.FULLSCREEN,
            WindowState.MINIMIZED,
            WindowState.PICTURE_IN_PICTURE -> OverlayCommand.Show(OverlayMode.FULL)
            WindowState.UNKNOWN -> OverlayCommand.Hide
        }
    }

    private fun evaluateYouTubeMusic(state: WindowState): OverlayCommand {
        return when (state) {
            WindowState.FULLSCREEN -> OverlayCommand.Show(OverlayMode.FULL)
            WindowState.MINIMIZED,
            WindowState.PICTURE_IN_PICTURE -> OverlayCommand.Show(OverlayMode.PARTIAL)
            WindowState.UNKNOWN -> OverlayCommand.Hide
        }
    }
}
