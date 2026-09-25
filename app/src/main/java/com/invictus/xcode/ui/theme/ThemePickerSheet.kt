package com.invictus.xcode.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.invictus.xcode.ui.icons.XIcons

/** Theme-changing UI: mode chips, AMOLED switch, and a grid of theme previews. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePickerSheet(
    onDismiss: () -> Unit,
    transitionController: ThemeTransitionController,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val view = LocalView.current
    val systemDark = isSystemInDarkTheme()
    val dark = when (ThemeSettings.mode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Appearance", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.values().forEach { m ->
                    FilterChip(
                        selected = ThemeSettings.mode == m,
                        onClick = { ThemeSettings.setMode(m) },
                        label = {
                            Text(
                                when (m) {
                                    ThemeMode.SYSTEM -> "System"
                                    ThemeMode.LIGHT -> "Light"
                                    ThemeMode.DARK -> "Dark"
                                },
                            )
                        },
                    )
                }
            }

            if (dark) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Pure black (AMOLED)", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = ThemeSettings.amoled,
                        onCheckedChange = { ThemeSettings.setAmoled(it) },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.heightIn(max = 460.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(AppTheme.values().toList()) { theme ->
                    ThemeCard(
                        theme = theme,
                        dark = dark,
                        selected = ThemeSettings.theme == theme,
                        onSelect = { tapPosition ->
                            transitionController.begin(view, tapPosition) {
                                ThemeSettings.setTheme(theme)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeCard(
    theme: AppTheme,
    dark: Boolean,
    selected: Boolean,
    onSelect: (Offset) -> Unit,
) {
    val scheme = remember(theme, dark) {
        if (dark) theme.getDarkColorScheme() else theme.getLightColorScheme()
    }

    Box(
        modifier = Modifier.pointerInput(theme) {
            detectTapGestures { offset -> onSelect(offset) }
        },
    ) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
            border = if (selected) {
                BorderStroke(2.dp, scheme.primary)
            } else {
                BorderStroke(1.dp, scheme.outlineVariant)
            },
        ) {
            Column(Modifier.padding(16.dp)) {
                // Palette swatches as mini icon preview.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEach { c ->
                        Box(
                            Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(c),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(theme.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (theme.isDynamic) "Wallpaper colors" else "Light · Dark",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    XIcons.Check,
                    contentDescription = null,
                    tint = scheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
