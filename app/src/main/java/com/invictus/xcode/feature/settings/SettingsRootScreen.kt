package com.invictus.xcode.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.invictus.xcode.R
import com.invictus.xcode.ui.icons.XIcons

/** Root of the Settings screen: category cards, styled after xmd's SettingsRootScreen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRootScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenEditing: () -> Unit,
    onOpenBehavior: () -> Unit,
    onOpenDiagnostics: () -> Unit,
    onOpenGitLogs: () -> Unit,
    onOpenGitHub: () -> Unit,
    batteryOptimizationDisabled: Boolean = true,
    onFixBatteryOptimization: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    // Dismiss is per-visit (like xmd) -- reappears next time Settings is opened as
    // long as battery optimization is still on.
    var batteryWarningDismissed by remember { mutableStateOf(false) }
    val showBatteryWarning = !batteryOptimizationDisabled && !batteryWarningDismissed

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_title)) },
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
            if (showBatteryWarning) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = XIcons.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.settings_battery_warning_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            IconButton(onClick = { batteryWarningDismissed = true }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = XIcons.Close,
                                    contentDescription = stringResource(R.string.action_dismiss),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.settings_battery_warning_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = onFixBatteryOptimization,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            modifier = Modifier.height(36.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.settings_battery_warning_fix),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            SettingsSectionCard {
                CategoryRow(
                    icon = XIcons.Palette,
                    chevron = XIcons.ChevronRight,
                    title = stringResource(R.string.settings_section_appearance),
                    subtitle = stringResource(R.string.settings_category_appearance_desc),
                    isFirst = true,
                    onClick = onOpenAppearance,
                )
                CategoryRowGap()
                CategoryRow(
                    icon = XIcons.Edit,
                    chevron = XIcons.ChevronRight,
                    title = stringResource(R.string.settings_section_editing),
                    subtitle = stringResource(R.string.settings_category_editing_desc),
                    onClick = onOpenEditing,
                )
                CategoryRowGap()
                CategoryRow(
                    icon = XIcons.Refresh,
                    chevron = XIcons.ChevronRight,
                    title = stringResource(R.string.settings_section_behavior),
                    subtitle = stringResource(R.string.settings_category_behavior_desc),
                    onClick = onOpenBehavior,
                )
                CategoryRowGap()
                CategoryRow(
                    icon = XIcons.Bolt,
                    chevron = XIcons.ChevronRight,
                    title = stringResource(R.string.settings_section_diagnostics),
                    subtitle = stringResource(R.string.settings_category_diagnostics_desc),
                    onClick = onOpenDiagnostics,
                )
                CategoryRowGap()
                CategoryRow(
                    icon = XIcons.Commit,
                    chevron = XIcons.ChevronRight,
                    title = stringResource(R.string.settings_section_git_logs),
                    subtitle = stringResource(R.string.settings_category_git_logs_desc),
                    onClick = onOpenGitLogs,
                )
                CategoryRowGap()
                CategoryRow(
                    icon = XIcons.Commit,
                    chevron = XIcons.ChevronRight,
                    title = stringResource(R.string.settings_section_github),
                    subtitle = stringResource(R.string.settings_category_github_desc),
                    isLast = true,
                    onClick = onOpenGitHub,
                )
            }
        }
    }
}
