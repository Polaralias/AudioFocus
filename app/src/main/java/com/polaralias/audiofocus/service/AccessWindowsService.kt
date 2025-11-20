package com.polaralias.audiofocus.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
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
        private const val POLL_INTERVAL_MS = 500L
        private val _windowInfo = MutableStateFlow(WindowInfo.Empty)
        val windowInfo: StateFlow<WindowInfo> = _windowInfo
        private val _windowCacheTelemetry = MutableStateFlow(WindowCacheTelemetry())
        val windowCacheTelemetry: StateFlow<WindowCacheTelemetry> = _windowCacheTelemetry
    }

    private val heuristics by lazy { WindowHeuristics(this) }
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val pollingRunnable = object : Runnable {
        override fun run() {
            publish()
            handler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "Accessibility service connected")
        publish()
        handler.post(pollingRunnable)
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
        handler.removeCallbacks(pollingRunnable)
    }

    private fun publish() {
        try {
            val powerManager = getSystemService(PowerManager::class.java)
            if (powerManager?.isInteractive == false) {
                Log.v(TAG, "Device not interactive, clearing window info")
                _windowInfo.value = WindowInfo.Empty
                _windowCacheTelemetry.value = heuristics.cacheTelemetry.value
                return
            }
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
