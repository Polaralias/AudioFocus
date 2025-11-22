package com.polaralias.audiofocus.settings

import com.polaralias.audiofocus.data.OverlayPreferences
import com.polaralias.audiofocus.service.OverlayServiceState
import com.polaralias.audiofocus.service.OverlayServiceStatus

data class SettingsUiState(
    val preferences: OverlayPreferences = OverlayPreferences(),
    val hasOverlayPermission: Boolean = false,
    val hasNotificationAccess: Boolean = false,
    val canPostNotifications: Boolean = false,
    val hasAccessibilityAccess: Boolean = false,
    val permissionDiagnostic: String = "",
    val notificationListenerConnected: Boolean = false,
    val serviceDiagnostic: String = "",
    val overlayServiceStatus: OverlayServiceStatus = OverlayServiceStatus(OverlayServiceState.STOPPED),
    val isLoading: Boolean = true
)
