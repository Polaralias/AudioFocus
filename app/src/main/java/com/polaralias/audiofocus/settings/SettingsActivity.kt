package com.polaralias.audiofocus.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.polaralias.audiofocus.R
import com.polaralias.audiofocus.service.OverlayService
import com.polaralias.audiofocus.ui.theme.AudioFocusTheme

class SettingsActivity : ComponentActivity() {
    companion object {
        private const val TAG = "SettingsActivity"
    }

    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "SettingsActivity created")
        setContent {
            AudioFocusTheme {
                val state by viewModel.uiState.collectAsState()
                val activity = this@SettingsActivity
                val hasStarted = remember { mutableStateOf(false) }
                LaunchedEffect(
                    state.hasOverlayPermission,
                    state.hasAccessibilityAccess,
                    state.hasNotificationAccess
                ) {
                    val ready =
                        state.hasOverlayPermission &&
                            state.hasAccessibilityAccess &&
                            state.hasNotificationAccess
                    if (ready && !hasStarted.value) {
                        Log.i(TAG, "All permissions granted, starting OverlayService")
                        hasStarted.value = true
                        OverlayService.start(activity)
                    } else if (!ready && hasStarted.value) {
                        Log.w(TAG, "Permissions revoked, stopping OverlayService")
                        hasStarted.value = false
                        OverlayService.stop(activity)
                    } else if (!ready) {
                        Log.w(TAG, "Cannot start service: ${state.permissionDiagnostic}")
                        hasStarted.value = false
                    }
                }
                SettingsScreen(
                    state = state,
                    onToggleYouTube = viewModel::setEnableYouTube,
                    onToggleYouTubeMusic = viewModel::setEnableYouTubeMusic,
                    onToggleStartOnBoot = viewModel::setStartOnBoot,
                    onDimAmountChange = viewModel::setDimAmount,
                    onRequestOverlay = { openOverlayPermission() },
                    onRequestNotification = { openNotificationAccess() },
                    onRequestAccessibility = { openAccessibilitySettings() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "SettingsActivity resumed, refreshing permissions")
        viewModel.refreshPermissions()
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
        Text(text = stringResource(id = R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
        Text(text = stringResource(id = R.string.settings_description), style = MaterialTheme.typography.bodyMedium)
        
        // Display permission diagnostic if any permissions are missing
        if (state.permissionDiagnostic.isNotEmpty() && 
            (!state.hasOverlayPermission || !state.hasNotificationAccess || !state.hasAccessibilityAccess)) {
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
            Text(text = stringResource(id = R.string.dim_amount), style = MaterialTheme.typography.titleMedium)
            Slider(
                value = state.preferences.dimAmount,
                onValueChange = onDimAmountChange,
                valueRange = 0.2f..1f,
                steps = 7
            )
            Text(text = String.format("%.0f%%", state.preferences.dimAmount * 100f), fontWeight = FontWeight.Medium)
        }
        PermissionRow(
            title = stringResource(id = R.string.permission_overlay),
            granted = state.hasOverlayPermission,
            onClick = onRequestOverlay
        )
        PermissionRow(
            title = stringResource(id = R.string.permission_notification),
            granted = state.hasNotificationAccess,
            onClick = onRequestNotification
        )
        PermissionRow(
            title = stringResource(id = R.string.permission_accessibility),
            granted = state.hasAccessibilityAccess,
            onClick = onRequestAccessibility
        )
        Text(text = stringResource(id = R.string.touch_disclaimer), style = MaterialTheme.typography.bodySmall)
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
        Text(text = title, style = MaterialTheme.typography.titleMedium)
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
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = if (granted) stringResource(id = R.string.permission_status_granted) else stringResource(id = R.string.permission_status_denied),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
            )
        }
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(text = title)
        }
    }
}

@Composable
private fun stringResource(id: Int): String = androidx.compose.ui.res.stringResource(id)
