package com.invictus.xcode.feature.search

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Code search -> workspace "Locate in file tree" request. Workspace screen isse collect
 * karke existing FileTreeEvent.Reveal / FileTreeEffect.ScrollTo pipeline chala deta hai.
 */
object SearchBus {
    private val _revealRequest = MutableStateFlow<File?>(null)
    val revealRequest: StateFlow<File?> = _revealRequest

    fun requestReveal(file: File) {
        _revealRequest.value = file
    }

    fun consumeReveal() {
        _revealRequest.value = null
    }
}
