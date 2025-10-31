package com.polaralias.audiofocus.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.polaralias.audiofocus.data.PreferencesRepository
import com.polaralias.audiofocus.service.OverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.Default + Job())
        scope.launch {
            try {
                val prefs = PreferencesRepository(context.applicationContext).current()
                if (prefs.startOnBoot && Settings.canDrawOverlays(context)) {
                    OverlayService.start(context)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
