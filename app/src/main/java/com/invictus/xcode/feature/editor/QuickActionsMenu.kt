package com.invictus.xcode.feature.editor

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.invictus.xcode.core.editor.EditorActionRegistry

/**
 * Plan 3.2 "quick actions menu" (top bar entry point). Lists every action registered in
 * [EditorActionRegistry] -- adding a new [com.invictus.xcode.core.editor.EditorAction] to
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
                enabled = available,
                onClick = {
                    onDismiss()
                    editor?.let { action.perform(it, activePath, context) }
                },
            )
        }
    }
}
