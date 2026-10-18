package com.example.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Official FocusShield open-book brand mark (the white symbol from the FocusShield logo).
 *
 * Used across overlays, blocking screens, bottom navigation, commitment controls, and branded UI
 * surfaces. Uses a 100x100 viewport so it scales crisply at any dp size.
 */
val FocusBrandMark: ImageVector by lazy {
    ImageVector.Builder(
        name = "FocusBrandMark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 100f,
        viewportHeight = 100f
    ).apply {
        val fillBrush = SolidColor(Color.White)

        // 1. Left inner page (tallest peak near center spine)
        path(fill = fillBrush) {
            moveTo(39.6f, 8.2f)
            lineTo(39.6f, 62.0f)
            curveTo(42.8f, 69.5f, 46.5f, 78.2f, 48.6f, 86.6f)
            lineTo(48.6f, 35.5f)
            curveTo(46.8f, 25.5f, 43.5f, 16.0f, 39.6f, 8.2f)
            close()
        }

        // 2. Right inner page (mirror across x = 50)
        path(fill = fillBrush) {
            moveTo(60.4f, 8.2f)
            curveTo(56.5f, 16.0f, 53.2f, 25.5f, 51.4f, 35.5f)
            lineTo(51.4f, 86.6f)
            curveTo(53.5f, 78.2f, 57.2f, 69.5f, 60.4f, 62.0f)
            lineTo(60.4f, 8.2f)
            close()
        }

        // 3. Left middle page
        path(fill = fillBrush) {
            moveTo(27.9f, 13.4f)
            lineTo(27.9f, 68.4f)
            curveTo(35.2f, 70.6f, 41.8f, 76.8f, 45.9f, 85.0f)
            curveTo(43.8f, 77.4f, 40.8f, 70.0f, 37.3f, 63.5f)
            lineTo(37.3f, 27.2f)
            curveTo(34.8f, 21.8f, 31.5f, 17.2f, 27.9f, 13.4f)
            close()
        }

        // 4. Right middle page
        path(fill = fillBrush) {
            moveTo(72.1f, 13.4f)
            curveTo(68.5f, 17.2f, 65.2f, 21.8f, 62.7f, 27.2f)
            lineTo(62.7f, 63.5f)
            curveTo(59.2f, 70.0f, 56.2f, 77.4f, 54.1f, 85.0f)
            curveTo(58.2f, 76.8f, 64.8f, 70.6f, 72.1f, 68.4f)
            lineTo(72.1f, 13.4f)
            close()
        }

        // 5. Left outer page (rounded top arch)
        path(fill = fillBrush) {
            moveTo(15.5f, 26.4f)
            curveTo(18.5f, 22.8f, 22.8f, 22.4f, 25.5f, 25.0f)
            lineTo(25.5f, 70.0f)
            curveTo(32.8f, 71.8f, 39.0f, 76.8f, 43.1f, 83.5f)
            curveTo(35.5f, 75.0f, 24.5f, 74.2f, 15.5f, 79.7f)
            lineTo(15.5f, 26.4f)
            close()
        }

        // 6. Right outer page (rounded top arch)
        path(fill = fillBrush) {
            moveTo(74.5f, 25.0f)
            curveTo(77.2f, 22.4f, 81.5f, 22.8f, 84.5f, 26.4f)
            lineTo(84.5f, 79.7f)
            curveTo(75.5f, 74.2f, 64.5f, 75.0f, 56.9f, 83.5f)
            curveTo(61.0f, 76.8f, 67.2f, 71.8f, 74.5f, 70.0f)
            lineTo(74.5f, 25.0f)
            close()
        }

        // 7. Outer book cover frame / base cradle
        path(fill = fillBrush) {
            moveTo(10.9f, 32.6f)
            lineTo(7.9f, 92.1f)
            lineTo(50.0f, 89.2f)
            lineTo(92.1f, 92.1f)
            lineTo(89.1f, 32.6f)
            lineTo(89.9f, 89.1f)
            lineTo(50.0f, 89.1f)
            lineTo(10.1f, 89.1f)
            close()
        }
    }.build()
}
