package com.invictus.xcode.feature.project

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.invictus.xcode.R
import com.invictus.xcode.feature.workspace.asString
import com.invictus.xcode.ui.components.FileTypeIcon
import com.invictus.xcode.ui.icons.XIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Open Project bottom sheet: one field that both filters and accepts a typed path, the recent
 * workspaces, and a folder browser. The search field is a fixed header, so the lists scroll
 * underneath it and never overlap it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenProjectSheet(
    state: ProjectsUiState,
    currentRoot: File,
    snackbarHostState: SnackbarHostState,
    onEvent: (ProjectsEvent) -> Unit,
    onOpen: (File) -> Unit,
    onCopyPath: (File) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { onEvent(ProjectsEvent.SheetOpened) }

    val trimmed = query.trim()
    val typedPath = trimmed.takeIf { it.startsWith("/") }
    val needle = if (typedPath == null) trimmed else ""
    val pathIsFolder by produceState<Boolean?>(null, typedPath) {
        value = typedPath?.let { path ->
            withContext(Dispatchers.IO) { File(path).let { it.isDirectory && it.canRead() } }
        }
    }

    val recents = state.recents.filter { matches(it.file, needle) }
    val shortcuts = state.shortcuts.filter { matches(it.file, needle) }
    val entries = state.browseEntries.filter { matches(it, needle) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(SHEET_HEIGHT_FRACTION).imePadding()) {
            Text(
                text = stringResource(R.string.sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            SearchField(
                query = query,
                onQueryChange = { query = it },
                onGo = { if (typedPath != null && pathIsFolder == true) onOpen(File(typedPath)) },
            )
            LazyColumn(modifier = Modifier.weight(1f)) {
                if (typedPath != null) {
                    item(key = "typed") {
                        TypedPathRow(path = typedPath, isFolder = pathIsFolder, onOpen = { onOpen(File(typedPath)) })
                    }
                }
                if (recents.isNotEmpty()) {
                    item(key = "h-recent") { SectionLabel(R.string.sheet_recent) }
                    items(items = recents, key = { "r:" + it.file.path }) { item ->
                        RecentRow(
                            item = item,
                            isCurrent = item.file.path == currentRoot.path,
                            isBackingUp = item.file.path in state.backingUp,
                            onOpen = { onOpen(item.file) },
                            onEvent = onEvent,
                            onCopyPath = onCopyPath,
                        )
                    }
                }
                item(key = "h-browse") { SectionLabel(R.string.sheet_browse) }
                val browseDir = state.browseDir
                if (browseDir == null) {
                    items(items = shortcuts, key = { "s:" + it.file.path }) { shortcut ->
                        FolderRow(
                            name = shortcut.label?.asString() ?: shortcut.file.name,
                            onEnter = { onEvent(ProjectsEvent.Browse(shortcut.file)) },
                            onOpen = { onOpen(shortcut.file) },
                        )
                    }
                } else {
                    item(key = "browse-head") {
                        BrowseHeader(
                            dir = browseDir,
                            onUp = { onEvent(ProjectsEvent.BrowseUp) },
                            onOpen = { onOpen(browseDir) },
                        )
                    }
                    val error = state.browseError
                    if (error != null) {
                        item(key = "browse-error") { HintText(error.asString(), isError = true) }
                    } else if (entries.isEmpty()) {
                        item(key = "browse-empty") { HintText(stringResource(R.string.sheet_no_subfolders)) }
                    }
                    items(items = entries, key = { "b:" + it.path }) { dir ->
                        FolderRow(
                            name = dir.name,
                            onEnter = { onEvent(ProjectsEvent.Browse(dir)) },
                            onOpen = { onOpen(dir) },
                        )
                    }
                }
            }
            SnackbarHost(snackbarHostState)
        }
    }

    state.renaming?.let { RenameProjectDialog(it, onEvent) }
}

private const val SHEET_HEIGHT_FRACTION = 0.9f

private fun matches(file: File, needle: String): Boolean =
    needle.isEmpty() || file.name.contains(needle, ignoreCase = true) || file.path.contains(needle, ignoreCase = true)

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, onGo: () -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.sheet_search_hint)) },
        leadingIcon = { Icon(XIcons.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(XIcons.Close, contentDescription = stringResource(R.string.action_clear))
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onGo() }),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionLabel(textRes: Int) {
    Text(
        text = stringResource(textRes),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun HintText(text: String, isError: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
    )
}

@Composable
private fun TypedPathRow(path: String, isFolder: Boolean?, onOpen: () -> Unit) {
    val enabled = isFolder == true
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, onClick = onOpen)
            .alpha(if (isFolder == false) 0.6f else 1f)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(XIcons.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.sheet_open_path),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = if (isFolder == false) stringResource(R.string.sheet_path_missing) else path,
                style = MaterialTheme.typography.bodySmall,
                color = if (isFolder == false) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BrowseHeader(dir: File, onUp: () -> Unit, onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onUp) {
            Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
        }
        Text(
            text = dir.path,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpen) { Text(stringResource(R.string.action_open_folder)) }
    }
}

