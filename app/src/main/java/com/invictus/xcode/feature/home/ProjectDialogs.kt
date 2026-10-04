package com.invictus.xcode.feature.home

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.invictus.xcode.R
import com.invictus.xcode.feature.project.DeleteProject
import com.invictus.xcode.feature.project.ProjectInfo
import com.invictus.xcode.feature.project.ProjectsEvent
import com.invictus.xcode.feature.project.ProjectsUiState
import com.invictus.xcode.feature.project.RenameProjectDialog
import com.invictus.xcode.feature.workspace.asString
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

/**
 * Dialogs behind the project card menu: rename, delete confirmation and info.
 * [showRename] lets Home hide its copy while the Open Project sheet (which draws its own) is up.
 */
@Composable
internal fun ProjectActionDialogs(
    state: ProjectsUiState,
    onEvent: (ProjectsEvent) -> Unit,
    showRename: Boolean = true,
) {
    if (showRename) state.renaming?.let { RenameProjectDialog(it, onEvent) }
    state.deleting?.let { DeleteProjectDialog(it, onEvent) }
    state.info?.let { ProjectInfoDialog(it, onEvent) }
}

@Composable
private fun DeleteProjectDialog(state: DeleteProject, onEvent: (ProjectsEvent) -> Unit) {
    val error = state.error
    AlertDialog(
        onDismissRequest = { onEvent(ProjectsEvent.DismissDelete) },
        title = { Text(stringResource(R.string.dialog_delete_title, state.file.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dialog_delete_project_body))
                Text(
                    text = state.file.path,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (error != null) {
                    Text(error.asString(), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onEvent(ProjectsEvent.ConfirmDelete) },
                enabled = !state.deleting,
            ) {
                if (state.deleting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(ProjectsEvent.DismissDelete) },
                enabled = !state.deleting,
            ) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ProjectInfoDialog(info: ProjectInfo, onEvent: (ProjectsEvent) -> Unit) {
    val context = LocalContext.current
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    val calculating = stringResource(R.string.project_info_calculating)
    val more = if (info.truncated) "+" else ""
    val none = stringResource(R.string.project_info_none)

    val git = when {
        info.loading -> calculating
        !info.isGit -> stringResource(R.string.project_info_git_no)
        info.gitBranch == null -> stringResource(R.string.project_info_git_yes)
        info.detached -> stringResource(R.string.project_info_git_detached, info.gitBranch)
        else -> stringResource(R.string.project_info_git_branch, info.gitBranch)
    }

    AlertDialog(
        onDismissRequest = { onEvent(ProjectsEvent.DismissInfo) },
        title = { Text(info.file.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoRow(stringResource(R.string.project_info_path), info.file.path)
                InfoRow(
                    stringResource(R.string.project_info_size),
                    if (info.loading) calculating else Formatter.formatFileSize(context, info.sizeBytes) + more,
                )
                InfoRow(
                    stringResource(R.string.project_info_files),
                    if (info.loading) calculating else NumberFormat.getInstance().format(info.fileCount) + more,
                )
                InfoRow(
                    stringResource(R.string.project_info_modified),
                    if (info.loading) calculating
                    else if (info.lastModified > 0L) dateFormat.format(Date(info.lastModified)) else none,
                )
                InfoRow(
                    stringResource(R.string.project_info_opened),
                    if (info.lastOpenedAt > 0L) dateFormat.format(Date(info.lastOpenedAt)) else none,
                )
                InfoRow(stringResource(R.string.project_info_git), git)
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(ProjectsEvent.DismissInfo) }) {
                Text(stringResource(R.string.action_close_dialog))
            }
        },
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
