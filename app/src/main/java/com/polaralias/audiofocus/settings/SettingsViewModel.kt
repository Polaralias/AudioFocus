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
        Log.i(TAG, "SettingsViewModel initialized - starting async data loading")
        
        // Asynchronously collect preferences without blocking
        viewModelScope.launch {
            try {
                Log.d(TAG, "Starting preferences collection from DataStore")
                repository.preferencesFlow
                    .catch { e ->
                        Log.e(TAG, "Error reading preferences from DataStore", e)
                        // Flow already emits default preferences on error via PreferencesRepository
                        // Mark loading as complete even on error to unblock UI
                        _uiState.update { it.copy(isLoading = false) }
                    }
                    .collectLatest { prefs ->
                        Log.d(TAG, "Preferences received: enableYouTube=${prefs.enableYouTube}, enableYouTubeMusic=${prefs.enableYouTubeMusic}, startOnBoot=${prefs.startOnBoot}, dimAmount=${prefs.dimAmount}")
                        _uiState.update { it.copy(preferences = prefs, isLoading = false) }
                        Log.d(TAG, "UI state updated with preferences, isLoading=false")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Fatal error in preferences collection", e)
                // Update state with defaults and mark as not loading to keep UI responsive
                _uiState.update { it.copy(isLoading = false) }
            }
        }
        
        // Refresh permissions asynchronously
        Log.d(TAG, "Initiating initial permission refresh")
        refreshPermissions()
    }

    fun refreshPermissions() {
        Log.i(TAG, "Refreshing permissions - starting async check")
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                Log.d(TAG, "Checking permissions via PermissionValidator")
                val permissionStatus = PermissionValidator.checkPermissions(context, TAG)
                
                Log.i(TAG, "Permission check completed: ${permissionStatus.getDiagnosticMessage()}")
                
                _uiState.update {
                    it.copy(
                        hasOverlayPermission = permissionStatus.hasOverlayPermission,
                        hasNotificationAccess = permissionStatus.hasNotificationAccess,
                        hasAccessibilityAccess = permissionStatus.hasAccessibilityAccess,
                        permissionDiagnostic = permissionStatus.getDiagnosticMessage()
                    )
                }
                
                if (!permissionStatus.allPermissionsGranted) {
                    Log.w(TAG, "Missing permissions detected: ${permissionStatus.getDiagnosticMessage()}")
                } else {
                    Log.i(TAG, "All permissions granted")
                }
                
                Log.d(TAG, "UI state updated with permission status")
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing permissions", e)
                // Update with error diagnostic but keep UI responsive
                _uiState.update {
                    it.copy(permissionDiagnostic = "Error checking permissions: ${e.message}")
                }
            }
        }
    }

    fun setEnableYouTube(enabled: Boolean) {
        Log.i(TAG, "User toggled YouTube: enabled=$enabled")
        viewModelScope.launch {
            try {
                repository.setEnableYouTube(enabled)
                Log.d(TAG, "YouTube setting saved successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting YouTube enabled to $enabled", e)
                // Don't crash - just log the error
            }
        }
    }

    fun setEnableYouTubeMusic(enabled: Boolean) {
        Log.i(TAG, "User toggled YouTube Music: enabled=$enabled")
        viewModelScope.launch {
            try {
                repository.setEnableYouTubeMusic(enabled)
                Log.d(TAG, "YouTube Music setting saved successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting YouTube Music enabled to $enabled", e)
                // Don't crash - just log the error
            }
        }
    }

    fun setStartOnBoot(enabled: Boolean) {
        Log.i(TAG, "User toggled start on boot: enabled=$enabled")
        viewModelScope.launch {
            try {
                repository.setStartOnBoot(enabled)
                Log.d(TAG, "Start on boot setting saved successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting start on boot to $enabled", e)
                // Don't crash - just log the error
            }
        }
    }

    fun setDimAmount(alpha: Float) {
        Log.d(TAG, "User adjusted dim amount: alpha=$alpha")
        viewModelScope.launch {
            try {
                repository.setDimAmount(alpha)
                // Don't log every successful dim change to reduce noise
            } catch (e: Exception) {
                Log.e(TAG, "Error setting dim amount to $alpha", e)
                // Don't crash - just log the error
            }
        }
    }
}
