package com.invictus.xcode.core.fs

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.Settings

/** "All files access" (MANAGE_EXTERNAL_STORAGE) helper. minSdk is 30, so the API is always present. */
class StoragePermission(private val appContext: Context) {

    fun isGranted(): Boolean = Environment.isExternalStorageManager()

    /**
     * Opens the per-app "All files access" settings page, falling back to the
     * general list if the device has no per-app screen. Pass an Activity context.
     */
    fun openSettings(activityContext: Context) {
        val perApp = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.fromParts("package", appContext.packageName, null),
        )
        try {
            activityContext.startActivity(perApp)
        } catch (_: ActivityNotFoundException) {
            activityContext.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }
}
