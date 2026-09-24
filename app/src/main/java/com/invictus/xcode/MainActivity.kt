package com.invictus.xcode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invictus.xcode.ui.MainUiEvent
import com.invictus.xcode.ui.MainViewModel
import com.invictus.xcode.ui.navigation.XcodeNavHost
import com.invictus.xcode.ui.theme.XcodeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val storagePermission = (application as XcodeApp).container.storagePermission

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            XcodeTheme {
                XcodeNavHost(
                    storageGranted = uiState.storageGranted,
                    onGrantClick = { storagePermission.openSettings(this) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // User may have just flipped the switch in system settings (or revoked it).
        viewModel.onEvent(MainUiEvent.RefreshPermission)
    }
}
