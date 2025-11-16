package com.polaralias.audiofocus.service

import android.media.session.PlaybackState

internal data class SeekCapabilitySnapshot(
    val supportsSeekTo: Boolean,
    val supportsRelativeSeek: Boolean
) {
    val canSeekBy: Boolean get() = supportsSeekTo || supportsRelativeSeek
    val canSeekRelativeOnly: Boolean get() = !supportsSeekTo && supportsRelativeSeek
}

internal fun resolveSeekCapabilities(actions: Long): SeekCapabilitySnapshot {
    val supportsSeekTo = actions.hasAny(
        PlaybackState.ACTION_SEEK_TO,
        ACTION_SEEK_FORWARD,
        ACTION_SEEK_BACKWARD
    )
    val supportsRelativeSeek = actions.hasAny(
        PlaybackState.ACTION_FAST_FORWARD,
        PlaybackState.ACTION_REWIND,
        ACTION_SEEK_FORWARD,
        ACTION_SEEK_BACKWARD
    )
    return SeekCapabilitySnapshot(supportsSeekTo, supportsRelativeSeek)
}

private fun Long.hasAny(vararg flags: Long): Boolean = flags.any { flag -> this and flag != 0L }

