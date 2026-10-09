package com.invictus.xcode.feature.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.xcode.R
import com.invictus.xcode.XcodeApp
import com.invictus.xcode.core.data.SearchQueryEntity
import com.invictus.xcode.core.editor.EditorSettingsStore
import com.invictus.xcode.core.search.FileSearchEngine
import com.invictus.xcode.core.search.SearchHistoryStore
import com.invictus.xcode.core.search.SearchKind
import com.invictus.xcode.ui.components.FileTypeIcon
import com.invictus.xcode.ui.icons.XIcons
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

/** Plan 3.6: file search (naam, fuzzy) — quick-open style overlay ka state. */
class FileSearchViewModel(
    private val root: File,
    private val engine: FileSearchEngine,
    private val history: SearchHistoryStore,
    private val settingsStore: EditorSettingsStore,
) : ViewModel() {
    data class UiState(
        val query: String = "",
        val results: List<FileSearchEngine.Result> = emptyList(),
        val searching: Boolean = false,
        val history: List<SearchQueryEntity> = emptyList(),
        val recent: List<File> = emptyList(),
        val showFilters: Boolean = false,
        val extFilter: String = "",
        val folderFilter: String = "",
        val includeIgnored: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            history.observe(SearchKind.FILE).collect { items ->
                _uiState.update { it.copy(history = items) }
            }
        }
        viewModelScope.launch {
            settingsStore.recentFiles.collect { paths ->
                // Drop files that vanished or live outside the current project.
                val rootPath = root.path
                val files = paths.map(::File).filter { it.isFile && it.path.startsWith(rootPath) }
                _uiState.update { it.copy(recent = files) }
            }
        }
    }

    fun toggleFilters() = _uiState.update { it.copy(showFilters = !it.showFilters) }
    fun setExtFilter(v: String) { _uiState.update { it.copy(extFilter = v) }; onQueryChange(_uiState.value.query) }
    fun setFolderFilter(v: String) { _uiState.update { it.copy(folderFilter = v) }; onQueryChange(_uiState.value.query) }
    fun setIncludeIgnored(v: Boolean) { _uiState.update { it.copy(includeIgnored = v) }; onQueryChange(_uiState.value.query) }

    fun clearHistory() {
        viewModelScope.launch { history.clear(SearchKind.FILE) }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(250) // debounce; naya query aane par purana cancel
            _uiState.update { it.copy(searching = true) }
            val s = _uiState.value
            val found = engine.search(
                root, query,
                includeIgnored = s.includeIgnored,
                extensionFilter = s.extFilter,
                folderFilter = s.folderFilter,
                defaultValuesOnly = settingsStore.searchDefaultStringsOnly.first(),
                excludeGlobs = settingsStore.searchExtraExcludes.first().split(',', '\n'),
            )
            _uiState.update { it.copy(results = found, searching = false) }
        }
    }

    /** Result open hone par query history me record karo. */
    fun recordHistory() {
        viewModelScope.launch { history.record(SearchKind.FILE, _uiState.value.query) }
    }

    fun removeHistory(query: String) {
        viewModelScope.launch { history.delete(SearchKind.FILE, query) }
    }

    companion object {
        private val RootKey = object : CreationExtras.Key<File> {}

        fun factory(root: File) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as XcodeApp
                FileSearchViewModel(
                    root = this[RootKey] ?: root,
                    engine = app.container.fileSearchEngine,
                    history = app.container.searchHistoryStore,
                    settingsStore = app.container.editorSettingsStore,
                )
            }
        }

        /** Root-keyed VM: root badalne par purana state reuse na ho. */
        @Composable
        fun get(root: File, owner: androidx.lifecycle.ViewModelStoreOwner): FileSearchViewModel {
            // Owner ke default extras (APPLICATION_KEY yahin se aata hai) ko base banao,
            // warna factory() me `this[APPLICATION_KEY] as XcodeApp` null milta hai aur crash hota hai.
            val defaultExtras = (owner as? androidx.lifecycle.HasDefaultViewModelProviderFactory)
                ?.defaultViewModelCreationExtras
            val extras = MutableCreationExtras(defaultExtras ?: CreationExtras.Empty).apply {
                set(RootKey, root)
            }
            return viewModel(owner, key = "file_search_${root.path}", factory = factory(root), extras = extras)
        }
    }
}

