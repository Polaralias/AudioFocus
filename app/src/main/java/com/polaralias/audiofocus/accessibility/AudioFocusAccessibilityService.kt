package com.polaralias.audiofocus.accessibility

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
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
        val root = rootInActiveWindow ?: return null
        return try {
            val packageName = root.packageName?.toString()
            val app = packageName.toSupportedApp() ?: return null
            val bounds = Rect().also(root::getBoundsInScreen)
            val state = when {
                isPictureInPicture(event, bounds) -> WindowState.PICTURE_IN_PICTURE
                else -> deriveStateFromBounds(bounds)
            }
            WindowSnapshot(app, state)
        } finally {
            root.recycle()
        }
    }

    private fun isPictureInPicture(event: AccessibilityEvent, bounds: Rect): Boolean {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            event.windowChanges and AccessibilityEvent.WINDOWS_CHANGE_PIP != 0
        ) {
            return true
        }

        val metrics = resources.displayMetrics
        val screenArea = (metrics.widthPixels * metrics.heightPixels).takeIf { it > 0 } ?: return false
        val windowArea = bounds.width().coerceAtLeast(0) * bounds.height().coerceAtLeast(0)
        if (screenArea == 0) return false
        val coverage = windowArea.toFloat() / screenArea.toFloat()
        return coverage < PIP_COVERAGE_THRESHOLD
    }

    private fun deriveStateFromBounds(bounds: Rect): WindowState {
        val metrics = resources.displayMetrics
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels
        if (screenWidth <= 0 || screenHeight <= 0) {
            return WindowState.UNKNOWN
        }

        val widthRatio = bounds.width().coerceAtLeast(0).toFloat() / screenWidth.toFloat()
        val heightRatio = bounds.height().coerceAtLeast(0).toFloat() / screenHeight.toFloat()
        val coverage = widthRatio * heightRatio

        return if (coverage >= FULLSCREEN_COVERAGE_THRESHOLD) {
            WindowState.FULLSCREEN
        } else {
            WindowState.MINIMIZED
        }
    }

    private companion object {
        private const val WINDOW_DEBOUNCE_MS = 250L
        private const val FULLSCREEN_COVERAGE_THRESHOLD = 0.75f
        private const val PIP_COVERAGE_THRESHOLD = 0.12f
    }
}
