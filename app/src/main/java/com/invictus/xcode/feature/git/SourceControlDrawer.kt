package com.invictus.xcode.feature.git

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.xcode.R
import com.invictus.xcode.core.git.model.GitFileDiffResult
import com.invictus.xcode.core.git.model.GitPathChange
import com.invictus.xcode.core.git.model.GitStageState
import com.invictus.xcode.core.git.model.GitWorkingState
import com.invictus.xcode.ui.icons.XIcons
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
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                item("commit") { DrawerCommitCard(state, onEvent) }
                item("actions") { DrawerQuickActions(state, onEvent) }
                if (state.snapshot?.mergeInProgress == true) {
                    item("merge-banner") { MergeInProgressBanner(state, onEvent) }
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
                drawerSection(
                    key = GitViewModel.SECTION_CONFLICTS,
                    titleRes = R.string.git_section_conflicts,
                    changes = status.conflicts,
                    state = state,
                    headerActionLabelRes = null,
                    onEvent = onEvent,
                )
                drawerSection(
                    key = GitViewModel.SECTION_STAGED,
                    titleRes = R.string.git_section_staged,
                    changes = status.staged,
                    state = state,
                    headerActionLabelRes = R.string.git_unstage_all,
                    onEvent = onEvent,
                )
                drawerSection(
                    key = GitViewModel.SECTION_CHANGES,
                    titleRes = R.string.git_section_changes,
                    changes = status.unstaged.filter { it.unstaged != GitWorkingState.UNTRACKED },
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
}

@Composable
private fun MergeInProgressBanner(state: GitViewModel.UiState, onEvent: (GitEvent) -> Unit) {
    val conflicts = state.status?.conflicts?.size ?: 0
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Text(
            text = if (conflicts > 0)
                stringResource(R.string.git_merge_in_progress_conflicts, conflicts)
            else stringResource(R.string.git_merge_in_progress),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { onEvent(GitEvent.CompleteMerge) }) {
                Text(stringResource(R.string.git_complete_merge))
            }
            TextButton(onClick = { onEvent(GitEvent.AbortMerge) }) {
                Text(
                    stringResource(R.string.git_abort_merge),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
    HorizontalDivider()
}

@Composable
private fun DrawerHeader(state: GitViewModel.UiState, onEvent: (GitEvent) -> Unit) {
    val snapshot = state.snapshot ?: return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
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
                enabled = state.commitMessage.isNotBlank() && !state.committing,
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
        OutlinedButton(onClick = { onEvent(GitEvent.Push) }, enabled = canSync) {
            Icon(XIcons.CloudUpload, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.git_push))
        }
        OutlinedButton(onClick = { onEvent(GitEvent.Pull) }, enabled = canSync) {
            Icon(XIcons.CloudDownload, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.git_pull))
        }
        OutlinedButton(
            onClick = { onEvent(GitEvent.OpenMerge) },
            enabled = state.snapshot?.hasCommits == true &&
                state.snapshot?.mergeInProgress != true && !state.merging,
        ) { Text(stringResource(R.string.git_merge)) }
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
    val expanded = key in state.expandedSections
    item("$key:header") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .clickable { onEvent(GitEvent.ToggleSection(key)) }
                .padding(start = 16.dp, end = 8.dp, top = 8.dp),
        ) {
            Icon(
                if (expanded) XIcons.KeyboardArrowDown else XIcons.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(titleRes) + "  (${'$'}{changes.size})",
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
        items(changes, key = { "$key:${'$'}{it.repoRelativePath}" }) { change ->
            DrawerChangeItem(
                change = change,
                diff = state.diffs[change.repoRelativePath],
                diffLoading = change.repoRelativePath in state.diffLoading,
                onEvent = onEvent,
            )
        }
    }
}

@Composable
private fun DrawerChangeItem(
    change: GitPathChange,
    diff: GitFileDiffResult?,
    diffLoading: Boolean,
    onEvent: (GitEvent) -> Unit,
) {
    val (color, labelRes) = changeColorLabel(change)
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
                .clickable { onEvent(GitEvent.LoadDiff(change.repoRelativePath)) }
                .padding(start = 28.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Box(Modifier.padding(end = 10.dp).size(8.dp).background(color, CircleShape))
            Text(
                change.repoRelativePath,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onEvent(GitEvent.OpenFile(change.repoRelativePath)) }) {
                Text(stringResource(R.string.git_open_in_editor))
            }
            Text(
                stringResource(labelRes),
                style = MaterialTheme.typography.labelSmall,
                color = color,
            )
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
        val isConflict = change.staged == GitStageState.CONFLICT ||
            change.unstaged == GitWorkingState.CONFLICT
        if (isConflict) {
            ConflictActionsRow(change, onEvent)
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
