package com.polaralias.audiofocus.data

import android.net.Uri
import android.test.RenamingDelegatingContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreferencesRepositoryTest {

    @Test
    fun overlayAppearancePreferencesPersist() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val baseContext = instrumentation.targetContext
        val context = RenamingDelegatingContext(baseContext, "test_")
        val repository = PreferencesRepository(context)

        val defaults = repository.current()
        assertEquals(OverlayFillMode.SOLID_COLOR, defaults.fillMode)
        assertEquals(repository.overlayDefaultColor, defaults.overlayColor)
        assertNull(defaults.imageUri)

        val translucentColor = 0x12AABBCC
        repository.setOverlayColor(translucentColor)
        val colorPrefs = repository.current()
        assertEquals(OverlayFillMode.SOLID_COLOR, colorPrefs.fillMode)
        val expectedOpaqueColor = 0xFFAABBCC.toInt()
        assertEquals(expectedOpaqueColor, colorPrefs.overlayColor)
        assertNull(colorPrefs.imageUri)

        val testUri = Uri.parse("content://com.polaralias.audiofocus.test/image")
        repository.setOverlayImage(testUri)
        val imagePrefs = repository.current()
        assertEquals(OverlayFillMode.IMAGE, imagePrefs.fillMode)
        assertEquals(testUri, imagePrefs.imageUri)

        repository.clearOverlayImage()
        val cleared = repository.current()
        assertEquals(OverlayFillMode.SOLID_COLOR, cleared.fillMode)
        assertNull(cleared.imageUri)
        assertEquals(expectedOpaqueColor, cleared.overlayColor)
    }
}
