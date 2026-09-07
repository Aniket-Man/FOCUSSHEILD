package com.example.feature.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Shared palette + building blocks for the three FocusShield home screen widgets.
 */
object FocusWidgetTheme {
    val background = Color(0xFF1C1B2E)
    val title = Color(0xFF9CA3C4)
    val value = Color(0xFFFFFFFF)
    val accent = Color(0xFF818CF8)
    val amber = Color(0xFFF59E0B)
    val green = Color(0xFF34D399)
    val segmentFill = Color(0xFF818CF8)
    val segmentEmpty = Color(0xFF312E4A)
}

/**
 * Rounded dark card container used by every widget.
 */
@Composable
fun WidgetCard(content: @Composable () -> Unit) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .background(ImageProvider(com.example.R.drawable.widget_card_background))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
fun WidgetTitle(text: String) {
    Text(
        text = text,
        style = TextStyle(
            color = ColorProvider(FocusWidgetTheme.title),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    )
}

@Composable
fun WidgetValue(text: String, color: Color = FocusWidgetTheme.value) {
    Text(
        text = text,
        style = TextStyle(
            color = ColorProvider(color),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    )
}

@Composable
fun WidgetCaption(text: String, color: Color = FocusWidgetTheme.title) {
    Text(
        text = text,
        style = TextStyle(
            color = ColorProvider(color),
            fontSize = 11.sp
        )
    )
}

/**
 * Ten-segment progress indicator (battery-style). Robust across every launcher
 * because it needs no fractional width constraints.
 */
@Composable
fun SegmentedProgress(progress: Float) {
    val filled = (progress.coerceIn(0f, 1f) * 10f).toInt()
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        for (i in 0 until 10) {
            Box(
                modifier = GlanceModifier
                    .defaultWeight()
                    .height(6.dp)
                    .background(
                        ImageProvider(
                            if (i < filled) com.example.R.drawable.widget_segment_fill
                            else com.example.R.drawable.widget_segment_empty
                        )
                    )
            ) {}
            if (i < 9) Spacer(modifier = GlanceModifier.width(3.dp))
        }
    }
}
