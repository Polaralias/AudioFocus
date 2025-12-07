package com.audiofocus.app.domain.settings

import com.audiofocus.app.core.model.OverlaySettings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val overlaySettings: Flow<OverlaySettings>

    suspend fun setBlurEnabled(enabled: Boolean)
    suspend fun setBackgroundColor(color: Long)
}
