package com.polaralias.audiofocus.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val OverlayColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC),
    onPrimary = Color.Black,
    surface = Color(0xFF121212),
    onSurface = Color.White,
    background = Color(0xFF000000),
    onBackground = Color.White
)

@Composable
fun AudioFocusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OverlayColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
