package com.polaralias.audiofocus.policy

import android.media.MediaMetadata
import android.media.session.PlaybackState
import com.polaralias.audiofocus.data.OverlayPreferences
import com.polaralias.audiofocus.window.WindowInfo

data class PolicyInput(
    val packageName: String?,
    val playbackState: PlaybackState?,
    val metadata: MediaMetadata?,
    val windowInfo: WindowInfo,
    val preferences: OverlayPreferences
)
