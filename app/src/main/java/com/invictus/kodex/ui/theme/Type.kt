package com.invictus.kodex.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.invictus.kodex.R

/** App-wide text styles: stock Material 3 for now, tweak here later. */
internal val KodexTypography = Typography()

/** Monospace family for code-ish UI (paths, diffs, commit hashes). The editor has its own font setting. */
val CodeFontFamily: FontFamily = FontFamily.Monospace

/** Brand wordmark font (home header). To change it, drop a .ttf in res/font and swap the resource here. */
val BrandFontFamily: FontFamily = FontFamily(Font(R.font.space_grotesk_semibold, weight = FontWeight.SemiBold))
