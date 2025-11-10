package com.polaralias.audiofocus.data

import android.content.Context
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.compose.ui.graphics.toArgb
import com.polaralias.audiofocus.data.OverlayFillMode
import com.polaralias.audiofocus.ui.theme.audioFocusColorScheme
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class PreferencesRepositoryTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val dataStoreFile = context.filesDir.resolve("datastore/$DATASTORE_NAME.preferences_pb")
        if (dataStoreFile.exists()) {
            dataStoreFile.delete()
        }
    }

    @Test
    fun defaultOverlayColorMatchesColorSchemeScrim() {
        val repository = PreferencesRepository(context)
        val expected = audioFocusColorScheme(context).scrim.toArgb()
        assertEquals(expected, repository.defaultOverlayColor)
    }

    @Test
    fun overlayFillModePersistsSelections() = runTest {
        val repository = PreferencesRepository(context)

        val customColor = Color.parseColor("#80404040")
        repository.setOverlaySolidColor(customColor)

        var preferences = repository.current()
        assertEquals(OverlayFillMode.SOLID_COLOR, preferences.overlayFillMode)
        assertEquals(customColor, preferences.overlayColor)
        assertNull(preferences.overlayImageUri)

        val imageUri = Uri.parse("content://com.polaralias.audiofocus.test/image")
        repository.setOverlayImage(imageUri)

        preferences = repository.current()
        assertEquals(OverlayFillMode.IMAGE, preferences.overlayFillMode)
        assertEquals(imageUri, preferences.overlayImageUri)

        repository.clearOverlayImage()

        preferences = repository.current()
        assertEquals(OverlayFillMode.SOLID_COLOR, preferences.overlayFillMode)
        assertNull(preferences.overlayImageUri)
    }
}
