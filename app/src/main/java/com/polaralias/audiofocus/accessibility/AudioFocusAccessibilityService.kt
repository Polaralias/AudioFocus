package com.polaralias.audiofocus.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.polaralias.audiofocus.AudioFocusApp
import com.polaralias.audiofocus.state.FocusStateRepository
import com.polaralias.audiofocus.state.WindowSnapshot
import com.polaralias.audiofocus.state.WindowState
import com.polaralias.audiofocus.state.toSupportedApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class AudioFocusAccessibilityService : AccessibilityService() {
    private lateinit var repository: FocusStateRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowSnapshots = MutableSharedFlow<WindowSnapshot?>(
        replay = 1,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override fun onCreate() {
        super.onCreate()
        repository = (application as AudioFocusApp).focusStateRepository
        observeSnapshots()
        windowSnapshots.tryEmit(null)
    }

    override fun onDestroy() {
        windowSnapshots.tryEmit(null)
        serviceScope.cancel()
        repository.updateWindowState(null)
        super.onDestroy()
    }

    override fun onInterrupt() {
        windowSnapshots.tryEmit(null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOWS_CHANGED
        ) {
            return
        }

        val snapshot = captureActiveWindow(event)
        windowSnapshots.tryEmit(snapshot)
    }

    private fun observeSnapshots() {
        serviceScope.launch {
            windowSnapshots
                .debounce(WINDOW_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { snapshot ->
                    repository.updateWindowState(snapshot)
                }
        }
    }

    private fun captureActiveWindow(event: AccessibilityEvent): WindowSnapshot? {
        val root = rootInActiveWindow
        if (root == null) {
            Log.d(TAG, "No active window root available")
            return null
        }
        return try {
            val packageName = root.packageName?.toString()
            
            // Strict enforcement: Only YouTube and YouTube Music are supported
            val app = packageName.toSupportedApp()
            if (app == null) {
                Log.v(TAG, "Ignoring unsupported app: $packageName")
                return null
            }
            
            val bounds = Rect().also(root::getBoundsInScreen)
            val state = when {
                isPictureInPicture(event, bounds) -> WindowState.PICTURE_IN_PICTURE
                else -> deriveStateFromBounds(bounds)
            }
            
            Log.d(TAG, "Window captured for $app: state=$state, bounds=$bounds")
            WindowSnapshot(app, state)
        } finally {
            root.recycle()
        }
    }

    private fun isPictureInPicture(event: AccessibilityEvent, bounds: Rect): Boolean {
        // Check PiP flag from event
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            event.windowChanges and AccessibilityEvent.WINDOWS_CHANGE_PIP != 0
        ) {
            Log.d(TAG, "PiP detected via window change flag")
            return true
        }

        // Fallback: Check window coverage
        val metrics = resources.displayMetrics
        val screenArea = (metrics.widthPixels * metrics.heightPixels).takeIf { it > 0 } ?: run {
            Log.w(TAG, "Invalid screen dimensions")
            return false
        }
        val windowArea = bounds.width().coerceAtLeast(0) * bounds.height().coerceAtLeast(0)
        if (windowArea <= 0) {
            Log.d(TAG, "Invalid window area: $windowArea")
            return false
        }
        val coverage = windowArea.toFloat() / screenArea.toFloat()
        val isPip = coverage < PIP_COVERAGE_THRESHOLD
        if (isPip) {
            Log.d(TAG, "PiP detected via coverage: $coverage (threshold: $PIP_COVERAGE_THRESHOLD)")
        }
        return isPip
    }

    private fun deriveStateFromBounds(bounds: Rect): WindowState {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        
        // Edge case: Invalid screen dimensions
        if (screenWidth <= 0 || screenHeight <= 0) {
            Log.w(TAG, "Invalid screen dimensions: ${screenWidth}x${screenHeight}")
            return WindowState.UNKNOWN
        }

        val widthRatio = bounds.width().coerceAtLeast(0).toFloat() / screenWidth.toFloat()
        val heightRatio = bounds.height().coerceAtLeast(0).toFloat() / screenHeight.toFloat()
        val coverage = widthRatio * heightRatio

        // Edge case: Window too small (likely background/hidden)
        if (coverage <= BACKGROUND_COVERAGE_THRESHOLD) {
            Log.d(TAG, "Window coverage indicates background: $coverage, state=BACKGROUND")
            return WindowState.BACKGROUND
        }

        // Determine fullscreen vs minimized
        val state = if (coverage >= FULLSCREEN_COVERAGE_THRESHOLD) {
            WindowState.FULLSCREEN
        } else {
            WindowState.MINIMIZED
        }
        
        Log.d(TAG, "Window state derived: $state (coverage: $coverage)")
        return state
    }

    private companion object {
        private const val TAG = "AudioFocusAccessibilityService"
        private const val WINDOW_DEBOUNCE_MS = 250L
        private const val FULLSCREEN_COVERAGE_THRESHOLD = 0.75f
        private const val PIP_COVERAGE_THRESHOLD = 0.12f
        private const val BACKGROUND_COVERAGE_THRESHOLD = 0.01f
    }
}
