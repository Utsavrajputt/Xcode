package com.invictus.xcode.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.invictus.xcode.feature.permission.PermissionScreen
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
    val startDestination = remember { if (storageGranted) Routes.WORKSPACE else Routes.PERMISSION }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
    ) {
        composable(Routes.PERMISSION) { PermissionScreen(onGrantClick = onGrantClick) }
        composable(Routes.WORKSPACE) { WorkspaceScreen() }
    }

    LaunchedEffect(storageGranted) {
        val route = navController.currentBackStackEntry?.destination?.route
        when {
            storageGranted && route == Routes.PERMISSION ->
                navController.navigate(Routes.WORKSPACE) {
                    popUpTo(Routes.PERMISSION) { inclusive = true }
                }
            !storageGranted && route == Routes.WORKSPACE ->
                navController.navigate(Routes.PERMISSION) {
                    popUpTo(Routes.WORKSPACE) { inclusive = true }
                }
        }
    }
}
