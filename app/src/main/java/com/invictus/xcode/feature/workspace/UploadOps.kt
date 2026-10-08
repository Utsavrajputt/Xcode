package com.invictus.xcode.feature.workspace

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import java.util.zip.ZipInputStream

/** File-side work for "Upload" in the tree menu. Everything here blocks: call from an IO dispatcher. */
object UploadOps {

    /** Copies each picked [uris] into [dir] (never overwrites: "a.txt" -> "a (1).txt"). Failed ones are skipped. */
    fun copyUris(resolver: ContentResolver, uris: List<Uri>, dir: File): List<File> {
        dir.mkdirs()
        return uris.mapNotNull { uri ->
            runCatching {
                val name = displayName(resolver, uri)
                val out = uniqueFile(dir, name)
                resolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { input.copyTo(it) }
                } ?: return@runCatching null
                out
            }.getOrNull()
        }
    }

    private fun displayName(resolver: ContentResolver, uri: Uri): String {
        val fromProvider = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        val raw = fromProvider ?: uri.lastPathSegment?.substringAfterLast('/') ?: "upload"
        return raw.replace('/', '_').ifBlank { "upload" }
    }

    internal fun uniqueFile(dir: File, name: String): File {
        var candidate = File(dir, name)
        if (!candidate.exists()) return candidate
        val base = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "").let { if (it.isEmpty() || it == name) "" else ".$it" }
        var n = 1
        while (candidate.exists()) {
            candidate = File(dir, "$base ($n)$ext")
            n++
        }
        return candidate
    }

    class Extracted(val folder: File, val fileCount: Int)

    /**
     * Unzips [zip] into a new folder next to it named after the archive ("app.zip" -> "app/",
     * "app (1)/" if taken), so nothing existing is ever overwritten. Entries that would escape
     * the folder (zip-slip: "../x") abort the extraction.
     */
    @Throws(IOException::class)
    fun extractZip(zip: File, parent: File): Extracted {
        val dest = uniqueFile(parent, zip.name.substringBeforeLast('.', zip.name))
        if (!dest.mkdirs()) throw IOException("Can't create ${dest.name}")
        val destRoot = dest.canonicalPath + File.separator
        var count = 0
        try {
            ZipInputStream(zip.inputStream().buffered()).use { zin ->
                while (true) {
                    val entry = zin.nextEntry ?: break
                    val out = File(dest, entry.name)
                    if (!out.canonicalPath.startsWith(destRoot)) {
                        throw IOException("Unsafe path in archive: ${entry.name}")
                    }
                    if (entry.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().buffered().use { zin.copyTo(it) }
                        count++
                    }
                    zin.closeEntry()
                }
            }
        } catch (e: Exception) {
            dest.deleteRecursively() // don't leave a half-extracted folder behind
            throw if (e is IOException) e else IOException(e)
        }
        return Extracted(dest, count)
    }
}
