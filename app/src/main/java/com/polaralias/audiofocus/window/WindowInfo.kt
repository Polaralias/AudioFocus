package com.polaralias.audiofocus.window

data class WindowInfo(
    val isFullscreen: Boolean,
    val hasLikelyVideoSurface: Boolean
) {
    companion object {
        val Empty = WindowInfo(isFullscreen = false, hasLikelyVideoSurface = false)
    }
}
