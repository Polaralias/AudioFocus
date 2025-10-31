package com.polaralias.audiofocus.policy

import android.media.session.PlaybackState
import com.polaralias.audiofocus.model.OverlayState

object PolicyEngine {
    private const val YOUTUBE = "com.google.android.youtube"
    private const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"

    fun compute(input: PolicyInput): OverlayState {
        val pkg = input.packageName ?: return OverlayState.None
        val prefs = input.preferences
        val isPlaying = input.playbackState?.state == PlaybackState.STATE_PLAYING
        if (!isPlaying) return OverlayState.None
        return when (pkg) {
            YOUTUBE -> if (prefs.enableYouTube) {
                OverlayState.Fullscreen(maskAlpha = prefs.dimAmount)
            } else OverlayState.None
            YOUTUBE_MUSIC -> if (prefs.enableYouTubeMusic) {
                val info = input.windowInfo
                if (!info.hasLikelyVideoSurface) {
                    OverlayState.None
                } else if (info.isFullscreen) {
                    OverlayState.Fullscreen(maskAlpha = prefs.dimAmount)
                } else {
                    OverlayState.Partial(maskAlpha = prefs.dimAmount)
                }
            } else OverlayState.None
            else -> OverlayState.None
        }
    }
}
