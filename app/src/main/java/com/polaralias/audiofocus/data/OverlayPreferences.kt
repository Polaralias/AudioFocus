package com.polaralias.audiofocus.data

import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class OverlayFillMode {
    SOLID_COLOR,
    IMAGE
}

object OverlayPreferencesDefaults {
    val DEFAULT_OVERLAY_COLOR: Int = Color(0f, 0f, 0f, 0.75f).toArgb()
}

data class OverlayPreferences(
    val enableYouTube: Boolean = true,
    val enableYouTubeMusic: Boolean = true,
    val startOnBoot: Boolean = false,
    val dimAmount: Float = 0.9f,
    val overlayFillMode: OverlayFillMode = OverlayFillMode.SOLID_COLOR,
    val overlayColor: Int = OverlayPreferencesDefaults.DEFAULT_OVERLAY_COLOR,
    val overlayImageUri: Uri? = null
)
