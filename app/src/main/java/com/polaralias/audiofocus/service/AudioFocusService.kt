package com.polaralias.audiofocus.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import com.polaralias.audiofocus.window.WindowHeuristics
import com.polaralias.audiofocus.window.WindowInfo
import com.polaralias.audiofocus.window.WindowState
import com.polaralias.audiofocus.window.PlayMode

class AudioFocusService : AccessibilityService(), MediaSessionManager.OnActiveSessionsChangedListener {

    private lateinit var mediaSessionManager: MediaSessionManager
    private lateinit var controllerManager: MediaControllerManager
    private lateinit var mainHandler: Handler
    private var overlayView: OverlayView? = null
    private var windowManager: WindowManager? = null

    private val heuristics by lazy { WindowHeuristics(this) }

    private val pollingRunnable = object : Runnable {
        override fun run() {
            evaluateOverlayState()
            mainHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        controllerManager = MediaControllerManager()
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        mainHandler = Handler(Looper.getMainLooper())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        overlayView = OverlayView(this, controllerManager)

        try {
            mediaSessionManager.addOnActiveSessionsChangedListener(this, null, mainHandler)
            onActiveSessionsChanged(mediaSessionManager.getActiveSessions(null))
        } catch (e: SecurityException) {
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        mainHandler.post(pollingRunnable)
    }

    override fun onDestroy() {
        try {
            mediaSessionManager.removeOnActiveSessionsChangedListener(this)
        } catch (e: Exception) {
        }
        controllerManager.setActiveController(null)
        mainHandler.removeCallbacks(pollingRunnable)
        hideOverlay()
        super.onDestroy()
    }

    override fun onActiveSessionsChanged(controllers: List<MediaController>?) {
        val target = controllers?.firstOrNull { isSupported(it.packageName) }
        controllerManager.setActiveController(target)
    }

    private fun isSupported(packageName: String?): Boolean {
        return PACKAGE_YOUTUBE == packageName || PACKAGE_YOUTUBE_MUSIC == packageName
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        evaluateOverlayState()
    }

    override fun onInterrupt() {
        hideOverlay()
    }

    private fun evaluateOverlayState() {
        if (!isScreenOn()) {
            hideOverlay()
            return
        }

        val info = try {
            heuristics.evaluate(windows, resources.displayMetrics)
        } catch (e: Exception) {
            WindowInfo.Empty
        }

        if (shouldShowOverlay(info)) {
            showOverlay()
        } else {
            hideOverlay()
        }
    }

    private fun shouldShowOverlay(info: WindowInfo): Boolean {
        val focusedPackage = info.focusedPackage

        if (focusedPackage != null && isSupported(focusedPackage)) {
            val appInfo = info.appWindows[focusedPackage] ?: return false

            if (appInfo.playMode == PlayMode.VIDEO || appInfo.playMode == PlayMode.SHORTS) {
                return true
            }

            if (appInfo.playMode == PlayMode.AUDIO) {
                return false
            }

            if (appInfo.hasVisibleVideoSurface) {
                return true
            }

            if (appInfo.state == WindowState.PICTURE_IN_PICTURE) {
                 return true
            }
        }

        info.appWindows.values.forEach { appInfo ->
            if (isSupported(appInfo.packageName) && appInfo.isVisible) {
                if (appInfo.state == WindowState.PICTURE_IN_PICTURE) {
                    return true
                }
                if (appInfo.playMode == PlayMode.VIDEO || appInfo.playMode == PlayMode.SHORTS) {
                    return true
                }
                if (appInfo.hasVisibleVideoSurface) {
                    return true
                }
            }
        }

        return false
    }

    private fun isScreenOn(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isInteractive == true
    }

    private fun showOverlay() {
        val view = overlayView?.view ?: return
        overlayView?.updateFromController()

        if (view.parent == null) {
            try {
                windowManager?.addView(view, overlayView?.layoutParams)
            } catch (e: Exception) {
            }
        }
    }

    private fun hideOverlay() {
        val view = overlayView?.view ?: return
        if (view.parent != null) {
            try {
                windowManager?.removeView(view)
                overlayView?.stopHeartbeat()
            } catch (e: Exception) {
            }
        }
    }

    companion object {
        private const val PACKAGE_YOUTUBE = "com.google.android.youtube"
        private const val PACKAGE_YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
        private const val POLL_INTERVAL_MS = 500L
    }
}
