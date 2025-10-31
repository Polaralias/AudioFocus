package com.polaralias.audiofocus.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import com.polaralias.audiofocus.model.MediaState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MediaSessionMonitor(
    context: Context,
    private val scope: CoroutineScope
) {
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private val listener = MediaSessionManager.OnActiveSessionsChangedListener { updateControllers(it) }
    private val _state = MutableStateFlow<MediaState>(MediaState.Idle)
    val state: StateFlow<MediaState> = _state

    private var controller: MediaController? = null
    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publish(controller)
        }

        override fun onMetadataChanged(metadata: android.media.MediaMetadata?) {
            publish(controller)
        }

        override fun onSessionDestroyed() {
            publish(null)
        }
    }

    fun start(componentName: ComponentName) {
        sessionManager.addOnActiveSessionsChangedListener(listener, componentName, handler)
        updateControllers(sessionManager.getActiveSessions(componentName))
    }

    fun stop() {
        sessionManager.removeOnActiveSessionsChangedListener(listener)
        bind(null)
    }

    private fun updateControllers(controllers: List<MediaController>?) {
        val controller = controllers.orEmpty()
            .firstOrNull { it.packageName == YOUTUBE || it.packageName == YOUTUBE_MUSIC }
        bind(controller)
    }

    private fun bind(target: MediaController?) {
        if (controller == target) {
            publish(controller)
            return
        }
        controller?.unregisterCallback(callback)
        controller = target
        target?.registerCallback(callback)
        publish(target)
    }

    private fun publish(target: MediaController?) {
        scope.launch(Dispatchers.Main.immediate) {
            if (target == null) {
                _state.value = MediaState.Idle
                return@launch
            }
            val playback = target.playbackState
            if (playback != null && playback.state == PlaybackState.STATE_PLAYING) {
                _state.value = MediaState.Playing(target, playback, target.metadata)
            } else {
                _state.value = MediaState.Paused(target, playback, target.metadata)
            }
        }
    }

    companion object {
        const val YOUTUBE = "com.google.android.youtube"
        const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
    }
}
