package com.polaralias.audiofocus.ui.controls

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithRole
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.polaralias.audiofocus.R
import com.polaralias.audiofocus.ui.theme.AudioFocusTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ControlsOverlayTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun controlsInvokeTransportCallbacks() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            AudioFocusTheme {
                ControlsOverlay(
                    state = ControlsUiState(
                        isVisible = true,
                        isPlaying = true,
                        position = 1_000L,
                        duration = 10_000L,
                        canSeek = true,
                        canSeekTo = true,
                        canSeekBy = true
                    ),
                    onTogglePlayPause = { events.add("toggle") },
                    onSeekBy = { events.add("seekBy:$it") },
                    onSeekTo = { events.add("seekTo:$it") }
                )
            }
        }

        val context = composeRule.activity
        composeRule.onNodeWithContentDescription(context.getString(R.string.control_pause)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.control_rewind)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.control_forward)).performClick()
        composeRule.onNodeWithRole(Role.Slider)
            .performSemanticsAction(SemanticsActions.SetProgress) { action -> action(5_000f) }

        assertTrue(events.contains("toggle"))
        assertTrue(events.contains("seekBy:-10000"))
        assertTrue(events.contains("seekBy:10000"))
        assertTrue(events.any { it.startsWith("seekTo:") })
    }

    @Test
    fun controlsInvokeTransportCallbacksInPartialMode() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            AudioFocusTheme {
                ControlsOverlay(
                    state = ControlsUiState(
                        isVisible = true,
                        isPlaying = true,
                        position = 1_000L,
                        duration = 10_000L,
                        canSeek = true,
                        canSeekTo = true,
                        canSeekBy = true,
                        isPartialOverlay = true
                    ),
                    onTogglePlayPause = { events.add("toggle") },
                    onSeekBy = { events.add("seekBy:$it") },
                    onSeekTo = { events.add("seekTo:$it") }
                )
            }
        }

        val context = composeRule.activity
        composeRule.onNodeWithContentDescription(context.getString(R.string.control_pause)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.control_rewind)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.control_forward)).performClick()
        composeRule.onNodeWithRole(Role.Slider)
            .performSemanticsAction(SemanticsActions.SetProgress) { action -> action(5_000f) }

        assertTrue(events.contains("toggle"))
        assertTrue(events.contains("seekBy:-10000"))
        assertTrue(events.contains("seekBy:10000"))
        assertTrue(events.any { it.startsWith("seekTo:") })
    }

    @Test
    fun sliderFallsBackToSeekByWhenSeekToUnavailable() {
        val events = mutableListOf<String>()
        composeRule.setContent {
            AudioFocusTheme {
                ControlsOverlay(
                    state = ControlsUiState(
                        isVisible = true,
                        isPlaying = true,
                        position = 2_000L,
                        duration = 12_000L,
                        canSeek = true,
                        canSeekTo = false,
                        canSeekBy = true,
                        canSeekRelativeOnly = true
                    ),
                    onTogglePlayPause = { events.add("toggle") },
                    onSeekBy = { events.add("seekBy:$it") },
                    onSeekTo = { events.add("seekTo:$it") }
                )
            }
        }

        composeRule.onNodeWithRole(Role.Slider)
            .performSemanticsAction(SemanticsActions.SetProgress) { action -> action(6_000f) }

        assertTrue(events.any { it == "seekBy:4000" })
        assertTrue(events.none { it.startsWith("seekTo:") })
    }

    @Test
    fun sliderEnabledAndUpdatesWithRelativeSeekOnly() {
        val events = mutableListOf<String>()
        var state by mutableStateOf(
            ControlsUiState(
                isVisible = true,
                isPlaying = true,
                position = 2_000L,
                duration = 12_000L,
                canSeek = true,
                canSeekTo = false,
                canSeekBy = true,
                canSeekRelativeOnly = true
            )
        )
        composeRule.setContent {
            AudioFocusTheme {
                ControlsOverlay(
                    state = state,
                    onTogglePlayPause = { events.add("toggle") },
                    onSeekBy = { events.add("seekBy:$it") },
                    onSeekTo = { events.add("seekTo:$it") }
                )
            }
        }

        val slider = composeRule.onNodeWithRole(Role.Slider)
        slider.assertIsEnabled()
        slider.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(2_000f, 0f..12_000f, 0)
            )
        )

        slider.performSemanticsAction(SemanticsActions.SetProgress) { action -> action(8_000f) }

        assertTrue(events.any { it == "seekBy:6000" })

        composeRule.runOnUiThread {
            state = state.copy(position = 8_000L)
        }

        slider.assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(8_000f, 0f..12_000f, 0)
            )
        )
    }
}
