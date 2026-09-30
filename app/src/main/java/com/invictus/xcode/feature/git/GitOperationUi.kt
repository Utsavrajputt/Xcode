package com.invictus.xcode.feature.git

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.invictus.xcode.core.git.model.GitFileDiffResult
import com.invictus.xcode.R
import com.invictus.xcode.core.git.model.GitConflictSide
import com.invictus.xcode.core.git.model.GitPathChange
import com.invictus.xcode.core.git.model.GitStageState
import com.invictus.xcode.core.git.model.GitWorkingState
import com.invictus.xcode.ui.icons.XIcons

/** True when [change] is an unresolved merge/rebase conflict. */
internal fun GitPathChange.isConflict(): Boolean =
    staged == GitStageState.CONFLICT || unstaged == GitWorkingState.CONFLICT

private val CompactButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)

/**
 * "Rebase in progress" / "Merge in progress" card with its Continue / Skip / Abort (or Complete /
 * Abort) buttons. Shared by the drawer and the full Git screen; renders nothing when no
 * operation is running.
 */
@Composable
internal fun GitOperationBanner(state: GitViewModel.UiState, onEvent: (GitEvent) -> Unit) {
    val snapshot = state.snapshot ?: return
    val rebase = snapshot.rebaseInProgress
    val merge = snapshot.mergeInProgress
    if (!rebase && !merge) return

    val conflicts = state.status?.conflicts?.size ?: 0
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (rebase) XIcons.Rebase else XIcons.Merge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp),
                )
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        stringResource(if (rebase) R.string.git_rebase_in_progress else R.string.git_merge_in_progress),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (conflicts > 0) stringResource(R.string.git_op_conflicts_left, conflicts)
                        else stringResource(R.string.git_op_all_resolved),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (conflicts > 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { onEvent(if (rebase) GitEvent.ContinueRebase else GitEvent.CompleteMerge) },
                    enabled = conflicts == 0,
                    contentPadding = CompactButtonPadding,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(XIcons.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(
                        stringResource(if (rebase) R.string.git_op_continue else R.string.git_op_complete),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (rebase) {
                    OutlinedButton(
                        onClick = { onEvent(GitEvent.SkipRebaseCommit) },
                        contentPadding = CompactButtonPadding,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(XIcons.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.git_op_skip), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                OutlinedButton(
                    onClick = { onEvent(if (rebase) GitEvent.AbortRebase else GitEvent.AbortMerge) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = CompactButtonPadding,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(XIcons.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.git_op_abort), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** "Conflicts (N)" header (+ "Mark all resolved") plus one [ConflictCard] per file. Nothing when there are none. */
internal fun LazyListScope.conflictSection(
    conflicts: List<GitPathChange>,
    diffs: Map<String, GitFileDiffResult>,
    diffLoading: Set<String>,
    onEvent: (GitEvent) -> Unit,
) {
    if (conflicts.isEmpty()) return
    item(key = "conflicts:header") {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 4.dp, bottom = 0.dp),
        ) {
            Text(
                stringResource(R.string.git_section_conflicts) + "  (${conflicts.size})",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onEvent(GitEvent.MarkAllResolved) },
                contentPadding = CompactButtonPadding,
            ) {
                Icon(XIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
                Text(
                    stringResource(R.string.git_mark_all_resolved),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    itemsIndexed(conflicts, key = { index, change -> "conflicts:${change.repoRelativePath}:$index" }) { _, change ->
        ConflictCard(
            change = change,
            diff = diffs[change.repoRelativePath],
            diffLoading = change.repoRelativePath in diffLoading,
            onEvent = onEvent,
        )
    }
}

/**
 * One conflicted file: name + folder, and Ours / Theirs / Mark resolved.
 * Tap the name to open the file in the editor; long-press it for a full-screen diff.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ConflictCard(
    change: GitPathChange,
    diff: GitFileDiffResult?,
    diffLoading: Boolean,
    onEvent: (GitEvent) -> Unit,
) {
    var showDiff by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val path = change.repoRelativePath
    val name = path.substringAfterLast('/')
    val dir = path.substringBeforeLast('/', "")
    if (showDiff) {
        ConflictDiffDialog(
            name = name,
            dir = dir,
            diff = diff,
            loading = diffLoading,
            onClose = {
                showDiff = false
                onEvent(GitEvent.CloseDiff(path))
            },
        )
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 14.dp, end = 6.dp, top = 0.dp, bottom = 12.dp)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onEvent(GitEvent.OpenFile(path)) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            showDiff = true
                            onEvent(GitEvent.LoadDiff(path))
                        },
                    )
                    .padding(end = 8.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (dir.isNotEmpty()) {
                    Text(
                        dir,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            ) {
                OutlinedButton(
                    onClick = { onEvent(GitEvent.ResolveConflict(path, GitConflictSide.OURS)) },
                    contentPadding = CompactButtonPadding,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.git_conflict_ours), maxLines = 1) }
                OutlinedButton(
                    onClick = { onEvent(GitEvent.ResolveConflict(path, GitConflictSide.THEIRS)) },
                    contentPadding = CompactButtonPadding,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.git_conflict_theirs), maxLines = 1) }
                FilledTonalButton(
                    onClick = { onEvent(GitEvent.MarkResolved(path)) },
                    contentPadding = CompactButtonPadding,
                    modifier = Modifier.weight(1.5f),
                ) {
                    Icon(XIcons.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.git_op_resolved), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Full-screen diff for one conflicted file (long-press on its name). */
@Composable
private fun ConflictDiffDialog(
    name: String,
    dir: String,
    diff: GitFileDiffResult?,
    loading: Boolean,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                ) {
                    IconButton(onClick = onClose) {
                        Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.git_diff_close))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        if (dir.isNotEmpty()) {
                            Text(
                                dir,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                HorizontalDivider()
                when {
                    diff != null -> GitDiffViewer(result = diff, modifier = Modifier.weight(1f))
                    loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    else -> Box(Modifier.weight(1f).fillMaxWidth())
                }
            }
        }
    }
}
