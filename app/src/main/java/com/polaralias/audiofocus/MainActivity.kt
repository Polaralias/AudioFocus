package com.polaralias.audiofocus

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.polaralias.audiofocus.accessibility.AudioFocusAccessibilityService
import com.polaralias.audiofocus.overlay.OverlayService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val repository by lazy { (application as AudioFocusApp).focusStateRepository }

    private lateinit var overlayStatus: TextView
    private lateinit var overlayAction: MaterialButton
    private lateinit var overlayStep: View

    private lateinit var accessibilityStatus: TextView
    private lateinit var accessibilityAction: MaterialButton
    private lateinit var accessibilityStep: View

    private lateinit var notificationStatus: TextView
    private lateinit var notificationAction: MaterialButton
    private lateinit var notificationStep: View

    private lateinit var manualControls: View
    private lateinit var manualStatus: TextView
    private lateinit var manualToggle: SwitchMaterial

    private var updatingManualToggle = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        overlayStatus = findViewById(R.id.overlay_status)
        overlayAction = findViewById(R.id.overlay_action)
        overlayStep = findViewById(R.id.overlay_step)

        accessibilityStatus = findViewById(R.id.accessibility_status)
        accessibilityAction = findViewById(R.id.accessibility_action)
        accessibilityStep = findViewById(R.id.accessibility_step)

        notificationStatus = findViewById(R.id.notification_status)
        notificationAction = findViewById(R.id.notification_action)
        notificationStep = findViewById(R.id.notification_step)

        manualControls = findViewById(R.id.manual_controls)
        manualStatus = findViewById(R.id.manual_status)
        manualToggle = findViewById(R.id.manual_toggle)

        overlayAction.setOnClickListener {
            launchSettings(buildOverlayPermissionIntent(this))
        }
        accessibilityAction.setOnClickListener {
            launchSettings(buildAccessibilitySettingsIntent(this))
        }
        notificationAction.setOnClickListener {
            launchSettings(buildNotificationListenerSettingsIntent(this))
        }
        manualToggle.setOnCheckedChangeListener { _, isChecked ->
            if (updatingManualToggle) return@setOnCheckedChangeListener
            repository.setManualPause(isChecked)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.manualPauseFlow.collect { paused ->
                    updateManualPauseState(paused)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionState()
    }

    private fun updatePermissionState() {
        val overlayGranted = hasOverlayPermission(this)
        val accessibilityGranted = isAccessibilityServiceEnabled(this)
        val notificationGranted = isNotificationListenerEnabled(this)

        updateStep(
            stepView = overlayStep,
            statusView = overlayStatus,
            actionButton = overlayAction,
            granted = overlayGranted,
            prerequisitesMet = true,
        )

        updateStep(
            stepView = accessibilityStep,
            statusView = accessibilityStatus,
            actionButton = accessibilityAction,
            granted = accessibilityGranted,
            prerequisitesMet = overlayGranted,
        )

        updateStep(
            stepView = notificationStep,
            statusView = notificationStatus,
            actionButton = notificationAction,
            granted = notificationGranted,
            prerequisitesMet = overlayGranted && accessibilityGranted,
        )

        val allGranted = overlayGranted && accessibilityGranted && notificationGranted
        manualControls.isVisible = allGranted
        manualToggle.isEnabled = allGranted

        updateManualPauseState(repository.manualPauseFlow.value)

        if (allGranted) {
            startOverlayService()
        }
    }

    private fun updateStep(
        stepView: View,
        statusView: TextView,
        actionButton: MaterialButton,
        granted: Boolean,
        prerequisitesMet: Boolean,
    ) {
        val statusRes = when {
            granted -> R.string.permission_status_granted
            prerequisitesMet -> R.string.permission_status_pending
            else -> R.string.permission_status_blocked
        }
        statusView.setText(statusRes)

        val buttonTextRes = if (granted) {
            R.string.permission_action_manage
        } else {
            R.string.permission_action_grant
        }
        actionButton.setText(buttonTextRes)

        val enabled = prerequisitesMet || granted
        actionButton.isEnabled = enabled
        stepView.alpha = if (enabled) 1f else 0.5f
    }

    private fun updateManualPauseState(paused: Boolean) {
        val statusRes = if (paused) {
            R.string.manual_status_paused
        } else {
            R.string.manual_status_active
        }
        manualStatus.setText(statusRes)

        val shouldCheck = paused
        if (manualToggle.isChecked != shouldCheck) {
            updatingManualToggle = true
            manualToggle.isChecked = shouldCheck
            updatingManualToggle = false
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun launchSettings(intent: Intent) {
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, R.string.permission_settings_error, Toast.LENGTH_LONG).show()
            }
    }

    companion object {
        fun hasOverlayPermission(context: Context): Boolean {
            return if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                true
            } else {
                Settings.canDrawOverlays(context)
            }
        }

        fun buildOverlayPermissionIntent(context: Context): Intent {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:${context.packageName}")
            return intent
        }

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedComponent = ComponentName(context, AudioFocusAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false
            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                val component = ComponentName.unflattenFromString(splitter.next())
                if (component == expectedComponent) {
                    val accessibilityEnabled = Settings.Secure.getInt(
                        context.contentResolver,
                        Settings.Secure.ACCESSIBILITY_ENABLED,
                        0,
                    ) == 1
                    return accessibilityEnabled
                }
            }
            return false
        }

        fun buildAccessibilitySettingsIntent(context: Context): Intent {
            return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }

        fun isNotificationListenerEnabled(context: Context): Boolean {
            val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(context)
            return context.packageName in enabledPackages
        }

        fun buildNotificationListenerSettingsIntent(context: Context): Intent {
            return Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }
    }
}
