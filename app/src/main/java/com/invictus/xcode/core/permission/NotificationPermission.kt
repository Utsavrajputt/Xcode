package com.invictus.xcode.core.permission

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Runtime notification permission (POST_NOTIFICATIONS, API 33+). Not required for
 * anything today -- requested during onboarding so future work (clone/sync progress,
 * background jobs) doesn't silently drop notifications the way it would if this were
 * only requested once that feature ships.
 */
class NotificationPermission(private val appContext: Context) {

    /** The runtime permission string to request, or null on API < 33 where none is needed. */
    val manifestPermission: String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null

    fun isGranted(): Boolean {
        if (!NotificationManagerCompat.from(appContext).areNotificationsEnabled()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                appContext, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Opens the app's notification settings page -- used when notifications are
     * disabled at the system level (channel/app toggle) rather than via the
     * runtime permission, which the caller should request through an
     * ActivityResultLauncher instead of this.
     */
    fun openSettings(activityContext: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, appContext.packageName)
        }
        try {
            activityContext.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            try {
                activityContext.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", appContext.packageName, null)),
                )
            } catch (_: ActivityNotFoundException) {
                // No settings screen available on this device; nothing more we can do.
            }
        }
    }
}
