package com.invictus.kodex.feature.git

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.kodex.R
import com.invictus.kodex.core.git.model.GitFileDiffResult
import com.invictus.kodex.core.git.model.GitPathChange
import com.invictus.kodex.core.git.model.GitStageState
import com.invictus.kodex.core.git.model.GitWorkingState
import com.invictus.kodex.ui.icons.XIcons
import java.io.File

/**
 * Source Control drawer content (M7). Lives inside a ModalNavigationDrawer next to the
 * editor, so status/commit/diff happen without leaving the file being edited.
 */
@Composable
fun SourceControlDrawerSheet(
    state: GitViewModel.UiState,
    projectRoot: File,
    onEvent: (GitEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DrawerHeader(state, onEvent)
        HorizontalDivider()
        val status = state.status
        if (state.loading && status == null) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (status == null || state.notARepo) {
            Text(
                stringResource(R.string.git_not_a_repo),
                modifier = Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                // Air under the last row so it never sits flush on the gesture bar.
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item("commit") { DrawerCommitCard(state, onEvent) }
                item("actions") { DrawerQuickActions(state, onEvent) }
                if (state.snapshot?.mergeInProgress == true || state.snapshot?.rebaseInProgress == true) {
                    item("op-banner") { GitOperationBanner(state, onEvent) }
                }
                if (status.isClean) {
                    item("clean") {
                        Text(
                            stringResource(R.string.git_clean),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(24.dp),
                        )
                    }
                }
                conflictSection(status.conflicts, state.diffs, state.diffLoading, onEvent)
                drawerSection(
                    key = GitViewModel.SECTION_STAGED,
                    titleRes = R.string.git_section_staged,
                    changes = status.staged.filterNot { it.isConflict() },
                    state = state,
                    headerActionLabelRes = R.string.git_unstage_all,
                    onEvent = onEvent,
                )
                drawerSection(
                    key = GitViewModel.SECTION_CHANGES,
                    titleRes = R.string.git_section_changes,
                    changes = status.unstaged.filter { it.unstaged != GitWorkingState.UNTRACKED && !it.isConflict() },
                    state = state,
                    headerActionLabelRes = R.string.git_stage_all,
                    onEvent = onEvent,
                )
                drawerSection(
                    key = GitViewModel.SECTION_UNTRACKED,
                    titleRes = R.string.git_section_untracked,
                    changes = status.untracked,
                    state = state,
                    headerActionLabelRes = R.string.git_stage_all,
                    onEvent = onEvent,
                )
            }
        }
    }
    // The three dialogs ride along wherever the drawer is hosted.
    state.identity?.let {
        IdentityDialog(
            state = it,
            onSave = { n, e, l -> onEvent(GitEvent.SaveIdentity(n, e, l)) },
            onDismiss = { onEvent(GitEvent.DismissIdentity) },
        )
    }
    state.tokenDialog?.let {
        TokenDialog(
            state = it,
            onSave = { h, u, t -> onEvent(GitEvent.SaveToken(h, u, t)) },
            onDismiss = { onEvent(GitEvent.DismissToken) },
        )
    }
    state.error?.let {
        GitErrorDialog(details = it, onDismiss = { onEvent(GitEvent.DismissError) })
    }
    GitMergeDialogs(state, onEvent)
    GitRebaseDialogs(state, onEvent)
    if (state.pushRejected) {
        GitConfirmDialog(
            title = stringResource(R.string.git_push_rejected_title),
            text = stringResource(R.string.git_push_rejected_body),
            confirmLabel = stringResource(R.string.git_push_rejected_pull),
            danger = false,
            onConfirm = { onEvent(GitEvent.PullThenRetryPush) },
            onDismiss = { onEvent(GitEvent.DismissPushRejected) },
        )
    }
    state.fetchFirstRemote?.let { remote ->
        GitFetchFirstDialog(
            remote = remote,
            onFetch = {
                onEvent(GitEvent.DismissFetchFirst)
                onEvent(GitEvent.Fetch)
            },
            onDismiss = { onEvent(GitEvent.DismissFetchFirst) },
        )
    }
}

@Composable
private fun DrawerHeader(state: GitViewModel.UiState, onEvent: (GitEvent) -> Unit) {
    val snapshot = state.snapshot ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Icon(XIcons.AccountTree, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                snapshot.headName ?: stringResource(R.string.git_no_commits),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
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
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(
            onClick = { onEvent(GitEvent.Fetch) },
            enabled = state.networkOp == null && state.remotes.isNotEmpty(),
        ) {
            Icon(XIcons.Sync, contentDescription = stringResource(R.string.git_sync))
        }
    }
}

@Composable
private fun DrawerCommitCard(state: GitViewModel.UiState, onEvent: (GitEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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
                enabled = (state.commitMessage.isNotBlank() || state.amend) && !state.committing,
            ) { Text(stringResource(R.string.git_commit)) }
        }
    }
}

