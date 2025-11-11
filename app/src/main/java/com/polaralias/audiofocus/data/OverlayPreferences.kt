package com.polaralias.audiofocus.data

import android.net.Uri
import androidx.annotation.ColorInt
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class OverlayFillMode {
    SOLID_COLOR,
    IMAGE
}

object OverlayDefaults {
    @ColorInt
    var defaultColor: Int = Color(0xFF0B1118).toArgb()
}

data class OverlayPreferences(
    val enableYouTube: Boolean = true,
    val enableYouTubeMusic: Boolean = true,
    val startOnBoot: Boolean = false,
    val fillMode: OverlayFillMode = OverlayFillMode.SOLID_COLOR,
    @ColorInt val overlayColor: Int = OverlayDefaults.defaultColor,
    val imageUri: Uri? = null,
)
