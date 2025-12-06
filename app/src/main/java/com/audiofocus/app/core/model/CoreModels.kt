package com.audiofocus.app.core.model

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
