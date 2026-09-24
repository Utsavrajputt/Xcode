package com.invictus.xcode.feature.workspace

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.invictus.xcode.R
import com.invictus.xcode.ui.components.FileTypeIcon
import com.invictus.xcode.ui.icons.XIcons
import java.io.File

@Composable
fun UiText.asString(): String = stringResource(resId, *args.toTypedArray())

/** The visible tree. Whole-row tap expands/collapses or opens; long-press opens the action menu. */
@Composable
fun FileTree(
    state: FileTreeUiState,
    onEvent: (FileTreeEvent) -> Unit,
    onCopyPath: (File) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    val rootLabel = state.rootName ?: stringResource(R.string.workspace_root_internal)
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState) {
        items(items = state.rows, key = { it.key }) { row ->
            when (row) {
                TreeRow.PinnedHeader -> PinnedHeader()
                TreeRow.Divider -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                is TreeRow.Pinned -> PinnedRow(row = row, onEvent = onEvent, onCopyPath = onCopyPath)
                is TreeRow.Entry -> FileRow(
                    row = row,
                    displayName = if (row.isRoot) rootLabel else row.file.name,
                    renaming = state.renaming?.takeIf { it.file.path == row.file.path },
                    isCutMarked = state.clipboard?.let { it.isCut && it.file.path == row.file.path } == true,
                    canPaste = state.clipboard != null,
                    isPinned = row.file.path in state.pinnedPaths,
                    onEvent = onEvent,
                    onCopyPath = onCopyPath,
                )
                is TreeRow.Empty -> EmptyRow(depth = row.depth)
            }
        }
    }
}

