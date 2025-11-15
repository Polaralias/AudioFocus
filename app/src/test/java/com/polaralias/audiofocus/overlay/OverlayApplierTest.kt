package com.polaralias.audiofocus.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayApplierTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `default overlay view is opaque and alpha is one`() {
        val view = invokeDefaultOverlayView(context)
        assertEquals(1f, view.alpha)

        val background = view.background
        assertNotNull("Background should be a ColorDrawable", background as? ColorDrawable)
        background as ColorDrawable
        val color = background.color
        assertEquals(255, Color.alpha(color))
    }

    private fun invokeDefaultOverlayView(context: Context): View {
        val method = OverlayApplier.Companion::class.java
            .getDeclaredMethod("defaultOverlayView", Context::class.java)
        method.isAccessible = true
        return method.invoke(OverlayApplier.Companion, context) as View
    }
}
