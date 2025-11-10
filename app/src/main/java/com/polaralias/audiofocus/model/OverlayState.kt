package com.polaralias.audiofocus.model

sealed interface OverlayState {
    data object None : OverlayState
    data object Fullscreen : OverlayState
    data class Partial(val heightRatio: Float = 5f / 6f) : OverlayState
}
