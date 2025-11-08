package com.polaralias.audiofocus.settings

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor
import com.polaralias.audiofocus.R
import com.polaralias.audiofocus.service.OverlayService
import com.polaralias.audiofocus.service.OverlayServiceState
import com.polaralias.audiofocus.ui.theme.AudioFocusTheme
import kotlinx.coroutines.delay

class SettingsActivity : ComponentActivity() {
    companion object {
        private const val TAG = "SettingsActivity"
    }

    private val viewModel: SettingsViewModel by viewModels()
    private val requestPostNotificationsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Log.i(TAG, "POST_NOTIFICATIONS permission granted in settings")
                viewModel.refreshPermissions()
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS permission denied in settings")
                viewModel.onPostNotificationsPermissionDenied()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "SettingsActivity onCreate - starting initialization")
        
        try {
            setContent {
                AudioFocusTheme {
                    // Wrap entire screen in Surface with Material3 colors to ensure proper contrast
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        // Force LocalContentColor to ensure all text uses proper contrast
                        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
                            val state by viewModel.uiState.collectAsState()
                            val activity = this@SettingsActivity
                            val hasStarted = remember { mutableStateOf(false) }
                            
                            LaunchedEffect(
                                state.hasOverlayPermission,
                                state.hasAccessibilityAccess,
                                state.hasNotificationAccess,
                                state.canPostNotifications,
                                state.notificationListenerConnected
                            ) {
                                try {
                                    val ready =
                                        state.hasOverlayPermission &&
                                            state.hasAccessibilityAccess &&
                                            state.hasNotificationAccess &&
                                            state.canPostNotifications
                                    val listenerConnected = state.notificationListenerConnected

                                    Log.d(
                                        TAG,
                                        "Permission state changed - ready=$ready, " +
                                            "listenerConnected=$listenerConnected, hasStarted=${hasStarted.value}"
                                    )
                                    Log.d(
                                        TAG,
                                        "Permissions: overlay=${state.hasOverlayPermission}, accessibility=${state.hasAccessibilityAccess}, notificationAccess=${state.hasNotificationAccess}, canPost=${state.canPostNotifications}"
                                    )

                                    if (ready && listenerConnected && !hasStarted.value) {
                                        Log.i(TAG, "All permissions and notification listener ready, starting OverlayService")
                                        hasStarted.value = true
                                        try {
                                            delay(300)
                                            OverlayService.start(activity)
                                            Log.d(TAG, "OverlayService start command sent successfully")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error starting OverlayService", e)
                                            // Don't crash - service may recover or user can retry
                                        }
                                    } else if (ready && !listenerConnected) {
                                        Log.i(
                                            TAG,
                                            "All permissions granted but notification listener not connected yet; waiting before starting"
                                        )
                                    } else if (!ready && hasStarted.value) {
                                        Log.w(TAG, "Permissions revoked, stopping OverlayService")
                                        hasStarted.value = false
                                        try {
                                            OverlayService.stop(activity)
                                            Log.d(TAG, "OverlayService stop command sent successfully")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error stopping OverlayService", e)
                                            // Don't crash - service may already be stopped
                                        }
                                    } else if (!ready) {
                                        Log.w(TAG, "Cannot start service - missing permissions: ${state.permissionDiagnostic}")
                                        hasStarted.value = false
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error in LaunchedEffect permission handling", e)
                                    // Don't crash - keep UI responsive
                                }
                            }
                            
                            SettingsScreen(
                                state = state,
                                onToggleYouTube = viewModel::setEnableYouTube,
                                onToggleYouTubeMusic = viewModel::setEnableYouTubeMusic,
                                onToggleStartOnBoot = viewModel::setStartOnBoot,
                                onDimAmountChange = viewModel::setDimAmount,
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
                                onRequestAccessibility = { openAccessibilitySettings() }
                            )
                        }
                    }
                }
            }
            Log.d(TAG, "SettingsActivity content set successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during SettingsActivity onCreate", e)
            // Don't crash - Compose will render with default state
        }
    }

    override fun onResume() {
        super.onResume()
        Log.i(TAG, "SettingsActivity onResume - refreshing permissions")
        try {
            viewModel.refreshPermissions()
            Log.d(TAG, "Permission refresh initiated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during onResume permission refresh", e)
        }
    }

    private fun requestPostNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                requestPostNotificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                Log.d(TAG, "POST_NOTIFICATIONS permission request launched from settings")
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting POST_NOTIFICATIONS permission in settings", e)
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
private fun SettingsScreen(
    state: SettingsUiState,
    onToggleYouTube: (Boolean) -> Unit,
    onToggleYouTubeMusic: (Boolean) -> Unit,
    onToggleStartOnBoot: (Boolean) -> Unit,
    onDimAmountChange: (Float) -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestAccessibility: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            text = stringResource(id = R.string.settings_title), 
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        // Show loading message or description based on loading state
        if (state.isLoading) {
            Text(
                text = stringResource(id = R.string.settings_loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        } else {
            Text(
                text = stringResource(id = R.string.settings_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        val overlayStatus = state.overlayServiceStatus
        val baseStatusMessage = when (overlayStatus.state) {
            OverlayServiceState.STARTING -> stringResource(id = R.string.overlay_status_starting)
            OverlayServiceState.RUNNING -> stringResource(id = R.string.overlay_status_running)
            OverlayServiceState.WAITING_FOR_MEDIA -> stringResource(id = R.string.overlay_status_waiting)
            OverlayServiceState.STOPPED -> stringResource(id = R.string.overlay_status_stopped)
            OverlayServiceState.ERROR -> stringResource(id = R.string.overlay_status_error_generic)
        }
        val statusText = if (overlayStatus.state == OverlayServiceState.ERROR) {
            overlayStatus.detail?.takeIf { it.isNotBlank() } ?: baseStatusMessage
        } else {
            baseStatusMessage
        }
        val statusColor = if (overlayStatus.state == OverlayServiceState.ERROR) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onBackground
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor
        )

        if (!state.isLoading) {
            // Display permission diagnostic if any permissions are missing
            if (state.permissionDiagnostic.isNotEmpty() &&
                (!state.hasOverlayPermission ||
                    !state.hasNotificationAccess ||
                    !state.canPostNotifications ||
                    !state.hasAccessibilityAccess)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "⚠️ Permission Status",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = state.permissionDiagnostic,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (state.serviceDiagnostic.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "⚠️ Service Status",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = state.serviceDiagnostic,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            ToggleRow(
                title = stringResource(id = R.string.toggle_youtube),
                checked = state.preferences.enableYouTube,
                onToggle = onToggleYouTube
            )
            ToggleRow(
                title = stringResource(id = R.string.toggle_youtube_music),
                checked = state.preferences.enableYouTubeMusic,
                onToggle = onToggleYouTubeMusic
            )
            ToggleRow(
                title = stringResource(id = R.string.toggle_boot),
                checked = state.preferences.startOnBoot,
                onToggle = onToggleStartOnBoot
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(id = R.string.dim_amount),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Slider(
                    value = state.preferences.dimAmount,
                    onValueChange = onDimAmountChange,
                    valueRange = 0.2f..1f,
                    steps = 7
                )
                Text(
                    text = String.format("%.0f%%", state.preferences.dimAmount * 100f),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            PermissionRow(
                title = stringResource(id = R.string.permission_overlay),
                granted = state.hasOverlayPermission,
                onClick = onRequestOverlay
            )
            PermissionRow(
                title = stringResource(id = R.string.permission_notification),
                granted = state.hasNotificationAccess && state.canPostNotifications,
                onClick = onRequestNotification
            )
            PermissionRow(
                title = stringResource(id = R.string.permission_accessibility),
                granted = state.hasAccessibilityAccess,
                onClick = onRequestAccessibility
            )
            Text(
                text = stringResource(id = R.string.touch_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(modifier = Modifier.weight(1f, fill = true))
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title, 
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title, 
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (granted) stringResource(id = R.string.permission_status_granted) else stringResource(id = R.string.permission_status_denied),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
            )
        }
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(text = title)
        }
    }
}

@Composable
private fun stringResource(id: Int): String = androidx.compose.ui.res.stringResource(id)
