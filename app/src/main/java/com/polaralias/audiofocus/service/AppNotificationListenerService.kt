package com.polaralias.audiofocus.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.polaralias.audiofocus.service.monitor.NotificationMonitor
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
