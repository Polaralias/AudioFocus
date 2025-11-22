package com.polaralias.audiofocus.overlay

import android.util.Log
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
}

class OverlayManager {
    companion object {
        private const val TAG = "OverlayManager"
    }

    fun evaluate(
        windowSnapshot: WindowSnapshot?,
        playbackSnapshot: PlaybackSnapshot?,
        manualPause: Boolean,
    ): OverlayCommand {
        if (manualPause) {
            Log.d(TAG, "Manual pause active, hiding overlay")
            return OverlayCommand.Hide
        }

        val activeWindow = windowSnapshot
        if (activeWindow == null) {
            Log.d(TAG, "No window snapshot available, hiding overlay")
            return OverlayCommand.Hide
        }

        val playback = playbackSnapshot
        if (playback == null) {
            Log.d(TAG, "No playback snapshot available, hiding overlay")
            return OverlayCommand.Hide
        }

        Log.d(TAG, "Evaluating overlay for app: ${activeWindow.app}, window state: ${activeWindow.state}")

        if (activeWindow.app != playback.app) {
            Log.w(TAG, "Window/playback app mismatch - window: ${activeWindow.app}, playback: ${playback.app}, hiding overlay")
            return OverlayCommand.Hide
        }

        if (playback.activity != PlaybackActivity.PLAYING || playback.contentType != PlaybackContentType.VIDEO) {
            Log.d(TAG, "Not showing overlay - activity: ${playback.activity}, contentType: ${playback.contentType}")
            return OverlayCommand.Hide
        }

        Log.d(TAG, "Video is playing, evaluating window state for ${activeWindow.app}")

        return when (activeWindow.app) {
            SupportedApp.YOUTUBE -> evaluateYouTube(activeWindow.state)
            SupportedApp.YOUTUBE_MUSIC -> evaluateYouTubeMusic(activeWindow.state)
        }
    }

    private fun evaluateYouTube(state: WindowState): OverlayCommand {
        return when (state) {
            WindowState.FULLSCREEN -> {
                Log.d(TAG, "YouTube fullscreen detected, showing full overlay")
                OverlayCommand.Show(OverlayMode.FULL)
            }
            WindowState.MINIMIZED -> {
                Log.d(TAG, "YouTube minimized detected, showing full overlay")
                OverlayCommand.Show(OverlayMode.FULL)
            }
            WindowState.PICTURE_IN_PICTURE -> {
                Log.d(TAG, "YouTube PiP detected, showing full overlay")
                OverlayCommand.Show(OverlayMode.FULL)
            }
            WindowState.BACKGROUND -> {
                Log.d(TAG, "YouTube background playback detected, hiding overlay")
                OverlayCommand.Hide
            }
            WindowState.UNKNOWN -> {
                Log.d(TAG, "YouTube window state unknown, hiding overlay")
                OverlayCommand.Hide
            }
        }
    }

    private fun evaluateYouTubeMusic(state: WindowState): OverlayCommand {
        return when (state) {
            WindowState.FULLSCREEN -> {
                Log.d(TAG, "YouTube Music fullscreen video detected, showing full overlay")
                OverlayCommand.Show(OverlayMode.FULL)
            }
            WindowState.MINIMIZED -> {
                Log.d(TAG, "YouTube Music miniplayer detected, showing full overlay")
                OverlayCommand.Show(OverlayMode.FULL)
            }
            WindowState.PICTURE_IN_PICTURE -> {
                Log.d(TAG, "YouTube Music PiP detected, showing full overlay")
                OverlayCommand.Show(OverlayMode.FULL)
            }
            WindowState.BACKGROUND -> {
                Log.d(TAG, "YouTube Music background playback detected, hiding overlay")
                OverlayCommand.Hide
            }
            WindowState.UNKNOWN -> {
                Log.d(TAG, "YouTube Music window state unknown, hiding overlay")
                OverlayCommand.Hide
            }
        }
    }
}
