package com.invictus.xcode.feature.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.xcode.R
import com.invictus.xcode.XcodeApp
import com.invictus.xcode.core.git.GitCredential
import com.invictus.xcode.core.git.GitRepoWatcher
import com.invictus.xcode.core.git.GitResult
import com.invictus.xcode.core.git.GitSession
import com.invictus.xcode.core.git.GitTokenCredentialsProvider
import com.invictus.xcode.core.git.normalizeHost
import com.invictus.xcode.core.git.model.GitAuthFailureType
import com.invictus.xcode.core.git.model.GitConflictSide
import com.invictus.xcode.core.git.model.MergeOutcome
import com.invictus.xcode.core.git.model.GitErrorDetails
import com.invictus.xcode.core.git.model.GitFileDiffResult
import com.invictus.xcode.core.git.model.GitCommitSummary
import com.invictus.xcode.core.git.model.GitPendingAction
import com.invictus.xcode.core.git.model.GitRemoteInfo
import com.invictus.xcode.core.git.model.GitRepoSnapshot
import com.invictus.xcode.core.git.model.GitResetMode
import com.invictus.xcode.core.git.model.GitStageState
import com.invictus.xcode.core.git.model.GitWorkingState
import com.invictus.xcode.core.git.model.GitWorkingTreeStatus
import com.invictus.xcode.core.git.model.RebaseOutcome
import com.invictus.xcode.core.security.GitCredentialStore
import com.invictus.xcode.feature.workspace.UiText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eclipse.jgit.transport.CredentialsProvider
import java.io.File

/**
 * Source Control screen state (M6): snapshot + status after every op, network ops
 * gated through [runNetwork] which resolves per-host tokens and surfaces an
 * auth-retry dialog on 401/403 instead of a dead end.
 */
