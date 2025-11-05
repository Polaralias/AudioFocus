package com.polaralias.audiofocus.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.ContextCompat
import com.polaralias.audiofocus.data.PreferencesRepository
import com.polaralias.audiofocus.media.MediaSessionMonitor
import com.polaralias.audiofocus.model.MediaState
import com.polaralias.audiofocus.model.OverlayState
import com.polaralias.audiofocus.model.controllerPackage
import com.polaralias.audiofocus.overlay.MediaTransportCommander
import com.polaralias.audiofocus.overlay.OverlayLayoutFactory
import com.polaralias.audiofocus.overlay.OverlayNotification
import com.polaralias.audiofocus.overlay.TransportCommander
import com.polaralias.audiofocus.policy.PolicyEngine
import com.polaralias.audiofocus.policy.PolicyInput
import com.polaralias.audiofocus.ui.controls.ControlsOverlay
import com.polaralias.audiofocus.ui.controls.ControlsUiState
import com.polaralias.audiofocus.ui.theme.AudioFocusTheme
import com.polaralias.audiofocus.ui.theme.audioFocusColorScheme
import com.polaralias.audiofocus.util.PermissionValidator
import com.polaralias.audiofocus.window.WindowInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OverlayService : Service() {
    companion object {
        private const val TAG = "OverlayService"
        private const val ACTION_TOGGLE_PLAYBACK = "com.polaralias.audiofocus.action.TOGGLE_PLAYBACK"
        private const val ACTION_STOP = "com.polaralias.audiofocus.action.STOP"

        fun start(context: Context) {
            Log.d(TAG, "Requesting service start")
            ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            Log.d(TAG, "Requesting service stop")
            ContextCompat.startForegroundService(context, Intent(context, OverlayService::class.java).apply { action = ACTION_STOP })
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var windowManager: WindowManager
    private lateinit var mediaMonitor: MediaSessionMonitor
    private lateinit var preferences: PreferencesRepository

    private var maskView: View? = null
    private var controlsView: ComposeView? = null
    private var currentOverlay: OverlayState = OverlayState.None
    private var commander: TransportCommander? = null
    private var isForeground = false

    private val controlsState = MutableStateFlow(ControlsUiState())
    private var latestPlaybackState: android.media.session.PlaybackState? = null
    private var latestDuration: Long = 0L
    private var tickerJob: Job? = null
    private var collectorsStarted = false

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "OverlayService onCreate")
        
        // Validate permissions before initializing
        val permissionStatus = PermissionValidator.checkPermissions(applicationContext, TAG)
        if (!permissionStatus.allPermissionsGranted) {
            Log.e(TAG, "Service started without required permissions: ${permissionStatus.getDiagnosticMessage()}")
            Log.e(TAG, "Stopping service immediately to prevent crashes")
            stopSelf()
            return
        }
        
        try {
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            preferences = PreferencesRepository(applicationContext)
            mediaMonitor = MediaSessionMonitor(this, scope)
            val notification = buildNotification(isPlaying = false)
            startForeground(OverlayNotification.NOTIFICATION_ID, notification)
            isForeground = true
            startCollectors()
            Log.i(TAG, "OverlayService initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OverlayService", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}")
        
        when (intent?.action) {
            ACTION_TOGGLE_PLAYBACK -> {
                Log.d(TAG, "Toggle playback requested")
                commander?.togglePlayPause()
            }
            ACTION_STOP -> {
                Log.i(TAG, "Stop action received")
                stopFromRequest()
                return START_NOT_STICKY
            }
        }
        startCollectors()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "OverlayService onDestroy")
        super.onDestroy()
        try {
            removeOverlays()
            mediaMonitor.stop()
            scope.cancel()
            Log.d(TAG, "OverlayService cleanup completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during service cleanup", e)
        }
    }

    private fun startCollectors() {
        if (collectorsStarted) {
            Log.d(TAG, "Collectors already started, skipping")
            return
        }
        collectorsStarted = true
        Log.d(TAG, "Starting collectors")
        
        val component = ComponentName(this, MediaNotificationListener::class.java)
        mediaMonitor.start(component)
        scope.launch {
            try {
                combine(
                    mediaMonitor.state,
                    AccessWindowsService.windowInfo,
                    preferences.preferencesFlow
                ) { media, windowInfo, prefs ->
                    val playback = when (media) {
                        is MediaState.Playing -> media.playbackState
                        is MediaState.Paused -> media.playbackState
                        MediaState.Idle -> null
                    }
                    val metadata = when (media) {
                        is MediaState.Playing -> media.metadata
                        is MediaState.Paused -> media.metadata
                        MediaState.Idle -> null
                    }
                    val overlay = PolicyEngine.compute(
                        PolicyInput(
                            packageName = media.controllerPackage(),
                            playbackState = playback,
                            metadata = metadata,
                            windowInfo = if (windowInfo == WindowInfo.Empty) WindowInfo.Empty else windowInfo,
                            preferences = prefs
                        )
                    )
                    OverlayRenderState(media, overlay)
                }.collectLatest { renderState ->
                    applyRenderState(renderState)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in collectors", e)
            }
        }
    }

    private fun applyRenderState(renderState: OverlayRenderState) {
        updateCommander(renderState.mediaState)
        updateControls(renderState.mediaState, renderState.overlayState)
        when (val overlay = renderState.overlayState) {
            OverlayState.None -> hideOverlay()
            else -> showOverlay(overlay, renderState.mediaState is MediaState.Playing)
        }
    }

    private fun updateCommander(mediaState: MediaState) {
        commander = when (mediaState) {
            is MediaState.Playing -> MediaTransportCommander(mediaState.controller, mediaState.playbackState)
            is MediaState.Paused -> MediaTransportCommander(mediaState.controller, mediaState.playbackState)
            MediaState.Idle -> null
        }
    }

    private fun updateControls(mediaState: MediaState, overlayState: OverlayState) {
        val playback = when (mediaState) {
            is MediaState.Playing -> mediaState.playbackState
            is MediaState.Paused -> mediaState.playbackState
            MediaState.Idle -> null
        }
        val metadata = when (mediaState) {
            is MediaState.Playing -> mediaState.metadata
            is MediaState.Paused -> mediaState.metadata
            MediaState.Idle -> null
        }
        latestPlaybackState = playback
        latestDuration = metadata?.getLong(android.media.MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val isPlaying = mediaState is MediaState.Playing
        val actions = playback?.actions ?: 0L
        val canSeek = actions and android.media.session.PlaybackState.ACTION_SEEK_TO != 0L
        val canSeekBy = canSeek ||
            actions and android.media.session.PlaybackState.ACTION_FAST_FORWARD != 0L ||
            actions and android.media.session.PlaybackState.ACTION_REWIND != 0L
        controlsState.value = ControlsUiState(
            isVisible = overlayState !is OverlayState.None,
            isPlaying = isPlaying,
            position = computePosition(playback, isPlaying),
            duration = latestDuration,
            canSeek = canSeek,
            canSeekBy = canSeekBy
        )
        restartTicker(isPlaying)
    }

    private fun computePosition(
        playback: android.media.session.PlaybackState?,
        isPlaying: Boolean
    ): Long {
        if (playback == null) return 0L
        var position = playback.position
        if (isPlaying && playback.lastPositionUpdateTime > 0L) {
            val delta = SystemClock.elapsedRealtime() - playback.lastPositionUpdateTime
            if (delta > 0) position += delta
        }
        return position.coerceAtLeast(0L)
    }

    private fun restartTicker(isPlaying: Boolean) {
        tickerJob?.cancel()
        if (!isPlaying) return
        tickerJob = scope.launch {
            while (true) {
                val playback = latestPlaybackState
                val position = computePosition(playback, true)
                controlsState.update { it.copy(position = position, duration = latestDuration) }
                delay(500L)
            }
        }
    }

    private fun showOverlay(state: OverlayState, isPlaying: Boolean) {
        try {
            Log.d(TAG, "Showing overlay: state=$state, isPlaying=$isPlaying")
            if (currentOverlay != state) {
                createOrUpdateMask(state)
            } else if (state !is OverlayState.None && maskView != null) {
                updateMaskAlpha(state)
            }
            ensureControls()
            val notification = buildNotification(isPlaying)
            if (!isForeground) {
                startForeground(OverlayNotification.NOTIFICATION_ID, notification)
                isForeground = true
            } else {
                startForeground(OverlayNotification.NOTIFICATION_ID, notification)
            }
            currentOverlay = state
        } catch (e: Exception) {
            Log.e(TAG, "Error showing overlay", e)
        }
    }

    private fun hideOverlay() {
        if (currentOverlay == OverlayState.None && maskView == null && controlsView == null) {
            return
        }
        Log.d(TAG, "Hiding overlay")
        releaseOverlayResources()
    }

    private fun stopFromRequest() {
        Log.i(TAG, "Stopping from request")
        releaseOverlayResources()
        stopSelf()
    }

    private fun releaseOverlayResources() {
        Log.d(TAG, "Releasing overlay resources")
        try {
            removeOverlays()
            tickerJob?.cancel()
            if (isForeground) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                isForeground = false
            }
            OverlayNotification.cancel(this)
            currentOverlay = OverlayState.None
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing overlay resources", e)
        }
    }

    private fun removeOverlays() {
        Log.d(TAG, "Removing overlays: maskView=${maskView != null}, controlsView=${controlsView != null}")
        try {
            maskView?.let { 
                runCatching { 
                    windowManager.removeViewImmediate(it) 
                    Log.d(TAG, "Mask view removed")
                }.onFailure { e ->
                    Log.e(TAG, "Error removing mask view", e)
                }
            }
            controlsView?.let { 
                runCatching { 
                    windowManager.removeViewImmediate(it)
                    Log.d(TAG, "Controls view removed")
                }.onFailure { e ->
                    Log.e(TAG, "Error removing controls view", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during overlay removal", e)
        } finally {
            maskView = null
            controlsView = null
        }
    }

    private fun createOrUpdateMask(state: OverlayState) {
        try {
            val params = OverlayLayoutFactory.maskLayoutFor(this, state)
            if (params == null) {
                Log.w(TAG, "Cannot create mask layout for state: $state")
                return
            }
            
            val alpha = when (state) {
                is OverlayState.Fullscreen -> state.maskAlpha
                is OverlayState.Partial -> state.maskAlpha
                OverlayState.None -> 0f
            }
            val backgroundColor = maskColor(alpha)
            val view = maskView as? FrameLayout ?: FrameLayout(this).also { frame ->
                frame.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                frame.setBackgroundColor(backgroundColor)
                maskView = frame
                try {
                    windowManager.addView(frame, params)
                    Log.d(TAG, "Mask view created and added")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to add mask view to window manager", e)
                    maskView = null
                    throw e
                }
                currentOverlay = state
                return
            }
            view.setBackgroundColor(backgroundColor)
            windowManager.updateViewLayout(view, params)
            Log.d(TAG, "Mask view updated")
            currentOverlay = state
        } catch (e: Exception) {
            Log.e(TAG, "Error creating or updating mask", e)
        }
    }

    private fun updateMaskAlpha(state: OverlayState) {
        val alpha = when (state) {
            is OverlayState.Fullscreen -> state.maskAlpha
            is OverlayState.Partial -> state.maskAlpha
            OverlayState.None -> 0f
        }
        (maskView as? FrameLayout)?.setBackgroundColor(maskColor(alpha))
    }

    private fun ensureControls() {
        if (controlsView != null) {
            Log.d(TAG, "Controls view already exists")
            return
        }
        
        try {
            Log.d(TAG, "Creating controls view")
            val composeView = ComposeView(this).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    AudioFocusTheme {
                        val state by controlsState.collectAsState()
                        ControlsOverlay(
                            state = state,
                            onTogglePlayPause = { commander?.togglePlayPause() },
                            onSeekBy = { commander?.seekBy(it) },
                            onSeekTo = { commander?.seekTo(it) }
                        )
                    }
                }
            }
            val params = OverlayLayoutFactory.controlsLayout()
            windowManager.addView(composeView, params)
            controlsView = composeView
            Log.d(TAG, "Controls view created and added")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create controls view", e)
            controlsView = null
        }
    }

    private fun buildNotification(isPlaying: Boolean) : android.app.Notification {
        val toggleIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, OverlayService::class.java).apply { action = ACTION_TOGGLE_PLAYBACK },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, com.polaralias.audiofocus.settings.SettingsActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return OverlayNotification.build(this, isPlaying, toggleIntent, contentIntent)
    }

    private data class OverlayRenderState(
        val mediaState: MediaState,
        val overlayState: OverlayState
    )
}

private fun OverlayService.maskColor(alpha: Float): Int {
    val scheme = audioFocusColorScheme(this)
    val scrim = scheme.scrim
    val resolvedAlpha = (scrim.alpha * alpha).coerceIn(0f, 1f)
    return scrim.copy(alpha = resolvedAlpha).toArgb()
}
