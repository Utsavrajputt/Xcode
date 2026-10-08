package com.invictus.xcode.feature.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.invictus.xcode.core.git.GitSessionRegistry
import com.invictus.xcode.core.git.GitTokenCredentialsProvider
import com.invictus.xcode.core.git.model.GitAuthFailureType
import com.invictus.xcode.core.git.model.GitErrorDetails
import com.invictus.xcode.core.git.model.GitTagInfo
import com.invictus.xcode.core.security.GitCredentialStore
import com.invictus.xcode.ui.icons.XIcons
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.eclipse.jgit.transport.CredentialsProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---- ordering: newest version first (v1.11.2 > v1.10.1 > v1.2.4), not alphabetical ----------

private class TagVersion(val nums: List<Long>, val pre: String)

private val versionRegex = Regex("""^[vV]?(\d+(?:\.\d+)*)(?:[-+_.]?(.*))?$""")

private fun parseTagVersion(name: String): TagVersion? {
    val m = versionRegex.matchEntire(name.trim()) ?: return null
    return TagVersion(
        nums = m.groupValues[1].split('.').map { it.toLongOrNull() ?: 0L },
        pre = m.groupValues[2],
    )
}

/**
 * Versions first, highest to lowest, compared number by number; a release ranks above its own
 * pre-release (v1.0.0 above v1.0.0-beta). Tags that aren't versions follow, Z to A.
 */
internal val tagDescending = Comparator<String> { a, b ->
    val va = parseTagVersion(a)
    val vb = parseTagVersion(b)
    when {
        va != null && vb != null -> {
            var result = 0
            for (i in 0 until maxOf(va.nums.size, vb.nums.size)) {
                val x = va.nums.getOrElse(i) { 0L }
                val y = vb.nums.getOrElse(i) { 0L }
                if (x != y) { result = y.compareTo(x); break }
            }
            when {
                result != 0 -> result
                va.pre.isEmpty() && vb.pre.isEmpty() -> 0
                va.pre.isEmpty() -> -1
                vb.pre.isEmpty() -> 1
                else -> vb.pre.compareTo(va.pre)
            }
        }
        va != null -> -1
        vb != null -> 1
        else -> b.compareTo(a)
    }
}

