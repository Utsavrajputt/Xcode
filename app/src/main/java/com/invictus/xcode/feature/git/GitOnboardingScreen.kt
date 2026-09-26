package com.invictus.xcode.feature.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invictus.xcode.R
import com.invictus.xcode.ui.icons.XIcons
import kotlinx.coroutines.launch

/**
 * M9 guided Git onboarding (plan 3.4): full-screen wizard with a step indicator;
 * every step skippable. Triggered automatically from the workspace when the repo
 * is incomplete, or manually from Source Control.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitOnboardingScreen(
    projectPath: String,
    onDone: () -> Unit,
    viewModel: GitOnboardingViewModel = viewModel(
        key = "git_onboarding:$projectPath",
        factory = GitOnboardingViewModel.factory(projectPath),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is GitOnboardingViewModel.Effect.Close -> onDone()
                is GitOnboardingViewModel.Effect.Message ->
                    scope.launch { snackbarHostState.showSnackbar(effect.text.resolve(context)) }
            }
        }
    }

    state.error?.let { details ->
        GitErrorDialog(details = details, onDismiss = { viewModel.onEvent(GitOnboardingEvent.DismissError) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.git_setup_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { viewModel.onEvent(GitOnboardingEvent.Back) }) {
                        Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            // Step indicator
            val total = state.steps.size.coerceAtLeast(1)
            val index = (state.stepIndex + 1).coerceIn(1, total)
            LinearProgressIndicator(
                progress = { index.toFloat() / total },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_step_indicator, index, total),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (state.currentStep) {
                    GitOnboardingViewModel.Step.INIT_REPO -> InitRepoStep(state)
                    GitOnboardingViewModel.Step.IDENTITY ->
                        IdentityStep(state, viewModel::onEvent)
                    GitOnboardingViewModel.Step.REMOTE ->
                        RemoteStep(state, viewModel::onEvent)
                    GitOnboardingViewModel.Step.FIRST_COMMIT ->
                        FirstCommitStep(state, viewModel::onEvent)
                    GitOnboardingViewModel.Step.PUSH -> PushStep(state)
                    null -> Unit
                }

                if (state.currentStep == GitOnboardingViewModel.Step.INIT_REPO) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = state.dontAskAgain,
                            onCheckedChange = { viewModel.onEvent(GitOnboardingEvent.ToggleDontAskAgain) },
                        )
                        Text(
                            text = stringResource(R.string.onboarding_dont_ask_again),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            // Bottom actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { viewModel.onEvent(GitOnboardingEvent.Skip) },
                    enabled = !state.working,
                ) { Text(stringResource(R.string.onboarding_skip)) }
                Spacer(Modifier.weight(1f))
                if (state.currentStep == GitOnboardingViewModel.Step.PUSH) {
                    OutlinedButton(
                        onClick = { viewModel.onEvent(GitOnboardingEvent.Finish) },
                        enabled = !state.working,
                    ) { Text(stringResource(R.string.onboarding_finish)) }
                    Spacer(Modifier.height(0.dp))
                    Button(
                        onClick = viewModel::pushAndSetUpstream,
                        enabled = !state.working,
                    ) { Text(stringResource(R.string.onboarding_push_action)) }
                } else {
                    Button(
                        onClick = { viewModel.onEvent(GitOnboardingEvent.Next) },
                        enabled = !state.working,
                    ) { Text(stringResource(R.string.onboarding_next)) }
                }
            }
        }
    }
}

@Composable
private fun InitRepoStep(state: GitOnboardingViewModel.UiState) {
    StepText(R.string.onboarding_step_init_title, R.string.onboarding_step_init_body)
    if (state.repoInitialized) {
        Text(
            text = stringResource(R.string.onboarding_repo_initialized),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun IdentityStep(state: GitOnboardingViewModel.UiState, onEvent: (GitOnboardingEvent) -> Unit) {
    StepText(R.string.onboarding_step_identity_title, R.string.onboarding_step_identity_body)
    OutlinedTextField(
        value = state.identityName,
        onValueChange = { onEvent(GitOnboardingEvent.NameChange(it)) },
        label = { Text(stringResource(R.string.git_identity_name)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.identityEmail,
        onValueChange = { onEvent(GitOnboardingEvent.EmailChange(it)) },
        label = { Text(stringResource(R.string.git_identity_email)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(R.string.git_identity),
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 4.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = !state.identityLocal,
            onClick = { onEvent(GitOnboardingEvent.IdentityScopeChange(false)) },
        )
        Text(stringResource(R.string.git_identity_scope_global))
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(
            selected = state.identityLocal,
            onClick = { onEvent(GitOnboardingEvent.IdentityScopeChange(true)) },
        )
        Text(stringResource(R.string.git_identity_scope_repo))
    }
}

@Composable
private fun RemoteStep(state: GitOnboardingViewModel.UiState, onEvent: (GitOnboardingEvent) -> Unit) {
    StepText(R.string.onboarding_step_remote_title, R.string.onboarding_step_remote_body)
    OutlinedTextField(
        value = state.remoteUrl,
        onValueChange = { onEvent(GitOnboardingEvent.RemoteUrlChange(it)) },
        label = { Text(stringResource(R.string.clone_url_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.remoteToken,
        onValueChange = { onEvent(GitOnboardingEvent.RemoteTokenChange(it)) },
        label = { Text(stringResource(R.string.clone_token_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FirstCommitStep(state: GitOnboardingViewModel.UiState, onEvent: (GitOnboardingEvent) -> Unit) {
    StepText(R.string.onboarding_step_commit_title, R.string.onboarding_step_commit_body)
    OutlinedTextField(
        value = state.initialMessage,
        onValueChange = { onEvent(GitOnboardingEvent.InitialMessageChange(it)) },
        label = { Text(stringResource(R.string.onboarding_commit_message_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PushStep(state: GitOnboardingViewModel.UiState) {
    StepText(R.string.onboarding_step_push_title, R.string.onboarding_step_push_body)
    Text(
        text = stringResource(R.string.onboarding_push_remote, state.pushRemote),
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun StepText(titleRes: Int, bodyRes: Int) {
    Text(text = stringResource(titleRes), style = MaterialTheme.typography.titleLarge)
    Text(
        text = stringResource(bodyRes),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
