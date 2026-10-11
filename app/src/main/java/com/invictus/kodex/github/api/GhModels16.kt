package com.invictus.kodex.github.api

import org.json.JSONObject
import java.time.Instant

/** M16 models: artifacts and deployments (plan section 4). */
data class GhArtifact(
    val id: Long,
    val name: String,
    val sizeBytes: Long,
    val expired: Boolean,
    val createdAt: Instant?,
    val expiresAt: Instant?,
    val runId: Long?,
    val branch: String?,
    val sha: String?,
) {
    /** GitHub flips `expired` lazily, so also compare the expiry timestamp. */
    fun isExpiredAt(now: Instant): Boolean = expired || (expiresAt != null && !expiresAt.isAfter(now))

    val shortSha: String get() = sha.orEmpty().take(7)

    companion object {
        fun fromJson(j: JSONObject): GhArtifact {
            val run = j.optJSONObject("workflow_run")
            return GhArtifact(
                id = j.getLong("id"),
                name = j.optString("name"),
                sizeBytes = j.optLong("size_in_bytes"),
                expired = j.optBoolean("expired"),
                createdAt = parseInstant(j.str("created_at")),
                expiresAt = parseInstant(j.str("expires_at")),
                runId = run?.optLong("id")?.takeIf { it > 0 },
                branch = run?.str("head_branch"),
                sha = run?.str("head_sha"),
            )
        }
    }
}

data class GhDeployment(
    val id: Long,
    val environment: String,
    val ref: String,
    val sha: String,
    val createdAt: Instant?,
    val description: String,
    /** Latest status `state` (`success`, `inactive`, ...); null until loaded. */
    val latestState: String? = null,
) {
    val shortSha: String get() = sha.take(7)

    companion object {
        fun fromJson(j: JSONObject): GhDeployment = GhDeployment(
            id = j.getLong("id"),
            environment = j.optString("environment"),
            ref = j.optString("ref"),
            sha = j.optString("sha"),
            createdAt = parseInstant(j.str("created_at")),
            description = j.str("description").orEmpty(),
        )
    }
}
