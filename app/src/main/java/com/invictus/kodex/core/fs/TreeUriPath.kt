package com.invictus.kodex.core.fs

import android.net.Uri
import android.os.Environment

/**
 * Resolves a `SAF` tree URI (from `ACTION_OPEN_DOCUMENT_TREE`) to the raw filesystem path it
 * points at, so callers can keep working with [java.io.File] the way the rest of the app does.
 * Only handles the primary external storage volume -- returns null for anything else (a
 * different volume, a document provider with no on-disk path, etc.), and the caller should
 * fall back to manual entry in that case.
 */
object TreeUriPath {
    private const val PRIMARY_VOLUME = "primary"

    fun resolve(uri: Uri): String? {
        val treeDocId = runCatching {
            android.provider.DocumentsContract.getTreeDocumentId(uri)
        }.getOrNull() ?: return null
        val parts = treeDocId.split(":", limit = 2)
        if (parts.size != 2 || parts[0] != PRIMARY_VOLUME) return null
        val relative = parts[1]
        val root = Environment.getExternalStorageDirectory().absolutePath
        return if (relative.isEmpty()) root else "$root/$relative"
    }
}
