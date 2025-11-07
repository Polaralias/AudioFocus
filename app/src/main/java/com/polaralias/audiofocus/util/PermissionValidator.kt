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
        Log.d(logTag, "Starting permission validation")
        
        var hasOverlay = false
        var hasNotification = false
        var hasAccessibility = false
        
        try {
            // Check overlay permission
            hasOverlay = Settings.canDrawOverlays(context)
            Log.d(logTag, "Overlay permission: $hasOverlay")
        } catch (e: Exception) {
            Log.e(logTag, "Error checking overlay permission", e)
        }
        
        try {
            // Check notification access
            hasNotification = NotificationManagerCompat
                .getEnabledListenerPackages(context)
                .contains(context.packageName)
            Log.d(logTag, "Notification access: $hasNotification")
        } catch (e: Exception) {
            Log.e(logTag, "Error checking notification access", e)
        }
        
        try {
            // Check accessibility access
            hasAccessibility = isAccessibilityEnabled(context)
            Log.d(logTag, "Accessibility access: $hasAccessibility")
        } catch (e: Exception) {
            Log.e(logTag, "Error checking accessibility access", e)
        }
        
        val status = PermissionStatus(hasOverlay, hasNotification, hasAccessibility)
        Log.i(logTag, "Permission check result: ${status.getDiagnosticMessage()}")
        
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
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            
            val component = ComponentName(context, AccessWindowsService::class.java)
            val componentString = component.flattenToString()
            
            enabledServices.split(":")
                .any { it.equals(componentString, ignoreCase = true) }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility enabled status", e)
            false
        }
    }
}
