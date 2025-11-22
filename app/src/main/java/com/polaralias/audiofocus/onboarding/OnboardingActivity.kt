package com.polaralias.audiofocus.onboarding

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
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
    private val requestPostNotificationsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.i(TAG, "POST_NOTIFICATIONS permission granted")
                viewModel.onPermissionGranted()
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS permission denied")
                viewModel.onPostNotificationsPermissionDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "OnboardingActivity onCreate - starting initialization")

        try {
            checkIfShouldSkipOnboarding()
            
            setContent {
                AudioFocusTheme {
                    val state by viewModel.uiState.collectAsState()
                    var hasAutoRequestedPostNotifications by rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(state.isOnboardingComplete) {
                        if (state.isOnboardingComplete) {
                            Log.i(TAG, "Onboarding complete, navigating to SettingsActivity")
                            try {
                                val intent = Intent(this@OnboardingActivity, SettingsActivity::class.java)
                                startActivity(intent)
                                Log.d(TAG, "SettingsActivity started successfully")
                                finish()
                                Log.d(TAG, "OnboardingActivity finished")
                            } catch (e: Exception) {
                                Log.e(TAG, "Error navigating to SettingsActivity", e)
                            }
                        }
                    }

                    LaunchedEffect(state.currentStep, state.canPostNotifications) {
                        if (state.currentStep == OnboardingStep.NOTIFICATION) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (!state.canPostNotifications && !hasAutoRequestedPostNotifications) {
                                    Log.d(TAG, "Auto-requesting POST_NOTIFICATIONS permission")
                                    hasAutoRequestedPostNotifications = true
                                    requestPostNotificationPermission()
                                }
                            }
                        } else {
                            hasAutoRequestedPostNotifications = false
                        }
                    }

                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        CompositionLocalProvider(
                            LocalContentColor provides MaterialTheme.colorScheme.onBackground
                        ) {
                            OnboardingScreen(
                                state = state,
                                onRequestOverlay = { openOverlayPermission() },
                                onRequestNotification = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        !state.canPostNotifications
                                    ) {
                                        requestPostNotificationPermission()
                                    } else {
                                        openNotificationAccess()
                                    }
                                },
                                onRequestAccessibility = { openAccessibilitySettings() },
                                onComplete = { viewModel.completeOnboarding() },
                                onContinue = { viewModel.startWelcome() },
                                onRetry = { viewModel.checkPermissionsAndUpdateStep() }
                            )
                        }
                    }
                }
            }
            Log.d(TAG, "OnboardingActivity content set successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during OnboardingActivity onCreate", e)
        }
    }

    private fun checkIfShouldSkipOnboarding() {
        Log.d(TAG, "Initiating onboarding skip check")
        try {
            viewModel.checkIfShouldSkipOnboarding { shouldSkip ->
                Log.i(TAG, "Onboarding skip check callback: shouldSkip=$shouldSkip")
                if (shouldSkip) {
                    Log.i(TAG, "Skipping onboarding, navigating to SettingsActivity")
                    try {
                        val intent = Intent(this@OnboardingActivity, SettingsActivity::class.java)
                        startActivity(intent)
                        Log.d(TAG, "SettingsActivity started successfully from skip")
                        finish()
                        Log.d(TAG, "OnboardingActivity finished after skip")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error navigating to SettingsActivity during skip", e)
                    }
                } else {
                    Log.d(TAG, "Not skipping onboarding - user will go through flow")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during skip check initiation", e)
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "OnboardingActivity onResume - refreshing permissions")
        try {
            viewModel.checkPermissionsAndUpdateStep()
            Log.d(TAG, "Permission check initiated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during onResume permission check", e)
        }
    }

    private fun requestPostNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                requestPostNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                Log.d(TAG, "POST_NOTIFICATIONS permission request launched")
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting POST_NOTIFICATIONS permission", e)
            }
        }
    }

    private fun openOverlayPermission() {
        Log.i(TAG, "Opening overlay permission settings")
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            Log.d(TAG, "Overlay permission settings opened successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening overlay permission settings", e)
        }
    }

    private fun openNotificationAccess() {
        Log.i(TAG, "Opening notification access settings")
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            Log.d(TAG, "Notification access settings opened successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening notification access settings", e)
        }
    }

    private fun openAccessibilitySettings() {
        Log.i(TAG, "Opening accessibility settings")
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Log.d(TAG, "Accessibility settings opened successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error opening accessibility settings", e)
        }
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
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        when (state.currentStep) {
            OnboardingStep.WELCOME -> WelcomeScreen(onContinue = onContinue)
            OnboardingStep.OVERLAY -> PermissionScreen(
                title = stringResource(R.string.onboarding_overlay_title),
                rationale = stringResource(R.string.onboarding_overlay_rationale),
                isGranted = state.hasOverlayPermission,
                showError = state.showError,
                diagnostic = state.permissionDiagnostic,
                onRequest = onRequestOverlay,
                onRetry = onRetry
            )
            OnboardingStep.NOTIFICATION -> PermissionScreen(
                title = stringResource(R.string.onboarding_notification_title),
                rationale = stringResource(R.string.onboarding_notification_rationale),
                isGranted = state.hasNotificationAccess && state.canPostNotifications,
                showError = state.showError,
                diagnostic = state.permissionDiagnostic,
                onRequest = onRequestNotification,
                onRetry = onRetry
            )
            OnboardingStep.ACCESSIBILITY -> PermissionScreen(
                title = stringResource(R.string.onboarding_accessibility_title),
                rationale = stringResource(R.string.onboarding_accessibility_rationale),
                isGranted = state.hasAccessibilityAccess,
                showError = state.showError,
                diagnostic = state.permissionDiagnostic,
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
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
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
    diagnostic: String,
    onRequest: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = rationale,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
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
                text = if (diagnostic.isNotEmpty()) diagnostic else stringResource(R.string.onboarding_error_message),
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
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.onboarding_complete_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_complete_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
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
