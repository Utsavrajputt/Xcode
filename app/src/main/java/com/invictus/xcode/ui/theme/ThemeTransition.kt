package com.invictus.xcode.ui.theme

import android.graphics.Bitmap
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.core.view.drawToBitmap
import kotlin.math.hypot

/** Drives the circular-reveal animation when the app theme changes. */
class ThemeTransitionController {
    var isAnimating by mutableStateOf(false); internal set
    var progress by mutableStateOf(0f); internal set
    var origin by mutableStateOf(Offset.Zero); internal set
    var bitmap by mutableStateOf<Bitmap?>(null); internal set
    var maxRadius by mutableStateOf(1f); internal set

    internal var pendingReveal: (() -> Unit)? = null

    /** Freezes the current screen, then reveals [onReveal] from [position] outward. */
    fun begin(view: View, position: Offset, onReveal: () -> Unit) {
        if (isAnimating) return
        origin = position
        bitmap = try { view.drawToBitmap() } catch (_: Exception) { null }
        pendingReveal = onReveal
        progress = 0f
        isAnimating = true
    }

    fun end() {
        isAnimating = false
        progress = 0f
        bitmap = null
        pendingReveal = null
    }
}

/**
 * Place inside the root themed container: shows the frozen old screen, then
 * punches a growing circle (from the tap point) that reveals the new theme.
 * (Same effect as xmd's ThemeTransition.)
 */
@Composable
fun ThemeTransitionOverlay(controller: ThemeTransitionController) {
    if (!controller.isAnimating) return

    LaunchedEffect(controller.isAnimating) {
        if (controller.bitmap == null) {
            controller.pendingReveal?.invoke()
            controller.end()
            return@LaunchedEffect
        }
        var revealed = false
        Animatable(0f).animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        ) {
            controller.progress = value
            if (!revealed && value > 0.3f) {
                controller.pendingReveal?.invoke()
                revealed = true
            }
        }
        controller.end()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { controller.maxRadius = hypot(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) {
                // Consume touches while the reveal plays.
                awaitPointerEventScope { while (true) awaitPointerEvent() }
            },
    ) {
        controller.bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithCache {
                        onDrawWithContent {
                            drawContent()
                            val r = controller.progress * controller.maxRadius
                            drawCircle(
                                color = Color.Transparent,
                                radius = r,
                                center = controller.origin,
                                blendMode = BlendMode.Clear,
                            )
                        }
                    },
            )
        }
    }
}
