package com.polaralias.audiofocus.overlay

import android.media.session.MediaController
import android.media.session.PlaybackState

interface TransportCommander {
    fun togglePlayPause()
    fun seekBy(deltaMs: Long)
    fun seekTo(positionMs: Long)
}

class MediaTransportCommander(
    private val controller: MediaController,
    private val playbackState: PlaybackState?
) : TransportCommander {
    private val controls = controller.transportControls

    override fun togglePlayPause() {
        if (playbackState?.state == PlaybackState.STATE_PLAYING) {
            controls.pause()
        } else {
            controls.play()
        }
    }

    override fun seekBy(deltaMs: Long) {
        val state = playbackState ?: return
        val supportsSeekTo = state.actions and PlaybackState.ACTION_SEEK_TO != 0L
        val target = (state.position + deltaMs).coerceAtLeast(0L)
        if (supportsSeekTo) {
            controls.seekTo(target)
        } else if (deltaMs < 0 && state.actions and PlaybackState.ACTION_REWIND != 0L) {
            controls.rewind()
        } else if (deltaMs > 0 && state.actions and PlaybackState.ACTION_FAST_FORWARD != 0L) {
            controls.fastForward()
        }
    }

    override fun seekTo(positionMs: Long) {
        val state = playbackState ?: return
        if (state.actions and PlaybackState.ACTION_SEEK_TO != 0L) {
            controls.seekTo(positionMs)
        }
    }
}
