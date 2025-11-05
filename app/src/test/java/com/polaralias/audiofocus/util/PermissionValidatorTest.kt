package com.polaralias.audiofocus.util

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider
import com.polaralias.audiofocus.service.AccessWindowsService
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
        
        assert(!status.hasOverlayPermission)
        assert(!status.hasNotificationAccess)
        assert(!status.hasAccessibilityAccess)
        assert(!status.allPermissionsGranted)
    }

    @Test
    fun `validateAllPermissions returns false when permissions missing`() {
        val result = PermissionValidator.validateAllPermissions(context)
        assert(!result)
    }

    @Test
    fun `getMissingPermissions returns all when none granted`() {
        val status = PermissionValidator.checkPermissions(context)
        val missing = status.getMissingPermissions()
        
        assert(missing.size == 3)
        assert(missing.contains("Overlay Permission"))
        assert(missing.contains("Notification Access"))
        assert(missing.contains("Accessibility Access"))
    }

    @Test
    fun `getDiagnosticMessage returns descriptive message when permissions missing`() {
        val status = PermissionValidator.checkPermissions(context)
        val message = status.getDiagnosticMessage()
        
        assert(message.contains("Missing permissions"))
        assert(message.contains("Overlay Permission"))
        assert(message.contains("Notification Access"))
        assert(message.contains("Accessibility Access"))
    }

    @Test
    fun `getDiagnosticMessage returns success when all granted`() {
        val status = PermissionValidator.PermissionStatus(
            hasOverlayPermission = true,
            hasNotificationAccess = true,
            hasAccessibilityAccess = true
        )
        
        val message = status.getDiagnosticMessage()
        assert(message == "All permissions granted")
    }

    @Test
    fun `allPermissionsGranted returns true when all permissions true`() {
        val status = PermissionValidator.PermissionStatus(
            hasOverlayPermission = true,
            hasNotificationAccess = true,
            hasAccessibilityAccess = true
        )
        
        assert(status.allPermissionsGranted)
    }

    @Test
    fun `allPermissionsGranted returns false when any permission false`() {
        val status1 = PermissionValidator.PermissionStatus(
            hasOverlayPermission = false,
            hasNotificationAccess = true,
            hasAccessibilityAccess = true
        )
        assert(!status1.allPermissionsGranted)
        
        val status2 = PermissionValidator.PermissionStatus(
            hasOverlayPermission = true,
            hasNotificationAccess = false,
            hasAccessibilityAccess = true
        )
        assert(!status2.allPermissionsGranted)
        
        val status3 = PermissionValidator.PermissionStatus(
            hasOverlayPermission = true,
            hasNotificationAccess = true,
            hasAccessibilityAccess = false
        )
        assert(!status3.allPermissionsGranted)
    }
}
