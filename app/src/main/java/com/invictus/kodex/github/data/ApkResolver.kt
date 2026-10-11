package com.invictus.kodex.github.data

import com.invictus.kodex.github.api.GhArtifact
import java.time.Instant

/** The artifact the APK card offers (plan 8.3). */
data class ApkCandidate(val artifact: GhArtifact, val universal: Boolean)

sealed interface ApkResolution {
    data class Found(val candidate: ApkCandidate) : ApkResolution

    /** Builds exist but every one is past the 14-day retention. [newestAt] = when the newest was made. */
    data class AllExpired(val newestAt: Instant?) : ApkResolution

    data object None : ApkResolution
}

/**
 * Picks the newest non-expired ARM64 artifact built from the default branch by a successful run;
 * falls back to the universal build. Pure: the run check is injected. Names are matched by their
 * ABI token (`<app>-arm64-v8a-<sha>`) so a renamed app keeps working.
 */
object ApkResolver {
    private val ARM64 = Regex("^.+-arm64-v8a-.+$")
    private val UNIVERSAL = Regex("^.+-universal-.+$")
    private const val MAX_RUN_CHECKS = 5

    fun isArm64(name: String) = ARM64.matches(name)
    fun isUniversal(name: String) = UNIVERSAL.matches(name)

    suspend fun resolve(
        artifacts: List<GhArtifact>,
        defaultBranch: String?,
        now: Instant,
        runSucceeded: suspend (Long) -> Boolean,
    ): ApkResolution {
        val newestFirst = artifacts.sortedByDescending { it.createdAt ?: Instant.EPOCH }
        for ((regex, universal) in listOf(ARM64 to false, UNIVERSAL to true)) {
            val live = newestFirst.filter {
                regex.matches(it.name) && !it.isExpiredAt(now) &&
                    (defaultBranch == null || it.branch == defaultBranch)
            }
            for (a in live.take(MAX_RUN_CHECKS)) {
                val run = a.runId
                if (run == null || runSucceeded(run)) return ApkResolution.Found(ApkCandidate(a, universal))
            }
        }
        val expired = newestFirst.filter {
            (ARM64.matches(it.name) || UNIVERSAL.matches(it.name)) && it.isExpiredAt(now) &&
                (defaultBranch == null || it.branch == defaultBranch)
        }
        return if (expired.isNotEmpty()) ApkResolution.AllExpired(expired.first().createdAt) else ApkResolution.None
    }
}
