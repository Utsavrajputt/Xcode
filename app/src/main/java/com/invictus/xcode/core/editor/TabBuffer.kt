package com.invictus.xcode.core.editor

import io.github.rosemoe.sora.text.Content
import java.io.File
import java.nio.charset.Charset

/** What, if anything, a tab's on-disk file did behind the app's back. */
enum class ExternalChange {
    None,
    Modified,
    Deleted,
}

/**
 * Everything the editor needs to bring one tab back exactly as the user left it.
 *
 * [content] is Sora's own text object and it owns the undo/redo stack, so handing the same
 * instance to a fresh editor view on tab switch keeps undo history for free.
 *
 * Main thread only, except construction.
 */
class TabBuffer(
    val file: File,
    val content: Content,
    val charset: Charset,
    val hasBom: Boolean,
    val lineEnding: LineEnding,
) {
    /** Bumped on every edit; a tab is dirty while it differs from [savedRevision]. */
    var revision: Long = 0
    var savedRevision: Long = 0

    val isDirty: Boolean get() = revision != savedRevision

    var cursorLine: Int = 0
    var cursorColumn: Int = 0

    /** 0 = editor default. Kept so a pinch-zoomed tab stays zoomed. */
    var textSizePx: Float = 0f

    /** Virtual scroll offset in pixels, restored on tab switch alongside cursor + zoom. */
    var scrollX: Int = 0
    var scrollY: Int = 0

    /** Survives close-all/close-others and is placed first in the tab row. */
    var isPinned: Boolean = false

    /** Hash of the file's content as far as this app instance knows it (set on open + save). */
    var diskContentHash: String = ""

    /** mtime as far as this app instance knows it (set on open + save); resume fallback check. */
    var lastKnownDiskModified: Long = 0L

    /** Set once a background watcher or resume check has seen the file differ; drives the banner. */
    var externalChange: ExternalChange = ExternalChange.None
}
