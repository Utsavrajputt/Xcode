package com.invictus.xcode.feature.git

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.unit.dp
import com.invictus.xcode.R

@Composable
fun GitConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

/** Multi-field text dialog; har field ek Pair(label, initialValue). */
@Composable
fun GitFieldsDialog(
    title: String,
    fields: List<Pair<String, String>>,
    confirmLabel: String,
    showCheckbox: Pair<String, Boolean>? = null,   // label -> checked
    onConfirm: (values: List<String>, checked: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val states = fields.map { remember { mutableStateOf(it.second) } }
    var checked by remember { mutableStateOf(showCheckbox?.second ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                fields.forEachIndexed { i, (label, _) ->
                    OutlinedTextField(
                        value = states[i].value,
                        onValueChange = { states[i].value = it },
                        label = { Text(label) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (i < fields.lastIndex) Spacer(Modifier.height(8.dp))
                }
                if (showCheckbox != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = checked, onCheckedChange = { checked = it })
                        Text(showCheckbox.first)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(states.map { it.value }, checked) }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
