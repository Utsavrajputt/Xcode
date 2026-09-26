package com.invictus.xcode.feature.git

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.style.TextOverflow
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
import com.invictus.xcode.core.git.model.GitErrorDetails
import com.invictus.xcode.core.git.model.GitRemoteInfo
import com.invictus.xcode.core.git.normalizeHost
import com.invictus.xcode.core.security.GitCredentialStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

// ============================== Remotes ======================================

class GitRemotesViewModel(
    projectPath: String,
    globalIdentityFile: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val session = GitSession(File(projectPath), globalIdentityFile, io)

    data class UiState(
        val loading: Boolean = true,
        val remotes: List<GitRemoteInfo> = emptyList(),
        val editTarget: String? = null,      // null + showEditor -> add
        val showEditor: Boolean = false,
        val editorName: String = "",
        val editorUrl: String = "",
        val renameTarget: String? = null,
        val renameName: String = "",
        val deleteTarget: String? = null,
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
            when (val r = session.listRemotes()) {
                is GitResult.Ok -> _ui.update { it.copy(loading = false, remotes = r.value) }
                is GitResult.Err -> _ui.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    fun openAdd() = _ui.update {
        it.copy(showEditor = true, editTarget = null, editorName = "", editorUrl = "")
    }

    fun openEdit(remote: GitRemoteInfo) = _ui.update {
        it.copy(showEditor = true, editTarget = remote.name, editorName = remote.name, editorUrl = remote.url)
    }

    fun onEditorName(v: String) = _ui.update { it.copy(editorName = v) }
    fun onEditorUrl(v: String) = _ui.update { it.copy(editorUrl = v) }
    fun dismissEditor() = _ui.update { it.copy(showEditor = false) }

    fun saveEditor() {
        val name = _ui.value.editorName.trim()
        val url = _ui.value.editorUrl.trim()
        if (name.isEmpty() || url.isEmpty()) return
        viewModelScope.launch(io) {
            val r = when (val target = _ui.value.editTarget) {
                null -> session.addRemote(name, url)
                else -> session.setRemoteUrl(target, url)
            }
            when (r) {
                is GitResult.Ok -> {
                    _ui.update { it.copy(showEditor = false) }
                    _messages.value = "Remote saved"
                    load()
                }
                is GitResult.Err -> _ui.update { it.copy(error = r.error) }
            }
        }
    }

    fun openRename(remote: GitRemoteInfo) =
        _ui.update { it.copy(renameTarget = remote.name, renameName = remote.name) }

    fun onRenameChange(v: String) = _ui.update { it.copy(renameName = v) }
    fun dismissRename() = _ui.update { it.copy(renameTarget = null) }

    fun rename() {
        val target = _ui.value.renameTarget ?: return
        val name = _ui.value.renameName.trim()
        if (name.isEmpty() || name == target) return
        viewModelScope.launch(io) {
            when (val r = session.renameRemote(target, name)) {
                is GitResult.Ok -> {
                    _ui.update { it.copy(renameTarget = null) }
                    _messages.value = "Remote renamed"
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
            when (val r = session.removeRemote(target)) {
                is GitResult.Ok -> {
                    _ui.update { it.copy(deleteTarget = null) }
                    _messages.value = "Remote removed"
                    load()
                }
                is GitResult.Err -> _ui.update { it.copy(error = r.error) }
            }
        }
    }

    fun dismissError() = _ui.update { it.copy(error = null) }

    companion object {
        fun factory(projectPath: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as XcodeApp
                GitRemotesViewModel(projectPath, app.container.gitGlobalIdentityFile)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitRemotesScreen(
    projectPath: String,
    onBack: () -> Unit,
    onOpenCredentials: () -> Unit,
) {
    val vm: GitRemotesViewModel = viewModel(
        key = "remotes:$projectPath",
        factory = GitRemotesViewModel.factory(projectPath),
    )
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> msg?.let { snackbar.showSnackbar(it); vm.messageHandled() } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.git_remotes_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹") } },
                actions = {
                    TextButton(onClick = onOpenCredentials) {
                        Text(stringResource(R.string.git_remote_manage_tokens))
                    }
                    TextButton(onClick = { vm.openAdd() }) {
                        Text(stringResource(R.string.git_remote_add))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(ui.remotes, key = { it.name }) { r ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(r.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            r.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row {
                            TextButton(onClick = { vm.openEdit(r) }) {
                                Text(stringResource(R.string.git_remote_edit))
                            }
                            TextButton(onClick = { vm.openRename(r) }) {
                                Text(stringResource(R.string.git_remote_rename))
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.openDelete(r.name) }) {
                                Text(
                                    stringResource(R.string.git_remote_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
            if (!ui.loading && ui.remotes.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.git_remotes_empty),
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (ui.showEditor) {
        GitFieldsDialog(
            title = stringResource(
                if (ui.editTarget == null) R.string.git_remote_add else R.string.git_remote_edit,
            ),
            fields = listOf(
                stringResource(R.string.git_remote_name_hint) to ui.editorName,
                stringResource(R.string.git_remote_url_hint) to ui.editorUrl,
            ),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { v, _ ->
                vm.onEditorName(v[0])
                vm.onEditorUrl(v[1])
                vm.saveEditor()
            },
            onDismiss = vm::dismissEditor,
        )
    }
    ui.renameTarget?.let { target ->
        GitFieldsDialog(
            title = stringResource(R.string.git_remote_rename),
            fields = listOf(stringResource(R.string.git_remote_name_hint) to ui.renameName),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { v, _ -> vm.onRenameChange(v[0]); vm.rename() },
            onDismiss = vm::dismissRename,
        )
    }
    ui.deleteTarget?.let { target ->
        GitConfirmDialog(
            title = stringResource(R.string.git_remote_delete),
            text = stringResource(R.string.git_remote_delete_confirm, target),
            confirmLabel = stringResource(R.string.git_remote_delete),
            danger = true,
            onConfirm = vm::delete,
            onDismiss = vm::dismissDelete,
        )
    }
    ui.error?.let { GitErrorDialog(details = it, onDismiss = vm::dismissError) }
}

// ========================= Credentials manager ================================

data class CredentialEntry(
    val host: String,
    val username: String,
    val linkedRemotes: List<String>,   // repo remotes whose URL host matches
)

class GitCredentialsViewModel(
    projectPath: String,
    private val credentialStore: GitCredentialStore,
    globalIdentityFile: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val session = GitSession(File(projectPath), globalIdentityFile, io)

    data class UiState(
        val entries: List<CredentialEntry> = emptyList(),
        val showEditor: Boolean = false,
        val editHost: String? = null,     // null -> add
        val editorHost: String = "",
        val editorUser: String = "",
        val editorToken: String = "",
        val deleteTarget: String? = null,
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
            val remotes = (session.listRemotes() as? GitResult.Ok)?.value.orEmpty()
            val entries = credentialStore.hosts().map { host ->
                val cred = credentialStore.get(host)
                CredentialEntry(
                    host = host,
                    username = cred?.username.orEmpty(),
                    linkedRemotes = remotes
                        .filter { runCatching { normalizeHost(it.url) }.getOrDefault("") == host }
                        .map { it.name },
                )
            }
            _ui.update { it.copy(entries = entries) }
        }
    }

    fun openAdd() = _ui.update {
        it.copy(
            showEditor = true, editHost = null, editorHost = "",
            editorUser = "x-access-token", editorToken = "",
        )
    }

    fun openEdit(entry: CredentialEntry) = _ui.update {
        it.copy(
            showEditor = true, editHost = entry.host, editorHost = entry.host,
            editorUser = entry.username, editorToken = "",
        )
    }

    fun onHost(v: String) = _ui.update { it.copy(editorHost = v) }
    fun onUser(v: String) = _ui.update { it.copy(editorUser = v) }
    fun onToken(v: String) = _ui.update { it.copy(editorToken = v) }
    fun dismissEditor() = _ui.update { it.copy(showEditor = false) }

    fun save() {
        val host = (_ui.value.editHost ?: _ui.value.editorHost).trim().lowercase()
        val token = _ui.value.editorToken
        if (host.isEmpty() || token.isBlank()) return
        credentialStore.put(
            GitCredential(
                host = host,
                username = _ui.value.editorUser.ifBlank { "x-access-token" },
                token = token,
            ),
        )
        _ui.update { it.copy(showEditor = false, editHost = null) }
        _messages.value = "Token saved for $host"
        load()
    }

    fun openDelete(host: String) = _ui.update { it.copy(deleteTarget = host) }
    fun dismissDelete() = _ui.update { it.copy(deleteTarget = null) }

    fun delete() {
        val target = _ui.value.deleteTarget ?: return
        credentialStore.remove(target)
        _ui.update { it.copy(deleteTarget = null) }
        _messages.value = "Token removed for $target"
        load()
    }

    fun dismissError() = _ui.update { it.copy(error = null) }

    companion object {
        fun factory(projectPath: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as XcodeApp
                GitCredentialsViewModel(
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
fun GitCredentialsScreen(projectPath: String, onBack: () -> Unit) {
    val vm: GitCredentialsViewModel = viewModel(
        key = "creds:$projectPath",
        factory = GitCredentialsViewModel.factory(projectPath),
    )
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> msg?.let { snackbar.showSnackbar(it); vm.messageHandled() } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.git_credentials_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹") } },
                actions = {
                    TextButton(onClick = { vm.openAdd() }) {
                        Text(stringResource(R.string.git_cred_add))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(ui.entries, key = { it.host }) { e ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(e.host, style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (e.linkedRemotes.isEmpty()) {
                                e.username
                            } else {
                                "${e.username} · ${e.linkedRemotes.joinToString()}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row {
                            TextButton(onClick = { vm.openEdit(e) }) {
                                Text(stringResource(R.string.git_cred_edit))
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { vm.openDelete(e.host) }) {
                                Text(
                                    stringResource(R.string.git_cred_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
            if (ui.entries.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.git_credentials_empty),
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (ui.showEditor) {
        GitFieldsDialog(
            title = stringResource(
                if (ui.editHost == null) R.string.git_cred_add else R.string.git_cred_edit,
            ),
            fields = listOf(
                stringResource(R.string.git_cred_host_hint) to ui.editorHost,
                stringResource(R.string.git_cred_user_hint) to ui.editorUser,
                stringResource(R.string.git_cred_token_hint) to ui.editorToken,
            ),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { v, _ ->
                vm.onHost(v[0])
                vm.onUser(v[1])
                vm.onToken(v[2])
                vm.save()
            },
            onDismiss = vm::dismissEditor,
        )
    }
    ui.deleteTarget?.let { target ->
        GitConfirmDialog(
            title = stringResource(R.string.git_cred_delete),
            text = stringResource(R.string.git_cred_delete_confirm, target),
            confirmLabel = stringResource(R.string.git_cred_delete),
            danger = true,
            onConfirm = vm::delete,
            onDismiss = vm::dismissDelete,
        )
    }
    ui.error?.let { GitErrorDialog(details = it, onDismiss = vm::dismissError) }
}