@Composable
private fun FileRow(
    row: TreeRow.Entry,
    displayName: String,
    renaming: RenameState?,
    isCutMarked: Boolean,
    canPaste: Boolean,
    isPinned: Boolean,
    onEvent: (FileTreeEvent) -> Unit,
    onCopyPath: (File) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(Offset.Zero) }
    var rowHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val isRenaming = renaming != null

    // DropdownMenu anchors to the bottom-left of its parent, so subtract the row height to
    // make the menu open at the finger instead of below the row.
    val menuOffset = with(density) {
        DpOffset(pressOffset.x.toDp(), (pressOffset.y - rowHeightPx).toDp())
    }

    Box(modifier = Modifier.fillMaxWidth().onSizeChanged { rowHeightPx = it.height }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .pointerInput(row.file.path, isRenaming) {
                    detectTapGestures(
                        onTap = {
                            if (!isRenaming) onEvent(FileTreeEvent.RowClicked(row.file, row.isDirectory))
                        },
                        onLongPress = { position ->
                            if (!isRenaming) {
                                pressOffset = position
                                menuOpen = true
                            }
                        },
                    )
                }
                .padding(start = (8 + row.depth * 16).dp, end = 8.dp)
                .alpha(if (isCutMarked) 0.5f else 1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (row.isDirectory) {
                val rotation by animateFloatAsState(
                    targetValue = if (row.isExpanded) 90f else 0f,
                    label = "chevron",
                )
                Icon(
                    imageVector = XIcons.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Spacer(Modifier.width(20.dp))
            }
            Spacer(Modifier.width(6.dp))
            FileTypeIcon(name = row.file.name, isDirectory = row.isDirectory)
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (renaming != null) {
                    InlineRenameField(
                        name = row.file.name,
                        error = renaming.error,
                        onCommit = { onEvent(FileTreeEvent.CommitRename(it)) },
                        onCancel = { onEvent(FileTreeEvent.CancelRename) },
                    )
                } else {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (row.isLoading) {
                Spacer(Modifier.width(8.dp))
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
            }
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            offset = menuOffset,
        ) {
            val close = { menuOpen = false }
            if (row.isDirectory) {
                MenuItem(R.string.tree_menu_new_file) {
                    close()
                    onEvent(FileTreeEvent.StartCreate(row.file, isFolder = false))
                }
                MenuItem(R.string.tree_menu_new_folder) {
                    close()
                    onEvent(FileTreeEvent.StartCreate(row.file, isFolder = true))
                }
            }
            if (!row.isRoot) {
                MenuItem(R.string.tree_menu_rename) {
                    close()
                    onEvent(FileTreeEvent.StartRename(row.file))
                }
                MenuItem(R.string.tree_menu_duplicate) {
                    close()
                    onEvent(FileTreeEvent.Duplicate(row.file))
                }
                MenuItem(R.string.tree_menu_cut) {
                    close()
                    onEvent(FileTreeEvent.Cut(row.file))
                }
                MenuItem(R.string.tree_menu_copy) {
                    close()
                    onEvent(FileTreeEvent.Copy(row.file))
                }
                MenuItem(if (isPinned) R.string.tree_menu_unpin else R.string.tree_menu_pin) {
                    close()
                    onEvent(FileTreeEvent.TogglePin(row.file, row.isDirectory))
                }
            }
            if (canPaste) {
                MenuItem(R.string.tree_menu_paste) {
                    close()
                    onEvent(FileTreeEvent.PasteInto(if (row.isDirectory) row.file else row.file.parentFile ?: row.file))
                }
            }
            MenuItem(R.string.tree_menu_copy_path) {
                close()
                onCopyPath(row.file)
            }
            if (!row.isRoot) {
                HorizontalDivider()
                MenuItem(R.string.tree_menu_delete) {
                    close()
                    onEvent(FileTreeEvent.RequestDelete(row.file, row.isDirectory))
                }
            }
        }
    }
}

@Composable
private fun PinnedHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = XIcons.Pin,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.tree_pinned_header),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Tap opens a pinned file (through the usual binary/large warning) or reveals a pinned folder. */
@Composable
private fun PinnedRow(row: TreeRow.Pinned, onEvent: (FileTreeEvent) -> Unit, onCopyPath: (File) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    var pressOffset by remember { mutableStateOf(Offset.Zero) }
    var rowHeightPx by remember { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val file = row.item.file
    val menuOffset = with(density) { DpOffset(pressOffset.x.toDp(), (pressOffset.y - rowHeightPx).toDp()) }

    Box(modifier = Modifier.fillMaxWidth().onSizeChanged { rowHeightPx = it.height }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .pointerInput(file.path) {
                    detectTapGestures(
                        onTap = {
                            if (row.item.isDirectory) {
                                onEvent(FileTreeEvent.Reveal(file, isDirectory = true))
                            } else {
                                onEvent(FileTreeEvent.RowClicked(file, isDirectory = false))
                            }
                        },
                        onLongPress = { position ->
                            pressOffset = position
                            menuOpen = true
                        },
                    )
                }
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FileTypeIcon(name = file.name, isDirectory = row.item.isDirectory)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (row.parentLabel.isNotEmpty()) {
                    Text(
                        text = row.parentLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, offset = menuOffset) {
            MenuItem(R.string.tree_menu_unpin) {
                menuOpen = false
                onEvent(FileTreeEvent.TogglePin(file, row.item.isDirectory))
            }
            MenuItem(R.string.tree_menu_reveal) {
                menuOpen = false
                onEvent(FileTreeEvent.Reveal(file, isDirectory = false))
            }
            MenuItem(R.string.tree_menu_copy_path) {
                menuOpen = false
                onCopyPath(file)
            }
        }
    }
}

@Composable
private fun MenuItem(@StringRes textRes: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(textRes)) }, onClick = onClick)
}

@Composable
private fun EmptyRow(depth: Int) {
    Text(
        text = stringResource(R.string.tree_empty_folder),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .padding(start = (8 + depth * 16 + 26).dp, end = 8.dp)
            .padding(top = 8.dp),
    )
}

/**
 * Rename in place. Selects the name without its extension, keeps itself scrolled above the
 * keyboard, Done commits, losing focus (or Back) cancels.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InlineRenameField(
    name: String,
    error: UiText?,
    onCommit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val stemEnd = name.lastIndexOf('.').let { if (it > 0) it else name.length }
    var value by remember { mutableStateOf(TextFieldValue(name, TextRange(0, stemEnd))) }
    val focusRequester = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    var hadFocus by remember { mutableStateOf(false) }
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    // Re-run as the keyboard animates in so the row is never left behind it.
    LaunchedEffect(imeBottom) { bringIntoView.bringIntoView() }

    val accent = MaterialTheme.colorScheme.primary
    Column(modifier = Modifier.fillMaxWidth().bringIntoViewRequester(bringIntoView)) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCommit(value.text) }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged {
                    if (it.isFocused) hadFocus = true else if (hadFocus) onCancel()
                },
            decorationBox = { innerTextField ->
                Column {
                    innerTextField()
                    HorizontalDivider(color = accent)
                }
            },
        )
        if (error != null) {
            Text(
                text = error.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
