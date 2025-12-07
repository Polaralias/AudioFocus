package com.audiofocus.app.service.monitor

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.audiofocus.app.core.model.TargetApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationMonitor @Inject constructor() {
    private val _activeMediaNotifications = MutableStateFlow<Set<TargetApp>>(emptySet())
    val activeMediaNotifications = _activeMediaNotifications.asStateFlow()

    fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val targetApp = TargetApp.entries.find { it.packageName == packageName } ?: return

        if (isMediaNotification(sbn)) {
            _activeMediaNotifications.update { it + targetApp }
        }
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        val targetApp = TargetApp.entries.find { it.packageName == packageName } ?: return

        if (isMediaNotification(sbn)) {
            _activeMediaNotifications.update { it - targetApp }
        }
    }

    private fun isMediaNotification(sbn: StatusBarNotification): Boolean {
        val extras = sbn.notification.extras
        val template = extras.getString(Notification.EXTRA_TEMPLATE)
        return template == "android.app.Notification\$MediaStyle" ||
               template == "android.media.style.MediaStyle"
    }
}
