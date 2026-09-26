package com.invictus.xcode.feature.git

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.xcode.R
import com.invictus.xcode.XcodeApp
import com.invictus.xcode.core.git.GitCloner
import com.invictus.xcode.core.git.GitCredential
import com.invictus.xcode.core.git.GitResult
import com.invictus.xcode.core.git.normalizeHost
import com.invictus.xcode.core.git.model.GitErrorDetails
import com.invictus.xcode.core.security.GitCredentialStore
import com.invictus.xcode.feature.workspace.UiText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class GitCloneViewModel(
    private val credentialStore: GitCredentialStore,
    private val defaultParent: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    data class UiState(
        val url: String = "",
        val username: String = DEFAULT_USERNAME,
        val token: String = "",
        val parentPath: String = "",
        val folderName: String = "",
        val branch: String = "",
        val cloning: Boolean = false,
        val progressTask: String = "",
        val error: GitErrorDetails? = null,
    )

    sealed interface Effect {
        data class Message(val text: UiText) : Effect
        data object NavigateBack : Effect
    }

    private val _uiState = MutableStateFlow(UiState(parentPath = defaultParent.absolutePath))
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun onEvent(event: GitCloneEvent) {
        when (event) {
            is GitCloneEvent.UrlChange -> _uiState.update {
                it.copy(
                    url = event.value,
                    folderName = if (it.folderName.isBlank()) deriveFolder(event.value) else it.folderName,
                )
            }
            is GitCloneEvent.UsernameChange -> _uiState.update { it.copy(username = event.value) }
            is GitCloneEvent.TokenChange -> _uiState.update { it.copy(token = event.value) }
            is GitCloneEvent.ParentChange -> _uiState.update { it.copy(parentPath = event.value) }
            is GitCloneEvent.FolderChange -> _uiState.update { it.copy(folderName = event.value) }
            is GitCloneEvent.BranchChange -> _uiState.update { it.copy(branch = event.value) }
            GitCloneEvent.Start -> startClone()
            GitCloneEvent.DismissError -> _uiState.update { it.copy(error = null) }
        }
    }

    private fun deriveFolder(url: String): String =
        url.trim().removeSuffix("/").substringAfterLast('/').removeSuffix(".git")

    private fun startClone() {
        val s = _uiState.value
        val url = s.url.trim()
        val folder = s.folderName.trim()
        when {
            !url.startsWith("https://") && !url.startsWith("http://") ->
                return message(R.string.clone_invalid_url)
            folder.isEmpty() -> return message(R.string.clone_empty_folder)
        }
        val target = File(s.parentPath.trim(), folder)
        if (target.exists()) return message(R.string.clone_folder_exists, folder)
        viewModelScope.launch(io) {
            _uiState.update { it.copy(cloning = true, progressTask = "") }
            val result = GitCloner.clone(
                url = url,
                username = s.username.trim().ifBlank { DEFAULT_USERNAME },
                token = s.token.trim(),
                directory = target,
                branch = s.branch.trim().ifBlank { null },
            ) { p -> _uiState.update { it.copy(progressTask = p.task) } }
            when (result) {
                is GitResult.Ok -> {
                    if (s.token.isNotBlank()) {
                        credentialStore.put(
                            GitCredential(
                                normalizeHost(url),
                                s.username.trim().ifBlank { DEFAULT_USERNAME },
                                s.token.trim(),
                            ),
                        )
                    }
                    message(R.string.clone_done, target.absolutePath)
                    _effects.send(Effect.NavigateBack)
                }
                is GitResult.Err -> {
                    // Don't leave a half-downloaded repo behind.
                    if (target.exists()) target.deleteRecursively()
                    _uiState.update { it.copy(error = result.error) }
                }
            }
            _uiState.update { it.copy(cloning = false, progressTask = "") }
        }
    }

    private fun message(res: Int, vararg args: Any) {
        _effects.trySend(Effect.Message(UiText(res, args.toList())))
    }

    companion object {
        private const val DEFAULT_USERNAME = "x-access-token"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as XcodeApp
                GitCloneViewModel(
                    credentialStore = app.container.gitCredentialStore,
                    defaultParent = Environment.getExternalStorageDirectory(),
                )
            }
        }
    }
}
