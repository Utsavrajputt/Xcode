package com.invictus.kodex.feature.editor

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.invictus.kodex.core.editor.EditorActionRegistry
import com.invictus.kodex.ui.icons.XIcons

/**
 * Plan 3.2 "quick actions menu" (top bar entry point). Lists every action registered in
 * [EditorActionRegistry] -- adding a new [com.invictus.kodex.core.editor.EditorAction] to
 * that list is enough for it to show up here, no separate UI wiring per action.
 */
@Composable
fun QuickActionsMenu(
    expanded: Boolean,
    handle: EditorHandle,
    activePath: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val editor = handle.editor
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        EditorActionRegistry.actions.forEach { action ->
            val available = editor != null && action.isAvailable(editor, activePath, context)
            DropdownMenuItem(
                text = { Text(stringResource(action.labelRes)) },
                leadingIcon = iconFor(action.id)?.let { icon -> { Icon(icon, contentDescription = null) } },
                enabled = available,
                onClick = {
                    onDismiss()
                    editor?.let { action.perform(it, activePath, context) }
                },
            )
        }
    }
}

private fun iconFor(id: String) = when (id) {
    "copy_file" -> XIcons.FileCopy
    "duplicate_line" -> XIcons.ContentCopy
    "delete_line" -> XIcons.Delete
    "move_line_up" -> XIcons.ArrowUpward
    "move_line_down" -> XIcons.ArrowDownward
    "toggle_comment" -> XIcons.Comment
    else -> null
}
