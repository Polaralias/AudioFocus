package com.polaralias.audiofocus.ui.controls

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
                        canSeekBy = true
                    ),
                    onTogglePlayPause = { events.add("toggle") },
                    onSeekBy = { events.add("seekBy:$it") },
                    onSeekTo = { events.add("seekTo:$it") }
                )
            }
        }

        val context = composeRule.activity
        composeRule.onNodeWithContentDescription(context.getString(R.string.control_play)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.control_rewind)).performClick()
        composeRule.onNodeWithContentDescription(context.getString(R.string.control_forward)).performClick()
        composeRule.onNodeWithRole(Role.Slider)
            .performSemanticsAction(SemanticsActions.SetProgress) { action -> action(5_000f) }

        assertTrue(events.contains("toggle"))
        assertTrue(events.contains("seekBy:-10000"))
        assertTrue(events.contains("seekBy:10000"))
        assertTrue(events.any { it.startsWith("seekTo:") })
    }
}
