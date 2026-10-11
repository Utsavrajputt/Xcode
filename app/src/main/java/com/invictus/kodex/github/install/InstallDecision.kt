package com.invictus.kodex.github.install

/** What installing the downloaded APK would do (plan 8.4). */
enum class InstallKind { Install, Update, Reinstall, Downgrade, SignatureMismatch }

/** Pure install/ABI decisions so they can be unit-tested without PackageManager. */
object InstallDecision {

    /**
     * @param installedVersion versionCode of the installed package, null when not installed.
     * @param installedSigners / [newSigners] SHA-256 hex of signing certs; null/empty = unknown,
     * which never produces a mismatch (the system installer has the final say).
     */
    fun decide(
        installedVersion: Long?,
        newVersion: Long,
        installedSigners: Set<String>?,
        newSigners: Set<String>?,
    ): InstallKind {
        if (installedVersion == null) return InstallKind.Install
        if (!installedSigners.isNullOrEmpty() && !newSigners.isNullOrEmpty() &&
            installedSigners.intersect(newSigners).isEmpty()
        ) return InstallKind.SignatureMismatch
        return when {
            newVersion > installedVersion -> InstallKind.Update
            newVersion == installedVersion -> InstallKind.Reinstall
            else -> InstallKind.Downgrade
        }
    }

    /** Downgrades are blocked outright; a signature mismatch is warned about but left to the system. */
    fun isBlocked(kind: InstallKind): Boolean = kind == InstallKind.Downgrade

    /** No native libs, or at least one ABI the device supports. */
    fun abiCompatible(apkAbis: Set<String>, deviceAbis: List<String>): Boolean =
        apkAbis.isEmpty() || apkAbis.any { it in deviceAbis }
}
