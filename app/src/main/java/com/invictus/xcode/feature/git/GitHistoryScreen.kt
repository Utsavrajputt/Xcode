package com.invictus.xcode.feature.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.invictus.xcode.core.git.GitResult
import com.invictus.xcode.core.git.GitSession
import com.invictus.xcode.core.git.model.GitCommitSummary
import com.invictus.xcode.core.git.model.GitErrorDetails
import com.invictus.xcode.core.git.model.GitLogSearchMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GitHistoryViewModel(
    projectPath: String,
    private val filePath: String?,
    globalIdentityFile: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val session = GitSession(File(projectPath), globalIdentityFile, io)

    data class UiState(
        val loading: Boolean = true,
        val commits: List<GitCommitSummary> = emptyList(),
        val query: String = "",
        val mode: GitLogSearchMode = GitLogSearchMode.MESSAGE,
        val detail: GitCommitSummary? = null,
        val picking: Boolean = false,
        val error: GitErrorDetails? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages.asStateFlow()

    private var searchJob: Job? = null

    init { reload() }

    fun messageHandled() { _messages.value = null }

    private fun reload() {
        viewModelScope.launch(io) {
            _ui.update { it.copy(loading = it.commits.isEmpty()) }
            val r = if (filePath != null) session.log(max = 300, path = filePath) else session.log()
            when (r) {
                is GitResult.Ok -> _ui.update { it.copy(loading = false, commits = r.value) }
                is GitResult.Err -> _ui.update { it.copy(loading = false, error = r.error) }
            }
        }
    }

    fun onQueryChange(q: String) {
        _ui.update { it.copy(query = q) }
        searchJob?.cancel()
        if (q.isBlank()) { reload(); return }
        searchJob = viewModelScope.launch(io) {
            delay(250)   // debounce
            when (val r = session.searchLog(q, _ui.value.mode)) {
                is GitResult.Ok -> _ui.update { it.copy(commits = r.value) }
                is GitResult.Err -> _ui.update { it.copy(error = r.error) }
            }
        }
    }

    fun onModeChange(mode: GitLogSearchMode) {
        _ui.update { it.copy(mode = mode) }
        if (_ui.value.query.isNotBlank()) onQueryChange(_ui.value.query) else reload()
    }

    fun openDetail(c: GitCommitSummary) = _ui.update { it.copy(detail = c) }
    fun dismissDetail() = _ui.update { it.copy(detail = null) }

    fun cherryPick(id: String) {
        viewModelScope.launch(io) {
            _ui.update { it.copy(picking = true, detail = null) }
            when (val r = session.cherryPick(id)) {
                is GitResult.Ok -> _messages.value = "Cherry-picked $id"
                is GitResult.Err -> _ui.update { it.copy(error = r.error) }
            }
            _ui.update { it.copy(picking = false) }
        }
    }

    fun dismissError() = _ui.update { it.copy(error = null) }

    companion object {
        fun factory(projectPath: String, filePath: String?) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as XcodeApp
                GitHistoryViewModel(
                    projectPath = projectPath,
                    filePath = filePath,
                    globalIdentityFile = app.container.gitGlobalIdentityFile,
                )
            }
        }
    }
}

private val historyTimeFormat = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHistoryScreen(projectPath: String, filePath: String? = null, onBack: () -> Unit) {
    val vm: GitHistoryViewModel = viewModel(
        key = "history:$projectPath:$filePath",
        factory = GitHistoryViewModel.factory(projectPath, filePath),
    )
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        vm.messages.collect { msg -> msg?.let { snackbar.showSnackbar(it); vm.messageHandled() } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (filePath != null) {
                            stringResource(R.string.git_history_title) + ": $filePath"
                        } else {
                            stringResource(R.string.git_history_title)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = ui.query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text(stringResource(R.string.git_history_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                GitLogSearchMode.entries.forEach { mode ->
                    FilterChip(
                        selected = ui.mode == mode,
                        onClick = { vm.onModeChange(mode) },
                        label = {
                            Text(
                                stringResource(
                                    when (mode) {
                                        GitLogSearchMode.MESSAGE -> R.string.git_history_mode_message
                                        GitLogSearchMode.AUTHOR -> R.string.git_history_mode_author
                                        GitLogSearchMode.HASH -> R.string.git_history_mode_hash
                                    },
                                ),
                            )
                        },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(ui.commits, key = { it.id }) { c ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.openDetail(c) }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Row {
                            Text(
                                c.shortId,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                historyTimeFormat.format(Date(c.timeMs)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            c.message.lineSequence().firstOrNull().orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            c.author,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!ui.loading && ui.commits.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.git_history_empty),
                            Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    ui.detail?.let { c ->
        AlertDialog(
            onDismissRequest = vm::dismissDetail,
            title = { Text(c.shortId) },
            text = {
                Column {
                    Text(c.message)
                    Spacer(Modifier.padding(4.dp))
                    Text(
                        "${c.author} · ${historyTimeFormat.format(Date(c.timeMs))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { vm.cherryPick(c.id) }, enabled = !ui.picking) {
                    Text(stringResource(R.string.git_cherry_pick))
                }
            },
            dismissButton = {
                TextButton(onClick = vm::dismissDetail) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
    ui.error?.let { GitErrorDialog(details = it, onDismiss = vm::dismissError) }
}
