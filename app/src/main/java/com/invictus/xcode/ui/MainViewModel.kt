package com.invictus.xcode.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.xcode.XcodeApp
import com.invictus.xcode.core.fs.StoragePermission
import com.invictus.xcode.core.permission.BatteryOptimization
import com.invictus.xcode.core.permission.NotificationPermission
import com.invictus.xcode.core.permission.OnboardingPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

data class MainUiState(
    val storageGranted: Boolean,
    val notificationGranted: Boolean,
    val batteryOptimizationDisabled: Boolean,
    val onboardingCompleted: Boolean,
)

sealed interface MainUiEvent {
    /** Re-check all permission states (call on resume, e.g. after returning from system settings). */
    data object RefreshPermission : MainUiEvent

    /** Onboarding finished or skipped -- persists so it never shows again. */
    data object CompleteOnboarding : MainUiEvent
}

class MainViewModel(
    private val storagePermission: StoragePermission,
    private val notificationPermission: NotificationPermission,
    private val batteryOptimization: BatteryOptimization,
    private val onboardingPrefs: OnboardingPrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MainUiState(
            storageGranted = storagePermission.isGranted(),
            notificationGranted = notificationPermission.isGranted(),
            batteryOptimizationDisabled = batteryOptimization.isDisabled(),
            // Read synchronously (tiny local DataStore, one boolean) so a returning user
            // whose onboarding is already done never sees a one-frame onboarding flash.
            onboardingCompleted = runBlocking { onboardingPrefs.isCompleted() },
        ),
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun onEvent(event: MainUiEvent) {
        when (event) {
            MainUiEvent.RefreshPermission -> _uiState.update {
                it.copy(
                    storageGranted = storagePermission.isGranted(),
                    notificationGranted = notificationPermission.isGranted(),
                    batteryOptimizationDisabled = batteryOptimization.isDisabled(),
                )
            }
            MainUiEvent.CompleteOnboarding -> {
                _uiState.update { it.copy(onboardingCompleted = true) }
                viewModelScope.launch { onboardingPrefs.setCompleted(true) }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as XcodeApp
                MainViewModel(
                    storagePermission = app.container.storagePermission,
                    notificationPermission = app.container.notificationPermission,
                    batteryOptimization = app.container.batteryOptimization,
                    onboardingPrefs = app.container.onboardingPrefs,
                )
            }
        }
    }
}
