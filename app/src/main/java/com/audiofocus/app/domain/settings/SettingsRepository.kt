package com.audiofocus.app.domain.settings

import com.audiofocus.app.core.model.AppSettings
import com.audiofocus.app.core.model.OverlaySettings
import com.audiofocus.app.core.model.ThemeConfig
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    // Deprecated or derived from AppSettings, kept for compatibility if needed
    val overlaySettings: Flow<OverlaySettings>

    val appSettings: Flow<AppSettings>

    suspend fun setMonitoringEnabled(enabled: Boolean)

    // Legacy support
    suspend fun setBlurEnabled(enabled: Boolean)
    suspend fun setBackgroundColor(color: Long)

    suspend fun setYoutubeTheme(theme: ThemeConfig)
    suspend fun setYoutubeMusicTheme(theme: ThemeConfig)
}
