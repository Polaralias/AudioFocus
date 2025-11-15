package com.polaralias.audiofocus.ui.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import android.graphics.Color as AndroidColor
import android.util.Log
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import kotlin.coroutines.cancellation.CancellationException
import com.polaralias.audiofocus.data.OverlayFillMode
import kotlin.math.roundToInt
import com.polaralias.audiofocus.R

@Composable
fun ControlsOverlay(
    state: ControlsUiState,
    onTogglePlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    
    // Adapt sizing based on whether it's a partial overlay
    val verticalPadding = if (state.isPartialOverlay) 16.dp else 24.dp
    val horizontalPadding = if (state.isPartialOverlay) 24.dp else 32.dp
    val buttonSizeSeek = if (state.isPartialOverlay) 56.dp else 64.dp
    val buttonSizePlay = if (state.isPartialOverlay) 64.dp else 72.dp
    val iconSizeSeek = if (state.isPartialOverlay) 40.dp else 48.dp
    val iconSizePlay = if (state.isPartialOverlay) 48.dp else 56.dp
    val spacerHeight = if (state.isPartialOverlay) 8.dp else 12.dp
    val baseContentColor = remember(state.contentColor) { Color(state.contentColor) }
    val contentColor = remember(state.overlayColor, state.overlayFillMode, baseContentColor) {
        if (state.overlayFillMode == OverlayFillMode.SOLID_COLOR && state.overlayColor == AndroidColor.BLACK) {
            Color.White
        } else {
            baseContentColor
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val rewindDescription = stringResource(id = R.string.control_rewind)
                IconButton(
                    onClick = {
                        if (state.canSeekBy) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSeekBy(-10_000)
                        }
                    },
                    modifier = Modifier.size(buttonSizeSeek)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Replay10,
                        contentDescription = rewindDescription,
                        tint = contentColor,
                        modifier = Modifier.size(iconSizeSeek)
                    )
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onTogglePlayPause()
                    },
                    modifier = Modifier.size(buttonSizePlay)
                ) {
                    val pauseDescription = stringResource(id = R.string.control_pause)
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = pauseDescription,
                        tint = contentColor,
                        modifier = Modifier.size(iconSizePlay)
                    )
                }
                val forwardDescription = stringResource(id = R.string.control_forward)
                IconButton(
                    onClick = {
                        if (state.canSeekBy) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSeekBy(10_000)
                        }
                    },
                    modifier = Modifier.size(buttonSizeSeek)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Forward10,
                        contentDescription = forwardDescription,
                        tint = contentColor,
                        modifier = Modifier.size(iconSizeSeek)
                    )
                }
            }
            Spacer(modifier = Modifier.height(spacerHeight))
            val sliderRangeMax = run {
                val base = maxOf(state.safeDuration, state.clampedPosition).toFloat().coerceAtLeast(0f)
                when {
                    base > 0f -> base
                    state.canSeekBy -> RELATIVE_SEEK_SLIDER_RANGE
                    else -> 0f
                }
            }
            val sliderValue = if (sliderRangeMax <= 0f) {
                0f
            } else {
                state.clampedPosition.toFloat().coerceIn(0f, sliderRangeMax)
            }
            val sliderEnabled = state.canSeekBy || (state.isPlaying && state.canSeek && sliderRangeMax > 0f)
            Slider(
                value = sliderValue,
                onValueChange = { newValue ->
                    if (!sliderEnabled) return@Slider
                    val target = newValue.roundToInt().toLong()
                    var handled = false
                    if (sliderRangeMax > 0f) {
                        handled = try {
                            onSeekTo(target)
                            true
                        } catch (error: Exception) {
                            if (error is CancellationException) throw error
                            Log.w(TAG, "onSeekTo failed", error)
                            false
                        }
                    }
                    if (!handled && state.canSeekBy) {
                        val delta = target - state.clampedPosition
                        if (delta != 0L) {
                            handled = true
                            onSeekBy(delta)
                        }
                    }
                },
                valueRange = 0f..sliderRangeMax,
                enabled = sliderEnabled,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = contentColor,
                    activeTrackColor = contentColor,
                    activeTickColor = contentColor.copy(alpha = 0.6f),
                    inactiveTrackColor = contentColor.copy(alpha = 0.24f),
                    inactiveTickColor = contentColor.copy(alpha = 0.24f)
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTimestamp(state.clampedPosition),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = contentColor
                )
                Text(
                    text = formatTimestamp(state.safeDuration),
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = contentColor
                )
            }
        }
    }
}

private const val RELATIVE_SEEK_SLIDER_RANGE = 10_000f
private const val TAG = "ControlsOverlay"

private fun formatTimestamp(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val totalSeconds = millis / 1000
    val seconds = (totalSeconds % 60).toInt()
    val minutes = ((totalSeconds / 60) % 60).toInt()
    val hours = (totalSeconds / 3600).toInt()
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes + hours * 60, seconds)
    }
}
