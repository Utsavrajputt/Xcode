package com.invictus.xcode.feature.editor

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.invictus.xcode.core.editor.EditorThemes
import com.invictus.xcode.core.editor.TabBuffer
import com.invictus.xcode.core.editor.TextMateSupport
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import io.github.rosemoe.sora.widget.schemes.SchemeDarcula

/** Lets the top bar reach the live editor for undo/redo without owning the view. */
class EditorHandle {
    var editor: CodeEditor? = null
}

private const val DEFAULT_TEXT_SIZE_SP = 14f

/**
 * Sora's [CodeEditor] inside Compose. One view per tab: callers wrap this in `key(path)` so a
 * tab switch builds a fresh view around that tab's own [TabBuffer.content] (which carries the
 * undo stack) and puts the cursor and zoom back.
 */
@Composable
fun CodeEditorView(
    buffer: TabBuffer,
    darkTheme: Boolean,
    textMate: TextMateSupport,
    highlightReady: Boolean,
    themeId: String = EditorThemes.SYSTEM_DEFAULT,
    handle: EditorHandle,
    onEdited: () -> Unit,
    onViewState: (line: Int, column: Int, textSizePx: Float, scrollX: Int, scrollY: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnEdited by rememberUpdatedState(onEdited)
    val currentOnViewState by rememberUpdatedState(onViewState)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            CodeEditor(context).apply {
                typefaceText = Typeface.MONOSPACE
                setTextSize(DEFAULT_TEXT_SIZE_SP)
                if (buffer.textSizePx > 0f) textSizePx = buffer.textSizePx
                applyLook(this, buffer, textMate, darkTheme, highlightReady, themeId)

                // Same Content object as last time, so undo/redo history comes along.
                setText(buffer.content)

                subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                    // Loading text into the view is not an edit.
                    if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) currentOnEdited()
                }

                val content = buffer.content
                val line = buffer.cursorLine.coerceIn(0, content.lineCount - 1)
                val column = buffer.cursorColumn.coerceIn(0, content.getColumnCount(line))
                // Cursor first (without centering the view -- restoreScroll below places the
                // viewport), then the exact scroll offset the user left the tab at.
                if (line != 0 || column != 0) post { setSelection(line, column, false) }
                restoreScroll(this, buffer.scrollX, buffer.scrollY)

                handle.editor = this
            }
        },
        update = { editor ->
            if (editor.tag != Look(darkTheme, highlightReady, themeId)) {
                applyLook(editor, buffer, textMate, darkTheme, highlightReady, themeId)
            }
        },
        onRelease = { editor ->
            val cursor = editor.cursor
            val (scrollX, scrollY) = captureScroll(editor)
            currentOnViewState(cursor.leftLine, cursor.leftColumn, editor.textSizePx, scrollX, scrollY)
            if (handle.editor === editor) handle.editor = null
            // Detach from the shared Content before releasing so the old view stops listening.
            editor.setText("")
            editor.release()
        },
    )
}

/**
 * Sora's virtual scroll position isn't part of its public-stable surface, so both sides of
 * this are defensive: a failure here must never take the editor down with it, it just means
 * the tab opens scrolled to the cursor instead of exactly where it was left.
 */
private fun captureScroll(editor: CodeEditor): Pair<Int, Int> = try {
    editor.scroller.currX to editor.scroller.currY
} catch (_: Throwable) {
    0 to 0
}

private fun restoreScroll(editor: CodeEditor, x: Int, y: Int) {
    if (x == 0 && y == 0) return
    editor.post {
        try {
            editor.scroller.startScroll(0, 0, x, y, 0)
            editor.invalidate()
        } catch (_: Throwable) {
            // Best effort -- cursor placement above still gets the view roughly on-screen.
        }
    }
}

/** What an editor view is currently dressed in; kept in its `tag` to skip redundant work. */
private data class Look(val dark: Boolean, val highlighted: Boolean, val themeId: String)

private fun applyLook(
    editor: CodeEditor,
    buffer: TabBuffer,
    textMate: TextMateSupport,
    dark: Boolean,
    highlightReady: Boolean,
    themeId: String,
) {
    val highlighted = highlightReady && textMate.applyTo(editor, buffer.file, dark, themeId)
    // Plain-text fallback (grammars still loading, or TextMate failed): built-in schemes.
    if (!highlighted) editor.colorScheme = if (dark) SchemeDarcula() else EditorColorScheme()
    editor.tag = Look(dark, highlightReady, themeId)
}
