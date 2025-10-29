package com.polaralias.audiofocus.state

enum class SupportedApp {
    YOUTUBE,
    YOUTUBE_MUSIC,
}

enum class WindowState {
    FULLSCREEN,
    MINIMIZED,
    PICTURE_IN_PICTURE,
    UNKNOWN,
}

data class WindowSnapshot(
    val app: SupportedApp,
    val state: WindowState,
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

fun CharSequence?.toSupportedApp(): SupportedApp? {
    return when (this?.toString()) {
        "com.google.android.youtube" -> SupportedApp.YOUTUBE
        "com.google.android.apps.youtube.music" -> SupportedApp.YOUTUBE_MUSIC
        else -> null
    }
}
