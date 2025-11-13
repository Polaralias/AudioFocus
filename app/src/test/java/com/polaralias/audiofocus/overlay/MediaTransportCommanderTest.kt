package com.polaralias.audiofocus.overlay

import android.media.session.MediaController
import android.media.session.PlaybackState
import android.util.Log
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.mockStatic
import org.mockito.Mockito.times
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class MediaTransportCommanderTest {
    private lateinit var logMock: MockedStatic<Log>

    @Before
    fun setup() {
        logMock = mockStatic(Log::class.java)
    }

    @After
    fun tearDown() {
        logMock.close()
    }

    @Test
    fun seekToFallsBackToRelativeWhenSeekToUnsupported() {
        val controls = mock<MediaController.TransportControls>()
        val controller = mock<MediaController>()
        
        val playbackState = mock<PlaybackState>()
        whenever(playbackState.actions).thenReturn(PlaybackState.ACTION_FAST_FORWARD or PlaybackState.ACTION_REWIND)
        whenever(playbackState.position).thenReturn(30_000L)

        doThrow(IllegalStateException("seekTo not supported"))
            .whenever(controls)
            .seekTo(any())

        val commander = MediaTransportCommander(controller, playbackState, controls)

        commander.seekTo(50_000L)

        verify(controls).seekTo(50_000L)
        verify(controls, times(2)).fastForward()
        verify(controls, times(0)).rewind()
    }
}
