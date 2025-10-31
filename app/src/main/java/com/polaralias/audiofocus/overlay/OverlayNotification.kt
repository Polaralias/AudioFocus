package com.polaralias.audiofocus.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.polaralias.audiofocus.R
import com.polaralias.audiofocus.settings.SettingsActivity

object OverlayNotification {
    const val CHANNEL_ID = "overlay_channel"
    const val NOTIFICATION_ID = 42

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.overlay_service_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.overlay_service_description)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun build(
        context: Context,
        isPlaying: Boolean,
        toggleIntent: PendingIntent,
        contentIntent: PendingIntent
    ): Notification {
        ensureChannel(context)
        val actionText = if (isPlaying) {
            context.getString(R.string.overlay_notification_pause)
        } else {
            context.getString(R.string.overlay_notification_play)
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(context.getString(R.string.overlay_service_active))
            .setContentText(context.getString(R.string.overlay_service_description))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, actionText, toggleIntent)
            .build()
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
