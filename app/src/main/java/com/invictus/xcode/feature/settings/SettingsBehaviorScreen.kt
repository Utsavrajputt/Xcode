package com.invictus.xcode.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invictus.xcode.R
import com.invictus.xcode.feature.editor.EditorViewModel
import com.invictus.xcode.ui.icons.XIcons

/** Behavior settings: what to do when a file changes on disk outside the app. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsBehaviorScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val autoReloadExternal by viewModel.autoReloadExternalChanges.collectAsStateWithLifecycle()
    val gitStatusPollingEnabled by viewModel.gitStatusPollingEnabled.collectAsStateWithLifecycle()
    val restoreTabsOnOpen by viewModel.restoreTabsOnOpen.collectAsStateWithLifecycle()
    val searchDefaultStringsOnly by viewModel.searchDefaultStringsOnly.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_section_behavior)) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SettingsSectionCard {
                RadioSettingRow(
                    title = stringResource(R.string.settings_external_changes_ask),
                    subtitle = stringResource(R.string.settings_external_changes_ask_desc),
                    selected = !autoReloadExternal,
                    onClick = { viewModel.setAutoReloadExternalChanges(false) },
                )
                SettingsDivider()
                RadioSettingRow(
                    title = stringResource(R.string.settings_external_changes_auto),
                    subtitle = stringResource(R.string.settings_external_changes_auto_desc),
                    selected = autoReloadExternal,
                    onClick = { viewModel.setAutoReloadExternalChanges(true) },
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsSectionCard {
                SwitchSettingRow(
                    title = stringResource(R.string.settings_restore_tabs),
                    subtitle = stringResource(R.string.settings_restore_tabs_desc),
                    checked = restoreTabsOnOpen,
                    onCheckedChange = viewModel::setRestoreTabsOnOpen,
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsSectionCard {
                SwitchSettingRow(
                    title = stringResource(R.string.settings_git_status_polling),
                    subtitle = stringResource(R.string.settings_git_status_polling_desc),
                    checked = gitStatusPollingEnabled,
                    onCheckedChange = viewModel::setGitStatusPollingEnabled,
                )
            }

            Spacer(Modifier.height(16.dp))

            SettingsSectionCard {
                SwitchSettingRow(
                    title = stringResource(R.string.settings_search_default_strings),
                    subtitle = stringResource(R.string.settings_search_default_strings_desc),
                    checked = searchDefaultStringsOnly,
                    onCheckedChange = viewModel::setSearchDefaultStringsOnly,
                )
            }
        }
    }
}
