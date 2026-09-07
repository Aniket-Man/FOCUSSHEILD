package com.example.feature.analytics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.feature.analytics.domain.PeriodAnalyticsSummary
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.component.lineComponent
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import java.util.Locale

enum class ChartType {
    BAR,
    LINE
}

/**
 * Interactive Weekly & Period Study Hours Chart powered by Vico Charting Library.
 * Displays daily study hours against the configured daily study goal with Bar and Line views.
 */
@Composable
fun WeeklyStudyHoursChartCard(
    summary: PeriodAnalyticsSummary,
    dailyGoalMinutes: Int,
    modifier: Modifier = Modifier
) {
    val dailyChart = summary.dailyChart
    var chartType by remember { mutableStateOf(ChartType.BAR) }
    var selectedDayIndex by remember { mutableIntStateOf(-1) }

    val goalHours = dailyGoalMinutes / 60f

    // Convert milliseconds to hours (float)
    val entries = remember(dailyChart) {
        dailyChart.mapIndexed { index, day ->
            val hours = (day.studyTimeMillis / (1000f * 60f * 60f)).coerceAtLeast(0f)
            entryOf(index.toFloat(), hours)
        }
    }

    val chartModelProducer = remember(entries) {
        ChartEntryModelProducer(entries)
    }

    val horizontalAxisValueFormatter = remember(dailyChart) {
        AxisValueFormatter<com.patrykandpatrick.vico.core.axis.AxisPosition.Horizontal.Bottom> { value, _ ->
            val index = value.toInt()
            if (index in dailyChart.indices) {
                val day = dailyChart[index]
                day.dayLabel.take(3)
            } else {
                ""
            }
        }
    }

    val verticalAxisValueFormatter = remember {
        AxisValueFormatter<com.patrykandpatrick.vico.core.axis.AxisPosition.Vertical.Start> { value, _ ->
            if (value == 0f) "0h" else String.format(Locale.US, "%.1fh", value)
        }
    }

    val totalHours = summary.totalStudyTimeMillis / (1000f * 60f * 60f)
    val avgHours = if (summary.dailyChart.isNotEmpty()) totalHours / summary.dailyChart.size else 0f
    val goalsMetCount = dailyChart.count { it.isGoalMet }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, FocusShapes.card, ambientColor = Color.Black.copy(alpha = 0.02f))
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(16.dp)
            .testTag("weekly_study_hours_chart")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header with Chart Type Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "STUDY HOURS VISUALIZER",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${summary.dateRangeLabel} • Goal: ${String.format(Locale.US, "%.1fh", goalHours)}/day",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextMuted,
                            fontSize = 11.sp
                        )
                    )
                }

                // Bar / Line Switcher
                Row(
                    modifier = Modifier
                        .clip(FocusShapes.pill)
                        .background(FocusColors.SurfaceSubtle)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (chartType == ChartType.BAR) FocusColors.Primary else Color.Transparent)
                            .clickable { chartType = ChartType.BAR }
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BarChart,
                            contentDescription = "Bar Chart",
                            tint = if (chartType == ChartType.BAR) Color.White else FocusColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (chartType == ChartType.LINE) FocusColors.Primary else Color.Transparent)
                            .clickable { chartType = ChartType.LINE }
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ShowChart,
                            contentDescription = "Line Chart",
                            tint = if (chartType == ChartType.LINE) Color.White else FocusColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Summary Stats Pill Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatChip(
                    label = "Total",
                    value = String.format(Locale.US, "%.1fh", totalHours),
                    color = FocusColors.Primary,
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = "Avg/Day",
                    value = String.format(Locale.US, "%.1fh", avgHours),
                    color = FocusColors.AmberOrange,
                    modifier = Modifier.weight(1f)
                )
                StatChip(
                    label = "Goals Met",
                    value = "$goalsMetCount/${dailyChart.size}",
                    color = FocusColors.EmeraldSuccess,
                    modifier = Modifier.weight(1f)
                )
            }

            if (dailyChart.isNotEmpty()) {
                // Vico Chart
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .padding(top = 8.dp)
                ) {
                    val primaryColor = FocusColors.Primary.toArgb()

                    val startAxis = rememberStartAxis(
                        valueFormatter = verticalAxisValueFormatter,
                        label = textComponent(
                            color = FocusColors.TextSecondary,
                            textSize = 10.sp
                        ),
                        guideline = lineComponent(
                            color = FocusColors.CardBorderSubtle,
                            thickness = 1.dp
                        ),
                        tick = lineComponent(
                            color = FocusColors.CardBorderSubtle,
                            thickness = 1.dp
                        )
                    )

                    val bottomAxis = rememberBottomAxis(
                        valueFormatter = horizontalAxisValueFormatter,
                        label = textComponent(
                            color = FocusColors.TextSecondary,
                            textSize = 10.sp
                        ),
                        guideline = null,
                        tick = lineComponent(
                            color = FocusColors.CardBorderSubtle,
                            thickness = 1.dp
                        )
                    )

                    when (chartType) {
                        ChartType.BAR -> {
                            val columnComponent = lineComponent(
                                color = FocusColors.Primary,
                                thickness = 14.dp,
                                shape = Shapes.roundedCornerShape(allPercent = 25)
                            )

                            Chart(
                                chart = columnChart(
                                    columns = listOf(columnComponent)
                                ),
                                chartModelProducer = chartModelProducer,
                                startAxis = startAxis,
                                bottomAxis = bottomAxis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        ChartType.LINE -> {
                            val lineSpec = LineChart.LineSpec(
                                lineColor = primaryColor,
                                lineThicknessDp = 2.5f,
                                lineBackgroundShader = null
                            )

                            Chart(
                                chart = lineChart(
                                    lines = listOf(lineSpec)
                                ),
                                chartModelProducer = chartModelProducer,
                                startAxis = startAxis,
                                bottomAxis = bottomAxis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // Interactive Day Selector Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    dailyChart.forEachIndexed { index, day ->
                        val isSelected = selectedDayIndex == index
                        val hasStudy = day.studyTimeMillis > 0

                        Column(
                            modifier = Modifier
                                .clip(FocusShapes.small)
                                .clickable {
                                    selectedDayIndex = if (selectedDayIndex == index) -1 else index
                                }
                                .background(if (isSelected) FocusColors.PrimaryContainer else Color.Transparent)
                                .padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            day.isGoalMet -> FocusColors.EmeraldSuccess
                                            hasStudy -> FocusColors.Primary
                                            else -> FocusColors.TextMuted.copy(alpha = 0.3f)
                                        }
                                    )
                            )
                            Text(
                                text = day.formattedStudyTime,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isSelected) FocusColors.Primary else FocusColors.TextMuted,
                                    fontSize = 9.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }

                // Selected Day Details Banner
                if (selectedDayIndex in dailyChart.indices) {
                    val day = dailyChart[selectedDayIndex]
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FocusShapes.small)
                            .background(FocusColors.SurfaceSubtle)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (day.isGoalMet) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = FocusColors.EmeraldSuccess,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Text(
                                    text = "${day.dateFormatted} (${day.dayLabel})",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                            Text(
                                text = "${day.formattedStudyTime} • ${day.sessionCount} sessions",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.Primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No study sessions in this period",
                        style = MaterialTheme.typography.bodySmall.copy(color = FocusColors.TextMuted)
                    )
                }
            }
        }
    }
}

