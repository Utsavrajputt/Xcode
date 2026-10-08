package com.invictus.xcode.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
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
import com.invictus.xcode.core.editor.EditorSettingsStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Plan 3.6: code search (content, poora workspace) — full screen state. */
class CodeSearchViewModel(
    private val root: File,
    private val engine: CodeSearchEngine,
    private val history: SearchHistoryStore,
    private val settingsStore: EditorSettingsStore,
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

    /** Files dismissed with the X on their card; cleared on every new query / option change. */
    private val dismissedPaths = mutableSetOf<String>()

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
        dismissedPaths.clear()
        _uiState.update { it.copy(query = query) }
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update {
                it.copy(results = emptyList(), fileCount = 0, matchCount = 0, searching = false, error = null)
            }
            return
        }
        val parsed = parseSearchQuery(query)
        val text = parsed.text
        if (_uiState.value.regex) {
            try {
                Regex(text)
                _uiState.update { it.copy(error = null) }
            } catch (e: PatternSyntaxException) {
                _uiState.update { it.copy(error = e.description ?: query, searching = false) }
                return
            }
        }
        val base = CodeSearchEngine.Options(
            regex = _uiState.value.regex,
            caseSensitive = _uiState.value.caseSensitive,
            wholeWord = _uiState.value.wholeWord,
            includeGlob = _uiState.value.includeGlob,
            excludeGlob = _uiState.value.excludeGlob,
            fileNameFilter = parsed.fileName,
        )
        searchJob = viewModelScope.launch {
            delay(300)
            val extra = settingsStore.searchExtraExcludes.first()
            val options = base.copy(
                defaultValuesOnly = settingsStore.searchDefaultStringsOnly.first(),
                maxFileSizeBytes = settingsStore.searchMaxFileMb.first() * 1024L * 1024L,
                excludeGlob = listOf(base.excludeGlob, extra).filter { it.isNotBlank() }.joinToString(","),
            )
            _uiState.update { it.copy(searching = true) }
            val found = mutableListOf<CodeSearchEngine.FileResult>()
            // Stream: results dheere dheere dikhte rahen; naya query -> collect cancel.
            engine.search(root, text, options).collect { fr ->
                found.add(fr)
                publishVisible(found)
            }
            _uiState.update { it.copy(searching = false) }
        }
    }

    /** Everything the engine found so far, minus dismissed files; drives the list and the summary counts. */
    private var allFound: List<CodeSearchEngine.FileResult> = emptyList()

    private fun publishVisible(found: List<CodeSearchEngine.FileResult>) {
        allFound = found.sortedBy { it.relativePath }
        val visible = allFound.filter { it.file.path !in dismissedPaths }
        _uiState.update {
            it.copy(results = visible, fileCount = visible.size, matchCount = visible.sumOf { r -> r.matches.size })
        }
    }

    /** X on a file card: drop that file's matches from the current results only. */
    fun dismissFile(file: File) {
        dismissedPaths.add(file.path)
        publishVisible(allFound)
    }

    fun setRegex(v: Boolean) = applyOption { it.copy(regex = v) }
    fun setCase(v: Boolean) = applyOption { it.copy(caseSensitive = v) }
    fun setWholeWord(v: Boolean) = applyOption { it.copy(wholeWord = v) }
    fun setInclude(v: String) = applyOption { it.copy(includeGlob = v) }
    fun setExclude(v: String) = applyOption { it.copy(excludeGlob = v) }

    /** 3-dot on a file card: add that file's relative path to the exclude filter and re-run. */
    fun excludeFile(relativePath: String) {
        val current = _uiState.value.excludeGlob
        val entries = current.split(',', '\n').map { it.trim().removePrefix("!") }.filter { it.isNotEmpty() }
        if (relativePath in entries) return
        setExclude(if (current.isBlank()) relativePath else current.trimEnd().trimEnd(',') + "," + relativePath)
    }

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

    fun clearHistory() {
        viewModelScope.launch { history.clear(SearchKind.CODE) }
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
                    settingsStore = app.container.editorSettingsStore,
                )
            }
        }

        @Composable
        fun get(projectPath: String, owner: androidx.lifecycle.ViewModelStoreOwner): CodeSearchViewModel {
            // FileSearchViewModel jaisa hi bug: owner ke default extras (jisme APPLICATION_KEY
            // hota hai) copy kiye bina empty MutableCreationExtras banane se factory() me
            // `this[APPLICATION_KEY] as XcodeApp` null milta hai aur crash hota hai.
            val defaultExtras = (owner as? androidx.lifecycle.HasDefaultViewModelProviderFactory)
                ?.defaultViewModelCreationExtras
            val extras = MutableCreationExtras(defaultExtras ?: CreationExtras.Empty).apply {
                set(RootKey, File(projectPath))
            }
            return androidx.lifecycle.viewmodel.compose.viewModel(
                owner,
                key = "code_search_$projectPath",
                factory = factory(File(projectPath)),
                extras = extras,
            )
        }
    }
}

