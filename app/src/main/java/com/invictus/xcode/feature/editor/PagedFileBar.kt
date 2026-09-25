package com.invictus.xcode.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.invictus.xcode.R
import com.invictus.xcode.ui.icons.XIcons

/**
 * Plan 3.2 / M4 "paged large file": a slim bar shown only for a file opened above
 * [com.invictus.xcode.core.editor.TextFileIo.MAX_BYTES] -- prev/next plus a jump-to-page
 * dialog, the page-nav UI decided on for this pass (find/replace stays current-page-only).
 *
 * The outgoing page's live cursor/scroll is read from [handle] right here, before dispatching
 * [onChangePage], the same "read the live editor, then hand it to the view model" shape
 * [CodeEditorView]'s own teardown capture already uses.
 */
@Composable
fun PagedFileBar(
    currentPage: Int,
    pageCount: Int,
    handle: EditorHandle,
    onChangePage: (toIndex: Int, cursorLine: Int, cursorColumn: Int, scrollX: Int, scrollY: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showJump by remember { mutableStateOf(false) }

    fun goTo(targetIndex: Int) {
        val target = targetIndex.coerceIn(0, pageCount - 1)
        if (target == currentPage) return
        val editor = handle.editor
        val cursor = editor?.cursor
        val (scrollX, scrollY) = try {
            editor?.scroller?.let { it.currX to it.currY } ?: (0 to 0)
        } catch (_: Throwable) {
            0 to 0
        }
        onChangePage(target, cursor?.leftLine ?: 0, cursor?.leftColumn ?: 0, scrollX, scrollY)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        IconButton(onClick = { goTo(currentPage - 1) }, enabled = currentPage > 0) {
            Icon(XIcons.ChevronLeft, contentDescription = stringResource(R.string.editor_page_previous))
        }
        Text(
            text = stringResource(R.string.editor_page_indicator, currentPage + 1, pageCount),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        )
        IconButton(onClick = { showJump = true }) {
            Icon(XIcons.Search, contentDescription = stringResource(R.string.editor_page_jump))
        }
        IconButton(onClick = { goTo(currentPage + 1) }, enabled = currentPage < pageCount - 1) {
            Icon(XIcons.ChevronRight, contentDescription = stringResource(R.string.editor_page_next))
        }
    }

    if (showJump) {
        JumpToPageDialog(
            pageCount = pageCount,
            onJump = { index -> goTo(index); showJump = false },
            onDismiss = { showJump = false },
        )
    }
}

@Composable
private fun JumpToPageDialog(pageCount: Int, onJump: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val target = text.toIntOrNull()
    val valid = target != null && target in 1..pageCount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_page_jump)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text(stringResource(R.string.editor_page_jump_hint, pageCount)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = text.isNotEmpty() && !valid,
                modifier = Modifier.width(160.dp),
            )
        },
        confirmButton = {
            TextButton(onClick = { target?.let { onJump(it - 1) } }, enabled = valid) {
                Text(stringResource(R.string.editor_page_jump))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
