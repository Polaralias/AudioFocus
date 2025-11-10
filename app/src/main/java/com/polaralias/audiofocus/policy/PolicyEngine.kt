package com.polaralias.audiofocus.policy

import android.media.session.PlaybackState
import android.util.Log
import com.polaralias.audiofocus.model.OverlayState
import com.polaralias.audiofocus.window.AppWindowInfo
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
            YOUTUBE -> evaluateYouTube(prefs.enableYouTube, windowInfo, prefs.dimAmount)
            YOUTUBE_MUSIC -> evaluateYouTubeMusic(
                enabled = prefs.enableYouTubeMusic,
                metadata = input.metadata,
                windowInfo = windowInfo,
                dimAmount = prefs.dimAmount,
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
        dimAmount: Float,
    ): OverlayState {
        if (!enabled) {
            Log.d(TAG, "YouTube overlay disabled in preferences")
            return OverlayState.None
        }

        val state = windowInfo?.state ?: WindowState.BACKGROUND
        return when (state) {
            WindowState.FULLSCREEN,
            WindowState.MINIMIZED_IN_APP,
            WindowState.PICTURE_IN_PICTURE -> {
                Log.d(TAG, "YouTube visible (state=$state), showing fullscreen overlay")
                OverlayState.Fullscreen(maskAlpha = dimAmount)
            }
            WindowState.BACKGROUND -> {
                Log.d(TAG, "YouTube not visible (state=$state), hiding overlay")
                OverlayState.None
            }
        }
    }

    private fun evaluateYouTubeMusic(
        enabled: Boolean,
        metadata: android.media.MediaMetadata?,
        windowInfo: AppWindowInfo?,
        dimAmount: Float,
    ): OverlayState {
        if (!enabled) {
            Log.d(TAG, "YouTube Music overlay disabled in preferences")
            return OverlayState.None
        }

        val windowState = windowInfo?.state ?: WindowState.BACKGROUND
        if (windowState == WindowState.BACKGROUND) {
            Log.d(TAG, "YouTube Music not visible (background playback)")
            return OverlayState.None
        }

        val isVideo = isYouTubeMusicVideo(metadata)
        if (!isVideo && windowInfo?.hasVisibleVideoSurface != true) {
            Log.d(TAG, "YouTube Music playback not identified as video")
            return OverlayState.None
        }

        return when (windowState) {
            WindowState.FULLSCREEN -> {
                Log.d(TAG, "YouTube Music fullscreen video - showing fullscreen overlay")
                OverlayState.Fullscreen(maskAlpha = dimAmount)
            }
            WindowState.MINIMIZED_IN_APP,
            WindowState.PICTURE_IN_PICTURE -> {
                Log.d(TAG, "YouTube Music partial video (state=$windowState) - showing partial overlay")
                OverlayState.Partial(maskAlpha = dimAmount, heightRatio = PARTIAL_HEIGHT_RATIO)
            }
            WindowState.BACKGROUND -> OverlayState.None
        }
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

    private fun isYouTubeMusicVideo(metadata: android.media.MediaMetadata?): Boolean {
        if (metadata == null) return false
        val width = metadata.getLong(METADATA_KEY_VIDEO_WIDTH)
        val height = metadata.getLong(METADATA_KEY_VIDEO_HEIGHT)
        val presentationType = metadata.getLong(METADATA_KEY_PRESENTATION_DISPLAY_TYPE)
        val isVideo = (width > 0 && height > 0) || presentationType == PRESENTATION_DISPLAY_TYPE_VIDEO
        Log.d(TAG, "YouTube Music metadata video check: width=$width height=$height presentation=$presentationType -> $isVideo")
        return isVideo
    }

    private enum class PlaybackActivity {
        PLAYING,
        PAUSED,
        STOPPED,
    }

    private const val PARTIAL_HEIGHT_RATIO = 0.8f

    private const val TAG = "PolicyEngine"
    private const val YOUTUBE = "com.google.android.youtube"
    private const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
    private const val METADATA_KEY_VIDEO_WIDTH = "android.media.metadata.VIDEO_WIDTH"
    private const val METADATA_KEY_VIDEO_HEIGHT = "android.media.metadata.VIDEO_HEIGHT"
    private const val METADATA_KEY_PRESENTATION_DISPLAY_TYPE = "android.media.metadata.PRESENTATION_DISPLAY_TYPE"
    private const val PRESENTATION_DISPLAY_TYPE_VIDEO = 1L
}
