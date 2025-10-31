package com.polaralias.audiofocus.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class MediaNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        _connected.value = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _connected.value = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = Unit

    override fun onNotificationRemoved(sbn: StatusBarNotification) = Unit

    companion object {
        private val _connected = MutableStateFlow(false)
        val connected: StateFlow<Boolean> = _connected
    }
}
