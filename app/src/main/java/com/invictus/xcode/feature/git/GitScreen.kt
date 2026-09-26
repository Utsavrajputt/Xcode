package com.invictus.xcode.feature.git

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.invictus.xcode.core.git.model.GitFileDiffResult
import com.invictus.xcode.core.git.model.GitPathChange
import com.invictus.xcode.core.git.model.GitRepoSnapshot
import com.invictus.xcode.core.git.model.GitStageState
import com.invictus.xcode.core.git.model.GitWorkingState
import com.invictus.xcode.ui.icons.XIcons
import com.invictus.xcode.ui.navigation.Routes
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
    onOpenRoute: (String) -> Unit = {},
    viewModel: GitViewModel = viewModel(
        key = "git:$projectPath",
        factory = GitViewModel.factory(projectPath),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var overflowOpen by remember { mutableStateOf(false) }
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
                is GitViewModel.Effect.OpenFile ->
                    scope.launch { snackbarHostState.showSnackbar(effect.file.name) }
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
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(XIcons.MoreVert, contentDescription = stringResource(R.string.git_title))
                        }
                        DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_branches_title)) },
                                onClick = {
                                    overflowOpen = false
                                    onOpenRoute(Routes.gitBranches(projectPath))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_history_title)) },
                                onClick = {
                                    overflowOpen = false
                                    onOpenRoute(Routes.gitHistory(projectPath))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_stash_title)) },
                                onClick = {
                                    overflowOpen = false
                                    onOpenRoute(Routes.gitStash(projectPath))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_tags_title)) },
                                onClick = {
                                    overflowOpen = false
                                    onOpenRoute(Routes.gitTags(projectPath))
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.git_remotes_title)) },
                                onClick = {
                                    overflowOpen = false
                                    onOpenRoute(Routes.gitRemotes(projectPath))
                                },
                            )
                            if (state.notARepo) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.git_setup_title)) },
                                    onClick = {
                                        overflowOpen = false
                                        onOpenRoute(Routes.gitOnboarding(projectPath))
                                    },
                                )
                            }
                        }
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.git_not_a_repo),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(onClick = { onOpenRoute(Routes.gitOnboarding(projectPath)) }) {
                            Text(stringResource(R.string.git_setup_title))
                        }
                    }
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
                keyPrefix = "changes",
                titleRes = R.string.git_section_changes,
                changes = st.unstaged.filter { it.unstaged != GitWorkingState.UNTRACKED },
                headerActionLabelRes = R.string.git_stage_all,
                isStagedSection = false,
                state = state,
                onHeaderAction = { onEvent(GitEvent.StageAll) },
                onEvent = onEvent,
            )
            changeSection(
                keyPrefix = "untracked",
                titleRes = R.string.git_section_untracked,
                changes = st.untracked,
                headerActionLabelRes = R.string.git_stage_all,
                isStagedSection = false,
                state = state,
                onHeaderAction = { onEvent(GitEvent.StageAll) },
                onEvent = onEvent,
            )
            changeSection(
                keyPrefix = "staged",
                titleRes = R.string.git_section_staged,
                changes = st.staged,
                headerActionLabelRes = R.string.git_unstage_all,
                isStagedSection = true,
                state = state,
                onHeaderAction = { onEvent(GitEvent.UnstageAll) },
                onEvent = onEvent,
            )
        }
    }
}

private fun LazyListScope.changeSection(
    keyPrefix: String,
    titleRes: Int,
    changes: List<GitPathChange>,
    headerActionLabelRes: Int,
    isStagedSection: Boolean,
    state: GitViewModel.UiState,
    onHeaderAction: () -> Unit,
    onEvent: (GitEvent) -> Unit,
) {
    if (changes.isEmpty()) return
    item(key = "$keyPrefix:header") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp),
        ) {
            Text(
                stringResource(titleRes),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onHeaderAction) { Text(stringResource(headerActionLabelRes)) }
        }
    }
    // Index bhi key me shamil — agar kabhi upstream se same repoRelativePath do baar
    // aa jaaye (kisi bhi wajah se), tab bhi LazyColumn crash nahi karega.
    itemsIndexed(changes, key = { index, change -> "$keyPrefix:${change.repoRelativePath}:$index" }) { _, change ->
        ChangeRow(
            change = change,
            isStagedSection = isStagedSection,
            diff = state.diffs[change.repoRelativePath],
            diffLoading = change.repoRelativePath in state.diffLoading,
            onEvent = onEvent,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChangeRow(
    change: GitPathChange,
    isStagedSection: Boolean,
    diff: GitFileDiffResult?,
    diffLoading: Boolean,
    onEvent: (GitEvent) -> Unit,
) {
    val path = change.repoRelativePath
    val (color, labelRes) = when {
        change.staged == GitStageState.CONFLICT || change.unstaged == GitWorkingState.CONFLICT ->
            MaterialTheme.colorScheme.error to R.string.git_state_conflict
        change.staged == GitStageState.ADDED || change.unstaged == GitWorkingState.UNTRACKED ->
            MaterialTheme.colorScheme.tertiary to R.string.git_state_added
        change.staged == GitStageState.DELETED || change.unstaged == GitWorkingState.DELETED ->
            MaterialTheme.colorScheme.error to R.string.git_state_deleted
        else -> MaterialTheme.colorScheme.primary to R.string.git_state_modified
    }
    val expanded = diff != null || diffLoading
    var menuOpen by remember { mutableStateOf(false) }
    val toggleStage = { onEvent(if (isStagedSection) GitEvent.Unstage(path) else GitEvent.Stage(path)) }

    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { onEvent(if (expanded) GitEvent.CloseDiff(path) else GitEvent.LoadDiff(path)) },
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(end = 12.dp)
                    .size(20.dp)
                    .clickable(onClick = toggleStage),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(8.dp).background(color, CircleShape))
            }
            Text(
                text = path,
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
            Box {
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (isStagedSection) R.string.git_unstage else R.string.git_stage,
                                ),
                            )
                        },
                        onClick = {
                            menuOpen = false
                            toggleStage()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.git_open_in_editor)) },
                        onClick = {
                            menuOpen = false
                            onEvent(GitEvent.OpenFile(path))
                        },
                    )
                }
            }
        }
        if (diffLoading) {
            Row(
                Modifier.padding(start = 40.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.git_diff_loading),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        diff?.let {
            Box(
                Modifier
                    .padding(start = 28.dp, end = 8.dp, bottom = 8.dp)
                    .heightIn(max = 360.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            ) {
                GitDiffViewer(result = it)
            }
        }
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
