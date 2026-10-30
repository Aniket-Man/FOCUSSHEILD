package com.example.feature.analytics.ui

import androidx.compose.foundation.background
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
 * Gridless Weekly & Period Study Hours Chart powered by Vico Charting Library.
 * Displays daily study hours without any card borders, boxes, or chart gridlines.
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("weekly_study_hours_chart"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header with Chart Type Toggle (Gridless)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Study hours visualizer",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Normal,
                        fontSize = 18.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${summary.dateRangeLabel} • Goal: ${String.format(Locale.US, "%.1fh", goalHours)}/day",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 13.sp
                    )
                )
            }

            // Minimal Bar / Line Switcher
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (chartType == ChartType.BAR) FocusColors.Primary.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { chartType = ChartType.BAR }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.BarChart,
                        contentDescription = "Bar Chart",
                        tint = if (chartType == ChartType.BAR) FocusColors.Primary else FocusColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (chartType == ChartType.LINE) FocusColors.Primary.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { chartType = ChartType.LINE }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ShowChart,
                        contentDescription = "Line Chart",
                        tint = if (chartType == ChartType.LINE) FocusColors.Primary else FocusColors.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // Gridless Digital Wellbeing-style counters row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GridlessChartMetric(
                value = String.format(Locale.US, "%.1fh", totalHours),
                label = "Total study"
            )
            GridlessChartMetric(
                value = String.format(Locale.US, "%.1fh", avgHours),
                label = "Avg / day"
            )
            GridlessChartMetric(
                value = "$goalsMetCount/${dailyChart.size}",
                label = "Goals met"
            )
        }

        if (dailyChart.isNotEmpty()) {
            // Gridless Vico Chart (no guidelines, no axis lines, no ticks)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .padding(top = 4.dp)
            ) {
                val primaryColor = FocusColors.Primary.toArgb()

                val startAxis = rememberStartAxis(
                    valueFormatter = verticalAxisValueFormatter,
                    label = textComponent(
                        color = FocusColors.TextSecondary,
                        textSize = 10.sp
                    ),
                    guideline = null,
                    tick = null,
                    axis = null
                )

                val bottomAxis = rememberBottomAxis(
                    valueFormatter = horizontalAxisValueFormatter,
                    label = textComponent(
                        color = FocusColors.TextSecondary,
                        textSize = 10.sp
                    ),
                    guideline = null,
                    tick = null,
                    axis = null
                )

                when (chartType) {
                    ChartType.BAR -> {
                        val columnComponent = lineComponent(
                            color = FocusColors.Primary,
                            thickness = 14.dp,
                            shape = Shapes.roundedCornerShape(allPercent = 30)
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

            // Interactive Day Selector Row (Gridless)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
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
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isSelected) 8.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        day.isGoalMet -> FocusColors.EmeraldSuccess
                                        hasStudy -> FocusColors.Primary
                                        else -> FocusColors.TextMuted.copy(alpha = 0.35f)
                                    }
                                )
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = day.formattedStudyTime,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isSelected) FocusColors.Primary else FocusColors.TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                    }
                }
            }

            // Selected Day Details Line (Gridless)
            if (selectedDayIndex in dailyChart.indices) {
                val day = dailyChart[selectedDayIndex]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
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
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        )
                    }
                    Text(
                        text = "${day.formattedStudyTime} • ${day.sessionCount} sessions",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = FocusColors.Primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        } else {
            Text(
                text = "No study sessions recorded in this period",
                style = MaterialTheme.typography.bodyMedium.copy(color = FocusColors.TextSecondary),
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
    }
}

/**
 * Gridless Focus Streak & Consistency Visualization powered by Vico Charting & Compose.
 * Displays streak progression, qualifying days, and consistency curve without boxes or gridlines.
 */
@Composable
fun FocusStreakChartCard(
    summary: PeriodAnalyticsSummary,
    modifier: Modifier = Modifier
) {
    val streak = summary.streakInfo
    val dailyChart = summary.dailyChart
    val thresholdMinutes = streak.thresholdMinutes

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("focus_streak_chart"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Streak Header (Gridless)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.LocalFireDepartment,
                    contentDescription = "Streak",
                    tint = FocusColors.AmberOrange,
                    modifier = Modifier.size(26.dp)
                )

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${streak.currentStreak} day focus streak",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.Normal,
                                fontSize = 18.sp
                            )
                        )
                        if (streak.isStreakActiveToday) {
                            Text(
                                text = "• Active today",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.EmeraldSuccess,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Longest: ${streak.longestStreak} days • Total study days: ${streak.totalStudyDays} • Threshold: ${thresholdMinutes}m/day",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }

        // Streak Timeline Indicators (Gridless)
        if (dailyChart.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                dailyChart.forEach { day ->
                    val minutes = day.studyTimeMillis / (60 * 1000)
                    val qualified = minutes >= thresholdMinutes

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = day.dayLabel.take(1),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )

                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        qualified -> FocusColors.AmberOrange
                                        minutes > 0 -> FocusColors.Primary.copy(alpha = 0.35f)
                                        else -> Color.Transparent
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (qualified) {
                                Icon(
                                    imageVector = Icons.Filled.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(15.dp)
                                )
                            } else if (minutes > 0) {
                                Text(
                                    text = "${minutes}m",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = FocusColors.TextPrimary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(FocusColors.TextMuted.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }
                }
            }

            // Vico Consistency Trend Line (Gridless)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Daily focus duration trend",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 13.sp
                    )
                )

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
                        guideline = null,
                        tick = null,
                        axis = null
                    )

                    val bottomAxis = rememberBottomAxis(
                        valueFormatter = horizontalAxisValueFormatter,
                        label = textComponent(
                            color = FocusColors.TextSecondary,
                            textSize = 9.sp
                        ),
                        guideline = null,
                        tick = null,
                        axis = null
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

@Composable
private fun GridlessChartMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(
                color = FocusColors.TextPrimary,
                fontWeight = FontWeight.Normal,
                fontSize = 24.sp
            )
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = FocusColors.TextSecondary,
                fontSize = 13.sp
            )
        )
    }
}
