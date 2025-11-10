package com.polaralias.audiofocus.window

/**
 * Aggregated visibility information for the supported media apps.
 *
 * The service tracks at most one window per package (the most visible one) and exposes the
 * inferred [AppWindowInfo] through [appWindows]. A missing entry indicates that the app does not
 * currently have a visible window (i.e. background playback only).
 */
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

/**
 * Visibility snapshot for a single package.
 */
data class AppWindowInfo(
    val packageName: String,
    val state: WindowState,
    val hasVisibleVideoSurface: Boolean,
) {
    val isVisible: Boolean
        get() = state != WindowState.BACKGROUND

    val isFullscreen: Boolean
        get() = state == WindowState.FULLSCREEN
}

enum class WindowState {
    FULLSCREEN,
    MINIMIZED_IN_APP,
    PICTURE_IN_PICTURE,
    BACKGROUND,
}
