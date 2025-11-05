package com.polaralias.audiofocus.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val DATASTORE_NAME = "audiofocus_preferences"

private val Context.dataStore by preferencesDataStore(DATASTORE_NAME)

class PreferencesRepository(private val context: Context) {
    private val enableYoutubeKey = booleanPreferencesKey("enable_youtube")
    private val enableYoutubeMusicKey = booleanPreferencesKey("enable_youtube_music")
    private val startOnBootKey = booleanPreferencesKey("start_on_boot")
    private val dimAmountKey = floatPreferencesKey("dim_amount")
    private val onboardingCompletedKey = booleanPreferencesKey("onboarding_completed")

    val preferencesFlow: Flow<OverlayPreferences> = context.dataStore.data.map { prefs ->
        OverlayPreferences(
            enableYouTube = prefs[enableYoutubeKey] ?: true,
            enableYouTubeMusic = prefs[enableYoutubeMusicKey] ?: true,
            startOnBoot = prefs[startOnBootKey] ?: false,
            dimAmount = prefs[dimAmountKey]?.coerceIn(0.2f, 1f) ?: 0.9f
        )
    }

    suspend fun setEnableYouTube(enabled: Boolean) {
        context.dataStore.edit { it[enableYoutubeKey] = enabled }
    }

    suspend fun setEnableYouTubeMusic(enabled: Boolean) {
        context.dataStore.edit { it[enableYoutubeMusicKey] = enabled }
    }

    suspend fun setStartOnBoot(enabled: Boolean) {
        context.dataStore.edit { it[startOnBootKey] = enabled }
    }

    suspend fun setDimAmount(alpha: Float) {
        context.dataStore.edit { it[dimAmountKey] = alpha.coerceIn(0.2f, 1f) }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[onboardingCompletedKey] = completed }
    }

    suspend fun isOnboardingCompleted(): Boolean {
        val prefs = context.dataStore.data.first()
        return prefs[onboardingCompletedKey] ?: false
    }

    suspend fun current(): OverlayPreferences {
        val prefs = context.dataStore.data.first()
        return OverlayPreferences(
            enableYouTube = prefs[enableYoutubeKey] ?: true,
            enableYouTubeMusic = prefs[enableYoutubeMusicKey] ?: true,
            startOnBoot = prefs[startOnBootKey] ?: false,
            dimAmount = prefs[dimAmountKey]?.coerceIn(0.2f, 1f) ?: 0.9f
        )
    }
}
