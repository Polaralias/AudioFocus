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
    private val playbackState: PlaybackState?,
    controls: MediaController.TransportControls? = null
) : TransportCommander {
    private val controls = controls ?: controller.transportControls
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
        } catch (error: UnsupportedOperationException) {
            Log.w(TAG, "Transport does not implement seekTo", error)
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

        // Try using SEEK_FORWARD/BACKWARD actions first (Android 12+)
        val supportsSeekForward = actions and ACTION_SEEK_FORWARD != 0L
        val supportsSeekBackward = actions and ACTION_SEEK_BACKWARD != 0L

        if (deltaMs > 0 && supportsSeekForward) {
             // Attempt to use the standard action via sendCustomAction if standard method isn't available
             // or simply trust that we can trigger it. Since TransportControls.seekForward is API 31,
             // and we want broad compatibility, we can try using reflection or custom action.
             // However, many apps implement ACTION_SEEK_FORWARD but might expect a custom action or key event.
             // But standard implementation is best if possible.
             // Let's try to invoke it safely.
             if (invokeSeekForward()) return
        } else if (deltaMs < 0 && supportsSeekBackward) {
             if (invokeSeekBackward()) return
        }

        // Fallback to fastForward/rewind
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

    private fun invokeSeekForward(): Boolean {
        // Try calling seekForward via reflection to avoid API level issues at compile time
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            try {
                 val method = controls.javaClass.getMethod("seekForward")
                 method.invoke(controls)
                 return true
            } catch (e: Exception) {
                 Log.w(TAG, "Failed to call seekForward via reflection", e)
            }
        }
        return false
    }

    private fun invokeSeekBackward(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
             try {
                 val method = controls.javaClass.getMethod("seekBackward")
                 method.invoke(controls)
                 return true
             } catch (e: Exception) {
                 Log.w(TAG, "Failed to call seekBackward via reflection", e)
             }
        }
        return false
    }

    companion object {
        private const val TAG = "MediaTransportCmd"
        private const val RELATIVE_SEEK_STEP_MS = 10_000L
        private const val ACTION_SEEK_FORWARD = 2097152L // 0x200000L
        private const val ACTION_SEEK_BACKWARD = 4194304L // 0x400000L
    }
}
