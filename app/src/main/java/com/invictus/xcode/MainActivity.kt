package com.invictus.xcode

import android.content.Intent
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.invictus.xcode.core.external.ExternalFileResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

    /** File from an "Open with Xcode" intent, waiting for the nav host to open it in the editor. */
    private var externalFile by mutableStateOf<File?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (savedInstanceState == null) handleOpenIntent(intent)

        val container = (application as XcodeApp).container
        val storagePermission = container.storagePermission
        val notificationPermission = container.notificationPermission

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
                        onFinishOnboarding = { viewModel.onEvent(MainUiEvent.CompleteOnboarding) },
                        externalFile = externalFile,
                        onExternalFileHandled = { externalFile = null },
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOpenIntent(intent)
    }

    /** ACTION_VIEW / ACTION_EDIT from a file manager or another app -> resolve off-main, then hand to the nav host. */
    private fun handleOpenIntent(intent: Intent?) {
        if (!ExternalFileResolver.isOpenIntent(intent)) return
        val open = intent ?: return
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { ExternalFileResolver.resolve(applicationContext, open) }
            if (file != null) externalFile = file
        }
    }

    override fun onResume() {
        super.onResume()
        // User may have just flipped a switch in system settings (or revoked one).
        viewModel.onEvent(MainUiEvent.RefreshPermission)
    }
}
