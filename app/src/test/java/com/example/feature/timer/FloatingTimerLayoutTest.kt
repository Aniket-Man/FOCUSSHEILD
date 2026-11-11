package com.example.feature.timer

import org.junit.Assert.*
import org.junit.Test

class FloatingTimerLayoutTest {
    @Test
    fun `collapsed dimensions are stable across font scaling`() {
        assertEquals(FloatingTimerDimensions(48, 56), FloatingTimerLayout.dimensions(false, 360, 800, 1f, 1f))
        assertEquals(FloatingTimerDimensions(48, 56), FloatingTimerLayout.dimensions(false, 360, 800, 1f, 2f))
    }

    @Test
    fun `expanded controls grow for text scale but stay inside the screen`() {
        assertEquals(FloatingTimerDimensions(320, 304), FloatingTimerLayout.dimensions(true, 360, 800, 1f, 2f))
        val small = FloatingTimerLayout.dimensions(true, 240, 220, 1f, 2f)
        assertEquals(240, small.width)
        assertEquals(148, small.height)
    }

    @Test
    fun `invalid configurations produce bounded nonzero geometry`() {
        assertEquals(FloatingTimerDimensions(1, 1), FloatingTimerLayout.dimensions(true, -1, 0, Float.NaN, Float.POSITIVE_INFINITY))
    }

    @Test
    fun `vertical movement respects physical system insets including an oversized overlay`() {
        assertEquals(24, FloatingTimerLayout.clampY(-100, 800, 304, 24, 48))
        assertEquals(448, FloatingTimerLayout.clampY(900, 800, 304, 24, 48))
        assertEquals(0, FloatingTimerLayout.clampY(100, 100, 304, 24, 48))
    }
}
