package com.polaralias.audiofocus.window

data class WindowInfo(
    val focusedPackage: String? = null,
    val appWindows: Map<String, AppWindowInfo> = emptyMap(),
) {
    fun infoFor(packageName: String?): AppWindowInfo? {
        if (packageName.isNullOrEmpty()) return null
        return appWindows[packageName]
    }

    companion object {
        val Empty = WindowInfo()
    }
}

data class AppWindowInfo(
    val packageName: String,
    val state: WindowState,
    val videoSurfaceFraction: Float,
    val playMode: PlayMode,
    val selectedMode: PlayMode? = null,
) {
    val isVisible: Boolean
        get() = state != WindowState.BACKGROUND

    val isFullscreen: Boolean
        get() = state == WindowState.FULLSCREEN

    val hasVisibleVideoSurface: Boolean
        get() = videoSurfaceFraction > 0f
}

enum class WindowState {
    FULLSCREEN,
    MINIMIZED_IN_APP,
    PICTURE_IN_PICTURE,
    BACKGROUND,
}

enum class PlayMode {
    AUDIO,
    VIDEO,
    SHORTS,
    UNKNOWN,
}
