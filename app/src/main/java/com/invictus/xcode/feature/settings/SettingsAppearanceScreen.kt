package com.invictus.xcode.feature.settings

import com.invictus.xcode.ui.components.expressiveClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invictus.xcode.R
import com.invictus.xcode.core.editor.EditorThemes
import com.invictus.xcode.feature.editor.DEFAULT_TEXT_SIZE_SP
import com.invictus.xcode.feature.editor.EditorViewModel
import com.invictus.xcode.feature.editor.SymbolBarCustomizeDialog
import com.invictus.xcode.core.editor.EditorSettingsStore
import com.invictus.xcode.ui.icons.XIcons
import com.invictus.xcode.ui.theme.ThemePickerState
import com.invictus.xcode.ui.theme.ThemeSettings

/** Appearance settings: app theme, editor theme, font size, symbol bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAppearanceScreen(
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val themeId by viewModel.themeId.collectAsStateWithLifecycle()
    val fontSizePx by viewModel.fontSizePx.collectAsStateWithLifecycle()
    val symbolBar by viewModel.symbolBar.collectAsStateWithLifecycle()
    val symbolBarVisible by viewModel.symbolBarVisible.collectAsStateWithLifecycle()
    val autoPreviewEnabled by viewModel.autoPreviewEnabled.collectAsStateWithLifecycle()

    var showThemePicker by remember { mutableStateOf(false) }
    var showSymbolCustomize by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val builtInDefaultPx = with(density) { DEFAULT_TEXT_SIZE_SP.sp.toPx() }
    val effectiveFontSizePx = if (fontSizePx > 0f) fontSizePx else builtInDefaultPx

    val selectedThemeName = if (themeId == EditorThemes.SYSTEM_DEFAULT) {
        stringResource(R.string.editor_theme_system_default)
    } else {
        EditorThemes.find(themeId)?.displayName ?: stringResource(R.string.editor_theme_system_default)
    }
    val symbolBarPreview = symbolBar.joinToString(" ")

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(XIcons.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                title = { Text(stringResource(R.string.settings_section_appearance)) },
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
                ClickableSettingRow(
                    title = stringResource(R.string.settings_app_theme),
                    subtitle = ThemeSettings.theme.title,
                    chevron = XIcons.ChevronRight,
                    onClick = { ThemePickerState.visible = true },
                )
                SettingsDivider()
                ClickableSettingRow(
                    title = stringResource(R.string.settings_editor_theme),
                    subtitle = selectedThemeName,
                    chevron = XIcons.ChevronRight,
                    onClick = { showThemePicker = true },
                )
                SettingsDivider()
                FontSizeRow(
                    label = stringResource(R.string.settings_font_size),
                    valuePx = effectiveFontSizePx,
                    onValueChange = { viewModel.setFontSizePx(it) },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.settings_symbol_bar_visible),
                    subtitle = stringResource(R.string.settings_symbol_bar_visible_desc),
                    checked = symbolBarVisible,
                    onCheckedChange = { viewModel.setSymbolBarVisible(it) },
                )
                SettingsDivider()
                SwitchSettingRow(
                    title = stringResource(R.string.settings_auto_preview),
                    subtitle = stringResource(R.string.settings_auto_preview_desc),
                    checked = autoPreviewEnabled,
                    onCheckedChange = { viewModel.setAutoPreviewEnabled(it) },
                )
                SettingsDivider()
                ClickableSettingRow(
                    title = stringResource(R.string.settings_symbol_bar),
                    subtitle = symbolBarPreview,
                    chevron = XIcons.ChevronRight,
                    onClick = { showSymbolCustomize = true },
                )
            }
        }
    }

    if (showThemePicker) {
        ThemePickerDialog(
            selected = themeId,
            onSelect = { viewModel.setTheme(it) },
            onDismiss = { showThemePicker = false },
        )
    }
    if (showSymbolCustomize) {
        SymbolBarCustomizeDialog(
            current = symbolBar,
            onSave = { viewModel.setSymbolBar(it); showSymbolCustomize = false },
            onResetDefault = { viewModel.setSymbolBar(EditorSettingsStore.DEFAULT_SYMBOLS); showSymbolCustomize = false },
            onDismiss = { showSymbolCustomize = false },
        )
    }
}

@Composable
private fun FontSizeRow(label: String, valuePx: Float, onValueChange: (Float) -> Unit) {
    val density = LocalDensity.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Slider(
                value = valuePx,
                onValueChange = onValueChange,
                valueRange = 20f..100f,
                modifier = Modifier.weight(1f),
            )
            Box(modifier = Modifier.size(width = 44.dp, height = 1.dp))
            Text(
                text = "${valuePx.toInt()}px",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.settings_font_size_preview),
            fontFamily = FontFamily.Monospace,
            fontSize = with(density) { valuePx.toSp() },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ThemePickerDialog(
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.editor_theme_picker_title)) },
        text = {
            Column {
                ThemeRow(
                    name = stringResource(R.string.editor_theme_system_default),
                    isSelected = selected == EditorThemes.SYSTEM_DEFAULT,
                    onClick = { onSelect(EditorThemes.SYSTEM_DEFAULT) },
                )
                EditorThemes.ALL.forEach { theme ->
                    ThemeRow(
                        name = theme.displayName,
                        isSelected = selected == theme.id,
                        onClick = { onSelect(theme.id) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close_dialog)) }
        },
    )
}

@Composable
private fun ThemeRow(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
            .expressiveClickable(onClick = onClick)
            .padding(vertical = 10.dp),
    ) {
        Box(modifier = Modifier.size(24.dp)) {
            if (isSelected) Icon(XIcons.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(text = name, modifier = Modifier.padding(start = 12.dp))
    }
}
