package com.polaralias.audiofocus.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
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

open class AudioFocusAccessibilityService : AccessibilityService() {
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
        if (root != null) {
            val snapshot = try {
                val packageName = root.packageName?.toString()

                // Strict enforcement: Only YouTube and YouTube Music are supported
                val app = packageName.toSupportedApp()
                if (app == null) {
                    Log.v(TAG, "Ignoring unsupported app: $packageName")
                    null
                } else {
                    val bounds = Rect().also(root::getBoundsInScreen)
                    val state = when {
                        isPictureInPicture(event, bounds) -> WindowState.PICTURE_IN_PICTURE
                        else -> deriveStateFromBounds(bounds)
                    }

                    Log.d(TAG, "Window captured for $app: state=$state, bounds=$bounds")
                    WindowSnapshot(app, state)
                }
            } finally {
                root.recycle()
            }

            if (snapshot != null) {
                return snapshot
            }
        } else {
            Log.d(TAG, "No active window root available")
        }

        return capturePictureInPictureWindow(event)
    }

    private fun capturePictureInPictureWindow(event: AccessibilityEvent): WindowSnapshot? {
        val candidates = collectCandidateWindows(event)
        if (candidates.isEmpty()) {
            return null
        }

        val metrics = resources.displayMetrics
        val screenArea = (metrics.widthPixels * metrics.heightPixels).takeIf { it > 0 }
        if (screenArea == null) {
            Log.w(TAG, "Invalid screen dimensions while evaluating PiP windows")
            return null
        }

        candidates.forEach { windowInfo ->
            val root = windowInfo.root ?: return@forEach
            try {
                val packageName = root.packageName?.toString()
                val app = packageName.toSupportedApp()
                if (app == null) {
                    Log.v(TAG, "Skipping unsupported PiP window: $packageName")
                    return@forEach
                }

                val bounds = Rect().also(windowInfo::getBoundsInScreen)
                val coverage = calculateCoverage(bounds, screenArea)
                // Guard TYPE_PICTURE_IN_PICTURE usage (API 26+) to ensure compilation compatibility
                val isPipWindow = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        windowInfo.type == TYPE_PICTURE_IN_PICTURE) ||
                    (coverage != null && coverage < PIP_COVERAGE_THRESHOLD)
                if (isPipWindow) {
                    Log.d(
                        TAG,
                        "PiP window detected for $app via windows list: bounds=$bounds, coverage=$coverage, type=${windowInfo.type}"
                    )
                    return WindowSnapshot(app, WindowState.PICTURE_IN_PICTURE)
                }
            } finally {
                root.recycle()
            }
        }

        return null
    }

    private fun calculateCoverage(bounds: Rect, screenArea: Int): Float? {
        val width = bounds.width().coerceAtLeast(0)
        val height = bounds.height().coerceAtLeast(0)
        val windowArea = width * height
        if (windowArea <= 0 || screenArea <= 0) {
            return null
        }
        return windowArea.toFloat() / screenArea.toFloat()
    }

    internal open fun serviceWindows(): List<AccessibilityWindowInfo> = windows

    private fun collectCandidateWindows(event: AccessibilityEvent): List<AccessibilityWindowInfo> {
        val candidateWindows = ArrayList<AccessibilityWindowInfo>()
        val sourceWindow = event.source?.window
        if (sourceWindow != null) {
            candidateWindows.add(sourceWindow)
        }
        candidateWindows.addAll(serviceWindows())
        return candidateWindows
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
        
        // TYPE_PICTURE_IN_PICTURE constant value from AccessibilityWindowInfo (API 26+)
        // Defined locally as it may not be available in SDK stubs
        private const val TYPE_PICTURE_IN_PICTURE = 4
    }
}
