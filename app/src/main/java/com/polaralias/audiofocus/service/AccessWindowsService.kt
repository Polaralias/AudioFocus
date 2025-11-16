package com.polaralias.audiofocus.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.polaralias.audiofocus.window.WindowCacheTelemetry
import com.polaralias.audiofocus.window.WindowHeuristics
import com.polaralias.audiofocus.window.WindowInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AccessWindowsService : AccessibilityService() {
    companion object {
        private const val TAG = "AccessWindowsService"
        private val _windowInfo = MutableStateFlow(WindowInfo.Empty)
        val windowInfo: StateFlow<WindowInfo> = _windowInfo
        private val _windowCacheTelemetry = MutableStateFlow(WindowCacheTelemetry())
        val windowCacheTelemetry: StateFlow<WindowCacheTelemetry> = _windowCacheTelemetry
    }

    private val heuristics by lazy { WindowHeuristics(this) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")
        publish()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        Log.d(TAG, "Accessibility event received: ${event?.eventType}")
        publish()
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Accessibility service destroyed")
    }

    private fun publish() {
        try {
            val info = heuristics.evaluate(windows?.toList(), resources.displayMetrics)
            _windowInfo.value = info
            _windowCacheTelemetry.value = heuristics.cacheTelemetry.value
            Log.d(
                TAG,
                "Window info published: focused=${info.focusedPackage}, entries=${info.appWindows}"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error publishing window info", e)
        }
    }
}
