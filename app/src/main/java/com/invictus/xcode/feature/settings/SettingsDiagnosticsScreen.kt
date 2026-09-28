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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.invictus.xcode.R
import com.invictus.xcode.ui.icons.XIcons

/** Diagnostics hub: Crash logs and Git logs as two separate subsections. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDiagnosticsScreen(
    onBack: () -> Unit,
    onOpenCrashLogs: () -> Unit,
    onOpenGitLogs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_section_diagnostics)) },
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
                CategoryRow(
                    icon = XIcons.Bolt,
                    chevron = XIcons.ChevronRight,
                    title = stringResource(R.string.settings_section_crash_logs),
                    subtitle = stringResource(R.string.settings_category_crash_logs_desc),
                    onClick = onOpenCrashLogs,
                )
                CategoryRowGap()
                CategoryRow(
                    icon = XIcons.Commit,
                    chevron = XIcons.ChevronRight,
                    title = stringResource(R.string.settings_section_git_logs),
                    subtitle = stringResource(R.string.settings_category_git_logs_desc),
                    isLast = true,
                    onClick = onOpenGitLogs,
                )
            }
        }
    }
}
