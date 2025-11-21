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

    // Use WindowHeuristics for reliable detection
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
            // Handle permission issues or service not ready
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
            // Ignore
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

        // Use WindowHeuristics to evaluate the state
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
        // Check if we have a focused supported package or any visible supported window
        val focusedPackage = info.focusedPackage

        // If we have a focused package that is supported
        if (focusedPackage != null && isSupported(focusedPackage)) {
            val appInfo = info.appWindows[focusedPackage] ?: return false

            // Logic: Video on screen -> Overlay.
            // Video means: Shorts, Fullscreen, PiP, Standard Player, Video Mode in YTM.

            // If PlayMode is VIDEO or SHORTS, show it.
            if (appInfo.playMode == PlayMode.VIDEO || appInfo.playMode == PlayMode.SHORTS) {
                return true
            }

            // Special case for YTM: If playMode is AUDIO, definitely hide.
            if (appInfo.playMode == PlayMode.AUDIO) {
                return false
            }

            // If we detected a video surface fraction > 0, it's likely a video.
            if (appInfo.hasVisibleVideoSurface) {
                return true
            }

            // If state is PiP, we usually want overlay if it's video
            if (appInfo.state == WindowState.PICTURE_IN_PICTURE) {
                 // PiP implies video usually, but let's stick to playMode/surface check if possible.
                 // However, WindowHeuristics sets PlayMode.VIDEO for PiP in inferPackageForPiP.
                 return true
            }
        }

        // Fallback: Check any visible window (e.g. PiP might not be focused)
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
                // Handle errors (e.g. permission denied)
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
                // Handle errors
            }
        }
    }

    companion object {
        private const val PACKAGE_YOUTUBE = "com.google.android.youtube"
        private const val PACKAGE_YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
        private const val POLL_INTERVAL_MS = 500L
    }
}
