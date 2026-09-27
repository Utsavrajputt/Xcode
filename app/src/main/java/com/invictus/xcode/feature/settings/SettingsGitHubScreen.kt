package com.invictus.xcode.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.invictus.xcode.core.git.normalizeHost
import com.invictus.xcode.core.git.model.GitErrorDetails
import com.invictus.xcode.core.security.GitCredentialStore
import com.invictus.xcode.feature.git.CredentialEntry
import com.invictus.xcode.feature.git.GitConfirmDialog
import com.invictus.xcode.feature.git.GitErrorDialog
import com.invictus.xcode.feature.git.GitFieldsDialog
import com.invictus.xcode.ui.icons.XIcons
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * App-wide GitHub settings: default commit identity (name/email, written to the
 * global gitconfig) and the encrypted per-host access tokens used to clone and
 * sync private repositories, independent of any single open project.
 */
class SettingsGitHubViewModel(
    private val credentialStore: GitCredentialStore,
    globalIdentityFile: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    // setIdentity/getIdentity(local = false) only ever touch the global config file below,
    // never the work tree, so a real repo path isn't needed here.
    private val session = GitSession(globalIdentityFile.parentFile ?: globalIdentityFile, globalIdentityFile, io)

    data class UiState(
        val loading: Boolean = true,
        val identityName: String = "",
        val identityEmail: String = "",
        val savingIdentity: Boolean = false,
        val tokens: List<CredentialEntry> = emptyList(),
        val showTokenEditor: Boolean = false,
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
        loadTokens()
        viewModelScope.launch(io) {
            when (val r = session.getIdentity(local = false)) {
                is GitResult.Ok -> _ui.update {
                    it.copy(
                        loading = false,
                        identityName = r.value?.name.orEmpty(),
                        identityEmail = r.value?.email.orEmpty(),
                    )
                }
                is GitResult.Err -> _ui.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    private fun loadTokens() {
        val entries = credentialStore.hosts().map { host ->
            val cred = credentialStore.get(host)
            CredentialEntry(host = host, username = cred?.username.orEmpty(), linkedRemotes = emptyList())
        }
        _ui.update { it.copy(tokens = entries) }
    }

    fun onIdentityName(v: String) = _ui.update { it.copy(identityName = v) }
    fun onIdentityEmail(v: String) = _ui.update { it.copy(identityEmail = v) }

    fun saveIdentity() {
        val name = _ui.value.identityName.trim()
        val email = _ui.value.identityEmail.trim()
        if (name.isEmpty() || email.isEmpty()) return
        viewModelScope.launch(io) {
            _ui.update { it.copy(savingIdentity = true) }
            when (val r = session.setIdentity(name, email, local = false)) {
                is GitResult.Ok -> {
                    _messages.value = "Identity saved"
                }
                is GitResult.Err -> _ui.update { it.copy(error = r.error) }
            }
            _ui.update { it.copy(savingIdentity = false) }
        }
    }

    fun openAddToken() = _ui.update {
        it.copy(showTokenEditor = true, editHost = null, editorHost = "", editorUser = "x-access-token", editorToken = "")
    }

    fun openEditToken(entry: CredentialEntry) = _ui.update {
        it.copy(
            showTokenEditor = true, editHost = entry.host, editorHost = entry.host,
            editorUser = entry.username, editorToken = "",
        )
    }

    fun dismissTokenEditor() = _ui.update { it.copy(showTokenEditor = false) }

    fun saveToken(host: String, username: String, token: String) {
        val normalizedHost = (host).trim().lowercase()
        if (normalizedHost.isEmpty() || token.isBlank()) return
        credentialStore.put(
            GitCredential(host = normalizedHost, username = username.ifBlank { "x-access-token" }, token = token),
        )
        _ui.update { it.copy(showTokenEditor = false, editHost = null) }
        _messages.value = "Token saved for $normalizedHost"
        loadTokens()
    }

    fun openDeleteToken(host: String) = _ui.update { it.copy(deleteTarget = host) }
    fun dismissDeleteToken() = _ui.update { it.copy(deleteTarget = null) }

    fun deleteToken() {
        val target = _ui.value.deleteTarget ?: return
        credentialStore.remove(target)
        _ui.update { it.copy(deleteTarget = null) }
        _messages.value = "Token removed for $target"
        loadTokens()
    }

    fun dismissError() = _ui.update { it.copy(error = null) }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as XcodeApp
                SettingsGitHubViewModel(
                    credentialStore = app.container.gitCredentialStore,
                    globalIdentityFile = app.container.gitGlobalIdentityFile,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsGitHubScreen(
    onBack: () -> Unit,
    viewModel: SettingsGitHubViewModel = viewModel(factory = SettingsGitHubViewModel.Factory),
) {
    val ui by viewModel.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg -> msg?.let { snackbar.showSnackbar(it); viewModel.messageHandled() } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_github_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.settings_github_identity_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.settings_github_identity_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = ui.identityName,
                    onValueChange = viewModel::onIdentityName,
                    label = { Text(stringResource(R.string.git_identity_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = ui.identityEmail,
                    onValueChange = viewModel::onIdentityEmail,
                    label = { Text(stringResource(R.string.git_identity_email)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = viewModel::saveIdentity,
                    enabled = !ui.savingIdentity &&
                        ui.identityName.isNotBlank() && ui.identityEmail.isNotBlank(),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(stringResource(R.string.settings_github_tokens_title), style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(R.string.settings_github_tokens_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = viewModel::openAddToken) {
                        Text(stringResource(R.string.git_cred_add))
                    }
                }
                if (ui.tokens.isEmpty()) {
                    Text(
                        stringResource(R.string.git_credentials_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    ui.tokens.forEach { entry ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(
                                    entry.host,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    entry.username,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row {
                                    TextButton(onClick = { viewModel.openEditToken(entry) }) {
                                        Text(stringResource(R.string.git_cred_edit))
                                    }
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { viewModel.openDeleteToken(entry.host) }) {
                                        Text(
                                            stringResource(R.string.git_cred_delete),
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (ui.showTokenEditor) {
        GitFieldsDialog(
            title = stringResource(if (ui.editHost == null) R.string.git_cred_add else R.string.git_cred_edit),
            fields = listOf(
                stringResource(R.string.git_cred_host_hint) to ui.editorHost,
                stringResource(R.string.git_cred_user_hint) to ui.editorUser,
                stringResource(R.string.git_cred_token_hint) to ui.editorToken,
            ),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = { v, _ -> viewModel.saveToken(v[0], v[1], v[2]) },
            onDismiss = viewModel::dismissTokenEditor,
        )
    }
    ui.deleteTarget?.let { target ->
        GitConfirmDialog(
            title = stringResource(R.string.git_cred_delete),
            text = stringResource(R.string.git_cred_delete_confirm, target),
            confirmLabel = stringResource(R.string.git_cred_delete),
            danger = true,
            onConfirm = viewModel::deleteToken,
            onDismiss = viewModel::dismissDeleteToken,
        )
    }
    ui.error?.let { GitErrorDialog(details = it, onDismiss = viewModel::dismissError) }
}
