package com.polaralias.audiofocus.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.polaralias.audiofocus.service.AccessWindowsService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class PermissionValidatorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `checkPermissions returns all false when no permissions granted`() {
        val status = PermissionValidator.checkPermissions(context)
        
        assertFalse(status.hasOverlayPermission)
        assertFalse(status.hasNotificationAccess)
        assertFalse(status.hasAccessibilityAccess)
        assertFalse(status.allPermissionsGranted)
    }

    @Test
    fun `validateAllPermissions returns false when permissions missing`() {
        val result = PermissionValidator.validateAllPermissions(context)
        assertFalse(result)
    }

    @Test
    fun `getMissingPermissions returns all when none granted`() {
        val status = PermissionValidator.checkPermissions(context)
        val missing = status.getMissingPermissions()
        
        assertEquals(3, missing.size)
        assertTrue(missing.contains("Overlay Permission"))
        assertTrue(missing.contains("Notification Access"))
        assertTrue(missing.contains("Accessibility Access"))
    }

    @Test
    fun `getDiagnosticMessage returns descriptive message when permissions missing`() {
        val status = PermissionValidator.checkPermissions(context)
        val message = status.getDiagnosticMessage()
        
        assertTrue(message.contains("Missing permissions"))
        assertTrue(message.contains("Overlay Permission"))
        assertTrue(message.contains("Notification Access"))
        assertTrue(message.contains("Accessibility Access"))
    }

    @Test
    fun `getDiagnosticMessage returns success when all granted`() {
        val status = PermissionValidator.PermissionStatus(
            hasOverlayPermission = true,
            hasNotificationAccess = true,
            hasAccessibilityAccess = true
        )
        
        val message = status.getDiagnosticMessage()
        assertEquals("All permissions granted", message)
    }

    @Test
    fun `allPermissionsGranted returns true when all permissions true`() {
        val status = PermissionValidator.PermissionStatus(
            hasOverlayPermission = true,
            hasNotificationAccess = true,
            hasAccessibilityAccess = true
        )
        
        assertTrue(status.allPermissionsGranted)
    }

    @Test
    fun `allPermissionsGranted returns false when any permission false`() {
        val status1 = PermissionValidator.PermissionStatus(
            hasOverlayPermission = false,
            hasNotificationAccess = true,
            hasAccessibilityAccess = true
        )
        assertFalse(status1.allPermissionsGranted)
        
        val status2 = PermissionValidator.PermissionStatus(
            hasOverlayPermission = true,
            hasNotificationAccess = false,
            hasAccessibilityAccess = true
        )
        assertFalse(status2.allPermissionsGranted)
        
        val status3 = PermissionValidator.PermissionStatus(
            hasOverlayPermission = true,
            hasNotificationAccess = true,
            hasAccessibilityAccess = false
        )
        assertFalse(status3.allPermissionsGranted)
    }
}
