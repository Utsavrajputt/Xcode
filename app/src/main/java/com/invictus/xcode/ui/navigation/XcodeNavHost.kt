package com.invictus.xcode.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invictus.xcode.feature.editor.EditorEvent
import com.invictus.xcode.feature.editor.EditorScreen
import com.invictus.xcode.feature.editor.EditorViewModel
import com.invictus.xcode.feature.permission.PermissionScreen
import com.invictus.xcode.feature.preview.MediaPreviewScreen
import com.invictus.xcode.feature.settings.SettingsScreen
import com.invictus.xcode.feature.workspace.WorkspaceScreen

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
    val startDestination = remember { if (storageGranted) Routes.WORKSPACE else Routes.PERMISSION }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.PERMISSION) { PermissionScreen(onGrantClick = onGrantClick) }
        composable(Routes.WORKSPACE) {
            WorkspaceScreen(
                onOpenFile = { editorViewModel.onEvent(EditorEvent.Open(it)) },
                externalMessages = editorViewModel.messages,
                onProjectRoot = { editorViewModel.onProjectOpened(it.path) },
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
            SettingsScreen(
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
    }

    LaunchedEffect(editorViewModel) {
        editorViewModel.openMediaPreview.collect {
            navController.navigate(Routes.MEDIA_PREVIEW) { launchSingleTop = true }
        }
    }

    LaunchedEffect(editorViewModel) {
        editorViewModel.showEditor.collect {
            if (navController.currentBackStackEntry?.destination?.route == Routes.WORKSPACE) {
                navController.navigate(Routes.EDITOR) { launchSingleTop = true }
            }
        }
    }

    LaunchedEffect(storageGranted) {
        val route = navController.currentBackStackEntry?.destination?.route
        when {
            storageGranted && route == Routes.PERMISSION ->
                navController.navigate(Routes.WORKSPACE) {
                    popUpTo(Routes.PERMISSION) { inclusive = true }
                }
            !storageGranted && (route == Routes.WORKSPACE || route == Routes.EDITOR) ->
                navController.navigate(Routes.PERMISSION) {
                    popUpTo(Routes.WORKSPACE) { inclusive = true }
                }
        }
    }
}
