package com.invictus.xcode.feature.git

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.invictus.xcode.core.git.GitTrigger
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.xcode.R
import com.invictus.xcode.XcodeApp
import com.invictus.xcode.core.git.GitCredential
import com.invictus.xcode.core.git.GitResult
import com.invictus.xcode.core.git.GitSessionRegistry
import com.invictus.xcode.core.git.GitTokenCredentialsProvider
import com.invictus.xcode.core.git.model.GitAuthFailureType
import com.invictus.xcode.core.git.model.GitBranchDetail
import com.invictus.xcode.core.git.model.GitErrorDetails
import com.invictus.xcode.core.git.model.GitRemoteInfo
import com.invictus.xcode.core.security.GitCredentialStore
import com.invictus.xcode.ui.icons.XIcons
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class GitBranchesViewModel(
    private val projectPath: String,
    private val credentialStore: GitCredentialStore,
    globalIdentityFile: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val session = GitSessionRegistry.acquire(File(projectPath), globalIdentityFile, io)

    override fun onCleared() {
        GitSessionRegistry.release(File(projectPath))
    }

    data class UiState(
        val loading: Boolean = true,
        val branches: List<GitBranchDetail> = emptyList(),
        val remotes: List<GitRemoteInfo> = emptyList(),
        val busy: Boolean = false,
        val progressTask: String = "",
        val showCreate: Boolean = false,
        val createName: String = "",
        val renameTarget: String? = null,
        val renameName: String = "",
        val deleteTarget: String? = null,
        val forcePushConfirm: Boolean = false,
        val tokenHost: String? = null,
        val error: GitErrorDetails? = null,
        /** Background fetch of every remote is running (screen open), so remote branches stay current. */
        val fetching: Boolean = false,
        val query: String = "",
        val filter: BranchFilter = BranchFilter.LOCAL,
        /** Every remote-tracking ref ("origin/main"), for the "create from" picker. */
        val remoteBranches: List<String> = emptyList(),
        /** null = branch off the current HEAD; otherwise the remote chosen in the New branch dialog. */
        val createRemote: String? = null,
        val createBase: String? = null,
    )

    enum class BranchFilter { LOCAL, REMOTE }

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    init {
        load("screen-open")
        fetchAllQuietly()
    }

    fun onQueryChange(v: String) = _ui.update { it.copy(query = v) }
    fun setFilter(f: BranchFilter) = _ui.update { it.copy(filter = f) }

    /**
     * Fetches every remote with the saved token (no prompts, failures ignored) and reloads, so
     * branches that exist on GitHub but were never fetched here actually show up.
     */
    private fun fetchAllQuietly() {
        viewModelScope.launch(io + GitTrigger("screen-open-fetch")) {
            val remotes = (session.listRemotes() as? GitResult.Ok)?.value.orEmpty()
            if (remotes.isEmpty()) return@launch
            _ui.update { it.copy(fetching = true) }
            var fetchedAny = false
            for (r in remotes) {
                val host = session.remoteUrl(r.name)?.let { com.invictus.xcode.core.git.normalizeHost(it) }
                val cred = host?.let { credentialStore.get(it) }
                val provider = cred?.let { GitTokenCredentialsProvider(it.username, it.token) }
                // Private remote without a saved token: skip silently (public ones fetch anonymously).
                if (session.fetch(r.name, provider) is GitResult.Ok) fetchedAny = true
            }
            _ui.update { it.copy(fetching = false) }
            if (fetchedAny) load("after-fetch")
        }
    }

    fun messageHandled() { _messages.value = null }

    private var divergenceJob: Job? = null

    fun load(trigger: String = "after-op") {
        viewModelScope.launch(io + GitTrigger(trigger)) {
            _ui.update { it.copy(loading = it.branches.isEmpty()) }
            val remotes = (session.listRemotes() as? GitResult.Ok)?.value.orEmpty()
            val remoteRefs = (session.listRemoteBranches() as? GitResult.Ok)?.value.orEmpty()
            when (val r = session.listBranchesDetailed()) {
                is GitResult.Ok -> {
                    // Keep counts we already have for a branch so a reload doesn't flicker them away.
                    val prev = _ui.value.branches.associateBy { it.name }
                    val branches = r.value.map { b ->
                        prev[b.name]?.let { b.copy(ahead = it.ahead, behind = it.behind) } ?: b
                    }
                    _ui.update {
                        it.copy(loading = false, branches = branches, remotes = remotes, remoteBranches = remoteRefs)
                    }
                    fillDivergence(branches)
                }
                is GitResult.Err -> _ui.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    /** Background pass: ahead/behind per tracked branch, current branch first; the list is already on screen. */
    private fun fillDivergence(branches: List<GitBranchDetail>) {
        divergenceJob?.cancel()
        divergenceJob = viewModelScope.launch(io) {
            branches.filter { it.upstream != null && !it.isRemote }
                .sortedByDescending { it.isCurrent }
                .forEach { b ->
                    val counts = (session.branchDivergence(b.name) as? GitResult.Ok)?.value ?: return@forEach
                    _ui.update { st ->
                        st.copy(branches = st.branches.map {
                            if (it.name == b.name) it.copy(ahead = counts.first, behind = counts.second) else it
                        })
                    }
                }
        }
    }

    fun openCreate(name: String = "") =
        _ui.update { it.copy(showCreate = true, createName = name, createRemote = null, createBase = null) }

    /** null = current HEAD; a remote name = branch off one of that remote's branches (main/master first). */
    fun onCreateRemoteChange(remote: String?) = _ui.update { st ->
        val base = remote?.let { r ->
            val mine = st.remoteBranches.filter { it.startsWith("$r/") }
            mine.firstOrNull { it == "$r/master" } ?: mine.firstOrNull { it == "$r/main" } ?: mine.firstOrNull()
        }
        st.copy(createRemote = remote, createBase = base)
    }
    fun onCreateBaseChange(ref: String) = _ui.update { it.copy(createBase = ref) }
    fun onCreateNameChange(v: String) = _ui.update { it.copy(createName = v) }
    fun dismissCreate() = _ui.update { it.copy(showCreate = false) }

    fun create(checkout: Boolean) {
        val name = _ui.value.createName.trim()
        if (name.isEmpty()) return
        val start = _ui.value.takeIf { it.createRemote != null }?.createBase
        if (_ui.value.createRemote != null && start == null) {
            _messages.value = "No branches fetched for '${_ui.value.createRemote}' yet"
            return
        }
        viewModelScope.launch(io) {
            when (val r = session.createBranch(name, checkout, start)) {
                is GitResult.Ok -> {
                    _ui.update { it.copy(showCreate = false, createName = "") }
                    _messages.value = "Branch '$name' created"
                    load()
                }
                is GitResult.Err -> _ui.update { it.copy(error = r.error) }
            }
        }
    }

    fun checkout(b: GitBranchDetail) {
        val name = b.name
        viewModelScope.launch(io) {
            val result = if (b.isRemote) session.checkoutRemoteBranch(name)
            else session.checkoutBranch(name, create = false)
            when (val r = result) {
                is GitResult.Ok -> {
                    _messages.value = "Switched to '${if (b.isRemote) name.substringAfter('/') else name}'"
                    load()
                }
                is GitResult.Err -> _ui.update { it.copy(error = r.error) }
            }
        }
    }

    fun openRename(target: String) =
        _ui.update { it.copy(renameTarget = target, renameName = target) }
    fun onRenameChange(v: String) = _ui.update { it.copy(renameName = v) }
    fun dismissRename() = _ui.update { it.copy(renameTarget = null) }

    fun rename() {
        val target = _ui.value.renameTarget ?: return
        val name = _ui.value.renameName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch(io) {
            when (val r = session.renameBranch(target, name)) {
                is GitResult.Ok -> {
                    _ui.update { it.copy(renameTarget = null) }
                    _messages.value = "Branch renamed to '$name'"
                    load()
                }
                is GitResult.Err -> _ui.update { it.copy(error = r.error) }
            }
        }
    }

    fun openDelete(name: String) = _ui.update { it.copy(deleteTarget = name) }
    fun dismissDelete() = _ui.update { it.copy(deleteTarget = null) }

    fun delete() {
        val target = _ui.value.deleteTarget ?: return
        viewModelScope.launch(io) {
            // Not-merged branches throw; retry with force once.
            val first = session.deleteBranch(target, force = false)
            val r = if (first is GitResult.Err) session.deleteBranch(target, force = true) else first
            when (r) {
                is GitResult.Ok -> {
                    _ui.update { it.copy(deleteTarget = null) }
                    _messages.value = "Branch '$target' deleted"
                    load()
                }
                is GitResult.Err -> _ui.update { it.copy(error = r.error) }
            }
        }
    }

    // ---- force push (lease-checked in GitSession) -----------------------------

    fun askForcePush() = _ui.update { it.copy(forcePushConfirm = true) }
    fun dismissForcePush() = _ui.update { it.copy(forcePushConfirm = false) }

    fun forcePush() {
        _ui.update { it.copy(forcePushConfirm = false) }
        val current = _ui.value.branches.firstOrNull { it.isCurrent } ?: return
        val remote = current.upstream?.substringBefore('/')
            ?: _ui.value.remotes.firstOrNull()?.name
            ?: run { _messages.value = "No remote configured"; return }
        val host = session.remoteUrl(remote)?.let { com.invictus.xcode.core.git.normalizeHost(it) }
        val cred = host?.let { credentialStore.get(it) }
        if (host != null && cred == null) {
            _ui.update { it.copy(tokenHost = host) }
            return
        }
        viewModelScope.launch(io) {
            _ui.update { it.copy(busy = true, progressTask = "") }
            val provider = cred?.let { GitTokenCredentialsProvider(it.username, it.token) }
            when (val r = session.push(remote, provider, force = true) { p ->
                _ui.update { s -> s.copy(progressTask = p.task) }
            }) {
                is GitResult.Ok -> { _messages.value = "Force pushed to $remote"; load() }
                is GitResult.Err -> {
                    if (host != null && r.error.authFailure.isAuthError()) {
                        _ui.update { it.copy(tokenHost = host) }
                    } else {
                        _ui.update { it.copy(error = r.error) }
                    }
                }
            }
            _ui.update { it.copy(busy = false, progressTask = "") }
        }
    }

    fun saveToken(host: String, username: String, token: String) {
        credentialStore.put(GitCredential(host, username.ifBlank { DEFAULT_USER }, token))
        _ui.update { it.copy(tokenHost = null) }
        forcePush()
    }

    fun dismissToken() = _ui.update { it.copy(tokenHost = null) }
    fun dismissError() = _ui.update { it.copy(error = null) }

    private fun GitAuthFailureType.isAuthError(): Boolean =
        this == GitAuthFailureType.AUTH_REQUIRED ||
            this == GitAuthFailureType.INVALID_CREDENTIALS ||
            this == GitAuthFailureType.EXPIRED_TOKEN ||
            this == GitAuthFailureType.PERMISSION_DENIED

    companion object {
        private const val DEFAULT_USER = "x-access-token"
        fun factory(projectPath: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as XcodeApp
                GitBranchesViewModel(
                    projectPath = projectPath,
                    credentialStore = app.container.gitCredentialStore,
                    globalIdentityFile = app.container.gitGlobalIdentityFile,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitBranchesScreen(projectPath: String, onBack: () -> Unit) {
    val vm: GitBranchesViewModel = viewModel(
        key = "branches:$projectPath",
        factory = GitBranchesViewModel.factory(projectPath),
    )
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> msg?.let { snackbar.showSnackbar(it); vm.messageHandled() } }
    }

    val visible = remember(ui.branches, ui.query, ui.filter) {
        val q = ui.query.trim()
        ui.branches
            .filter { b ->
                when (ui.filter) {
                    GitBranchesViewModel.BranchFilter.LOCAL -> !b.isRemote
                    GitBranchesViewModel.BranchFilter.REMOTE -> b.isRemote
                }
            }
            .filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
            .sortedWith(compareBy({ it.isRemote }, { !it.isCurrent }))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.git_branches_title)) },
                // Plain arrow: no container / highlight behind it.
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    Button(
                        onClick = { vm.openCreate() },
                        contentPadding = PaddingValues(start = 10.dp, end = 14.dp),
                        modifier = Modifier.padding(end = 12.dp).height(34.dp),
                    ) {
                        Icon(XIcons.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.git_branch_create))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (ui.busy || ui.fetching) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                if (ui.busy && ui.progressTask.isNotBlank()) {
                    Text(
                        ui.progressTask,
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            OutlinedTextField(
                value = ui.query,
                onValueChange = vm::onQueryChange,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.git_branch_search_hint)) },
                leadingIcon = { Icon(XIcons.Search, contentDescription = null) },
                trailingIcon = {
                    if (ui.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) {
                            Icon(XIcons.Close, contentDescription = null)
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    GitBranchesViewModel.BranchFilter.LOCAL to R.string.git_branch_filter_local,
                    GitBranchesViewModel.BranchFilter.REMOTE to R.string.git_branch_filter_remote,
                ).forEach { (f, label) ->
                    FilterChip(
                        selected = ui.filter == f,
                        onClick = { vm.setFilter(f) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = { (if (it.isRemote) "r:" else "l:") + it.name }) { b ->
                    BranchCard(
                        b = b,
                        onForcePush = vm::askForcePush,
                        onCheckout = { vm.checkout(b) },
                        onRename = { vm.openRename(b.name) },
                        onDelete = { vm.openDelete(b.name) },
                    )
                }
                if (!ui.loading) {
                    item(key = "footer") {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                if (visible.isEmpty()) stringResource(R.string.git_branches_empty)
                                else stringResource(R.string.git_branches_found, visible.size),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                stringResource(R.string.git_branches_found_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (ui.showCreate) {
        BranchCreateDialog(
            ui = ui,
            onRemoteChange = vm::onCreateRemoteChange,
            onBaseChange = vm::onCreateBaseChange,
            onConfirm = { name, checkout ->
                vm.onCreateNameChange(name)
                vm.create(checkout)
            },
            onDismiss = vm::dismissCreate,
        )
    }
    ui.renameTarget?.let {
        GitFieldsDialog(
            title = stringResource(R.string.git_branch_rename),
            fields = listOf(stringResource(R.string.git_branch_rename_hint) to ui.renameName),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { values, _ ->
                vm.onRenameChange(values[0])
                vm.rename()
            },
            onDismiss = vm::dismissRename,
        )
    }
    ui.deleteTarget?.let { target ->
        GitConfirmDialog(
            title = stringResource(R.string.git_branch_delete),
            text = stringResource(R.string.git_branch_delete_confirm, target),
            confirmLabel = stringResource(R.string.git_branch_delete),
            danger = true,
            onConfirm = vm::delete,
            onDismiss = vm::dismissDelete,
        )
    }
    if (ui.forcePushConfirm) {
        GitConfirmDialog(
            title = stringResource(R.string.git_force_push),
            text = stringResource(R.string.git_force_push_confirm_text),
            confirmLabel = stringResource(R.string.git_force_push),
            danger = true,
            onConfirm = vm::forcePush,
            onDismiss = vm::dismissForcePush,
        )
    }
    ui.tokenHost?.let { host ->
        GitFieldsDialog(
            title = "Token: $host",
            fields = listOf("Username" to "x-access-token", "Token" to ""),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { v, _ -> vm.saveToken(host, v[0], v[1]) },
            onDismiss = vm::dismissToken,
        )
    }
    ui.error?.let { GitErrorDialog(details = it, onDismiss = vm::dismissError) }
}


@Composable
private fun BranchCard(
    b: GitBranchDetail,
    onForcePush: () -> Unit,
    onCheckout: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 14.dp, end = if (b.isRemote) 14.dp else 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(36.dp).background(
                    if (b.isRemote) MaterialTheme.colorScheme.secondaryContainer
                    else MaterialTheme.colorScheme.primaryContainer,
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (b.isRemote) XIcons.Cloud else XIcons.AccountTree,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (b.isRemote) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    b.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (b.isCurrent) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (b.isRemote) {
                    Text(
                        stringResource(R.string.git_branch_remote_only),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                } else {
                    // Same left edge as the branch name (no pill padding pushing it in).
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            b.upstream ?: stringResource(R.string.git_upstream_none),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = if (b.upstream != null) FontFamily.Monospace else null,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (b.upstream != null && b.ahead != null && b.behind != null) {
                            Text(
                                "  ↑${b.ahead} ↓${b.behind}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                onClick = if (b.isCurrent) onForcePush else onCheckout,
                contentPadding = PaddingValues(horizontal = 14.dp),
                modifier = Modifier.height(36.dp),
            ) {
                Text(
                    stringResource(if (b.isCurrent) R.string.git_force_push else R.string.git_branch_checkout),
                    maxLines = 1,
                )
            }
            if (!b.isRemote) {
                var menuOpen by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(XIcons.MoreVert, contentDescription = stringResource(R.string.git_branch_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.git_branch_rename)) },
                            onClick = { menuOpen = false; onRename() },
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.git_branch_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = { menuOpen = false; onDelete() },
                        )
                    }
                }
            }
        }
    }
}

/** New branch: name + where it starts from (current HEAD, or a branch of a chosen remote). */
@Composable
private fun BranchCreateDialog(
    ui: GitBranchesViewModel.UiState,
    onRemoteChange: (String?) -> Unit,
    onBaseChange: (String) -> Unit,
    onConfirm: (name: String, checkout: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(ui.createName) }
    var checkout by remember { mutableStateOf(true) }
    var baseMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val remote = ui.createRemote
    val remoteBranches = remember(remote, ui.remoteBranches) {
        if (remote == null) emptyList() else ui.remoteBranches.filter { it.startsWith("$remote/") }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.git_branch_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.git_branch_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                Text(
                    stringResource(R.string.git_branch_create_from),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = remote == null,
                        onClick = { onRemoteChange(null) },
                        label = { Text(stringResource(R.string.git_branch_from_head)) },
                    )
                    ui.remotes.forEach { r ->
                        FilterChip(
                            selected = remote == r.name,
                            onClick = { onRemoteChange(r.name) },
                            label = { Text(r.name) },
                        )
                    }
                }
                if (remote != null) {
                    Box {
                        OutlinedButton(
                            onClick = { baseMenu = true },
                            enabled = remoteBranches.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                ui.createBase ?: stringResource(R.string.git_branch_from_none),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(XIcons.KeyboardArrowDown, contentDescription = null)
                        }
                        DropdownMenu(expanded = baseMenu, onDismissRequest = { baseMenu = false }) {
                            remoteBranches.forEach { ref ->
                                DropdownMenuItem(
                                    text = { Text(ref) },
                                    onClick = { baseMenu = false; onBaseChange(ref) },
                                )
                            }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checkout, onCheckedChange = { checkout = it })
                    Text(stringResource(R.string.git_branch_checkout))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, checkout) },
                enabled = name.isNotBlank() && (remote == null || ui.createBase != null),
            ) { Text(stringResource(R.string.git_branch_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
