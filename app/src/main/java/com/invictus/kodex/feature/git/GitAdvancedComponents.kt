package com.invictus.kodex.feature.git

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import com.invictus.kodex.R

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

/** A force push was refused because [remote] has commits that were never fetched here. */
@Composable
fun GitFetchFirstDialog(remote: String, onFetch: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.git_force_needs_fetch_title)) },
        text = { Text(stringResource(R.string.git_force_needs_fetch_body, remote)) },
        confirmButton = {
            TextButton(onClick = onFetch) { Text(stringResource(R.string.git_push_rejected_fetch)) }
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

/**
 * Add/edit token dialog for Settings > GitHub: host is a closed dropdown (no typos, no need to
 * remember the exact domain spelling) instead of free text, and the username field starts empty
 * with just a hint -- "x-access-token" was being silently pre-filled before, which looked like
 * the user's own username. Editing an existing entry keeps its saved host/username as the field
 * value (there's something real to show), only the *add* case starts blank.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHostTokenDialog(
    title: String,
    initialHost: String,
    initialUsername: String,
    confirmLabel: String,
    onConfirm: (host: String, username: String, token: String) -> Unit,
    onDismiss: () -> Unit,
) {
    val hostOptions = listOf("github.com", "gitlab.com", "bitbucket.org")
    var hostExpanded by remember { mutableStateOf(false) }
    var host by remember { mutableStateOf(initialHost) }
    var username by remember { mutableStateOf(initialUsername) }
    var token by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                ExposedDropdownMenuBox(
                    expanded = hostExpanded,
                    onExpandedChange = { hostExpanded = it },
                ) {
                    OutlinedTextField(
                        value = host,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.git_cred_host_hint)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = hostExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    )
                    DropdownMenu(
                        expanded = hostExpanded,
                        onDismissRequest = { hostExpanded = false },
                    ) {
                        hostOptions.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = { host = option; hostExpanded = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.git_cred_user_hint)) },
                    placeholder = { Text(stringResource(R.string.git_cred_user_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(stringResource(R.string.git_cred_token_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(host, username, token) }) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}


/** File names (last path segment) used by more than one changed path in the working tree. */
internal fun GitViewModel.UiState.duplicateChangeNames(): Set<String> =
    status?.changes?.map { it.repoRelativePath }?.distinct().orEmpty()
        .groupingBy { it.substringAfterLast('/') }.eachCount()
        .filterValues { it > 1 }.keys

/** Just the file name, or "parent/name" when another change has the same file name. */
internal fun shortChangeLabel(path: String, duplicateNames: Set<String>): String {
    val name = path.substringAfterLast('/')
    if (name !in duplicateNames) return name
    val parent = path.substringBeforeLast('/', "").substringAfterLast('/')
    return if (parent.isEmpty()) name else "$parent/$name"
}

/**
 * A changed file's path: shown in full while it fits on one line; once it would run off the
 * screen, only the file name (with its parent folder when the name isn't unique).
 */
@Composable
internal fun ChangePathText(
    path: String,
    duplicateNames: Set<String>,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    var tooLong by remember(path) { mutableStateOf(false) }
    Text(
        text = if (tooLong) shortChangeLabel(path, duplicateNames) else path,
        style = style,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier,
        onTextLayout = { if (!tooLong && it.hasVisualOverflow) tooLong = true },
    )
}
