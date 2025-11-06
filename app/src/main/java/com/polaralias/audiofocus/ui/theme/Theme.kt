package com.polaralias.audiofocus.ui.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
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

private val ChromeLightColorScheme = lightColorScheme(
    primary = Color(0xFF3B6EB4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),
    onPrimaryContainer = Color(0xFF001632),
    secondary = Color(0xFF0062A1),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCAE6FF),
    onSecondaryContainer = Color(0xFF00243C),
    tertiary = Color(0xFF6E3875),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD6FA),
    onTertiaryContainer = Color(0xFF36003A),
    background = Color(0xFFFBFCFF),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFFBFCFF),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C6CF),
    inverseSurface = Color(0xFF2E3135),
    inverseOnSurface = Color(0xFFF0F1F4),
    inversePrimary = Color(0xFF8AB4F8),
    scrim = Color(0xFF000000)
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
    val isLightMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_NO
    
    val dynamicScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isLightMode) {
            dynamicLightColorScheme(context)
        } else {
            dynamicDarkColorScheme(context)
        }
    } else {
        null
    }
    
    val baseScheme = if (isLightMode) ChromeLightColorScheme else ChromeDarkColorScheme
    val scheme = dynamicScheme ?: baseScheme
    
    // Keep scrim from the base scheme to ensure consistent overlay dimming
    return scheme.copy(scrim = baseScheme.scrim)
}
