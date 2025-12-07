package com.audiofocus.app.service.monitor

import android.app.usage.UsageStatsManager
import android.content.Context
import android.view.accessibility.AccessibilityEvent
import com.audiofocus.app.domain.PermissionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundAppDetector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permissionManager: PermissionManager
) {
    private val _foregroundPackage = MutableStateFlow<String?>(null)
    val foregroundPackage = _foregroundPackage.asStateFlow()

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.toString()?.let { packageName ->
                _foregroundPackage.value = packageName
            }
        }
    }

    fun checkUsageStats() {
        if (permissionManager.hasUsageStatsPermission()) {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val time = System.currentTimeMillis()
            // Query events or usage stats? queryUsageStats is easier for "last used".
            val stats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                time - 1000 * 60,
                time
            )
            val recent = stats?.maxByOrNull { it.lastTimeUsed }
            recent?.packageName?.let {
                _foregroundPackage.value = it
            }
        }
    }
}
