package com.invictus.xcode.core.project

import android.os.Environment
import com.invictus.xcode.core.fs.FsError
import com.invictus.xcode.core.fs.FsResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext

/**
 * Zips a project folder into `Documents/XcodeBackups/<name>_<timestamp>.zip`.
 *
 * Safe by construction: the project is only read, the zip is written to a `.part` file and
 * renamed when complete (so a failed or cancelled backup never leaves a half-written zip),
 * symlinks are skipped, and the backups folder is never packed into itself.
 */
class ProjectBackup(
    private val backupsDir: File = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
        BACKUPS_FOLDER,
    ),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun backup(project: File): FsResult<File> = withContext(io) {
        if (!project.isDirectory) return@withContext FsResult.Err(FsError.NOT_FOUND)
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val target = File(backupsDir, "${project.name.ifEmpty { "project" }}_$stamp.zip")
        val part = File(backupsDir, target.name + ".part")
        try {
            if (!backupsDir.isDirectory && !backupsDir.mkdirs()) {
                return@withContext FsResult.Err(FsError.PERMISSION, backupsDir.path)
            }
            ZipOutputStream(BufferedOutputStream(FileOutputStream(part))).use { zip -> writeTree(project, zip) }
            if (!part.renameTo(target)) throw IOException("could not rename ${part.name}")
            FsResult.Ok(target)
        } catch (e: SecurityException) {
            part.delete()
            FsResult.Err(FsError.PERMISSION, e.message)
        } catch (e: IOException) {
            part.delete()
            FsResult.Err(FsError.IO, e.message)
        } catch (e: kotlinx.coroutines.CancellationException) {
            part.delete()
            throw e
        }
    }

    private suspend fun writeTree(project: File, zip: ZipOutputStream) {
        val skipDir = runCatching { backupsDir.canonicalPath }.getOrNull()
        val base = project.name.ifEmpty { "project" }
        val walk = project.walkTopDown().onEnter { dir ->
            !Files.isSymbolicLink(dir.toPath()) && runCatching { dir.canonicalPath }.getOrNull() != skipDir
        }
        for (file in walk) {
            coroutineContext.ensureActive()
            if (file == project) continue
            if (Files.isSymbolicLink(file.toPath())) continue
            val rel = file.relativeTo(project).path.replace(File.separatorChar, '/')
            if (file.isDirectory) {
                zip.putNextEntry(ZipEntry("$base/$rel/").apply { time = file.lastModified() })
                zip.closeEntry()
            } else {
                zip.putNextEntry(ZipEntry("$base/$rel").apply { time = file.lastModified() })
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    companion object {
        const val BACKUPS_FOLDER = "XcodeBackups"
    }
}