/** Tap enters the folder; the trailing button opens it as the workspace. */
@Composable
private fun FolderRow(name: String, onEnter: () -> Unit, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(onClick = onEnter)
            .padding(start = 24.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileTypeIcon(name = name, isDirectory = true)
        Spacer(Modifier.width(16.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpen) { Text(stringResource(R.string.action_open_folder)) }
    }
}

/** Tap opens; long-press opens the action menu at the finger (same pattern as the file tree). */
@Composable
private fun RecentRow(
    item: RecentItem,
    isCurrent: Boolean,
    isBackingUp: Boolean,
    onOpen: () -> Unit,
    onEvent: (ProjectsEvent) -> Unit,
    onCopyPath: (File) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(Offset.Zero) }
    var rowHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val menuOffset = with(density) { DpOffset(pressOffset.x.toDp(), (pressOffset.y - rowHeightPx).toDp()) }
    val file = item.file

    Box(modifier = Modifier.fillMaxWidth().onSizeChanged { rowHeightPx = it.height }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .pointerInput(file.path, item.exists) {
                    detectTapGestures(
                        onTap = { if (item.exists) onOpen() },
                        onLongPress = { position ->
                            pressOffset = position
                            menuOpen = true
                        },
                    )
                }
                .alpha(if (item.exists) 1f else 0.5f)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileTypeIcon(name = file.name, isDirectory = true)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (item.isDeviceStorage) stringResource(R.string.workspace_root_internal) else file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (item.exists) file.path else stringResource(R.string.sheet_folder_missing, file.path),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when {
                isBackingUp -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                isCurrent -> Text(
                    text = stringResource(R.string.sheet_current),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, offset = menuOffset) {
            val close = { menuOpen = false }
            val canModify = item.exists && !item.isDeviceStorage
            if (canModify) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.recent_menu_backup)) },
                    enabled = !isBackingUp,
                    onClick = {
                        close()
                        onEvent(ProjectsEvent.Backup(file))
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.tree_menu_copy_path)) },
                onClick = {
                    close()
                    onCopyPath(file)
                },
            )
            if (canModify) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.tree_menu_rename)) },
                    onClick = {
                        close()
                        onEvent(ProjectsEvent.StartRename(file))
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.recent_menu_remove)) },
                onClick = {
                    close()
                    onEvent(ProjectsEvent.RemoveRecent(file.path))
                },
            )
        }
    }
}

@Composable
private fun RenameProjectDialog(state: RenameProject, onEvent: (ProjectsEvent) -> Unit) {
    var text by rememberSaveable(state.file.path) { mutableStateOf(state.file.name) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val error = state.error

    AlertDialog(
        onDismissRequest = { onEvent(ProjectsEvent.DismissRename) },
        title = { Text(stringResource(R.string.dialog_rename_project_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    isError = error != null,
                    supportingText = {
                        Text(error?.asString() ?: stringResource(R.string.dialog_rename_project_hint))
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onEvent(ProjectsEvent.ConfirmRename(text)) }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(ProjectsEvent.ConfirmRename(text)) }) {
                Text(stringResource(R.string.tree_menu_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ProjectsEvent.DismissRename) }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}