@Composable
private fun FilterField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(XIcons.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeSearchScreen(
    projectPath: String,
    onBack: () -> Unit,
    onOpenMatch: (file: File, line: Int, column: Int, length: Int) -> Unit,
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
            // M3 Expressive: pill-shaped, tonal, floating search field (FileSearchOverlay jaisa).
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(56.dp),
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
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.search_code_hint)) },
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

            // Options row: toggles on the left, "more" on the right reveals include/exclude.
            var showFilters by rememberSaveable { mutableStateOf(false) }
            val filtersActive = state.includeGlob.isNotBlank() || state.excludeGlob.isNotBlank()
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = state.regex,
                    onClick = { viewModel.setRegex(!state.regex) },
                    label = { Text(stringResource(R.string.search_regex)) },
                    shape = RoundedCornerShape(50),
                )
                FilterChip(
                    selected = state.caseSensitive,
                    onClick = { viewModel.setCase(!state.caseSensitive) },
                    label = { Text(stringResource(R.string.search_case_sensitive)) },
                    shape = RoundedCornerShape(50),
                )
                FilterChip(
                    selected = state.wholeWord,
                    onClick = { viewModel.setWholeWord(!state.wholeWord) },
                    label = { Text(stringResource(R.string.search_whole_word)) },
                    shape = RoundedCornerShape(50),
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showFilters = !showFilters }) {
                    BadgedBox(badge = { if (filtersActive) Badge() }) {
                        Icon(
                            XIcons.MoreVert,
                            contentDescription = stringResource(R.string.search_filters),
                            tint = if (showFilters || filtersActive) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
            AnimatedVisibility(
                visible = showFilters,
                enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut(),
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FilterField(
                            value = state.includeGlob,
                            onValueChange = viewModel::setInclude,
                            label = stringResource(R.string.search_include_glob),
                        )
                        FilterField(
                            value = state.excludeGlob,
                            onValueChange = viewModel::setExclude,
                            label = stringResource(R.string.search_exclude_glob),
                        )
                        if (filtersActive) {
                            TextButton(
                                onClick = {
                                    viewModel.setInclude("")
                                    viewModel.setExclude("")
                                },
                                modifier = Modifier.align(Alignment.End),
                            ) { Text(stringResource(R.string.search_clear_filters)) }
                        }
                    }
                }
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

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.query.isBlank()) {
                    item(key = "history_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
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
                                    .clickable { viewModel.runFromHistory(entry) }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(XIcons.Search, contentDescription = null)
                                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(entry.query, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    val summary = historyOptionsSummary(entry.optionsJson)
                                    if (summary.isNotEmpty()) {
                                        Text(
                                            text = summary,
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
                    }
                } else {
                    state.results.forEach { fileResult ->
                        item(key = "group:${fileResult.file.path}") {
                            // Har file ka group ek rounded tonal card me — divider ki zaroorat
                            // nahi, card khud visual separation de deta hai.
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column {
                                    FileResultHeader(
                                        fileResult = fileResult,
                                        onLocateInTree = { onLocateInTree(fileResult.file) },
                                        onDismiss = { viewModel.dismissFile(fileResult.file) },
                                        onExclude = { viewModel.excludeFile(fileResult.relativePath) },
                                    )
                                    fileResult.matches.forEach { m ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    // Query clear NAHI hoti: dusra result bina dobara
                                                    // type kiye chuna ja sakta hai.
                                                    viewModel.recordHistory()
                                                    val first = m.ranges.firstOrNull()
                                                    onOpenMatch(fileResult.file, m.line, first?.first ?: -1, first?.let { it.last - it.first + 1 } ?: 0)
                                                }
                                                .padding(start = 32.dp, end = 16.dp, top = 3.dp, bottom = 3.dp),
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
                                    Spacer(Modifier.height(4.dp))
                                }
                            }
                        }
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
    onDismiss: () -> Unit,
    onExclude: () -> Unit,
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
        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            Text(
                text = fileResult.file.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FolderPathSubtitle(relativePath = fileResult.relativePath)
        }
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
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_exclude_file)) },
                    onClick = {
                        menuOpen = false
                        onExclude()
                    },
                )
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(XIcons.Close, contentDescription = stringResource(R.string.search_dismiss_file))
        }
    }
}

