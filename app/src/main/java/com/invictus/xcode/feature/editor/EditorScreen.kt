package com.invictus.xcode.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invictus.xcode.R
import com.invictus.xcode.ui.components.FileTypeIcon
import com.invictus.xcode.ui.icons.XIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val handle = remember { EditorHandle() }
    val context = LocalContext.current
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val highlightReady by viewModel.textMate.ready.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }
    // Last tab closed: nothing left to show here.
    LaunchedEffect(state.tabs.isEmpty()) {
        if (state.tabs.isEmpty()) onBack()
    }

    val active = state.tabs.firstOrNull { it.path == state.activePath }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = {
                    Text(
                        text = active?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    IconButton(onClick = { handle.editor?.undo() }) {
                        Icon(XIcons.Undo, contentDescription = stringResource(R.string.action_undo))
                    }
                    IconButton(onClick = { handle.editor?.redo() }) {
                        Icon(XIcons.Redo, contentDescription = stringResource(R.string.action_redo))
                    }
                    IconButton(
                        onClick = { viewModel.onEvent(EditorEvent.SaveActive) },
                        enabled = active?.dirty == true,
                    ) {
                        Icon(XIcons.Save, contentDescription = stringResource(R.string.action_save))
                    }
                    EditorOverflowMenu(
                        activePath = state.activePath,
                        anyDirty = state.tabs.any { it.dirty },
                        onEvent = viewModel::onEvent,
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            TabBar(state = state, onEvent = viewModel::onEvent)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val path = state.activePath
                val buffer = path?.let { viewModel.buffer(it) }
                if (path != null && buffer != null) {
                    key(path) {
                        CodeEditorView(
                            buffer = buffer,
                            darkTheme = darkTheme,
                            textMate = viewModel.textMate,
                            highlightReady = highlightReady,
                            handle = handle,
                            onEdited = { viewModel.onEdited(path) },
                            onViewState = { line, column, size ->
                                viewModel.onViewState(path, line, column, size)
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }

    state.pendingClose?.let { pending ->
        UnsavedChangesDialog(pending = pending, onEvent = viewModel::onEvent)
    }
}

@Composable
private fun EditorOverflowMenu(
    activePath: String?,
    anyDirty: Boolean,
    onEvent: (EditorEvent) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(XIcons.MoreVert, contentDescription = stringResource(R.string.action_more))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_save_all)) },
                enabled = anyDirty,
                onClick = {
                    open = false
                    onEvent(EditorEvent.SaveAll)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_close_tab)) },
                enabled = activePath != null,
                onClick = {
                    open = false
                    activePath?.let { onEvent(EditorEvent.CloseTab(it)) }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_close_others)) },
                enabled = activePath != null,
                onClick = {
                    open = false
                    activePath?.let { onEvent(EditorEvent.CloseOthers(it)) }
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_close_all)) },
                onClick = {
                    open = false
                    onEvent(EditorEvent.CloseAll)
                },
            )
        }
    }
}

@Composable
private fun TabBar(state: EditorUiState, onEvent: (EditorEvent) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.activePath) {
        val index = state.tabs.indexOfFirst { it.path == state.activePath }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer),
        verticalAlignment = Alignment.Bottom,
    ) {
        itemsIndexed(state.tabs, key = { _, tab -> tab.path }) { _, tab ->
            EditorTab(
                tab = tab,
                selected = tab.path == state.activePath,
                onSelect = { onEvent(EditorEvent.Select(tab.path)) },
                onClose = { onEvent(EditorEvent.CloseTab(tab.path)) },
            )
        }
    }
}

@Composable
private fun EditorTab(
    tab: EditorTabUi,
    selected: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp),
        modifier = Modifier.height(40.dp).clickable(onClick = onSelect),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 2.dp),
        ) {
            FileTypeIcon(name = tab.name, isDirectory = false, size = 18.dp)
            Text(
                text = tab.name,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp).widthIn(max = 160.dp),
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (tab.dirty) {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(8.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = XIcons.Close,
                    contentDescription = stringResource(R.string.editor_close_tab),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun UnsavedChangesDialog(pending: PendingClose, onEvent: (EditorEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(EditorEvent.DismissPendingClose) },
        title = { Text(stringResource(R.string.editor_unsaved_title)) },
        text = {
            Text(
                if (pending.dirtyNames.size == 1) {
                    stringResource(R.string.editor_unsaved_one, pending.dirtyNames.first())
                } else {
                    stringResource(R.string.editor_unsaved_many, pending.dirtyNames.size)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { onEvent(EditorEvent.ConfirmSaveAndClose) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { onEvent(EditorEvent.DismissPendingClose) }) {
                    Text(stringResource(R.string.action_cancel))
                }
                TextButton(onClick = { onEvent(EditorEvent.ConfirmDiscardAndClose) }) {
                    Text(stringResource(R.string.action_discard))
                }
            }
        },
    )
}
