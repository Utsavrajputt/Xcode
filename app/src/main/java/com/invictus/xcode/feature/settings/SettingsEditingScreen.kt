package com.invictus.xcode.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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

/** Editing settings: autocomplete, pair cursor. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsEditingScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val autocompleteEnabled by viewModel.autocompleteEnabled.collectAsStateWithLifecycle()
    val pairCursorEnabled by viewModel.pairCursorEnabled.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_section_editing)) },
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
                SwitchSettingRow(
                    title = stringResource(R.string.settings_autocomplete),
                    subtitle = stringResource(R.string.settings_autocomplete_desc),
                    checked = autocompleteEnabled,
                    onCheckedChange = { viewModel.setAutocompleteEnabled(it) },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.settings_pair_cursor),
                    subtitle = stringResource(R.string.settings_pair_cursor_desc),
                    checked = pairCursorEnabled,
                    onCheckedChange = { viewModel.setPairCursorEnabled(it) },
                )
            }
        }
    }
}
