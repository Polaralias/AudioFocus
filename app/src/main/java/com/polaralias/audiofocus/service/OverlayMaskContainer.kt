package com.polaralias.audiofocus.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.FrameLayout
import androidx.appcompat.widget.AppCompatImageView
import com.polaralias.audiofocus.data.OverlayFillMode
import com.polaralias.audiofocus.data.OverlayPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class OverlayMaskContainer(context: Context) : FrameLayout(context) {
    private val imageView: AppCompatImageView = AppCompatImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        visibility = GONE
    }

    private var loadJob: Job? = null

    init {
        addView(imageView)
    }

    fun applyPreferences(scope: CoroutineScope, preferences: OverlayPreferences) {
        loadJob?.cancel()
        clearImage()
        when (preferences.fillMode) {
            OverlayFillMode.SOLID_COLOR -> {
                setBackgroundColor(preferences.overlayColor)
            }

            OverlayFillMode.IMAGE -> {
                val uri = preferences.imageUri
                if (uri == null) {
                    setBackgroundColor(preferences.overlayColor)
                    return
                }
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                loadJob = scope.launch {
                    val bitmap = loadBitmap(uri)
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                            imageView.visibility = VISIBLE
                        } else {
                            setBackgroundColor(preferences.overlayColor)
                        }
                        loadJob = null
                    }
                }
            }
        }
    }

    fun clearImage() {
        loadJob?.cancel()
        loadJob = null
        imageView.setImageDrawable(null)
        imageView.visibility = GONE
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        clearImage()
    }

    private suspend fun loadBitmap(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
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
            Log.e(TAG, "Unable to decode overlay image: $uri", error)
        }.getOrNull()
    }

    companion object {
        private const val TAG = "OverlayMaskContainer"
    }
}
