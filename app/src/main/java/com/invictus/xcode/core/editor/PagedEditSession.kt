package com.invictus.xcode.core.editor

import java.util.concurrent.ConcurrentHashMap

/**
 * Plan section 7 "Paged large file (solved pattern)": a file above [TextFileIo.MAX_BYTES] is
 * split into fixed-size pages up front, and the editor only ever holds one page's text in a
 * Sora `Content` at a time (see [TabBuffer.goToPage]).
 *
 * [originalPages] is the on-disk baseline, split once at load. [modifiedPages] holds only the
 * pages the user actually changed -- a page absent from it is still exactly its original text.
 * [mergedText] stitches both back into one string for [TextFileIo.write]; [markSaved] folds a
 * successful save's edits back into the baseline.
 *
 * Each page gets a fresh `Content` when loaded, so undo/redo history is per-page and resets on
 * page switch -- unifying undo across pages would mean keeping every page's editor state alive
 * at once, which defeats the point of paging in the first place.
 *
 * Main thread only (mirrors [TabBuffer]'s own threading contract); [modifiedPages] is a
 * [ConcurrentHashMap] only so a future background pre-fetch of neighboring pages can write to
 * it safely, not because it's touched off the main thread today.
 */
class PagedEditSession(
    originalText: String,
    pageSizeChars: Int = DEFAULT_PAGE_SIZE_CHARS,
) {
    private val originalPages: MutableList<String> = split(originalText, pageSizeChars).toMutableList()
    private val modifiedPages = ConcurrentHashMap<Int, String>()
    private val viewStates = HashMap<Int, PageViewState>()

    val pageCount: Int get() = originalPages.size

    var currentPageIndex: Int = 0
        private set

    /** Cursor/scroll a page was left at, restored if the user comes back to it this session. */
    data class PageViewState(
        val cursorLine: Int = 0,
        val cursorColumn: Int = 0,
        val scrollX: Int = 0,
        val scrollY: Int = 0,
    )

    fun textForPage(index: Int): String = modifiedPages[index] ?: originalPages[index]

    fun viewStateForPage(index: Int): PageViewState = viewStates[index] ?: PageViewState()

    fun isPageDirty(index: Int): Boolean = modifiedPages[index]?.let { it != originalPages.getOrNull(index) } == true

    /** True while any page differs from its on-disk baseline. */
    val isDirty: Boolean get() = modifiedPages.isNotEmpty() && modifiedPages.keys.any(::isPageDirty)

    /** Called right before leaving [index] for another page: records its live text + cursor. */
    fun commitPage(index: Int, text: String, viewState: PageViewState) {
        if (index !in originalPages.indices) return
        if (text == originalPages[index]) modifiedPages.remove(index) else modifiedPages[index] = text
        viewStates[index] = viewState
    }

    /** Same as [commitPage] but for the save path, which isn't switching pages or view state. */
    fun commitCurrentPageText(text: String) {
        val index = currentPageIndex
        if (index !in originalPages.indices) return
        if (text == originalPages[index]) modifiedPages.remove(index) else modifiedPages[index] = text
    }

    fun selectPage(index: Int) {
        currentPageIndex = index.coerceIn(0, pageCount - 1)
    }

    /** Whole-file text for [TextFileIo.write]: original pages untouched, edited ones swapped in. */
    fun mergedText(): String = buildString {
        for (i in 0 until pageCount) append(textForPage(i))
    }

    /** After a successful save: every edited page's baseline becomes what's now on disk. */
    fun markSaved() {
        modifiedPages.forEach { (index, text) -> originalPages[index] = text }
        modifiedPages.clear()
    }

    companion object {
        const val DEFAULT_PAGE_SIZE_CHARS = 460_000
        const val MIN_PAGE_SIZE_CHARS = 16

        fun split(text: String, pageSizeChars: Int): List<String> {
            val size = pageSizeChars.coerceAtLeast(MIN_PAGE_SIZE_CHARS)
            if (text.isEmpty()) return listOf("")
            val pages = ArrayList<String>(text.length / size + 1)
            var i = 0
            while (i < text.length) {
                val end = (i + size).coerceAtMost(text.length)
                pages += text.substring(i, end)
                i = end
            }
            return pages
        }
    }
}
