package com.polaralias.audiofocus.data

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.polaralias.audiofocus.ui.theme.audioFocusColorScheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private const val DATASTORE_NAME = "audiofocus_preferences"
private const val TAG = "PreferencesRepository"

private val Context.dataStore by preferencesDataStore(DATASTORE_NAME)

class PreferencesRepository(private val context: Context) {
    private val enableYoutubeKey = booleanPreferencesKey("enable_youtube")
    private val enableYoutubeMusicKey = booleanPreferencesKey("enable_youtube_music")
    private val startOnBootKey = booleanPreferencesKey("start_on_boot")
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")
    private val overlayFillModeKey = stringPreferencesKey("overlay_fill_mode")
    private val overlayColorKey = intPreferencesKey("overlay_color")
    private val overlayImageUriKey = stringPreferencesKey("overlay_image_uri")

    private val defaultOverlayColor: Int by lazy {
        val color = audioFocusColorScheme(context).scrim.copy(alpha = 1f).toArgb()
        OverlayDefaults.defaultColor = color
        color
    }

    val overlayDefaultColor: Int
        get() = defaultOverlayColor

    val preferencesFlow: Flow<OverlayPreferences> = context.dataStore.data
        .catch { e ->
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
                fillMode = prefs[overlayFillModeKey]?.let { storedMode ->
                    runCatching { OverlayFillMode.valueOf(storedMode) }
                        .getOrDefault(OverlayFillMode.SOLID_COLOR)
                } ?: OverlayFillMode.SOLID_COLOR,
                overlayColor = prefs[overlayColorKey] ?: defaultOverlayColor,
                imageUri = prefs[overlayImageUriKey]?.takeIf { it.isNotBlank() }?.let(Uri::parse)
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

    suspend fun setOnboardingCompleted(completed: Boolean) {
        editPreference(onboardingCompletedKey, completed)
    }

    suspend fun setOverlayFillMode(mode: OverlayFillMode) {
        Log.i(TAG, "Setting overlay fill mode to $mode")
        try {
            context.dataStore.edit { prefs ->
                prefs[overlayFillModeKey] = mode.name
                if (mode != OverlayFillMode.IMAGE) {
                    prefs.remove(overlayImageUriKey)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing overlay fill mode", e)
            throw e
        }
    }

    suspend fun setOverlayColor(color: Int) {
        val opaqueColor = ensureOpaque(color)
        Log.i(TAG, "Setting overlay color to ${String.format("%08X", opaqueColor)}")
        try {
            context.dataStore.edit { prefs ->
                prefs[overlayColorKey] = opaqueColor
                prefs[overlayFillModeKey] = OverlayFillMode.SOLID_COLOR.name
                prefs.remove(overlayImageUriKey)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing overlay color", e)
            throw e
        }
    }

    suspend fun setOverlayImage(uri: Uri) {
        Log.i(TAG, "Persisting overlay image uri: $uri")
        try {
            context.dataStore.edit { prefs ->
                prefs[overlayImageUriKey] = uri.toString()
                prefs[overlayFillModeKey] = OverlayFillMode.IMAGE.name
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error writing overlay image", e)
            throw e
        }
    }

    suspend fun clearOverlayImage() {
        Log.i(TAG, "Clearing overlay image selection")
        try {
            context.dataStore.edit { prefs ->
                prefs.remove(overlayImageUriKey)
                if (prefs[overlayFillModeKey] == OverlayFillMode.IMAGE.name) {
                    prefs[overlayFillModeKey] = OverlayFillMode.SOLID_COLOR.name
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error clearing overlay image", e)
            throw e
        }
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
                fillMode = prefs[overlayFillModeKey]?.let { storedMode ->
                    runCatching { OverlayFillMode.valueOf(storedMode) }
                        .getOrDefault(OverlayFillMode.SOLID_COLOR)
                } ?: OverlayFillMode.SOLID_COLOR,
                overlayColor = prefs[overlayColorKey] ?: defaultOverlayColor,
                imageUri = prefs[overlayImageUriKey]?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            )
        } catch (e: IOException) {
            Log.e(TAG, "Error reading current preferences, using defaults", e)
            OverlayPreferences()
        }
    }
}

private fun ensureOpaque(color: Int): Int = color or 0xFF000000.toInt()
