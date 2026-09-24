package com.invictus.xcode.core.fs

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption

/**
 * All file-tree filesystem operations. Everything runs on [io]; nothing here ever
 * overwrites an existing entry (name clashes on copy/move/duplicate get a numbered
 * " copy" suffix instead).
 */
class FileOps(private val io: CoroutineDispatcher = Dispatchers.IO) {

    suspend fun list(dir: File): FsResult<List<FsEntry>> = withContext(io) {
        if (!dir.isDirectory) return@withContext FsResult.Err(FsError.NOT_FOUND)
        val children = dir.listFiles() ?: return@withContext FsResult.Err(FsError.PERMISSION)
        val entries = children.map { FsEntry(it, it.isDirectory) }
        FsResult.Ok(
            entries.sortedWith(
                compareByDescending<FsEntry> { it.isDirectory }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            ),
        )
    }

    suspend fun createFile(parent: File, rawName: String): FsResult<File> = withContext(io) {
        val name = validateName(rawName) ?: return@withContext FsResult.Err(FsError.INVALID_NAME)
        if (!parent.isDirectory) return@withContext FsResult.Err(FsError.NOT_FOUND)
        val target = File(parent, name)
        if (target.exists()) return@withContext FsResult.Err(FsError.ALREADY_EXISTS)
        guard {
            if (!target.createNewFile()) throw IOException("createNewFile returned false")
            target
        }
    }

    suspend fun createFolder(parent: File, rawName: String): FsResult<File> = withContext(io) {
        val name = validateName(rawName) ?: return@withContext FsResult.Err(FsError.INVALID_NAME)
        if (!parent.isDirectory) return@withContext FsResult.Err(FsError.NOT_FOUND)
        val target = File(parent, name)
        if (target.exists()) return@withContext FsResult.Err(FsError.ALREADY_EXISTS)
        guard {
            if (!target.mkdir()) throw IOException("mkdir returned false")
            target
        }
    }

    suspend fun rename(file: File, rawName: String): FsResult<File> = withContext(io) {
        val name = validateName(rawName) ?: return@withContext FsResult.Err(FsError.INVALID_NAME)
        if (!file.exists()) return@withContext FsResult.Err(FsError.NOT_FOUND)
        val parent = file.parentFile ?: return@withContext FsResult.Err(FsError.INVALID_NAME)
        val target = File(parent, name)
        if (target.path == file.path) return@withContext FsResult.Ok(file)
        // Shared storage is case-insensitive: "a.txt" -> "A.txt" "exists" but is the same file.
        if (target.exists() && !isSameFile(file, target)) {
            return@withContext FsResult.Err(FsError.ALREADY_EXISTS)
        }
        guard {
            if (!file.renameTo(target)) throw IOException("renameTo returned false")
            target
        }
    }

    suspend fun delete(file: File): FsResult<Unit> = withContext(io) {
        if (!Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            return@withContext FsResult.Err(FsError.NOT_FOUND)
        }
        guard { deleteTree(file) }
    }

    /** Numbered copy next to the original: "a.kt" -> "a copy.kt" -> "a copy 2.kt". */
    suspend fun duplicate(file: File): FsResult<File> = withContext(io) {
        if (!file.exists()) return@withContext FsResult.Err(FsError.NOT_FOUND)
        val parent = file.parentFile ?: return@withContext FsResult.Err(FsError.INVALID_NAME)
        val target = uniqueName(parent, file.name, file.isDirectory, alwaysSuffix = true)
        guard {
            copyTreeSafely(file, target)
            target
        }
    }

    suspend fun copyInto(source: File, targetDir: File): FsResult<File> = withContext(io) {
        if (!source.exists() || !targetDir.isDirectory) return@withContext FsResult.Err(FsError.NOT_FOUND)
        if (isInsideItself(source, targetDir)) return@withContext FsResult.Err(FsError.INSIDE_ITSELF)
        val target = uniqueName(targetDir, source.name, source.isDirectory, alwaysSuffix = false)
        guard {
            copyTreeSafely(source, target)
            target
        }
    }

