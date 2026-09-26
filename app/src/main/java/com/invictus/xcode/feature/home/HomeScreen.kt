package com.invictus.xcode.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.xcode.R
import com.invictus.xcode.feature.project.OpenProjectSheet
import com.invictus.xcode.feature.project.ProjectsEffect
import com.invictus.xcode.feature.project.ProjectsEvent
import com.invictus.xcode.feature.project.ProjectsViewModel
import com.invictus.xcode.feature.project.RecentItem
import com.invictus.xcode.feature.workspace.FileTreeEvent
import com.invictus.xcode.feature.workspace.FileTreeViewModel
import com.invictus.xcode.ui.icons.XIcons
import com.invictus.xcode.ui.components.expressiveClickable
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.cos
import kotlin.math.sin

/**
 * Home screen: app name + settings in the top bar, a compass "build your app" hero with the
 * Open Project button at its center, and recent projects below. Opening a project from here
 * (button or a recent row) records it in recents and takes the user to the workspace.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onProjectOpened: () -> Unit,
    modifier: Modifier = Modifier,
    projectsViewModel: ProjectsViewModel = viewModel(factory = ProjectsViewModel.Factory),
    fileTreeViewModel: FileTreeViewModel = viewModel(factory = FileTreeViewModel.Factory),
) {
    val projectsState by projectsViewModel.uiState.collectAsStateWithLifecycle()
    val treeState by fileTreeViewModel.uiState.collectAsStateWithLifecycle()
    var showProjectSheet by rememberSaveable { mutableStateOf(false) }
    var pendingOpen by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Navigate only once the tree has actually switched to the picked project, so the
    // workspace (a fresh ViewModel there) restores this same project as its latest recent.
    LaunchedEffect(treeState.root) {
        val pending = pendingOpen
        if (pending != null && treeState.root.path == pending) {
            pendingOpen = null
            onProjectOpened()
        }
    }

    LaunchedEffect(projectsViewModel) {
        projectsViewModel.effects.collect { effect ->
            when (effect) {
                is ProjectsEffect.Message ->
                    scope.launch { snackbarHostState.showSnackbar(effect.text.resolve(context)) }
                is ProjectsEffect.ProjectMoved ->
                    fileTreeViewModel.onEvent(FileTreeEvent.ProjectMoved(effect.old, effect.new))
            }
        }
    }

    val openProject: (File) -> Unit = { dir ->
        showProjectSheet = false
        if (dir.path == treeState.root.path) {
            onProjectOpened()
        } else {
            pendingOpen = dir.path
            fileTreeViewModel.onEvent(FileTreeEvent.OpenProject(dir))
        }
    }

    val copyPath: (File) -> Unit = { file ->
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(file.name, file.path))
        // Android 13+ shows its own "copied" confirmation.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.msg_path_copied)) }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(XIcons.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Compass(Modifier.fillMaxSize().padding(28.dp))
                Button(onClick = { showProjectSheet = true }) {
                    Icon(XIcons.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_open_project))
                }
            }
            Text(
                text = stringResource(R.string.home_recent),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            if (projectsState.recents.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_no_recents),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp),
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(items = projectsState.recents, key = { "r:" + it.file.path }) { item ->
                        RecentProjectRow(
                            item = item,
                            onOpen = { openProject(item.file) },
                            onRemove = { projectsViewModel.onEvent(ProjectsEvent.RemoveRecent(item.file.path)) },
                        )
                    }
                }
            }
        }
    }

    if (showProjectSheet) {
        OpenProjectSheet(
            state = projectsState,
            currentRoot = treeState.root,
            snackbarHostState = snackbarHostState,
            onEvent = projectsViewModel::onEvent,
            onOpen = openProject,
            onCopyPath = copyPath,
            onDismiss = { showProjectSheet = false },
        )
    }
}

@Composable
private fun RecentProjectRow(
    item: RecentItem,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .expressiveClickable(onClick = onOpen)
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            XIcons.Folder,
            contentDescription = null,
            tint = if (item.exists) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
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

/** Blueprint grid + rings + ticks + slowly rotating needle — "build your app" compass. */
@Composable
private fun Compass(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant

    val surface = MaterialTheme.colorScheme.surface
    val infiniteTransition = rememberInfiniteTransition(label = "compass")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(24000, easing = LinearEasing)),
        label = "angle",
    )
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f * 0.92f
            // blueprint grid
            val grid = r / 5f
            var gx = c.x % grid
            while (gx < size.width) {
                drawLine(outline.copy(alpha = 0.3f), Offset(gx, 0f), Offset(gx, size.height))
                gx += grid
            }
            var gy = c.y % grid
            while (gy < size.height) {
                drawLine(outline.copy(alpha = 0.3f), Offset(0f, gy), Offset(size.width, gy))
                gy += grid
            }
            // rings
            drawCircle(primary, r, c, style = Stroke(width = 3f))
            drawCircle(primary.copy(alpha = 0.55f), r * 0.78f, c, style = Stroke(width = 1.5f))
            drawCircle(primary.copy(alpha = 0.3f), r * 0.42f, c, style = Stroke(width = 1f))
            // crosshair
            drawLine(outline, Offset(c.x - r, c.y), Offset(c.x + r, c.y))
            drawLine(outline, Offset(c.x, c.y - r), Offset(c.x, c.y + r))
            // degree ticks
            for (i in 0 until 72) {
                val a = Math.toRadians((i * 5).toDouble())
                val ca = cos(a).toFloat()
                val sa = sin(a).toFloat()
                val major = i % 18 == 0
                val len = if (major) r * 0.12f else if (i % 6 == 0) r * 0.07f else r * 0.035f
                drawLine(
                    color = primary.copy(alpha = if (major) 0.9f else 0.5f),
                    start = Offset(c.x + (r - len) * ca, c.y + (r - len) * sa),
                    end = Offset(c.x + r * ca, c.y + r * sa),
                    strokeWidth = if (major) 4f else 2f,
                )
            }
            // rotating needle
            val rad = Math.toRadians(angle.toDouble())
            val nx = cos(rad).toFloat()
            val ny = sin(rad).toFloat()
            val tip = Offset(c.x + nx * r * 0.74f, c.y + ny * r * 0.74f)
            val tail = Offset(c.x - nx * r * 0.52f, c.y - ny * r * 0.52f)
            val w1 = Offset(c.x - ny * r * 0.09f, c.y + nx * r * 0.09f)
            val w2 = Offset(c.x + ny * r * 0.09f, c.y - nx * r * 0.09f)
            val needle = Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(w1.x, w1.y)
                lineTo(tail.x, tail.y)
                lineTo(w2.x, w2.y)
                close()
            }
            drawPath(needle, primary.copy(alpha = 0.85f))
            // clean hole behind the center button
            drawCircle(surface, radius = r * 0.16f, center = c)

            drawCircle(primary, radius = 5f, center = c)
        }
        val labelStyle = MaterialTheme.typography.labelLarge
        val labelColor = MaterialTheme.colorScheme.primary
        Text("N", style = labelStyle, color = labelColor, modifier = Modifier.align(Alignment.TopCenter))
        Text("S", style = labelStyle, color = labelColor, modifier = Modifier.align(Alignment.BottomCenter))
        Text("W", style = labelStyle, color = labelColor, modifier = Modifier.align(Alignment.CenterStart))
        Text("E", style = labelStyle, color = labelColor, modifier = Modifier.align(Alignment.CenterEnd))
    }
}
