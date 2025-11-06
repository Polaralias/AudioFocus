package com.polaralias.audiofocus.ui.controls

data class ControlsUiState(
    val isVisible: Boolean = false,
    val isPlaying: Boolean = false,
    val position: Long = 0L,
    val duration: Long = 0L,
    val canSeek: Boolean = false,
    val canSeekBy: Boolean = false,
    val isPartialOverlay: Boolean = false
) {
    val safeDuration: Long get() = duration.coerceAtLeast(0L)
    val clampedPosition: Long get() = position.coerceIn(0L, safeDuration.takeIf { it > 0L } ?: Long.MAX_VALUE)
}