    suspend fun moveInto(source: File, targetDir: File): FsResult<File> = withContext(io) {
        if (!source.exists() || !targetDir.isDirectory) return@withContext FsResult.Err(FsError.NOT_FOUND)
        val sourceParent = source.parentFile
        if (sourceParent != null && canonical(sourceParent) == canonical(targetDir)) {
            return@withContext FsResult.Ok(source) // already there
        }
        if (isInsideItself(source, targetDir)) return@withContext FsResult.Err(FsError.INSIDE_ITSELF)
        val target = uniqueName(targetDir, source.name, source.isDirectory, alwaysSuffix = false)
        guard {
            if (!source.renameTo(target)) {
                // Different filesystem (or rename refused): copy, then remove the original.
                copyTreeSafely(source, target)
                deleteTree(source)
            }
            target
        }
    }

    // region helpers

    private inline fun <T> guard(block: () -> T): FsResult<T> = try {
        FsResult.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: SecurityException) {
        Log.w(TAG, "Permission denied", e)
        FsResult.Err(FsError.PERMISSION, e.message)
    } catch (e: Exception) {
        Log.w(TAG, "File operation failed", e)
        FsResult.Err(FsError.IO, e.message)
    }

    private fun deleteTree(file: File) {
        val isLink = Files.isSymbolicLink(file.toPath())
        if (!isLink && file.isDirectory) {
            val kids = file.listFiles() ?: throw IOException("Cannot list ${file.path}")
            kids.forEach(::deleteTree)
        }
        if (!file.delete()) throw IOException("Cannot delete ${file.path}")
    }

    /** Copies, and removes the half-written target if anything fails. Symlinks are copied as links. */
    private fun copyTreeSafely(source: File, target: File) {
        try {
            copyTree(source, target)
        } catch (e: IOException) {
            runCatching { deleteTree(target) }
            throw e
        }
    }

    private fun copyTree(source: File, target: File) {
        val sourcePath = source.toPath()
        if (Files.isSymbolicLink(sourcePath)) {
            Files.createSymbolicLink(target.toPath(), Files.readSymbolicLink(sourcePath))
        } else if (source.isDirectory) {
            if (!target.mkdir()) throw IOException("Cannot create ${target.path}")
            val kids = source.listFiles() ?: throw IOException("Cannot list ${source.path}")
            kids.forEach { copyTree(it, File(target, it.name)) }
        } else {
            source.copyTo(target, overwrite = false)
        }
    }

    private fun uniqueName(dir: File, name: String, isDirectory: Boolean, alwaysSuffix: Boolean): File {
        if (!alwaysSuffix) {
            val plain = File(dir, name)
            if (!plain.exists()) return plain
        }
        val dot = if (isDirectory) -1 else name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (true) {
            val suffix = if (n == 1) " copy" else " copy $n"
            val candidate = File(dir, "$stem$suffix$ext")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    private fun isInsideItself(source: File, targetDir: File): Boolean {
        if (!source.isDirectory) return false
        val s = canonical(source)
        val t = canonical(targetDir)
        return t == s || t.startsWith(s + File.separator)
    }

    private fun canonical(file: File): String = try {
        file.canonicalPath
    } catch (_: IOException) {
        file.absolutePath
    }

    private fun isSameFile(a: File, b: File): Boolean = try {
        Files.isSameFile(a.toPath(), b.toPath())
    } catch (_: IOException) {
        false
    }

    // endregion

    companion object {
        private const val TAG = "FileOps"
        private const val MAX_NAME_BYTES = 255

        /** Trimmed name, or null if it can't be a single path segment. */
        fun validateName(raw: String): String? {
            val name = raw.trim()
            if (name.isEmpty() || name == "." || name == "..") return null
            if (name.any { it == '/' || it == '\u0000' }) return null
            if (name.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES) return null
            return name
        }
    }
}
