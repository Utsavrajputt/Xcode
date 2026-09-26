package com.invictus.xcode.feature.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invictus.xcode.R
import com.invictus.xcode.core.editor.EditorSettingsStore
import com.invictus.xcode.ui.components.FileTypeIcon
import com.invictus.xcode.ui.icons.XIcons
import io.github.rosemoe.sora.widget.EditorSearcher
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val handle = remember { EditorHandle() }
    val context = LocalContext.current
    val darkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val highlightReady by viewModel.textMate.ready.collectAsStateWithLifecycle()
    val themeId by viewModel.themeId.collectAsStateWithLifecycle()
    val autocompleteEnabled by viewModel.autocompleteEnabled.collectAsStateWithLifecycle()
    val pairCursorEnabled by viewModel.pairCursorEnabled.collectAsStateWithLifecycle()
    var showSymbolCustomize by remember { mutableStateOf(false) }
    var showFind by remember { mutableStateOf(false) }
    var showQuickActions by remember { mutableStateOf(false) }
    val findState = remember { FindReplaceState() }
    val symbolBar by viewModel.symbolBar.collectAsStateWithLifecycle()
    val symbolBarVisible by viewModel.symbolBarVisible.collectAsStateWithLifecycle()

    // Collapsible app bar: scrolling the editor content hides the title/nav/action row (not the
    // tab bar below it) to give the small-screen keyboard more room, VS Code-mobile style.
    val density = LocalDensity.current
    var appBarHeightPx by remember { mutableFloatStateOf(0f) }
    val appBarOffset = remember { Animatable(0f) }
    val scrollScope = rememberCoroutineScope()
    val onEditorScroll = remember {
        OnEditorScroll { deltaY ->
            if (appBarHeightPx <= 0f) return@OnEditorScroll
            val target = (appBarOffset.value - deltaY).coerceIn(-appBarHeightPx, 0f)
            scrollScope.launch { appBarOffset.snapTo(target) }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it.resolve(context)) }
    }
    // Last tab closed: nothing left to show here.
    LaunchedEffect(state.tabs.isEmpty()) {
        if (state.tabs.isEmpty()) onBack()
    }
    // Switching tabs closes the finder rather than trying to carry it to a different buffer,
    // and un-collapses the bar so a new file always opens with title/actions visible.
    LaunchedEffect(state.activePath) {
        showFind = false
        findState.reset()
        appBarOffset.snapTo(0f)
    }

    // A save from Termux/git or another app while Xcode was backgrounded may not have raised a
    // FileObserver event (Android 11+ FUSE/SAF can miss inotify) -- resume always double-checks.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onResumeCheck() }

    val active = state.tabs.firstOrNull { it.path == state.activePath }
    val activePath = state.activePath
    val activeBuffer = activePath?.let { viewModel.buffer(it) }

    // Keeps Sora's live search and the panel's independent match count both in sync with the
    // query/options/edits, without polling on every recomposition.
    LaunchedEffect(activePath, showFind, findState.query, findState.caseSensitive, findState.useRegex, findState.editEpoch) {
        if (!showFind || activePath == null) return@LaunchedEffect
        val count = activeBuffer?.let {
            countMatches(it.content, findState.query, findState.caseSensitive, findState.useRegex)
        } ?: 0
        findState.matchCount = count
        val editor = handle.editor ?: return@LaunchedEffect
        if (findState.query.isEmpty() || count == null) {
            editor.searcher.stopSearch()
        } else {
            try {
                editor.searcher.search(
                    findState.query,
                    EditorSearcher.SearchOptions(!findState.caseSensitive, findState.useRegex),
                )
            } catch (_: Exception) {
                // Independent countMatches() above already flags a bad pattern to the user.
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            // Bar's own measured height clipped down by how far it has scrolled off --
            // Scaffold reserves exactly this much space, so the tab bar below rides up flush,
            // no blank gap. onGloballyPositioned captures the natural (uncollapsed) height once.
            val barHeightDp = with(density) {
                (appBarHeightPx + appBarOffset.value).coerceAtLeast(0f).toDp()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .let { if (appBarHeightPx > 0f) it.height(barHeightDp) else it }
                    .clipToBounds(),
            ) {
                TopAppBar(
                    modifier = Modifier.onGloballyPositioned { coords ->
                        if (appBarHeightPx <= 0f) appBarHeightPx = coords.size.height.toFloat()
                    },
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
                        IconButton(
                            onClick = {
                                showFind = !showFind
                                if (!showFind) {
                                    handle.editor?.searcher?.stopSearch()
                                    findState.reset()
                                }
                            },
                        ) {
                            Icon(XIcons.Search, contentDescription = stringResource(R.string.find_action))
                        }
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
                        Box {
                            IconButton(onClick = { showQuickActions = true }, enabled = activePath != null) {
                                Icon(XIcons.Bolt, contentDescription = stringResource(R.string.editor_quick_actions))
                            }
                            QuickActionsMenu(
                                expanded = showQuickActions,
                                handle = handle,
                                activePath = activePath,
                                onDismiss = { showQuickActions = false },
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(XIcons.Settings, contentDescription = stringResource(R.string.action_settings))
                        }
                        EditorOverflowMenu(
                            activePath = state.activePath,
                            anyDirty = state.tabs.any { it.dirty },
                            onEvent = viewModel::onEvent,
                        )
                    },
                )
            }
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
            active?.let { tab ->
                ExternalChangeBanner(tab = tab, onEvent = viewModel::onEvent)
            }
            val activePageIndex = active?.pageIndex
            val activePageCount = active?.pageCount
            if (activePath != null && activePageIndex != null && activePageCount != null) {
                PagedFileBar(
                    currentPage = activePageIndex,
                    pageCount = activePageCount,
                    handle = handle,
                    onChangePage = { toIndex, line, column, scrollX, scrollY ->
                        viewModel.onEvent(
                            EditorEvent.ChangePage(activePath, toIndex, line, column, scrollX, scrollY),
                        )
                    },
                )
            }
            if (showFind) {
                FindReplacePanel(
                    state = findState,
                    onQueryChange = { findState.query = it },
                    onFindNext = { try { handle.editor?.searcher?.gotoNext() } catch (_: Exception) {} },
                    onFindPrevious = { try { handle.editor?.searcher?.gotoPrevious() } catch (_: Exception) {} },
                    onReplace = {
                        try {
                            handle.editor?.searcher?.replaceCurrentMatch(findState.replacement)
                        } catch (_: Exception) {
                        }
                    },
                    onReplaceAll = {
                        try {
                            handle.editor?.searcher?.replaceAll(findState.replacement)
                        } catch (_: Exception) {
                        }
                    },
                    onClose = {
                        showFind = false
                        handle.editor?.searcher?.stopSearch()
                        findState.reset()
                    },
                )
            }
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val path = activePath
                val buffer = activeBuffer
                if (path != null && buffer != null) {
                    key(path, activePageIndex ?: 0) {
                        CodeEditorView(
                            buffer = buffer,
                            darkTheme = darkTheme,
                            textMate = viewModel.textMate,
                            highlightReady = highlightReady,
                            themeId = themeId,
                            autocompleteEnabled = autocompleteEnabled,
                            handle = handle,
                            onEdited = {
                                viewModel.onEdited(path)
                                if (showFind) findState.editEpoch++
                            },
                            onViewState = { line, column, size, scrollX, scrollY ->
                                viewModel.onViewState(path, line, column, size, scrollX, scrollY)
                            },
                            onScroll = onEditorScroll,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            if (activePath != null && symbolBarVisible) {
                SymbolBar(
                    handle = handle,
                    symbols = symbolBar,
                    pairCursorEnabled = pairCursorEnabled,
                    onCustomize = { showSymbolCustomize = true },
                )
            }
        }
    }

    state.pendingClose?.let { pending ->
        UnsavedChangesDialog(pending = pending, onEvent = viewModel::onEvent)
    }
    if (showSymbolCustomize) {
        SymbolBarCustomizeDialog(
            current = symbolBar,
            onSave = { viewModel.setSymbolBar(it); showSymbolCustomize = false },
            onResetDefault = { viewModel.setSymbolBar(EditorSettingsStore.DEFAULT_SYMBOLS); showSymbolCustomize = false },
            onDismiss = { showSymbolCustomize = false },
        )
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

/**
 * Drag-to-reorder tab strip. Long-press starts a drag (a plain tap still just switches tabs);
 * the dragged tab follows the finger and swaps past a neighbor once it crosses that neighbor's
 * midpoint -- the usual reorderable-list feel.
 */
@Composable
private fun TabBar(state: EditorUiState, onEvent: (EditorEvent) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.activePath) {
        val index = state.tabs.indexOfFirst { it.path == state.activePath }
        if (index >= 0 && listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listState.animateScrollToItem(index)
        }
    }

    var draggingPath by remember { mutableStateOf<String?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    // Filled in by each tab's onGloballyPositioned; used to size neighbor swap thresholds.
    val itemWidths = remember { mutableMapOf<String, Float>() }

    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer),
        verticalAlignment = Alignment.Bottom,
    ) {
        itemsIndexed(state.tabs, key = { _, tab -> tab.path }) { _, tab ->
            val isDragging = tab.path == draggingPath
            EditorTab(
                tab = tab,
                selected = tab.path == state.activePath,
                onSelect = { onEvent(EditorEvent.Select(tab.path)) },
                onClose = { onEvent(EditorEvent.CloseTab(tab.path)) },
                onTogglePin = { onEvent(EditorEvent.TogglePin(tab.path)) },
                onCloseOthers = { onEvent(EditorEvent.CloseOthers(tab.path)) },
                onCloseAll = { onEvent(EditorEvent.CloseAll) },
                modifier = Modifier
                    .onGloballyPositioned { coords -> itemWidths[tab.path] = coords.size.width.toFloat() }
                    .zIndex(if (isDragging) 1f else 0f)
                    .let { m -> if (isDragging) m.offset { IntOffset(dragOffsetX.roundToInt(), 0) } else m }
                    .pointerInput(tab.path, state.tabs.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingPath = tab.path
                                dragOffsetX = 0f
                            },
                            onDragEnd = {
                                draggingPath = null
                                dragOffsetX = 0f
                            },
                            onDragCancel = {
                                draggingPath = null
                                dragOffsetX = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffsetX += amount.x
                                val myWidth = itemWidths[tab.path] ?: return@detectDragGesturesAfterLongPress
                                val fromIndex = state.tabs.indexOfFirst { it.path == tab.path }
                                if (fromIndex < 0) return@detectDragGesturesAfterLongPress
                                if (dragOffsetX > 0) {
                                    val next = state.tabs.getOrNull(fromIndex + 1) ?: return@detectDragGesturesAfterLongPress
                                    val nextWidth = itemWidths[next.path] ?: myWidth
                                    if (dragOffsetX > nextWidth / 2) {
                                        onEvent(EditorEvent.Reorder(fromIndex, fromIndex + 1))
                                        dragOffsetX -= nextWidth
                                    }
                                } else {
                                    val prev = if (fromIndex > 0) state.tabs[fromIndex - 1] else null
                                    if (prev != null) {
                                        val prevWidth = itemWidths[prev.path] ?: myWidth
                                        if (-dragOffsetX > prevWidth / 2) {
                                            onEvent(EditorEvent.Reorder(fromIndex, fromIndex - 1))
                                            dragOffsetX += prevWidth
                                        }
                                    }
                                }
                            },
                        )
                    },
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
    onTogglePin: () -> Unit,
    onCloseOthers: () -> Unit,
    onCloseAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }
    Surface(
        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.small.copy(bottomStart = CornerSize(0.dp), bottomEnd = CornerSize(0.dp)),
        modifier = modifier
            .height(40.dp)
            .combinedClickable(onClick = onSelect, onLongClick = { showMenu = true }),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 10.dp, end = 2.dp),
        ) {
            if (tab.isPinned) {
                Icon(
                    imageVector = XIcons.Pin,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp).padding(end = 4.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
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
        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (tab.isPinned) R.string.editor_unpin_tab else R.string.editor_pin_tab,
                        ),
                    )
                },
                leadingIcon = { Icon(XIcons.Pin, contentDescription = null) },
                onClick = { showMenu = false; onTogglePin() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_close_others)) },
                onClick = { showMenu = false; onCloseOthers() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.editor_close_all)) },
                onClick = { showMenu = false; onCloseAll() },
            )
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
