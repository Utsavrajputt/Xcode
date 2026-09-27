package com.invictus.xcode.feature.git

import com.invictus.xcode.core.git.model.GitConflictSide
import com.invictus.xcode.core.git.model.GitResetMode

sealed interface GitEvent {
    data object Refresh : GitEvent
    data object StageAll : GitEvent
    data object UnstageAll : GitEvent
    data class Stage(val path: String) : GitEvent
    data class Unstage(val path: String) : GitEvent
    /** Long-press menu: ask to discard an unstaged/untracked change (shows a confirm dialog). */
    data class RequestDiscard(val path: String, val isUntracked: Boolean) : GitEvent
    data object ConfirmDiscard : GitEvent
    data object DismissDiscard : GitEvent
    data class CommitMessageChange(val text: String) : GitEvent
    data object ToggleAmend : GitEvent
    data object Commit : GitEvent
    data object Push : GitEvent
    data object Pull : GitEvent
    data object Fetch : GitEvent
    // M7 drawer
    data class ToggleSection(val name: String) : GitEvent
    data class LoadDiff(val path: String) : GitEvent
    data class CloseDiff(val path: String) : GitEvent
    /** Repo-relative path the editor should open. */
    data class OpenFile(val path: String) : GitEvent
    data object OpenIdentity : GitEvent
    data class SaveIdentity(val name: String, val email: String, val isLocal: Boolean) : GitEvent
    data object DismissIdentity : GitEvent
    data class SaveToken(val host: String, val username: String, val token: String) : GitEvent
    data object DismissToken : GitEvent
    data object DismissError : GitEvent
    // M10: merge + conflicts
    data object OpenMerge : GitEvent
    data object DismissMerge : GitEvent
    data class Merge(val branch: String) : GitEvent
    data class ResolveConflict(val path: String, val side: GitConflictSide) : GitEvent
    data class MarkResolved(val path: String) : GitEvent
    data class PreviewConflictSide(val path: String, val side: GitConflictSide) : GitEvent
    data object DismissConflictPreview : GitEvent
    data object AbortMerge : GitEvent
    data object ConfirmAbortMerge : GitEvent
    data object DismissAbortMerge : GitEvent
    data object CompleteMerge : GitEvent
    data class CompleteMergeMessageChange(val text: String) : GitEvent
    data object DismissCompleteMerge : GitEvent
    // Reset (soft/mixed/hard) — HEAD, a remote-tracking ref, or any picked commit.
    data object OpenReset : GitEvent
    data object DismissReset : GitEvent
    data object LoadResetCommits : GitEvent
    data class ResetTo(val ref: String, val label: String, val mode: GitResetMode) : GitEvent
    data object ConfirmHardReset : GitEvent
    data object DismissHardResetConfirm : GitEvent
}

sealed interface GitCloneEvent {
    data class UrlChange(val value: String) : GitCloneEvent
    data class ParentChange(val value: String) : GitCloneEvent
    data class FolderChange(val value: String) : GitCloneEvent
    data class BranchChange(val value: String) : GitCloneEvent
    data object Start : GitCloneEvent
    data object DismissError : GitCloneEvent
    // Private-repo auth prompt, shown only when an unauthenticated clone attempt fails.
    data class ConfirmPrivateToken(val username: String, val token: String) : GitCloneEvent
    data object DismissPrivateRepoDialog : GitCloneEvent
    data object ConfigureCredentials : GitCloneEvent
}
