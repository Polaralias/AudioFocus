package com.polaralias.audiofocus.service.monitor

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.polaralias.audiofocus.core.model.PlaybackType
import com.polaralias.audiofocus.core.model.TargetApp
import com.polaralias.audiofocus.core.model.WindowState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class AccessibilityState(
    val windowState: WindowState = WindowState.NOT_VISIBLE,
    val playbackType: PlaybackType = PlaybackType.NONE
)

@Singleton
class AccessibilityMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val _states = MutableStateFlow<Map<TargetApp, AccessibilityState>>(emptyMap())
    val states: StateFlow<Map<TargetApp, AccessibilityState>> = _states.asStateFlow()

    private val lastAnalysisTime = mutableMapOf<String, Long>()
    private val THROTTLE_INTERVAL_MS = 500L

    private val displayMetrics = context.resources.displayMetrics
    private val screenHeight = displayMetrics.heightPixels
    private val screenWidth = displayMetrics.widthPixels

    fun onEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?) {
        val packageName = event.packageName?.toString() ?: return
        val targetApp = TargetApp.entries.find { it.packageName == packageName } ?: return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            val currentTime = System.currentTimeMillis()
            val lastTime = lastAnalysisTime[packageName] ?: 0L
            if (currentTime - lastTime < THROTTLE_INTERVAL_MS) {
                return
            }
            lastAnalysisTime[packageName] = currentTime
        }

        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            return
        }

        if (rootNode == null) {
            // If root node is null, we can't analyze content.
            // It might mean the window is gone or we lost access.
            // For now, we don't clear state to avoid flickering,
            // but in a real scenario we might want to set to BACKGROUND if we know the app closed.
            return
        }

        val state = analyzeWindow(rootNode)
        _states.update { current ->
            current + (targetApp to state)
        }
    }

    @Suppress("DEPRECATION")
    private fun analyzeWindow(root: AccessibilityNodeInfo): AccessibilityState {
        var hasVideoSurface = false
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        // Safety counter to prevent infinite loops or massive tree hangs
        var nodeCount = 0
        val maxNodes = 2000

        while (queue.isNotEmpty() && nodeCount < maxNodes) {
            val node = queue.removeFirst()
            nodeCount++

            // Check for video surface
            if (!hasVideoSurface) {
                if (node.className != null && (
                    node.className == "android.view.SurfaceView" ||
                    node.className == "android.view.TextureView" ||
                    node.className.contains("SurfaceView") ||
                    node.className.contains("TextureView")
                )) {
                    if (node.isVisibleToUser) {
                        hasVideoSurface = true
                    }
                }
            }

            // Optimization: Only traverse children if we haven't found a surface yet.
            // If we found it, we just need to finish recycling the queue.
            if (!hasVideoSurface) {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }

            if (node != root) {
                node.recycle()
            }
        }

        // Clean up remaining queue items if we hit limit
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (node != root) node.recycle()
        }

        val playbackType = if (hasVideoSurface) PlaybackType.VISIBLE_VIDEO else PlaybackType.NONE

        // Window State Determination
        val rect = Rect()
        root.getBoundsInScreen(rect)

        // Simple heuristic for window state
        // Fullscreen usually matches screen dimensions
        val isFullscreen = rect.width() >= screenWidth && rect.height() >= screenHeight

        val windowState = when {
            isFullscreen -> WindowState.FOREGROUND_FULLSCREEN
            // PiP is usually smaller and floating.
            // How to distinguish PiP from Dialog?
            // PiP usually has specific window type but AccessibilityNode doesn't show window type directly easily.
            // Assuming any non-fullscreen visible window is PiP or Minimised for now.
            rect.width() > 0 && rect.height() > 0 -> WindowState.PICTURE_IN_PICTURE
            else -> WindowState.NOT_VISIBLE
        }

        return AccessibilityState(windowState, playbackType)
    }
}
