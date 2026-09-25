package com.invictus.xcode.feature.editor

import java.io.File

data class EditorTabUi(val path: String, val name: String, val dirty: Boolean, val isPinned: Boolean = false)

/** A close the user has to confirm because some of [paths] have unsaved edits. */
data class PendingClose(val paths: List<String>, val dirtyNames: List<String>)

data class EditorUiState(
    val tabs: List<EditorTabUi> = emptyList(),
    val activePath: String? = null,
    val loading: Boolean = false,
    val pendingClose: PendingClose? = null,
)

sealed interface EditorEvent {
    data class Open(val file: File) : EditorEvent
    data class Select(val path: String) : EditorEvent
    data class CloseTab(val path: String) : EditorEvent
    data class CloseOthers(val path: String) : EditorEvent
    data object CloseAll : EditorEvent
    /** Drag-reorder in the tab row: the tab at [from] is moved to sit at [to]. */
    data class Reorder(val from: Int, val to: Int) : EditorEvent
    data class TogglePin(val path: String) : EditorEvent
    data object SaveActive : EditorEvent
    data object SaveAll : EditorEvent
    data object ConfirmSaveAndClose : EditorEvent
    data object ConfirmDiscardAndClose : EditorEvent
    data object DismissPendingClose : EditorEvent
}
