package com.invictus.xcode.feature.workspace

import android.content.Context
import androidx.annotation.StringRes
import com.invictus.xcode.core.fs.OpenDecision
import java.io.File

/** A string resource + args, so ViewModels can talk to the UI without holding a Context. */
data class UiText(@StringRes val resId: Int, val args: List<Any> = emptyList()) {
    fun resolve(context: Context): String = context.getString(resId, *args.toTypedArray())
}

/** One line of the flattened, currently-visible tree. */
sealed interface TreeRow {
    val key: String

    data class Entry(
        val file: File,
        val depth: Int,
        val isDirectory: Boolean,
        val isExpanded: Boolean,
        val isLoading: Boolean,
        val isRoot: Boolean,
    ) : TreeRow {
        override val key: String get() = file.path
    }

    /** Placeholder shown under an expanded folder that has nothing (visible) in it. */
    data class Empty(val parentPath: String, val depth: Int) : TreeRow {
        override val key: String get() = "$parentPath/<empty>"
    }
}

sealed interface TreeDialog {
    data class Create(val parent: File, val isFolder: Boolean, val error: UiText? = null) : TreeDialog
    data class ConfirmDelete(val file: File, val isDirectory: Boolean) : TreeDialog
    data class OpenWarning(val file: File, val decision: OpenDecision) : TreeDialog
}

/** Inline rename in progress for [file]; [error] is shown under the field (name clash etc.). */
data class RenameState(val file: File, val error: UiText? = null)

data class Clipboard(val file: File, val isCut: Boolean)

data class FileTreeUiState(
    val root: File,
    /** Null means "the device's internal storage root": the UI shows a friendly label. */
    val rootName: String?,
    val rows: List<TreeRow> = emptyList(),
    val showHidden: Boolean = false,
    val showGitFolder: Boolean = false,
    val clipboard: Clipboard? = null,
    val renaming: RenameState? = null,
    val dialog: TreeDialog? = null,
    val rootError: UiText? = null,
)

sealed interface FileTreeEvent {
    data class RowClicked(val file: File, val isDirectory: Boolean) : FileTreeEvent
    data object Refresh : FileTreeEvent
    data object CollapseAll : FileTreeEvent
    data class SetShowHidden(val value: Boolean) : FileTreeEvent
    data class SetShowGitFolder(val value: Boolean) : FileTreeEvent

    data class StartCreate(val parent: File, val isFolder: Boolean) : FileTreeEvent
    data class ConfirmCreate(val name: String) : FileTreeEvent
    data object DismissDialog : FileTreeEvent

    data class StartRename(val file: File) : FileTreeEvent
    data class CommitRename(val newName: String) : FileTreeEvent
    data object CancelRename : FileTreeEvent

    data class RequestDelete(val file: File, val isDirectory: Boolean) : FileTreeEvent
    data object ConfirmDelete : FileTreeEvent

    data class Duplicate(val file: File) : FileTreeEvent
    data class Cut(val file: File) : FileTreeEvent
    data class Copy(val file: File) : FileTreeEvent
    data class PasteInto(val dir: File) : FileTreeEvent
    data object ClearClipboard : FileTreeEvent

    /** User accepted the binary / large-file warning. */
    data class ConfirmOpen(val file: File) : FileTreeEvent
}

/** One-shot things the screen must react to. */
sealed interface FileTreeEffect {
    data class Message(val text: UiText) : FileTreeEffect
    data class OpenFile(val file: File) : FileTreeEffect
}
