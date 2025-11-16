package com.polaralias.audiofocus.model

sealed interface OverlayState {
    data object None : OverlayState
    data object Fullscreen : OverlayState
}
