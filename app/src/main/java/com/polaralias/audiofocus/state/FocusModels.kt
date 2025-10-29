package com.polaralias.audiofocus.state

enum class SupportedApp {
    YOUTUBE,
    YOUTUBE_MUSIC,
}

enum class WindowMode {
    FULLSCREEN,
    MINIMIZED,
    PICTURE_IN_PICTURE,
    UNKNOWN,
}

data class WindowSnapshot(
    val app: SupportedApp,
    val mode: WindowMode,
)

enum class PlaybackActivity {
    STOPPED,
    PAUSED,
    PLAYING,
}

enum class PlaybackContentType {
    UNKNOWN,
    AUDIO_ONLY,
    VIDEO,
}

data class PlaybackSnapshot(
    val app: SupportedApp,
    val activity: PlaybackActivity,
    val contentType: PlaybackContentType,
)
