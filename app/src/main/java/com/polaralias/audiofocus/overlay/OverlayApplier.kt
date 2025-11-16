package com.polaralias.audiofocus.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.polaralias.audiofocus.data.OverlayDefaults
import com.polaralias.audiofocus.data.PreferencesRepository
import kotlinx.coroutines.runBlocking

class OverlayApplier(
    private val context: Context,
    private val overlayViewFactory: () -> View = { defaultOverlayView(context) }
) {
    private val wm: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null
    private var attached = false

    fun showFullScreenOverlay() {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        attachOrUpdate(lp)
    }

    fun hideOverlay() {
        overlayView?.let { v ->
            if (attached) {
                try { wm.removeViewImmediate(v) } catch (_: Throwable) {}
                attached = false
            }
        }
    }

    private fun attachOrUpdate(lp: WindowManager.LayoutParams) {
        val view = overlayView ?: overlayViewFactory().also { overlayView = it }
        if (attached) {
            wm.updateViewLayout(view, lp)
        } else {
            wm.addView(view, lp)
            attached = true
        }
    }

    companion object {
        private fun defaultOverlayView(context: Context): View {
            val overlayColor = runCatching {
                runBlocking {
                    PreferencesRepository(context.applicationContext).current().overlayColor
                }
            }.getOrElse { OverlayDefaults.defaultColor }
            val opaqueColor = overlayColor or 0xFF000000.toInt()
            return FrameLayout(context).apply {
                alpha = 1f
                setBackgroundColor(opaqueColor)
            }
        }
    }
}
