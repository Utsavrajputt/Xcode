package com.invictus.kodex.github.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.kodex.R
import com.invictus.kodex.ui.icons.XIcons

/**
 * Run log in its own screen (plan 6.3): Error excerpt (default) / Full log toggle, job picker when
 * several jobs failed, and two always-visible copy actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunLogScreen(
    projectPath: String,
    runId: Long,
    onBack: () -> Unit,
    viewModel: RunLogViewModel = viewModel(
        key = "gh_run_log_${projectPath}_$runId",
        factory = RunLogViewModel.factory(projectPath, runId),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event -> handleEvent(event, context, snackbar, scope) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gh_log_title), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::copyError, enabled = !state.loading && state.error == null) {
                        Icon(XIcons.ContentCopy, contentDescription = stringResource(R.string.gh_copy_error))
                    }
                    IconButton(onClick = viewModel::copyFull, enabled = !state.loading && state.hasLogFile) {
                        Icon(XIcons.FileCopy, contentDescription = stringResource(R.string.gh_copy_full_log))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            state.run?.let { run ->
                Text(
                    listOf(
                        state.workflowName, "run #${run.runNumber}", run.branch, run.shortSha,
                        stringResource(statusLabel(run.display)),
                    ).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                LogMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { viewModel.setMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, LogMode.entries.size),
                    ) { Text(stringResource(if (mode == LogMode.Error) R.string.gh_log_mode_error else R.string.gh_log_mode_full)) }
                }
            }
            if (state.failedJobs.size > 1) JobPicker(state, viewModel)

            Box(Modifier.fillMaxSize().padding(top = 8.dp)) {
                when {
                    state.loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.error != null -> Column(
                        Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(state.error!!.resolve(context), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = viewModel::retry) { Text(stringResource(R.string.gh_retry)) }
                        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
                    }
                    state.mode == LogMode.Error -> LogLines(state.excerptLines, note = null)
                    state.fullLoading || state.fullLines == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                    else -> LogLines(
                        state.fullLines!!,
                        note = if (state.fullCutAtStart) stringResource(R.string.gh_log_showing_tail) else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun JobPicker(state: RunLogState, vm: RunLogViewModel) {
    var open by remember { mutableStateOf(false) }
    val current = state.failedJobs.firstOrNull { it.id == state.selectedJobId }
    Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        OutlinedButton(onClick = { open = true }) {
            Text(current?.name ?: stringResource(R.string.gh_log_pick_job), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            state.failedJobs.forEach { job ->
                DropdownMenuItem(text = { Text(job.name) }, onClick = { open = false; vm.selectJob(job.id) })
            }
        }
    }
}

/** Monospace virtualised lines; one shared horizontal scroll so the whole block pans together. */
@Composable
private fun LogLines(lines: List<String>, note: String?) {
    val hScroll = rememberScrollState()
    val style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
    Column(Modifier.fillMaxSize()) {
        note?.let { Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        Box(Modifier.fillMaxSize().horizontalScroll(hScroll)) {
            LazyColumn(Modifier.fillMaxHeight().padding(horizontal = 12.dp)) {
                itemsIndexed(lines) { _, line ->
                    Text(line, style = style, softWrap = false)
                }
            }
        }
    }
}

private fun statusLabel(s: com.invictus.kodex.github.api.RunDisplayStatus): Int = when (s) {
    com.invictus.kodex.github.api.RunDisplayStatus.Success -> R.string.gh_status_success
    com.invictus.kodex.github.api.RunDisplayStatus.Failed -> R.string.gh_status_failed
    com.invictus.kodex.github.api.RunDisplayStatus.Running -> R.string.gh_status_running
    com.invictus.kodex.github.api.RunDisplayStatus.Queued -> R.string.gh_status_queued
    com.invictus.kodex.github.api.RunDisplayStatus.Cancelled -> R.string.gh_status_cancelled
    com.invictus.kodex.github.api.RunDisplayStatus.Skipped -> R.string.gh_status_skipped
}
