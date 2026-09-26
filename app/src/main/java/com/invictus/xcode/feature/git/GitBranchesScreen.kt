package com.invictus.xcode.feature.git

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
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
import com.invictus.xcode.core.git.GitSession
import com.invictus.xcode.core.git.GitTokenCredentialsProvider
import com.invictus.xcode.core.git.model.GitAuthFailureType
import com.invictus.xcode.core.git.model.GitBranchDetail
import com.invictus.xcode.core.git.model.GitErrorDetails
import com.invictus.xcode.core.git.model.GitRemoteInfo
import com.invictus.xcode.core.security.GitCredentialStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class GitBranchesViewModel(
    projectPath: String,
    private val credentialStore: GitCredentialStore,
    globalIdentityFile: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val session = GitSession(File(projectPath), globalIdentityFile, io)

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
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    init { load() }

    fun messageHandled() { _messages.value = null }

    fun load() {
        viewModelScope.launch(io) {
            _ui.update { it.copy(loading = it.branches.isEmpty()) }
            val remotes = (session.listRemotes() as? GitResult.Ok)?.value.orEmpty()
            when (val r = session.listBranchesDetailed()) {
                is GitResult.Ok -> _ui.update {
                    it.copy(loading = false, branches = r.value, remotes = remotes)
                }
                is GitResult.Err -> _ui.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    fun openCreate(name: String = "") = _ui.update { it.copy(showCreate = true, createName = name) }
    fun onCreateNameChange(v: String) = _ui.update { it.copy(createName = v) }
    fun dismissCreate() = _ui.update { it.copy(showCreate = false) }

    fun create(checkout: Boolean) {
        val name = _ui.value.createName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch(io) {
            when (val r = session.createBranch(name, checkout)) {
                is GitResult.Ok -> {
                    _ui.update { it.copy(showCreate = false, createName = "") }
                    _messages.value = "Branch '$name' created"
                    load()
                }
                is GitResult.Err -> _ui.update { it.copy(error = r.error) }
            }
        }
    }

    fun checkout(name: String) {
        viewModelScope.launch(io) {
            when (val r = session.checkoutBranch(name, create = false)) {
                is GitResult.Ok -> { _messages.value = "Switched to '$name'"; load() }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.git_branches_title)) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("‹") }
                },
                actions = {
                    TextButton(onClick = { vm.openCreate() }) {
                        Text(stringResource(R.string.git_branch_create))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (ui.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                if (ui.progressTask.isNotBlank()) {
                    Text(
                        ui.progressTask,
                        Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(ui.branches, key = { it.name }) { b ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    b.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (b.isCurrent) FontWeight.Bold else FontWeight.Normal,
                                )
                                Text(
                                    when {
                                        b.upstream != null -> "$b.upstream ↑${b.ahead} ↓${b.behind}"
                                        else -> stringResource(R.string.git_upstream_none)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (b.isCurrent) {
                                TextButton(onClick = { vm.askForcePush() }) {
                                    Text(stringResource(R.string.git_force_push))
                                }
                            } else {
                                TextButton(onClick = { vm.checkout(b.name) }) {
                                    Text(stringResource(R.string.git_branch_checkout))
                                }
                            }
                            TextButton(onClick = { vm.openRename(b.name) }) {
                                Text(stringResource(R.string.git_branch_rename))
                            }
                            TextButton(onClick = { vm.openDelete(b.name) }) {
                                Text(
                                    stringResource(R.string.git_branch_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (ui.showCreate) {
        GitFieldsDialog(
            title = stringResource(R.string.git_branch_create),
            fields = listOf(stringResource(R.string.git_branch_name_hint) to ui.createName),
            confirmLabel = stringResource(R.string.git_branch_create),
            showCheckbox = stringResource(R.string.git_branch_checkout) to true,
            onConfirm = { values, checked ->
                vm.onCreateNameChange(values[0])
                vm.create(checked)
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
