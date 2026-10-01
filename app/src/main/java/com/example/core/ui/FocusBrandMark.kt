package com.example.core.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Compact, tintable version of the folded-map Focus brand mark.
 *
 * The source artwork uses four shaded facets. In small controls the facets merge into one clear
 * silhouette so [androidx.compose.material3.Icon] can apply the same selected/unselected tint and
 * visual treatment as the Material icon this replaces.
 */
val FocusBrandMark: ImageVector by lazy {
    ImageVector.Builder(
        name = "FocusBrandMark",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(2.5f, 8.1f)
            lineTo(7.5f, 1.5f)
            lineTo(12f, 8.1f)
            lineTo(16.5f, 1.5f)
            lineTo(21.5f, 8.1f)
            lineTo(21.5f, 22.5f)
            lineTo(16.5f, 16.1f)
            lineTo(12f, 22.5f)
            lineTo(7.5f, 16.1f)
            lineTo(2.5f, 22.5f)
            close()
        }
    }.build()
}
