package com.invictus.kodex.github.install

import java.io.File
import java.io.IOException
import java.util.zip.ZipException
import java.util.zip.ZipFile

/** Why an artifact zip was rejected (plan 8.3 edge cases). */
class ArtifactZipException(val reason: Reason) : IOException(reason.name) {
    enum class Reason { NoApk, UnsafeEntry, TooLarge, Corrupt }
}

/**
 * Pure zip helpers for GitHub artifacts (a zip that wraps the `.apk`). Defends against zip-slip
 * (every entry name is checked, output is always a sanitised file name inside the target dir) and
 * zip bombs (bytes are counted while copying, never trusted from headers).
 */
object ArtifactZip {
    const val MAX_APK_BYTES = 300L * 1024 * 1024
    private const val MAX_ENTRIES = 10_000
    private val NAME_HINT = Regex("(?i)(apk|arm64|armeabi|x86|universal)")

    /** Heuristic from the artifact name only; the zip content is verified after download. */
    fun looksLikeApk(artifactName: String): Boolean = NAME_HINT.containsMatchIn(artifactName)

    /** True for an entry name that could escape the target directory. */
    fun isUnsafeEntry(name: String): Boolean {
        if (name.isEmpty()) return true
        if (name.startsWith("/") || name.startsWith("\\")) return true
        if (name.length >= 2 && name[1] == ':') return true // drive letter
        return name.split('/', '\\').any { it == ".." }
    }

    /** Extracts the first `.apk` (shallowest path wins) into [destDir] and returns it. */
    fun extractApk(zip: File, destDir: File, maxBytes: Long = MAX_APK_BYTES): File {
        try {
            ZipFile(zip).use { zf ->
                val entries = zf.entries().toList()
                if (entries.size > MAX_ENTRIES) throw ArtifactZipException(ArtifactZipException.Reason.TooLarge)
                if (entries.any { isUnsafeEntry(it.name) }) throw ArtifactZipException(ArtifactZipException.Reason.UnsafeEntry)
                val apk = entries
                    .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                    .minByOrNull { it.name.count { c -> c == '/' } }
                    ?: throw ArtifactZipException(ArtifactZipException.Reason.NoApk)

                destDir.mkdirs()
                val safeName = apk.name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { "app.apk" }
                val out = File(destDir, safeName)
                val root = destDir.canonicalPath + File.separator
                if (!out.canonicalPath.startsWith(root)) throw ArtifactZipException(ArtifactZipException.Reason.UnsafeEntry)

                var total = 0L
                try {
                    zf.getInputStream(apk).use { input ->
                        out.outputStream().use { o ->
                            val buf = ByteArray(32 * 1024)
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                total += n
                                if (total > maxBytes) throw ArtifactZipException(ArtifactZipException.Reason.TooLarge)
                                o.write(buf, 0, n)
                            }
                        }
                    }
                } catch (e: IOException) {
                    out.delete()
                    throw e
                }
                return out
            }
        } catch (e: ArtifactZipException) {
            throw e
        } catch (e: ZipException) {
            throw ArtifactZipException(ArtifactZipException.Reason.Corrupt)
        }
    }

    /** True when the zip holds an `.apk` entry (cheap probe used to decide `.apk` vs `.zip` on save). */
    fun containsApk(zip: File): Boolean = try {
        ZipFile(zip).use { zf -> zf.entries().asSequence().any { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) } }
    } catch (e: IOException) {
        false
    }

    /** ABI folders under `lib/` of an APK. Empty = no native code (runs anywhere). */
    fun apkAbis(apk: File): Set<String> = try {
        ZipFile(apk).use { zf ->
            zf.entries().asSequence()
                .map { it.name }
                .filter { it.startsWith("lib/") }
                .mapNotNull { it.removePrefix("lib/").substringBefore('/', "").ifEmpty { null } }
                .toSet()
        }
    } catch (e: IOException) {
        emptySet()
    }
}
