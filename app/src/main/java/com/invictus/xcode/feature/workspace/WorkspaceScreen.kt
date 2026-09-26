package com.invictus.xcode.feature.workspace

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.activity.compose.BackHandler
import com.invictus.xcode.ui.components.expressiveClickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.invictus.xcode.feature.project.OpenProjectSheet
import com.invictus.xcode.feature.project.ProjectsEffect
import com.invictus.xcode.feature.project.ProjectsEvent
import com.invictus.xcode.feature.project.ProjectsViewModel
import com.invictus.xcode.ui.icons.XIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Workspace home: the file tree. [onOpenFile] hands a tapped file to the editor;
 * [externalMessages] carries errors from it (e.g. a file that failed to open).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    modifier: Modifier = Modifier,
    viewModel: FileTreeViewModel = viewModel(factory = FileTreeViewModel.Factory),
    projectsViewModel: ProjectsViewModel = viewModel(factory = ProjectsViewModel.Factory),
    onOpenFile: ((File) -> Unit)? = null,
    externalMessages: Flow<UiText>? = null,
    onProjectRoot: (File) -> Unit = {},
    onOpenGit: () -> Unit = {},
    onClone: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.root) { onProjectRoot(state.root) }
    val projectsState by projectsViewModel.uiState.collectAsStateWithLifecycle()
    val isGitRepo by produceState(false, state.root) {
        value = withContext(Dispatchers.IO) { File(state.root, ".git").isDirectory }
    }
    var showProjectSheet by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
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
                is FileTreeEffect.ScrollTo -> {
                    val index = viewModel.uiState.value.rows
                        .indexOfFirst { it is TreeRow.Entry && it.file.path == effect.path }
                    if (index >= 0) listState.animateScrollToItem(index)
                }
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

    LaunchedEffect(externalMessages) {
        externalMessages?.collect { text ->
            scope.launch { snackbarHostState.showSnackbar(text.resolve(context)) }
        }
    }

    LaunchedEffect(projectsViewModel) {
        projectsViewModel.effects.collect { effect ->
            when (effect) {
                is ProjectsEffect.Message ->
                    scope.launch { snackbarHostState.showSnackbar(effect.text.resolve(context)) }
                is ProjectsEffect.ProjectMoved ->
                    viewModel.onEvent(FileTreeEvent.ProjectMoved(effect.old, effect.new))
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
        topBar = {
            WorkspaceTopBar(
                state = state,
                onEvent = viewModel::onEvent,
                onOpenProjectSheet = { showProjectSheet = true },
                onBackup = { projectsViewModel.onEvent(ProjectsEvent.Backup(state.root)) },
                onOpenGit = if (isGitRepo) onOpenGit else null,
            )
        },
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
                FileTree(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onCopyPath = copyPath,
                    listState = listState,
                )
            }
        }
    }

    TreeDialogHost(dialog = state.dialog, onEvent = viewModel::onEvent)

    if (showProjectSheet) {
        OpenProjectSheet(
            state = projectsState,
            currentRoot = state.root,
            snackbarHostState = snackbarHostState,
            onEvent = projectsViewModel::onEvent,
            onOpen = { dir ->
                showProjectSheet = false
                viewModel.onEvent(FileTreeEvent.OpenProject(dir))
            },
            onCopyPath = copyPath,
            onClone = onClone,
            onDismiss = { showProjectSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceTopBar(
    state: FileTreeUiState,
    onEvent: (FileTreeEvent) -> Unit,
    onOpenProjectSheet: () -> Unit,
    onBackup: () -> Unit,
    onOpenGit: (() -> Unit)? = null,
) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = {
            Text(
                text = state.rootName ?: stringResource(R.string.workspace_root_internal),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.expressiveClickable(onClick = onOpenProjectSheet),
            )
        },
        actions = {
            IconButton(onClick = onOpenProjectSheet) {
                Icon(XIcons.FolderOpen, contentDescription = stringResource(R.string.menu_open_project))
            }
            IconButton(onClick = { onEvent(FileTreeEvent.Refresh) }) {
                Icon(XIcons.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
            if (onOpenGit != null) {
                IconButton(onClick = onOpenGit) {
                    Icon(XIcons.Commit, contentDescription = stringResource(R.string.git_title))
                }
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
                    if (state.rootName != null) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_backup_project)) },
                            onClick = {
                                menuOpen = false
                                onBackup()
                            },
                        )
                    }
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