/**
 * Quick-open overlay: top se, fuzzy file search; result tap -> [onOpen]. Long-press on a result
 * (when [onLocateInTree] is given) offers "Show in tree".
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileSearchOverlay(
    root: File,
    onOpen: (File) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onLocateInTree: ((File) -> Unit)? = null,
) {
    // File whose long-press menu is open (recent rows and result rows share it).
    var menuFile by remember { mutableStateOf<File?>(null) }
    val viewModel = FileSearchViewModel.get(root, LocalViewModelStoreOwner.current!!)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            // Status bar ke neeche se shuru ho, warna search field aadhi status bar
            // ke peeche chhup jaati hai (jaisa screenshot me dikha).
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                Spacer(Modifier.width(4.dp))
                // M3 Expressive: pill-shaped, tonal, floating search field.
                Surface(
                    modifier = Modifier.weight(1f).height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp,
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            XIcons.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 12.dp),
                        )
                        TextField(
                            value = state.query,
                            onValueChange = viewModel::onQueryChange,
                            modifier = Modifier.weight(1f).focusRequester(focusRequester),
                            placeholder = { Text(stringResource(R.string.search_files_hint)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        )
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(
                                    XIcons.Close,
                                    contentDescription = stringResource(R.string.action_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = viewModel::toggleFilters) {
                    Icon(
                        XIcons.Tune,
                        contentDescription = stringResource(R.string.search_filters),
                        tint = if (state.showFilters || state.extFilter.isNotBlank() || state.folderFilter.isNotBlank() || state.includeIgnored) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            if (state.showFilters) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = state.extFilter,
                            onValueChange = viewModel::setExtFilter,
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.search_filter_ext)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                        )
                        OutlinedTextField(
                            value = state.folderFilter,
                            onValueChange = viewModel::setFolderFilter,
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.search_filter_folder)) },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                        )
                    }
                    FilterChip(
                        selected = state.includeIgnored,
                        onClick = { viewModel.setIncludeIgnored(!state.includeIgnored) },
                        label = { Text(stringResource(R.string.search_include_ignored)) },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            if (state.query.isBlank()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (state.recent.isNotEmpty()) {
                        item(key = "recent_header") {
                            Text(
                                text = stringResource(R.string.search_recent_files),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            )
                        }
                        items(state.recent, key = { "recent:" + it.path }) { file ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Box {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = { onOpen(file) },
                                            onLongClick = { if (onLocateInTree != null) menuFile = file },
                                        )
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    FileTypeIcon(name = file.name, isDirectory = false)
                                    Column(modifier = Modifier.padding(start = 12.dp)) {
                                        Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(
                                            text = file.path.removePrefix(root.path),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.StartEllipsis,
                                        )
                                    }
                                }
                                ShowInTreeMenu(menuFile == file, { menuFile = null }) { onLocateInTree?.invoke(file) }
                                }
                            }
                        }
                    }
                    item(key = "history_header") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.search_history),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp, vertical = 8.dp),
                            )
                            if (state.history.isNotEmpty()) {
                                TextButton(onClick = viewModel::clearHistory) {
                                    Text(stringResource(R.string.search_clear_history))
                                }
                            }
                        }
                    }
                    items(state.history, key = { it.id }) { entry ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.onQueryChange(entry.query) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(XIcons.Search, contentDescription = null)
                                Text(
                                    text = entry.query,
                                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                IconButton(onClick = { viewModel.removeHistory(entry.query) }) {
                                    Icon(XIcons.Close, contentDescription = stringResource(R.string.action_delete))
                                }
                            }
                        }
                    }
                }
            } else {
                if (state.searching && state.results.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_searching),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.results, key = { it.file.path }) { result ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        onClick = {
                                            viewModel.recordHistory()
                                            onOpen(result.file)
                                        },
                                        onLongClick = { if (onLocateInTree != null) menuFile = result.file },
                                    )
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                FileTypeIcon(name = result.name, isDirectory = false)
                                Column(modifier = Modifier.padding(start = 12.dp)) {
                                    Text(
                                        text = highlightedName(result),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = result.relativePath,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            ShowInTreeMenu(menuFile == result.file, { menuFile = null }) { onLocateInTree?.invoke(result.file) }
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** Fuzzy-matched characters ko highlight karta hua file naam. Workspace tree ke inline search me bhi reuse hota hai. */
@Composable
internal fun highlightedName(result: FileSearchEngine.Result): AnnotatedString =
    buildAnnotatedString {
        val highlight = MaterialTheme.colorScheme.primary
        var cursor = 0
        val indices = result.matchedIndices
        var i = 0
        while (i < indices.size) {
            val start = indices[i]
            var end = start
            while (i + 1 < indices.size && indices[i + 1] == end + 1) {
                end = indices[i + 1]
                i++
            }
            if (start > cursor) append(result.name.substring(cursor, start))
            pushStyle(SpanStyle(color = highlight, fontWeight = FontWeight.Bold))
            append(result.name.substring(start, end + 1))
            pop()
            cursor = end + 1
            i++
        }
        if (cursor < result.name.length) append(result.name.substring(cursor))
    }

/** Long-press menu on a file-search row: reveal the file in the project tree. */
@Composable
private fun ShowInTreeMenu(expanded: Boolean, onDismiss: () -> Unit, onShow: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.search_show_in_tree)) },
            onClick = {
                onDismiss()
                onShow()
            },
        )
    }
}
