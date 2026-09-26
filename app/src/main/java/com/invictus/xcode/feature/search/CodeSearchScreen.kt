package com.invictus.xcode.feature.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.xcode.R
import com.invictus.xcode.XcodeApp
import com.invictus.xcode.core.data.SearchQueryEntity
import com.invictus.xcode.core.search.CodeSearchEngine
import com.invictus.xcode.core.search.SearchHistoryStore
import com.invictus.xcode.core.search.SearchKind
import com.invictus.xcode.ui.components.FileTypeIcon
import com.invictus.xcode.ui.icons.XIcons
import java.io.File
import java.util.regex.PatternSyntaxException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Plan 3.6: code search (content, poora workspace) — full screen state. */
class CodeSearchViewModel(
    private val root: File,
    private val engine: CodeSearchEngine,
    private val history: SearchHistoryStore,
) : ViewModel() {
    data class UiState(
        val query: String = "",
        val regex: Boolean = false,
        val caseSensitive: Boolean = false,
        val wholeWord: Boolean = false,
        val includeGlob: String = "",
        val excludeGlob: String = "",
        val searching: Boolean = false,
        val results: List<CodeSearchEngine.FileResult> = emptyList(),
        val fileCount: Int = 0,
        val matchCount: Int = 0,
        val history: List<SearchQueryEntity> = emptyList(),
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            history.observe(SearchKind.CODE).collect { items ->
                _uiState.update { it.copy(history = items) }
            }
        }
    }

    private fun optionsJson(): String {
        val s = _uiState.value
        return JSONObject().apply {
            put("regex", s.regex)
            put("case", s.caseSensitive)
            put("word", s.wholeWord)
            put("include", s.includeGlob)
            put("exclude", s.excludeGlob)
        }.toString()
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(results = emptyList(), fileCount = 0, matchCount = 0, searching = false, error = null)
            }
            return
        }
        if (_uiState.value.regex) {
            try {
                Regex(query)
                _uiState.update { it.copy(error = null) }
            } catch (e: PatternSyntaxException) {
                _uiState.update { it.copy(error = e.description ?: query, searching = false) }
                return
            }
        }
        val options = CodeSearchEngine.Options(
            regex = _uiState.value.regex,
            caseSensitive = _uiState.value.caseSensitive,
            wholeWord = _uiState.value.wholeWord,
            includeGlob = _uiState.value.includeGlob,
            excludeGlob = _uiState.value.excludeGlob,
        )
        searchJob = viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(searching = true) }
            val found = mutableListOf<CodeSearchEngine.FileResult>()
            var matches = 0
            // Stream: results dheere dheere dikhte rahen; naya query -> collect cancel.
            engine.search(root, query, options).collect { fr ->
                found.add(fr)
                matches += fr.matches.size
                _uiState.update {
                    it.copy(results = found.toList(), fileCount = found.size, matchCount = matches)
                }
            }
            _uiState.update { it.copy(searching = false) }
        }
    }

    fun setRegex(v: Boolean) = applyOption { it.copy(regex = v) }
    fun setCase(v: Boolean) = applyOption { it.copy(caseSensitive = v) }
    fun setWholeWord(v: Boolean) = applyOption { it.copy(wholeWord = v) }
    fun setInclude(v: String) = applyOption { it.copy(includeGlob = v) }
    fun setExclude(v: String) = applyOption { it.copy(excludeGlob = v) }

    private fun applyOption(transform: (UiState) -> UiState) {
        _uiState.update(transform)
        onQueryChange(_uiState.value.query)
    }

    fun recordHistory() {
        viewModelScope.launch { history.record(SearchKind.CODE, _uiState.value.query, optionsJson()) }
    }

    /** History item: options restore karke wahi search dobara chalao. */
    fun runFromHistory(entry: SearchQueryEntity) {
        runCatching { JSONObject(entry.optionsJson) }.getOrNull()?.let { o ->
            _uiState.update {
                it.copy(
                    regex = o.optBoolean("regex"),
                    caseSensitive = o.optBoolean("case"),
                    wholeWord = o.optBoolean("word"),
                    includeGlob = o.optString("include"),
                    excludeGlob = o.optString("exclude"),
                )
            }
        }
        onQueryChange(entry.query)
    }

    fun removeHistory(query: String) {
        viewModelScope.launch { history.delete(SearchKind.CODE, query) }
    }

    companion object {
        private val RootKey = object : CreationExtras.Key<File> {}

        private fun factory(root: File) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as XcodeApp
                CodeSearchViewModel(
                    root = this[RootKey] ?: root,
                    engine = app.container.codeSearchEngine,
                    history = app.container.searchHistoryStore,
                )
            }
        }

        @Composable
        fun get(projectPath: String, owner: androidx.lifecycle.ViewModelStoreOwner): CodeSearchViewModel {
            val extras = MutableCreationExtras().apply { set(RootKey, File(projectPath)) }
            return androidx.lifecycle.viewmodel.compose.viewModel(
                owner,
                key = "code_search_$projectPath",
                factory = factory(File(projectPath)),
                extras = extras,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeSearchScreen(
    projectPath: String,
    onBack: () -> Unit,
    onOpenMatch: (file: File, line: Int) -> Unit,
    onLocateInTree: (file: File) -> Unit,
) {
    val owner = LocalViewModelStoreOwner.current ?: return
    val viewModel = CodeSearchViewModel.get(projectPath, owner)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_code_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(stringResource(R.string.search_code_hint)) },
                singleLine = true,
            )

            // Options row
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                FilterChip(
                    selected = state.regex,
                    onClick = { viewModel.setRegex(!state.regex) },
                    label = { Text(stringResource(R.string.search_regex)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
                FilterChip(
                    selected = state.caseSensitive,
                    onClick = { viewModel.setCase(!state.caseSensitive) },
                    label = { Text(stringResource(R.string.search_case_sensitive)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
                FilterChip(
                    selected = state.wholeWord,
                    onClick = { viewModel.setWholeWord(!state.wholeWord) },
                    label = { Text(stringResource(R.string.search_whole_word)) },
                )
            }
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                OutlinedTextField(
                    value = state.includeGlob,
                    onValueChange = viewModel::setInclude,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    label = { Text(stringResource(R.string.search_include_glob)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.excludeGlob,
                    onValueChange = viewModel::setExclude,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(R.string.search_exclude_glob)) },
                    singleLine = true,
                )
            }

            state.error?.let { err ->
                Text(
                    text = stringResource(R.string.search_invalid_regex, err),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }

            if (state.fileCount > 0) {
                Text(
                    text = stringResource(R.string.search_matches_summary, state.matchCount, state.fileCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (state.query.isBlank()) {
                    item(key = "history_header") {
                        Text(
                            text = stringResource(R.string.search_history),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    items(state.history, key = { it.id }) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.runFromHistory(entry) }
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(XIcons.Search, contentDescription = null)
                            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(entry.query, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (entry.optionsJson != "{}") {
                                    Text(
                                        text = entry.optionsJson,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.removeHistory(entry.query) }) {
                                Icon(XIcons.Close, contentDescription = stringResource(R.string.action_delete))
                            }
                        }
                    }
                } else {
                    state.results.forEach { fileResult ->
                        item(key = "hdr:${fileResult.file.path}") {
                            FileResultHeader(
                                fileResult = fileResult,
                                onLocateInTree = { onLocateInTree(fileResult.file) },
                            )
                        }
                        items(fileResult.matches, key = { "${fileResult.file.path}:${it.line}" }) { m ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // Query clear NAHI hoti: dusra result bina dobara
                                        // type kiye chuna ja sakta hai.
                                        viewModel.recordHistory()
                                        onOpenMatch(fileResult.file, m.line)
                                    }
                                    .padding(start = 32.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
                            ) {
                                Text(
                                    text = "${m.line}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 8.dp),
                                )
                                Text(
                                    text = highlightedSnippet(m),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        item(key = "div:${fileResult.file.path}") { HorizontalDivider() }
                    }
                    if (state.searching) {
                        item(key = "searching_footer") {
                            Text(
                                text = stringResource(R.string.search_searching),
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FileResultHeader(
    fileResult: CodeSearchEngine.FileResult,
    onLocateInTree: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { menuOpen = true }
            .padding(start = 16.dp, end = 8.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileTypeIcon(name = fileResult.file.name, isDirectory = false)
        Text(
            text = fileResult.relativePath,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${fileResult.matches.size}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(XIcons.MoreVert, contentDescription = stringResource(R.string.action_more))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_locate_in_tree)) },
                    onClick = {
                        menuOpen = false
                        onLocateInTree()
                    },
                )
            }
        }
    }
}

/** Snippet me match ranges soft highlight (plan: line highlight ke saath jump). */
@Composable
private fun highlightedSnippet(m: CodeSearchEngine.LineMatch): AnnotatedString =
    buildAnnotatedString {
        val hl = SpanStyle(background = MaterialTheme.colorScheme.tertiaryContainer)
        var cursor = 0
        m.ranges.forEach { range ->
            val start = range.first.coerceIn(0, m.text.length)
            val end = (range.last + 1).coerceIn(start, m.text.length)
            if (start > cursor) append(m.text.substring(cursor, start))
            pushStyle(hl)
            append(m.text.substring(start, end))
            pop()
            cursor = end
        }
        if (cursor < m.text.length) append(m.text.substring(cursor))
    }
