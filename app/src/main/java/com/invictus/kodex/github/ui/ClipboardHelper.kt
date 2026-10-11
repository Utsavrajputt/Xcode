package com.invictus.kodex.github.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

/** Clipboard write for logs: flagged sensitive on Android 13+ (logs can echo secrets). */
object ClipboardHelper {
    /** @return false if the system refused (e.g. payload too large). */
    fun copy(context: Context, label: String, text: String, sensitive: Boolean = true): Boolean {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return false
        val clip = ClipData.newPlainText(label, text)
        if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        return runCatching { cm.setPrimaryClip(clip) }.isSuccess
    }
}
