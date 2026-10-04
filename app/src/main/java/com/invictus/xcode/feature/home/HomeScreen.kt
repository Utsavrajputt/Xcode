package com.invictus.xcode.feature.home

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.xcode.R
import com.invictus.xcode.feature.project.OpenProjectSheet
import com.invictus.xcode.feature.project.ProjectsEffect
import com.invictus.xcode.feature.project.ProjectsEvent
import com.invictus.xcode.feature.project.ProjectsViewModel
import com.invictus.xcode.feature.workspace.FileTreeEvent
import com.invictus.xcode.feature.workspace.FileTreeViewModel
import com.invictus.xcode.ui.icons.XIcons
import kotlinx.coroutines.launch
import java.io.File

/** Recents shown on Home before "View All" takes over. */
private const val HomeRecentLimit = 5

/**
 * Home screen: hero banner, Open Project + New project buttons, and a short recent-projects list.
 * The search icon turns the top bar into an inline filter over recents; "View All" opens the full
 * list. Opening a project records it in recents and takes the user to the workspace.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onProjectOpened: () -> Unit,
    onClone: () -> Unit = {},
    onViewAll: () -> Unit = {},
    modifier: Modifier = Modifier,
    projectsViewModel: ProjectsViewModel = viewModel(factory = ProjectsViewModel.Factory),
    fileTreeViewModel: FileTreeViewModel = viewModel(factory = FileTreeViewModel.Factory),
) {
    val projectsState by projectsViewModel.uiState.collectAsStateWithLifecycle()
    val treeState by fileTreeViewModel.uiState.collectAsStateWithLifecycle()
    var showProjectSheet by rememberSaveable { mutableStateOf(false) }
    var showNewSheet by rememberSaveable { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val openProject = rememberProjectOpener(
        onProjectOpened = onProjectOpened,
        fileTreeViewModel = fileTreeViewModel,
        beforeOpen = {
            showProjectSheet = false
            showNewSheet = false
        },
    )

    // The effect collector outlives recompositions; always call the latest opener.
    val latestOpen by rememberUpdatedState(openProject)
    LaunchedEffect(projectsViewModel) {
        projectsViewModel.effects.collect { effect ->
            when (effect) {
                is ProjectsEffect.Message ->
                    scope.launch { snackbarHostState.showSnackbar(effect.text.resolve(context)) }
                is ProjectsEffect.ProjectMoved ->
                    fileTreeViewModel.onEvent(FileTreeEvent.ProjectMoved(effect.old, effect.new))
                is ProjectsEffect.ProjectCreated -> latestOpen(effect.dir)
            }
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

    val closeSearch = {
        searching = false
        query = ""
    }
    BackHandler(enabled = searching) { closeSearch() }

    val filtered = remember(projectsState.recents, query) { projectsState.recents.filter { it.matches(query) } }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (searching) {
                        IconButton(onClick = closeSearch) {
                            Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                        }
                    }
                },
                title = {
                    if (searching) {
                        InlineSearchField(query = query, onQueryChange = { query = it })
                    } else {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 28.sp, fontWeight = FontWeight.SemiBold),
                        )
                    }
                },
                actions = {
                    if (searching) {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(XIcons.Close, contentDescription = stringResource(R.string.action_close_dialog))
                            }
                        }
                    } else {
                        IconButton(onClick = { searching = true }) {
                            Icon(XIcons.Search, contentDescription = stringResource(R.string.home_search))
                        }
                        IconButton(onClick = onClone) {
                            Icon(XIcons.Commit, contentDescription = stringResource(R.string.home_clone))
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(XIcons.Settings, contentDescription = stringResource(R.string.action_settings))
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(Modifier.fillMaxSize().padding(innerPadding)) {
            if (!searching) {
                item(key = "hero") {
                    HeroBanner(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                }
                item(key = "actions") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = { showProjectSheet = true }, modifier = Modifier.weight(1f).height(52.dp)) {
                            Icon(XIcons.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.home_open_project))
                        }
                        FilledTonalIconButton(onClick = { showNewSheet = true }, modifier = Modifier.size(52.dp)) {
                            Icon(XIcons.Add, contentDescription = stringResource(R.string.home_new_project))
                        }
                    }
                }
                item(key = "header") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.home_recent),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                        )
                        if (projectsState.recents.size > HomeRecentLimit) {
                            TextButton(onClick = onViewAll) { Text(stringResource(R.string.home_view_all)) }
                        }
                    }
                }
            }
            val shown = if (searching) filtered else filtered.take(HomeRecentLimit)
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(if (searching) R.string.home_no_matches else R.string.home_no_recents),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
            } else {
                items(items = shown, key = { "r:" + it.file.path }) { item ->
                    ProjectCardRow(
                        item = item,
                        onOpen = { openProject(item.file) },
                        onRemove = { projectsViewModel.onEvent(ProjectsEvent.RemoveRecent(item.file.path)) },
                    )
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

    if (showNewSheet) {
        NewProjectSheet(
            defaultParent = File(Environment.getExternalStorageDirectory(), "Projects"),
            error = projectsState.createError,
            onCreate = { parent, name -> projectsViewModel.onEvent(ProjectsEvent.CreateProject(parent, name)) },
            onDismissError = { projectsViewModel.onEvent(ProjectsEvent.DismissCreateError) },
            onDismiss = {
                showNewSheet = false
                projectsViewModel.onEvent(ProjectsEvent.DismissCreateError)
            },
        )
    }
}

/** Borderless text field for the top bar's search mode. Grabs focus when first shown. */
@Composable
internal fun InlineSearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text(stringResource(R.string.home_search_hint)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
    )
}

/**
 * Gradient hero card. Two soft blobs and a "</>" tile drift slowly (draw-phase only, so the
 * animation never triggers recomposition) behind the compass illustration and headline.
 */
@Composable
private fun HeroBanner(modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val transition = rememberInfiniteTransition(label = "hero")
    val a by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Reverse),
        label = "a",
    )
    val b by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(7500, easing = LinearEasing), RepeatMode.Reverse),
        label = "b",
    )
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .clip(shape)
            .background(Brush.linearGradient(listOf(lerp(surface, primary, 0.28f), lerp(surface, primary, 0.06f))))
            .drawBehind {
                val w = size.width
                val h = size.height
                val drift = 14.dp.toPx()
                drawCircle(primary.copy(alpha = 0.16f), radius = w * 0.26f, center = Offset(w * 0.08f, h * 0.02f + a * drift))
                drawCircle(primary.copy(alpha = 0.12f), radius = w * 0.30f, center = Offset(w * 0.98f - b * drift, h * 0.30f))
            },
    ) {
        Text(
            text = "</>",
            style = MaterialTheme.typography.headlineMedium,
            color = primary.copy(alpha = 0.45f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 28.dp, bottom = 22.dp)
                .graphicsLayer { rotationZ = -15f + a * 8f },
        )
        BuildIllustration(
            Modifier.align(Alignment.CenterEnd).padding(end = 72.dp).size(108.dp),
        )
        Column(Modifier.align(Alignment.CenterStart).padding(start = 24.dp)) {
            Text(
                text = stringResource(R.string.home_hero_kicker),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 3.sp),
                color = primary.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildAnnotatedString {
                    append(stringResource(R.string.home_hero_line1))
                    append("\n")
                    withStyle(SpanStyle(color = primary)) { append(stringResource(R.string.home_hero_line2)) }
                },
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold, lineHeight = 30.sp),
            )
        }
    }
}

/** Static "build your app" illustration: a drafting compass crossed with a pencil. */
@Composable
internal fun BuildIllustration(modifier: Modifier = Modifier) {
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
