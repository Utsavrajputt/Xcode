package com.invictus.xcode.feature.git

import androidx.compose.animation.core.animateFloatAsState
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
        BranchPickerDialog(
            title = stringResource(R.string.git_merge_title),
            description = null,
            phase = when {
                state.merging -> BranchPickerPhase.BUSY
                state.mergeCandidates.isEmpty() -> BranchPickerPhase.LOADING
                else -> BranchPickerPhase.LIST
            },
            branches = state.mergeCandidates,
            busyText = stringResource(R.string.git_merging),
            emptyText = "",
            dismissible = true,
            onPick = { onEvent(GitEvent.Merge(it)) },
            onDismiss = { onEvent(GitEvent.DismissMerge) },
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
        BranchPickerDialog(
            title = stringResource(R.string.git_rebase_picker_title),
            description = stringResource(R.string.git_rebase_picker_desc),
            phase = when {
                state.rebasing -> BranchPickerPhase.BUSY
                state.rebaseLoading -> BranchPickerPhase.LOADING
                state.rebaseCandidates.isEmpty() -> BranchPickerPhase.EMPTY
                else -> BranchPickerPhase.LIST
            },
            branches = state.rebaseCandidates,
            busyText = stringResource(R.string.git_rebasing),
            emptyText = stringResource(R.string.git_rebase_no_branches),
            dismissible = !state.rebasing,
            onPick = { onEvent(GitEvent.RebaseOnto(it)) },
            onDismiss = { onEvent(GitEvent.DismissRebasePicker) },
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
 * reset, stage all). Appears only after 1.5s so fast ops never flash it.
 * Shows a determinate bar when the op reports a total (e.g. resolving N conflicted files).
 */
@Composable
internal fun GitOpProgressDialog(progress: GitViewModel.GitOpProgress) {
    // Quick ops (stage a few files, small commit) finish well inside this window, so they never
    // flash a dialog; only genuinely long ones get one.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(PROGRESS_DIALOG_DELAY_MS)
        visible = true
    }
    if (!visible) return
    val determinate = progress.total > 0
    val fraction = if (determinate) (progress.done.toFloat() / progress.total).coerceIn(0f, 1f) else 0f
    val animated by animateFloatAsState(fraction, label = "gitOpProgress")
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(progress.titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (determinate) {
                    LinearProgressIndicator(
                        progress = { animated },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.git_progress_count, progress.done, progress.total),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${(fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
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

private const val PROGRESS_DIALOG_DELAY_MS = 1500L
