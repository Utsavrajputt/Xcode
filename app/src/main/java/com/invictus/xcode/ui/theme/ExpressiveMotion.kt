package com.invictus.xcode.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring

/**
 * Material 3 Expressive motion specs — springs over tweens for everything
 * spatial; short tweens reserved for small effects like press-down.
 */
object ExpressiveMotion {
    /** Quick positional moves (chips, sheets sliding). */
    val FastSpatialSpring: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumHigh)

    /** Default component motion (cards, toggles). */
    val DefaultSpatialSpring: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Emphasis moves (theme reveal, hero elements). */
    val SlowSpatialSpring: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)

    /** Default effects spring (bounce-back, scale). */
    val DefaultEffectsSpring: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)

    /** Expressive effect used for the theme-change reveal. */
    val ExpressiveEffectsSpring: SpringSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)

    /** Tiny press-down tween (before the spring release). */
    val FastEffectsTween: TweenSpec<Float> =
        tween(durationMillis = 120, easing = FastOutSlowInEasing)
}
