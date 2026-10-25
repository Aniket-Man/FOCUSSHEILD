package com.example.core.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Modifier to add a shimmering animation effect to a component,
 * ideal for skeleton loading screens.
 */
fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
        ),
        start = Offset(translateAnim.value - 400f, 0f),
        end = Offset(translateAnim.value, 0f)
    )
    this.background(brush)
}

/**
 * A generic skeleton box component that applies the shimmer effect.
 */
@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp)
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
            .shimmerEffect()
    )
}