@Composable
private fun DrawerQuickActions(state: GitViewModel.UiState, onEvent: (GitEvent) -> Unit) {
    val canSync = state.snapshot?.hasCommits == true && state.remotes.isNotEmpty()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        DrawerSplitSyncButton(
            label = stringResource(R.string.git_push),
            icon = XIcons.CloudUpload,
            enabled = canSync,
            menuContentDescription = stringResource(R.string.git_push_more),
            menuItemLabel = stringResource(R.string.git_force_push_lease),
            onClick = { onEvent(GitEvent.Push) },
            onMenuItemClick = { onEvent(GitEvent.ForcePushWithLease) },
        )
        DrawerSplitSyncButton(
            label = stringResource(R.string.git_pull),
            icon = XIcons.CloudDownload,
            enabled = canSync,
            menuContentDescription = stringResource(R.string.git_pull_more),
            menuItemLabel = stringResource(R.string.git_pull_rebase),
            onClick = { onEvent(GitEvent.Pull) },
            onMenuItemClick = { onEvent(GitEvent.PullRebase) },
        )
    }
}

/** Drawer-sized variant of the split push/pull button (smaller icon/spacing). */
@Composable
private fun DrawerSplitSyncButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    menuContentDescription: String,
    menuItemLabel: String,
    onClick: () -> Unit,
    onMenuItemClick: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = onClick, enabled = enabled) {
            Icon(icon, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label)
        }
        Box {
            IconButton(onClick = { menuOpen = true }, enabled = enabled) {
                Icon(XIcons.KeyboardArrowDown, contentDescription = menuContentDescription)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(menuItemLabel) },
                    onClick = {
                        menuOpen = false
                        onMenuItemClick()
                    },
                )
            }
        }
    }
}

private fun LazyListScope.drawerSection(
    key: String,
    titleRes: Int,
    changes: List<GitPathChange>,
    state: GitViewModel.UiState,
    headerActionLabelRes: Int?,
    onEvent: (GitEvent) -> Unit,
) {
    if (changes.isEmpty()) return
    val duplicateNames = state.duplicateChangeNames()
    val expanded = key in state.expandedSections
    item("$key:header") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .clickable { onEvent(GitEvent.ToggleSection(key)) }
                .padding(start = 16.dp, end = 8.dp, top = 2.dp),
        ) {
            Icon(
                if (expanded) XIcons.KeyboardArrowDown else XIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(titleRes) + "  (${changes.size})",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            headerActionLabelRes?.let {
                TextButton(onClick = {
                    onEvent(
                        if (key == GitViewModel.SECTION_STAGED) GitEvent.UnstageAll else GitEvent.StageAll,
                    )
                }) { Text(stringResource(it)) }
            }
        }
    }
    if (expanded) {
        // Index bhi key me shamil — kisi bhi wajah se duplicate repoRelativePath aaye
        // to bhi LazyColumn crash nahi karega.
        itemsIndexed(changes, key = { index, change -> "$key:${change.repoRelativePath}:$index" }) { _, change ->
            DrawerChangeItem(
                change = change,
                duplicateNames = duplicateNames,
                diff = state.diffs[change.repoRelativePath],
                diffLoading = change.repoRelativePath in state.diffLoading,
                onEvent = onEvent,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerChangeItem(
    change: GitPathChange,
    duplicateNames: Set<String>,
    diff: GitFileDiffResult?,
    diffLoading: Boolean,
    onEvent: (GitEvent) -> Unit,
) {
    val (color, labelRes) = changeColorLabel(change)
    val path = change.repoRelativePath
    var menuOpen by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .combinedClickable(
                    // Tap toggles the inline diff: open it, tap again to fold it away.
                    onClick = {
                        if (diff != null) onEvent(GitEvent.CloseDiff(path))
                        else if (!diffLoading) onEvent(GitEvent.LoadDiff(path))
                    },
                    onLongClick = { menuOpen = true },
                )
                .padding(start = 28.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
        ) {
            Box(Modifier.padding(end = 10.dp).size(8.dp).background(color, CircleShape))
            ChangePathText(
                path = path,
                duplicateNames = duplicateNames,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.padding(start = 8.dp),
            )
            Icon(
                if (diff != null) XIcons.KeyboardArrowUp else XIcons.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.padding(start = 4.dp).size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.git_open_in_editor)) },
                        leadingIcon = { Icon(XIcons.FolderOpen, contentDescription = null) },
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
internal fun changeColorLabel(change: GitPathChange): Pair<Color, Int> = when {
    change.staged == GitStageState.CONFLICT || change.unstaged == GitWorkingState.CONFLICT ->
        MaterialTheme.colorScheme.error to R.string.git_state_conflict
    change.staged == GitStageState.ADDED || change.unstaged == GitWorkingState.UNTRACKED ->
        MaterialTheme.colorScheme.tertiary to R.string.git_state_added
    change.staged == GitStageState.DELETED || change.unstaged == GitWorkingState.DELETED ->
        MaterialTheme.colorScheme.error to R.string.git_state_deleted
    else -> MaterialTheme.colorScheme.primary to R.string.git_state_modified
}
