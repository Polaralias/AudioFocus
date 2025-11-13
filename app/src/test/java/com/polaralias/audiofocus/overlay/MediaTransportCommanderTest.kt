package com.polaralias.audiofocus.overlay

import android.media.session.MediaController
import android.media.session.PlaybackState
import org.junit.Test
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MediaTransportCommanderTest {
    @Test
    fun seekToFallsBackToRelativeWhenSeekToUnsupported() {
        val controls = mock<MediaController.TransportControls>()
        val controller = mock<MediaController>()
        whenever(controller.transportControls).thenReturn(controls)
        val playbackState = PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND)
            .setState(PlaybackState.STATE_PLAYING, 30_000L, 1f)
            .build()

        doThrow(IllegalStateException("seekTo not supported"))
            .whenever(controls)
            .seekTo(any())

        val commander = MediaTransportCommander(controller, playbackState)

        commander.seekTo(50_000L)

        verify(controls).seekTo(50_000L)
        verify(controls, times(2)).fastForward()
        verify(controls, times(0)).rewind()
    }
}
