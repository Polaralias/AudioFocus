package com.audiofocus.app.service

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
import com.audiofocus.app.core.Constants
import com.audiofocus.app.service.monitor.AccessibilityMonitor
import com.audiofocus.app.service.monitor.ForegroundAppDetector
import com.audiofocus.app.service.monitor.MediaSessionMonitor
import com.audiofocus.app.service.monitor.NotificationMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startMonitoring()
    }

    private fun startMonitoring() {
        // Initial check for foreground app
        foregroundAppDetector.checkUsageStats()

        serviceScope.launch {
            mediaSessionMonitor.observe().collect { state ->
                Log.d("AudioFocusService", "Media Session State: $state")
            }
        }

        serviceScope.launch {
            accessibilityMonitor.states.collect { state ->
                Log.d("AudioFocusService", "Accessibility State: $state")
            }
        }

        serviceScope.launch {
            notificationMonitor.activeMediaNotifications.collect { state ->
                Log.d("AudioFocusService", "Notification State: $state")
            }
        }

        serviceScope.launch {
            foregroundAppDetector.foregroundPackage.collect { pkg ->
                Log.d("AudioFocusService", "Foreground App: $pkg")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_MONITORING) {
            stopMonitoring()
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

    private fun stopMonitoring() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    companion object {
        const val ACTION_STOP_MONITORING = "com.audiofocus.app.action.STOP_MONITORING"
    }
}
