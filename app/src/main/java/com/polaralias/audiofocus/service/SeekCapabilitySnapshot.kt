package com.polaralias.audiofocus.service

import android.media.session.PlaybackState

internal data class SeekCapabilitySnapshot(
    val advertisedSeekTo: Boolean,
    val assumedSeekTo: Boolean,
    val supportsRelativeSeek: Boolean
) {
    val supportsSeekTo: Boolean get() = advertisedSeekTo || assumedSeekTo
    val canSeekBy: Boolean get() = supportsSeekTo || supportsRelativeSeek
    val canSeekRelativeOnly: Boolean get() = !advertisedSeekTo && supportsRelativeSeek
}

internal fun resolveSeekCapabilities(
    actions: Long,
    hasCommander: Boolean,
    hasPlayback: Boolean
): SeekCapabilitySnapshot {
    val advertisedSeekTo = actions and PlaybackState.ACTION_SEEK_TO != 0L
    val supportsRelativeSeek = actions.hasAny(
        PlaybackState.ACTION_FAST_FORWARD,
        PlaybackState.ACTION_REWIND,
        ACTION_SEEK_FORWARD,
        ACTION_SEEK_BACKWARD
    )
    val assumedSeekTo = hasCommander && hasPlayback
    return SeekCapabilitySnapshot(advertisedSeekTo, assumedSeekTo, supportsRelativeSeek)
}

private fun Long.hasAny(vararg flags: Long): Boolean = flags.any { flag -> this and flag != 0L }

