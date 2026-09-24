package com.invictus.xcode.feature.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.xcode.R
import com.invictus.xcode.XcodeApp
import com.invictus.xcode.core.editor.FileTooLargeException
import com.invictus.xcode.core.editor.TabBuffer
import com.invictus.xcode.core.editor.TextMateSupport
import com.invictus.xcode.core.editor.TextFileIo
import com.invictus.xcode.feature.workspace.UiText
import io.github.rosemoe.sora.text.Content
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    init {
        // Grammars load while the user is still browsing the tree; editors upgrade when ready.
        viewModelScope.launch { textMate.ensureLoaded() }
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

    fun buffer(path: String): TabBuffer? = buffers[path]

    fun onEvent(event: EditorEvent) {
        when (event) {
            is EditorEvent.Open -> open(event.file)
            is EditorEvent.Select -> if (event.path in buffers) {
                _uiState.update { it.copy(activePath = event.path) }
            }
            is EditorEvent.CloseTab -> requestClose(listOf(event.path))
            is EditorEvent.CloseOthers ->
                requestClose(_uiState.value.tabs.map { it.path }.filter { it != event.path })
            EditorEvent.CloseAll -> requestClose(_uiState.value.tabs.map { it.path })
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
    }

    /** The editor view hands back cursor and zoom when a tab's view is torn down. */
    fun onViewState(path: String, line: Int, column: Int, textSizePx: Float) {
        val buffer = buffers[path] ?: return
        buffer.cursorLine = line
        buffer.cursorColumn = column
        buffer.textSizePx = textSizePx
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
                        ),
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
                // Typed more while the write was running? Stay dirty.
                setDirty(path, buffer.isDirty)
            } else {
                allOk = false
                _messages.tryEmit(UiText(R.string.editor_err_save, listOf(buffer.file.name)))
            }
        }
        return allOk
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as XcodeApp
                EditorViewModel(app.container.textMate)
            }
        }
    }
}
