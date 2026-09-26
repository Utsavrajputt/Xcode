package com.invictus.xcode.feature.editor

import com.invictus.xcode.core.editor.ExternalChange
import com.invictus.xcode.core.preview.PreviewMode
import com.invictus.xcode.core.preview.PreviewType
import java.io.File

data class EditorTabUi(
    val path: String,
    val name: String,
    val dirty: Boolean,
    val isPinned: Boolean = false,
    /** Drives the "changed on disk" / "deleted on disk" banner for this tab, when active. */
    val externalChange: ExternalChange = ExternalChange.None,
    /** M5: NONE for anything but markdown/html (media never reaches the tab system at all). */
    val previewType: PreviewType = PreviewType.NONE,
    /** Markdown/html open straight into SPLIT; the app bar toggle cycles it from there. */
    val previewMode: PreviewMode = PreviewMode.EDITOR,
)

/** A close the user has to confirm because some of [paths] have unsaved edits. */
data class PendingClose(val paths: List<String>, val dirtyNames: List<String>)

data class EditorUiState(
    val tabs: List<EditorTabUi> = emptyList(),
    val activePath: String? = null,
    val loading: Boolean = false,
    val pendingClose: PendingClose? = null,
    /** Bumped on every real edit to the active tab -- the markdown/html preview's redraw signal. */
    val activeContentRevision: Long = 0,
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
    /** App bar's preview toggle: Editor -> Split -> Preview -> Editor, for markdown/html tabs. */
    data class CyclePreviewMode(val path: String) : EditorEvent
    /** Drag handle in split mode; ratio is the editor pane's share of the available height. */
    data class SetSplitRatio(val path: String, val ratio: Float) : EditorEvent
    /** HTML preview's one-time "Enable JavaScript?" dialog. */
    data class SetHtmlJsEnabled(val path: String, val enabled: Boolean) : EditorEvent
}
