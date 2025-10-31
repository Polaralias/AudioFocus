package com.polaralias.audiofocus.settings

import android.app.Application
import android.content.ComponentName
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.polaralias.audiofocus.data.PreferencesRepository
import com.polaralias.audiofocus.service.AccessWindowsService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PreferencesRepository(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.preferencesFlow.collectLatest { prefs ->
                _uiState.update { it.copy(preferences = prefs) }
            }
        }
        refreshPermissions()
    }

    fun refreshPermissions() {
        val context = getApplication<Application>()
        val overlay = Settings.canDrawOverlays(context)
        val notification = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        val accessibility = isAccessibilityEnabled(context)
        _uiState.update {
            it.copy(
                hasOverlayPermission = overlay,
                hasNotificationAccess = notification,
                hasAccessibilityAccess = accessibility
            )
        }
    }

    fun setEnableYouTube(enabled: Boolean) {
        viewModelScope.launch { repository.setEnableYouTube(enabled) }
    }

    fun setEnableYouTubeMusic(enabled: Boolean) {
        viewModelScope.launch { repository.setEnableYouTubeMusic(enabled) }
    }

    fun setStartOnBoot(enabled: Boolean) {
        viewModelScope.launch { repository.setStartOnBoot(enabled) }
    }

    fun setDimAmount(alpha: Float) {
        viewModelScope.launch { repository.setDimAmount(alpha) }
    }

    private fun isAccessibilityEnabled(context: Application): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val component = ComponentName(context, AccessWindowsService::class.java)
        return enabledServices.split(":").any { it.equals(component.flattenToString(), ignoreCase = true) }
    }
}
