package com.audiofocus.app.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import com.audiofocus.app.domain.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val permissionManager: PermissionManager
) : ViewModel() {

    fun checkPermissions(
        onOverlayMissing: () -> Unit,
        onAccessibilityMissing: () -> Unit,
        onNotificationMissing: () -> Unit,
        onUsageStatsMissing: () -> Unit,
        onAllGranted: () -> Unit
    ) {
        when {
            !permissionManager.hasOverlayPermission() -> onOverlayMissing()
            !permissionManager.hasAccessibilityPermission() -> onAccessibilityMissing()
            !permissionManager.hasNotificationListenerPermission() -> onNotificationMissing()
            !permissionManager.hasUsageStatsPermission() -> onUsageStatsMissing()
            else -> onAllGranted()
        }
    }

    fun requestOverlayPermission(context: android.content.Context) {
        context.startActivity(permissionManager.getOverlayPermissionIntent())
    }

    fun requestAccessibilityPermission(context: android.content.Context) {
        context.startActivity(permissionManager.getAccessibilityPermissionIntent())
    }

    fun requestNotificationPermission(context: android.content.Context) {
        context.startActivity(permissionManager.getNotificationListenerPermissionIntent())
    }

    fun requestUsageStatsPermission(context: android.content.Context) {
        context.startActivity(permissionManager.getUsageStatsPermissionIntent())
    }
}

@Composable
fun OnboardingScreen(
    onOnboardingComplete: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var currentStep by remember { mutableStateOf(OnboardingStep.WELCOME) }

    // Check permissions whenever the app resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions(
                    onOverlayMissing = { currentStep = OnboardingStep.OVERLAY_PERMISSION },
                    onAccessibilityMissing = { currentStep = OnboardingStep.ACCESSIBILITY_PERMISSION },
                    onNotificationMissing = { currentStep = OnboardingStep.NOTIFICATION_PERMISSION },
                    onUsageStatsMissing = { currentStep = OnboardingStep.USAGE_STATS_PERMISSION },
                    onAllGranted = {
                        // Only auto-advance if we were in a permission step
                        if (currentStep != OnboardingStep.WELCOME) {
                            onOnboardingComplete()
                        }
                    }
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when (currentStep) {
                OnboardingStep.WELCOME -> WelcomeStep(onNext = {
                     // Check permissions to determine next step
                     viewModel.checkPermissions(
                        onOverlayMissing = { currentStep = OnboardingStep.OVERLAY_PERMISSION },
                        onAccessibilityMissing = { currentStep = OnboardingStep.ACCESSIBILITY_PERMISSION },
                        onNotificationMissing = { currentStep = OnboardingStep.NOTIFICATION_PERMISSION },
                        onUsageStatsMissing = { currentStep = OnboardingStep.USAGE_STATS_PERMISSION },
                        onAllGranted = { onOnboardingComplete() }
                     )
                })
                OnboardingStep.OVERLAY_PERMISSION -> PermissionStep(
                    title = "Display Over Other Apps",
                    description = "Audio Focus needs to display content over other apps to show the overlay controls.",
                    buttonText = "Grant Permission",
                    onGrant = { viewModel.requestOverlayPermission(context) }
                )
                OnboardingStep.ACCESSIBILITY_PERMISSION -> PermissionStep(
                    title = "Accessibility Service",
                    description = "We use Accessibility Services to detect when you are using media apps like YouTube. This allows us to show the overlay automatically.",
                    buttonText = "Open Settings",
                    onGrant = { viewModel.requestAccessibilityPermission(context) }
                )
                OnboardingStep.NOTIFICATION_PERMISSION -> PermissionStep(
                    title = "Notification Access",
                    description = "To control media playback (play/pause), we need access to media notifications.",
                    buttonText = "Allow Access",
                    onGrant = { viewModel.requestNotificationPermission(context) }
                )
                OnboardingStep.USAGE_STATS_PERMISSION -> PermissionStep(
                    title = "Usage Stats Access",
                    description = "We need to know which app is currently in the foreground to show the overlay only when relevant.",
                    buttonText = "Allow Access",
                    onGrant = { viewModel.requestUsageStatsPermission(context) }
                )
            }
        }
    }
}

enum class OnboardingStep {
    WELCOME,
    OVERLAY_PERMISSION,
    ACCESSIBILITY_PERMISSION,
    NOTIFICATION_PERMISSION,
    USAGE_STATS_PERMISSION
}

@Composable
fun WelcomeStep(onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Welcome to Audio Focus",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Enhance your music experience on YouTube and other apps with a persistent overlay control.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onNext) {
            Text("Get Started")
        }
    }
}

@Composable
fun PermissionStep(
    title: String,
    description: String,
    buttonText: String,
    onGrant: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onGrant) {
                Text(buttonText)
            }
        }
    }
}
