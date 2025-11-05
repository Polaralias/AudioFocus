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
    PARTIAL,
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
        // Edge case: Manual pause always hides overlay
        if (manualPause) {
            Log.d(TAG, "Manual pause active, hiding overlay")
            return OverlayCommand.Hide
        }

        // Edge case: Missing window information
        val activeWindow = windowSnapshot
        if (activeWindow == null) {
            Log.d(TAG, "No window snapshot available, hiding overlay")
            return OverlayCommand.Hide
        }

        // Edge case: Missing playback information
        val playback = playbackSnapshot
        if (playback == null) {
            Log.d(TAG, "No playback snapshot available, hiding overlay")
            return OverlayCommand.Hide
        }

        // Strict enforcement: Only show overlays for YouTube and YouTube Music
        // This is enforced by the SupportedApp enum, but log for clarity
        Log.d(TAG, "Evaluating overlay for app: ${activeWindow.app}, window state: ${activeWindow.state}")

        // Edge case: Window and playback app mismatch
        if (activeWindow.app != playback.app) {
            Log.w(TAG, "Window/playback app mismatch - window: ${activeWindow.app}, playback: ${playback.app}, hiding overlay")
            return OverlayCommand.Hide
        }

        // Strict enforcement: Only show overlay when STATE_PLAYING and content is VIDEO
        if (playback.activity != PlaybackActivity.PLAYING || playback.contentType != PlaybackContentType.VIDEO) {
            Log.d(TAG, "Not showing overlay - activity: ${playback.activity}, contentType: ${playback.contentType}")
            return OverlayCommand.Hide
        }

        Log.d(TAG, "Video is playing, evaluating window state for ${activeWindow.app}")

        // App-specific logic for overlay display
        return when (activeWindow.app) {
            SupportedApp.YOUTUBE -> evaluateYouTube(activeWindow.state)
            SupportedApp.YOUTUBE_MUSIC -> evaluateYouTubeMusic(activeWindow.state)
        }
    }

    // YouTube: Show overlay in fullscreen, PiP, and minimized modes only
    // Strict enforcement: Only when video is playing (already validated above)
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
            WindowState.UNKNOWN -> {
                Log.d(TAG, "YouTube window state unknown, hiding overlay")
                OverlayCommand.Hide
            }
        }
    }

    // YouTube Music: Different overlay modes based on window state
    // - Fullscreen video: full mask overlay
    // - Miniplayer/non-fullscreen video: partial overlay (5/6 screen height)
    // - Audio-only or background playback: no overlay (already filtered by content type check)
    private fun evaluateYouTubeMusic(state: WindowState): OverlayCommand {
        return when (state) {
            WindowState.FULLSCREEN -> {
                Log.d(TAG, "YouTube Music fullscreen video detected, showing full overlay")
                OverlayCommand.Show(OverlayMode.FULL)
            }
            WindowState.MINIMIZED -> {
                Log.d(TAG, "YouTube Music miniplayer detected, showing partial overlay")
                OverlayCommand.Show(OverlayMode.PARTIAL)
            }
            WindowState.PICTURE_IN_PICTURE -> {
                Log.d(TAG, "YouTube Music PiP detected, showing partial overlay")
                OverlayCommand.Show(OverlayMode.PARTIAL)
            }
            WindowState.UNKNOWN -> {
                Log.d(TAG, "YouTube Music window state unknown, hiding overlay")
                OverlayCommand.Hide
            }
        }
    }
}
