package com.polaralias.audiofocus.notifications

import android.app.Notification
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.polaralias.audiofocus.AudioFocusApp
import com.polaralias.audiofocus.state.FocusStateRepository
import com.polaralias.audiofocus.state.PlaybackActivity
import com.polaralias.audiofocus.state.PlaybackContentType
import com.polaralias.audiofocus.state.PlaybackSnapshot
import com.polaralias.audiofocus.state.SupportedApp
import com.polaralias.audiofocus.state.toSupportedApp
import com.polaralias.audiofocus.util.ExpiringValueCache

class AudioFocusNotificationListener : NotificationListenerService() {
    private lateinit var repository: FocusStateRepository

    private val callbackHandler = Handler(Looper.getMainLooper())
    private val mediaControllers = mutableMapOf<SupportedApp, ControllerEntry>()
    private val latestSnapshots = mutableMapOf<SupportedApp, PlaybackSnapshot>()
    private val contentTypeCache = ExpiringValueCache<String, PlaybackContentType>(CONTENT_TYPE_CACHE_TTL_MS)
    private var lastPublished: PlaybackSnapshot? = null
    @Volatile
    private var activeApp: SupportedApp? = null

    override fun onCreate() {
        super.onCreate()
        repository = (application as AudioFocusApp).focusStateRepository
        instance = this
    }

