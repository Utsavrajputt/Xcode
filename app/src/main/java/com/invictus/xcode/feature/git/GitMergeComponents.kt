package com.invictus.xcode.feature.git

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.xcode.R
import com.invictus.xcode.core.git.model.GitConflictSide
import com.invictus.xcode.core.git.model.GitPathChange

/** Hosts every M10 dialog; rides along wherever the drawer sheet is composed. */
@Composable
internal fun GitMergeDialogs(state: GitViewModel.UiState, onEvent: (GitEvent) -> Unit) {
    if (state.mergeDialog) {
        AlertDialog(
            onDismissRequest = { onEvent(GitEvent.DismissMerge) },
            title = { Text(stringResource(R.string.git_merge_title)) },
            text = {
                if (state.merging) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    ) {
                        CircularProgressIndicator(Modifier.width(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.git_merging),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else if (state.mergeCandidates.isEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                    ) {
                        CircularProgressIndicator(Modifier.width(18.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.git_loading),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                } else {
                    Column(
                        Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    ) {
                        state.mergeCandidates.forEach { branch ->
                            Text(
                                branch,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEvent(GitEvent.Merge(branch)) }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { onEvent(GitEvent.DismissMerge) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (state.abortConfirm) {
        AlertDialog(
            onDismissRequest = { onEvent(GitEvent.DismissAbortMerge) },
            title = { Text(stringResource(R.string.git_abort_title)) },
            text = { Text(stringResource(R.string.git_abort_message)) },
            confirmButton = {
                TextButton(onClick = { onEvent(GitEvent.ConfirmAbortMerge) }) {
                    Text(
                        stringResource(R.string.git_abort_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(GitEvent.DismissAbortMerge) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (state.completeMergeDialog) {
        AlertDialog(
            onDismissRequest = { onEvent(GitEvent.DismissCompleteMerge) },
            title = { Text(stringResource(R.string.git_complete_title)) },
            text = {
                OutlinedTextField(
                    value = state.completeMergeMessage,
                    onValueChange = { onEvent(GitEvent.CompleteMergeMessageChange(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text(stringResource(R.string.git_commit_hint)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { onEvent(GitEvent.CompleteMerge) },
                    enabled = state.completeMergeMessage.isNotBlank() && !state.completingMerge,
                ) { Text(stringResource(R.string.git_complete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(GitEvent.DismissCompleteMerge) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    state.conflictPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { onEvent(GitEvent.DismissConflictPreview) },
            title = {
                Text(
                    stringResource(
                        if (preview.side == GitConflictSide.OURS)
                            R.string.git_preview_ours_title else R.string.git_preview_theirs_title,
                        preview.path,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        preview.content.ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { onEvent(GitEvent.DismissConflictPreview) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** Per-file resolution actions shown under a conflicted row in the drawer. */
@Composable
internal fun ConflictActionsRow(change: GitPathChange, onEvent: (GitEvent) -> Unit) {
    val path = change.repoRelativePath
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 46.dp, end = 8.dp),
    ) {
        TextButton(onClick = { onEvent(GitEvent.ResolveConflict(path, GitConflictSide.OURS)) }) {
            Text(stringResource(R.string.git_conflict_ours))
        }
        TextButton(onClick = { onEvent(GitEvent.ResolveConflict(path, GitConflictSide.THEIRS)) }) {
            Text(stringResource(R.string.git_conflict_theirs))
        }
        TextButton(onClick = { onEvent(GitEvent.MarkResolved(path)) }) {
            Text(stringResource(R.string.git_conflict_resolved))
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 46.dp, end = 8.dp, bottom = 4.dp),
    ) {
        TextButton(onClick = { onEvent(GitEvent.PreviewConflictSide(path, GitConflictSide.OURS)) }) {
            Text(stringResource(R.string.git_preview_ours))
        }
        TextButton(onClick = { onEvent(GitEvent.PreviewConflictSide(path, GitConflictSide.THEIRS)) }) {
            Text(stringResource(R.string.git_preview_theirs))
        }
    }
}
