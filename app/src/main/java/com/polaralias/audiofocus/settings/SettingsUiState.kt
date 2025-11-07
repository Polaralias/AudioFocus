package com.polaralias.audiofocus.settings

import com.polaralias.audiofocus.data.OverlayPreferences

data class SettingsUiState(
    val preferences: OverlayPreferences = OverlayPreferences(),
    val hasOverlayPermission: Boolean = false,
    val hasNotificationAccess: Boolean = false,
    val hasAccessibilityAccess: Boolean = false,
    val permissionDiagnostic: String = "",
    val isLoading: Boolean = true // Indicates whether initial data is still being loaded
)
