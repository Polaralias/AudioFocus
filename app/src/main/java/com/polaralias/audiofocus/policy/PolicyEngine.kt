package com.polaralias.audiofocus.policy

import android.media.session.PlaybackState
import android.util.Log
import com.polaralias.audiofocus.model.OverlayState

object PolicyEngine {
    private const val TAG = "PolicyEngine"
    private const val YOUTUBE = "com.google.android.youtube"
    private const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"

    fun compute(input: PolicyInput): OverlayState {
        val pkg = input.packageName
        if (pkg == null) {
            Log.d(TAG, "No package name provided, returning None")
            return OverlayState.None
        }
        
        val prefs = input.preferences
        val isPlaying = input.playbackState?.state == PlaybackState.STATE_PLAYING
        
        if (!isPlaying) {
            Log.d(TAG, "Not playing, returning None for package: $pkg")
            return OverlayState.None
        }
        
        return when (pkg) {
            YOUTUBE -> {
                if (prefs.enableYouTube) {
                    Log.d(TAG, "YouTube overlay enabled: Fullscreen with alpha ${prefs.dimAmount}")
                    OverlayState.Fullscreen(maskAlpha = prefs.dimAmount)
                } else {
                    Log.d(TAG, "YouTube overlay disabled in preferences")
                    OverlayState.None
                }
            }
            YOUTUBE_MUSIC -> {
                if (prefs.enableYouTubeMusic) {
                    val info = input.windowInfo
                    if (!info.hasLikelyVideoSurface) {
                        Log.d(TAG, "YouTube Music: No video surface detected")
                        OverlayState.None
                    } else if (info.isFullscreen) {
                        Log.d(TAG, "YouTube Music: Fullscreen mode with alpha ${prefs.dimAmount}")
                        OverlayState.Fullscreen(maskAlpha = prefs.dimAmount)
                    } else {
                        Log.d(TAG, "YouTube Music: Partial mode with alpha ${prefs.dimAmount}")
                        OverlayState.Partial(maskAlpha = prefs.dimAmount)
                    }
                } else {
                    Log.d(TAG, "YouTube Music overlay disabled in preferences")
                    OverlayState.None
                }
            }
            else -> {
                Log.d(TAG, "Package not supported for overlays: $pkg")
                OverlayState.None
            }
        }
    }
}
