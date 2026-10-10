package com.invictus.kodex.feature.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.kodex.R
import com.invictus.kodex.core.git.GitCloneLogLine
import com.invictus.kodex.ui.components.rememberFolderPickerLauncher
import com.invictus.kodex.ui.icons.XIcons
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitCloneScreen(
    onBack: () -> Unit,
    onOpenGitHubSettings: () -> Unit = {},
    viewModel: GitCloneViewModel = viewModel(factory = GitCloneViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is GitCloneViewModel.Effect.Message ->
                    scope.launch { snackbarHostState.showSnackbar(effect.text.resolve(context)) }
                GitCloneViewModel.Effect.NavigateBack -> onBack()
                GitCloneViewModel.Effect.OpenGitHubSettings -> onOpenGitHubSettings()
            }
        }
    }

    val fieldsLocked = state.cloning || state.completed

    state.error?.let { details ->
        GitErrorDialog(details = details, onDismiss = { viewModel.onEvent(GitCloneEvent.DismissError) })
    }
    state.privateRepoHost?.let { host ->
        PrivateRepoDialog(
            host = host,
            onConfirm = { username, token ->
                viewModel.onEvent(GitCloneEvent.ConfirmPrivateToken(username, token))
            },
            onCancel = { viewModel.onEvent(GitCloneEvent.DismissPrivateRepoDialog) },
            onConfigure = { viewModel.onEvent(GitCloneEvent.ConfigureCredentials) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.clone_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = state.url,
                onValueChange = { viewModel.onEvent(GitCloneEvent.UrlChange(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.clone_url_hint)) },
                singleLine = true,
                enabled = !fieldsLocked,
            )
            OutlinedTextField(
                value = state.folderName,
                onValueChange = { viewModel.onEvent(GitCloneEvent.FolderChange(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.clone_folder_hint)) },
                singleLine = true,
                enabled = !fieldsLocked,
            )
            val pickParentFolder = rememberFolderPickerLauncher(
                onPicked = { path -> viewModel.onEvent(GitCloneEvent.ParentChange(path)) },
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = state.parentPath,
                    onValueChange = { viewModel.onEvent(GitCloneEvent.ParentChange(it)) },
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.clone_parent_hint)) },
                    singleLine = true,
                    enabled = !fieldsLocked,
                )
                IconButton(onClick = pickParentFolder, enabled = !fieldsLocked) {
                    Icon(XIcons.Folder, contentDescription = stringResource(R.string.clone_browse_folder))
                }
            }
            OutlinedTextField(
                value = state.branch,
                onValueChange = { viewModel.onEvent(GitCloneEvent.BranchChange(it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.clone_branch_hint)) },
                singleLine = true,
                enabled = !fieldsLocked,
            )
            if (state.logLines.isNotEmpty()) {
                val activePercent = state.logLines.lastOrNull()?.takeIf { !it.done }?.percent
                if (state.cloning) {
                    if (activePercent != null) {
                        LinearProgressIndicator(
                            progress = { activePercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                CloneLog(lines = state.logLines, modifier = Modifier.fillMaxWidth().height(220.dp))
            }
            if (state.completed) {
                Button(
                    onClick = { viewModel.onEvent(GitCloneEvent.Done) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.clone_finish_button))
                }
            } else {
                Button(
                    onClick = { viewModel.onEvent(GitCloneEvent.Start) },
                    enabled = !state.cloning && state.url.isNotBlank() && state.folderName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.clone_start))
                }
            }
        }
    }
}

/** Termux/native-git style scrolling transcript: dark, monospace, auto-scrolls to the newest line. */
@Composable
private fun CloneLog(lines: List<GitCloneLogLine>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.lastIndex)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(TerminalBackground),
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(lines, key = { index, line -> "$index-${line.taskName}" }) { _, line ->
                Text(
                    text = line.text,
                    color = if (line.taskName == "Total") TerminalHighlight else TerminalForeground,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

private val TerminalBackground = androidx.compose.ui.graphics.Color(0xFF0B0F14)
private val TerminalForeground = androidx.compose.ui.graphics.Color(0xFFD7DDE3)
private val TerminalHighlight = androidx.compose.ui.graphics.Color(0xFF6FCF97)
