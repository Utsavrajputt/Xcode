package com.invictus.xcode.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Expressive press feedback without springs — two short fixed-duration tweens
 * (cheap, fixed per-frame cost; safe to use on every list row even on low-end devices).
 */
private const val PressScaleDownMs = 100
private const val PressScaleUpMs = 150
private const val PressedScale = 0.96f

@Composable
private fun animatedPressScale(pressed: Boolean): Float {
    val scale by animateFloatAsState(
        targetValue = if (pressed) PressedScale else 1f,
        animationSpec = tween(if (pressed) PressScaleDownMs else PressScaleUpMs),
        label = "pressScale",
    )
    return scale
}

/**
 * Attach alongside a `clickable`/`combinedClickable` modifier that shares [interactionSource],
 * so the row scales down slightly on press and eases back on release.
 */
fun Modifier.expressivePressScale(interactionSource: MutableInteractionSource): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = animatedPressScale(pressed)
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Same press-scale, driven directly by a [pressed] boolean — for rows that track their own
 * press state (e.g. via `detectTapGestures(onPress = ...)`) instead of a `clickable` modifier.
 */
fun Modifier.expressivePressScale(pressed: Boolean): Modifier = composed {
    val scale = animatedPressScale(pressed)
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/**
 * Drop-in replacement for `Modifier.clickable(onClick = ...)` that keeps the normal themed
 * ripple but also adds the press-scale feel above — one call, same call sites as before.
 */
fun Modifier.expressiveClickable(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    this
        .expressivePressScale(interactionSource)
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick,
        )
}
