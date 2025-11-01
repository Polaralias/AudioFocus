package com.polaralias.audiofocus.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val ChromeDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF001632),
    primaryContainer = Color(0xFF1F3B64),
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFF69C1FF),
    onSecondary = Color(0xFF00243C),
    secondaryContainer = Color(0xFF17364D),
    onSecondaryContainer = Color(0xFFCAE6FF),
    tertiary = Color(0xFFF6A8FF),
    onTertiary = Color(0xFF36003A),
    tertiaryContainer = Color(0xFF4C1B51),
    onTertiaryContainer = Color(0xFFFFD6FA),
    background = Color(0xFF101418),
    onBackground = Color(0xFFE1E7F1),
    surface = Color(0xFF13171C),
    onSurface = Color(0xFFE1E7F1),
    surfaceVariant = Color(0xFF2C3138),
    onSurfaceVariant = Color(0xFFC3C8D2),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF42464D),
    inverseSurface = Color(0xFFE1E7F1),
    inverseOnSurface = Color(0xFF1C1F24),
    inversePrimary = Color(0xFF3B6EB4),
    scrim = Color(0xFF0B1118)
)

private val OverlayTypography = Typography()

@Composable
fun AudioFocusTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = remember(context) { audioFocusColorScheme(context) }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = OverlayTypography,
        content = content
    )
}

fun audioFocusColorScheme(context: Context): ColorScheme {
    val dynamicScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        dynamicDarkColorScheme(context)
    } else {
        null
    }
    val scheme = dynamicScheme ?: ChromeDarkColorScheme
    return scheme.copy(scrim = ChromeDarkColorScheme.scrim)
}
