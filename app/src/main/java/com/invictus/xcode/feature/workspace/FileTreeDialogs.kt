package com.invictus.xcode.feature.workspace

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import com.invictus.xcode.R
import com.invictus.xcode.core.fs.OpenDecision

/** Renders whichever tree dialog is pending (create / delete / open warning). */
@Composable
fun TreeDialogHost(dialog: TreeDialog?, onEvent: (FileTreeEvent) -> Unit) {
    when (dialog) {
        null -> Unit
        is TreeDialog.Create -> NameDialog(dialog, onEvent)
        is TreeDialog.ConfirmDelete -> DeleteDialog(dialog, onEvent)
        is TreeDialog.OpenWarning -> OpenWarningDialog(dialog, onEvent)
        is TreeDialog.ExtractZip -> ExtractZipDialog(dialog, onEvent)
    }
}

@Composable
private fun NameDialog(dialog: TreeDialog.Create, onEvent: (FileTreeEvent) -> Unit) {
    var text by rememberSaveable(dialog.parent.path, dialog.isFolder) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val error = dialog.error
    AlertDialog(
        onDismissRequest = { onEvent(FileTreeEvent.DismissDialog) },
        title = {
            Text(
                stringResource(
                    if (dialog.isFolder) R.string.dialog_new_folder_title else R.string.dialog_new_file_title,
                ),
            )
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = {
                    Text(
                        stringResource(
                            if (dialog.isFolder) R.string.dialog_name_label_folder else R.string.dialog_name_label_file,
                        ),
                    )
                },
                isError = error != null,
                supportingText = if (error != null) {
                    { Text(error.asString()) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = { if (text.isNotBlank()) onEvent(FileTreeEvent.ConfirmCreate(text)) },
                ),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(FileTreeEvent.ConfirmCreate(text)) },
                enabled = text.isNotBlank(),
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(FileTreeEvent.DismissDialog) }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun DeleteDialog(dialog: TreeDialog.ConfirmDelete, onEvent: (FileTreeEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { onEvent(FileTreeEvent.DismissDialog) },
        title = { Text(stringResource(R.string.dialog_delete_title, dialog.file.name)) },
        text = {
            Text(
                stringResource(
                    if (dialog.isDirectory) R.string.dialog_delete_folder_body else R.string.dialog_delete_file_body,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(FileTreeEvent.ConfirmDelete) },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.action_delete)) }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(FileTreeEvent.DismissDialog) }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun OpenWarningDialog(dialog: TreeDialog.OpenWarning, onEvent: (FileTreeEvent) -> Unit) {
    val context = LocalContext.current
    val name = dialog.file.name
    val (title, body) = when (val decision = dialog.decision) {
        is OpenDecision.LargeText -> stringResource(R.string.dialog_open_large_title) to
            stringResource(
                R.string.dialog_open_large_body,
                name,
                Formatter.formatShortFileSize(context, decision.sizeBytes),
            )
        else -> stringResource(R.string.dialog_open_binary_title) to
            stringResource(R.string.dialog_open_binary_body, name)
    }
    AlertDialog(
        onDismissRequest = { onEvent(FileTreeEvent.DismissDialog) },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = { onEvent(FileTreeEvent.ConfirmOpen(dialog.file)) }) {
                Text(stringResource(R.string.action_open_anyway))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(FileTreeEvent.DismissDialog) }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/** Shown after uploading a .zip: [Done] (left, keeps the zip) and [Extract] (right end), plus "delete after". */
@Composable
private fun ExtractZipDialog(dialog: TreeDialog.ExtractZip, onEvent: (FileTreeEvent) -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!dialog.busy) onEvent(FileTreeEvent.SkipExtract) },
        properties = DialogProperties(dismissOnBackPress = !dialog.busy, dismissOnClickOutside = !dialog.busy),
        title = { Text(stringResource(R.string.dialog_extract_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.dialog_extract_body, dialog.zip.name))
                if (dialog.busy) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.dialog_extracting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEvent(FileTreeEvent.SetDeleteAfterExtract(!dialog.deleteAfter)) },
                    ) {
                        Checkbox(
                            checked = dialog.deleteAfter,
                            onCheckedChange = { onEvent(FileTreeEvent.SetDeleteAfterExtract(it)) },
                        )
                        Text(stringResource(R.string.dialog_extract_delete_after))
                    }
                }
            }
        },
        // confirmButton sits at the right end, dismissButton directly to its left.
        confirmButton = {
            TextButton(onClick = { onEvent(FileTreeEvent.ConfirmExtract) }, enabled = !dialog.busy) {
                Text(stringResource(R.string.action_extract))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(FileTreeEvent.SkipExtract) }, enabled = !dialog.busy) {
                Text(stringResource(R.string.action_done))
            }
        },
    )
}
