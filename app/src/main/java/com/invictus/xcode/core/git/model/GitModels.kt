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
    val conflicts: List<GitPathChange> get() = changes.filter {
        it.staged == GitStageState.CONFLICT || it.unstaged == GitWorkingState.CONFLICT
    }
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

// ---- M7: diff + file-tree decoration models --------------------------------

/** Line classification for one side of a diff row. PADDING = that side has no line. */
enum class GitDiffLineType { CONTEXT, ADDED, REMOVED, PADDING }

/**
 * One aligned row of a side-by-side diff. Adds/deletes pair up inside a hunk; a leftover
 * line on one side gets PADDING on the other. [intralineLeft]/[intralineRight] hold the
 * changed character ranges inside a paired line for the soft highlight.
 */
data class GitDiffRow(
    val leftNumber: Int?,
    val leftText: String?,
    val leftType: GitDiffLineType,
    val rightNumber: Int?,
    val rightText: String?,
    val rightType: GitDiffLineType,
    val intralineLeft: List<IntRange> = emptyList(),
    val intralineRight: List<IntRange> = emptyList(),
)

/**
 * Parsed diff for one file, render-ready (no JGit on the UI thread).
 * [oldImageBytes] is only filled for image files so the diff UI can show "before".
 * [workFilePath] is the absolute worktree path, used as the "after" image source.
 */
data class GitFileDiffResult(
    val path: String,
    val oldLabel: String,
    val newLabel: String,
    val isBinary: Boolean,
    val isImage: Boolean,
    val rows: List<GitDiffRow>,
    val truncated: Boolean = false,
    val oldImageBytes: ByteArray? = null,
    val workFilePath: String? = null,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is GitFileDiffResult && other.path == path && other.rows == rows)

    override fun hashCode(): Int = 31 * path.hashCode() + rows.hashCode()
}

/** File-tree status stripe for one repo-relative path (plan 3.4 GitPathDecoration). */
data class GitPathDecoration(
    val repoRelativePath: String,
    val label: String,
    val kind: Kind,
) {
    /** [priority] orders aggregation when a folder contains mixed states. */
    enum class Kind(val priority: Int) {
        CONFLICT(4), DELETED(3), MODIFIED(2), ADDED(1), UNTRACKED(0),
    }
}

// ---- M8: advanced ops models ------------------------------------------------

/** Branch with tracking info (upstream + divergence) for the branches screen. */
data class GitBranchDetail(
    val name: String,
    val isCurrent: Boolean,
    val upstream: String?,   // "origin/main", null when no tracking config
    val ahead: Int,
    val behind: Int,
)

data class GitTagInfo(
    val name: String,
    val commitId: String,
    val message: String?,
    val timeMs: Long,
)

data class GitStashInfo(
    val ref: String,     // "stash@{0}"
    val index: Int,
    val message: String,
    val timeMs: Long,
)

enum class GitLogSearchMode { MESSAGE, AUTHOR, HASH }
