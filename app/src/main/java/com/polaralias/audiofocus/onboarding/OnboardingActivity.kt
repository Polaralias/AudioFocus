package com.polaralias.audiofocus.onboarding

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.polaralias.audiofocus.R
import com.polaralias.audiofocus.settings.SettingsActivity
import com.polaralias.audiofocus.ui.theme.AudioFocusTheme

class OnboardingActivity : ComponentActivity() {
    companion object {
        private const val TAG = "OnboardingActivity"
    }

    private val viewModel: OnboardingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "OnboardingActivity created")
        
        // Check if we should skip onboarding
        checkIfShouldSkipOnboarding()
        
        setContent {
            AudioFocusTheme {
                val state by viewModel.uiState.collectAsState()
                
                LaunchedEffect(state.isOnboardingComplete) {
                    if (state.isOnboardingComplete) {
                        Log.i(TAG, "Onboarding complete, navigating to SettingsActivity")
                        startActivity(Intent(this@OnboardingActivity, SettingsActivity::class.java))
                        finish()
                    }
                }
                
                OnboardingScreen(
                    state = state,
                    onRequestOverlay = { openOverlayPermission() },
                    onRequestNotification = { openNotificationAccess() },
                    onRequestAccessibility = { openAccessibilitySettings() },
                    onComplete = { viewModel.completeOnboarding() },
                    onContinue = { viewModel.startWelcome() },
                    onRetry = { viewModel.checkPermissionsAndUpdateStep() }
                )
            }
        }
    }

    private fun checkIfShouldSkipOnboarding() {
        viewModel.checkIfShouldSkipOnboarding { shouldSkip ->
            if (shouldSkip) {
                Log.i(TAG, "Skipping onboarding, all permissions granted")
                startActivity(Intent(this@OnboardingActivity, SettingsActivity::class.java))
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "OnboardingActivity resumed, checking permissions")
        viewModel.checkPermissionsAndUpdateStep()
    }

    private fun openOverlayPermission() {
        Log.d(TAG, "Opening overlay permission settings")
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }

    private fun openNotificationAccess() {
        Log.d(TAG, "Opening notification access settings")
        startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
    }

    private fun openAccessibilitySettings() {
        Log.d(TAG, "Opening accessibility settings")
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }
}

@Composable
private fun OnboardingScreen(
    state: OnboardingUiState,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onComplete: () -> Unit,
    onContinue: () -> Unit,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        when (state.currentStep) {
            OnboardingStep.WELCOME -> WelcomeScreen(onContinue = onContinue)
            OnboardingStep.OVERLAY -> PermissionScreen(
                title = stringResource(R.string.onboarding_overlay_title),
                rationale = stringResource(R.string.onboarding_overlay_rationale),
                isGranted = state.hasOverlayPermission,
                showError = state.showError,
                onRequest = onRequestOverlay,
                onRetry = onRetry
            )
            OnboardingStep.NOTIFICATION -> PermissionScreen(
                title = stringResource(R.string.onboarding_notification_title),
                rationale = stringResource(R.string.onboarding_notification_rationale),
                isGranted = state.hasNotificationAccess,
                showError = state.showError,
                onRequest = onRequestNotification,
                onRetry = onRetry
            )
            OnboardingStep.ACCESSIBILITY -> PermissionScreen(
                title = stringResource(R.string.onboarding_accessibility_title),
                rationale = stringResource(R.string.onboarding_accessibility_rationale),
                isGranted = state.hasAccessibilityAccess,
                showError = state.showError,
                onRequest = onRequestAccessibility,
                onRetry = onRetry
            )
            OnboardingStep.COMPLETE -> CompleteScreen(onComplete = onComplete)
        }
    }
}

@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.onboarding_continue))
        }
    }
}

@Composable
private fun PermissionScreen(
    title: String,
    rationale: String,
    isGranted: Boolean,
    showError: Boolean,
    onRequest: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = rationale,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        if (showError) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.onboarding_error_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_error_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (isGranted) {
            Text(
                text = stringResource(R.string.onboarding_permission_granted),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
        } else {
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.onboarding_grant_permission))
            }
            
            if (showError) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = stringResource(R.string.onboarding_retry))
                }
            }
        }
    }
}

@Composable
private fun CompleteScreen(onComplete: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.onboarding_complete_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_complete_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.onboarding_finish))
        }
    }
}
