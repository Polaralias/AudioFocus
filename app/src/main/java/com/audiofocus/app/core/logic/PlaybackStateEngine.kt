package com.audiofocus.app.core.logic

import com.audiofocus.app.core.model.OverlayDecision
import com.audiofocus.app.core.model.OverlayMode
import com.audiofocus.app.core.model.PlaybackStateSimplified
import com.audiofocus.app.core.model.PlaybackType
import com.audiofocus.app.core.model.TargetApp
import com.audiofocus.app.core.model.WindowState
import com.audiofocus.app.service.monitor.AccessibilityMonitor
import com.audiofocus.app.service.monitor.AccessibilityState
import com.audiofocus.app.service.monitor.ForegroundAppDetector
import com.audiofocus.app.service.monitor.MediaSessionMonitor
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

        // Utilization of foregroundPackage to verify the target app is actually the top package
        if (foregroundPackage != null && foregroundPackage != app.packageName) {
             if (windowState == WindowState.FOREGROUND_FULLSCREEN ||
                 windowState == WindowState.FOREGROUND_MINIMISED) {
                 windowState = WindowState.BACKGROUND
             }
        }

        return when (app) {
            TargetApp.YOUTUBE -> evaluateYouTube(windowState, playbackState)
            TargetApp.YOUTUBE_MUSIC -> evaluateYouTubeMusic(windowState, playbackState)
        }
    }

    private fun evaluateYouTube(
        windowState: WindowState,
        playbackState: PlaybackStateSimplified
    ): OverlayDecision {
        if (playbackState == PlaybackStateSimplified.PAUSED || playbackState == PlaybackStateSimplified.STOPPED) {
            return noOverlay()
        }

        if (windowState == WindowState.FOREGROUND_FULLSCREEN ||
             windowState == WindowState.FOREGROUND_MINIMISED ||
             windowState == WindowState.PICTURE_IN_PICTURE) {
             return OverlayDecision(true, OverlayMode.FULL_SCREEN, TargetApp.YOUTUBE)
        }

        return noOverlay()
    }

    private fun evaluateYouTubeMusic(
        windowState: WindowState,
        playbackState: PlaybackStateSimplified
    ): OverlayDecision {
        if (playbackState != PlaybackStateSimplified.PLAYING) {
             return noOverlay()
        }

        if (windowState != WindowState.NOT_VISIBLE && windowState != WindowState.BACKGROUND) {
             return OverlayDecision(true, OverlayMode.FULL_SCREEN, TargetApp.YOUTUBE_MUSIC)
        }

        return noOverlay()
    }

    private fun noOverlay() = OverlayDecision(false, OverlayMode.NONE, null)
}
