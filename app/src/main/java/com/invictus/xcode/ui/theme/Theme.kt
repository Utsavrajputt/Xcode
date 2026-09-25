package com.invictus.xcode.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * App theme wrapper — now fully Material 3 Expressive with multi-theme support
 * (Aurora default; resolver + settings ported from xmd).
 */
@Composable
fun XcodeTheme(
    appTheme: AppTheme = ThemeSettings.theme,
    themeMode: ThemeMode = ThemeSettings.mode,
    isAmoled: Boolean = ThemeSettings.amoled,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colorScheme = resolveXcodeColorScheme(LocalContext.current, appTheme, darkTheme, isAmoled)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ExpressiveTypography,
        shapes = ExpressiveShapes,
        content = content,
    )
}
