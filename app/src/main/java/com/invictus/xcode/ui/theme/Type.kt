package com.invictus.xcode.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontFamily

/** App-wide text styles: stock Material 3 for now, tweak here later. */
internal val XcodeTypography = Typography()

/** Monospace family for code-ish UI (paths, diffs, commit hashes). The editor has its own font setting. */
val CodeFontFamily: FontFamily = FontFamily.Monospace
