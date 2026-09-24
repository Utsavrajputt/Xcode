package com.invictus.xcode.feature.workspace

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.xcode.R
import com.invictus.xcode.ui.icons.XIcons
import kotlinx.coroutines.launch
import java.io.File

/**
 * Workspace home: the file tree. [onOpenFile] is where the editor (M3) plugs in; until then
 * tapping a file just says so.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    modifier: Modifier = Modifier,
    viewModel: FileTreeViewModel = viewModel(factory = FileTreeViewModel.Factory),
    onOpenFile: ((File) -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Changes made outside the app (or missed by the watcher) show up when the user comes back.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(FileTreeEvent.Refresh) }

    BackHandler(enabled = state.renaming != null) { viewModel.onEvent(FileTreeEvent.CancelRename) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is FileTreeEffect.Message ->
                    scope.launch { snackbarHostState.showSnackbar(effect.text.resolve(context)) }
                is FileTreeEffect.OpenFile ->
                    if (onOpenFile != null) {
                        onOpenFile(effect.file)
                    } else {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.workspace_open_placeholder, effect.file.name),
                            )
                        }
                    }
            }
        }
    }

    val copyPath: (File) -> Unit = { file ->
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(file.name, file.path))
        // Android 13+ shows its own "copied" confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_path_copied)) }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { WorkspaceTopBar(state = state, onEvent = viewModel::onEvent) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val clipboard = state.clipboard
            if (clipboard != null) {
                ClipboardBar(clipboard = clipboard, onClear = { viewModel.onEvent(FileTreeEvent.ClearClipboard) })
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            val rootError = state.rootError
            if (rootError != null) {
                Text(
                    text = rootError.asString(),
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                FileTree(state = state, onEvent = viewModel::onEvent, onCopyPath = copyPath)
            }
        }
    }

    TreeDialogHost(dialog = state.dialog, onEvent = viewModel::onEvent)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceTopBar(state: FileTreeUiState, onEvent: (FileTreeEvent) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Text(
                text = state.rootName ?: stringResource(R.string.workspace_root_internal),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        actions = {
            IconButton(onClick = { onEvent(FileTreeEvent.Refresh) }) {
                Icon(XIcons.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(XIcons.MoreVert, contentDescription = stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_show_hidden)) },
                        onClick = { onEvent(FileTreeEvent.SetShowHidden(!state.showHidden)) },
                        trailingIcon = { Checkbox(checked = state.showHidden, onCheckedChange = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_show_git)) },
                        onClick = { onEvent(FileTreeEvent.SetShowGitFolder(!state.showGitFolder)) },
                        trailingIcon = { Checkbox(checked = state.showGitFolder, onCheckedChange = null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.menu_collapse_all)) },
                        onClick = {
                            menuOpen = false
                            onEvent(FileTreeEvent.CollapseAll)
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun ClipboardBar(clipboard: Clipboard, onClear: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (clipboard.isCut) R.string.clipboard_bar_cut else R.string.clipboard_bar_copied,
                    clipboard.file.name,
                ),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) }
        }
    }
}
