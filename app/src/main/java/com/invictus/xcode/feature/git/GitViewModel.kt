package com.invictus.xcode.feature.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.xcode.R
import com.invictus.xcode.XcodeApp
import com.invictus.xcode.core.git.GitCredential
import com.invictus.xcode.core.git.GitResult
import com.invictus.xcode.core.git.GitSession
import com.invictus.xcode.core.git.GitTokenCredentialsProvider
import com.invictus.xcode.core.git.normalizeHost
import com.invictus.xcode.core.git.model.GitAuthFailureType
import com.invictus.xcode.core.git.model.GitConflictSide
import com.invictus.xcode.core.git.model.MergeOutcome
import com.invictus.xcode.core.git.model.GitErrorDetails
import com.invictus.xcode.core.git.model.GitFileDiffResult
import com.invictus.xcode.core.git.model.GitPendingAction
import com.invictus.xcode.core.git.model.GitRemoteInfo
import com.invictus.xcode.core.git.model.GitRepoSnapshot
import com.invictus.xcode.core.git.model.GitWorkingTreeStatus
import com.invictus.xcode.core.security.GitCredentialStore
import com.invictus.xcode.feature.workspace.UiText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
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

    init {
        if (!session.isRepo) {
            _uiState.update { it.copy(loading = false, notARepo = true) }
        } else {
            refresh()
        }
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
        }
    }

    // ---- refresh -----------------------------------------------------------

    private fun refresh() {
        viewModelScope.launch(io) {
            _uiState.update { it.copy(loading = it.snapshot == null) }
            val remotes = (session.listRemotes() as? GitResult.Ok)?.value.orEmpty()
            when (val result = session.refresh()) {
                is GitResult.Ok -> {
                    val conflicts = result.value.second.conflicts.size
                    _uiState.update {
                        it.copy(
                            loading = false,
                            snapshot = result.value.first,
                            status = result.value.second,
                            remotes = remotes,
                        )
                    }
                    if (result.value.first.mergeInProgress &&
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

    private fun stage(path: String?) {
        viewModelScope.launch(io) {
            val result = if (path == null) session.stageAll() else session.stage(path)
            when (result) {
                is GitResult.Ok -> refresh()
                is GitResult.Err -> _uiState.update { it.copy(error = result.error) }
            }
        }
    }

    private fun unstage(path: String?) {
        viewModelScope.launch(io) {
            val result = if (path == null) session.unstageAll() else session.unstage(path)
            when (result) {
                is GitResult.Ok -> refresh()
                is GitResult.Err -> _uiState.update { it.copy(error = result.error) }
            }
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

        const val SECTION_CONFLICTS = "conflicts"
        const val SECTION_STAGED = "staged"
        const val SECTION_CHANGES = "changes"
        const val SECTION_UNTRACKED = "untracked"

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
