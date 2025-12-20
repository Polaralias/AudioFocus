package com.polaralias.audiofocus.service.monitor

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import com.polaralias.audiofocus.core.model.PlaybackStateSimplified
import com.polaralias.audiofocus.core.model.TargetApp
import com.polaralias.audiofocus.domain.PermissionManager
import com.polaralias.audiofocus.service.AppNotificationListenerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaSessionMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionManager: PermissionManager
) {
    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val componentName = ComponentName(context, AppNotificationListenerService::class.java)

    private val _controllers = MutableSharedFlow<List<MediaController>>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val controllers: SharedFlow<List<MediaController>> = _controllers.asSharedFlow()

    private val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var isMonitoring = false

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        updateControllers(controllers)
    }

    init {
        // Ensure initial state
        _controllers.tryEmit(emptyList())
    }

    fun start() {
        if (isMonitoring) return
        if (!permissionManager.hasNotificationListenerPermission()) {
            Log.w("MediaSessionMonitor", "Notification listener permission missing")
            return
        }

        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsListener, componentName)
            val initialControllers = mediaSessionManager.getActiveSessions(componentName)
            updateControllers(initialControllers)
            isMonitoring = true
        } catch (e: SecurityException) {
            Log.e("MediaSessionMonitor", "SecurityException: ${e.message}")
        }
    }

    fun stop() {
        if (!isMonitoring) return
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
            // Ignore
        }
        cleanupCallbacks()
        _controllers.tryEmit(emptyList())
        isMonitoring = false
    }

    // Retain observe() for compatibility with PlaybackStateEngine and Service logs
    fun observe(): Flow<Map<TargetApp, PlaybackStateSimplified>> {
        return controllers.map { controllerList ->
            val stateMap = mutableMapOf<TargetApp, PlaybackStateSimplified>()
            controllerList.forEach { controller ->
                val targetApp = TargetApp.entries.find { it.packageName == controller.packageName }
                if (targetApp != null) {
                    stateMap[targetApp] = mapPlaybackState(controller.playbackState)
                }
            }
            stateMap
        }
    }

    fun getController(targetApp: TargetApp): MediaController? {
        // We can't synchronously get value from SharedFlow easily unless we use replayCache
        return _controllers.replayCache.firstOrNull()?.find { it.packageName == targetApp.packageName }
    }

    private fun updateControllers(newControllers: List<MediaController>?) {
        cleanupCallbacks()

        val validControllers = newControllers ?: emptyList()

        validControllers.forEach { controller ->
            val targetApp = TargetApp.entries.find { it.packageName == controller.packageName }
            if (targetApp != null) {
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        // Force update by emitting a new list copy
                        _controllers.tryEmit(_controllers.replayCache.firstOrNull()?.toList() ?: emptyList())
                    }
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        _controllers.tryEmit(_controllers.replayCache.firstOrNull()?.toList() ?: emptyList())
                    }
                }
                controller.registerCallback(callback)
                controllerCallbacks[controller] = callback
            }
        }
        _controllers.tryEmit(validControllers)
    }

    private fun cleanupCallbacks() {
        controllerCallbacks.forEach { (controller, callback) ->
            controller.unregisterCallback(callback)
        }
        controllerCallbacks.clear()
    }

    private fun mapPlaybackState(state: PlaybackState?): PlaybackStateSimplified {
        if (state == null) return PlaybackStateSimplified.STOPPED
        return when (state.state) {
            PlaybackState.STATE_PLAYING,
            PlaybackState.STATE_BUFFERING,
            PlaybackState.STATE_CONNECTING -> PlaybackStateSimplified.PLAYING
            PlaybackState.STATE_PAUSED -> PlaybackStateSimplified.PAUSED
            else -> PlaybackStateSimplified.STOPPED
        }
    }
}
