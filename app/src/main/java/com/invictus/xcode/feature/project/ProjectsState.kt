package com.invictus.xcode.feature.project

import com.invictus.xcode.core.editor.ShortcutPref
import com.invictus.xcode.feature.workspace.UiText
import java.io.File

/** One row of the "Recent" list. [exists] is checked off the main thread. */
data class RecentItem(
    val file: File,
    val exists: Boolean,
    val isDeviceStorage: Boolean,
)

/** A starting point in the sheet's folder browser. */
data class BrowseShortcut(val file: File, val label: UiText?)

/** A Browse shortcut plus its edit-mode flags: [enabled] = shown in Browse, [isCustom] = added by the user. */
data class ShortcutEntry(val shortcut: BrowseShortcut, val enabled: Boolean, val isCustom: Boolean)

data class RenameProject(val file: File, val error: UiText? = null)

data class ProjectsUiState(
    val recents: List<RecentItem> = emptyList(),
    val shortcuts: List<BrowseShortcut> = emptyList(),
    /** Every shortcut in the user's order, including hidden ones -- what the Browse edit dialog shows. */
    val shortcutEntries: List<ShortcutEntry> = emptyList(),
    /** Null = showing [shortcuts]; otherwise the folder being browsed. */
    val browseDir: File? = null,
    val browseEntries: List<File> = emptyList(),
    val browseError: UiText? = null,
    val renaming: RenameProject? = null,
    /** Project paths with a backup running right now. */
    val backingUp: Set<String> = emptySet(),
    /** Whether the folder browser also lists dot-prefixed (hidden) folders. */
    val showHidden: Boolean = false,
)

sealed interface ProjectsEvent {
    /** Sheet became visible: re-check which recents still exist and reset the browser. */
    data object SheetOpened : ProjectsEvent
    data class Browse(val dir: File?) : ProjectsEvent
    data object BrowseUp : ProjectsEvent
    data class RemoveRecent(val path: String) : ProjectsEvent
    data class StartRename(val file: File) : ProjectsEvent
    data class ConfirmRename(val newName: String) : ProjectsEvent
    data object DismissRename : ProjectsEvent
    data class Backup(val file: File) : ProjectsEvent
    data object ToggleShowHidden : ProjectsEvent
    data class SaveShortcuts(val prefs: List<ShortcutPref>) : ProjectsEvent
}

sealed interface ProjectsEffect {
    data class Message(val text: UiText) : ProjectsEffect

    /** A project folder was renamed on disk; the workspace follows it if it was open. */
    data class ProjectMoved(val old: File, val new: File) : ProjectsEffect
}
