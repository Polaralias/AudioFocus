package com.polaralias.audiofocus.policy

import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.util.Log
import com.polaralias.audiofocus.media.YouTubeMusicMetadata
import com.polaralias.audiofocus.model.OverlayState
import com.polaralias.audiofocus.window.AppWindowInfo
import com.polaralias.audiofocus.window.PlayMode
import com.polaralias.audiofocus.window.WindowState

object PolicyEngine {
    fun compute(input: PolicyInput): OverlayState {
        val pkg = input.packageName
        if (pkg == null) {
            Log.d(TAG, "No package name provided, returning None")
            return OverlayState.None
        }

        val prefs = input.preferences
        val playbackActivity = playbackActivity(input.playbackState)
        if (playbackActivity != PlaybackActivity.PLAYING) {
            Log.d(TAG, "Playback not in PLAYING state for $pkg (activity=$playbackActivity)")
            return OverlayState.None
        }

        val windowInfo = input.windowInfo.infoFor(pkg)
        return when (pkg) {
            YOUTUBE -> evaluateYouTube(prefs.enableYouTube, windowInfo)
            YOUTUBE_MUSIC -> evaluateYouTubeMusic(
                enabled = prefs.enableYouTubeMusic,
                windowInfo = windowInfo,
                metadata = input.metadata,
            )
            else -> {
                Log.d(TAG, "Package not supported for overlays: $pkg")
                OverlayState.None
            }
        }
    }

    private fun evaluateYouTube(
        enabled: Boolean,
        windowInfo: AppWindowInfo?,
    ): OverlayState {
        if (!enabled) {
            Log.d(TAG, "YouTube overlay disabled in preferences")
            return OverlayState.None
        }

        val info = windowInfo ?: run {
            Log.d(TAG, "YouTube window not visible, hiding overlay")
            return OverlayState.None
        }

        val state = info.state
        if (state == WindowState.BACKGROUND) {
            Log.d(TAG, "YouTube not visible (state=$state), hiding overlay")
            return OverlayState.None
        }

        val heuristicsVideo = info.playMode == PlayMode.VIDEO || info.playMode == PlayMode.SHORTS
        val pipOverride = state == WindowState.PICTURE_IN_PICTURE && info.videoSurfaceFraction > 0f

        if (!heuristicsVideo && !pipOverride) {
            Log.d(
                TAG,
                "YouTube state=$state playMode=${info.playMode} surfaceFraction=${info.videoSurfaceFraction} -> hiding overlay"
            )
            return OverlayState.None
        }

        return when (state) {
            WindowState.FULLSCREEN,
            WindowState.MINIMIZED_IN_APP,
            WindowState.PICTURE_IN_PICTURE -> {
                Log.d(
                    TAG,
                    "YouTube visible (state=$state, playMode=${info.playMode}), showing fullscreen overlay"
                )
                OverlayState.Fullscreen
            }
            WindowState.BACKGROUND -> OverlayState.None
        }
    }

    private fun evaluateYouTubeMusic(
        enabled: Boolean,
        windowInfo: AppWindowInfo?,
        metadata: MediaMetadata?,
    ): OverlayState {
        if (!enabled) {
            Log.d(TAG, "YouTube Music overlay disabled in preferences")
            return OverlayState.None
        }

        val info = windowInfo ?: run {
            Log.d(TAG, "YouTube Music window not visible, hiding overlay")
            return OverlayState.None
        }

        val windowState = info.state
        if (windowState == WindowState.BACKGROUND) {
            Log.d(TAG, "YouTube Music not visible (background playback)")
            return OverlayState.None
        }

        val videoMetadata = YouTubeMusicMetadata.extractVideoMetadata(metadata)
        if (videoMetadata?.indicatesVideo == true) {
            Log.d(
                TAG,
                "YouTube Music metadata indicates video (state=$windowState, metadata=$videoMetadata)"
            )
            return OverlayState.Fullscreen
        }

        val selectedMode = info.selectedMode
        val videoSurfaceFraction = info.videoSurfaceFraction
        val isVideo = videoSurfaceFraction >= VIDEO_SURFACE_THRESHOLD || selectedMode == PlayMode.VIDEO

        if (!isVideo) {
            Log.d(
                TAG,
                "YouTube Music playback classified as audio (mode=${info.playMode}, selection=$selectedMode, " +
                    "fraction=$videoSurfaceFraction, metadata=$videoMetadata)"
            )
            return OverlayState.None
        }

        Log.d(
            TAG,
            "YouTube Music video detected via heuristics (state=$windowState, selection=$selectedMode, " +
                "fraction=$videoSurfaceFraction) - showing fullscreen overlay"
        )
        return OverlayState.Fullscreen
    }

    private fun playbackActivity(state: PlaybackState?): PlaybackActivity {
        val playbackState = state?.state ?: PlaybackState.STATE_NONE
        return when (playbackState) {
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING -> PlaybackActivity.PLAYING
            PlaybackState.STATE_PAUSED -> PlaybackActivity.PAUSED
            PlaybackState.STATE_STOPPED,
            PlaybackState.STATE_NONE,
            PlaybackState.STATE_CONNECTING -> PlaybackActivity.STOPPED
            else -> PlaybackActivity.PAUSED
        }
    }

    private enum class PlaybackActivity {
        PLAYING,
        PAUSED,
        STOPPED,
    }

    private const val TAG = "PolicyEngine"
    private const val YOUTUBE = "com.google.android.youtube"
    private const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
    private const val VIDEO_SURFACE_THRESHOLD = 0.02f
}
