package com.invictus.kodex.github.install

import com.invictus.kodex.github.api.GhArtifact
import com.invictus.kodex.github.data.GitHubRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Streams an artifact zip to a `.part` file in `cacheDir/gh_apk`, renames it on success and (for installs)
 * extracts the `.apk`. Everything lives in the cache dir, which Android may purge at any time;
 * stale files are removed on start and after use (plan 8.4).
 */
class ApkDownloader(cacheDir: File) {
    val dir: File = File(cacheDir, "gh_apk")

    /** Downloads [artifact]'s zip. The returned file is final (not `.part`). Cancel = coroutine cancel. */
    suspend fun downloadZip(repo: GitHubRepository, artifact: GhArtifact, onProgress: (Long) -> Unit): File {
        withContext(Dispatchers.IO) { dir.mkdirs() }
        val part = File(dir, "artifact-${artifact.id}.zip.part")
        val zip = File(dir, "artifact-${artifact.id}.zip")
        try {
            repo.downloadArtifact(artifact, part, onProgress)
            val ok = withContext(Dispatchers.IO) { zip.delete(); part.renameTo(zip) }
            if (!ok) throw java.io.IOException("rename failed")
            return zip
        } catch (e: Throwable) {
            part.delete()
            throw e
        }
    }

    /** Extracts the `.apk` from [zip] (zip-slip / size safe) and removes the zip. */
    suspend fun extract(zip: File): File = withContext(Dispatchers.IO) {
        try {
            ArtifactZip.extractApk(zip, dir)
        } finally {
            zip.delete()
        }
    }

    /** Removes `.part` leftovers (process death mid-download) and files older than [maxAgeMs]. */
    fun cleanOld(maxAgeMs: Long = 24L * 60 * 60 * 1000, now: Long = System.currentTimeMillis()) {
        dir.listFiles()?.forEach { f ->
            if (f.name.endsWith(".part") || now - f.lastModified() > maxAgeMs) f.delete()
        }
    }

    fun deleteAll() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
