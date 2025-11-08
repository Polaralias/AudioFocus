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
import com.polaralias.audiofocus.overlay.OverlayAnimator
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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OverlayService : Service() {
    companion object {
        private const val TAG = "OverlayService"
        private const val ACTION_TOGGLE_PLAYBACK = "com.polaralias.audiofocus.action.TOGGLE_PLAYBACK"
        private const val ACTION_STOP = "com.polaralias.audiofocus.action.STOP"
        
        // Debounce period: Wait this long before applying state changes to prevent flicker
        // on rapid state transitions (e.g., transient window state changes during animations)
        private const val DEBOUNCE_DELAY_MS = 300L
        
        // Grace period: Once overlay is visible, tolerate brief "hide" signals for this duration
        // This prevents overlay from disappearing during momentary detection mismatches
        private const val GRACE_PERIOD_MS = 500L

        fun start(context: Context) {
            Log.d(TAG, "Requesting service start")
            val permissionStatus = PermissionValidator.checkPermissions(context.applicationContext, TAG)
            if (!permissionStatus.canPostNotifications) {
                Log.w(
                    TAG,
                    "Cannot start OverlayService - notifications are disabled or POST_NOTIFICATIONS not granted"
                )
                return
            }
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
    private var hideAnimationJob: Job? = null
    
    // Debounce mechanism: Track pending overlay state changes to prevent flickering
    // Grace period allows tolerating brief mismatches in detection without hiding overlay
    private var pendingOverlayState: OverlayState? = null
    private var debounceJob: Job? = null
    private var lastVisibleState: OverlayState = OverlayState.None
    private var lastVisibleTimestamp: Long = 0L

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "OverlayService onCreate - starting initialization")
        
        try {
            // Validate permissions before initializing
            Log.d(TAG, "Checking permissions before service initialization")
            val permissionStatus = PermissionValidator.checkPermissions(applicationContext, TAG)
            if (!permissionStatus.allPermissionsGranted) {
                Log.e(TAG, "Service started without required permissions: ${permissionStatus.getDiagnosticMessage()}")
                Log.e(TAG, "Stopping service immediately to prevent crashes")
                stopSelf()
                return
            }
            Log.i(TAG, "All permissions verified - proceeding with service initialization")
            
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            preferences = PreferencesRepository(applicationContext)
            mediaMonitor = MediaSessionMonitor(this, scope)
            
            Log.d(TAG, "Building foreground notification")
            val notification = buildNotification(isPlaying = false)
            startForeground(OverlayNotification.NOTIFICATION_ID, notification)
            isForeground = true
            Log.i(TAG, "Service started in foreground successfully")
            
            // NEW BEHAVIOR: Attach overlay views immediately when service starts
            // This keeps views present (like AudioFocus_old), we'll control visibility via alpha/visibility
            // Views stay attached as long as overlay permission is granted and service is running
            Log.d(TAG, "Attaching overlay views to WindowManager")
            attachOverlayViews()
            
            Log.d(TAG, "Starting data collectors")
            startCollectors()
            Log.i(TAG, "OverlayService initialized successfully with overlay views attached")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error initializing OverlayService", e)
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: action=${intent?.action}")
        
        try {
            when (intent?.action) {
                ACTION_TOGGLE_PLAYBACK -> {
                    Log.d(TAG, "Toggle playback requested")
                    try {
                        commander?.togglePlayPause()
                        Log.d(TAG, "Toggle playback command sent")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error toggling playback", e)
                    }
                }
                ACTION_STOP -> {
                    Log.i(TAG, "Stop action received")
                    stopFromRequest()
                    return START_NOT_STICKY
                }
            }
            val permissionStatus = PermissionValidator.checkPermissions(applicationContext, TAG)
            if (!permissionStatus.canPostNotifications) {
                Log.w(
                    TAG,
                    "Stopping service - unable to post notifications: ${permissionStatus.getDiagnosticMessage()}"
                )
                stopFromRequest()
                return START_NOT_STICKY
            }
            startCollectors()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "OverlayService onDestroy - starting cleanup")
        super.onDestroy()
        try {
            Log.d(TAG, "Removing overlay views")
            removeOverlays()
            Log.d(TAG, "Stopping media monitor")
            mediaMonitor.stop()
            Log.d(TAG, "Cancelling coroutine scope")
            scope.cancel()
            Log.i(TAG, "OverlayService cleanup completed successfully")
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
        Log.i(TAG, "Starting data collectors")
        
        try {
            val component = ComponentName(this, MediaNotificationListener::class.java)
            mediaMonitor.start(component)
            Log.d(TAG, "Media monitor started")
            
            scope.launch {
                try {
                    Log.d(TAG, "Starting combined flow collection")
                    combine(
                        mediaMonitor.state,
                        AccessWindowsService.windowInfo,
                        preferences.preferencesFlow.catch { e ->
                            Log.e(TAG, "Error reading preferences in collector, using defaults", e)
                            emit(com.polaralias.audiofocus.data.OverlayPreferences())
                        }
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
                    Log.e(TAG, "Error in data collectors flow", e)
                }
            }
            Log.i(TAG, "Data collectors started successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting collectors", e)
        }
    }

    private fun applyRenderState(renderState: OverlayRenderState) {
        updateCommander(renderState.mediaState)
        updateControls(renderState.mediaState, renderState.overlayState)
        
        // NEW BEHAVIOR: Use debouncing and grace period to prevent flicker
        // Instead of immediately showing/hiding overlay, we schedule the change with a delay
        // This allows us to ignore transient state changes and rapid transitions
        applyOverlayStateWithDebounce(renderState.overlayState, renderState.mediaState is MediaState.Playing)
    }
    
    /**
     * Apply overlay state with debounce and grace period logic.
     * 
     * DEBOUNCE: Wait DEBOUNCE_DELAY_MS before applying state changes. If multiple state changes
     * arrive within this window, only the last one is applied. This prevents flickering during
     * rapid transitions (e.g., during app UI animations or window state changes).
     * 
     * GRACE PERIOD: Once overlay is visible, we tolerate "hide" commands for GRACE_PERIOD_MS
     * before actually hiding. This prevents momentary detection mismatches from causing the
     * overlay to disappear. If a "show" command arrives during grace period, we cancel the hide.
     * 
     * This approach keeps overlay views attached (like old app) but controls visibility in-place.
     */
    private fun applyOverlayStateWithDebounce(newState: OverlayState, isPlaying: Boolean) {
        // Cancel any pending debounce job
        debounceJob?.cancel()
        
        // Store the pending state
        pendingOverlayState = newState
        
        // If requesting to hide but we recently showed overlay, apply grace period
        if (newState is OverlayState.None && lastVisibleState !is OverlayState.None) {
            val timeSinceVisible = System.currentTimeMillis() - lastVisibleTimestamp
            if (timeSinceVisible < GRACE_PERIOD_MS) {
                Log.d(TAG, "Grace period active (${timeSinceVisible}ms < ${GRACE_PERIOD_MS}ms), delaying hide")
                // Schedule hide after remaining grace period
                val remainingGrace = GRACE_PERIOD_MS - timeSinceVisible
                debounceJob = scope.launch {
                    delay(remainingGrace)
                    applyOverlayStateImmediate(newState, isPlaying)
                }
                return
            }
        }
        
        // Apply state change after debounce delay
        debounceJob = scope.launch {
            delay(DEBOUNCE_DELAY_MS)
            applyOverlayStateImmediate(newState, isPlaying)
        }
    }
    
    /**
     * Immediately apply overlay state by updating view visibility.
     * 
     * NEW BEHAVIOR: Views remain attached to WindowManager. We control visibility using
     * View.VISIBLE and View.GONE (or alpha for smooth transitions). This prevents the
     * add/remove overhead and potential flickering from repeated WindowManager operations.
     */
    private fun applyOverlayStateImmediate(state: OverlayState, isPlaying: Boolean) {
        Log.d(TAG, "Applying overlay state: $state (isPlaying=$isPlaying)")
        
        when (state) {
            OverlayState.None -> setOverlayVisibility(visible = false, isPlaying)
            else -> {
                updateMaskForState(state)
                setOverlayVisibility(visible = true, isPlaying)
                
                // Track when overlay became visible for grace period
                if (lastVisibleState is OverlayState.None) {
                    lastVisibleTimestamp = System.currentTimeMillis()
                }
                lastVisibleState = state
            }
        }
        
        currentOverlay = state
        
        // Update notification
        val notification = buildNotification(isPlaying)
        startForeground(OverlayNotification.NOTIFICATION_ID, notification)
        if (!isForeground) {
            isForeground = true
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
            canSeekBy = canSeekBy,
            isPartialOverlay = overlayState is OverlayState.Partial
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
    
    /**
     * Attach overlay views to WindowManager early (onCreate).
     * NEW BEHAVIOR: Views are attached once and kept attached (like AudioFocus_old).
     * We control visibility in-place rather than repeatedly adding/removing views.
     * This provides better startup reliability and prevents flickering.
     */
    private fun attachOverlayViews() {
        try {
            Log.i(TAG, "Attaching overlay views to WindowManager")
            
            // Create and attach mask view (initially hidden)
            val maskFrame = FrameLayout(this).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                visibility = View.GONE // Start hidden
                alpha = 0f
            }
            
            // Use Fullscreen state for initial layout params (will be updated later based on actual state)
            // Fullscreen params work for all states as they cover the entire screen
            try {
                Log.d(TAG, "Creating mask layout parameters")
                val maskParams = OverlayLayoutFactory.maskLayoutFor(this, OverlayState.Fullscreen())
                if (maskParams != null) {
                    Log.d(TAG, "Adding mask view to WindowManager")
                    windowManager.addView(maskFrame, maskParams)
                    maskView = maskFrame
                    Log.i(TAG, "Mask view attached successfully")
                } else {
                    Log.e(TAG, "Failed to create mask layout parameters - maskParams is null")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error attaching mask view to WindowManager", e)
            }
            
            // Create and attach controls view (initially hidden)
            try {
                Log.d(TAG, "Creating controls ComposeView")
                val composeView = ComposeView(this).apply {
                    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                    visibility = View.GONE // Start hidden
                    alpha = 0f
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
                
                Log.d(TAG, "Creating controls layout parameters")
                val controlsParams = OverlayLayoutFactory.controlsLayout()
                Log.d(TAG, "Adding controls view to WindowManager")
                windowManager.addView(composeView, controlsParams)
                controlsView = composeView
                Log.i(TAG, "Controls view attached successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error attaching controls view to WindowManager", e)
            }
            
            Log.i(TAG, "Overlay views attachment completed - maskView=${maskView != null}, controlsView=${controlsView != null}")
        } catch (e: Exception) {
            Log.e(TAG, "Fatal error attaching overlay views", e)
            // Clean up on failure
            maskView = null
            controlsView = null
        }
    }
    
    /**
     * Update mask view layout parameters and appearance for the given overlay state.
     * This updates the existing attached view rather than recreating it.
     */
    private fun updateMaskForState(state: OverlayState) {
        val mask = maskView as? FrameLayout ?: run {
            Log.w(TAG, "Mask view not attached, cannot update")
            return
        }
        
        try {
            Log.d(TAG, "Updating mask for state: $state")
            
            // Update layout parameters if state changed
            try {
                val params = OverlayLayoutFactory.maskLayoutFor(this, state)
                if (params != null) {
                    windowManager.updateViewLayout(mask, params)
                    Log.d(TAG, "Mask layout parameters updated successfully")
                } else {
                    Log.w(TAG, "Failed to get layout parameters for state: $state")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating mask layout parameters", e)
            }
            
            // Update background color based on mask alpha
            try {
                val alpha = when (state) {
                    is OverlayState.Fullscreen -> state.maskAlpha
                    is OverlayState.Partial -> state.maskAlpha
                    OverlayState.None -> 0f
                }
                mask.setBackgroundColor(maskColor(alpha))
                Log.d(TAG, "Mask background color updated with alpha: $alpha")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating mask background color", e)
            }
            
            Log.d(TAG, "Mask view updated successfully for state: $state")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating mask for state: $state", e)
        }
    }
    
    /**
     * Control overlay visibility by setting View.VISIBLE or View.GONE and animating alpha.
     * NEW BEHAVIOR: Views stay attached, we just change visibility/alpha in-place.
     * This prevents the overhead of repeated WindowManager add/remove calls.
     */
    private fun setOverlayVisibility(visible: Boolean, isPlaying: Boolean) {
        Log.i(TAG, "Setting overlay visibility: visible=$visible, isPlaying=$isPlaying")
        
        try {
            if (visible) {
                // Make views visible with fade-in animation
                // Note: OverlayAnimator.fadeIn handles setting visibility internally
                maskView?.let { view ->
                    if (view.visibility != View.VISIBLE || view.alpha < 1f) {
                        scope.launch {
                            try {
                                Log.d(TAG, "Fading in mask view")
                                OverlayAnimator.fadeIn(view)
                                Log.d(TAG, "Mask view fade-in completed")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error animating mask fade-in", e)
                            }
                        }
                    }
                }
                
                controlsView?.let { view ->
                    if (view.visibility != View.VISIBLE || view.alpha < 1f) {
                        scope.launch {
                            try {
                                Log.d(TAG, "Fading in controls view")
                                OverlayAnimator.fadeIn(view)
                                Log.d(TAG, "Controls view fade-in completed")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error animating controls fade-in", e)
                            }
                        }
                    }
                }
            } else {
                // Hide views with fade-out animation, then set GONE
                // Note: OverlayAnimator.fadeOut handles setting visibility to GONE internally
                hideAnimationJob?.cancel()
                hideAnimationJob = scope.launch {
                    try {
                        val mask = maskView
                        val controls = controlsView
                        
                        if (mask != null || controls != null) {
                            Log.d(TAG, "Starting fade-out animation for overlay views")
                            // Fade out both views in parallel
                            val maskJob = async {
                                mask?.let { 
                                    Log.d(TAG, "Fading out mask view")
                                    OverlayAnimator.fadeOut(it) 
                                }
                            }
                            val controlsJob = async {
                                controls?.let { 
                                    Log.d(TAG, "Fading out controls view")
                                    OverlayAnimator.fadeOut(it) 
                                }
                            }
                            maskJob.await()
                            controlsJob.await()
                            Log.d(TAG, "Fade-out animation completed")
                        }
                        
                        // Track that overlay is no longer visible
                        lastVisibleState = OverlayState.None
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during hide animation", e)
                        // Fall back to immediate hide on error
                        try {
                            maskView?.let { OverlayAnimator.hideImmediate(it) }
                            controlsView?.let { OverlayAnimator.hideImmediate(it) }
                            lastVisibleState = OverlayState.None
                        } catch (e2: Exception) {
                            Log.e(TAG, "Error during immediate hide fallback", e2)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting overlay visibility", e)
        }
    }

    private fun stopFromRequest() {
        Log.i(TAG, "Stopping service from request")
        try {
            releaseOverlayResources()
            stopSelf()
            Log.d(TAG, "Service stop completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error during service stop", e)
            // Still try to stop self even on error
            try {
                stopSelf()
            } catch (e2: Exception) {
                Log.e(TAG, "Error calling stopSelf", e2)
            }
        }
    }

    private fun releaseOverlayResources() {
        Log.i(TAG, "Releasing overlay resources")
        try {
            // Cancel any ongoing animations and debounce jobs
            Log.d(TAG, "Cancelling pending jobs")
            try {
                hideAnimationJob?.cancel()
                hideAnimationJob = null
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling hide animation job", e)
            }
            
            try {
                debounceJob?.cancel()
                debounceJob = null
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling debounce job", e)
            }
            
            // Remove overlay views from WindowManager
            Log.d(TAG, "Removing overlay views")
            removeOverlays()
            
            try {
                tickerJob?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling ticker job", e)
            }
            
            if (isForeground) {
                try {
                    Log.d(TAG, "Stopping foreground service")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    isForeground = false
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping foreground", e)
                }
            }
            
            try {
                OverlayNotification.cancel(this)
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling notification", e)
            }
            
            currentOverlay = OverlayState.None
            lastVisibleState = OverlayState.None
            Log.i(TAG, "Overlay resources released successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing overlay resources", e)
        }
    }

    private fun removeOverlays() {
        Log.i(TAG, "Removing overlays: maskView=${maskView != null}, controlsView=${controlsView != null}")
        try {
            maskView?.let { 
                runCatching {
                    Log.d(TAG, "Removing mask view from WindowManager")
                    // Ensure animations are cancelled before removal
                    OverlayAnimator.hideImmediate(it)
                    windowManager.removeViewImmediate(it) 
                    Log.i(TAG, "Mask view removed successfully")
                }.onFailure { e ->
                    Log.e(TAG, "Error removing mask view", e)
                }
            }
            
            controlsView?.let { 
                runCatching {
                    Log.d(TAG, "Removing controls view from WindowManager")
                    // Ensure animations are cancelled before removal
                    OverlayAnimator.hideImmediate(it)
                    windowManager.removeViewImmediate(it)
                    Log.i(TAG, "Controls view removed successfully")
                }.onFailure { e ->
                    Log.e(TAG, "Error removing controls view", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during overlay removal", e)
        } finally {
            maskView = null
            controlsView = null
            Log.d(TAG, "Overlay view references cleared")
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
