package com.polaralias.audiofocus.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.polaralias.audiofocus.model.OverlayState

object OverlayLayoutFactory {
    private const val TAG = "OverlayLayoutFactory"

    fun maskLayoutFor(context: Context, state: OverlayState): WindowManager.LayoutParams? {
        return when (state) {
            OverlayState.None -> {
                Log.d(TAG, "No overlay state, returning null layout")
                null
            }
            is OverlayState.Fullscreen -> {
                Log.d(TAG, "Creating fullscreen mask layout")
                createMaskLayout(
                    context,
                    height = WindowManager.LayoutParams.MATCH_PARENT
                )
            }
            is OverlayState.Partial -> {
                val displayHeight = context.resources.displayMetrics.heightPixels
                val height = (displayHeight * state.heightRatio).toInt()
                Log.d(TAG, "Creating partial mask layout: heightRatio=${state.heightRatio}, height=$height px")
                createMaskLayout(context, height = height)
            }
        }
    }

    // Creates mask layout with FLAG_NOT_TOUCHABLE for full touch passthrough
    // This ensures the overlay doesn't intercept any touch events
    private fun createMaskLayout(context: Context, height: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
        }
    }

    fun controlsLayout(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
    }
}
