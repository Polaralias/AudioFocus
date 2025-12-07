package com.audiofocus.app.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.audiofocus.app.core.model.AppSettings
import com.audiofocus.app.core.model.OverlaySettings
import com.audiofocus.app.core.model.ThemeConfig
import com.audiofocus.app.core.model.ThemeType
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
        val MONITORING_ENABLED = booleanPreferencesKey("monitoring_enabled")

        // Legacy keys mapped to general/YouTube theme
        val BLUR_ENABLED = booleanPreferencesKey("blur_enabled")
        val BACKGROUND_COLOR = longPreferencesKey("background_color")

        // YouTube Theme
        val YT_THEME_TYPE = stringPreferencesKey("yt_theme_type")
        val YT_THEME_COLOR = intPreferencesKey("yt_theme_color")
        val YT_THEME_IMAGE = stringPreferencesKey("yt_theme_image")
        val YT_THEME_BLUR = intPreferencesKey("yt_theme_blur")

        // YouTube Music Theme
        val YTM_THEME_TYPE = stringPreferencesKey("ytm_theme_type")
        val YTM_THEME_COLOR = intPreferencesKey("ytm_theme_color")
        val YTM_THEME_IMAGE = stringPreferencesKey("ytm_theme_image")
        val YTM_THEME_BLUR = intPreferencesKey("ytm_theme_blur")
    }

    override val appSettings: Flow<AppSettings> = context.dataStore.data
        .map { preferences ->
            val monitoringEnabled = preferences[PreferencesKeys.MONITORING_ENABLED] ?: true

            // Map legacy keys if new ones are missing, or use defaults
            val legacyBlur = preferences[PreferencesKeys.BLUR_ENABLED] ?: true
            val legacyColor = preferences[PreferencesKeys.BACKGROUND_COLOR] ?: 0xFF000000

            val ytTheme = ThemeConfig(
                type = ThemeType.valueOf(preferences[PreferencesKeys.YT_THEME_TYPE] ?: ThemeType.SOLID.name),
                color = preferences[PreferencesKeys.YT_THEME_COLOR] ?: legacyColor.toInt(),
                imageUri = preferences[PreferencesKeys.YT_THEME_IMAGE],
                blurLevel = preferences[PreferencesKeys.YT_THEME_BLUR] ?: (if (legacyBlur) 1 else 0)
            )

            val ytmTheme = ThemeConfig(
                type = ThemeType.valueOf(preferences[PreferencesKeys.YTM_THEME_TYPE] ?: ThemeType.SOLID.name),
                color = preferences[PreferencesKeys.YTM_THEME_COLOR] ?: legacyColor.toInt(),
                imageUri = preferences[PreferencesKeys.YTM_THEME_IMAGE],
                blurLevel = preferences[PreferencesKeys.YTM_THEME_BLUR] ?: (if (legacyBlur) 1 else 0)
            )

            AppSettings(
                isMonitoringEnabled = monitoringEnabled,
                youtubeTheme = ytTheme,
                youtubeMusicTheme = ytmTheme
            )
        }

    override val overlaySettings: Flow<OverlaySettings> = appSettings.map { appSettings ->
        // Map back to OverlaySettings for compatibility.
        // We'll use YouTube theme as the default "Global" theme for now if needed,
        // or just return a generic one. But better to use the specific one based on context.
        // However, OverlayManager might just need a default.
        OverlaySettings(
            isBlurEnabled = appSettings.youtubeTheme.blurLevel > 0,
            backgroundColor = appSettings.youtubeTheme.color.toLong()
        )
    }

    override suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.MONITORING_ENABLED] = enabled
        }
    }

    override suspend fun setBlurEnabled(enabled: Boolean) {
        // Legacy support: update YT theme
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BLUR_ENABLED] = enabled
            preferences[PreferencesKeys.YT_THEME_BLUR] = if (enabled) 1 else 0
        }
    }

    override suspend fun setBackgroundColor(color: Long) {
        // Legacy support: update YT theme
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.BACKGROUND_COLOR] = color
            preferences[PreferencesKeys.YT_THEME_COLOR] = color.toInt()
        }
    }

    override suspend fun setYoutubeTheme(theme: ThemeConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.YT_THEME_TYPE] = theme.type.name
            preferences[PreferencesKeys.YT_THEME_COLOR] = theme.color
            if (theme.imageUri != null) {
                preferences[PreferencesKeys.YT_THEME_IMAGE] = theme.imageUri
            } else {
                preferences.remove(PreferencesKeys.YT_THEME_IMAGE)
            }
            preferences[PreferencesKeys.YT_THEME_BLUR] = theme.blurLevel
        }
    }

    override suspend fun setYoutubeMusicTheme(theme: ThemeConfig) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.YTM_THEME_TYPE] = theme.type.name
            preferences[PreferencesKeys.YTM_THEME_COLOR] = theme.color
            if (theme.imageUri != null) {
                preferences[PreferencesKeys.YTM_THEME_IMAGE] = theme.imageUri
            } else {
                preferences.remove(PreferencesKeys.YTM_THEME_IMAGE)
            }
            preferences[PreferencesKeys.YTM_THEME_BLUR] = theme.blurLevel
        }
    }
}
