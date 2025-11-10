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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.polaralias.audiofocus.R
import com.polaralias.audiofocus.data.OverlayFillMode
import com.polaralias.audiofocus.service.OverlayService
import com.polaralias.audiofocus.service.OverlayServiceState
import com.polaralias.audiofocus.ui.theme.AudioFocusTheme
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

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
                            
                            val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                                if (uri != null) {
                                    try {
                                        Log.d(TAG, "Persisting read permission for overlay image: $uri")
                                        activity.contentResolver.takePersistableUriPermission(
                                            uri,
                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                        )
                                    } catch (e: SecurityException) {
                                        Log.w(TAG, "Unable to persist read permission for $uri", e)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Unexpected error persisting URI permission for $uri", e)
                                    }

                                    state.preferences.overlayImageUri?.let { previousUri ->
                                        if (previousUri != uri) {
                                            activity.releasePersistablePermission(previousUri)
                                        }
                                    }

                                    viewModel.setOverlayImage(uri)
                                } else {
                                    Log.d(TAG, "Overlay image picker returned null URI")
                                }
                            }

                            SettingsScreen(
                                state = state,
                                onToggleYouTube = viewModel::setEnableYouTube,
                                onToggleYouTubeMusic = viewModel::setEnableYouTubeMusic,
                                onToggleStartOnBoot = viewModel::setStartOnBoot,
                                onDimAmountChange = viewModel::setDimAmount,
                                onUseDefaultOverlayColor = viewModel::useDefaultOverlayColor,
                                onPickCustomOverlayColor = viewModel::setCustomOverlayColor,
                                onSelectOverlayImage = {
                                    try {
                                        imagePickerLauncher.launch(arrayOf("image/*"))
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error launching overlay image picker", e)
                                    }
                                },
                                onClearOverlayImage = {
                                    val currentImage = state.preferences.overlayImageUri
                                    if (currentImage != null) {
                                        activity.releasePersistablePermission(currentImage)
                                    }
                                    viewModel.clearOverlayImage()
                                },
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

    private fun releasePersistablePermission(uri: Uri) {
        try {
            val resolver = contentResolver
            val persisted = resolver.persistedUriPermissions.firstOrNull { it.uri == uri }
            if (persisted != null) {
                Log.d(TAG, "Releasing persisted permission for overlay image: $uri")
                resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Unable to release persisted permission for $uri", e)
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error releasing persisted permission for $uri", e)
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
    onUseDefaultOverlayColor: () -> Unit,
    onPickCustomOverlayColor: (Int) -> Unit,
    onSelectOverlayImage: () -> Unit,
    onClearOverlayImage: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestNotification: () -> Unit,
    onRequestAccessibility: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .consumeWindowInsets(WindowInsets.safeDrawing)
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

            OverlayAppearanceSection(
                fillMode = state.preferences.overlayFillMode,
                currentColor = state.preferences.overlayColor,
                defaultColor = state.defaultOverlayColor,
                imageUri = state.preferences.overlayImageUri,
                onUseDefaultOverlayColor = onUseDefaultOverlayColor,
                onPickCustomOverlayColor = onPickCustomOverlayColor,
                onSelectOverlayImage = onSelectOverlayImage,
                onClearOverlayImage = onClearOverlayImage
            )

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
        Spacer(modifier = Modifier.height(24.dp))
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
private fun OverlayAppearanceSection(
    fillMode: OverlayFillMode,
    currentColor: Int,
    defaultColor: Int,
    imageUri: Uri?,
    onUseDefaultOverlayColor: () -> Unit,
    onPickCustomOverlayColor: (Int) -> Unit,
    onSelectOverlayImage: () -> Unit,
    onClearOverlayImage: () -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    val colorHex = remember(currentColor) { String.format("#%08X", currentColor) }
    val defaultHex = remember(defaultColor) { String.format("#%08X", defaultColor) }

    if (showColorPicker) {
        ColorPickerDialog(
            initialColor = Color(currentColor),
            onDismiss = { showColorPicker = false },
            onColorSelected = { selected ->
                onPickCustomOverlayColor(selected.toArgb())
                showColorPicker = false
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(id = R.string.overlay_fill_section_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                if (fillMode == OverlayFillMode.IMAGE && imageUri != null) {
                    AsyncImage(
                        model = imageUri,
                        contentDescription = stringResource(id = R.string.overlay_fill_preview_content_description),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(currentColor))
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val modeLabel = when (fillMode) {
                    OverlayFillMode.IMAGE -> stringResource(id = R.string.overlay_fill_mode_image)
                    OverlayFillMode.SOLID_COLOR -> stringResource(id = R.string.overlay_fill_mode_solid)
                }
                Text(
                    text = modeLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(
                        id = R.string.overlay_fill_current_color,
                        colorHex
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
                if (fillMode == OverlayFillMode.IMAGE && imageUri == null) {
                    Text(
                        text = stringResource(id = R.string.overlay_fill_image_missing),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Text(
            text = androidx.compose.ui.res.stringResource(
                id = R.string.overlay_fill_default_color,
                defaultHex
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )

        Button(
            onClick = onUseDefaultOverlayColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(id = R.string.overlay_fill_use_default))
        }
        Button(
            onClick = { showColorPicker = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(id = R.string.overlay_fill_pick_custom))
        }
        Button(
            onClick = onSelectOverlayImage,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(id = R.string.overlay_fill_select_image))
        }
        if (imageUri != null) {
            OutlinedButton(
                onClick = onClearOverlayImage,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(id = R.string.overlay_fill_remove_image))
            }
        }
    }
}

@Composable
private fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit
) {
    var alpha by remember(initialColor) { mutableFloatStateOf(initialColor.alpha * 255f) }
    var red by remember(initialColor) { mutableFloatStateOf(initialColor.red * 255f) }
    var green by remember(initialColor) { mutableFloatStateOf(initialColor.green * 255f) }
    var blue by remember(initialColor) { mutableFloatStateOf(initialColor.blue * 255f) }

    val previewColor = Color(
        alpha = (alpha / 255f).coerceIn(0f, 1f),
        red = (red / 255f).coerceIn(0f, 1f),
        green = (green / 255f).coerceIn(0f, 1f),
        blue = (blue / 255f).coerceIn(0f, 1f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onColorSelected(previewColor) }) {
                Text(text = stringResource(id = R.string.color_picker_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.color_picker_cancel))
            }
        },
        title = { Text(text = stringResource(id = R.string.color_picker_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(previewColor)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp)
                        )
                )
                ColorSliderRow(
                    label = stringResource(id = R.string.color_picker_alpha),
                    value = alpha,
                    onValueChange = { alpha = it }
                )
                ColorSliderRow(
                    label = stringResource(id = R.string.color_picker_red),
                    value = red,
                    onValueChange = { red = it }
                )
                ColorSliderRow(
                    label = stringResource(id = R.string.color_picker_green),
                    value = green,
                    onValueChange = { green = it }
                )
                ColorSliderRow(
                    label = stringResource(id = R.string.color_picker_blue),
                    value = blue,
                    onValueChange = { blue = it }
                )
            }
        }
    )
}

@Composable
private fun ColorSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = value.roundToInt().toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        Slider(
            value = value.coerceIn(0f, 255f),
            onValueChange = { onValueChange(it.coerceIn(0f, 255f)) },
            valueRange = 0f..255f
        )
    }
}

@Composable
private fun stringResource(id: Int): String = androidx.compose.ui.res.stringResource(id)
