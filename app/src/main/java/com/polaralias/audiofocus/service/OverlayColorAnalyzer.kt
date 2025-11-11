package com.polaralias.audiofocus.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.polaralias.audiofocus.data.OverlayFillMode
import com.polaralias.audiofocus.data.OverlayPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class OverlayColorScheme(
    @ColorInt val containerColor: Int,
    @ColorInt val contentColor: Int
)

object OverlayColorAnalyzer {
    private const val TAG = "OverlayColorAnalyzer"
    private const val CONTAINER_ALPHA = 0xEB

    suspend fun compute(
        context: Context,
        preferences: OverlayPreferences
    ): OverlayColorScheme {
        return when (preferences.fillMode) {
            OverlayFillMode.SOLID_COLOR -> schemeForSolid(preferences.overlayColor)
            OverlayFillMode.IMAGE -> {
                val scheme = preferences.imageUri?.let { analyzeImage(context, it) }
                scheme ?: schemeForSolid(preferences.overlayColor)
            }
        }
    }

    fun fallbackFor(preferences: OverlayPreferences): OverlayColorScheme =
        schemeForSolid(preferences.overlayColor)

    private fun schemeForSolid(@ColorInt overlayColor: Int): OverlayColorScheme {
        val container = overlayContainerColor(overlayColor)
        val content = contrastingColor(container)
        return OverlayColorScheme(container, content)
    }

    private fun overlayContainerColor(@ColorInt color: Int): Int {
        return ColorUtils.setAlphaComponent(color, CONTAINER_ALPHA)
    }

    private fun contrastingColor(@ColorInt color: Int): Int {
        return if (ColorUtils.calculateLuminance(color) > 0.5) Color.BLACK else Color.WHITE
    }

    private fun ensureReadable(@ColorInt candidate: Int, @ColorInt background: Int): Int {
        val contrast = ColorUtils.calculateContrast(candidate, background)
        return if (contrast >= 3.0) candidate else contrastingColor(background)
    }

    private suspend fun analyzeImage(
        context: Context,
        uri: Uri
    ): OverlayColorScheme? = withContext(Dispatchers.IO) {
        val bitmap = loadBitmap(context, uri) ?: return@withContext null
        try {
            val palette = Palette.Builder(bitmap)
                .clearFilters()
                .maximumColorCount(16)
                .generate()
            val swatch = palette.dominantSwatch
                ?: palette.vibrantSwatch
                ?: palette.lightVibrantSwatch
                ?: palette.mutedSwatch
                ?: palette.darkVibrantSwatch
                ?: palette.lightMutedSwatch
                ?: palette.darkMutedSwatch
            swatch?.let {
                val container = overlayContainerColor(it.rgb)
                val content = ensureReadable(it.bodyTextColor, container)
                OverlayColorScheme(container, content)
            }
        } finally {
            if (bitmap.config != Bitmap.Config.HARDWARE && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private suspend fun loadBitmap(context: Context, uri: Uri): Bitmap? =
        withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        decoder.isMutableRequired = false
                    }
                } else {
                    context.contentResolver.openInputStream(uri)?.use(BitmapFactory::decodeStream)
                }
            }.onFailure { error ->
                Log.e(TAG, "Unable to decode image for color analysis: $uri", error)
            }.getOrNull()
        }
}
