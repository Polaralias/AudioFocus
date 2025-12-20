package com.polaralias.audiofocus.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.polaralias.audiofocus.core.Constants
import com.polaralias.audiofocus.core.logic.PlaybackStateEngine
import com.polaralias.audiofocus.service.monitor.AccessibilityMonitor
import com.polaralias.audiofocus.service.monitor.ForegroundAppDetector
import com.polaralias.audiofocus.service.monitor.MediaSessionMonitor
import com.polaralias.audiofocus.service.monitor.NotificationMonitor
import com.polaralias.audiofocus.domain.settings.SettingsRepository
import com.polaralias.audiofocus.service.overlay.OverlayManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AudioFocusService : Service() {

    @Inject
    lateinit var mediaSessionMonitor: MediaSessionMonitor

    @Inject
    lateinit var accessibilityMonitor: AccessibilityMonitor

    @Inject
    lateinit var notificationMonitor: NotificationMonitor

    @Inject
    lateinit var foregroundAppDetector: ForegroundAppDetector

    @Inject
    lateinit var playbackStateEngine: PlaybackStateEngine

    @Inject
    lateinit var overlayManager: OverlayManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var monitoringJob: Job? = null
    private var isMonitoring = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Observe settings to enable/disable monitoring
        serviceScope.launch {
            settingsRepository.appSettings.collect { settings ->
                if (settings.isMonitoringEnabled) {
                    if (!isMonitoring) {
                        startMonitoring()
                    }
                } else {
                    if (isMonitoring) {
                        stopMonitoring(stopService = false)
                    }
                }
            }
        }
    }

    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        monitoringJob?.cancel()
        monitoringJob = SupervisorJob(serviceScope.coroutineContext[Job])

        mediaSessionMonitor.start()

        // Initial check for foreground app
        foregroundAppDetector.checkUsageStats()

        val scope = CoroutineScope(serviceScope.coroutineContext + monitoringJob!!)

        scope.launch {
            mediaSessionMonitor.observe().collect { state ->
                Log.d("AudioFocusService", "Media Session State: $state")
            }
        }

        scope.launch {
            accessibilityMonitor.states.collect { state ->
                Log.d("AudioFocusService", "Accessibility State: $state")
            }
        }

        scope.launch {
            notificationMonitor.activeMediaNotifications.collect { state ->
                Log.d("AudioFocusService", "Notification State: $state")
            }
        }

        scope.launch {
            foregroundAppDetector.foregroundPackage.collect { pkg ->
                Log.d("AudioFocusService", "Foreground App: $pkg")
            }
        }

        scope.launch {
            playbackStateEngine.overlayDecision.collect { decision ->
                Log.d("AudioFocusService", "Overlay Decision: $decision")
                if (decision.shouldOverlay && decision.targetApp != null) {
                    overlayManager.show(decision.targetApp)
                } else {
                    overlayManager.hide()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mediaSessionMonitor.stop()
        overlayManager.hide()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_MONITORING) {
            stopMonitoring(stopService = true)
            return START_NOT_STICKY
        }

        startForeground(Constants.MONITOR_NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.MONITOR_NOTIFICATION_CHANNEL_ID,
                "AudioFocus Monitor",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoring active media apps"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, Constants.MONITOR_NOTIFICATION_CHANNEL_ID)
            .setContentTitle("AudioFocus Active")
            .setContentText("Monitoring media playback...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopMonitoring(stopService: Boolean) {
        if (!isMonitoring && !stopService) return

        isMonitoring = false
        monitoringJob?.cancel()
        monitoringJob = null

        mediaSessionMonitor.stop()
        overlayManager.hide()

        if (stopService) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            stopSelf()
        }
    }

    companion object {
        const val ACTION_STOP_MONITORING = "com.polaralias.audiofocus.action.STOP_MONITORING"
    }
}
