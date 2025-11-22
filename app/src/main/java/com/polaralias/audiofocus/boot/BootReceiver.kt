package com.polaralias.audiofocus.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.polaralias.audiofocus.R
import com.polaralias.audiofocus.data.PreferencesRepository
import com.polaralias.audiofocus.service.OverlayService
import com.polaralias.audiofocus.service.ServiceDiagnostics
import com.polaralias.audiofocus.util.PermissionValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
        private const val PERMISSION_RETRY_DELAY_MS = 1000L
        private const val PERMISSION_MAX_ATTEMPTS = 3
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Ignoring non-boot intent: ${intent.action}")
            return
        }
        
        Log.i(TAG, "Boot completed received, checking if service should start")
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.Default + Job())
        scope.launch {
            try {
                val prefs = PreferencesRepository(context.applicationContext).current()
                Log.d(TAG, "Start on boot preference: ${prefs.startOnBoot}")
                
                if (!prefs.startOnBoot) {
                    Log.i(TAG, "Start on boot disabled, not starting service")
                    return@launch
                }
                
                var attempt = 1
                var permissionStatus = PermissionValidator.checkPermissions(context.applicationContext, TAG)
                while (!permissionStatus.allPermissionsGranted && attempt < PERMISSION_MAX_ATTEMPTS) {
                    Log.w(
                        TAG,
                        "Boot permission check failed (attempt $attempt/$PERMISSION_MAX_ATTEMPTS): ${permissionStatus.getDiagnosticMessage()}"
                    )
                    delay(PERMISSION_RETRY_DELAY_MS)
                    attempt++
                    permissionStatus = PermissionValidator.checkPermissions(context.applicationContext, TAG)
                }

                if (!permissionStatus.allPermissionsGranted) {
                    val diagnostic = context.getString(R.string.diagnostic_notification_listener_retry_exhausted)
                    val fullMessage = "$diagnostic\n${permissionStatus.getDiagnosticMessage()}"
                    Log.w(TAG, "Cannot start OverlayService at boot: $fullMessage")
                    Log.w(TAG, "User must open app and grant missing permissions")
                    ServiceDiagnostics.report(fullMessage)
                    return@launch
                }

                Log.i(TAG, "All permissions granted, starting OverlayService")
                OverlayService.start(context)
            } catch (e: Exception) {
                Log.e(TAG, "Error during boot startup", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
