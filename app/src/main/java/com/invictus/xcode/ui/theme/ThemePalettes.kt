package com.invictus.xcode.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Resolves the active [ColorScheme] for the given theme / dark / AMOLED flags.
 * Same logic as xmd's resolveXmdColorScheme.
 */
fun resolveXcodeColorScheme(
    context: Context,
    theme: AppTheme,
    isDark: Boolean,
    isAmoled: Boolean,
): ColorScheme = when {
    theme.isDynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        if (isDark) {
            val scheme = dynamicDarkColorScheme(context)
            if (isAmoled) scheme.pureBlack() else scheme
        } else {
            dynamicLightColorScheme(context)
        }
    }
    isDark && isAmoled -> theme.getAmoledColorScheme()
    isDark -> theme.getDarkColorScheme()
    else -> theme.getLightColorScheme()
}

/** Forces pure-black surfaces (AMOLED). */
internal fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF222222),
)
