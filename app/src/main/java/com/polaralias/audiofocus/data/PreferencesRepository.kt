package com.polaralias.audiofocus.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import com.polaralias.audiofocus.ui.theme.audioFocusColorScheme

internal const val DATASTORE_NAME = "audiofocus_preferences"
private const val TAG = "PreferencesRepository"

private val Context.dataStore by preferencesDataStore(DATASTORE_NAME)

class PreferencesRepository(private val context: Context) {
    private val enableYoutubeKey = booleanPreferencesKey("enable_youtube")
    private val enableYoutubeMusicKey = booleanPreferencesKey("enable_youtube_music")
    private val startOnBootKey = booleanPreferencesKey("start_on_boot")
    private val dimAmountKey = floatPreferencesKey("dim_amount")
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
    private val overlayFillModeKey = stringPreferencesKey("overlay_fill_mode")
    private val overlayColorKey = intPreferencesKey("overlay_color")
    private val overlayImageUriKey = stringPreferencesKey("overlay_image_uri")

    private val defaultOverlayColorInternal: Int by lazy {
        audioFocusColorScheme(context).scrim.toArgb()
    }

    val defaultOverlayColor: Int
        get() = defaultOverlayColorInternal

    val preferencesFlow: Flow<OverlayPreferences> = context.dataStore.data
        .catch { e ->
            // Handle IO errors gracefully by emitting default preferences
            if (e is IOException) {
                Log.e(TAG, "Error reading preferences, using defaults", e)
                emit(androidx.datastore.preferences.core.emptyPreferences())
            } else {
                throw e
            }
        }
        .map { prefs ->
            OverlayPreferences(
                enableYouTube = prefs[enableYoutubeKey] ?: true,
                enableYouTubeMusic = prefs[enableYoutubeMusicKey] ?: true,
                startOnBoot = prefs[startOnBootKey] ?: false,
                dimAmount = prefs[dimAmountKey]?.coerceIn(0.2f, 1f) ?: 0.9f,
                overlayFillMode = prefs[overlayFillModeKey]
                    ?.let { stored ->
                        runCatching { OverlayFillMode.valueOf(stored) }
                            .getOrDefault(OverlayFillMode.SOLID_COLOR)
                    }
                    ?: OverlayFillMode.SOLID_COLOR,
                overlayColor = prefs[overlayColorKey] ?: defaultOverlayColorInternal,
                overlayImageUri = prefs[overlayImageUriKey]
                    ?.let { stored -> runCatching { Uri.parse(stored) }.getOrNull() }
            )
        }

    private suspend fun <T> editPreference(key: androidx.datastore.preferences.core.Preferences.Key<T>, value: T) {
        try {
            context.dataStore.edit { it[key] = value }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing preference: $key", e)
            throw e
        }
    }

    suspend fun setEnableYouTube(enabled: Boolean) {
        editPreference(enableYoutubeKey, enabled)
    }

    suspend fun setEnableYouTubeMusic(enabled: Boolean) {
        editPreference(enableYoutubeMusicKey, enabled)
    }

    suspend fun setStartOnBoot(enabled: Boolean) {
        editPreference(startOnBootKey, enabled)
    }

    suspend fun setDimAmount(alpha: Float) {
        editPreference(dimAmountKey, alpha.coerceIn(0.2f, 1f))
    }

    suspend fun setOverlaySolidColor(color: Int) {
        try {
            context.dataStore.edit { prefs ->
                prefs[overlayColorKey] = color
                prefs[overlayFillModeKey] = OverlayFillMode.SOLID_COLOR.name
                prefs.remove(overlayImageUriKey)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error setting overlay solid color", e)
            throw e
        }
    }

    suspend fun setOverlayImage(uri: Uri) {
        try {
            context.dataStore.edit { prefs ->
                prefs[overlayImageUriKey] = uri.toString()
                prefs[overlayFillModeKey] = OverlayFillMode.IMAGE.name
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error setting overlay image uri", e)
            throw e
        }
    }

    suspend fun clearOverlayImage() {
        try {
            context.dataStore.edit { prefs ->
                prefs.remove(overlayImageUriKey)
                prefs[overlayFillModeKey] = OverlayFillMode.SOLID_COLOR.name
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error clearing overlay image uri", e)
            throw e
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        editPreference(onboardingCompletedKey, completed)
    }

    suspend fun isOnboardingCompleted(): Boolean {
        return try {
            val prefs = context.dataStore.data.first()
            prefs[onboardingCompletedKey] ?: false
        } catch (e: IOException) {
            Log.e(TAG, "Error reading onboardingCompleted preference, assuming false", e)
            false
        }
    }

    suspend fun current(): OverlayPreferences {
        return try {
            val prefs = context.dataStore.data.first()
            OverlayPreferences(
                enableYouTube = prefs[enableYoutubeKey] ?: true,
                enableYouTubeMusic = prefs[enableYoutubeMusicKey] ?: true,
                startOnBoot = prefs[startOnBootKey] ?: false,
                dimAmount = prefs[dimAmountKey]?.coerceIn(0.2f, 1f) ?: 0.9f,
                overlayFillMode = prefs[overlayFillModeKey]
                    ?.let { stored ->
                        runCatching { OverlayFillMode.valueOf(stored) }
                            .getOrDefault(OverlayFillMode.SOLID_COLOR)
                    }
                    ?: OverlayFillMode.SOLID_COLOR,
                overlayColor = prefs[overlayColorKey] ?: defaultOverlayColorInternal,
                overlayImageUri = prefs[overlayImageUriKey]
                    ?.let { stored -> runCatching { Uri.parse(stored) }.getOrNull() }
            )
        } catch (e: IOException) {
            Log.e(TAG, "Error reading current preferences, using defaults", e)
            OverlayPreferences(overlayColor = defaultOverlayColorInternal)
        }
    }
}
