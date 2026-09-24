package com.invictus.xcode.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.xcode.XcodeApp
import com.invictus.xcode.core.fs.StoragePermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class MainUiState(val storageGranted: Boolean)

sealed interface MainUiEvent {
    /** Re-check All files access (call on resume, e.g. after returning from system settings). */
    data object RefreshPermission : MainUiEvent
}

class MainViewModel(private val storagePermission: StoragePermission) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState(storageGranted = storagePermission.isGranted()))
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun onEvent(event: MainUiEvent) {
        when (event) {
            MainUiEvent.RefreshPermission ->
                _uiState.update { it.copy(storageGranted = storagePermission.isGranted()) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as XcodeApp
                MainViewModel(app.container.storagePermission)
            }
        }
    }
}
