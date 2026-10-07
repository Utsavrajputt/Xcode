package com.invictus.xcode.feature.git

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.xcode.R
import com.invictus.xcode.core.git.model.GitCommitSummary
import com.invictus.xcode.core.git.model.GitResetMode
import com.invictus.xcode.ui.icons.XIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One selectable reset target: what gets passed to `git reset --<mode> <ref>`. */
private data class GitResetTargetChip(val ref: String, val label: String)

@Composable
fun GitResetModeSelector(mode: GitResetMode, onModeChange: (GitResetMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GitResetMode.entries.forEach { m ->
                FilterChip(
                    selected = mode == m,
                    onClick = { onModeChange(m) },
                    label = { Text(stringResource(m.titleRes())) },
                )
            }
        }
        Text(
            text = stringResource(mode.descRes()),
            style = MaterialTheme.typography.bodySmall,
            color = if (mode == GitResetMode.HARD) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private fun GitResetMode.titleRes(): Int = when (this) {
    GitResetMode.SOFT -> R.string.git_reset_mode_soft
    GitResetMode.MIXED -> R.string.git_reset_mode_mixed
    GitResetMode.HARD -> R.string.git_reset_mode_hard
}

private fun GitResetMode.descRes(): Int = when (this) {
    GitResetMode.SOFT -> R.string.git_reset_mode_soft_desc
    GitResetMode.MIXED -> R.string.git_reset_mode_mixed_desc
    GitResetMode.HARD -> R.string.git_reset_mode_hard_desc
}

/**
 * Full reset sheet, opened from [GitScreen]'s top bar: mode (soft/mixed/hard) + quick
 * targets (HEAD, every configured remote's tracking branch) + a commit picker for
 * anything further back in history. HARD confirms via [GitViewModel.PendingReset]
 * before it actually runs (see GitScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitResetSheet(
    state: GitViewModel.UiState,
    onEvent: (GitEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var mode by remember { mutableStateOf(GitResetMode.HARD) }
    var target by remember { mutableStateOf<GitResetTargetChip?>(null) }
    var picking by remember { mutableStateOf(false) }
    var pickingBranch by remember { mutableStateOf(false) }

    val branch = state.snapshot?.headName
    val tracking = state.snapshot?.trackingInfo
    // Only refs that really exist: HEAD, then main/master (local or remote), then the current
    // branch's own tracking / same-named remote ref. Everything else is behind the "…" chip.
    val quickTargets = remember(branch, tracking, state.resetBranches, state.remotes) {
        val existing = state.resetBranches.toSet()
        val defaults = buildSet {
            add("master"); add("main")
            state.remotes.forEach { r -> add("${r.name}/master"); add("${r.name}/main") }
        }
        fun isDefault(n: String) = n in defaults
        buildList {
            add(GitResetTargetChip("HEAD", "HEAD"))
            state.resetBranches.filter(::isDefault).forEach { add(GitResetTargetChip(it, it)) }
            tracking?.let { t -> "${t.remote}/${t.branch}".takeIf { it in existing }?.let { add(GitResetTargetChip(it, it)) } }
            branch?.let { b ->
                state.resetBranches.filter { it.contains('/') && it.substringAfter('/') == b }
                    .forEach { add(GitResetTargetChip(it, it)) }
            }
        }.distinctBy { it.ref }
    }
    val chips = remember(target, quickTargets) {
        val custom = target?.takeIf { t -> quickTargets.none { it.ref == t.ref } }
        if (custom != null) listOf(custom) + quickTargets else quickTargets
    }

    LaunchedEffect(picking) { if (picking) onEvent(GitEvent.LoadResetCommits) }

    if (pickingBranch) {
        BranchPickerDialog(
            title = stringResource(R.string.git_reset_target_label),
            description = null,
            phase = when {
                !state.resetBranchesLoaded -> BranchPickerPhase.LOADING
                state.resetBranches.isEmpty() -> BranchPickerPhase.EMPTY
                else -> BranchPickerPhase.LIST
            },
            branches = state.resetBranches,
            busyText = "",
            emptyText = stringResource(R.string.git_rebase_no_branches),
            dismissible = true,
            onPick = { name ->
                target = GitResetTargetChip(ref = name, label = name)
                pickingBranch = false
            },
            onDismiss = { pickingBranch = false },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = branch?.let { stringResource(R.string.git_reset_title_branch, it) }
                    ?: stringResource(R.string.git_reset_title),
                style = MaterialTheme.typography.titleLarge,
            )

            if (picking) {
                GitResetCommitPicker(
                    commits = state.resetCommits,
                    loading = state.resetCommitsLoading,
                    onPick = { c ->
                        target = GitResetTargetChip(
                            ref = c.id,
                            label = "${c.shortId} · ${c.message.lineSequence().firstOrNull().orEmpty()}",
                        )
                        picking = false
                    },
                    onBack = { picking = false },
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.git_reset_mode_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    GitResetModeSelector(mode = mode, onModeChange = { mode = it })
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.git_reset_target_label),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        chips.forEach { t ->
                            FilterChip(
                                selected = target?.ref == t.ref,
                                onClick = { target = t },
                                label = {
                                    Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                            )
                        }
                        AssistChip(
                            onClick = { pickingBranch = true },
                            label = { Text("…") },
                        )
                    }
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(XIcons.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.git_reset_pick_commit))
                    }
                }

                Button(
                    onClick = { target?.let { onEvent(GitEvent.ResetTo(it.ref, it.label, mode)) } },
                    enabled = target != null && !state.resetting,
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (mode == GitResetMode.HARD) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                ) {
                    if (state.resetting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(stringResource(R.string.git_reset_confirm))
                    }
                }
            }
        }
    }
}

private val resetCommitTimeFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

@Composable
private fun GitResetCommitPicker(
    commits: List<GitCommitSummary>,
    loading: Boolean,
    onPick: (GitCommitSummary) -> Unit,
    onBack: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(XIcons.ChevronLeft, contentDescription = stringResource(R.string.action_back))
            }
            Text(
                text = stringResource(R.string.git_reset_pick_commit),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        when {
            loading -> Row(
                Modifier.fillMaxWidth().padding(24.dp),
                horizontalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator(Modifier.size(28.dp)) }
            commits.isEmpty() -> Text(
                text = stringResource(R.string.git_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                items(commits, key = { it.id }) { c ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 10.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = c.message.lineSequence().firstOrNull().orEmpty(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    text = "${c.shortId} · ${c.author} · " +
                                        resetCommitTimeFormat.format(Date(c.timeMs)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { onPick(c) }) {
                                Text(stringResource(R.string.git_reset_use_commit))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Compact "reset to this commit" dialog used from [GitHistoryScreen]'s commit detail —
 * the target is already fixed (the tapped commit), the person only picks the mode.
 */
@Composable
fun GitResetModeDialog(
    commit: GitCommitSummary,
    resetting: Boolean,
    onConfirm: (GitResetMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var mode by remember(commit) { mutableStateOf(GitResetMode.HARD) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.git_reset_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.git_reset_here_target, commit.shortId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                GitResetModeSelector(mode = mode, onModeChange = { mode = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(mode) }, enabled = !resetting) {
                Text(
                    text = stringResource(R.string.git_reset_confirm),
                    color = if (mode == GitResetMode.HARD) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !resetting) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
