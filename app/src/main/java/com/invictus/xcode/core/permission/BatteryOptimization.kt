package com.invictus.xcode.core.permission

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * "Ignore battery optimizations" helper. Not needed by anything today -- there's no
 * background/long-running work yet -- but requested during onboarding so it's already
 * in place once clone/push/sync work moves off the foreground UI.
 */
class BatteryOptimization(private val appContext: Context) {

    fun isDisabled(): Boolean {
        val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) ?: true
    }

    /**
     * Opens the direct "ignore battery optimizations" system dialog for this app,
     * falling back to the general list screen on OEMs (MIUI, ColorOS, etc.) that
     * block the direct-request dialog.
     */
    fun requestDisable(activityContext: Context) {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${appContext.packageName}"))
        try {
            activityContext.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                activityContext.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: ActivityNotFoundException) {
                // No battery settings screen available on this device; nothing more we can do.
            }
        }
    }
}
