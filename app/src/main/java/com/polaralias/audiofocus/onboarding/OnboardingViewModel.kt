package com.polaralias.audiofocus.onboarding

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.polaralias.audiofocus.R
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
        try {
            checkPermissionsAndUpdateStep()
        } catch (e: Exception) {
            Log.e(TAG, "Error during ViewModel initialization", e)
            _uiState.update { it.copy(showError = true) }
        }
    }

    /**
     * Check if onboarding should be skipped asynchronously.
     * Returns a StateFlow that emits the skip decision.
     * This prevents blocking the UI thread during the check.
     */
    fun checkIfShouldSkipOnboarding(callback: (Boolean) -> Unit) {
        Log.d(TAG, "Checking if should skip onboarding - async operation started")
        viewModelScope.launch {
            try {
                // Defensive: Run all checks in background to avoid blocking
                val isCompleted = repository.isOnboardingCompleted()
                Log.d(TAG, "Onboarding completed status: $isCompleted")
                
                val context = getApplication<Application>()
                val permissionStatus = PermissionValidator.checkPermissions(context, TAG)
                Log.d(TAG, "Permission check completed: ${permissionStatus.getDiagnosticMessage()}")
                
                val shouldSkip = isCompleted && permissionStatus.allPermissionsGranted
                Log.i(TAG, "Skip onboarding decision: $shouldSkip (completed=$isCompleted, allPermissions=${permissionStatus.allPermissionsGranted})")
                
                callback(shouldSkip)
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if should skip onboarding", e)
                // On error, don't skip onboarding to be safe - user can still proceed through flow
                Log.w(TAG, "Defaulting to NOT skipping onboarding due to error")
                callback(false)
            }
        }
    }

    fun checkPermissionsAndUpdateStep() {
        Log.d(TAG, "Checking permissions and updating step - starting async operation")
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                Log.d(TAG, "Performing permission check...")
                val permissionStatus = PermissionValidator.checkPermissions(context, TAG)
                
                val currentStep = when {
                    !permissionStatus.hasOverlayPermission -> OnboardingStep.OVERLAY
                    !permissionStatus.hasNotificationAccess || !permissionStatus.canPostNotifications ->
                        OnboardingStep.NOTIFICATION
                    !permissionStatus.hasAccessibilityAccess -> OnboardingStep.ACCESSIBILITY
                    else -> OnboardingStep.COMPLETE
                }

                Log.i(TAG, "Permission check result - Step: $currentStep, Status: ${permissionStatus.getDiagnosticMessage()}")

                _uiState.update {
                    it.copy(
                        currentStep = currentStep,
                        hasOverlayPermission = permissionStatus.hasOverlayPermission,
                        hasNotificationAccess = permissionStatus.hasNotificationAccess,
                        canPostNotifications = permissionStatus.canPostNotifications,
                        hasAccessibilityAccess = permissionStatus.hasAccessibilityAccess,
                        showError = false,
                        permissionDiagnostic = if (permissionStatus.allPermissionsGranted) {
                            ""
                        } else {
                            permissionStatus.getDiagnosticMessage()
                        }
                    )
                }
                
                Log.d(TAG, "UI state updated successfully with step: $currentStep")
            } catch (e: Exception) {
                Log.e(TAG, "Error checking permissions and updating step", e)
                // Update UI to show error but keep it responsive
                _uiState.update { it.copy(showError = true) }
            }
        }
    }

    fun onPermissionGranted() {
        Log.d(TAG, "Permission granted, moving to next step")
        _uiState.update { it.copy(showError = false) }
        checkPermissionsAndUpdateStep()
    }

    fun onPermissionDenied() {
        Log.w(TAG, "Permission denied")
        val context = getApplication<Application>()
        _uiState.update {
            it.copy(
                showError = true,
                permissionDiagnostic = context.getString(R.string.onboarding_error_message)
            )
        }
    }

    fun onPostNotificationsPermissionDenied() {
        Log.w(TAG, "POST_NOTIFICATIONS permission denied")
        val context = getApplication<Application>()
        _uiState.update {
            it.copy(
                showError = true,
                permissionDiagnostic = context.getString(R.string.post_notification_permission_required)
            )
        }
    }

    fun completeOnboarding() {
        Log.i(TAG, "Completing onboarding - starting async operation")
        viewModelScope.launch {
            try {
                Log.d(TAG, "Setting onboarding completed flag in DataStore...")
                repository.setOnboardingCompleted(true)
                Log.i(TAG, "Onboarding completed flag set successfully")
                
                // Update UI state to trigger navigation
                _uiState.update { it.copy(isOnboardingComplete = true) }
                Log.d(TAG, "UI state updated - isOnboardingComplete=true")
            } catch (e: Exception) {
                Log.e(TAG, "Error completing onboarding in DataStore", e)
                // Still mark as complete in UI to allow user to proceed
                // On next app start, we'll check permissions again to determine skip status
                Log.w(TAG, "Proceeding with navigation despite DataStore error")
                _uiState.update { it.copy(isOnboardingComplete = true) }
            }
        }
    }

    fun startWelcome() {
        Log.i(TAG, "User continuing from welcome screen")
        try {
            _uiState.update { it.copy(currentStep = OnboardingStep.OVERLAY) }
            Log.d(TAG, "UI state updated - moved to OVERLAY step")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating state from welcome", e)
        }
    }
}

data class OnboardingUiState(
    val currentStep: OnboardingStep = OnboardingStep.WELCOME,
    val hasOverlayPermission: Boolean = false,
    val hasNotificationAccess: Boolean = false,
    val canPostNotifications: Boolean = false,
    val hasAccessibilityAccess: Boolean = false,
    val showError: Boolean = false,
    val permissionDiagnostic: String = "",
    val isOnboardingComplete: Boolean = false
)

enum class OnboardingStep {
    WELCOME,
    OVERLAY,
    NOTIFICATION,
    ACCESSIBILITY,
    COMPLETE
}
