package com.polaralias.audiofocus.model

sealed interface OverlayState {
    data object None : OverlayState
    data class Fullscreen(val maskAlpha: Float = 1f) : OverlayState
    data class Partial(val heightRatio: Float = 5f / 6f, val maskAlpha: Float = 1f) : OverlayState
}
