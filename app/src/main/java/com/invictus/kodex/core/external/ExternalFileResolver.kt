package com.invictus.kodex.core.external

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.File

/**
 * Turns an "Open with" / "Edit with" intent (ACTION_VIEW / ACTION_EDIT) into a real [File] the
 * editor can open and save back to.
 *
 * Order: `file://` -> external-storage documents URI -> MediaStore `_data` -> (last resort) a copy
 * in the app cache. A copy is only used when the provider gives no real path; edits then stay in
 * the copy, which is why it's the last choice. Call off the main thread.
 */
object ExternalFileResolver {

    fun isOpenIntent(intent: Intent?): Boolean =
        intent != null && (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_EDIT) &&
            intent.data != null

    fun resolve(context: Context, intent: Intent): File? {
        val uri = intent.data ?: return null
        return when (uri.scheme) {
            ContentResolver.SCHEME_FILE -> uri.path?.let(::File)?.takeIf { it.isFile }
            ContentResolver.SCHEME_CONTENT ->
                externalStoragePath(uri)
                    ?: mediaStorePath(context, uri)
                    ?: copyToCache(context, uri)
            else -> null
        }
    }

    /** content://com.android.externalstorage.documents/document/primary:Download/a.txt */
    private fun externalStoragePath(uri: Uri): File? {
        if (uri.authority != "com.android.externalstorage.documents") return null
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val volume = docId.substringBefore(':')
        val rel = docId.substringAfter(':', "")
        val base = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory()
        } else {
            File("/storage/$volume")
        }
        return File(base, rel).takeIf { it.isFile }
    }

    @Suppress("DEPRECATION")
    private fun mediaStorePath(context: Context, uri: Uri): File? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        c.getString(0)?.let(::File)?.takeIf { it.isFile && it.canWrite() }
                    } else null
                }
        }.getOrNull()

    private fun copyToCache(context: Context, uri: Uri): File? = runCatching {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "untitled.txt"
        val dir = File(context.cacheDir, "external-open").apply { mkdirs() }
        val out = File(dir, name.replace('/', '_'))
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it) }
        } ?: return@runCatching null
        out
    }.getOrNull()
}
