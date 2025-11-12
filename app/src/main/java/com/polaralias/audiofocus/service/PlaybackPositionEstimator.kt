package com.polaralias.audiofocus.service

import android.media.session.PlaybackState
import android.os.SystemClock

internal class PlaybackPositionEstimator(
    private val elapsedRealtime: () -> Long = { SystemClock.elapsedRealtime() }
) {
    private var anchorRealtime: Long = NO_TIME
    private var anchorPosition: Long = 0L
    private var anchorSpeed: Float = 1f
    private var lastRawPosition: Long = 0L

    fun compute(playback: PlaybackState?, isPlaying: Boolean): Long {
        if (playback == null) {
            reset()
            return 0L
        }

        val rawPosition = playback.position
        val rawUpdateTime = playback.lastPositionUpdateTime
        val speed = playback.playbackSpeed.takeIf { it > 0f }
            ?: anchorSpeed.takeIf { it > 0f }
            ?: 1f
        val now = elapsedRealtime()
        val rawUpdateDelta = now - rawUpdateTime
        val hasSaneUpdateTime = rawUpdateTime > 0L && rawUpdateDelta >= 0 && rawUpdateDelta <= MAX_SANE_UPDATE_DELTA

        val position = if (isPlaying) {
            val basePosition: Long
            val baseTime: Long
            if (hasSaneUpdateTime) {
                basePosition = rawPosition
                baseTime = rawUpdateTime
            } else if (anchorRealtime != NO_TIME && rawPosition == lastRawPosition) {
                basePosition = anchorPosition
                baseTime = anchorRealtime
            } else {
                basePosition = rawPosition
                baseTime = now
            }
            val delta = now - baseTime
            val advanced = if (delta > 0L) basePosition + (delta * speed).toLong() else basePosition
            anchorRealtime = now
            anchorPosition = advanced
            anchorSpeed = speed
            advanced
        } else {
            anchorRealtime = NO_TIME
            anchorPosition = rawPosition
            anchorSpeed = speed
            rawPosition
        }

        lastRawPosition = rawPosition
        return position.coerceAtLeast(0L)
    }

    fun reset() {
        anchorRealtime = NO_TIME
        anchorPosition = 0L
        anchorSpeed = 1f
        lastRawPosition = 0L
    }

    private companion object {
        private const val NO_TIME = Long.MIN_VALUE
        private const val MAX_SANE_UPDATE_DELTA = 5 * 60 * 1000L
    }
}
