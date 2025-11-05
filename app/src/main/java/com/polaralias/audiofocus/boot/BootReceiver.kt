package com.polaralias.audiofocus.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.polaralias.audiofocus.data.PreferencesRepository
import com.polaralias.audiofocus.service.OverlayService
import com.polaralias.audiofocus.util.PermissionValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BootReceiver"
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
                
                // Perform strict sequential permission checks
                val permissionStatus = PermissionValidator.checkPermissions(context.applicationContext, TAG)
                
                if (!permissionStatus.allPermissionsGranted) {
                    Log.w(TAG, "Cannot start OverlayService at boot: ${permissionStatus.getDiagnosticMessage()}")
                    Log.w(TAG, "User must open app and grant missing permissions")
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
