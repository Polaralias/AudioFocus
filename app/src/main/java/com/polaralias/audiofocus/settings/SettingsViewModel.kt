package com.polaralias.audiofocus.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.polaralias.audiofocus.data.PreferencesRepository
import com.polaralias.audiofocus.util.PermissionValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "SettingsViewModel"
    }

    private val repository = PreferencesRepository(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "SettingsViewModel initialized")
        viewModelScope.launch {
            repository.preferencesFlow.collectLatest { prefs ->
                _uiState.update { it.copy(preferences = prefs) }
            }
        }
        refreshPermissions()
    }

    fun refreshPermissions() {
        Log.d(TAG, "Refreshing permissions")
        val context = getApplication<Application>()
        val permissionStatus = PermissionValidator.checkPermissions(context, TAG)
        
        _uiState.update {
            it.copy(
                hasOverlayPermission = permissionStatus.hasOverlayPermission,
                hasNotificationAccess = permissionStatus.hasNotificationAccess,
                hasAccessibilityAccess = permissionStatus.hasAccessibilityAccess,
                permissionDiagnostic = permissionStatus.getDiagnosticMessage()
            )
        }
        
        if (!permissionStatus.allPermissionsGranted) {
            Log.w(TAG, "Missing permissions: ${permissionStatus.getDiagnosticMessage()}")
        } else {
            Log.i(TAG, "All permissions granted")
        }
    }

    fun setEnableYouTube(enabled: Boolean) {
        Log.d(TAG, "Setting YouTube enabled: $enabled")
        viewModelScope.launch { repository.setEnableYouTube(enabled) }
    }

    fun setEnableYouTubeMusic(enabled: Boolean) {
        Log.d(TAG, "Setting YouTube Music enabled: $enabled")
        viewModelScope.launch { repository.setEnableYouTubeMusic(enabled) }
    }

    fun setStartOnBoot(enabled: Boolean) {
        Log.d(TAG, "Setting start on boot: $enabled")
        viewModelScope.launch { repository.setStartOnBoot(enabled) }
    }

    fun setDimAmount(alpha: Float) {
        viewModelScope.launch { repository.setDimAmount(alpha) }
    }
}
