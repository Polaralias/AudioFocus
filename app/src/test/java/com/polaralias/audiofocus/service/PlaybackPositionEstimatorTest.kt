package com.polaralias.audiofocus.service

import android.media.session.PlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackPositionEstimatorTest {
    private class FakeClock(var now: Long = 0L) {
        fun advance(byMillis: Long) {
            now += byMillis
        }
    }

    @Test
    fun advancesWhenUpdateTimeIsZero() {
        val clock = FakeClock(now = 1_000L)
        val estimator = PlaybackPositionEstimator { clock.now }
        val playback = playbackState(
            state = PlaybackState.STATE_BUFFERING,
            position = 5_000L,
            speed = 1f,
            updateTime = 0L
        )

        val first = estimator.compute(playback, isPlaying = true)
        assertEquals(5_000L, first)

        clock.advance(500L)
        val second = estimator.compute(playback, isPlaying = true)
        assertEquals(5_500L, second)
    }

    @Test
    fun resetsAnchorWhenPositionJumps() {
        val clock = FakeClock(now = 0L)
        val estimator = PlaybackPositionEstimator { clock.now }
        val initial = playbackState(
            state = PlaybackState.STATE_BUFFERING,
            position = 10_000L,
            speed = 1f,
            updateTime = 0L
        )

        estimator.compute(initial, isPlaying = true)
        clock.advance(1_000L)
        estimator.compute(initial, isPlaying = true)

        val nextTrack = playbackState(
            state = PlaybackState.STATE_BUFFERING,
            position = 0L,
            speed = 1f,
            updateTime = 0L
        )

        clock.advance(200L)
        val reset = estimator.compute(nextTrack, isPlaying = true)
        assertEquals(0L, reset)
    }

    @Test
    fun fallsBackToPreviousSpeedWhenReportedSpeedIsZero() {
        val clock = FakeClock(now = 0L)
        val estimator = PlaybackPositionEstimator { clock.now }
        val playing = playbackState(
            state = PlaybackState.STATE_PLAYING,
            position = 5_000L,
            speed = 1f,
            updateTime = 0L
        )

        estimator.compute(playing, isPlaying = true)
        clock.advance(1_000L)

        val buffering = playbackState(
            state = PlaybackState.STATE_BUFFERING,
            position = 5_000L,
            speed = 0f,
            updateTime = 0L
        )

        val advanced = estimator.compute(buffering, isPlaying = true)
        assertEquals(6_000L, advanced)
    }

    private fun playbackState(
        state: Int,
        position: Long,
        speed: Float,
        updateTime: Long
    ): PlaybackState {
        return PlaybackState.Builder()
            .setState(state, position, speed, updateTime)
            .build()
    }
}
