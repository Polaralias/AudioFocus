package com.polaralias.audiofocus.overlay

import android.view.View
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object OverlayAnimator {
    private const val ANIMATION_DURATION_MS = 200L

    suspend fun fadeIn(view: View) {
        suspendCancellableCoroutine { continuation ->
            view.alpha = 0f
            view.visibility = View.VISIBLE

            view.animate()
                .alpha(1f)
                .setDuration(ANIMATION_DURATION_MS)
                .withEndAction {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
                .start()
            
            continuation.invokeOnCancellation {
                view.animate().cancel()
            }
        }
    }

    suspend fun fadeOut(view: View) {
        suspendCancellableCoroutine { continuation ->
            view.animate()
                .alpha(0f)
                .setDuration(ANIMATION_DURATION_MS)
                .withEndAction {
                    view.visibility = View.GONE
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
                .start()
            
            continuation.invokeOnCancellation {
                view.animate().cancel()
            }
        }
    }

    fun showImmediate(view: View) {
        view.animate().cancel()
        view.alpha = 1f
        view.visibility = View.VISIBLE
    }

    fun hideImmediate(view: View) {
        view.animate().cancel()
        view.alpha = 0f
        view.visibility = View.GONE
    }
}
