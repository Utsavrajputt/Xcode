package com.invictus.xcode.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.xcode.R
import com.invictus.xcode.XcodeApp
import com.invictus.xcode.core.editor.EditorSessionStore
import com.invictus.xcode.core.editor.EditorSettingsStore
import com.invictus.xcode.core.editor.EditorThemes
import com.invictus.xcode.core.editor.FileTooLargeException
import com.invictus.xcode.core.editor.SessionState
import com.invictus.xcode.core.editor.SessionTab
import com.invictus.xcode.core.editor.TabBuffer
import com.invictus.xcode.core.editor.TextMateSupport
import com.invictus.xcode.core.editor.TextFileIo
import com.invictus.xcode.feature.workspace.UiText
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Owns the open tabs. Lives at activity scope so tabs survive going back to the file tree.
 * Everything mutable is touched on the main thread; disk work runs on [io].
 */
class EditorViewModel(
    val textMate: TextMateSupport,
    private val sessionStore: EditorSessionStore,
    private val settingsStore: EditorSettingsStore,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    /** Selected editor colour theme; [EditorThemes.SYSTEM_DEFAULT] follows the app light/dark. */
    val themeId: StateFlow<String> = settingsStore.themeId
        .stateIn(viewModelScope, SharingStarted.Eagerly, EditorThemes.SYSTEM_DEFAULT)

    fun setTheme(id: String) {
        viewModelScope.launch { settingsStore.setThemeId(id) }
    }

    /** Plan 3.2 "customizable symbol bar" -- ordered quick-insert symbols shown above the keyboard. */
    val symbolBar: StateFlow<List<String>> = settingsStore.symbolBar
        .stateIn(viewModelScope, SharingStarted.Eagerly, EditorSettingsStore.DEFAULT_SYMBOLS)

    fun setSymbolBar(symbols: List<String>) {
        viewModelScope.launch { settingsStore.setSymbolBar(symbols) }
    }

    /** Last font size the user zoomed to anywhere, in px; 0f = editor's own built-in default. */
    private var defaultFontSizePx: Float = 0f

    init {
        // Grammars load while the user is still browsing the tree; editors upgrade when ready.
        viewModelScope.launch { textMate.ensureLoaded() }
        viewModelScope.launch { settingsStore.fontSizePx.collect { defaultFontSizePx = it } }
    }

    private val buffers = LinkedHashMap<String, TabBuffer>()
    private val loading = HashSet<String>()

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    /** Errors to show wherever the user currently is (file tree or editor). */
    private val _messages = MutableSharedFlow<UiText>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiText> = _messages.asSharedFlow()

    /** Fired once a file is ready, so navigation can bring the editor screen forward. */
    private val _showEditor = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showEditor: SharedFlow<Unit> = _showEditor.asSharedFlow()

    /** The workspace this session belongs to; set once by [onProjectOpened]. */
    private var projectPath: String? = null
    private var restoredForProject: String? = null
    private var persistJob: Job? = null

    fun buffer(path: String): TabBuffer? = buffers[path]

    /**
     * Called when the workspace root becomes known (once at cold start, and again on every
     * project switch). Only restores a session the first time a given project is seen with no
     * tabs already open -- it never clobbers tabs the user already opened this run.
     */
    fun onProjectOpened(path: String) {
        if (projectPath == path) return
        projectPath = path
        if (restoredForProject == path || buffers.isNotEmpty()) return
        restoredForProject = path
        loading.add(SESSION_LOAD_KEY)
        _uiState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val session = withContext(io) { sessionStore.load(path) }
            restoreSession(session)
            loading.remove(SESSION_LOAD_KEY)
            _uiState.update { it.copy(loading = loading.isNotEmpty()) }
        }
    }

    fun onEvent(event: EditorEvent) {
        when (event) {
            is EditorEvent.Open -> open(event.file)
            is EditorEvent.Select -> if (event.path in buffers) {
                _uiState.update { it.copy(activePath = event.path) }
                schedulePersist()
            }
            is EditorEvent.CloseTab -> requestClose(listOf(event.path))
            is EditorEvent.CloseOthers -> requestClose(
                _uiState.value.tabs.filter { it.path != event.path && !it.isPinned }.map { it.path },
            )
            EditorEvent.CloseAll -> requestClose(_uiState.value.tabs.filter { !it.isPinned }.map { it.path })
            is EditorEvent.Reorder -> reorder(event.from, event.to)
            is EditorEvent.TogglePin -> togglePin(event.path)
            EditorEvent.SaveActive -> _uiState.value.activePath?.let { save(listOf(it)) }
            EditorEvent.SaveAll -> save(buffers.keys.filter(::isDirty))
            EditorEvent.ConfirmSaveAndClose -> confirmSaveAndClose()
            EditorEvent.ConfirmDiscardAndClose -> {
                val pending = _uiState.value.pendingClose ?: return
                _uiState.update { it.copy(pendingClose = null) }
                closeNow(pending.paths)
            }
            EditorEvent.DismissPendingClose -> _uiState.update { it.copy(pendingClose = null) }
        }
    }

    /** The editor view reports every real edit (not the initial text load). */
    fun onEdited(path: String) {
        val buffer = buffers[path] ?: return
        buffer.revision++
        val tab = _uiState.value.tabs.firstOrNull { it.path == path }
        if (tab != null && !tab.dirty) setDirty(path, true)
        schedulePersist()
    }

    /** The editor view hands back cursor, zoom and scroll when a tab's view is torn down. */
    fun onViewState(path: String, line: Int, column: Int, textSizePx: Float, scrollX: Int, scrollY: Int) {
        val buffer = buffers[path] ?: return
        buffer.cursorLine = line
        buffer.cursorColumn = column
        buffer.scrollX = scrollX
        buffer.scrollY = scrollY
        // Last size zoomed to anywhere becomes the default for the *next* newly opened tab.
        if (textSizePx > 0f && textSizePx != buffer.textSizePx) {
            buffer.textSizePx = textSizePx
            defaultFontSizePx = textSizePx
            viewModelScope.launch { settingsStore.setFontSizePx(textSizePx) }
        }
        schedulePersist()
    }

    private fun open(file: File) {
        val path = file.path
        if (path in buffers) {
            _uiState.update { it.copy(activePath = path) }
            _showEditor.tryEmit(Unit)
            return
        }
        if (!loading.add(path)) return
        _uiState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result: Result<TabBuffer> = withContext(io) {
                try {
                    val loaded = TextFileIo.read(file)
                    Result.success(
                        TabBuffer(
                            file = file,
                            content = Content(loaded.text),
                            charset = loaded.charset,
                            hasBom = loaded.hasBom,
                            lineEnding = loaded.lineEnding,
                        ).apply {
                            diskContentHash = EditorSessionStore.hashOf(loaded.text)
                            if (defaultFontSizePx > 0f) textSizePx = defaultFontSizePx
                        },
                    )
                } catch (e: IOException) {
                    Result.failure(e)
                } catch (e: OutOfMemoryError) {
                    Result.failure(FileTooLargeException(file.length()))
                }
            }
            loading.remove(path)
            _uiState.update { it.copy(loading = loading.isNotEmpty()) }
            result.fold(
                onSuccess = { buffer ->
                    buffers[path] = buffer
                    _uiState.update {
                        it.copy(
                            tabs = it.tabs + EditorTabUi(path, file.name, dirty = false),
                            activePath = path,
                        )
                    }
                    _showEditor.tryEmit(Unit)
                    schedulePersist()
                },
                onFailure = { error ->
                    val text = if (error is FileTooLargeException) {
                        UiText(R.string.editor_err_too_large, listOf(file.name))
                    } else {
                        UiText(R.string.editor_err_read, listOf(file.name))
                    }
                    _messages.tryEmit(text)
                },
            )
        }
    }

    private fun isDirty(path: String): Boolean = buffers[path]?.isDirty == true

    private fun setDirty(path: String, dirty: Boolean) {
        _uiState.update { state ->
            state.copy(tabs = state.tabs.map { if (it.path == path) it.copy(dirty = dirty) else it })
        }
    }

    private fun togglePin(path: String) {
        val buffer = buffers[path] ?: return
        buffer.isPinned = !buffer.isPinned
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs.map { if (it.path == path) it.copy(isPinned = buffer.isPinned) else it },
            )
        }
        schedulePersist()
    }

    private fun reorder(from: Int, to: Int) {
        val tabs = _uiState.value.tabs
        if (from !in tabs.indices || to !in tabs.indices || from == to) return
        val mutable = tabs.toMutableList()
        val moved = mutable.removeAt(from)
        mutable.add(to, moved)
        _uiState.update { it.copy(tabs = mutable) }
        schedulePersist()
    }

    private fun requestClose(paths: List<String>) {
        if (paths.isEmpty()) return
        val dirty = paths.filter(::isDirty)
        if (dirty.isEmpty()) {
            closeNow(paths)
        } else {
            val names = dirty.map { buffers[it]?.file?.name ?: it }
            _uiState.update { it.copy(pendingClose = PendingClose(paths, names)) }
        }
    }

    private fun confirmSaveAndClose() {
        val pending = _uiState.value.pendingClose ?: return
        _uiState.update { it.copy(pendingClose = null) }
        viewModelScope.launch {
            // If any save fails the tabs stay open, so nothing is lost.
            if (saveBuffers(pending.paths.filter(::isDirty))) closeNow(pending.paths)
        }
    }

    private fun closeNow(paths: Collection<String>) {
        val closing = paths.toSet()
        val state = _uiState.value
        var active = state.activePath
        if (active != null && active in closing) {
            // Land on the tab to the right, else the nearest one to the left.
            val index = state.tabs.indexOfFirst { it.path == active }
            active = state.tabs.drop(index + 1).firstOrNull { it.path !in closing }?.path
                ?: state.tabs.take(index).lastOrNull { it.path !in closing }?.path
        }
        closing.forEach { buffers.remove(it) }
        _uiState.update { it.copy(tabs = it.tabs.filter { tab -> tab.path !in closing }, activePath = active) }
        schedulePersist()
    }

    private fun save(paths: List<String>) {
        if (paths.isEmpty()) return
        viewModelScope.launch { saveBuffers(paths) }
    }

    /** Saves each buffer; returns true only if every one succeeded. */
    private suspend fun saveBuffers(paths: List<String>): Boolean {
        var allOk = true
        for (path in paths) {
            val buffer = buffers[path] ?: continue
            // Snapshot on the main thread (the editor mutates Content there), write on IO.
            val text = buffer.content.toString()
            val revision = buffer.revision
            val ok = withContext(io) {
                try {
                    TextFileIo.write(buffer.file, text, buffer.charset, buffer.hasBom, buffer.lineEnding)
                    true
                } catch (_: IOException) {
                    false
                }
            }
            if (ok) {
                buffer.savedRevision = revision
                buffer.diskContentHash = EditorSessionStore.hashOf(text)
                // Typed more while the write was running? Stay dirty.
                setDirty(path, buffer.isDirty)
            } else {
                allOk = false
                _messages.tryEmit(UiText(R.string.editor_err_save, listOf(buffer.file.name)))
            }
        }
        if (allOk) schedulePersist()
        return allOk
    }

    /** Debounced so typing doesn't write JSON to disk on every keystroke. */
    private fun schedulePersist() {
        val path = projectPath ?: return
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(PERSIST_DEBOUNCE_MS)
            val state = buildSessionState(path)
            withContext(io) { sessionStore.save(state) }
        }
    }

    private fun buildSessionState(projectPath: String): SessionState {
        val ui = _uiState.value
        val tabs = ui.tabs.mapNotNull { tabUi ->
            val buffer = buffers[tabUi.path] ?: return@mapNotNull null
            SessionTab(
                path = tabUi.path,
                name = tabUi.name,
                isPinned = buffer.isPinned,
                cursorLine = buffer.cursorLine,
                cursorColumn = buffer.cursorColumn,
                scrollX = buffer.scrollX,
                scrollY = buffer.scrollY,
                contentHash = buffer.diskContentHash,
                pendingEditSnapshot = if (buffer.isDirty) buffer.content.toString() else null,
            )
        }
        return SessionState(projectPath = projectPath, activePath = ui.activePath, tabs = tabs)
    }

    private suspend fun restoreSession(session: SessionState?) {
        if (session == null || session.tabs.isEmpty()) return
        val restoredTabs = mutableListOf<EditorTabUi>()
        var droppedUnsavedEdits = 0
        for (saved in session.tabs) {
            val file = File(saved.path)
            val loaded = withContext(io) {
                if (!file.exists() || !file.isFile) null else try {
                    TextFileIo.read(file)
                } catch (_: IOException) {
                    null
                }
            } ?: continue // File gone or unreadable: just drop this tab, same as a closed one.

            val currentHash = EditorSessionStore.hashOf(loaded.text)
            val trustSnapshot = saved.pendingEditSnapshot != null && currentHash == saved.contentHash
            if (saved.pendingEditSnapshot != null && !trustSnapshot) droppedUnsavedEdits++

            val text = if (trustSnapshot) saved.pendingEditSnapshot!! else loaded.text
            val buffer = TabBuffer(
                file = file,
                content = Content(text),
                charset = loaded.charset,
                hasBom = loaded.hasBom,
                lineEnding = loaded.lineEnding,
            ).apply {
                isPinned = saved.isPinned
                if (defaultFontSizePx > 0f) textSizePx = defaultFontSizePx
                cursorLine = saved.cursorLine
                cursorColumn = saved.cursorColumn
                scrollX = saved.scrollX
                scrollY = saved.scrollY
                diskContentHash = if (trustSnapshot) saved.contentHash else currentHash
                if (trustSnapshot) revision = 1 // savedRevision stays 0 -> dirty
            }
            buffers[saved.path] = buffer
            restoredTabs += EditorTabUi(saved.path, saved.name, dirty = trustSnapshot, isPinned = saved.isPinned)
        }
        if (restoredTabs.isEmpty()) return
        val active = session.activePath?.takeIf { p -> restoredTabs.any { it.path == p } } ?: restoredTabs.first().path
        _uiState.update { it.copy(tabs = restoredTabs, activePath = active) }
        if (droppedUnsavedEdits > 0) {
            _messages.tryEmit(UiText(R.string.editor_session_edits_stale, listOf(droppedUnsavedEdits)))
        }
        _showEditor.tryEmit(Unit)
    }

    companion object {
        private const val SESSION_LOAD_KEY = "__session__"
        private const val PERSIST_DEBOUNCE_MS = 600L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as XcodeApp
                EditorViewModel(app.container.textMate, app.container.editorSessionStore, app.container.editorSettingsStore)
            }
        }
    }
}
