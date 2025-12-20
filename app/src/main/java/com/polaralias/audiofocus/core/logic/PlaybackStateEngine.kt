package com.polaralias.audiofocus.core.logic

import com.polaralias.audiofocus.core.model.OverlayDecision
import com.polaralias.audiofocus.core.model.OverlayMode
import com.polaralias.audiofocus.core.model.PlaybackStateSimplified
import com.polaralias.audiofocus.core.model.PlaybackType
import com.polaralias.audiofocus.core.model.TargetApp
import com.polaralias.audiofocus.core.model.WindowState
import com.polaralias.audiofocus.service.monitor.AccessibilityMonitor
import com.polaralias.audiofocus.service.monitor.AccessibilityState
import com.polaralias.audiofocus.service.monitor.ForegroundAppDetector
import com.polaralias.audiofocus.service.monitor.MediaSessionMonitor
import android.util.Log
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackStateEngine @Inject constructor(
    accessibilityMonitor: AccessibilityMonitor,
    mediaSessionMonitor: MediaSessionMonitor,
    foregroundAppDetector: ForegroundAppDetector
) {
    @OptIn(FlowPreview::class)
    val overlayDecision: Flow<OverlayDecision> = combine(
        accessibilityMonitor.states,
        mediaSessionMonitor.observe(),
        foregroundAppDetector.foregroundPackage
    ) { accessibilityStates, mediaSessionStates, foregroundPackage ->
        determineOverlayDecision(accessibilityStates, mediaSessionStates, foregroundPackage)
    }
    .debounce(200)
    .distinctUntilChanged()

    private fun determineOverlayDecision(
        accessibilityStates: Map<TargetApp, AccessibilityState>,
        mediaSessionStates: Map<TargetApp, PlaybackStateSimplified>,
        foregroundPackage: String?
    ): OverlayDecision {
        for (app in TargetApp.entries) {
            val decision = evaluateApp(app, accessibilityStates, mediaSessionStates, foregroundPackage)
            if (decision.shouldOverlay) {
                return decision
            }
        }
        return OverlayDecision(shouldOverlay = false, overlayMode = OverlayMode.NONE, targetApp = null)
    }

    private fun evaluateApp(
        app: TargetApp,
        accessibilityStates: Map<TargetApp, AccessibilityState>,
        mediaSessionStates: Map<TargetApp, PlaybackStateSimplified>,
        foregroundPackage: String?
    ): OverlayDecision {
        val accState = accessibilityStates[app]
        val playbackState = mediaSessionStates[app] ?: PlaybackStateSimplified.STOPPED

        var windowState = accState?.windowState ?: WindowState.NOT_VISIBLE
        val playbackType = accState?.playbackType ?: PlaybackType.NONE

        // Utilization of foregroundPackage to verify the target app is actually the top package
        if (foregroundPackage != null && foregroundPackage != app.packageName) {
             if (windowState == WindowState.FOREGROUND_FULLSCREEN ||
                 windowState == WindowState.FOREGROUND_MINIMISED) {
                 Log.d("PlaybackStateEngine", "Demoting ${app.name} to BACKGROUND because foreground is $foregroundPackage")
                 windowState = WindowState.BACKGROUND
             }
        }

        return when (app) {
            TargetApp.YOUTUBE -> evaluateYouTube(windowState, playbackState, playbackType)
            TargetApp.YOUTUBE_MUSIC -> evaluateYouTubeMusic(windowState, playbackState, playbackType)
        }
    }

    private fun evaluateYouTube(
        windowState: WindowState,
        playbackState: PlaybackStateSimplified,
        playbackType: PlaybackType
    ): OverlayDecision {
        if (playbackState == PlaybackStateSimplified.PAUSED || playbackState == PlaybackStateSimplified.STOPPED) {
            Log.d("PlaybackStateEngine", "YouTube: No overlay (PlaybackState: $playbackState)")
            return noOverlay()
        }

        if (playbackType == PlaybackType.VISIBLE_VIDEO) {
             if (windowState == WindowState.FOREGROUND_FULLSCREEN ||
                 windowState == WindowState.FOREGROUND_MINIMISED ||
                 windowState == WindowState.PICTURE_IN_PICTURE) {
                 Log.d("PlaybackStateEngine", "YouTube: Show Overlay (FULL_SCREEN)")
                 return OverlayDecision(true, OverlayMode.FULL_SCREEN, TargetApp.YOUTUBE)
             } else {
                 Log.d("PlaybackStateEngine", "YouTube: No overlay (WindowState: $windowState)")
             }
        } else {
            Log.d("PlaybackStateEngine", "YouTube: No overlay (PlaybackType: $playbackType)")
        }

        return noOverlay()
    }

    private fun evaluateYouTubeMusic(
        windowState: WindowState,
        playbackState: PlaybackStateSimplified,
        playbackType: PlaybackType
    ): OverlayDecision {
        if (playbackState != PlaybackStateSimplified.PLAYING) {
             Log.d("PlaybackStateEngine", "YouTube Music: No overlay (PlaybackState: $playbackState)")
             return noOverlay()
        }

        if (playbackType == PlaybackType.VISIBLE_VIDEO) {
             if (windowState != WindowState.NOT_VISIBLE && windowState != WindowState.BACKGROUND) {
                 Log.d("PlaybackStateEngine", "YouTube Music: Show Overlay (FULL_SCREEN)")
                 return OverlayDecision(true, OverlayMode.FULL_SCREEN, TargetApp.YOUTUBE_MUSIC)
             } else {
                 Log.d("PlaybackStateEngine", "YouTube Music: No overlay (WindowState: $windowState)")
             }
        } else {
            Log.d("PlaybackStateEngine", "YouTube Music: No overlay (PlaybackType: $playbackType)")
        }

        return noOverlay()
    }

    private fun noOverlay() = OverlayDecision(false, OverlayMode.NONE, null)
}
