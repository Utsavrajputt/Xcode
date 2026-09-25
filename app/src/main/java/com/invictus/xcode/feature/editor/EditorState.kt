package com.invictus.xcode.feature.editor

import com.invictus.xcode.core.editor.ExternalChange
import java.io.File

data class EditorTabUi(
    val path: String,
    val name: String,
    val dirty: Boolean,
    val isPinned: Boolean = false,
    /** Drives the "changed on disk" / "deleted on disk" banner for this tab, when active. */
    val externalChange: ExternalChange = ExternalChange.None,
)

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
    /** Banner "Reload": discard the buffer's in-memory edits and load the file fresh from disk. */
    data class ReloadFromDisk(val path: String) : EditorEvent
    /** Banner "Keep my edits": dismiss the banner, buffer stays as-is (still dirty vs disk). */
    data class KeepMyEdits(val path: String) : EditorEvent
    /** Deleted-on-disk banner "Close tab": drop the tab, nothing to save. */
    data class CloseDeletedTab(val path: String) : EditorEvent
    /** Deleted-on-disk banner "Keep as new file": clears the missing state, next save recreates it. */
    data class KeepAsNewFile(val path: String) : EditorEvent
}
