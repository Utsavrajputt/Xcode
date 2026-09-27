package com.invictus.xcode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invictus.xcode.ui.MainUiEvent
import com.invictus.xcode.ui.MainViewModel
import com.invictus.xcode.ui.navigation.XcodeNavHost
import com.invictus.xcode.ui.theme.ThemePickerSheet
import com.invictus.xcode.ui.theme.ThemePickerState
import com.invictus.xcode.ui.theme.ThemeTransitionController
import com.invictus.xcode.ui.theme.ThemeTransitionOverlay
import com.invictus.xcode.ui.theme.XcodeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }
    private val transitionController = ThemeTransitionController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as XcodeApp).container
        val storagePermission = container.storagePermission
        val notificationPermission = container.notificationPermission
        val batteryOptimization = container.batteryOptimization

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            // POST_NOTIFICATIONS is a runtime permission on API 33+ only; below that,
            // declaring it in the manifest is enough, so there's nothing to launch.
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) {
                viewModel.onEvent(MainUiEvent.RefreshPermission)
            }

            XcodeTheme {
                Box(Modifier.fillMaxSize()) {
                    XcodeNavHost(
                        storageGranted = uiState.storageGranted,
                        notificationGranted = uiState.notificationGranted,
                        batteryOptimizationDisabled = uiState.batteryOptimizationDisabled,
                        onboardingCompleted = uiState.onboardingCompleted,
                        onGrantStorage = { storagePermission.openSettings(this@MainActivity) },
                        onRequestNotification = {
                            val permission = notificationPermission.manifestPermission
                            if (permission != null && !notificationPermission.isGranted()) {
                                notificationPermissionLauncher.launch(permission)
                            } else {
                                // Already declined once (Android won't show the system prompt
                                // again) or on API < 33 where notifications are just disabled
                                // in system settings -- send the user there instead.
                                notificationPermission.openSettings(this@MainActivity)
                            }
                        },
                        onDisableBattery = { batteryOptimization.requestDisable(this@MainActivity) },
                        onFinishOnboarding = { viewModel.onEvent(MainUiEvent.CompleteOnboarding) },
                    )
                    ThemeTransitionOverlay(transitionController)
                    if (ThemePickerState.visible) {
                        ThemePickerSheet(
                            onDismiss = { ThemePickerState.visible = false },
                            transitionController = transitionController,
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // User may have just flipped a switch in system settings (or revoked one).
        viewModel.onEvent(MainUiEvent.RefreshPermission)
    }
}