class GitViewModel(
    private val projectPath: String,
    private val credentialStore: GitCredentialStore,
    globalIdentityFile: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val session = GitSession(File(projectPath), globalIdentityFile, io)

    data class IdentityDialogState(
        val name: String,
        val email: String,
        val isLocal: Boolean,
    )

    data class TokenDialogState(
        val host: String,
        val username: String,
        /** Which op to re-run once a token is saved; null = just store it. */
        val pending: GitPendingAction?,
    )

    data class ConflictPreviewState(
        val path: String,
        val side: GitConflictSide,
        val content: String,
    )

    /** A reset (mode + target) waiting on the "this discards changes" confirmation. */
    data class PendingReset(val ref: String, val label: String, val mode: GitResetMode)

    data class UiState(
        val loading: Boolean = true,
        val notARepo: Boolean = false,
        val snapshot: GitRepoSnapshot? = null,
        val status: GitWorkingTreeStatus? = null,
        val remotes: List<GitRemoteInfo> = emptyList(),
        val networkOp: GitPendingAction? = null,
        val committing: Boolean = false,
        val progressTask: String = "",
        val commitMessage: String = "",
        val amend: Boolean = false,
        val identity: IdentityDialogState? = null,
        val tokenDialog: TokenDialogState? = null,
        val error: GitErrorDetails? = null,
        // M7 drawer
        val expandedSections: Set<String> = setOf(SECTION_CONFLICTS, SECTION_STAGED, SECTION_CHANGES),
        val diffs: Map<String, GitFileDiffResult> = emptyMap(),
        val diffLoading: Set<String> = emptySet(),
        // M10 merge + conflicts
        val mergeDialog: Boolean = false,
        val mergeCandidates: List<String> = emptyList(),
        val merging: Boolean = false,
        val abortConfirm: Boolean = false,
        val completeMergeDialog: Boolean = false,
        val completeMergeMessage: String = "",
        val completingMerge: Boolean = false,
        val conflictPreview: ConflictPreviewState? = null,
        /** Long-press "Discard changes" confirmation, pending [GitEvent.ConfirmDiscard]. */
        val discardConfirm: DiscardConfirmState? = null,
        // Reset (soft/mixed/hard)
        val resetSheetOpen: Boolean = false,
        val resetCommits: List<GitCommitSummary> = emptyList(),
        val resetCommitsLoading: Boolean = false,
        val resetHardConfirm: PendingReset? = null,
        val resetting: Boolean = false,
        // M11: rebase
        val abortRebaseConfirm: Boolean = false,
        val rebasePicker: Boolean = false,
        val rebaseCandidates: List<String> = emptyList(),
        val rebasing: Boolean = false,
    )

    data class DiscardConfirmState(val path: String, val isUntracked: Boolean)

    sealed interface Effect {
        data class Message(val text: UiText) : Effect
        /** The drawer asked to open a repo-relative path in the editor. */
        data class OpenFile(val file: java.io.File) : Effect
    }

    private var lastConflictCount = 0

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /** External changes (Termux, another app) picked up by [repoWatcher]; refreshed debounced. */
    private val externalChangeEvents = Channel<Unit>(Channel.UNLIMITED)
    private val repoWatcher = GitRepoWatcher { externalChangeEvents.trySend(Unit) }

    init {
        if (!session.isRepo) {
            _uiState.update { it.copy(loading = false, notARepo = true) }
        } else {
            val memCached = statusCache[projectPath]
            if (memCached != null) {
                applyCached(memCached)
            } else {
                // No in-process cache (first visit this run, or process was killed) —
                // fall back to the small on-disk snapshot from the last successful
                // refresh, if any, so a cold start also shows something instantly.
                viewModelScope.launch(io) {
                    loadDiskCache(projectPath)?.let { disk ->
                        statusCache[projectPath] = disk
                        if (_uiState.value.snapshot == null) applyCached(disk)
                    }
                }
            }
            refresh()
            repoWatcher.start(File(projectPath, ".git"))
            viewModelScope.launch { debounceExternalChanges() }
        }
    }

    private fun applyCached(cached: CachedStatus) {
        _uiState.update {
            it.copy(
                loading = false,
                snapshot = cached.snapshot,
                status = cached.status,
                remotes = cached.remotes,
            )
        }
    }

    /** Mirrors FileTreeViewModel's debounceWatcherEvents: coalesce a burst into one refresh. */
    private suspend fun debounceExternalChanges() {
        while (true) {
            externalChangeEvents.receive()
            delay(EXTERNAL_CHANGE_DEBOUNCE_MS)
            while (externalChangeEvents.tryReceive().isSuccess) { /* drain the rest of the burst */ }
            refresh()
        }
    }

    override fun onCleared() {
        repoWatcher.stop()
    }

    fun onEvent(event: GitEvent) {
        when (event) {
            GitEvent.Refresh -> refresh()
            GitEvent.StageAll -> stage(null)
            GitEvent.UnstageAll -> unstage(null)
            is GitEvent.Stage -> stage(event.path)
            is GitEvent.Unstage -> unstage(event.path)
            is GitEvent.RequestDiscard ->
                _uiState.update { it.copy(discardConfirm = DiscardConfirmState(event.path, event.isUntracked)) }
            GitEvent.ConfirmDiscard -> discard()
            GitEvent.DismissDiscard -> _uiState.update { it.copy(discardConfirm = null) }
            is GitEvent.CommitMessageChange ->
                _uiState.update { it.copy(commitMessage = event.text) }
            GitEvent.ToggleAmend -> _uiState.update { it.copy(amend = !it.amend) }
            GitEvent.Commit -> commit()
            GitEvent.Push -> push()
            GitEvent.Pull -> pull()
            GitEvent.Fetch -> fetch()
            is GitEvent.ToggleSection -> _uiState.update {
                it.copy(
                    expandedSections = if (event.name in it.expandedSections)
                        it.expandedSections - event.name else it.expandedSections + event.name,
                )
            }
            is GitEvent.LoadDiff -> loadDiff(event.path)
            is GitEvent.CloseDiff -> _uiState.update { it.copy(diffs = it.diffs - event.path) }
            is GitEvent.OpenFile ->
                _effects.trySend(Effect.OpenFile(java.io.File(projectPath, event.path)))
            GitEvent.OpenIdentity -> openIdentity()
            is GitEvent.SaveIdentity -> saveIdentity(event.name, event.email, event.isLocal)
            GitEvent.DismissIdentity -> _uiState.update { it.copy(identity = null) }
            is GitEvent.SaveToken -> saveToken(event.host, event.username, event.token)
            GitEvent.DismissToken -> _uiState.update { it.copy(tokenDialog = null) }
            GitEvent.DismissError -> _uiState.update { it.copy(error = null) }
            // M10: merge + conflicts
            GitEvent.OpenMerge -> openMergeDialog()
            GitEvent.DismissMerge ->
                _uiState.update { it.copy(mergeDialog = false, mergeCandidates = emptyList()) }
            is GitEvent.Merge -> merge(event.branch)
            is GitEvent.ResolveConflict -> resolveConflict(event.path, event.side)
            is GitEvent.MarkResolved -> markResolved(event.path)
            is GitEvent.PreviewConflictSide -> previewConflictSide(event.path, event.side)
            GitEvent.DismissConflictPreview ->
                _uiState.update { it.copy(conflictPreview = null) }
            GitEvent.AbortMerge -> _uiState.update { it.copy(abortConfirm = true) }
            GitEvent.ConfirmAbortMerge -> abortMerge()
            GitEvent.DismissAbortMerge -> _uiState.update { it.copy(abortConfirm = false) }
            GitEvent.CompleteMerge -> openCompleteMerge()
            is GitEvent.CompleteMergeMessageChange ->
                _uiState.update { it.copy(completeMergeMessage = event.text) }
            GitEvent.DismissCompleteMerge ->
                _uiState.update { it.copy(completeMergeDialog = false) }
            // ---- reset ----
            GitEvent.OpenReset -> _uiState.update { it.copy(resetSheetOpen = true) }
            GitEvent.DismissReset -> _uiState.update { it.copy(resetSheetOpen = false) }
            GitEvent.LoadResetCommits -> loadResetCommits()
            is GitEvent.ResetTo -> requestReset(event.ref, event.label, event.mode)
            GitEvent.ConfirmHardReset -> confirmHardReset()
            GitEvent.DismissHardResetConfirm ->
                _uiState.update { it.copy(resetHardConfirm = null) }
            // ---- rebase (continue/skip/abort) ----
            GitEvent.ContinueRebase -> continueRebase()
            GitEvent.SkipRebaseCommit -> skipRebaseCommit()
            GitEvent.AbortRebase -> _uiState.update { it.copy(abortRebaseConfirm = true) }
            GitEvent.ConfirmAbortRebase -> abortRebase()
            GitEvent.DismissAbortRebase -> _uiState.update { it.copy(abortRebaseConfirm = false) }
            GitEvent.OpenRebasePicker -> openRebasePicker()
            GitEvent.DismissRebasePicker ->
                _uiState.update { it.copy(rebasePicker = false, rebaseCandidates = emptyList()) }
            is GitEvent.RebaseOnto -> rebaseOnto(event.branch)
        }
    }

    // ---- refresh -----------------------------------------------------------

    private fun refresh() {
        viewModelScope.launch(io) {
            _uiState.update { it.copy(loading = it.snapshot == null) }
            when (val result = session.refreshFull()) {
                is GitResult.Ok -> {
                    val (snapshot, status, remotes) = result.value
                    val cached = CachedStatus(snapshot, status, remotes)
                    statusCache[projectPath] = cached
                    saveDiskCache(projectPath, cached)
                    val conflicts = status.conflicts.size
                    _uiState.update {
                        it.copy(
                            loading = false,
                            snapshot = snapshot,
                            status = status,
                            remotes = remotes,
                        )
                    }
                    if ((snapshot.mergeInProgress || snapshot.rebaseInProgress) &&
                        lastConflictCount > 0 && conflicts == 0
                    ) {
                        message(R.string.git_msg_all_conflicts_resolved)
                    }
                    lastConflictCount = conflicts
                }
                is GitResult.Err -> _uiState.update { it.copy(loading = false, error = result.error) }
            }
        }
    }

    // ---- stage ---------------------------------------------------------------

    /**
     * Bulk stage/unstage still does a full [refresh] afterward — too many paths change at
     * once to guess cheaply, and it also needs the fresh ahead/behind + remotes snapshot.
     * Single-path stage/unstage skips that: JGit's `status().call()` walk is what makes
     * this feel slow, and for one already-known path we can flip its state locally instead
     * of re-walking the whole tree just to learn something we already know the answer to.
     */
    private fun stage(path: String?) {
        viewModelScope.launch(io) {
            if (path == null) {
                when (val result = session.stageAll()) {
                    is GitResult.Ok -> refresh()
                    is GitResult.Err -> _uiState.update { it.copy(error = result.error) }
                }
                return@launch
            }
            when (val result = session.stage(path)) {
                is GitResult.Ok -> applyLocalStage(path)
                is GitResult.Err -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    private fun unstage(path: String?) {
        viewModelScope.launch(io) {
            if (path == null) {
                when (val result = session.unstageAll()) {
                    is GitResult.Ok -> refresh()
                    is GitResult.Err -> _uiState.update { it.copy(error = result.error) }
                }
                return@launch
            }
            when (val result = session.unstage(path)) {
                is GitResult.Ok -> applyLocalUnstage(path)
                is GitResult.Err -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    /** Flips [path]'s staged state locally to mirror what [GitSession.stage] just did. */
    private fun applyLocalStage(path: String) {
        _uiState.update { state ->
            val status = state.status ?: return@update state
            val changes = status.changes.map { change ->
                if (change.repoRelativePath != path) return@map change
                val staged = when (change.unstaged) {
                    GitWorkingState.UNTRACKED -> GitStageState.ADDED
                    GitWorkingState.DELETED -> GitStageState.DELETED
                    GitWorkingState.MODIFIED, GitWorkingState.NONE, GitWorkingState.CONFLICT ->
                        GitStageState.MODIFIED
                }
                // Staging clears the working-tree side too: `git add` snapshots the file
                // into the index, so there's nothing left un-added about it until it
                // changes again post-stage.
                change.copy(staged = staged, unstaged = GitWorkingState.NONE)
            }
            state.copy(status = status.copy(changes = changes))
        }
    }

    /** Flips [path]'s staged state back to NONE locally to mirror [GitSession.unstage]. */
    private fun applyLocalUnstage(path: String) {
        _uiState.update { state ->
            val status = state.status ?: return@update state
            val changes = status.changes.mapNotNull { change ->
                if (change.repoRelativePath != path) return@mapNotNull change
                // If the file was already showing further un-added changes on top of the
                // staged snapshot (staged MODIFIED + additionally edited since), that
                // working-tree state is unaffected by unstaging and must be kept as-is
                // rather than recomputed from `staged` alone.
                val unstaged = if (change.unstaged != GitWorkingState.NONE) {
                    change.unstaged
                } else when (change.staged) {
                    GitStageState.ADDED -> GitWorkingState.UNTRACKED
                    GitStageState.DELETED -> GitWorkingState.DELETED
                    GitStageState.MODIFIED, GitStageState.NONE, GitStageState.CONFLICT ->
                        GitWorkingState.MODIFIED
                }
                change.copy(staged = GitStageState.NONE, unstaged = unstaged)
            }
            state.copy(status = status.copy(changes = changes))
        }
    }

    private fun discard() {
        val target = _uiState.value.discardConfirm ?: return
        _uiState.update { it.copy(discardConfirm = null) }
        viewModelScope.launch(io) {
            when (val result = session.discard(target.path, target.isUntracked)) {
                is GitResult.Ok -> refresh()
                is GitResult.Err -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    // ---- commit ---------------------------------------------------------------

    private fun commit() {
        val message = _uiState.value.commitMessage.trim()
        if (message.isEmpty()) return
        viewModelScope.launch(io) {
            _uiState.update { it.copy(committing = true) }
            when (val result = session.commit(message, _uiState.value.amend)) {
                is GitResult.Ok -> {
                    _uiState.update { it.copy(commitMessage = "", amend = false) }
                    message(R.string.git_msg_committed)
                    refresh()
                }
                is GitResult.Err -> {
                    when {
                        result.error.exceptionClass.endsWith("EmptyCommitException") ->
                            message(R.string.git_err_nothing_to_commit)
                        result.error.exceptionClass.endsWith("GitIdentityMissingException") ->
                            openIdentity()
                        else -> _uiState.update { it.copy(error = result.error) }
                    }
                }
            }
            _uiState.update { it.copy(committing = false) }
        }
    }

    private fun loadDiff(path: String) {
        if (_uiState.value.diffs.containsKey(path) || path in _uiState.value.diffLoading) return
        viewModelScope.launch(io) {
            _uiState.update { it.copy(diffLoading = it.diffLoading + path) }
            when (val result = session.fileDiff(path)) {
                is GitResult.Ok -> _uiState.update {
                    it.copy(
                        diffs = it.diffs + (path to result.value),
                        diffLoading = it.diffLoading - path,
                    )
                }
                is GitResult.Err -> _uiState.update {
                    it.copy(diffLoading = it.diffLoading - path, error = result.error)
                }
            }
        }
    }

    // ---- network ---------------------------------------------------------------

    private fun push() {
        val snapshot = _uiState.value.snapshot ?: return
        if (!snapshot.hasCommits) return
        val remote = snapshot.trackingInfo?.remote ?: _uiState.value.remotes.firstOrNull()?.name
        if (remote == null) {
            message(R.string.git_err_no_remote)
            return
        }
        viewModelScope.launch(io) {
            runNetwork(GitPendingAction.PUSH, remote) { cp ->
                session.push(remote, cp) { p -> _uiState.update { it.copy(progressTask = p.task) } }
            }
        }
    }

    private fun pull() {
        val snapshot = _uiState.value.snapshot ?: return
        if (!snapshot.hasCommits) return
        val tracking = snapshot.trackingInfo
        val remote = tracking?.remote ?: _uiState.value.remotes.singleOrNull()?.name
        if (remote == null) {
            message(R.string.git_err_no_remote)
            return
        }
        val branch = tracking?.branch ?: snapshot.headName
        viewModelScope.launch(io) {
            runNetwork(GitPendingAction.PULL, remote) { cp ->
                session.pull(remote, branch, cp) { p -> _uiState.update { it.copy(progressTask = p.task) } }
            }
        }
    }

    private fun fetch() {
        val snapshot = _uiState.value.snapshot ?: return
        if (!snapshot.hasCommits) return
        val remote = snapshot.trackingInfo?.remote ?: _uiState.value.remotes.firstOrNull()?.name
        if (remote == null) {
            message(R.string.git_err_no_remote)
            return
        }
        viewModelScope.launch(io) {
            runNetwork(GitPendingAction.FETCH, remote) { cp ->
                session.fetch(remote, cp) { p -> _uiState.update { it.copy(progressTask = p.task) } }
            }
        }
    }

    private suspend fun runNetwork(
        action: GitPendingAction,
        remote: String,
        call: suspend (CredentialsProvider?) -> GitResult<Unit>,
    ) {
        val url = session.remoteUrl(remote)
        val host = url?.let(::normalizeHost)
        val credential = host?.let { credentialStore.get(it) }
        if (host != null && credential == null) {
            _uiState.update { it.copy(tokenDialog = TokenDialogState(host, DEFAULT_USERNAME, action)) }
            return
        }
        val provider = credential?.let { GitTokenCredentialsProvider(it.username, it.token) }
        _uiState.update { it.copy(networkOp = action, progressTask = "") }
        when (val result = call(provider)) {
            is GitResult.Ok -> {
                message(
                    when (action) {
                        GitPendingAction.PUSH -> R.string.git_msg_pushed
                        GitPendingAction.PULL -> R.string.git_msg_pulled
                        else -> R.string.git_msg_fetched
                    },
                )
                refresh()
            }
            is GitResult.Err -> {
                if (host != null && result.error.authFailure.isAuthError()) {
                    _uiState.update {
                        it.copy(
                            tokenDialog = TokenDialogState(
                                host,
                                credential?.username ?: DEFAULT_USERNAME,
                                action,
                            ),
                        )
                    }
                } else {
                    _uiState.update { it.copy(error = result.error) }
                }
            }
        }
        _uiState.update { it.copy(networkOp = null, progressTask = "") }
    }

    private fun GitAuthFailureType.isAuthError(): Boolean =
        this == GitAuthFailureType.AUTH_REQUIRED ||
            this == GitAuthFailureType.INVALID_CREDENTIALS ||
            this == GitAuthFailureType.EXPIRED_TOKEN ||
            this == GitAuthFailureType.PERMISSION_DENIED

    // ---- M10: merge + conflicts ------------------------------------------------

    private fun openMergeDialog() {
        val snapshot = _uiState.value.snapshot ?: return
        if (!snapshot.hasCommits || snapshot.mergeInProgress) return
        viewModelScope.launch(io) {
            _uiState.update { it.copy(mergeDialog = true, mergeCandidates = emptyList()) }
            when (val result = session.mergeCandidates()) {
                is GitResult.Ok -> _uiState.update { it.copy(mergeCandidates = result.value) }
                is GitResult.Err -> _uiState.update {
                    it.copy(mergeDialog = false, error = result.error)
                }
            }
        }
    }

    private fun merge(branch: String) {
        viewModelScope.launch(io) {
            _uiState.update { it.copy(merging = true, mergeDialog = false) }
            when (val result = session.mergeBranch(branch)) {
                is GitResult.Ok -> message(
                    when (result.value) {
                        MergeOutcome.FAST_FORWARD -> R.string.git_msg_merged_ff
                        MergeOutcome.MERGED -> R.string.git_msg_merged
                        MergeOutcome.ALREADY_UP_TO_DATE -> R.string.git_msg_merge_uptodate
                        MergeOutcome.CONFLICTS -> R.string.git_msg_merge_conflicts
                    },
                )
                is GitResult.Err -> _uiState.update { it.copy(error = result.error) }
            }
            _uiState.update { it.copy(merging = false, mergeCandidates = emptyList()) }
            refresh()
        }
    }

    private fun resolveConflict(path: String, side: GitConflictSide) {
        viewModelScope.launch(io) {
            when (val result = session.checkoutConflictSide(path, side)) {
                is GitResult.Ok -> refresh()
                is GitResult.Err -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    private fun markResolved(path: String) {
        viewModelScope.launch(io) {
            when (val result = session.markConflictResolved(path)) {
                is GitResult.Ok -> refresh()
                is GitResult.Err -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    private fun previewConflictSide(path: String, side: GitConflictSide) {
        viewModelScope.launch(io) {
            when (val result = session.conflictSideContent(path, side)) {
                is GitResult.Ok -> _uiState.update {
                    it.copy(conflictPreview = ConflictPreviewState(path, side, result.value))
                }
                is GitResult.Err -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    private fun abortMerge() {
        viewModelScope.launch(io) {
            _uiState.update { it.copy(abortConfirm = false) }
            when (val result = session.abortMerge()) {
                is GitResult.Ok -> {
                    message(R.string.git_msg_merge_aborted)
                    refresh()
                }
                is GitResult.Err -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    private fun openCompleteMerge() {
        viewModelScope.launch(io) {
            val saved = (session.mergeMessage() as? GitResult.Ok)?.value
            val head = _uiState.value.snapshot?.headName.orEmpty()
            _uiState.update {
                it.copy(
                    completeMergeDialog = true,
                    completeMergeMessage = saved ?: "Merge branch '$head'",
                )
            }
        }
    }

    private fun completeMerge() {
        val message = _uiState.value.completeMergeMessage.trim()
        if (message.isEmpty()) return
        viewModelScope.launch(io) {
            _uiState.update { it.copy(completingMerge = true) }
            when (val result = session.completeMerge(message)) {
                is GitResult.Ok -> {
                    _uiState.update { it.copy(completeMergeDialog = false) }
                    message(R.string.git_msg_committed)
                    refresh()
                }
                is GitResult.Err -> {
                    when {
                        result.error.exceptionClass.endsWith("GitIdentityMissingException") -> {
                            _uiState.update { it.copy(completeMergeDialog = false) }
                            openIdentity()
                        }
                        else -> _uiState.update { it.copy(error = result.error) }
                    }
                }
            }
            _uiState.update { it.copy(completingMerge = false) }
        }
    }

    // ---- M11: rebase (continue/skip/abort) --------------------------------------

    private fun continueRebase() {
        viewModelScope.launch(io) {
            when (val result = session.continueRebase()) {
                is GitResult.Ok -> {
                    message(rebaseOutcomeMessage(result.value))
                    refresh()
                }
                is GitResult.Err -> {
                    if (result.error.exceptionClass.endsWith("GitIdentityMissingException")) {
                        openIdentity()
                    } else {
                        _uiState.update { it.copy(error = result.error) }
                    }
                }
            }
        }
    }

    private fun skipRebaseCommit() {
        viewModelScope.launch(io) {
            when (val result = session.skipRebaseCommit()) {
                is GitResult.Ok -> {
                    message(rebaseOutcomeMessage(result.value))
                    refresh()
                }
                is GitResult.Err -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    private fun abortRebase() {
        viewModelScope.launch(io) {
            _uiState.update { it.copy(abortRebaseConfirm = false) }
            when (val result = session.abortRebase()) {
                is GitResult.Ok -> {
                    message(R.string.git_msg_rebase_aborted)
                    refresh()
                }
                is GitResult.Err -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    /** Started from the top bar: pick a branch to move the current branch's commits onto. */
    private fun openRebasePicker() {
        val snapshot = _uiState.value.snapshot ?: return
        if (!snapshot.hasCommits || snapshot.rebaseInProgress || snapshot.mergeInProgress) return
        viewModelScope.launch(io) {
            _uiState.update { it.copy(rebasePicker = true, rebaseCandidates = emptyList()) }
            when (val result = session.mergeCandidates()) {
                is GitResult.Ok -> _uiState.update { it.copy(rebaseCandidates = result.value) }
                is GitResult.Err -> _uiState.update {
                    it.copy(rebasePicker = false, error = result.error)
                }
            }
        }
    }

    private fun rebaseOnto(branch: String) {
        viewModelScope.launch(io) {
            _uiState.update { it.copy(rebasing = true, rebasePicker = false) }
            when (val result = session.rebaseOnto(branch)) {
                is GitResult.Ok -> message(rebaseOutcomeMessage(result.value))
                is GitResult.Err -> _uiState.update { it.copy(error = result.error) }
            }
            _uiState.update { it.copy(rebasing = false, rebaseCandidates = emptyList()) }
            refresh()
        }
    }

    private fun rebaseOutcomeMessage(outcome: RebaseOutcome): Int = when (outcome) {
        RebaseOutcome.FAST_FORWARD -> R.string.git_msg_rebase_ff
        RebaseOutcome.OK -> R.string.git_msg_rebase_ok
        RebaseOutcome.ALREADY_UP_TO_DATE -> R.string.git_msg_rebase_uptodate
        RebaseOutcome.CONFLICTS -> R.string.git_msg_rebase_conflicts
    }

    // ---- reset -------------------------------------------------------------

    private fun loadResetCommits() {
        if (_uiState.value.resetCommits.isNotEmpty() || _uiState.value.resetCommitsLoading) return
        viewModelScope.launch(io) {
            _uiState.update { it.copy(resetCommitsLoading = true) }
            when (val result = session.log(max = 100)) {
                is GitResult.Ok -> _uiState.update {
                    it.copy(resetCommits = result.value, resetCommitsLoading = false)
                }
                is GitResult.Err -> _uiState.update {
                    it.copy(resetCommitsLoading = false, error = result.error)
                }
            }
        }
    }

    /** Hard needs an explicit "this discards changes" confirm first; soft/mixed run right away. */
    private fun requestReset(ref: String, label: String, mode: GitResetMode) {
        if (mode == GitResetMode.HARD) {
            _uiState.update { it.copy(resetHardConfirm = PendingReset(ref, label, mode)) }
        } else {
            performReset(ref, label, mode)
        }
    }

    private fun confirmHardReset() {
        val pending = _uiState.value.resetHardConfirm ?: return
        _uiState.update { it.copy(resetHardConfirm = null) }
        performReset(pending.ref, pending.label, pending.mode)
    }

    private fun performReset(ref: String, label: String, mode: GitResetMode) {
        viewModelScope.launch(io) {
            _uiState.update { it.copy(resetting = true) }
            when (val result = session.resetTo(ref, mode)) {
                is GitResult.Ok -> {
                    _uiState.update {
                        it.copy(resetSheetOpen = false, resetCommits = emptyList())
                    }
                    message(R.string.git_reset_success, mode.label(), label)
                    refresh()
                }
                is GitResult.Err -> _uiState.update { it.copy(error = result.error) }
            }
            _uiState.update { it.copy(resetting = false) }
        }
    }

    private fun GitResetMode.label(): String = when (this) {
        GitResetMode.SOFT -> "soft"
        GitResetMode.MIXED -> "mixed"
        GitResetMode.HARD -> "hard"
    }

    // ---- identity / token dialogs ----------------------------------------------

    private fun openIdentity() {
        viewModelScope.launch(io) {
            val local = (session.getIdentity(local = true) as? GitResult.Ok)?.value
            val global = (session.getIdentity(local = false) as? GitResult.Ok)?.value
            val chosen = local ?: global
            _uiState.update {
                it.copy(
                    identity = IdentityDialogState(
                        name = chosen?.name ?: "",
                        email = chosen?.email ?: "",
                        isLocal = local != null,
                    ),
                )
            }
        }
    }

    private fun saveIdentity(name: String, email: String, isLocal: Boolean) {
        viewModelScope.launch(io) {
            when (val result = session.setIdentity(name, email, isLocal)) {
                is GitResult.Ok -> {
                    _uiState.update { it.copy(identity = null) }
                    message(R.string.git_msg_identity_saved)
                    refresh()
                }
                is GitResult.Err -> _uiState.update { it.copy(identity = null, error = result.error) }
            }
        }
    }

    private fun saveToken(host: String, username: String, token: String) {
        credentialStore.put(GitCredential(host, username.ifBlank { DEFAULT_USERNAME }, token))
        val pending = _uiState.value.tokenDialog?.pending
        _uiState.update { it.copy(tokenDialog = null) }
        message(R.string.git_msg_token_saved, host)
        when (pending) {
            GitPendingAction.PUSH -> push()
            GitPendingAction.PULL -> pull()
            GitPendingAction.FETCH -> fetch()
            else -> Unit
        }
    }

    private fun message(res: Int, vararg args: Any) {
        _effects.trySend(Effect.Message(UiText(res, args.toList())))
    }

    companion object {
        private const val DEFAULT_USERNAME = "x-access-token"
        private const val EXTERNAL_CHANGE_DEBOUNCE_MS = 400L

        const val SECTION_CONFLICTS = "conflicts"
        const val SECTION_STAGED = "staged"
        const val SECTION_CHANGES = "changes"
        const val SECTION_UNTRACKED = "untracked"

        /**
         * In-memory, per-process cache of each project's last known Source Control state.
         * [GitViewModel] gets a fresh instance every time its owning back-stack entry is
         * torn down — i.e. leaving the project and coming back — and building that state
         * from scratch (`refresh()`'s `session.refreshFull()`) costs exactly as much as
         * the very first load. Caching it here means a revisit shows the last-known state
         * immediately while a real [refresh] quietly catches up underneath.
         */
        private val statusCache =
            java.util.concurrent.ConcurrentHashMap<String, CachedStatus>()

        private data class CachedStatus(
            val snapshot: GitRepoSnapshot,
            val status: GitWorkingTreeStatus,
            val remotes: List<GitRemoteInfo>,
        )

        /** Where the on-disk snapshot for [projectPath] lives — next to git's own metadata. */
        private fun diskCacheFile(projectPath: String): File =
            File(projectPath, ".git/xcode-sc-cache.json")

        /**
         * Best-effort disk snapshot of the last known status, read on a cold start (fresh
         * app process, so [statusCache] is empty) before the real [GitSession.refreshFull]
         * lands. Any read/parse failure — missing file, corrupt JSON, schema mismatch after
         * an app update — is swallowed and just means no seed, never a crash.
         */
        private fun loadDiskCache(projectPath: String): CachedStatus? = runCatching {
            val file = diskCacheFile(projectPath)
            if (!file.isFile) return null
            val root = org.json.JSONObject(file.readText())

            val snapJson = root.getJSONObject("snapshot")
            val trackingJson = snapJson.optJSONObject("tracking")
            val snapshot = GitRepoSnapshot(
                gitRoot = File(projectPath),
                hasCommits = snapJson.getBoolean("hasCommits"),
                headId = snapJson.optString("headId", null),
                headName = snapJson.optString("headName", null),
                trackingInfo = trackingJson?.let {
                    GitTrackingInfo(
                        remote = it.getString("remote"),
                        branch = it.getString("branch"),
                        ahead = it.getInt("ahead"),
                        behind = it.getInt("behind"),
                    )
                },
                mergeInProgress = snapJson.getBoolean("mergeInProgress"),
                rebaseInProgress = snapJson.getBoolean("rebaseInProgress"),
            )

            val statusJson = root.getJSONObject("status")
            val changesJson = statusJson.getJSONArray("changes")
            val changes = (0 until changesJson.length()).map { i ->
                val c = changesJson.getJSONObject(i)
                GitPathChange(
                    repoRelativePath = c.getString("path"),
                    staged = GitStageState.valueOf(c.getString("staged")),
                    unstaged = GitWorkingState.valueOf(c.getString("unstaged")),
                )
            }
            val status = GitWorkingTreeStatus(
                changes = changes,
                hasUnmerged = statusJson.getBoolean("hasUnmerged"),
            )

            val remotesJson = root.getJSONArray("remotes")
            val remotes = (0 until remotesJson.length()).map { i ->
                val r = remotesJson.getJSONObject(i)
                GitRemoteInfo(name = r.getString("name"), url = r.getString("url"))
            }

            CachedStatus(snapshot, status, remotes)
        }.getOrNull()

        /** Mirror of [loadDiskCache]; failures are swallowed — losing the cache is fine. */
        private fun saveDiskCache(projectPath: String, cached: CachedStatus) {
            runCatching {
                val snapJson = org.json.JSONObject().apply {
                    put("hasCommits", cached.snapshot.hasCommits)
                    put("headId", cached.snapshot.headId)
                    put("headName", cached.snapshot.headName)
                    cached.snapshot.trackingInfo?.let {
                        put("tracking", org.json.JSONObject().apply {
                            put("remote", it.remote)
                            put("branch", it.branch)
                            put("ahead", it.ahead)
                            put("behind", it.behind)
                        })
                    }
                    put("mergeInProgress", cached.snapshot.mergeInProgress)
                    put("rebaseInProgress", cached.snapshot.rebaseInProgress)
                }

                val changesJson = org.json.JSONArray()
                cached.status.changes.forEach { c ->
                    changesJson.put(org.json.JSONObject().apply {
                        put("path", c.repoRelativePath)
                        put("staged", c.staged.name)
                        put("unstaged", c.unstaged.name)
                    })
                }
                val statusJson = org.json.JSONObject().apply {
                    put("changes", changesJson)
                    put("hasUnmerged", cached.status.hasUnmerged)
                }

                val remotesJson = org.json.JSONArray()
                cached.remotes.forEach { r ->
                    remotesJson.put(org.json.JSONObject().apply {
                        put("name", r.name)
                        put("url", r.url)
                    })
                }

                val root = org.json.JSONObject().apply {
                    put("snapshot", snapJson)
                    put("status", statusJson)
                    put("remotes", remotesJson)
                }
                diskCacheFile(projectPath).writeText(root.toString())
            }
        }

        fun factory(projectPath: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as XcodeApp
                GitViewModel(
                    projectPath = projectPath,
                    credentialStore = app.container.gitCredentialStore,
                    globalIdentityFile = app.container.gitGlobalIdentityFile,
                )
            }
        }
    }
}
