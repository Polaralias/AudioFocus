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
            YOUTUBE -> evaluateYouTube(prefs.enableYouTube, windowInfo)
            YOUTUBE_MUSIC -> evaluateYouTubeMusic(
                enabled = prefs.enableYouTubeMusic,
                metadata = input.metadata,
                windowInfo = windowInfo,
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

        val hasVideoSignal = info.hasVisibleVideoSurface || state in RELAXED_VIDEO_STATES
        if (!hasVideoSignal && state == WindowState.FULLSCREEN) {
            Log.d(TAG, "YouTube fullscreen without video signal, hiding overlay")
            return OverlayState.None
        }

        return when (state) {
            WindowState.FULLSCREEN,
            WindowState.MINIMIZED_IN_APP,
            WindowState.PICTURE_IN_PICTURE -> {
                if (!hasVideoSignal) {
                    Log.d(TAG, "YouTube state=$state lacks video signal, hiding overlay")
                    return OverlayState.None
                }
                Log.d(TAG, "YouTube visible (state=$state), showing fullscreen overlay")
                OverlayState.Fullscreen
            }
            WindowState.BACKGROUND -> OverlayState.None
        }
    }

    private fun evaluateYouTubeMusic(
        enabled: Boolean,
        metadata: android.media.MediaMetadata?,
        windowInfo: AppWindowInfo?,
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

        val hasVideoSurface = info.hasVisibleVideoSurface
        val relaxedSignal = windowState in RELAXED_VIDEO_STATES
        val classification = classifyYouTubeMusicVideo(metadata)
        val heuristicVideoSignal = hasVideoSurface || relaxedSignal || windowState == WindowState.FULLSCREEN
        val isVideo = when (classification.category) {
            VideoClassification.VIDEO ->
                hasVideoSurface || windowState == WindowState.PICTURE_IN_PICTURE
            VideoClassification.AUDIO -> {
                if (!classification.metadataTrusted && heuristicVideoSignal) {
                    Log.d(
                        TAG,
                        "YouTube Music metadata reported audio but heuristics detected video cues (state=$windowState, " +
                            "hasSurface=$hasVideoSurface)"
                    )
                    heuristicVideoSignal
                } else {
                    false
                }
            }
            VideoClassification.UNKNOWN -> heuristicVideoSignal
        }

        if (!isVideo) {
            Log.d(TAG, "YouTube Music playback classified as non-video")
            return OverlayState.None
        }

        return when (windowState) {
            WindowState.FULLSCREEN -> {
                Log.d(TAG, "YouTube Music fullscreen video - showing fullscreen overlay")
                OverlayState.Fullscreen
            }
            WindowState.MINIMIZED_IN_APP,
            WindowState.PICTURE_IN_PICTURE -> {
                Log.d(TAG, "YouTube Music partial video (state=$windowState) - showing partial overlay")
                OverlayState.Partial(heightRatio = PARTIAL_HEIGHT_RATIO)
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

    private fun classifyYouTubeMusicVideo(
        metadata: android.media.MediaMetadata?,
    ): YouTubeMusicVideoClassification {
        if (metadata == null) {
            Log.d(TAG, "YouTube Music metadata unavailable; falling back to relaxed heuristics")
            return YouTubeMusicVideoClassification(VideoClassification.UNKNOWN, metadataTrusted = false)
        }

        val hasWidthKey = metadata.containsKey(METADATA_KEY_VIDEO_WIDTH)
        val hasHeightKey = metadata.containsKey(METADATA_KEY_VIDEO_HEIGHT)
        val hasPresentationKey = metadata.containsKey(METADATA_KEY_PRESENTATION_DISPLAY_TYPE)

        val width = metadata.getLong(METADATA_KEY_VIDEO_WIDTH)
        val height = metadata.getLong(METADATA_KEY_VIDEO_HEIGHT)
        val presentationType = metadata.getLong(METADATA_KEY_PRESENTATION_DISPLAY_TYPE)

        val hasPositiveDimensions = width > 0 && height > 0
        val isVideoPresentation = presentationType == PRESENTATION_DISPLAY_TYPE_VIDEO
        val metadataTrusted = hasPositiveDimensions || isVideoPresentation

        val reportedAudio = when {
            hasPresentationKey && !isVideoPresentation -> true
            (hasWidthKey || hasHeightKey) && !hasPositiveDimensions -> true
            else -> false
        }

        val category = when {
            hasPositiveDimensions || isVideoPresentation -> VideoClassification.VIDEO
            reportedAudio -> VideoClassification.AUDIO
            else -> VideoClassification.UNKNOWN
        }

        Log.d(
            TAG,
            "YouTube Music metadata video check: width=$width height=$height presentation=$presentationType " +
                "trusted=$metadataTrusted -> $category"
        )

        return YouTubeMusicVideoClassification(category, metadataTrusted = metadataTrusted)
    }

    private enum class PlaybackActivity {
        PLAYING,
        PAUSED,
        STOPPED,
    }

    private data class YouTubeMusicVideoClassification(
        val category: VideoClassification,
        val metadataTrusted: Boolean,
    )

    private enum class VideoClassification {
        VIDEO,
        AUDIO,
        UNKNOWN,
    }

    private const val PARTIAL_HEIGHT_RATIO = 0.8f

    private const val TAG = "PolicyEngine"
    private const val YOUTUBE = "com.google.android.youtube"
    private const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
    private val RELAXED_VIDEO_STATES = setOf(
        WindowState.MINIMIZED_IN_APP,
        WindowState.PICTURE_IN_PICTURE,
    )
    private const val METADATA_KEY_VIDEO_WIDTH = "android.media.metadata.VIDEO_WIDTH"
    private const val METADATA_KEY_VIDEO_HEIGHT = "android.media.metadata.VIDEO_HEIGHT"
    private const val METADATA_KEY_PRESENTATION_DISPLAY_TYPE = "android.media.metadata.PRESENTATION_DISPLAY_TYPE"
    private const val PRESENTATION_DISPLAY_TYPE_VIDEO = 1L
}
