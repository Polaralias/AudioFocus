package com.polaralias.audiofocus.core.model

enum class TargetApp(val packageName: String) {
    YOUTUBE("com.google.android.youtube"),
    YOUTUBE_MUSIC("com.google.android.apps.youtube.music")
}

enum class WindowState {
    NOT_VISIBLE,
    FOREGROUND_FULLSCREEN,
    FOREGROUND_MINIMISED,
    PICTURE_IN_PICTURE,
    BACKGROUND
}

enum class PlaybackType {
    NONE,
    AUDIO_ONLY,
    VISIBLE_VIDEO
}

enum class PlaybackStateSimplified {
    STOPPED,
    PAUSED,
    PLAYING
}

enum class OverlayMode {
    NONE,
    FULL_SCREEN
}

data class AppVisualState(
    val app: TargetApp,
    val windowState: WindowState,
    val playbackState: PlaybackStateSimplified,
    val playbackType: PlaybackType
)

data class OverlayDecision(
    val shouldOverlay: Boolean,
    val overlayMode: OverlayMode,
    val targetApp: TargetApp?
)

sealed class MediaAction {
    data object Play : MediaAction()
    data object Pause : MediaAction()
    data object SkipForward : MediaAction()
    data object SkipBackward : MediaAction()
    data class Seek(val position: Long) : MediaAction()
}

data class OverlaySettings(
    val isBlurEnabled: Boolean = true,
    val backgroundColor: Long = 0xFF000000
)

enum class ThemeType {
    SOLID,
    IMAGE
}

data class ThemeConfig(
    val type: ThemeType = ThemeType.SOLID,
    val color: Int = 0xFF000000.toInt(),
    val imageUri: String? = null,
    val blurLevel: Int = 1 // 0-3
)

data class AppSettings(
    val isMonitoringEnabled: Boolean = true,
    val youtubeTheme: ThemeConfig = ThemeConfig(),
    val youtubeMusicTheme: ThemeConfig = ThemeConfig()
)
