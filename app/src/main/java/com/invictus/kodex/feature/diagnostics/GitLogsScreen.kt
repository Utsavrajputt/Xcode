package com.invictus.kodex.feature.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invictus.kodex.R
import com.invictus.kodex.core.diagnostics.GitLog
import com.invictus.kodex.ui.icons.XIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Slow threshold used only to tint a log line (see [GitLog]); not a filter. */
private const val SLOW_MS = 1_000L

/**
 * Every git operation the app ran (see [GitLog]): op, total / lock-wait / run time, trigger,
 * branch, repo and — for failures — the error and top stack frames. Opened from Settings;
 * recording can be switched off here. Newest first. Failed lines are red, slow ones (>= 1s)
 * are tinted so they stand out.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitLogsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var entries by remember { mutableStateOf<List<GitLog.Entry>>(emptyList()) }
    var enabled by remember { mutableStateOf(GitLog.enabled) }
    val copiedLabel = stringResource(R.string.diagnostics_copied)

    suspend fun reload() {
        entries = withContext(Dispatchers.IO) { GitLog.entries(context) }
    }
    LaunchedEffect(Unit) { reload() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.git_logs_title)) },
                actions = {
                    IconButton(onClick = { scope.launch { reload() } }) {
                        Icon(XIcons.Refresh, contentDescription = null)
                    }
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                val text = withContext(Dispatchers.IO) { GitLog.exportText(context) }
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard?.setPrimaryClip(ClipData.newPlainText("git logs", text))
                                snackbarHostState.showSnackbar(copiedLabel)
                            }
                        }) {
                            Icon(XIcons.Save, contentDescription = stringResource(R.string.action_copy))
                        }
                        IconButton(onClick = {
                            scope.launch {
                                val text = withContext(Dispatchers.IO) { GitLog.exportText(context) }
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(send, null))
                            }
                        }) {
                            Icon(XIcons.CloudUpload, contentDescription = stringResource(R.string.action_share))
                        }
                        IconButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { GitLog.clear(context) }
                                reload()
                            }
                        }) {
                            Icon(XIcons.Close, contentDescription = stringResource(R.string.action_clear))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.git_logs_toggle)) },
                supportingContent = { Text(stringResource(R.string.git_logs_toggle_desc)) },
                trailingContent = {
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            GitLog.setEnabled(context, it)
                        },
                    )
                },
            )
            HorizontalDivider()
            if (entries.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        stringResource(R.string.git_logs_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    )
                }
            } else {
                SelectionContainer {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(entries) { entry ->
                            Text(
                                text = entry.raw,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = when {
                                    !entry.ok -> MaterialTheme.colorScheme.error
                                    entry.totalMs >= SLOW_MS -> MaterialTheme.colorScheme.tertiary
                                    else -> MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