/**
 * Small grey folder path under the file name. The full path is shown; when it is wider than the
 * row it glides to the end, pauses, glides back to the start and repeats, so every folder name can
 * be read without any truncation. A path that fits just stays still.
 */
@Composable
private fun FolderPathSubtitle(relativePath: String) {
    val text = remember(relativePath) {
        relativePath.substringBeforeLast('/', "").ifEmpty { "/" }
    }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val maxScroll = scroll.maxValue
    LaunchedEffect(text, maxScroll) {
        if (maxScroll <= 0) return@LaunchedEffect
        // ~36 dp per second keeps the text readable while it moves.
        val millis = ((maxScroll / density.density) / 36f * 1000f).toInt().coerceIn(600, 12_000)
        while (true) {
            delay(1200)
            scroll.animateScrollTo(maxScroll, tween(millis, easing = LinearEasing))
            delay(900)
            scroll.animateScrollTo(0, tween(millis, easing = LinearEasing))
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll, enabled = false),
    )
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

/** "Regex · Aa · Word · *.kt · !build/" -- human readable snapshot of a saved code-search history entry. */
@Composable
private fun historyOptionsSummary(optionsJson: String): String {
    val o = runCatching { JSONObject(optionsJson) }.getOrNull() ?: return ""
    val regex = stringResource(R.string.search_regex)
    val case = stringResource(R.string.search_case_sensitive)
    val word = stringResource(R.string.search_whole_word)
    return buildList {
        if (o.optBoolean("regex")) add(regex)
        if (o.optBoolean("case")) add(case)
        if (o.optBoolean("word")) add(word)
        o.optString("include").takeIf { it.isNotBlank() }?.let { add(it) }
        o.optString("exclude").takeIf { it.isNotBlank() }?.let { add("!$it") }
    }.joinToString(" · ")
}

/** "text//settings" = search "text" only in files named like "settings". */
internal data class ParsedSearchQuery(val text: String, val fileName: String)

private val FILE_FILTER_RE = Regex("""^(.+?)(?<!:)//([^\s/]+)$""")

internal fun parseSearchQuery(query: String): ParsedSearchQuery {
    val m = FILE_FILTER_RE.find(query)
    if (m == null || m.groupValues[1].isBlank()) return ParsedSearchQuery(query, "")
    return ParsedSearchQuery(m.groupValues[1], m.groupValues[2])
}
