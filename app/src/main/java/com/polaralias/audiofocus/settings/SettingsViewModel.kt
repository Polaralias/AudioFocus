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
import kotlinx.coroutines.flow.catch
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
        
        // Asynchronously collect preferences without blocking
        viewModelScope.launch {
            try {
                repository.preferencesFlow
                    .catch { e ->
                        Log.e(TAG, "Error reading preferences", e)
                        // Continue with default preferences on error
                        // Mark loading as complete even on error
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    .collectLatest { prefs ->
                        Log.d(TAG, "Preferences updated: $prefs")
                        _uiState.update { it.copy(preferences = prefs, isLoading = false) }
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Error in preferences collection", e)
                _uiState.update { it.copy(isLoading = false) }
            }
        }
        
        // Refresh permissions asynchronously
        refreshPermissions()
    }

    fun refreshPermissions() {
        Log.d(TAG, "Refreshing permissions")
        viewModelScope.launch {
            try {
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
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing permissions", e)
                // Update with error diagnostic
                _uiState.update {
                    it.copy(permissionDiagnostic = "Error checking permissions: ${e.message}")
                }
            }
        }
    }

    fun setEnableYouTube(enabled: Boolean) {
        Log.d(TAG, "Setting YouTube enabled: $enabled")
        viewModelScope.launch {
            try {
                repository.setEnableYouTube(enabled)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting YouTube enabled", e)
            }
        }
    }

    fun setEnableYouTubeMusic(enabled: Boolean) {
        Log.d(TAG, "Setting YouTube Music enabled: $enabled")
        viewModelScope.launch {
            try {
                repository.setEnableYouTubeMusic(enabled)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting YouTube Music enabled", e)
            }
        }
    }

    fun setStartOnBoot(enabled: Boolean) {
        Log.d(TAG, "Setting start on boot: $enabled")
        viewModelScope.launch {
            try {
                repository.setStartOnBoot(enabled)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting start on boot", e)
            }
        }
    }

    fun setDimAmount(alpha: Float) {
        viewModelScope.launch {
            try {
                repository.setDimAmount(alpha)
            } catch (e: Exception) {
                Log.e(TAG, "Error setting dim amount", e)
            }
        }
    }
}
