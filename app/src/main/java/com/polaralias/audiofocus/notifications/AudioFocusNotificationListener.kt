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
import android.util.Log
import com.polaralias.audiofocus.AudioFocusApp
import com.polaralias.audiofocus.media.YouTubeMusicMetadata
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
            SupportedApp.YOUTUBE -> {
                // For YouTube: treat all playing content as video
                // YouTube app doesn't provide reliable metadata for content type
                val contentType = if (activity == PlaybackActivity.PLAYING) {
                    PlaybackContentType.VIDEO
                } else {
                    PlaybackContentType.UNKNOWN
                }
                Log.d(TAG, "YouTube content type: $contentType (activity: $activity)")
                contentType
            }

            SupportedApp.YOUTUBE_MUSIC -> {
                val contentType = deriveYouTubeMusicContent(metadata)
                Log.d(TAG, "YouTube Music content type: $contentType")
                contentType
            }
        }
    }

    private fun deriveYouTubeMusicContent(metadata: MediaMetadata?): PlaybackContentType {
        if (metadata == null) {
            Log.d(TAG, "YouTube Music: No metadata available, assuming audio-only")
            return PlaybackContentType.AUDIO_ONLY
        }

        val baseKey = metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: DEFAULT_YTM_CACHE_KEY

        val videoMetadata = YouTubeMusicMetadata.extractVideoMetadata(metadata)
        val contentType = when {
            videoMetadata == null -> {
                Log.d(TAG, "YouTube Music: No video metadata hints, assuming audio-only")
                PlaybackContentType.AUDIO_ONLY
            }
            videoMetadata.indicatesVideo -> {
                Log.d(
                    TAG,
                    "YouTube Music: Video detected (width=${videoMetadata.videoWidth}, " +
                        "height=${videoMetadata.videoHeight}, presentation=${videoMetadata.presentationDisplayType})"
                )
                PlaybackContentType.VIDEO
            }
            else -> {
                Log.d(
                    TAG,
                    "YouTube Music: Audio-only content (width=${videoMetadata.videoWidth}, " +
                        "height=${videoMetadata.videoHeight}, presentation=${videoMetadata.presentationDisplayType})"
                )
                PlaybackContentType.AUDIO_ONLY
            }
        }

        val cacheKey = buildYouTubeMusicCacheKey(baseKey, videoMetadata)
        val cachedValue = contentTypeCache.get(cacheKey)
        if (cachedValue != contentType) {
            Log.d(TAG, "YouTube Music: Updating cached content type for $cacheKey to $contentType")
        }
        contentTypeCache.put(cacheKey, contentType)

        return contentType
    }

    private fun buildYouTubeMusicCacheKey(
        baseKey: String,
        videoMetadata: YouTubeMusicMetadata.VideoMetadata?,
    ): String {
        val modeHint = when {
            videoMetadata == null -> "no_video_hint"
            videoMetadata.indicatesVideo -> "video_mode"
            else -> "audio_mode"
        }
        val presentationHint = videoMetadata?.presentationDisplayType ?: -1L
        return "$baseKey#$modeHint#$presentationHint"
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
        private const val TAG = "AudioFocusNotificationListener"
        private const val CONTENT_TYPE_CACHE_TTL_MS = 30_000L
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
