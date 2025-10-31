package com.polaralias.audiofocus.model

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState

sealed interface MediaState {
    data object Idle : MediaState

    data class Paused(
        val controller: MediaController,
        val playbackState: PlaybackState?,
        val metadata: MediaMetadata?
    ) : MediaState

    data class Playing(
        val controller: MediaController,
        val playbackState: PlaybackState,
        val metadata: MediaMetadata?
    ) : MediaState
}

fun MediaState.controllerPackage(): String? = when (this) {
    is MediaState.Playing -> controller.packageName
    is MediaState.Paused -> controller.packageName
    MediaState.Idle -> null
}
