package com.polaralias.audiofocus.ui.controls

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Slider
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val rewindDescription = stringResource(id = R.string.control_rewind)
            IconButton(onClick = {
                if (state.canSeekBy) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSeekBy(-10_000)
                }
            }) {
                Icon(
                    imageVector = Icons.Rounded.Replay10,
                    contentDescription = rewindDescription,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onTogglePlayPause()
            }) {
                val playDescription = stringResource(id = R.string.control_play)
                Icon(
                    imageVector = if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = playDescription,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            val forwardDescription = stringResource(id = R.string.control_forward)
            IconButton(onClick = {
                if (state.canSeekBy) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSeekBy(10_000)
                }
            }) {
                Icon(
                    imageVector = Icons.Rounded.Forward10,
                    contentDescription = forwardDescription,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        val sliderValue = remember(state.position, state.duration) {
            val duration = state.safeDuration.toFloat()
            if (duration <= 0f) 0f else state.clampedPosition.toFloat().coerceIn(0f, duration)
        }
        Slider(
            value = sliderValue,
            onValueChange = {
                if (state.canSeek) {
                    onSeekTo(it.roundToInt().toLong())
                }
            },
            valueRange = 0f..state.safeDuration.toFloat().coerceAtLeast(0f),
            enabled = state.canSeek,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTimestamp(state.clampedPosition),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
            )
            Text(
                text = formatTimestamp(state.safeDuration),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
            )
        }
    }
}

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
