package com.polaralias.audiofocus.onboarding

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.polaralias.audiofocus.data.PreferencesRepository
import com.polaralias.audiofocus.util.PermissionValidator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "OnboardingViewModel"
    }

    private val repository = PreferencesRepository(application)
    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        Log.d(TAG, "OnboardingViewModel initialized")
        checkPermissionsAndUpdateStep()
    }

    fun checkIfShouldSkipOnboarding(callback: (Boolean) -> Unit) {
        Log.d(TAG, "Checking if should skip onboarding")
        viewModelScope.launch {
            val isCompleted = repository.isOnboardingCompleted()
            val context = getApplication<Application>()
            val permissionStatus = PermissionValidator.checkPermissions(context, TAG)
            
            val shouldSkip = isCompleted && permissionStatus.allPermissionsGranted
            Log.d(TAG, "Onboarding completed: $isCompleted, All permissions: ${permissionStatus.allPermissionsGranted}, Should skip: $shouldSkip")
            callback(shouldSkip)
        }
    }

    fun checkPermissionsAndUpdateStep() {
        Log.d(TAG, "Checking permissions and updating step")
        val context = getApplication<Application>()
        val permissionStatus = PermissionValidator.checkPermissions(context, TAG)
        
        val currentStep = when {
            !permissionStatus.hasOverlayPermission -> OnboardingStep.OVERLAY
            !permissionStatus.hasNotificationAccess -> OnboardingStep.NOTIFICATION
            !permissionStatus.hasAccessibilityAccess -> OnboardingStep.ACCESSIBILITY
            else -> OnboardingStep.COMPLETE
        }
        
        _uiState.update {
            it.copy(
                currentStep = currentStep,
                hasOverlayPermission = permissionStatus.hasOverlayPermission,
                hasNotificationAccess = permissionStatus.hasNotificationAccess,
                hasAccessibilityAccess = permissionStatus.hasAccessibilityAccess,
                showError = false
            )
        }
        
        Log.d(TAG, "Current step: $currentStep, All granted: ${permissionStatus.allPermissionsGranted}")
    }

    fun onPermissionGranted() {
        Log.d(TAG, "Permission granted, moving to next step")
        _uiState.update { it.copy(showError = false) }
        checkPermissionsAndUpdateStep()
    }

    fun onPermissionDenied() {
        Log.w(TAG, "Permission denied")
        _uiState.update { it.copy(showError = true) }
    }

    fun completeOnboarding() {
        Log.i(TAG, "Completing onboarding")
        viewModelScope.launch {
            repository.setOnboardingCompleted(true)
            _uiState.update { it.copy(isOnboardingComplete = true) }
        }
    }

    fun startWelcome() {
        Log.d(TAG, "Starting from welcome screen")
        _uiState.update { it.copy(currentStep = OnboardingStep.OVERLAY) }
    }
}

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val hasOverlayPermission: Boolean = false,
    val hasNotificationAccess: Boolean = false,
    val hasAccessibilityAccess: Boolean = false,
    val showError: Boolean = false,
    val isOnboardingComplete: Boolean = false
)

enum class OnboardingStep {
    WELCOME,
    OVERLAY,
    NOTIFICATION,
    ACCESSIBILITY,
    COMPLETE
}
