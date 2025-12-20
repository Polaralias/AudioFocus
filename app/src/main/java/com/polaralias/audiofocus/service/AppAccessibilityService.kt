package com.polaralias.audiofocus.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.polaralias.audiofocus.service.monitor.AccessibilityMonitor
import com.polaralias.audiofocus.service.monitor.ForegroundAppDetector
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AppAccessibilityService : AccessibilityService() {

    @Inject
    lateinit var accessibilityMonitor: AccessibilityMonitor

    @Inject
    lateinit var foregroundAppDetector: ForegroundAppDetector

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // We use rootInActiveWindow to get the current window content
        // Note: rootInActiveWindow might be null if window is not active or accessible
        val rootNode = rootInActiveWindow
        try {
            accessibilityMonitor.onEvent(event, rootNode)
            foregroundAppDetector.onAccessibilityEvent(event)
        } finally {
            @Suppress("DEPRECATION")
            rootNode?.recycle()
        }
    }

    override fun onInterrupt() {
        // Handle interruption
    }
}
