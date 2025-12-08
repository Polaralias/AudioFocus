package com.audiofocus.app.ui.overlay

import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.audiofocus.app.core.logic.MediaControlClient
import com.audiofocus.app.core.model.AppSettings
import com.audiofocus.app.core.model.MediaAction
import com.audiofocus.app.core.model.TargetApp
import com.audiofocus.app.core.model.ThemeConfig
import com.audiofocus.app.core.model.ThemeType
import com.audiofocus.app.service.monitor.MediaSessionMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun OverlayScreen(
    targetAppFlow: StateFlow<TargetApp?>,
    mediaControlClient: MediaControlClient,
    mediaSessionMonitor: MediaSessionMonitor,
    appSettingsFlow: Flow<AppSettings>
) {
    val targetApp by targetAppFlow.collectAsState()
    val appSettings by appSettingsFlow.collectAsState(initial = AppSettings())
    val controllers by mediaSessionMonitor.controllers.collectAsState(initial = emptyList())

    val targetTheme = when (targetApp) {
        TargetApp.YOUTUBE -> appSettings.youtubeTheme
        TargetApp.YOUTUBE_MUSIC -> appSettings.youtubeMusicTheme
        else -> ThemeConfig() // Default fallback
    }

    val controller = targetApp?.let { app ->
        controllers.find { it.packageName == app.packageName }
    }

    // Restore Album Art extraction logic
    val metadata = controller?.metadata
    val artBitmap = remember(metadata) {
        metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(targetTheme.color))
    ) {
        // 1. Custom Image (Highest Priority if type is IMAGE and URI exists)
        if (targetTheme.type == ThemeType.IMAGE && targetTheme.imageUri != null) {
            val blurModifier = if (targetTheme.blurLevel > 0 && Build.VERSION.SDK_INT >= 31) {
                // Map blur level (0-3) to dp (0, 10, 20, 30)
                Modifier.blur((targetTheme.blurLevel * 10).dp)
            } else {
                Modifier
            }

            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(Uri.parse(targetTheme.imageUri))
                    .crossfade(true)
                    .build(),
                contentDescription = "Background",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurModifier)
            )

            if (targetTheme.blurLevel < 2) {
                 Box(
                     modifier = Modifier
                         .fillMaxSize()
                         .background(Color.Black.copy(alpha = 0.3f))
                 )
            }
        }
        // 2. Album Art Fallback (If in SOLID mode, OR IMAGE mode but no URI)
        else if (artBitmap != null) {
            // If user selected SOLID, they likely want the art to be the main focus or background.
            // If they selected a specific blur level, we should apply it to the art too?
            // Spec: "An optional blur setting... is available for images".
            // Implementation decision: Apply the blur setting to the Album Art as well if it's acting as the background image.

            val blurModifier = if (targetTheme.blurLevel > 0 && Build.VERSION.SDK_INT >= 31) {
                Modifier.blur((targetTheme.blurLevel * 10).dp)
            } else {
                Modifier
            }

            Image(
                bitmap = artBitmap.asImageBitmap(),
                contentDescription = "Album Art",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .then(blurModifier)
            )

            // Standard scrim for legibility
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.5f))
            )
        }

        if (targetApp != null) {
            if (controller != null) {
                MediaControls(
                    controller = controller,
                    onAction = { action -> mediaControlClient.sendAction(targetApp!!, action) }
                )
            } else {
                Text(
                    text = "Connecting to ${targetApp!!.name}...",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
    }
}

@Composable
fun MediaControls(
    controller: MediaController,
    onAction: (MediaAction) -> Unit
) {
    val playbackState = controller.playbackState
    val metadata = controller.metadata

    val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING ||
                    playbackState?.state == PlaybackState.STATE_BUFFERING

    val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L

    var currentPosition by remember { mutableLongStateOf(0L) }
    var dragPosition by remember { mutableLongStateOf(0L) }
    var isDragging by remember { mutableStateOf(false) }

    LaunchedEffect(playbackState) {
        currentPosition = calculatePosition(playbackState)
        if (isPlaying) {
            while(true) {
                delay(200) // Update every 200ms
                if (!isDragging) {
                    currentPosition = calculatePosition(playbackState)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title (optional, helpful for context)
        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)

        if (!title.isNullOrEmpty()) {
            Text(text = title, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
        if (!artist.isNullOrEmpty()) {
            Text(text = artist, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Progress Bar
        if (duration > 0) {
            Slider(
                value = (if (isDragging) dragPosition else currentPosition).toFloat(),
                onValueChange = {
                    isDragging = true
                    dragPosition = it.toLong()
                },
                onValueChangeFinished = {
                    onAction(MediaAction.Seek(dragPosition))
                    // Optimistically update current position to avoid jump back
                    currentPosition = dragPosition
                    isDragging = false
                },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = formatTime(if (isDragging) dragPosition else currentPosition), color = Color.Gray)
                Text(text = formatTime(duration), color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Controls
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = { onAction(MediaAction.SkipBackward) }) {
                Icon(
                    imageVector = Icons.Default.FastRewind,
                    contentDescription = "Rewind 10s",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.width(32.dp))

            IconButton(onClick = {
                if (isPlaying) onAction(MediaAction.Pause) else onAction(MediaAction.Play)
            }) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(modifier = Modifier.width(32.dp))

            IconButton(onClick = { onAction(MediaAction.SkipForward) }) {
                Icon(
                    imageVector = Icons.Default.FastForward,
                    contentDescription = "Forward 10s",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

private fun calculatePosition(state: PlaybackState?): Long {
    if (state == null) return 0L
    val current = state.position
    if (state.state == PlaybackState.STATE_PLAYING) {
        val timeDelta = SystemClock.elapsedRealtime() - state.lastPositionUpdateTime
        val predicted = current + (timeDelta * state.playbackSpeed).toLong()
        return predicted.coerceAtLeast(0L)
    }
    return current
}

private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}
