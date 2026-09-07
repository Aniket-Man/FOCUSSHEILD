package com.example.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.design.FocusType
import com.example.core.design.focusCard

/**
 * Today's Progress Card matching the Clean Minimalism design.
 * Shows total study time, mini weekly bar trend, and circular completion ring with clean slate typography.
 */
@Composable
fun ProgressCard(
    modifier: Modifier = Modifier,
    studyTime: String = "2h 45m",
    progressPercentage: Int = 73,
    weeklyBars: List<Float> = listOf(0.35f, 0.65f, 0.45f, 0.95f, 0.55f, 0.85f, 0.73f)
) {
    Box(
        modifier = modifier
            .focusCard()
            .testTag("progress_card")
    ) {
        Column {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TODAY'S PROGRESS",
                    style = FocusType.sectionLabel
                )
                Text(
                    text = "$progressPercentage% of Daily Goal",
                    style = FocusType.sectionLabel.copy(
                        color = FocusColors.Primary,
                        letterSpacing = 0.2.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Content Row: Study Time | Mini Bar Trend | Circular Ring
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Study time stat
                Column {
                    Text(
                        text = "Time Studied",
                        style = FocusType.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = studyTime,
                        style = FocusType.statNumberLarge
                    )
                }

                // Middle: Mini Bar Chart Trend
                Row(
                    modifier = Modifier
                        .height(36.dp)
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    weeklyBars.forEachIndexed { index, ratio ->
                        val isToday = index == weeklyBars.lastIndex
                        Box(
                            modifier = Modifier
                                .width(5.dp)
                                .fillMaxHeight(ratio.coerceIn(0.18f, 1f))
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isToday) FocusColors.Primary else FocusColors.PrimaryContainer
                                )
                        )
                    }
                }

                // Right: Circular progress ring. The ring uses the same purple accent as the rest
                // of the card (bars, "% of Daily Goal") rather than a lone emerald, so the whole
                // card reads as one deliberate accent.
                val ringColor = FocusColors.Primary
                val trackColor = FocusColors.SurfaceSubtle

                Box(
                    modifier = Modifier.size(58.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(54.dp)) {
                        val strokeWidth = 5.dp.toPx()
                        // Background track
                        drawCircle(
                            color = trackColor,
                            style = Stroke(width = strokeWidth)
                        )
                        // Progress arc
                        drawArc(
                            color = ringColor,
                            startAngle = -90f,
                            sweepAngle = (progressPercentage / 100f) * 360f,
                            useCenter = false,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                        )
                    }

                    Text(
                        text = "$progressPercentage%",
                        style = FocusType.primary.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    )
                }
            }
        }
    }
}