/**
 * Focus Streak & Consistency Visualization Card powered by Vico Charting & Compose.
 * Displays streak progression, consistency curve, daily qualifying thresholds, and milestone tracker.
 */
@Composable
fun FocusStreakChartCard(
    summary: PeriodAnalyticsSummary,
    modifier: Modifier = Modifier
) {
    val streak = summary.streakInfo
    val dailyChart = summary.dailyChart
    val thresholdMinutes = streak.thresholdMinutes

    // Convert daily study minutes for streak visualization
    val entries = remember(dailyChart) {
        dailyChart.mapIndexed { index, day ->
            val minutes = (day.studyTimeMillis / (1000f * 60f)).coerceAtLeast(0f)
            entryOf(index.toFloat(), minutes)
        }
    }

    val chartModelProducer = remember(entries) {
        ChartEntryModelProducer(entries)
    }

    val horizontalAxisValueFormatter = remember(dailyChart) {
        AxisValueFormatter<com.patrykandpatrick.vico.core.axis.AxisPosition.Horizontal.Bottom> { value, _ ->
            val index = value.toInt()
            if (index in dailyChart.indices) {
                dailyChart[index].dayLabel.take(3)
            } else {
                ""
            }
        }
    }

    val verticalAxisValueFormatter = remember {
        AxisValueFormatter<com.patrykandpatrick.vico.core.axis.AxisPosition.Vertical.Start> { value, _ ->
            "${value.toInt()}m"
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, FocusShapes.card, ambientColor = Color.Black.copy(alpha = 0.02f))
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(16.dp)
            .testTag("focus_streak_chart")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Streak Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(FocusColors.AmberLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = "Streak",
                            tint = FocusColors.AmberOrange,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${streak.currentStreak} Day Focus Streak",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = FocusColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                            if (streak.isStreakActiveToday) {
                                Box(
                                    modifier = Modifier
                                        .clip(FocusShapes.pill)
                                        .background(FocusColors.EmeraldLight)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Active Today",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = FocusColors.EmeraldSuccess,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp
                                        )
                                    )
                                }
                            }
                        }
                        Text(
                            text = "Longest: ${streak.longestStreak} days • Min threshold: ${thresholdMinutes}m/day",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            // Streak Timeline Badges
            if (dailyChart.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FocusShapes.medium)
                        .background(FocusColors.SurfaceSubtle)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    dailyChart.forEach { day ->
                        val minutes = day.studyTimeMillis / (60 * 1000)
                        val qualified = minutes >= thresholdMinutes

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = day.dayLabel.take(1),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.TextSecondary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            qualified -> FocusColors.AmberOrange
                                            minutes > 0 -> FocusColors.PrimaryLight
                                            else -> FocusColors.CardBorderSubtle
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (qualified) {
                                    Icon(
                                        imageVector = Icons.Filled.LocalFireDepartment,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                } else if (minutes > 0) {
                                    Text(
                                        text = "${minutes}m",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(4.dp)
                                            .clip(CircleShape)
                                            .background(FocusColors.TextMuted.copy(alpha = 0.5f))
                                    )
                                }
                            }
                        }
                    }
                }

                // Vico Consistency Trend Line
                Column {
                    Text(
                        text = "DAILY FOCUS DURATION VS THRESHOLD",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                    ) {
                        val amberColor = FocusColors.AmberOrange.toArgb()

                        val startAxis = rememberStartAxis(
                            valueFormatter = verticalAxisValueFormatter,
                            label = textComponent(
                                color = FocusColors.TextSecondary,
                                textSize = 9.sp
                            ),
                            guideline = lineComponent(
                                color = FocusColors.CardBorderSubtle,
                                thickness = 1.dp
                            ),
                            tick = lineComponent(
                                color = FocusColors.CardBorderSubtle,
                                thickness = 1.dp
                            )
                        )

                        val bottomAxis = rememberBottomAxis(
                            valueFormatter = horizontalAxisValueFormatter,
                            label = textComponent(
                                color = FocusColors.TextSecondary,
                                textSize = 9.sp
                            ),
                            guideline = null,
                            tick = lineComponent(
                                color = FocusColors.CardBorderSubtle,
                                thickness = 1.dp
                            )
                        )

                        val lineSpec = LineChart.LineSpec(
                            lineColor = amberColor,
                            lineThicknessDp = 2.5f,
                            lineBackgroundShader = null
                        )

                        Chart(
                            chart = lineChart(
                                lines = listOf(lineSpec)
                            ),
                            chartModelProducer = chartModelProducer,
                            startAxis = startAxis,
                            bottomAxis = bottomAxis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(FocusShapes.small)
            .background(FocusColors.SurfaceSubtle)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = FocusColors.TextMuted,
                    fontSize = 9.sp
                )
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            )
        }
    }
}
