package com.invictus.xcode.core.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.invictus.xcode.R
import io.github.rosemoe.sora.widget.CodeEditor
import java.io.File

/**
 * Plan 3.2 "quick actions menu" -- the "action registry pattern": each action is its own
 * small class implementing [EditorAction], and [EditorActionRegistry] is the single place
 * that lists them for whatever UI surface wants to show them (today: the top bar menu in
 * [com.invictus.xcode.feature.editor.QuickActionsMenu]; a selection popup can read from the
 * same list later without duplicating any action logic).
 *
 * LSP-only actions from the ACSIDE reference (format, organize imports, rename, quick fix)
 * are out of this app's scope per PLAN.md 3.2 and are not modelled here.
 */
interface EditorAction {
    val id: String
    val labelRes: Int

    /** Whether this action makes sense right now (e.g. paste needs clipboard text). */
    fun isAvailable(editor: CodeEditor, filePath: String?, context: Context): Boolean = true

    fun perform(editor: CodeEditor, filePath: String?, context: Context)
}

object EditorActionRegistry {
    val actions: List<EditorAction> = listOf(
        SelectAllAction,
        CutAction,
        CopyAction,
        PasteAction,
        DuplicateLineAction,
        DeleteLineAction,
        MoveLineUpAction,
        MoveLineDownAction,
        ToggleCommentAction,
    )
}

private data class LineRange(val startLine: Int, val endLine: Int)

/**
 * Whole document as lines. `Content.toString()` is the one text-extraction call this codebase
 * already relies on elsewhere (EditorViewModel's dirty-check snapshot), so actions build on it
 * too instead of a less certain per-line accessor.
 */
private fun linesOf(editor: CodeEditor): List<String> = editor.text.toString().split("\n")

private fun selectionOrCurrentLine(editor: CodeEditor): LineRange {
    val cursor = editor.cursor
    return if (cursor.isSelected) {
        LineRange(cursor.leftLine, cursor.rightLine)
    } else {
        LineRange(cursor.leftLine, cursor.leftLine)
    }
}

private fun selectedText(editor: CodeEditor): String? {
    val cursor = editor.cursor
    if (!cursor.isSelected) return null
    val lines = linesOf(editor)
    val startLine = cursor.leftLine
    val startCol = cursor.leftColumn
    val endLine = cursor.rightLine
    val endCol = cursor.rightColumn
    return if (startLine == endLine) {
        lines[startLine].substring(startCol, endCol)
    } else {
        buildString {
            append(lines[startLine].substring(startCol))
            for (l in (startLine + 1) until endLine) append('\n').append(lines[l])
            append('\n').append(lines[endLine].substring(0, endCol))
        }
    }
}

private fun clipboardOf(context: Context): ClipboardManager? =
    context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

object SelectAllAction : EditorAction {
    override val id = "select_all"
    override val labelRes = R.string.qa_select_all

    override fun perform(editor: CodeEditor, filePath: String?, context: Context) {
        try {
            val lines = linesOf(editor)
            val lastLine = (lines.size - 1).coerceAtLeast(0)
            editor.setSelectionRegion(0, 0, lastLine, lines[lastLine].length)
        } catch (_: Exception) {
        }
    }
}

object CutAction : EditorAction {
    override val id = "cut"
    override val labelRes = R.string.qa_cut

    override fun isAvailable(editor: CodeEditor, filePath: String?, context: Context) =
        editor.cursor.isSelected

    override fun perform(editor: CodeEditor, filePath: String?, context: Context) {
        try {
            val text = selectedText(editor) ?: return
            clipboardOf(context)?.setPrimaryClip(ClipData.newPlainText("xcode", text))
            val cursor = editor.cursor
            editor.text.replace(cursor.leftLine, cursor.leftColumn, cursor.rightLine, cursor.rightColumn, "")
        } catch (_: Exception) {
        }
    }
}

object CopyAction : EditorAction {
    override val id = "copy"
    override val labelRes = R.string.qa_copy

    override fun isAvailable(editor: CodeEditor, filePath: String?, context: Context) =
        editor.cursor.isSelected

    override fun perform(editor: CodeEditor, filePath: String?, context: Context) {
        try {
            val text = selectedText(editor) ?: return
            clipboardOf(context)?.setPrimaryClip(ClipData.newPlainText("xcode", text))
        } catch (_: Exception) {
        }
    }
}

object PasteAction : EditorAction {
    override val id = "paste"
    override val labelRes = R.string.qa_paste

    override fun isAvailable(editor: CodeEditor, filePath: String?, context: Context): Boolean {
        val clip = clipboardOf(context)?.primaryClip ?: return false
        return clip.itemCount > 0
    }

    override fun perform(editor: CodeEditor, filePath: String?, context: Context) {
        try {
            val clip = clipboardOf(context)?.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: return
            val cursor = editor.cursor
            if (cursor.isSelected) {
                editor.text.replace(cursor.leftLine, cursor.leftColumn, cursor.rightLine, cursor.rightColumn, text)
            } else {
                editor.text.insert(cursor.leftLine, cursor.leftColumn, text)
            }
        } catch (_: Exception) {
        }
    }
}

