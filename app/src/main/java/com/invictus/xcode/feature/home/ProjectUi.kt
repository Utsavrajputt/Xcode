package com.invictus.xcode.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.xcode.R
import com.invictus.xcode.feature.project.RecentItem
import com.invictus.xcode.feature.workspace.FileTreeEvent
import com.invictus.xcode.feature.workspace.FileTreeViewModel
import com.invictus.xcode.ui.components.expressiveClickable
import com.invictus.xcode.ui.icons.XIcons
import java.io.File

/** Fixed palette for project folder tiles; a project's color comes from a hash of its name. */
private val ProjectPalette = listOf(
    Color(0xFF7C6CFF), // purple
    Color(0xFF4FC3F7), // blue
    Color(0xFF66BB6A), // green
    Color(0xFFFFB74D), // amber
    Color(0xFFF06292), // pink
    Color(0xFF26C6A9), // teal
)

/** Stable per-name color: same project always gets the same tile, independent of list order. */
internal fun projectColor(name: String): Color =
    ProjectPalette[(name.hashCode() and Int.MAX_VALUE) % ProjectPalette.size]

/** Case-insensitive match on project name or path. */
internal fun RecentItem.matches(query: String): Boolean {
    val q = query.trim()
    return q.isEmpty() || file.name.contains(q, ignoreCase = true) || file.path.contains(q, ignoreCase = true)
}

/**
 * Returns a function that opens a project and navigates to the workspace only once the tree has
 * actually switched to it, so the workspace (a fresh ViewModel there) restores this same project
 * as its latest recent.
 */
@Composable
internal fun rememberProjectOpener(
    onProjectOpened: () -> Unit,
    fileTreeViewModel: FileTreeViewModel = viewModel(factory = FileTreeViewModel.Factory),
    beforeOpen: () -> Unit = {},
): (File) -> Unit {
    val treeState by fileTreeViewModel.uiState.collectAsStateWithLifecycle()
    var pendingOpen by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(treeState.root) {
        val pending = pendingOpen
        if (pending != null && treeState.root.path == pending) {
            pendingOpen = null
            onProjectOpened()
        }
    }

    val rootPath = treeState.root.path
    return { dir ->
        beforeOpen()
        if (dir.path == rootPath) {
            onProjectOpened()
        } else {
            pendingOpen = dir.path
            fileTreeViewModel.onEvent(FileTreeEvent.OpenProject(dir))
        }
    }
}

/** Card row for one recent project: colored folder tile, name, path and a remove menu. */
@Composable
internal fun ProjectCardRow(
    item: RecentItem,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    val accent = if (item.exists) projectColor(item.file.name) else MaterialTheme.colorScheme.outline
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .clip(shape)
                .expressiveClickable(onClick = onOpen)
                .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(XIcons.Folder, contentDescription = null, tint = accent)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.file.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = item.file.path,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(XIcons.MoreVert, contentDescription = null)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.recent_menu_remove)) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}
