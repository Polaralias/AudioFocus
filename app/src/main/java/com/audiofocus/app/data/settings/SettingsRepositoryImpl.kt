package com.audiofocus.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.audiofocus.app.core.model.OverlaySettings
import com.audiofocus.app.domain.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : SettingsRepository {

    private object PreferencesKeys {
        val BLUR_ENABLED = booleanPreferencesKey("blur_enabled")
        val BACKGROUND_COLOR = longPreferencesKey("background_color")
    }

    override val overlaySettings: Flow<OverlaySettings> = context.dataStore.data
        .map { preferences ->
            OverlaySettings(
                isBlurEnabled = preferences[PreferencesKeys.BLUR_ENABLED] ?: true,
                backgroundColor = preferences[PreferencesKeys.BACKGROUND_COLOR] ?: 0xFF000000
            )
        }

    override suspend fun setBlurEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLUR_ENABLED] = enabled
        }
    }

    override suspend fun setBackgroundColor(color: Long) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKGROUND_COLOR] = color
        }
    }
}