    override fun onDestroy() {
        mediaControllers.values.forEach { entry ->
            entry.controller.unregisterCallback(entry.callback)
        }
        mediaControllers.clear()
        latestSnapshots.clear()
        contentTypeCache.clear()
        repository.updatePlaybackState(null)
        activeApp = null
        instance = null
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeNotifications?.forEach { notification ->
            handleNotification(notification)
        }
        dispatchCurrentSnapshot()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handleNotification(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val app = sbn.packageName.toSupportedApp() ?: return
        val entry = mediaControllers[app] ?: return
        if (entry.notificationKey == sbn.key) {
            removeController(app)
        }
    }

    private fun handleNotification(sbn: StatusBarNotification) {
        val app = sbn.packageName.toSupportedApp() ?: return
        val token = extractSessionToken(sbn) ?: run {
            removeController(app)
            return
        }

        val existing = mediaControllers[app]
        if (existing != null && existing.notificationKey == sbn.key) {
            publishSnapshot(app, existing.controller)
            return
        }

        existing?.let { removeController(app) }

        val controller = runCatching { MediaController(this, token) }.getOrNull() ?: return
        val callback = ControllerCallback(app, controller)
        controller.registerCallback(callback, callbackHandler)
        mediaControllers[app] = ControllerEntry(sbn.key, controller, callback)
        publishSnapshot(app, controller)
    }

    private fun publishSnapshot(app: SupportedApp, controller: MediaController) {
        val snapshot = buildSnapshot(app, controller.playbackState, controller.metadata)
        if (snapshot != null) {
            latestSnapshots[app] = snapshot
        } else {
            latestSnapshots.remove(app)
        }
        dispatchCurrentSnapshot()
    }

    private fun dispatchCurrentSnapshot() {
        val snapshot = selectSnapshot()
        if (snapshot != lastPublished) {
            lastPublished = snapshot
            repository.updatePlaybackState(snapshot)
        }
        activeApp = snapshot?.app
    }

    private fun selectSnapshot(): PlaybackSnapshot? {
        for (app in SupportedApp.values()) {
            val snapshot = latestSnapshots[app]
            if (snapshot?.activity == PlaybackActivity.PLAYING) {
                return snapshot
            }
        }
        for (app in SupportedApp.values()) {
            latestSnapshots[app]?.let { return it }
        }
        return null
    }

    private fun buildSnapshot(
        app: SupportedApp,
        playbackState: PlaybackState?,
        metadata: MediaMetadata?,
    ): PlaybackSnapshot? {
        if (playbackState == null && metadata == null) {
            return null
        }
        val activity = mapActivity(playbackState)
        val contentType = deriveContentType(app, activity, metadata)
        return PlaybackSnapshot(app, activity, contentType)
    }

    private fun mapActivity(playbackState: PlaybackState?): PlaybackActivity {
        val state = playbackState?.state ?: PlaybackState.STATE_NONE
        return when (state) {
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

    private fun deriveContentType(
        app: SupportedApp,
        activity: PlaybackActivity,
        metadata: MediaMetadata?,
    ): PlaybackContentType {
        return when (app) {
            SupportedApp.YOUTUBE -> if (activity == PlaybackActivity.PLAYING) {
                PlaybackContentType.VIDEO
            } else {
                PlaybackContentType.UNKNOWN
            }

            SupportedApp.YOUTUBE_MUSIC -> deriveYouTubeMusicContent(metadata)
        }
    }

    private fun deriveYouTubeMusicContent(metadata: MediaMetadata?): PlaybackContentType {
        if (metadata == null) {
            return PlaybackContentType.AUDIO_ONLY
        }

        val key = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: DEFAULT_YTM_CACHE_KEY

        val contentType = contentTypeCache.getOrPut(key) {
            val width = metadata.getLong(METADATA_KEY_VIDEO_WIDTH)
            val height = metadata.getLong(METADATA_KEY_VIDEO_HEIGHT)
            val presentationType = metadata.getLong(METADATA_KEY_PRESENTATION_DISPLAY_TYPE)

            if ((width > 0 && height > 0) || presentationType == PRESENTATION_DISPLAY_TYPE_VIDEO) {
                PlaybackContentType.VIDEO
            } else {
                PlaybackContentType.AUDIO_ONLY
            }
        }
        return contentType
    }

    private fun extractSessionToken(sbn: StatusBarNotification): MediaSession.Token? {
        val extras = sbn.notification.extras ?: return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelable(Notification.EXTRA_MEDIA_SESSION) as? MediaSession.Token
        }
    }

    private fun removeController(app: SupportedApp) {
        val entry = mediaControllers.remove(app) ?: return
        entry.controller.unregisterCallback(entry.callback)
        latestSnapshots.remove(app)
        dispatchCurrentSnapshot()
    }

    private fun performOnActiveController(action: (MediaController) -> Unit) {
        val app = activeApp ?: return
        val entry = mediaControllers[app] ?: return
        callbackHandler.post {
            action(entry.controller)
        }
    }

    private fun togglePlayPauseInternal() {
        performOnActiveController { controller ->
            val state = controller.playbackState
            val transport = controller.transportControls
            val isPlaying = state?.state == PlaybackState.STATE_PLAYING ||
                state?.state == PlaybackState.STATE_BUFFERING ||
                state?.state == PlaybackState.STATE_FAST_FORWARDING ||
                state?.state == PlaybackState.STATE_REWINDING
            if (isPlaying) {
                transport.pause()
            } else {
                transport.play()
            }
        }
    }

    private fun seekByInternal(offsetMs: Long) {
        if (offsetMs == 0L) return
        performOnActiveController { controller ->
            val state = controller.playbackState ?: return@performOnActiveController
            val transport = controller.transportControls
            val currentPosition = state.position
            val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)
            val target = (currentPosition + offsetMs).coerceAtLeast(0L).let { position ->
                if (duration != null && duration > 0) {
                    position.coerceAtMost(duration)
                } else {
                    position
                }
            }
            transport.seekTo(target)
        }
    }

    private fun hasActiveControllerInternal(): Boolean {
        val app = activeApp ?: return false
        return mediaControllers[app]?.controller != null
    }

    private inner class ControllerCallback(
        private val app: SupportedApp,
        private val controller: MediaController,
    ) : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            publishSnapshot(app, controller)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            publishSnapshot(app, controller)
        }

        override fun onSessionDestroyed() {
            removeController(app)
        }
    }

    private data class ControllerEntry(
        val notificationKey: String,
        val controller: MediaController,
        val callback: MediaController.Callback,
    )

    companion object {
        private const val CONTENT_TYPE_CACHE_TTL_MS = 30_000L
        private const val METADATA_KEY_VIDEO_WIDTH = "android.media.metadata.VIDEO_WIDTH"
        private const val METADATA_KEY_VIDEO_HEIGHT = "android.media.metadata.VIDEO_HEIGHT"
        private const val METADATA_KEY_PRESENTATION_DISPLAY_TYPE = "android.media.metadata.PRESENTATION_DISPLAY_TYPE"
        private const val PRESENTATION_DISPLAY_TYPE_VIDEO = 1L
        private const val DEFAULT_YTM_CACHE_KEY = "youtube_music_default"

        @Volatile
        private var instance: AudioFocusNotificationListener? = null

        fun togglePlayPause() {
            instance?.togglePlayPauseInternal()
        }

        fun seekBy(offsetMs: Long) {
            instance?.seekByInternal(offsetMs)
        }

        fun hasActiveController(): Boolean {
            return instance?.hasActiveControllerInternal() ?: false
        }
    }
}
