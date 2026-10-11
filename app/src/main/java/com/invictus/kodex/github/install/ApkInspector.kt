package com.invictus.kodex.github.install

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/** Facts read from a downloaded APK (plan 8.4 ApkInspector). */
data class ApkInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val abis: Set<String>,
    val signers: Set<String>,
)

data class InspectResult(
    val info: ApkInfo,
    val kind: InstallKind,
    val abiCompatible: Boolean,
    /** The APK is this very app: installing it closes the running process. */
    val isSelf: Boolean,
)

/** Reads package metadata with PackageManager; the decisions live in [InstallDecision]. */
class ApkInspector(private val context: Context) {

    /** Null when the file is not a parseable APK ("Invalid package"). */
    fun inspect(apk: File): InspectResult? {
        val pm = context.packageManager
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return null
        val info = ApkInfo(
            packageName = archive.packageName,
            versionName = archive.versionName.orEmpty(),
            versionCode = archive.longVersionCode,
            abis = ArtifactZip.apkAbis(apk),
            signers = signersOf(archive, history = false),
        )
        val installed: PackageInfo? = try {
            pm.getPackageInfo(info.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        val kind = InstallDecision.decide(
            installedVersion = installed?.longVersionCode,
            newVersion = info.versionCode,
            installedSigners = installed?.let { signersOf(it, history = true) },
            newSigners = info.signers,
        )
        return InspectResult(
            info = info,
            kind = kind,
            abiCompatible = InstallDecision.abiCompatible(info.abis, Build.SUPPORTED_ABIS.toList()),
            isSelf = info.packageName == context.packageName,
        )
    }

    /** SHA-256 (hex) of the signing certs. [history] also includes rotated-from certs. */
    private fun signersOf(pi: PackageInfo, history: Boolean): Set<String> {
        val si = pi.signingInfo ?: return emptySet()
        val sigs = when {
            si.hasMultipleSigners() -> si.apkContentsSigners
            history -> si.signingCertificateHistory
            else -> si.apkContentsSigners
        } ?: return emptySet()
        return sigs.map { sha256Hex(it.toByteArray()) }.toSet()
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
