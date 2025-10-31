package com.polaralias.audiofocus.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.polaralias.audiofocus.window.WindowHeuristics
import com.polaralias.audiofocus.window.WindowInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AccessWindowsService : AccessibilityService() {
    private val heuristics by lazy { WindowHeuristics(this) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        publish()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        publish()
    }

    override fun onInterrupt() = Unit

    private fun publish() {
        val info = heuristics.evaluate(windows?.toList(), resources.displayMetrics)
        _windowInfo.value = info
    }

    companion object {
        private val _windowInfo = MutableStateFlow(WindowInfo.Empty)
        val windowInfo: StateFlow<WindowInfo> = _windowInfo
    }
}
