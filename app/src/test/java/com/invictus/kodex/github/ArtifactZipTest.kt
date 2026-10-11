package com.invictus.kodex.github

import com.invictus.kodex.github.install.ArtifactZip
import com.invictus.kodex.github.install.ArtifactZipException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArtifactZipTest {
    private fun tmp(): File = Files.createTempDirectory("gh_zip").toFile().also { it.deleteOnExit() }

    private fun zip(dir: File, vararg entries: Pair<String, ByteArray>): File {
        val f = File(dir, "a.zip")
        ZipOutputStream(f.outputStream()).use { z ->
            entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() }
        }
        return f
    }

    private fun expectReason(reason: ArtifactZipException.Reason, block: () -> Unit) {
        try { block(); fail("expected $reason") } catch (e: ArtifactZipException) { assertEquals(reason, e.reason) }
    }

    @Test fun extractsApkFromWrapperZip() {
        val d = tmp()
        val z = zip(d, "kodex-arm64-v8a-release.apk" to "APKDATA".toByteArray(), "notes.txt" to byteArrayOf(1))
        val out = ArtifactZip.extractApk(z, File(d, "out"))
        assertEquals("kodex-arm64-v8a-release.apk", out.name)
        assertEquals("APKDATA", out.readText())
        assertEquals(File(d, "out").canonicalPath, out.parentFile!!.canonicalPath)
    }

    @Test fun prefersShallowestApk() {
        val d = tmp()
        val z = zip(d, "deep/er/x.apk" to "deep".toByteArray(), "top.apk" to "top".toByteArray())
        assertEquals("top", ArtifactZip.extractApk(z, File(d, "o")).readText())
    }

    @Test fun noApkInside() {
        val d = tmp()
        expectReason(ArtifactZipException.Reason.NoApk) { ArtifactZip.extractApk(zip(d, "readme.txt" to byteArrayOf(1)), File(d, "o")) }
        assertFalse(ArtifactZip.containsApk(zip(d, "readme.txt" to byteArrayOf(1))))
    }

    @Test fun zipSlipEntriesAreRejected() {
        val d = tmp()
        for (name in listOf("../evil.apk", "a/../../evil.apk", "/abs/evil.apk", "..\\evil.apk", "C:\\evil.apk")) {
            expectReason(ArtifactZipException.Reason.UnsafeEntry) {
                ArtifactZip.extractApk(zip(d, name to byteArrayOf(1), "ok.apk" to byteArrayOf(2)), File(d, "o"))
            }
        }
        assertFalse(File(d.parentFile, "evil.apk").exists())
    }

    @Test fun oversizedApkIsRefusedAndRemoved() {
        val d = tmp()
        val z = zip(d, "big.apk" to ByteArray(10_000))
        expectReason(ArtifactZipException.Reason.TooLarge) { ArtifactZip.extractApk(z, File(d, "o"), maxBytes = 1_000) }
    }

    @Test fun corruptZip() {
        val d = tmp()
        val f = File(d, "bad.zip").also { it.writeBytes("this is not a zip".toByteArray()) }
        expectReason(ArtifactZipException.Reason.Corrupt) { ArtifactZip.extractApk(f, File(d, "o")) }
    }

    @Test fun oddFileNamesAreSanitised() {
        val d = tmp()
        val out = ArtifactZip.extractApk(zip(d, "my app (1).apk" to byteArrayOf(1)), File(d, "o"))
        assertEquals("my_app__1_.apk", out.name)
    }

    @Test fun abiFoldersAreListed() {
        val d = tmp()
        val apk = zip(d, "lib/arm64-v8a/libx.so" to byteArrayOf(1), "lib/armeabi-v7a/libx.so" to byteArrayOf(1), "classes.dex" to byteArrayOf(1))
        assertEquals(setOf("arm64-v8a", "armeabi-v7a"), ArtifactZip.apkAbis(apk))
        assertTrue(ArtifactZip.apkAbis(zip(tmp(), "classes.dex" to byteArrayOf(1))).isEmpty())
    }

    @Test fun apkNameHeuristic() {
        assertTrue(ArtifactZip.looksLikeApk("kodex-arm64-v8a-abc1234"))
        assertTrue(ArtifactZip.looksLikeApk("kodex-universal-abc1234"))
        assertTrue(ArtifactZip.looksLikeApk("app-release-apk"))
        assertFalse(ArtifactZip.looksLikeApk("test-results"))
    }
}
