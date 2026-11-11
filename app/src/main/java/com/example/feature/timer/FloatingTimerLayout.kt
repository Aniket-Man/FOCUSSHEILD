package com.example.feature.timer

import kotlin.math.roundToInt

internal data class FloatingTimerDimensions(val width: Int, val height: Int)
internal enum class FloatingTimerMove { UP, DOWN, LEFT_EDGE, RIGHT_EDGE }

/** Geometry depends on configuration and expansion, never on countdown digits or translated text. */
internal object FloatingTimerLayout {
    fun dimensions(expanded: Boolean, screenWidth: Int, screenHeight: Int, density: Float, fontScale: Float): FloatingTimerDimensions {
        val safeDensity = density.takeIf { it.isFinite() && it > 0f } ?: 1f
        val scale = fontScale.takeIf { it.isFinite() && it > 0f } ?: 1f
        val width = ((if (expanded) 320 else 48) * safeDensity).roundToInt().coerceIn(1, screenWidth.coerceAtLeast(1))
        val preferredHeight = if (expanded) (224f + 80f * (scale - 1f).coerceAtLeast(0f)) else 56f
        val availableHeight = (screenHeight - (72f * safeDensity).roundToInt()).coerceAtLeast(1)
        val height = (preferredHeight * safeDensity).roundToInt().coerceIn(1, availableHeight)
        return FloatingTimerDimensions(width, height)
    }

    fun clampY(y: Int, screenHeight: Int, overlayHeight: Int, topInset: Int, bottomInset: Int): Int {
        val maxY = (screenHeight - overlayHeight - bottomInset.coerceAtLeast(0)).coerceAtLeast(0)
        val minY = topInset.coerceIn(0, maxY)
        return y.coerceIn(minY, maxY)
    }
}
