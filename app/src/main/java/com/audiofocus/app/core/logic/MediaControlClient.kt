package com.audiofocus.app.core.logic

import com.audiofocus.app.core.model.MediaAction
import com.audiofocus.app.core.model.TargetApp
import com.audiofocus.app.service.monitor.MediaSessionMonitor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaControlClient @Inject constructor(
    private val mediaSessionMonitor: MediaSessionMonitor
) {
    fun sendAction(targetApp: TargetApp, action: MediaAction) {
        val controller = mediaSessionMonitor.getController(targetApp) ?: return
        val transportControls = controller.transportControls ?: return

        when (action) {
            is MediaAction.Play -> transportControls.play()
            is MediaAction.Pause -> transportControls.pause()
            is MediaAction.SkipForward -> {
                val state = controller.playbackState
                if (state != null) {
                    val current = state.position
                    transportControls.seekTo(current + 10000)
                } else {
                    transportControls.skipToNext()
                }
            }
            is MediaAction.SkipBackward -> {
                val state = controller.playbackState
                if (state != null) {
                    val current = state.position
                    val newPos = (current - 10000).coerceAtLeast(0)
                    transportControls.seekTo(newPos)
                } else {
                    transportControls.skipToPrevious()
                }
            }
            is MediaAction.Seek -> transportControls.seekTo(action.position)
        }
    }
}
