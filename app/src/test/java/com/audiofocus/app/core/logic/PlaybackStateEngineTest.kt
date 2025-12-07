package com.audiofocus.app.core.logic

import com.audiofocus.app.core.model.*
import com.audiofocus.app.service.monitor.AccessibilityMonitor
import com.audiofocus.app.service.monitor.AccessibilityState
import com.audiofocus.app.service.monitor.MediaSessionMonitor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class PlaybackStateEngineTest {

    private lateinit var accessibilityMonitor: AccessibilityMonitor
    private lateinit var mediaSessionMonitor: MediaSessionMonitor
    private lateinit var engine: PlaybackStateEngine

    private val accessibilityStates = MutableStateFlow<Map<TargetApp, AccessibilityState>>(emptyMap())
    private val mediaSessionStates = MutableStateFlow<Map<TargetApp, PlaybackStateSimplified>>(emptyMap())

    @Before
    fun setup() {
        accessibilityMonitor = mockk()
        mediaSessionMonitor = mockk()

        every { accessibilityMonitor.states } returns accessibilityStates
        every { mediaSessionMonitor.observe() } returns mediaSessionStates

        engine = PlaybackStateEngine(accessibilityMonitor, mediaSessionMonitor)
    }

    @Test
    fun `YouTube Fullscreen Video Playing should show overlay`() = runTest {
        // Arrange
        accessibilityStates.value = mapOf(
            TargetApp.YOUTUBE to AccessibilityState(WindowState.FOREGROUND_FULLSCREEN, PlaybackType.VISIBLE_VIDEO)
        )
        mediaSessionStates.value = mapOf(TargetApp.YOUTUBE to PlaybackStateSimplified.PLAYING)

        // Act
        val decision = engine.overlayDecision.first()

        // Assert
        assertEquals(true, decision.shouldOverlay)
        assertEquals(OverlayMode.FULL_SCREEN, decision.overlayMode)
        assertEquals(TargetApp.YOUTUBE, decision.targetApp)
    }

    @Test
    fun `YouTube Paused should hide overlay`() = runTest {
        // Arrange
        accessibilityStates.value = mapOf(
            TargetApp.YOUTUBE to AccessibilityState(WindowState.FOREGROUND_FULLSCREEN, PlaybackType.VISIBLE_VIDEO)
        )
        mediaSessionStates.value = mapOf(TargetApp.YOUTUBE to PlaybackStateSimplified.PAUSED)

        // Act
        val decision = engine.overlayDecision.first()

        // Assert
        assertEquals(false, decision.shouldOverlay)
    }

    @Test
    fun `YouTube Audio Only should hide overlay`() = runTest {
        // Arrange
        accessibilityStates.value = mapOf(
            TargetApp.YOUTUBE to AccessibilityState(WindowState.FOREGROUND_FULLSCREEN, PlaybackType.NONE) // No video surface
        )
        mediaSessionStates.value = mapOf(TargetApp.YOUTUBE to PlaybackStateSimplified.PLAYING)

        // Act
        val decision = engine.overlayDecision.first()

        // Assert
        assertEquals(false, decision.shouldOverlay)
    }

    @Test
    fun `YouTube Music Video Playing should show overlay`() = runTest {
        // Arrange
        accessibilityStates.value = mapOf(
            TargetApp.YOUTUBE_MUSIC to AccessibilityState(WindowState.FOREGROUND_FULLSCREEN, PlaybackType.VISIBLE_VIDEO)
        )
        mediaSessionStates.value = mapOf(TargetApp.YOUTUBE_MUSIC to PlaybackStateSimplified.PLAYING)

        // Act
        val decision = engine.overlayDecision.first()

        // Assert
        assertEquals(true, decision.shouldOverlay)
        assertEquals(OverlayMode.FULL_SCREEN, decision.overlayMode)
        assertEquals(TargetApp.YOUTUBE_MUSIC, decision.targetApp)
    }

    @Test
    fun `YouTube Music Audio Playing should hide overlay`() = runTest {
        // Arrange
        accessibilityStates.value = mapOf(
            TargetApp.YOUTUBE_MUSIC to AccessibilityState(WindowState.FOREGROUND_FULLSCREEN, PlaybackType.NONE)
        )
        mediaSessionStates.value = mapOf(TargetApp.YOUTUBE_MUSIC to PlaybackStateSimplified.PLAYING)

        // Act
        val decision = engine.overlayDecision.first()

        // Assert
        assertEquals(false, decision.shouldOverlay)
    }
}
