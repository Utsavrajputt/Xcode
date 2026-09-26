package com.invictus.xcode.core.git.model

import java.io.File

/** What we know about the repository at HEAD, without walking history. */
data class GitRepoSnapshot(
    val gitRoot: File,
    val hasCommits: Boolean,
    val headId: String?,
    val headName: String?,
    val trackingInfo: GitTrackingInfo?,
)

/** Upstream tracking: which remote/branch HEAD follows, and how far it has diverged. */
data class GitTrackingInfo(
    val remote: String,
    val branch: String,
    val ahead: Int,
    val behind: Int,
)

/** Full working-tree status, derived from JGit's Status in one pass. */
data class GitWorkingTreeStatus(
    val changes: List<GitPathChange>,
    val hasUnmerged: Boolean,
) {
    val isClean: Boolean get() = changes.isEmpty()

    val staged: List<GitPathChange> get() = changes.filter { it.staged != GitStageState.NONE }
    val unstaged: List<GitPathChange> get() = changes.filter { it.unstaged != GitWorkingState.NONE }
    val untracked: List<GitPathChange> get() = changes.filter { it.unstaged == GitWorkingState.UNTRACKED }
}

/** One repo-relative path and how it differs from HEAD/index in both directions. */
data class GitPathChange(
    val repoRelativePath: String,
    val staged: GitStageState,
    val unstaged: GitWorkingState,
)

enum class GitStageState { NONE, ADDED, MODIFIED, DELETED, CONFLICT }
enum class GitWorkingState { NONE, MODIFIED, DELETED, UNTRACKED, CONFLICT }

data class GitRemoteInfo(
    val name: String,
    val url: String,
)

data class GitBranchInfo(
    val name: String,
    val isCurrent: Boolean,
)

data class GitCommitSummary(
    val id: String,
    val shortId: String,
    val message: String,
    val author: String,
    val timeMs: Long,
)

/** Everything the interactive error dialog shows; one-click copy serializes this. */
data class GitErrorDetails(
    val title: String,
    val message: String,
    val exceptionClass: String,
    val stackTrace: String,
    val timestamp: Long = System.currentTimeMillis(),
    val authFailure: GitAuthFailureType = GitAuthFailureType.NONE,
) {
    fun toCopyableText(): String = buildString {
        appendLine(title)
        appendLine(message)
        appendLine()
        appendLine(exceptionClass)
        append(stackTrace)
    }
}

enum class GitAuthMethod { TOKEN }

enum class GitAuthFailureType { NONE, AUTH_REQUIRED, INVALID_CREDENTIALS, EXPIRED_TOKEN, PERMISSION_DENIED, NETWORK_ERROR, UNKNOWN }

/** Which network operation to retry once the user supplies a fresh token. */
enum class GitPendingAction { PUSH, PULL, FETCH, CLONE }
