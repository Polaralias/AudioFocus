package com.audiofocus.app.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.audiofocus.app.service.monitor.NotificationMonitor
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AppNotificationListenerService : NotificationListenerService() {

    @Inject
    lateinit var notificationMonitor: NotificationMonitor

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let { notificationMonitor.onNotificationPosted(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.let { notificationMonitor.onNotificationRemoved(it) }
    }
}
