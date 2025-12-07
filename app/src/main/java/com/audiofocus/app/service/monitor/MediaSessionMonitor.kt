package com.audiofocus.app.service.monitor

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import com.audiofocus.app.core.model.PlaybackStateSimplified
import com.audiofocus.app.core.model.TargetApp
import com.audiofocus.app.domain.PermissionManager
import com.audiofocus.app.service.AppNotificationListenerService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaSessionMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionManager: PermissionManager
) {
    private val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val componentName = ComponentName(context, AppNotificationListenerService::class.java)

    fun observe(): Flow<Map<TargetApp, PlaybackStateSimplified>> = callbackFlow {
        if (!permissionManager.hasNotificationListenerPermission()) {
            Log.w("MediaSessionMonitor", "Notification listener permission missing")
            trySend(emptyMap())
            close()
            return@callbackFlow
        }

        val controllerCallbacks = mutableMapOf<MediaController, MediaController.Callback>()

        // Helper to update state based on current controllers
        fun updateState(controllers: List<MediaController>?) {
            val stateMap = mutableMapOf<TargetApp, PlaybackStateSimplified>()
            controllers?.forEach { controller ->
                val targetApp = TargetApp.entries.find { it.packageName == controller.packageName }
                if (targetApp != null) {
                    val playbackState = controller.playbackState
                    stateMap[targetApp] = mapPlaybackState(playbackState)
                }
            }
            trySend(stateMap)
        }

        val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            // Unregister old callbacks
            controllerCallbacks.forEach { (controller, callback) ->
                controller.unregisterCallback(callback)
            }
            controllerCallbacks.clear()

            // Register new callbacks
            controllers?.forEach { controller ->
                val targetApp = TargetApp.entries.find { it.packageName == controller.packageName }
                if (targetApp != null) {
                    val callback = object : MediaController.Callback() {
                        override fun onPlaybackStateChanged(state: PlaybackState?) {
                            updateState(controllers)
                        }
                    }
                    controller.registerCallback(callback)
                    controllerCallbacks[controller] = callback
                }
            }
            updateState(controllers)
        }

        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(sessionsListener, componentName)
            // Initial fetch
            val initialControllers = mediaSessionManager.getActiveSessions(componentName)
            // Manually trigger listener logic to setup callbacks and initial state
            sessionsListener.onActiveSessionsChanged(initialControllers)
        } catch (e: SecurityException) {
            Log.e("MediaSessionMonitor", "SecurityException: ${e.message}")
            trySend(emptyMap())
            close()
        }

        awaitClose {
            try {
                mediaSessionManager.removeOnActiveSessionsChangedListener(sessionsListener)
            } catch (e: Exception) {
                // Ignore
            }
            controllerCallbacks.forEach { (controller, callback) ->
                controller.unregisterCallback(callback)
            }
            controllerCallbacks.clear()
        }
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
