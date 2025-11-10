package com.polaralias.audiofocus.data

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
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
            )
        } catch (e: IOException) {
            Log.e(TAG, "Error reading current preferences, using defaults", e)
            OverlayPreferences()
        }
    }
}
