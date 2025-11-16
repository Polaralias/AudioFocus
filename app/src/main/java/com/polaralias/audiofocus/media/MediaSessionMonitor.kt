package com.polaralias.audiofocus.media

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.polaralias.audiofocus.model.MediaState
import com.polaralias.audiofocus.service.AccessWindowsService
import com.polaralias.audiofocus.state.SupportedApp
import com.polaralias.audiofocus.state.toSupportedApp
import com.polaralias.audiofocus.window.WindowState
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
        val windowInfo = AccessWindowsService.windowInfo.value
        val candidates = controllers.orEmpty()
            .mapIndexedNotNull { index, controller ->
                val supportedApp = controller.packageName.toSupportedApp() ?: return@mapIndexedNotNull null
                val playbackState = controller.playbackState
                val isPlaying = playbackState?.isActivePlayback() == true
                val appWindowInfo = windowInfo.infoFor(controller.packageName)
                val isForeground = windowInfo.focusedPackage == controller.packageName
                val isPipMatch = appWindowInfo?.state == WindowState.PICTURE_IN_PICTURE
                SessionCandidate(
                    controller = controller,
                    app = supportedApp,
                    playbackState = playbackState,
                    isPlaying = isPlaying,
                    isForeground = isForeground,
                    isPipMatch = isPipMatch,
                    index = index,
                )
            }

        if (candidates.isEmpty()) {
            Log.d(TAG, "No supported media controllers found, publishing idle state")
            bind(null)
            return
        }

        Log.d(TAG, "Controller candidates: ${candidates.joinToString { it.describe() }}")

        val selected = candidates
            .sortedWith(candidateComparator)
            .firstOrNull()

        if (selected == null) {
            Log.d(TAG, "No controller selected after prioritization")
            bind(null)
            return
        }

        val reason = selected.selectionReason()
        Log.i(TAG, "Selecting controller ${selected.controller.packageName}: $reason")
        bind(selected.controller)
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
            if (playback != null && playback.isActivePlayback()) {
                _state.value = MediaState.Playing(target, playback, target.metadata)
            } else {
                _state.value = MediaState.Paused(target, playback, target.metadata)
            }
        }
    }

    companion object {
        private const val TAG = "MediaSessionMonitor"
        private val candidateComparator = compareByDescending<SessionCandidate> {
            it.isPlaying && (it.isForeground || it.isPipMatch)
        }
            .thenByDescending { it.isForeground }
            .thenByDescending { it.isPlaying }
            .thenByDescending { it.isPipMatch }
            .thenBy { it.index }
    }
}

private data class SessionCandidate(
    val controller: MediaController,
    val app: SupportedApp,
    val playbackState: PlaybackState?,
    val isPlaying: Boolean,
    val isForeground: Boolean,
    val isPipMatch: Boolean,
    val index: Int,
) {
    fun describe(): String {
        return "${controller.packageName}(app=$app, playing=$isPlaying, foreground=$isForeground, pip=$isPipMatch)"
    }

    fun selectionReason(): String {
        return when {
            isPlaying && (isForeground || isPipMatch) ->
                "playing session with ${if (isForeground) "foreground" else "PiP"} priority"
            isForeground -> "foreground package priority"
            isPlaying -> "playing fallback"
            else -> "default fallback"
        }
    }
}

private fun PlaybackState.isActivePlayback(): Boolean = when (state) {
    PlaybackState.STATE_PLAYING,
    PlaybackState.STATE_BUFFERING,
    PlaybackState.STATE_FAST_FORWARDING,
    PlaybackState.STATE_REWINDING -> true
    else -> false
}
