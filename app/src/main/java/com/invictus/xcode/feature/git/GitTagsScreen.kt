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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.xcode.R
import com.invictus.xcode.XcodeApp
import com.invictus.xcode.core.git.GitResult
import com.invictus.xcode.core.git.GitSession
import com.invictus.xcode.core.git.model.GitErrorDetails
import com.invictus.xcode.core.git.model.GitTagInfo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GitTagsViewModel(
    projectPath: String,
    globalIdentityFile: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val session = GitSession(File(projectPath), globalIdentityFile, io)

    data class UiState(
        val loading: Boolean = true,
        val tags: List<GitTagInfo> = emptyList(),
        val showCreate: Boolean = false,
        val createName: String = "",
        val createMessage: String = "",
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
            _ui.update { it.copy(loading = it.tags.isEmpty()) }
            when (val r = session.listTags()) {
                is GitResult.Ok -> _ui.update { it.copy(loading = false, tags = r.value) }
                is GitResult.Err -> _ui.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

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

    fun openDelete(name: String) = _ui.update { it.copy(deleteTarget = name) }
    fun dismissDelete() = _ui.update { it.copy(deleteTarget = null) }

    fun delete() {
        val target = _ui.value.deleteTarget ?: return
        viewModelScope.launch(io) {
            when (val r = session.deleteTag(target)) {
                is GitResult.Ok -> {
                    _ui.update { it.copy(deleteTarget = null) }
                    _messages.value = "Tag '$target' deleted"
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
                GitTagsViewModel(projectPath, app.container.gitGlobalIdentityFile)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.git_tags_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹") } },
                actions = {
                    TextButton(onClick = { vm.openCreate() }) {
                        Text(stringResource(R.string.git_tag_create))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(ui.tags, key = { it.name }) { t ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOfNotNull(
                                    t.message,
                                    t.timeMs.takeIf { it > 0 }?.let { tagTimeFormat.format(Date(it)) },
                                    t.commitId.take(7),
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { vm.openDelete(t.name) }) {
                            Text(
                                stringResource(R.string.git_tag_delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            if (!ui.loading && ui.tags.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.git_tags_empty),
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
            text = stringResource(R.string.git_tag_delete_confirm, target),
            confirmLabel = stringResource(R.string.git_tag_delete),
            danger = true,
            onConfirm = vm::delete,
            onDismiss = vm::dismissDelete,
        )
    }
    ui.error?.let { GitErrorDialog(details = it, onDismiss = vm::dismissError) }
}
