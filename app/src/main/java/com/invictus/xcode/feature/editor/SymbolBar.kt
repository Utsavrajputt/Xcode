package com.invictus.xcode.feature.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import com.invictus.xcode.R
import com.invictus.xcode.core.editor.EditorSettingsStore
import com.invictus.xcode.ui.icons.XIcons

/**
 * Inserts [symbol] at the caret. Content.insert() is a raw text edit; Sora's own Cursor is
 * listening to the same Content object (see [CodeEditorView]) so it advances on its own --
 * same idiom the rest of this screen uses for undo/redo via the shared [EditorHandle].
 */
private fun insertSymbol(handle: EditorHandle, symbol: String) {
    val editor = handle.editor ?: return
    try {
        val cursor = editor.cursor
        editor.text.insert(cursor.leftLine, cursor.leftColumn, symbol)
    } catch (_: Exception) {
        // Best effort -- e.g. editor mid-teardown on a fast tab switch.
    }
}

/**
 * Quick-insert strip shown above the soft keyboard (plan 3.2 "customizable symbol bar"). The
 * symbols themselves are user-editable via [SymbolBarCustomizeDialog], persisted in
 * [EditorSettingsStore].
 */
@Composable
fun SymbolBar(
    handle: EditorHandle,
    symbols: List<String>,
    onCustomize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            symbols.forEach { symbol ->
                SymbolKey(symbol = symbol, onClick = { insertSymbol(handle, symbol) })
            }
            IconButton(onClick = onCustomize, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = XIcons.Edit,
                    contentDescription = stringResource(R.string.symbol_bar_customize),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SymbolKey(symbol: String, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .widthIn(min = 36.dp)
            .height(32.dp)
            .clickable(onClick = onClick),
    ) {
        Text(text = symbol, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun SymbolBarCustomizeDialog(
    current: List<String>,
    onSave: (List<String>) -> Unit,
    onResetDefault: () -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(current) { mutableStateOf(current.joinToString(" ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.symbol_bar_dialog_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.symbol_bar_dialog_hint)) },
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = text.split(" ").filter { it.isNotEmpty() }
                    if (parsed.isNotEmpty()) onSave(parsed)
                },
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onResetDefault) {
                    Text(stringResource(R.string.symbol_bar_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}
