package com.invictus.xcode.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

/** Plan 3.6: file search (naam, fuzzy) — quick-open style overlay ka state. */
class FileSearchViewModel(
    private val root: File,
    private val engine: FileSearchEngine,
    private val history: SearchHistoryStore,
) : ViewModel() {
    data class UiState(
        val query: String = "",
        val results: List<FileSearchEngine.Result> = emptyList(),
        val searching: Boolean = false,
        val history: List<SearchQueryEntity> = emptyList(),
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
            val found = engine.search(root, query)
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

/** Quick-open overlay: top se, fuzzy file search; result tap -> [onOpen]. */
@Composable
fun FileSearchOverlay(
    root: File,
    onOpen: (File) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = FileSearchViewModel.get(root, LocalViewModelStoreOwner.current!!)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                }
                TextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    placeholder = { Text(stringResource(R.string.search_files_hint)) },
                    singleLine = true,
                )
            }
            HorizontalDivider()

            if (state.query.isBlank()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item(key = "history_header") {
                        Text(
                            text = stringResource(R.string.search_history),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    items(state.history, key = { it.id }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onQueryChange(entry.query) }
                                .padding(horizontal = 16.dp, vertical = 4.dp),
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
            } else {
                if (state.searching && state.results.isEmpty()) {
                    Text(
                        text = stringResource(R.string.search_searching),
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.results, key = { it.file.path }) { result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.recordHistory()
                                    onOpen(result.file)
                                }
                                .padding(horizontal = 16.dp, vertical = 6.dp),
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
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}

/** Fuzzy-matched characters ko highlight karta hua file naam. */
@Composable
private fun highlightedName(result: FileSearchEngine.Result): AnnotatedString =
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
