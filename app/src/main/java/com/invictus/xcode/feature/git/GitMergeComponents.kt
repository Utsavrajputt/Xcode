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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
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
    state.opProgress?.let { GitOpProgressDialog(it) }
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
                    onClick = { onEvent(GitEvent.ConfirmCompleteMerge) },
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

/** M11 rebase dialogs; rides along wherever the drawer sheet is composed. */
@Composable
internal fun GitRebaseDialogs(state: GitViewModel.UiState, onEvent: (GitEvent) -> Unit) {
    if (state.rebasePicker) {
        AlertDialog(
            onDismissRequest = { if (!state.rebasing) onEvent(GitEvent.DismissRebasePicker) },
            title = { Text(stringResource(R.string.git_rebase_picker_title)) },
            text = {
                Column(
                    Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        stringResource(R.string.git_rebase_picker_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    if (state.rebasing) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                        ) {
                            CircularProgressIndicator(Modifier.width(18.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.git_rebasing),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else if (state.rebaseLoading) {
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
                    } else if (state.rebaseCandidates.isEmpty()) {
                        Text(
                            stringResource(R.string.git_rebase_no_branches),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(8.dp),
                        )
                    } else {
                        state.rebaseCandidates.forEach { branch ->
                            Text(
                                branch,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onEvent(GitEvent.RebaseOnto(branch)) }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(
                    onClick = { onEvent(GitEvent.DismissRebasePicker) },
                    enabled = !state.rebasing,
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (state.abortRebaseConfirm) {
        AlertDialog(
            onDismissRequest = { onEvent(GitEvent.DismissAbortRebase) },
            title = { Text(stringResource(R.string.git_abort_rebase_title)) },
            text = { Text(stringResource(R.string.git_abort_rebase_message)) },
            confirmButton = {
                TextButton(onClick = { onEvent(GitEvent.ConfirmAbortRebase) }) {
                    Text(
                        stringResource(R.string.git_abort_rebase_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { onEvent(GitEvent.DismissAbortRebase) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}


/**
 * Non-dismissible "working…" dialog for long local git ops (resolve, commit, merge, rebase,
 * reset, stage all). Appears only after 300ms so fast ops on small repos never flash it.
 * Shows a determinate bar when the op reports a total (e.g. resolving N conflicted files).
 */
@Composable
internal fun GitOpProgressDialog(progress: GitViewModel.GitOpProgress) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(300)
        visible = true
    }
    if (!visible) return
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(progress.titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { progress.done.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.git_progress_count, progress.done, progress.total),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                Text(
                    stringResource(R.string.git_progress_wait),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
    )
}
