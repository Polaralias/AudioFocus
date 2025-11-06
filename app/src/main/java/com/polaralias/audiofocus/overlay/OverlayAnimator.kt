package com.polaralias.audiofocus.overlay

import android.view.View
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Handles fade-in and fade-out animations for overlay views.
 * Animation duration is 200ms as per Material Design guidelines.
 */
object OverlayAnimator {
    private const val ANIMATION_DURATION_MS = 200L

    /**
     * Animates a view fade-in from alpha 0 to 1.
     * Sets alpha to 0 at the start to ensure consistent animation behavior.
     * Suspends until animation completes.
     */
    suspend fun fadeIn(view: View) {
        suspendCancellableCoroutine { continuation ->
            view.alpha = 0f  // Ensure consistent starting point
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

    /**
     * Animates a view fade-out from current alpha to 0.
     * Suspends until animation completes.
     */
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

    /**
     * Immediately sets view to visible with alpha 1 without animation.
     * Used when quickly transitioning between overlay states.
     */
    fun showImmediate(view: View) {
        view.animate().cancel()
        view.alpha = 1f
        view.visibility = View.VISIBLE
    }

    /**
     * Immediately hides view without animation.
     * Used for cleanup and emergency hide operations.
     */
    fun hideImmediate(view: View) {
        view.animate().cancel()
        view.alpha = 0f
        view.visibility = View.GONE
    }
}