class GitTagsViewModel(
    private val projectPath: String,
    private val credentialStore: GitCredentialStore,
    globalIdentityFile: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val session = GitSessionRegistry.acquire(File(projectPath), globalIdentityFile, io)

    override fun onCleared() {
        GitSessionRegistry.release(File(projectPath))
    }

    enum class TagFilter { LOCAL, REMOTE }

    data class RemoteTag(val name: String, val commitId: String)

    data class UiState(
        val loading: Boolean = true,
        val tags: List<GitTagInfo> = emptyList(),
        val filter: TagFilter = TagFilter.LOCAL,
        val remoteTags: List<RemoteTag> = emptyList(),
        val remoteLoaded: Boolean = false,
        /** A network operation (push / delete on remote / listing) is running. */
        val busy: Boolean = false,
        val showCreate: Boolean = false,
        val createName: String = "",
        val createMessage: String = "",
        val deleteTarget: String? = null,
        val tokenHost: String? = null,
        val error: GitErrorDetails? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    /** Re-runs the network action that was waiting for a token. */
    private var pending: (() -> Unit)? = null

    init { load() }
    fun messageHandled() { _messages.value = null }

    fun load() {
        viewModelScope.launch(io) {
            _ui.update { it.copy(loading = it.tags.isEmpty()) }
            when (val r = session.listTags()) {
                is GitResult.Ok -> _ui.update {
                    it.copy(loading = false, tags = r.value.sortedWith { a, b -> tagDescending.compare(a.name, b.name) })
                }
                is GitResult.Err -> _ui.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    fun setFilter(f: TagFilter) {
        _ui.update { it.copy(filter = f) }
        if (f == TagFilter.REMOTE && !_ui.value.remoteLoaded) loadRemote()
    }

    // ---- create ---------------------------------------------------------------

    fun openCreate() = _ui.update { it.copy(showCreate = true) }
    fun dismissCreate() =
        _ui.update { it.copy(showCreate = false, createName = "", createMessage = "") }
    fun onCreateName(v: String) = _ui.update { it.copy(createName = v) }
    fun onCreateMessage(v: String) = _ui.update { it.copy(createMessage = v) }

    fun create() {
        val name = _ui.value.createName.trim()
        if (name.isEmpty()) return
        viewModelScope.launch(io) {
            when (val r = session.createTag(name, _ui.value.createMessage)) {
                is GitResult.Ok -> {
                    _ui.update { it.copy(showCreate = false, createName = "", createMessage = "") }
                    _messages.value = "Tag '$name' created"
                    load()
                }
                is GitResult.Err -> _ui.update { it.copy(error = r.error) }
            }
        }
    }

    // ---- delete: removes the tag from this device AND from the remote ----------

    fun openDelete(name: String) = _ui.update { it.copy(deleteTarget = name) }
    fun dismissDelete() = _ui.update { it.copy(deleteTarget = null) }

    fun delete() {
        val target = _ui.value.deleteTarget ?: return
        _ui.update { it.copy(deleteTarget = null) }
        viewModelScope.launch(io) {
            // A tag seen only on the remote has no local copy to delete.
            val hasLocal = _ui.value.tags.any { it.name == target }
            if (hasLocal) {
                when (val r = session.deleteTag(target)) {
                    is GitResult.Ok -> load()
                    is GitResult.Err -> {
                        _ui.update { it.copy(error = r.error) }
                        return@launch
                    }
                }
            }
            deleteOnRemote(target, alsoLocal = hasLocal)
        }
    }

    fun push(tag: String) = runRemote(
        block = { remote, creds -> session.pushTag(remote, tag, creds, force = false) },
        onOk = { _, remote -> _messages.value = "Pushed '$tag' to $remote"; refreshRemoteIfShown() },
    )

    private fun deleteOnRemote(tag: String, alsoLocal: Boolean) = runRemote(
        block = { remote, creds -> session.deleteRemoteTag(remote, tag, creds) },
        onOk = { _, remote ->
            _messages.value = if (alsoLocal) "Tag '$tag' deleted locally and from $remote"
            else "Tag '$tag' deleted from $remote"
            _ui.update { s -> s.copy(remoteTags = s.remoteTags.filterNot { it.name == tag }) }
        },
    )

    fun loadRemote() = runRemote(
        requireToken = false,
        block = { remote, creds -> session.listRemoteTags(remote, creds) },
        onOk = { list, _ ->
            _ui.update { s ->
                s.copy(
                    remoteLoaded = true,
                    remoteTags = list.map { RemoteTag(it.first, it.second) }
                        .sortedWith { a, b -> tagDescending.compare(a.name, b.name) },
                )
            }
        },
    )

    private fun refreshRemoteIfShown() {
        if (_ui.value.filter == TagFilter.REMOTE) loadRemote()
    }

    private suspend fun defaultRemote(): String? {
        val remotes = (session.listRemotes() as? GitResult.Ok)?.value.orEmpty()
        return remotes.firstOrNull { it.name == "origin" }?.name ?: remotes.firstOrNull()?.name
    }

    /**
     * Shared network path: pick the remote (origin first), use the saved token (ask for one when
     * the host has none and [requireToken]), run [block], and on an auth failure ask for a token
     * and retry with it.
     */
    private fun <T> runRemote(
        requireToken: Boolean = true,
        block: suspend (String, CredentialsProvider?) -> GitResult<T>,
        onOk: suspend (T, String) -> Unit,
    ) {
        viewModelScope.launch(io) {
            val remote = defaultRemote() ?: run {
                _messages.value = "No remote configured"
                return@launch
            }
            val host = session.remoteUrl(remote)?.let { com.invictus.xcode.core.git.normalizeHost(it) }
            val cred = host?.let { credentialStore.get(it) }
            if (host != null && cred == null && requireToken) {
                pending = { runRemote(requireToken, block, onOk) }
                _ui.update { it.copy(tokenHost = host) }
                return@launch
            }
            _ui.update { it.copy(busy = true) }
            val provider = cred?.let { GitTokenCredentialsProvider(it.username, it.token) }
            when (val r = block(remote, provider)) {
                is GitResult.Ok -> onOk(r.value, remote)
                is GitResult.Err -> {
                    if (host != null && r.error.authFailure.isAuthError()) {
                        pending = { runRemote(requireToken, block, onOk) }
                        _ui.update { it.copy(tokenHost = host) }
                    } else {
                        _ui.update { it.copy(error = r.error) }
                    }
                }
            }
            _ui.update { it.copy(busy = false) }
        }
    }

    fun saveToken(host: String, username: String, token: String) {
        credentialStore.put(GitCredential(host, username.ifBlank { DEFAULT_USER }, token))
        _ui.update { it.copy(tokenHost = null) }
        pending?.invoke()
        pending = null
    }

    fun dismissToken() {
        pending = null
        _ui.update { it.copy(tokenHost = null) }
    }

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
                GitTagsViewModel(
                    projectPath = projectPath,
                    credentialStore = app.container.gitCredentialStore,
                    globalIdentityFile = app.container.gitGlobalIdentityFile,
                )
            }
        }
    }
}

private val tagTimeFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitTagsScreen(projectPath: String, onBack: () -> Unit) {
    val vm: GitTagsViewModel = viewModel(
        key = "tags:$projectPath",
        factory = GitTagsViewModel.factory(projectPath),
    )
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> msg?.let { snackbar.showSnackbar(it); vm.messageHandled() } }
    }

    val localNames = remember(ui.tags) { ui.tags.map { it.name }.toSet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.git_tags_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    TextButton(onClick = { vm.openCreate() }) {
                        Text(stringResource(R.string.git_tag_create))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = ui.filter == GitTagsViewModel.TagFilter.LOCAL,
                    onClick = { vm.setFilter(GitTagsViewModel.TagFilter.LOCAL) },
                    label = { Text(stringResource(R.string.git_tag_filter_local)) },
                )
                FilterChip(
                    selected = ui.filter == GitTagsViewModel.TagFilter.REMOTE,
                    onClick = { vm.setFilter(GitTagsViewModel.TagFilter.REMOTE) },
                    label = { Text(stringResource(R.string.git_tag_filter_remote)) },
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (ui.filter == GitTagsViewModel.TagFilter.LOCAL) {
                    items(ui.tags, key = { "l:" + it.name }) { t ->
                        TagCard(
                            name = t.name,
                            subtitle = listOfNotNull(
                                t.message,
                                t.timeMs.takeIf { it > 0 }?.let { tagTimeFormat.format(Date(it)) },
                                t.commitId.take(7),
                            ).joinToString(" · "),
                            onDelete = { vm.openDelete(t.name) },
                            menu = listOf(MenuEntry(R.string.git_tag_push) { vm.push(t.name) }),
                        )
                    }
                    if (!ui.loading && ui.tags.isEmpty()) {
                        item { EmptyHint(stringResource(R.string.git_tags_empty)) }
                    }
                } else {
                    items(ui.remoteTags, key = { "r:" + it.name }) { t ->
                        TagCard(
                            name = t.name,
                            subtitle = stringResource(
                                if (t.name in localNames) R.string.git_tag_also_local else R.string.git_tag_remote_only,
                            ) + " · " + t.commitId.take(7),
                            onDelete = { vm.openDelete(t.name) },
                            menu = emptyList(),
                        )
                    }
                    if (ui.remoteLoaded && !ui.busy && ui.remoteTags.isEmpty()) {
                        item { EmptyHint(stringResource(R.string.git_tags_remote_empty)) }
                    }
                }
            }
        }
    }

    if (ui.showCreate) {
        GitFieldsDialog(
            title = stringResource(R.string.git_tag_create),
            fields = listOf(
                stringResource(R.string.git_tag_name_hint) to ui.createName,
                stringResource(R.string.git_tag_message_hint) to ui.createMessage,
            ),
            confirmLabel = stringResource(R.string.git_tag_create),
            onConfirm = { v, _ ->
                vm.onCreateName(v[0])
                vm.onCreateMessage(v[1])
                vm.create()
            },
            onDismiss = vm::dismissCreate,
        )
    }
    ui.deleteTarget?.let { target ->
        GitConfirmDialog(
            title = stringResource(R.string.git_tag_delete),
            text = stringResource(R.string.git_tag_delete_both_confirm, target),
            confirmLabel = stringResource(R.string.git_tag_delete),
            danger = true,
            onConfirm = vm::delete,
            onDismiss = vm::dismissDelete,
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

private class MenuEntry(val label: Int, val onClick: () -> Unit)

@Composable
private fun TagCard(
    name: String,
    subtitle: String,
    onDelete: () -> Unit,
    menu: List<MenuEntry>,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(
                start = 14.dp,
                end = if (menu.isEmpty()) 8.dp else 4.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onDelete) {
                Text(stringResource(R.string.git_tag_delete), color = MaterialTheme.colorScheme.error)
            }
            if (menu.isNotEmpty()) {
                var open by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { open = true }) {
                        Icon(XIcons.MoreVert, contentDescription = stringResource(R.string.git_branch_more))
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        menu.forEach { e ->
                            DropdownMenuItem(
                                text = { Text(stringResource(e.label)) },
                                onClick = { open = false; e.onClick() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}
