package com.invictus.xcode.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import com.invictus.xcode.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Material 3 Expressive corner family (ported from xmd's ComposeTheme). */
val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/** Space Grotesk for display/headline/title — copied from xmd (res/font/space_grotesk_semibold.ttf). */
private val HeadingFont = FontFamily(Font(R.font.space_grotesk_semibold, weight = FontWeight.SemiBold))

private fun TextStyle.expressive(fontFamily: FontFamily = this.fontFamily ?: FontFamily.Default): TextStyle =
    copy(fontFamily = fontFamily, letterSpacing = 0.sp)

private val Base = Typography()

/** Expressive typography: Space Grotesk headings + zero letter-spacing (matches xmd). */
val ExpressiveTypography = Typography(
    displayLarge = Base.displayLarge.expressive(HeadingFont),
    displayMedium = Base.displayMedium.expressive(HeadingFont),
    displaySmall = Base.displaySmall.expressive(HeadingFont),
    headlineLarge = Base.headlineLarge.expressive(HeadingFont),
    headlineMedium = Base.headlineMedium.expressive(HeadingFont),
    headlineSmall = Base.headlineSmall.expressive(HeadingFont),
    titleLarge = Base.titleLarge.expressive(HeadingFont),
    titleMedium = Base.titleMedium.expressive(HeadingFont),
    titleSmall = Base.titleSmall.expressive(HeadingFont),
    bodyLarge = Base.bodyLarge.expressive(),
    bodyMedium = Base.bodyMedium.expressive(),
    bodySmall = Base.bodySmall.expressive(),
    labelLarge = Base.labelLarge.expressive(),
    labelMedium = Base.labelMedium.expressive(),
    labelSmall = Base.labelSmall.expressive(),
)
