package com.invictus.xcode.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/**
 * Home screen: app name + settings in the top bar, a compass+pencil "build your app" hero with the
 * Open Project button at its center, and recent projects below. Opening a project from here
 * (button or a recent row) records it in recents and takes the user to the workspace.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onProjectOpened: () -> Unit,
    onClone: () -> Unit = {},
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
                title = {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                    )
                },
                actions = {
                    IconButton(onClick = onClone) {
                        Icon(XIcons.Commit, contentDescription = stringResource(R.string.home_clone))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(XIcons.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BuildIllustration(Modifier.size(110.dp))
                Spacer(Modifier.height(24.dp))
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
            onClone = onClone,
            onDismiss = { showProjectSheet = false },
            // Home screen already shows Recent Projects behind the sheet; no need to repeat it here.
            showRecent = false,
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

/** Static "build your app" illustration: a drafting compass crossed with a pencil. */
@Composable
private fun BuildIllustration(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.045f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        // --- Drafting compass (two legs joined at a hinge near the top) ---
        val hinge = Offset(w * 0.50f, h * 0.14f)
        val leftFoot = Offset(w * 0.22f, h * 0.92f)
        val rightFoot = Offset(w * 0.72f, h * 0.92f)

        // hinge knob
        drawCircle(primary, radius = w * 0.045f, center = hinge)
        drawCircle(primary, radius = w * 0.045f, center = hinge, style = Stroke(width = w * 0.02f))

        // legs
        drawLine(primary, hinge, leftFoot, strokeWidth = stroke.width, cap = StrokeCap.Round)
        drawLine(primary, hinge, rightFoot, strokeWidth = stroke.width, cap = StrokeCap.Round)

        // left foot: pivot point (small filled dot)
        drawCircle(primary, radius = w * 0.028f, center = leftFoot)

        // right foot: pencil tip (small triangle)
        val tipPath = Path().apply {
            moveTo(rightFoot.x, rightFoot.y)
            lineTo(rightFoot.x - w * 0.05f, rightFoot.y - h * 0.09f)
            lineTo(rightFoot.x + w * 0.05f, rightFoot.y - h * 0.09f)
            close()
        }
        drawPath(tipPath, primary)

        // cross-bar connecting the two legs partway down (compass hallmark)
        val barT = 0.5f
        val barStart = Offset(
            hinge.x + (leftFoot.x - hinge.x) * barT,
            hinge.y + (leftFoot.y - hinge.y) * barT,
        )
        val barEnd = Offset(
            hinge.x + (rightFoot.x - hinge.x) * barT,
            hinge.y + (rightFoot.y - hinge.y) * barT,
        )
        drawLine(onSurfaceVariant, barStart, barEnd, strokeWidth = w * 0.02f, cap = StrokeCap.Round)

        // faint drawn arc under the pivot foot, as if the compass just traced it
        val arcRect = androidx.compose.ui.geometry.Rect(
            center = leftFoot,
            radius = w * 0.16f,
        )
        drawArc(
            color = onSurfaceVariant.copy(alpha = 0.5f),
            startAngle = -50f,
            sweepAngle = 220f,
            useCenter = false,
            topLeft = arcRect.topLeft,
            size = arcRect.size,
            style = Stroke(width = w * 0.014f, cap = StrokeCap.Round),
        )

        // --- Pencil, laid diagonally across the compass ---
        val pencilStart = Offset(w * 0.08f, h * 0.62f)
        val pencilBodyEnd = Offset(w * 0.72f, h * 0.20f)
        val pencilTip = Offset(w * 0.86f, h * 0.08f)

        drawLine(primary, pencilStart, pencilBodyEnd, strokeWidth = w * 0.075f, cap = StrokeCap.Square)
        drawLine(onSurfaceVariant, pencilBodyEnd, pencilTip, strokeWidth = w * 0.03f, cap = StrokeCap.Round)
        drawCircle(onSurfaceVariant, radius = w * 0.014f, center = pencilTip)
    }
}