object DuplicateLineAction : EditorAction {
    override val id = "duplicate_line"
    override val labelRes = R.string.qa_duplicate_line

    override fun perform(editor: CodeEditor, filePath: String?, context: Context) {
        try {
            val range = selectionOrCurrentLine(editor)
            val lines = linesOf(editor)
            val block = (range.startLine..range.endLine).joinToString("\n") { lines[it] }
            val insertCol = editor.text.getColumnCount(range.endLine)
            editor.text.insert(range.endLine, insertCol, "\n$block")
        } catch (_: Exception) {
        }
    }
}

object DeleteLineAction : EditorAction {
    override val id = "delete_line"
    override val labelRes = R.string.qa_delete_line

    override fun perform(editor: CodeEditor, filePath: String?, context: Context) {
        try {
            val range = selectionOrCurrentLine(editor)
            val lines = linesOf(editor)
            val lastIndex = lines.size - 1
            when {
                range.endLine < lastIndex -> {
                    // Lines below shift up to take startLine's place; also eat the newline
                    // right after the deleted block.
                    editor.text.replace(range.startLine, 0, range.endLine + 1, 0, "")
                }
                range.startLine > 0 -> {
                    // Deleting the tail of the document: eat the newline *before* the block
                    // instead, since there's nothing below to pull up into its place.
                    val prevCol = editor.text.getColumnCount(range.startLine - 1)
                    val endCol = editor.text.getColumnCount(range.endLine)
                    editor.text.replace(range.startLine - 1, prevCol, range.endLine, endCol, "")
                }
                else -> {
                    // The only line in the document: a line-based buffer always has at least
                    // one line, so clear its content instead of removing it outright.
                    editor.text.replace(0, 0, 0, editor.text.getColumnCount(0), "")
                }
            }
        } catch (_: Exception) {
        }
    }
}

object MoveLineUpAction : EditorAction {
    override val id = "move_line_up"
    override val labelRes = R.string.qa_move_line_up

    override fun isAvailable(editor: CodeEditor, filePath: String?, context: Context) =
        editor.cursor.leftLine > 0

    override fun perform(editor: CodeEditor, filePath: String?, context: Context) {
        try {
            val line = editor.cursor.leftLine
            if (line <= 0) return
            val column = editor.cursor.leftColumn
            val lines = linesOf(editor)
            val above = lines[line - 1]
            val current = lines[line]
            val endCol = editor.text.getColumnCount(line)
            editor.text.replace(line - 1, 0, line, endCol, "$current\n$above")
            editor.setSelection(line - 1, column.coerceAtMost(current.length), false)
        } catch (_: Exception) {
        }
    }
}

object MoveLineDownAction : EditorAction {
    override val id = "move_line_down"
    override val labelRes = R.string.qa_move_line_down

    override fun isAvailable(editor: CodeEditor, filePath: String?, context: Context): Boolean {
        val lines = linesOf(editor)
        return editor.cursor.leftLine < lines.size - 1
    }

    override fun perform(editor: CodeEditor, filePath: String?, context: Context) {
        try {
            val line = editor.cursor.leftLine
            val column = editor.cursor.leftColumn
            val lines = linesOf(editor)
            if (line >= lines.size - 1) return
            val below = lines[line + 1]
            val current = lines[line]
            val endCol = editor.text.getColumnCount(line + 1)
            editor.text.replace(line, 0, line + 1, endCol, "$below\n$current")
            editor.setSelection(line + 1, column.coerceAtMost(current.length), false)
        } catch (_: Exception) {
        }
    }
}

object ToggleCommentAction : EditorAction {
    override val id = "toggle_comment"
    override val labelRes = R.string.qa_toggle_comment

    override fun isAvailable(editor: CodeEditor, filePath: String?, context: Context): Boolean =
        filePath?.let { CommentSyntax.forFile(File(it)) } != null

    override fun perform(editor: CodeEditor, filePath: String?, context: Context) {
        try {
            val prefix = filePath?.let { CommentSyntax.forFile(File(it)) } ?: return
            val range = selectionOrCurrentLine(editor)
            val lines = linesOf(editor)
            val block = (range.startLine..range.endLine).map { lines[it] }
            val nonBlank = block.filter { it.isNotBlank() }
            val trimmedPrefix = prefix.trimEnd()
            val allCommented = nonBlank.isNotEmpty() && nonBlank.all { it.trimStart().startsWith(trimmedPrefix) }
            val edited = block.map { rawLine ->
                if (rawLine.isBlank()) return@map rawLine
                val indent = rawLine.takeWhile { it == ' ' || it == '\t' }
                val rest = rawLine.substring(indent.length)
                when {
                    !allCommented -> indent + prefix + rest
                    rest.startsWith(prefix) -> indent + rest.removePrefix(prefix)
                    else -> indent + rest.removePrefix(trimmedPrefix)
                }
            }
            val endCol = editor.text.getColumnCount(range.endLine)
            editor.text.replace(range.startLine, 0, range.endLine, endCol, edited.joinToString("\n"))
        } catch (_: Exception) {
        }
    }
}
