package com.invictus.xcode.feature.git

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.xcode.R
import com.invictus.xcode.core.git.model.GitPathChange
import com.invictus.xcode.core.git.model.GitRepoSnapshot
import com.invictus.xcode.core.git.model.GitStageState
import com.invictus.xcode.core.git.model.GitWorkingState
import com.invictus.xcode.ui.icons.XIcons
import kotlinx.coroutines.launch

/**
 * Source Control screen (M6): branch + tracking header, commit box with amend,
 * push/pull/fetch, and stage/unstage lists. The M7 drawer wraps the same
 * [GitViewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitScreen(
    projectPath: String,
    onBack: () -> Unit,
    viewModel: GitViewModel = viewModel(
        key = "git:$projectPath",
        factory = GitViewModel.factory(projectPath),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // External edits (Termux, another git client) show up when the user comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(GitEvent.Refresh) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is GitViewModel.Effect.Message ->
                    scope.launch { snackbarHostState.showSnackbar(effect.text.resolve(context)) }
            }
        }
    }

    val canSync = state.snapshot?.hasCommits == true && state.remotes.isNotEmpty()

    state.identity?.let { identity ->
        IdentityDialog(
            state = identity,
            onSave = { name, email, local ->
                viewModel.onEvent(GitEvent.SaveIdentity(name, email, local))
            },
            onDismiss = { viewModel.onEvent(GitEvent.DismissIdentity) },
        )
    }
    state.tokenDialog?.let { token ->
        TokenDialog(
            state = token,
            onSave = { host, user, pass -> viewModel.onEvent(GitEvent.SaveToken(host, user, pass)) },
            onDismiss = { viewModel.onEvent(GitEvent.DismissToken) },
        )
    }
    state.error?.let { details ->
        GitErrorDialog(details = details, onDismiss = { viewModel.onEvent(GitEvent.DismissError) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.snapshot?.headName ?: stringResource(R.string.git_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.onEvent(GitEvent.Fetch) },
                        enabled = canSync && state.networkOp == null,
                    ) {
                        Icon(XIcons.Sync, contentDescription = stringResource(R.string.git_fetch))
                    }
                    IconButton(onClick = { viewModel.onEvent(GitEvent.OpenIdentity) }) {
                        Icon(XIcons.Person, contentDescription = stringResource(R.string.git_identity))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            state.networkOp?.let { op ->
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = state.progressTask.ifBlank { op.name },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.notARepo -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.git_not_a_repo),
                        modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> GitContent(state = state, canSync = canSync, onEvent = viewModel::onEvent)
            }
        }
    }
}

@Composable
private fun GitContent(
    state: GitViewModel.UiState,
    canSync: Boolean,
    onEvent: (GitEvent) -> Unit,
) {
    val snapshot = state.snapshot ?: return
    val status = state.status
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item(key = "branch") { BranchCard(snapshot) }
        item(key = "commit") { CommitCard(state, onEvent) }
        item(key = "sync") { SyncRow(canSync, snapshot, onEvent) }
        if (status != null && status.isClean) {
            item(key = "clean") {
                Text(
                    text = stringResource(R.string.git_clean),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                )
            }
        }
        status?.let { st ->
            changeSection(
                keyPrefix = "staged",
                title = stringResource(R.string.git_section_staged),
                changes = st.staged,
                headerActionLabel = stringResource(R.string.git_unstage_all),
                onHeaderAction = { onEvent(GitEvent.UnstageAll) },
                onItemClick = { onEvent(GitEvent.Unstage(it.repoRelativePath)) },
            )
            changeSection(
                keyPrefix = "changes",
                title = stringResource(R.string.git_section_changes),
                changes = st.unstaged.filter { it.unstaged != GitWorkingState.UNTRACKED },
                headerActionLabel = stringResource(R.string.git_stage_all),
                onHeaderAction = { onEvent(GitEvent.StageAll) },
                onItemClick = { onEvent(GitEvent.Stage(it.repoRelativePath)) },
            )
            changeSection(
                keyPrefix = "untracked",
                title = stringResource(R.string.git_section_untracked),
                changes = st.untracked,
                headerActionLabel = stringResource(R.string.git_stage_all),
                onHeaderAction = { onEvent(GitEvent.StageAll) },
                onItemClick = { onEvent(GitEvent.Stage(it.repoRelativePath)) },
            )
        }
    }
}

private fun LazyListScope.changeSection(
    keyPrefix: String,
    title: String,
    changes: List<GitPathChange>,
    headerActionLabel: String,
    onHeaderAction: () -> Unit,
    onItemClick: (GitPathChange) -> Unit,
) {
    if (changes.isEmpty()) return
    item(key = "$keyPrefix:header") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onHeaderAction) { Text(headerActionLabel) }
        }
    }
    items(changes, key = { "$keyPrefix:${it.repoRelativePath}" }) { change ->
        ChangeRow(change = change, onClick = { onItemClick(change) })
    }
}

@Composable
private fun ChangeRow(change: GitPathChange, onClick: () -> Unit) {
    val (color, labelRes) = when {
        change.staged == GitStageState.CONFLICT || change.unstaged == GitWorkingState.CONFLICT ->
            MaterialTheme.colorScheme.error to R.string.git_state_conflict
        change.staged == GitStageState.ADDED || change.unstaged == GitWorkingState.UNTRACKED ->
            MaterialTheme.colorScheme.tertiary to R.string.git_state_added
        change.staged == GitStageState.DELETED || change.unstaged == GitWorkingState.DELETED ->
            MaterialTheme.colorScheme.error to R.string.git_state_deleted
        else -> MaterialTheme.colorScheme.primary to R.string.git_state_modified
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(
            text = change.repoRelativePath,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun BranchCard(snapshot: GitRepoSnapshot) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Icon(XIcons.AccountTree, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = snapshot.headName ?: stringResource(R.string.git_no_commits),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val tracking = snapshot.trackingInfo
            Text(
                text = when {
                    !snapshot.hasCommits -> stringResource(R.string.git_no_commits)
                    tracking == null -> stringResource(R.string.git_no_upstream)
                    else -> stringResource(R.string.git_tracking, tracking.remote, tracking.branch) +
                        "  " + stringResource(R.string.git_ahead_behind, tracking.ahead, tracking.behind)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CommitCard(state: GitViewModel.UiState, onEvent: (GitEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.commitMessage,
            onValueChange = { onEvent(GitEvent.CommitMessageChange(it)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            label = { Text(stringResource(R.string.git_commit_hint)) },
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = state.amend,
                onClick = { onEvent(GitEvent.ToggleAmend) },
                label = { Text(stringResource(R.string.git_amend)) },
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { onEvent(GitEvent.Commit) },
                enabled = state.commitMessage.isNotBlank() && state.snapshot != null && !state.committing,
            ) {
                Text(stringResource(R.string.git_commit))
            }
        }
    }
}

@Composable
private fun SyncRow(
    canSync: Boolean,
    snapshot: GitRepoSnapshot,
    onEvent: (GitEvent) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        OutlinedButton(onClick = { onEvent(GitEvent.Push) }, enabled = canSync) {
            Icon(XIcons.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.git_push))
        }
        OutlinedButton(
            onClick = { onEvent(GitEvent.Pull) },
            enabled = canSync && snapshot.trackingInfo != null,
        ) {
            Icon(XIcons.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.git_pull))
        }
    }
}
