package com.invictus.xcode.feature.editor

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.invictus.xcode.core.editor.TabBuffer
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
    handle: EditorHandle,
    onEdited: () -> Unit,
    onViewState: (line: Int, column: Int, textSizePx: Float) -> Unit,
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
                colorScheme = schemeFor(darkTheme)
                tag = darkTheme

                // Same Content object as last time, so undo/redo history comes along.
                setText(buffer.content)

                subscribeEvent(ContentChangeEvent::class.java) { event, _ ->
                    // Loading text into the view is not an edit.
                    if (event.action != ContentChangeEvent.ACTION_SET_NEW_TEXT) currentOnEdited()
                }

                val content = buffer.content
                val line = buffer.cursorLine.coerceIn(0, content.lineCount - 1)
                val column = buffer.cursorColumn.coerceIn(0, content.getColumnCount(line))
                if (line != 0 || column != 0) post { setSelection(line, column, true) }

                handle.editor = this
            }
        },
        update = { editor ->
            if (editor.tag != darkTheme) {
                editor.colorScheme = schemeFor(darkTheme)
                editor.tag = darkTheme
            }
        },
        onRelease = { editor ->
            val cursor = editor.cursor
            currentOnViewState(cursor.leftLine, cursor.leftColumn, editor.textSizePx)
            if (handle.editor === editor) handle.editor = null
            // Detach from the shared Content before releasing so the old view stops listening.
            editor.setText("")
            editor.release()
        },
    )
}

private fun schemeFor(dark: Boolean): EditorColorScheme =
    if (dark) SchemeDarcula() else EditorColorScheme()
