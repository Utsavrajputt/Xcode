package com.invictus.xcode.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.invictus.xcode.feature.editor.EditorEvent
import com.invictus.xcode.feature.editor.EditorScreen
import com.invictus.xcode.feature.editor.EditorViewModel
import com.invictus.xcode.feature.git.GitCloneScreen
import com.invictus.xcode.feature.git.GitScreen
import com.invictus.xcode.feature.home.HomeScreen
import com.invictus.xcode.feature.permission.PermissionScreen
import com.invictus.xcode.feature.preview.MediaPreviewScreen
import com.invictus.xcode.feature.settings.SettingsAppearanceScreen
import com.invictus.xcode.feature.settings.SettingsBehaviorScreen
import com.invictus.xcode.feature.settings.SettingsEditingScreen
import com.invictus.xcode.feature.settings.SettingsRootScreen
import com.invictus.xcode.feature.workspace.WorkspaceScreen

/** Shared-axis-X (slide + fade) screen transition, tween-based — cheap on low-end devices. */
private const val NavMotionDurationMs = 260
private const val NavFadeDurationMs = 180
private const val NavSlideFraction = 4

/**
 * Permission gate + app screens. Starts on the permission screen when All files
 * access is missing, and moves between the two as the permission is granted/revoked.
 */
@Composable
fun XcodeNavHost(
    storageGranted: Boolean,
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    // Activity-scoped on purpose: open tabs outlive the trip back to the file tree.
    val editorViewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory)
    val startDestination = remember { if (storageGranted) Routes.HOME else Routes.PERMISSION }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = {
            slideInHorizontally(tween(NavMotionDurationMs)) { it / NavSlideFraction } +
                fadeIn(tween(NavFadeDurationMs))
        },
        exitTransition = {
            slideOutHorizontally(tween(NavMotionDurationMs)) { -it / NavSlideFraction } +
                fadeOut(tween(NavFadeDurationMs))
        },
        popEnterTransition = {
            slideInHorizontally(tween(NavMotionDurationMs)) { -it / NavSlideFraction } +
                fadeIn(tween(NavFadeDurationMs))
        },
        popExitTransition = {
            slideOutHorizontally(tween(NavMotionDurationMs)) { it / NavSlideFraction } +
                fadeOut(tween(NavFadeDurationMs))
        },
    ) {
        composable(Routes.PERMISSION) { PermissionScreen(onGrantClick = onGrantClick) }
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onProjectOpened = { navController.navigate(Routes.WORKSPACE) },
                onClone = { navController.navigate(Routes.GIT_CLONE) },
            )
        }
        composable(Routes.WORKSPACE) {
            WorkspaceScreen(
                onOpenFile = { editorViewModel.onEvent(EditorEvent.Open(it)) },
                externalMessages = editorViewModel.messages,
                onProjectRoot = { editorViewModel.onProjectOpened(it.path) },
                onOpenGit = { root -> navController.navigate(Routes.git(root.path)) },
                onClone = { navController.navigate(Routes.GIT_CLONE) },
            )
        }
        composable(Routes.EDITOR) {
            EditorScreen(
                viewModel = editorViewModel,
                onBack = {
                    if (navController.currentBackStackEntry?.destination?.route == Routes.EDITOR) {
                        navController.popBackStack()
                    }
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsRootScreen(
                onBack = { navController.popBackStack() },
                onOpenAppearance = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                onOpenEditing = { navController.navigate(Routes.SETTINGS_EDITING) },
                onOpenBehavior = { navController.navigate(Routes.SETTINGS_BEHAVIOR) },
            )
        }
        composable(Routes.SETTINGS_APPEARANCE) {
            SettingsAppearanceScreen(
                viewModel = editorViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS_EDITING) {
            SettingsEditingScreen(
                viewModel = editorViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS_BEHAVIOR) {
            SettingsBehaviorScreen(
                viewModel = editorViewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.MEDIA_PREVIEW) {
            val file by editorViewModel.mediaPreviewFile.collectAsStateWithLifecycle()
            file?.let {
                MediaPreviewScreen(
                    file = it,
                    onBack = {
                        if (navController.currentBackStackEntry?.destination?.route == Routes.MEDIA_PREVIEW) {
                            navController.popBackStack()
                        }
                    },
                )
            }
        }
        composable(Routes.GIT) { entry ->
            val path = entry.arguments?.getString("projectPath")
            if (path != null) {
                GitScreen(projectPath = path, onBack = { navController.popBackStack() })
            }
        }
        composable(Routes.GIT_CLONE) {
            GitCloneScreen(onBack = { navController.popBackStack() })
        }
    }

    LaunchedEffect(editorViewModel) {
        editorViewModel.showEditor.collect {
            if (navController.currentBackStackEntry?.destination?.route == Routes.WORKSPACE) {
                navController.navigate(Routes.EDITOR) { launchSingleTop = true }
            }
        }
    }

    LaunchedEffect(editorViewModel) {
        editorViewModel.openMediaPreview.collect {
            navController.navigate(Routes.MEDIA_PREVIEW) { launchSingleTop = true }
        }
    }

    LaunchedEffect(storageGranted) {
        val route = navController.currentBackStackEntry?.destination?.route
        when {
            storageGranted && route == Routes.PERMISSION ->
                navController.navigate(Routes.HOME) {
                    popUpTo(Routes.PERMISSION) { inclusive = true }
                }
            !storageGranted && (route == Routes.HOME || route == Routes.WORKSPACE || route == Routes.EDITOR) ->
                navController.navigate(Routes.PERMISSION) {
                    popUpTo(Routes.HOME) { inclusive = true }
                }
        }
    }
}
