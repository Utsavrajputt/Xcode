package com.invictus.xcode.feature.git

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.invictus.xcode.R
import com.invictus.xcode.XcodeApp
import com.invictus.xcode.core.git.GitCredential
import com.invictus.xcode.core.git.GitOnboardingPrefs
import com.invictus.xcode.core.git.GitResult
import com.invictus.xcode.core.git.GitSession
import com.invictus.xcode.core.git.GitTokenCredentialsProvider
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

/**
 * M9 guided onboarding: a 5-step wizard (init -> identity -> remote -> first commit ->
 * push+upstream). Every step is skippable; the wizard only appears when something is
 * actually missing (plan 3.4). All JGit work goes through [GitSession] on [io].
 */
class GitOnboardingViewModel(
    private val projectPath: String,
    private val credentialStore: GitCredentialStore,
    private val onboardingPrefs: GitOnboardingPrefs,
    globalIdentityFile: File,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    /** Ordered wizard steps; the visible list is computed from the repo state. */
    enum class Step { INIT_REPO, IDENTITY, REMOTE, FIRST_COMMIT, PUSH }

    data class UiState(
        val loading: Boolean = true,
        val steps: List<Step> = emptyList(),
        val stepIndex: Int = 0,
        val working: Boolean = false,
        // step: INIT_REPO
        val repoInitialized: Boolean = false,
        // step: IDENTITY
        val identityName: String = "",
        val identityEmail: String = "",
        /** false = global identity file (default), true = only this repository. */
        val identityLocal: Boolean = false,
        // step: REMOTE
        val remoteUrl: String = "",
        val remoteToken: String = "",
        // step: FIRST_COMMIT
        val initialMessage: String = "",
        // step: PUSH
        val pushRemote: String = "origin",
        // common
        val dontAskAgain: Boolean = false,
        val error: GitErrorDetails? = null,
    ) {
        val currentStep: Step? get() = steps.getOrNull(stepIndex)
        val isLastStep: Boolean get() = stepIndex >= steps.lastIndex
    }

    sealed interface Effect {
        data object Close : Effect
        data class Message(val text: UiText) : Effect
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _effects = Channel<Effect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    /**
     * Built up-front but harmless before `git init`: [GitSession.repository] is lazy,
     * so nothing touches JGit until the repo actually exists.
     */
    private val session = GitSession(File(projectPath), globalIdentityFile, io)

    init {
        detectStartStep()
    }

    // ---- detection -----------------------------------------------------------

    /** Find the first missing thing and build the visible step list from it. */
    private fun detectStartStep() {
        viewModelScope.launch(io) {
            val hasGit = File(projectPath, ".git").isDirectory
            val steps = mutableListOf<Step>()

            if (!hasGit) {
                steps += Step.INIT_REPO
                steps += Step.IDENTITY
                steps += Step.REMOTE
                steps += Step.FIRST_COMMIT
            } else {
                val refreshed = session.refresh()
                val snapshot = (refreshed as? GitResult.Ok)?.value?.first
                val status = (refreshed as? GitResult.Ok)?.value?.second
                val identity = (session.getIdentity(local = true) as? GitResult.Ok)?.value
                    ?: (session.getIdentity(local = false) as? GitResult.Ok)?.value
                val remotes = (session.listRemotes() as? GitResult.Ok)?.value.orEmpty()

                if (identity == null) {
                    steps += Step.IDENTITY
                    steps += Step.REMOTE
                }
                if (remotes.isEmpty()) steps += Step.REMOTE
                if (snapshot?.hasCommits != true && status?.isClean == false) {
                    steps += Step.FIRST_COMMIT
                }
                if (remotes.isNotEmpty() && snapshot?.hasCommits == true && snapshot.trackingInfo == null) {
                    steps += Step.PUSH
                }
                if (identity != null) {
                    _uiState.update {
                        it.copy(identityName = identity.name, identityEmail = identity.email)
                    }
                }
            }

            if (steps.isEmpty()) {
                // Nothing missing: never pester this project again.
                onboardingPrefs.markDone(projectPath)
                _effects.trySend(Effect.Close)
            } else {
                // De-duplicate while preserving order (IDENTITY + REMOTE both missing etc.).
                _uiState.update {
                    it.copy(loading = false, steps = steps.distinct())
                }
            }
        }
    }

    // ---- events ---------------------------------------------------------------

    fun onEvent(event: GitOnboardingEvent) {
        when (event) {
            is GitOnboardingEvent.NameChange ->
                _uiState.update { it.copy(identityName = event.value) }
            is GitOnboardingEvent.EmailChange ->
                _uiState.update { it.copy(identityEmail = event.value) }
            is GitOnboardingEvent.IdentityScopeChange ->
                _uiState.update { it.copy(identityLocal = event.local) }
            is GitOnboardingEvent.RemoteUrlChange ->
                _uiState.update { it.copy(remoteUrl = event.value) }
            is GitOnboardingEvent.RemoteTokenChange ->
                _uiState.update { it.copy(remoteToken = event.value) }
            is GitOnboardingEvent.InitialMessageChange ->
                _uiState.update { it.copy(initialMessage = event.value) }
            GitOnboardingEvent.ToggleDontAskAgain ->
                _uiState.update { it.copy(dontAskAgain = !it.dontAskAgain) }
            GitOnboardingEvent.DismissError ->
                _uiState.update { it.copy(error = null) }
            GitOnboardingEvent.Back -> back()
            GitOnboardingEvent.Skip -> next(skipped = true)
            GitOnboardingEvent.Next -> next(skipped = false)
            GitOnboardingEvent.Finish -> exitWizard()
        }
    }

    private fun back() {
        val s = _uiState.value
        if (s.stepIndex <= 0) exitWizard() else _uiState.update { it.copy(stepIndex = it.stepIndex - 1) }
    }

    /** Perform the current step's action (unless skipped), then advance. */
    private fun next(skipped: Boolean) {
        val s = _uiState.value
        val step = s.currentStep ?: return
        if (s.isLastStep) { exitWizard(); return }
        viewModelScope.launch(io) {
            if (!skipped) {
                val ok = when (step) {
                    Step.INIT_REPO -> doInit()
                    Step.IDENTITY -> doIdentity()
                    Step.REMOTE -> doRemote()
                    Step.FIRST_COMMIT -> doFirstCommit()
                    Step.PUSH -> true   // PUSH runs from its own button, not from Next
                }
                if (!ok) return@launch
            }
            _uiState.update { it.copy(stepIndex = it.stepIndex + 1) }
        }
    }

    private fun exitWizard() {
        viewModelScope.launch(io) {
            if (_uiState.value.dontAskAgain) onboardingPrefs.markDone(projectPath)
            _effects.trySend(Effect.Close)
        }
    }

    // ---- step actions -------------------------------------------------------------

    private suspend fun doInit(): Boolean {
        _uiState.update { it.copy(working = true) }
        return when (val r = session.initRepository()) {
            is GitResult.Ok -> {
                _uiState.update { it.copy(working = false, repoInitialized = true) }
                true
            }
            is GitResult.Err -> {
                _uiState.update { it.copy(working = false, error = r.error) }
                false
            }
        }
    }

    private suspend fun doIdentity(): Boolean {
        val s = _uiState.value
        val name = s.identityName.trim()
        val email = s.identityEmail.trim()
        if (name.isEmpty() || email.isEmpty()) {
            _effects.trySend(Effect.Message(UiText(R.string.git_identity_missing)))
            return false
        }
        _uiState.update { it.copy(working = true) }
        return when (val r = session.setIdentity(name, email, s.identityLocal)) {
            is GitResult.Ok -> {
                _uiState.update { it.copy(working = false) }
                true
            }
            is GitResult.Err -> {
                _uiState.update { it.copy(working = false, error = r.error) }
                false
            }
        }
    }

    private suspend fun doRemote(): Boolean {
        val url = _uiState.value.remoteUrl.trim()
        if (url.isEmpty()) return true   // optional step, empty URL = skipped
        _uiState.update { it.copy(working = true) }
        return when (val r = session.addRemote("origin", url)) {
            is GitResult.Ok -> {
                // Store the token up-front (M8 pattern) so the PUSH step can authenticate.
                val token = _uiState.value.remoteToken.trim()
                val host = runCatching { normalizeHost(url) }.getOrNull()
                if (host != null && token.isNotEmpty()) {
                    credentialStore.put(GitCredential(host, DEFAULT_USERNAME, token))
                }
                _uiState.update { it.copy(working = false) }
                true
            }
            is GitResult.Err -> {
                _uiState.update { it.copy(working = false, error = r.error) }
                false
            }
        }
    }

    private suspend fun doFirstCommit(): Boolean {
        val message = _uiState.value.initialMessage.trim().ifEmpty { "Initial commit" }
        _uiState.update { it.copy(working = true) }
        val staged = session.stageAll()
        if (staged is GitResult.Err) {
            _uiState.update { it.copy(working = false, error = staged.error) }
            return false
        }
        return when (val r = session.commit(message, amend = false)) {
            is GitResult.Ok -> {
                _uiState.update { it.copy(working = false) }
                true
            }
            is GitResult.Err -> {
                _uiState.update { it.copy(working = false, error = r.error) }
                false
            }
        }
    }

    /** PUSH step runs eagerly from its own button (plan: upstream set on first push). */
    fun pushAndSetUpstream() {
        val remote = _uiState.value.pushRemote.ifBlank { "origin" }
        viewModelScope.launch(io) {
            _uiState.update { it.copy(working = true) }
            val url = session.remoteUrl(remote)
            val host = url?.let { runCatching { normalizeHost(it) }.getOrNull() }
            val credential = host?.let { credentialStore.get(it) }
            if (host != null && credential == null) {
                _uiState.update { it.copy(working = false) }
                _effects.trySend(Effect.Message(UiText(R.string.git_err_no_remote)))
                return@launch
            }
            val provider = credential?.let { GitTokenCredentialsProvider(it.username, it.token) }
            when (val r = session.push(remote, provider)) {
                is GitResult.Ok -> {
                    val branch = (session.refresh() as? GitResult.Ok)?.value?.first?.headName
                    if (branch != null) {
                        when (val up = session.setUpstream(remote, branch)) {
                            is GitResult.Err ->
                                _uiState.update { it.copy(working = false, error = up.error) }
                            is GitResult.Ok -> Unit
                        }
                    }
                    _uiState.update { it.copy(working = false) }
                    _effects.trySend(Effect.Message(UiText(R.string.git_msg_pushed)))
                    next(skipped = true)   // advance past the PUSH step
                }
                is GitResult.Err ->
                    _uiState.update { it.copy(working = false, error = r.error) }
            }
        }
    }

    companion object {
        private const val DEFAULT_USERNAME = "x-access-token"

        fun factory(projectPath: String) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as XcodeApp
                GitOnboardingViewModel(
                    projectPath = projectPath,
                    credentialStore = app.container.gitCredentialStore,
                    onboardingPrefs = app.container.gitOnboardingPrefs,
                    globalIdentityFile = app.container.gitGlobalIdentityFile,
                )
            }
        }
    }
}

/** M9 wizard events (UDF, same style as [GitEvent]). */
sealed interface GitOnboardingEvent {
    data class NameChange(val value: String) : GitOnboardingEvent
    data class EmailChange(val value: String) : GitOnboardingEvent
    data class IdentityScopeChange(val local: Boolean) : GitOnboardingEvent
    data class RemoteUrlChange(val value: String) : GitOnboardingEvent
    data class RemoteTokenChange(val value: String) : GitOnboardingEvent
    data class InitialMessageChange(val value: String) : GitOnboardingEvent
    data object ToggleDontAskAgain : GitOnboardingEvent
    data object DismissError : GitOnboardingEvent
    data object Back : GitOnboardingEvent
    data object Skip : GitOnboardingEvent
    data object Next : GitOnboardingEvent
    data object Finish : GitOnboardingEvent
}
