package com.invictus.xcode.feature.home

import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.xcode.R
import com.invictus.xcode.feature.project.OpenProjectSheet
import com.invictus.xcode.feature.project.ProjectsEffect
import com.invictus.xcode.feature.project.ProjectsEvent
import com.invictus.xcode.feature.project.ProjectsViewModel
import com.invictus.xcode.feature.workspace.FileTreeEvent
import com.invictus.xcode.feature.workspace.FileTreeViewModel
import com.invictus.xcode.ui.icons.XIcons
import com.invictus.xcode.ui.theme.BrandFontFamily
import kotlinx.coroutines.launch
import java.io.File

/** Recents shown on Home before "View All" takes over. */
private const val HomeRecentLimit = 5

/** The search icon only appears once there are more recents than this. */
private const val HomeSearchMinProjects = 9

/** Left/right edge shared by the title, hero, section label and project cards. */
private val HomeEdge = 16.dp

/** Vertical gap between the hero, the action buttons and the Recent Projects label. */
private val HomeSectionGap = 20.dp

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
    var searchRequested by rememberSaveable { mutableStateOf(false) }
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
                is ProjectsEffect.ProjectDeleted ->
                    fileTreeViewModel.onEvent(FileTreeEvent.ProjectDeleted(effect.dir))
                is ProjectsEffect.ProjectCreated -> latestOpen(effect.dir)
            }
        }
    }

    val copyPath = rememberCopyPath(snackbarHostState)

    // Search is only worth a button once the list is long enough to need it; if the list shrinks
    // below that while searching, the search bar closes with it.
    val canSearch = projectsState.recents.size > HomeSearchMinProjects
    val searching = searchRequested && canSearch
    val closeSearch = {
        searchRequested = false
        query = ""
    }
    BackHandler(enabled = searching) { closeSearch() }

    val filtered = remember(projectsState.recents, query) { projectsState.recents.filter { it.matches(query) } }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                expandedHeight = 88.dp,
                // Same colour as the page, also while the list scrolls under it: no tonal seam.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
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
                        Column {
                            val brand = MaterialTheme.colorScheme
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontFamily = BrandFontFamily,
                                    fontSize = 34.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = (-0.8).sp,
                                    brush = Brush.linearGradient(listOf(brand.onSurface, brand.primary)),
                                ),
                            )
                            Text(
                                text = stringResource(R.string.home_tagline),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 9.sp,
                                    letterSpacing = 3.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
                        if (canSearch) {
                            HeaderIconButton(onClick = { searchRequested = true }) {
                                Icon(XIcons.Search, contentDescription = stringResource(R.string.home_search))
                            }
                            Spacer(Modifier.width(10.dp))
                        }
                        HeaderIconButton(onClick = onClone) {
                            Icon(XIcons.Clone, contentDescription = stringResource(R.string.home_clone))
                        }
                        Spacer(Modifier.width(10.dp))
                        HeaderIconButton(onClick = onOpenSettings) {
                            Icon(XIcons.Settings, contentDescription = stringResource(R.string.action_settings))
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            if (!searching) {
                item(key = "hero") {
                    HeroBanner(Modifier.padding(horizontal = HomeEdge))
                }
                item(key = "actions") {
                    Row(
                        // The label below carries 8dp of its own vertical inset, so take that off the
                        // bottom: hero->buttons and buttons->"Recent Projects" both measure HomeSectionGap.
                        Modifier.fillMaxWidth().padding(top = HomeSectionGap, bottom = HomeSectionGap - 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = { showProjectSheet = true }, shape = RoundedCornerShape(14.dp)) {
                            Icon(XIcons.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.home_open_project))
                        }
                        FilledTonalIconButton(onClick = { showNewSheet = true }, shape = RoundedCornerShape(14.dp)) {
                            Icon(XIcons.Add, contentDescription = stringResource(R.string.home_new_project))
                        }
                    }
                }
                item(key = "header") {
                    Row(
                        Modifier.fillMaxWidth().padding(start = HomeEdge, end = 12.dp),
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
                        onTogglePin = { projectsViewModel.onEvent(ProjectsEvent.TogglePin(item.file.path, !item.pinned)) },
                        onRename = { projectsViewModel.onEvent(ProjectsEvent.StartRename(item.file)) },
                        onDelete = { projectsViewModel.onEvent(ProjectsEvent.StartDelete(item.file)) },
                        onInfo = { projectsViewModel.onEvent(ProjectsEvent.ShowInfo(item.file)) },
                        onCopyPath = { copyPath(item.file) },
                    )
                }
            }
        }
    }

    // The Open Project sheet draws its own rename dialog, so Home's copy steps aside while it is up.
    ProjectActionDialogs(
        state = projectsState,
        onEvent = projectsViewModel::onEvent,
        showRename = !showProjectSheet,
    )

    if (showProjectSheet) {
        OpenProjectSheet(
            state = projectsState,
            currentRoot = treeState.root,
            snackbarHostState = snackbarHostState,
            onEvent = projectsViewModel::onEvent,
            onOpen = openProject,
            onCopyPath = copyPath,
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

/** Round tonal 44dp button used for the header's search / settings actions. */
@Composable
private fun HeaderIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        content = content,
    )
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
 * Welcome hero: dark gradient card with drifting glow blobs, three floating glass panels with a
 * glowing "</>" on the front one, and twinkling dot grids. Everything is drawn in one Canvas and
 * the animated values are only read in the draw phase, so it never triggers recomposition.
 * Sizes scale with the card width (designed at 328dp).
 */
@Composable
private fun HeroBanner(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "hero")
    val a by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "a",
    )
    val b by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "b",
    )
    val shape = RoundedCornerShape(28.dp)

    // GitHub-profile style "typing" intro: kicker, then line 1 and 2, then line 3 cycles words.
    val kickerFull = stringResource(R.string.home_hero_kicker)
    val line1Full = stringResource(R.string.home_hero_line1)
    val line2Full = stringResource(R.string.home_hero_line2)
    val words = listOf(
        stringResource(R.string.home_hero_line3),
        stringResource(R.string.home_hero_word2),
        stringResource(R.string.home_hero_word3),
        stringResource(R.string.home_hero_word4),
    )
    var kickerLen by remember { mutableIntStateOf(0) }
    var line1Len by remember { mutableIntStateOf(0) }
    var line2Len by remember { mutableIntStateOf(0) }
    var wordText by remember { mutableStateOf("") }
    var activeLine by remember { mutableIntStateOf(0) } // 0 kicker, 1/2/3 = heading lines
    var cursorOn by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        while (true) { delay(480); cursorOn = !cursorOn }
    }
    LaunchedEffect(kickerFull, line1Full, line2Full, words) {
        delay(350)
        for (i in 1..kickerFull.length) { kickerLen = i; delay(60) }
        delay(220)
        activeLine = 1
        for (i in 1..line1Full.length) { line1Len = i; delay(95) }
        delay(120)
        activeLine = 2
        for (i in 1..line2Full.length) { line2Len = i; delay(80) }
        delay(120)
        activeLine = 3
        var idx = 0
        while (true) {
            val word = words[idx % words.size]
            for (i in 1..word.length) { wordText = word.take(i); delay(90) }
            delay(1700)
            for (i in word.length - 1 downTo 0) { wordText = word.take(i); delay(45) }
            delay(250)
            idx++
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.57f)
            .clip(shape)
            // No outline: the card fades out of the page instead of sitting on it like a sticker.
            .background(Brush.linearGradient(listOf(Color(0xFF030716), Color(0xFF071445), Color(0xFF040B26)))),
    ) {
        val s = maxWidth.value / 328f
        Canvas(Modifier.fillMaxSize()) { drawHeroArt(a, b) }
        Column(Modifier.align(Alignment.CenterStart).padding(start = (26 * s).dp)) {
            Text(
                text = buildAnnotatedString {
                    append(kickerFull.take(kickerLen))
                    // Invisible when off / inactive (keeps the line height and width stable).
                    withStyle(SpanStyle(color = if (activeLine == 0 && cursorOn) Color(0xFFB4EAFF) else Color.Transparent)) {
                        append("|")
                    }
                },
                style = TextStyle(
                    brush = Brush.horizontalGradient(listOf(Color(0xFF8DB0FF), Color(0xFFB4EAFF))),
                    fontSize = (10 * s).sp,
                    letterSpacing = (3 * s).sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
            Spacer(Modifier.height((9 * s).dp))
            Box(
                Modifier.size(width = (22 * s).dp, height = (3 * s).dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color(0xFF5B6CFF)),
            )
            Spacer(Modifier.height((8 * s).dp))
            Text(
                text = buildAnnotatedString {
                    val cursor = SpanStyle(color = Color(0xFF7FEBFF))
                    val hidden = SpanStyle(color = Color.Transparent)
                    fun cursorFor(line: Int) = if (activeLine == line && cursorOn) cursor else hidden
                    append(line1Full.take(line1Len))
                    withStyle(cursorFor(1)) { append("|") }
                    append("\n")
                    append(line2Full.take(line2Len))
                    withStyle(cursorFor(2)) { append("|") }
                    append("\n")
                    withStyle(SpanStyle(brush = Brush.horizontalGradient(listOf(Color(0xFF2F9BFF), Color(0xFF7FEBFF))))) {
                        append(wordText)
                    }
                    withStyle(cursorFor(3)) { append("|") }
                },
                fontSize = (24 * s).sp,
                lineHeight = (26 * s).sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height((8 * s).dp))
            Text(
                text = stringResource(R.string.home_hero_sub),
                fontSize = (10 * s).sp,
                lineHeight = (14 * s).sp,
                color = Color(0xFFB8C4F0),
            )
        }
    }
}

private fun DrawScope.drawHeroArt(a: Float, b: Float) {
    val w = size.width
    val h = size.height
    val drift = 12.dp.toPx()

    fun blob(cx: Float, cy: Float, r: Float, c: Color, alpha: Float) {
        val center = Offset(cx, cy)
        drawCircle(
            brush = Brush.radialGradient(listOf(c.copy(alpha = alpha), c.copy(alpha = alpha * 0.3f)), center = center, radius = r),
            radius = r,
            center = center,
        )
    }
    blob(w * 0.02f, h * 0.05f + a * drift, w * 0.26f, Color(0xFF1F4DFF), 0.55f)
    blob(w * 0.66f - b * drift, h * -0.04f, w * 0.20f, Color(0xFF2B5CFF), 0.45f)
    blob(w * 0.88f, h * 1.0f - a * drift, w * 0.24f, Color(0xFF2E6BFF), 0.50f)
    blob(b * drift * 0.5f, h * 1.02f, w * 0.17f, Color(0xFF2A4BFF), 0.45f)
    blob(w * 1.02f, h * 0.62f + b * drift, w * 0.13f, Color(0xFF8B5CFF), 0.50f)

    // Twinkling dot grids (top right, bottom left).
    val gap = 9.dp.toPx()
    for (r in 0..4) for (c in 0..4) {
        val on = (r + c) % 2 == 0
        val alpha = 0.12f + 0.28f * (if (on) a else 1f - a)
        val dot = Color(0xFF7FA0FF).copy(alpha = alpha)
        drawCircle(dot, 1.2.dp.toPx(), Offset(w * 0.86f + c * gap, h * 0.06f + r * gap))
        drawCircle(dot, 1.2.dp.toPx(), Offset(w * 0.14f + c * gap, h * 0.80f + r * gap))
    }

    // Three stacked glass panels, each bobbing on its own phase.
    val panelW = w * 0.27f
    val rise = h * 0.20f
    val panelH = h * 0.36f
    val bob = 6.dp.toPx()
    val corner = 9.dp.toPx()
    val fills = listOf(
        listOf(Color(0x885CC8FF), Color(0x883A6BFF)),
        listOf(Color(0x993F6BFF), Color(0x992F46E8)),
        listOf(Color(0xE63B5BFF), Color(0xE67A4DFF)),
    )
    val edges = listOf(Color(0xCC8FE3FF), Color(0x887FA8FF), Color(0xAA9FB4FF))
    var frontX = 0f
    var frontY = 0f
    for (k in 0..2) {
        val phase = when (k) { 0 -> b; 1 -> a; else -> 1f - b }
        val x0 = w * (0.51f + 0.055f * k)
        val y0 = h * (0.36f + 0.04f * k) + (phase - 0.5f) * 2f * bob * (0.6f + 0.2f * k)
        val quad = listOf(
            Offset(x0, y0),
            Offset(x0 + panelW, y0 - rise),
            Offset(x0 + panelW, y0 - rise + panelH),
            Offset(x0, y0 + panelH),
        )
        val path = roundedPolygon(quad, corner)
        drawPath(
            path,
            Brush.linearGradient(fills[k], start = Offset(x0, y0 - rise), end = Offset(x0 + panelW, y0 + panelH)),
        )
        drawPath(path, edges[k], style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round))
        if (k == 2) {
            frontX = x0
            frontY = y0
        }
    }

    // Window dots along the front panel's top edge.
    for (i in 0..2) {
        val t = 0.68f + 0.10f * i
        drawCircle(
            Color(0xCCBFD8FF),
            radius = 3.dp.toPx(),
            center = Offset(frontX + panelW * t, frontY - rise * t + h * 0.05f),
        )
    }

    // Inset screen on the front panel, then the thin glowing </> inside it.
    val t0 = 0.07f
    val top = h * 0.075f
    val bottom = h * 0.05f
    val tl = Offset(frontX + panelW * t0, frontY - rise * t0 + top)
    val tr = Offset(frontX + panelW * (1 - t0), frontY - rise * (1 - t0) + top)
    val br = Offset(tr.x, frontY - rise * (1 - t0) + panelH - bottom)
    val bl = Offset(tl.x, frontY - rise * t0 + panelH - bottom)
    val screen = roundedPolygon(listOf(tl, tr, br, bl), 5.dp.toPx())
    drawPath(screen, Color(0x33081250))
    drawPath(screen, Color(0x445F8CFF), style = Stroke(width = 1.dp.toPx(), join = StrokeJoin.Round))

    val cx = (tl.x + tr.x) / 2f
    val cy = (tl.y + tr.y + br.y + bl.y) / 4f
    val u = w * 0.02f
    val slope = -rise / panelW // same lean as the panel's top edge
    fun pt(x: Float, y: Float) = Offset(cx + x * u, cy + y * u + x * u * slope)
    val glyph = Path().apply {
        val a1 = pt(-1.4f, -1.9f); val a2 = pt(-3.3f, 0f); val a3 = pt(-1.4f, 1.9f)
        moveTo(a1.x, a1.y); lineTo(a2.x, a2.y); lineTo(a3.x, a3.y)
        val s1 = pt(0.6f, -2.6f); val s2 = pt(-0.6f, 2.6f)
        moveTo(s1.x, s1.y); lineTo(s2.x, s2.y)
        val c1 = pt(1.4f, -1.9f); val c2 = pt(3.3f, 0f); val c3 = pt(1.4f, 1.9f)
        moveTo(c1.x, c1.y); lineTo(c2.x, c2.y); lineTo(c3.x, c3.y)
    }
    // Soft glow (pulses with [b]) under a thin bright stroke.
    drawPath(glyph, Color(0xFF4FF0FF).copy(alpha = 0.10f + 0.15f * b), style = Stroke(width = w * 0.02f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(glyph, Color(0xFF5FF3FF), style = Stroke(width = w * 0.0085f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Polygon with every corner rounded by [radius] (quadratic curve through the original vertex). */
private fun roundedPolygon(pts: List<Offset>, radius: Float): Path {
    val path = Path()
    val n = pts.size
    for (i in 0 until n) {
        val prev = pts[(i - 1 + n) % n]
        val cur = pts[i]
        val next = pts[(i + 1) % n]
        val toPrev = prev - cur
        val toNext = next - cur
        val start = cur + toPrev * (radius / toPrev.getDistance())
        val end = cur + toNext * (radius / toNext.getDistance())
        if (i == 0) path.moveTo(start.x, start.y) else path.lineTo(start.x, start.y)
        path.quadraticBezierTo(cur.x, cur.y, end.x, end.y)
    }
    path.close()
    return path
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
