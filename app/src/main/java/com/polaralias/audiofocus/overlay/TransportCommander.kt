package com.polaralias.audiofocus.overlay

import android.media.session.MediaController
import android.media.session.PlaybackState
import android.util.Log
import kotlin.math.abs

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
    private val actions: Long = playbackState?.actions ?: 0L

    override fun togglePlayPause() {
        if (playbackState?.state == PlaybackState.STATE_PLAYING) {
            controls.pause()
        } else {
            controls.play()
        }
    }

    override fun seekBy(deltaMs: Long) {
        val state = playbackState ?: return
        if (deltaMs == 0L) return
        val target = (state.position + deltaMs).coerceAtLeast(0L)
        if (trySeekTo(target)) {
            return
        }
        performRelativeSeek(deltaMs)
    }

    override fun seekTo(positionMs: Long) {
        val state = playbackState ?: return
        val delta = positionMs - state.position
        if (trySeekTo(positionMs)) {
            return
        }
        performRelativeSeek(delta)
    }

    private fun trySeekTo(positionMs: Long): Boolean {
        return try {
            controls.seekTo(positionMs)
            true
        } catch (error: SecurityException) {
            Log.w(TAG, "Transport denied seekTo", error)
            false
        } catch (error: IllegalArgumentException) {
            Log.w(TAG, "Transport rejected seekTo", error)
            false
        } catch (error: IllegalStateException) {
            Log.w(TAG, "Transport failed seekTo", error)
            false
        } catch (error: RuntimeException) {
            Log.w(TAG, "Transport threw during seekTo", error)
            false
        }
    }

    private fun performRelativeSeek(deltaMs: Long) {
        if (deltaMs == 0L) return
        val supportsForward = actions and PlaybackState.ACTION_FAST_FORWARD != 0L
        val supportsRewind = actions and PlaybackState.ACTION_REWIND != 0L
        val step = RELATIVE_SEEK_STEP_MS
        val repetitions = ((abs(deltaMs) + step - 1) / step).toInt()
        if (repetitions <= 0) return
        if (deltaMs > 0 && supportsForward) {
            repeat(repetitions) { controls.fastForward() }
        } else if (deltaMs < 0 && supportsRewind) {
            repeat(repetitions) { controls.rewind() }
        }
    }

    companion object {
        private const val TAG = "MediaTransportCmd"
        private const val RELATIVE_SEEK_STEP_MS = 10_000L
    }
}
