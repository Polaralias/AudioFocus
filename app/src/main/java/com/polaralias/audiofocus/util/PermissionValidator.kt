package com.polaralias.audiofocus.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.polaralias.audiofocus.service.AccessWindowsService

/**
 * Centralized utility for validating app permissions.
 * Provides strict sequential permission checks with comprehensive logging.
 */
object PermissionValidator {
    private const val TAG = "PermissionValidator"

    /**
     * Permission check result with detailed diagnostic information
     */
    data class PermissionStatus(
        val hasOverlayPermission: Boolean,
        val hasNotificationAccess: Boolean,
        val hasAccessibilityAccess: Boolean
    ) {
        val allPermissionsGranted: Boolean
            get() = hasOverlayPermission && hasNotificationAccess && hasAccessibilityAccess

        fun getMissingPermissions(): List<String> {
            val missing = mutableListOf<String>()
            if (!hasOverlayPermission) missing.add("Overlay Permission")
            if (!hasNotificationAccess) missing.add("Notification Access")
            if (!hasAccessibilityAccess) missing.add("Accessibility Access")
            return missing
        }

        fun getDiagnosticMessage(): String {
            return if (allPermissionsGranted) {
                "All permissions granted"
            } else {
                "Missing permissions: ${getMissingPermissions().joinToString(", ")}"
            }
        }
    }

    /**
     * Check all required permissions with logging
     */
    fun checkPermissions(context: Context, logTag: String = TAG): PermissionStatus {
        Log.i(logTag, "Starting permission validation")
        
        var hasOverlay = false
        var hasNotification = false
        var hasAccessibility = false
        
        try {
            // Check overlay permission
            Log.d(logTag, "Checking overlay permission...")
            hasOverlay = Settings.canDrawOverlays(context)
            Log.i(logTag, "Overlay permission check result: $hasOverlay")
        } catch (e: Exception) {
            Log.e(logTag, "Error checking overlay permission", e)
        }
        
        try {
            // Check notification access
            Log.d(logTag, "Checking notification access...")
            hasNotification = NotificationManagerCompat
                .getEnabledListenerPackages(context)
                .contains(context.packageName)
            Log.i(logTag, "Notification access check result: $hasNotification")
        } catch (e: Exception) {
            Log.e(logTag, "Error checking notification access", e)
        }
        
        try {
            // Check accessibility access
            Log.d(logTag, "Checking accessibility access...")
            hasAccessibility = isAccessibilityEnabled(context)
            Log.i(logTag, "Accessibility access check result: $hasAccessibility")
        } catch (e: Exception) {
            Log.e(logTag, "Error checking accessibility access", e)
        }
        
        val status = PermissionStatus(hasOverlay, hasNotification, hasAccessibility)
        Log.i(logTag, "Permission check completed: ${status.getDiagnosticMessage()}")
        
        return status
    }

    /**
     * Validate permissions and log errors if any are missing
     * @return true if all permissions are granted, false otherwise
     */
    fun validateAllPermissions(context: Context, logTag: String = TAG): Boolean {
        val status = checkPermissions(context, logTag)
        if (!status.allPermissionsGranted) {
            Log.w(logTag, "Permission validation failed: ${status.getDiagnosticMessage()}")
        }
        return status.allPermissionsGranted
    }

    /**
     * Check if accessibility service is enabled
     */
    private fun isAccessibilityEnabled(context: Context): Boolean {
        return try {
            Log.d(TAG, "Reading accessibility enabled services from system settings")
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: run {
                Log.d(TAG, "No enabled accessibility services found")
                return false
            }
            
            val component = ComponentName(context, AccessWindowsService::class.java)
            val componentString = component.flattenToString()
            Log.d(TAG, "Looking for component: $componentString")
            
            val isEnabled = enabledServices.split(":")
                .any { it.equals(componentString, ignoreCase = true) }
            
            Log.d(TAG, "Accessibility service enabled check: $isEnabled")
            isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility enabled status", e)
            false
        }
    }
}
