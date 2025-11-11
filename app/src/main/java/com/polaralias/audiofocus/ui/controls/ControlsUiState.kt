package com.polaralias.audiofocus.ui.controls

import android.net.Uri
import com.polaralias.audiofocus.data.OverlayDefaults
import com.polaralias.audiofocus.data.OverlayFillMode

data class ControlsUiState(
    val isVisible: Boolean = false,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val canSeek: Boolean = false,
    val canSeekBy: Boolean = false,
    val isPartialOverlay: Boolean = false,
    val overlayFillMode: OverlayFillMode = OverlayFillMode.SOLID_COLOR,
    val overlayColor: Int = OverlayDefaults.defaultColor,
    val overlayImageUri: Uri? = null,
    val appearanceVersion: Int = 0
) {
    val safeDuration: Long get() = duration.coerceAtLeast(0L)
    val clampedPosition: Long get() = position.coerceIn(0L, safeDuration.takeIf { it > 0L } ?: Long.MAX_VALUE)
}
