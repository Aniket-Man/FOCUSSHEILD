package com.example.feature.analytics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.ui.BottomTab
import com.example.R
import com.example.core.ui.FocusBottomNavigation
import com.example.core.ui.FocusLottieAnimation
import com.example.feature.analytics.domain.AnalyticsPeriod
import com.example.feature.analytics.domain.BlockedAppStat
import com.example.feature.analytics.domain.PeriodAnalyticsSummary
import com.example.feature.analytics.domain.SourceBreakdown
import com.example.feature.analytics.domain.StudyDay
import com.example.feature.analytics.domain.SubjectBreakdown
import com.example.feature.analytics.domain.TopicBreakdown
import com.example.feature.analytics.ui.FocusStreakChartCard
import com.example.feature.analytics.ui.WeeklyStudyHoursChartCard
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AnalyticsScreen(
    viewModel: AnalyticsViewModel,
    onNavigateToStartSession: () -> Unit,
    onTabSelected: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var isGoalDialogOpen by remember { mutableStateOf(false) }
    var isSharingCard by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val shareScope = rememberCoroutineScope()

    Scaffold(
        bottomBar = {
            FocusBottomNavigation(
                selectedTab = BottomTab.STATS,
                onTabSelected = onTabSelected
            )
        },
        containerColor = FocusColors.Background,
        modifier = modifier.testTag("analytics_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.lg)
        ) {
            // 1. Header Section
            item {
                AnalyticsHeader(
                    onEditGoalClick = { isGoalDialogOpen = true },
                    onShareClick = {
                        if (!isSharingCard) {
                            shareScope.launch {
                                isSharingCard = true
                                try {
                                    com.example.feature.analytics.share.AnalyticsCardSharer.shareSummaryCard(
                                        context = context,
                                        summary = uiState.summary,
                                        dailyGoalMinutes = uiState.dailyGoalMinutes
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("AnalyticsScreen", "Failed to share card", e)
                                } finally {
                                    isSharingCard = false
                                }
                            }
                        }
                    },
                    isSharing = isSharingCard
                )
            }

            // 2. Period Filter Tabs
            item {
                Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                    PeriodFilterSelector(
                        selectedPeriod = uiState.selectedPeriod,
                        onPeriodSelected = { viewModel.selectPeriod(it) }
                    )
                }
            }

            // 3. Hero Metrics Cards Grid
            item {
                Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                    HeroMetricsGrid(summary = uiState.summary)
                }
            }

            // 4. Lottie Screen Time Graph Animation
            item {
                Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                    FocusLottieAnimation(
                        rawRes = R.raw.screentime_graph,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        iterations = 1
                    )
                }
            }

            // 5. Weekly & Period Study Hours Chart (Vico Visualizer)
            item {
                Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                    WeeklyStudyHoursChartCard(
                        summary = uiState.summary,
                        dailyGoalMinutes = uiState.dailyGoalMinutes
                    )
                }
            }

            // 5. Focus Streaks & Consistency Chart (Vico Visualizer)
            item {
                Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                    FocusStreakChartCard(
                        summary = uiState.summary
                    )
                }
            }

            // 6. Daily Goal Details Card
            item {
                Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                    DailyGoalAndStreakCard(
                        summary = uiState.summary,
                        dailyGoalMinutes = uiState.dailyGoalMinutes,
                        onEditGoalClick = { isGoalDialogOpen = true }
                    )
                }
            }

            // 7. Subject Breakdown Section
            if (uiState.summary.subjectBreakdowns.isNotEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                        SubjectBreakdownCard(
                            breakdowns = uiState.summary.subjectBreakdowns
                        )
                    }
                }
            }

            // 7. Topic Breakdown Section
            if (uiState.summary.topicBreakdowns.isNotEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                        TopicBreakdownCard(
                            topics = uiState.summary.topicBreakdowns
                        )
                    }
                }
            }

            // 8. Study Mode & Source Breakdown
            if (uiState.summary.sourceBreakdowns.isNotEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                        SourceBreakdownCard(
                            sources = uiState.summary.sourceBreakdowns
                        )
                    }
                }
            }

            // 9. YouTube Study Intelligence Section
            item {
                Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                    YouTubeIntelligenceCard(summary = uiState.summary)
                }
            }

            // 10. Distraction Shield Analytics
            item {
                Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                    DistractionShieldCard(summary = uiState.summary)
                }
            }

            // 11. Break & Rest Analytics
            item {
                Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                    BreakAnalyticsCard(summary = uiState.summary)
                }
            }

            // 12. Quick Study CTA
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FocusSpacing.screenHorizontal)
                        .padding(bottom = FocusSpacing.base)
                ) {
                    Button(
                        onClick = onNavigateToStartSession,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_session_from_analytics"),
                        shape = FocusShapes.medium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FocusColors.Primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Start Focus Session",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                    }
                }
            }
        }
    }

    if (isGoalDialogOpen) {
        GoalSettingsDialog(
            currentGoalMinutes = uiState.dailyGoalMinutes,
            onDismiss = { isGoalDialogOpen = false },
            onConfirm = { hours, minutes ->
                viewModel.updateDailyGoal(hours, minutes)
                isGoalDialogOpen = false
            }
        )
    }
}

@Composable
private fun AnalyticsHeader(
    onEditGoalClick: () -> Unit,
    onShareClick: () -> Unit,
    isSharing: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FocusSpacing.screenHorizontal, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Study Analytics",
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = FocusColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Real-time focus and distraction metrics",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = FocusColors.TextSecondary,
                    fontSize = 12.sp
                )
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(FocusShapes.pill)
                    .background(FocusColors.PrimaryContainer)
                    .clickable { onShareClick() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("share_analytics_button"),
                contentAlignment = Alignment.Center
            ) {
                if (isSharing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = FocusColors.Primary
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Share",
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Share",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .clip(FocusShapes.pill)
                    .background(FocusColors.PrimaryContainer)
                    .clickable { onEditGoalClick() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("edit_daily_goal_button"),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit Goal",
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Goal",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun PeriodFilterSelector(
    selectedPeriod: AnalyticsPeriod,
    onPeriodSelected: (AnalyticsPeriod) -> Unit
) {
    val periods = listOf(
        AnalyticsPeriod.TODAY,
        AnalyticsPeriod.LAST_7_DAYS,
        AnalyticsPeriod.LAST_30_DAYS,
        AnalyticsPeriod.THIS_MONTH
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.medium)
            .background(FocusColors.SurfaceSubtle)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        periods.forEach { period ->
            val isSelected = period == selectedPeriod
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(FocusShapes.small)
                    .background(if (isSelected) FocusColors.Surface else Color.Transparent)
                    .clickable { onPeriodSelected(period) }
                    .padding(vertical = 8.dp)
                    .testTag("period_tab_${period.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = period.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (isSelected) FocusColors.Primary else FocusColors.TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun HeroMetricsGrid(
    summary: PeriodAnalyticsSummary
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HeroMetricCard(
                title = "Total Study Time",
                value = summary.formattedTotalStudyTime,
                subtitle = "Pure focus duration",
                icon = Icons.Filled.Timer,
                iconTint = FocusColors.Primary,
                iconBg = FocusColors.PrimaryContainer,
                modifier = Modifier.weight(1f)
            )

            HeroMetricCard(
                title = "Sessions",
                value = "${summary.completedSessions}/${summary.totalSessions}",
                subtitle = "Completed sessions",
                icon = Icons.Filled.CheckCircle,
                iconTint = FocusColors.EmeraldSuccess,
                iconBg = FocusColors.EmeraldLight,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HeroMetricCard(
                title = "Daily Average",
                value = summary.formattedAverageStudyPerDay,
                subtitle = "${summary.consistencyPercentage}% consistency",
                icon = Icons.Filled.Schedule,
                iconTint = FocusColors.AmberOrange,
                iconBg = FocusColors.AmberLight,
                modifier = Modifier.weight(1f)
            )

            HeroMetricCard(
                title = "Distractions Blocked",
                value = summary.blockedAttemptsCount.toString(),
                subtitle = "Attempts shielded",
                icon = Icons.Filled.Shield,
                iconTint = FocusColors.CoralWarning,
                iconBg = FocusColors.CoralLight,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HeroMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(1.dp, FocusShapes.card, ambientColor = Color.Black.copy(alpha = 0.02f))
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    color = FocusColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = FocusColors.TextMuted,
                    fontSize = 10.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DailyGoalAndStreakCard(
    summary: PeriodAnalyticsSummary,
    dailyGoalMinutes: Int,
    onEditGoalClick: () -> Unit
) {
    val goalHours = dailyGoalMinutes / 60
    val goalMins = dailyGoalMinutes % 60
    val goalFormatted = if (goalMins > 0) "${goalHours}h ${goalMins}m" else "${goalHours}h"

    val streak = summary.streakInfo

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, FocusShapes.card, ambientColor = Color.Black.copy(alpha = 0.02f))
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Row 1: Streak Banner
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "${streak.currentStreak} Day Streak",
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
                            text = "Longest: ${streak.longestStreak} days • ${streak.totalStudyDays} total study days",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(FocusColors.CardBorderSubtle)
            )

            // Row 2: Target Goal info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DAILY TARGET",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$goalFormatted / day",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )
                }

                Text(
                    text = "Threshold: ${streak.thresholdMinutes}m / day",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextMuted,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun StudyTrendChartCard(
    summary: PeriodAnalyticsSummary,
    dailyGoalMinutes: Int
) {
    val dailyChart = summary.dailyChart
    val maxStudyTime = (dailyChart.maxOfOrNull { it.studyTimeMillis } ?: 1L).coerceAtLeast(3600 * 1000L)
    var selectedDayIndex by remember { mutableIntStateOf(-1) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, FocusShapes.card, ambientColor = Color.Black.copy(alpha = 0.02f))
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "STUDY DURATION TREND",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = summary.dateRangeLabel,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextMuted,
                            fontSize = 11.sp
                        )
                    )
                }

                if (summary.bestStudyDayLabel != null) {
                    Box(
                        modifier = Modifier
                            .clip(FocusShapes.pill)
                            .background(FocusColors.PrimaryContainer)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Peak: ${summary.bestStudyDayLabel}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Bars Row
            if (dailyChart.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    dailyChart.forEachIndexed { index, day ->
                        val ratio = (day.studyTimeMillis.toFloat() / maxStudyTime.toFloat()).coerceIn(0.04f, 1f)
                        val isSelected = selectedDayIndex == index

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    selectedDayIndex = if (selectedDayIndex == index) -1 else index
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            // Bar
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.65f)
                                    .fillMaxHeight(ratio)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        when {
                                            day.isGoalMet -> FocusColors.EmeraldSuccess
                                            day.isToday -> FocusColors.Primary
                                            isSelected -> FocusColors.Primary
                                            day.studyTimeMillis > 0 -> FocusColors.PrimaryLight
                                            else -> FocusColors.SurfaceSubtle
                                        }
                                    )
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            // Day Label
                            Text(
                                text = day.dayLabel.take(3),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (day.isToday || isSelected) FocusColors.Primary else FocusColors.TextSecondary,
                                    fontWeight = if (day.isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                // Selected Day Info Tooltip
                if (selectedDayIndex in dailyChart.indices) {
                    val day = dailyChart[selectedDayIndex]
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FocusShapes.small)
                            .background(FocusColors.SurfaceSubtle)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${day.dateFormatted} (${day.dayLabel})",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                text = "${day.formattedStudyTime} • ${day.sessionCount} sessions",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.Primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
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

@Composable
private fun SubjectBreakdownCard(
    breakdowns: List<SubjectBreakdown>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, FocusShapes.card, ambientColor = Color.Black.copy(alpha = 0.02f))
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "SUBJECT BREAKDOWN",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = FocusColors.TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            )

            breakdowns.forEach { item ->
                val barColor = when (item.subject.lowercase()) {
                    "physics" -> FocusColors.Primary
                    "chemistry" -> FocusColors.AmberOrange
                    "mathematics" -> FocusColors.EmeraldSuccess
                    else -> FocusColors.PrimaryLight
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.subject,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.formattedDuration,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = FocusColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            )
                            Text(
                                text = "(${(item.percentage * 100).roundToInt()}%)",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextMuted,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { item.percentage.coerceIn(0.02f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = barColor,
                        trackColor = FocusColors.SurfaceSubtle
                    )
                }
            }
        }
    }
}

@Composable
private fun TopicBreakdownCard(
    topics: List<TopicBreakdown>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, FocusShapes.card, ambientColor = Color.Black.copy(alpha = 0.02f))
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "TOPICS STUDIED",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = FocusColors.TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            )

            topics.take(6).forEach { topic ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = topic.topic,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = topic.subject,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextMuted,
                                fontSize = 10.sp
                            )
                        )
                    }

                    Text(
                        text = topic.formattedDuration,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceBreakdownCard(
    sources: List<SourceBreakdown>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, FocusShapes.card, ambientColor = Color.Black.copy(alpha = 0.02f))
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "STUDY MODE DISTRIBUTION",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = FocusColors.TextSecondary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp
                )
            )

            sources.forEach { source ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = source.sourceLabel,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = source.formattedDuration,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                        Text(
                            text = "(${source.activityCount} sessions)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextMuted,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YouTubeIntelligenceCard(
    summary: PeriodAnalyticsSummary
) {
    val yt = summary.youTubeStats

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, FocusShapes.card, ambientColor = Color.Black.copy(alpha = 0.02f))
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(FocusColors.CoralLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Tv,
                            contentDescription = "YouTube Study",
                            tint = FocusColors.CoralWarning,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = "YouTube Study Mode",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }

                Text(
                    text = "Verified Only",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.EmeraldSuccess,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = yt.formattedWatchTime,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                    Text(
                        text = "Educational Study",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextMuted,
                            fontSize = 10.sp
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = yt.blockedShortsCount.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FocusColors.CoralWarning,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                    Text(
                        text = "Shorts Blocked",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextMuted,
                            fontSize = 10.sp
                        )
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = yt.blockedUnapprovedCount.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FocusColors.Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                    Text(
                        text = "Off-Topic Blocked",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextMuted,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun DistractionShieldCard(
    summary: PeriodAnalyticsSummary
) {
    val stats = summary.blockedAppStats

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, FocusShapes.card, ambientColor = Color.Black.copy(alpha = 0.02f))
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DISTRACTION SHIELD REPORT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                )

                Text(
                    text = "${summary.blockedAttemptsCount} Total",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.CoralWarning,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }

            if (stats.isNotEmpty()) {
                stats.take(5).forEach { app ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        )

                        Text(
                            text = "${app.attemptCount} blocked",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = FocusColors.CoralWarning,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        )
                    }
                }
            } else {
                Text(
                    text = "No distraction attempts detected. Great focus!",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextMuted,
                        fontSize = 11.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun BreakAnalyticsCard(
    summary: PeriodAnalyticsSummary
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(1.dp, FocusShapes.card, ambientColor = Color.Black.copy(alpha = 0.02f))
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "BREAK & REST ANALYTICS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                )

                Icon(
                    imageVector = Icons.Outlined.Coffee,
                    contentDescription = null,
                    tint = FocusColors.AmberOrange,
                    modifier = Modifier.size(16.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Total Rest Time",
                        style = MaterialTheme.typography.bodySmall.copy(color = FocusColors.TextMuted, fontSize = 10.sp)
                    )
                    Text(
                        text = summary.formattedTotalBreakTime,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }

                Column {
                    Text(
                        text = "Total Breaks",
                        style = MaterialTheme.typography.bodySmall.copy(color = FocusColors.TextMuted, fontSize = 10.sp)
                    )
                    Text(
                        text = summary.breakCount.toString(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }

                Column {
                    Text(
                        text = "Avg Break Length",
                        style = MaterialTheme.typography.bodySmall.copy(color = FocusColors.TextMuted, fontSize = 10.sp)
                    )
                    Text(
                        text = summary.formattedAverageBreakDuration,
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalSettingsDialog(
    currentGoalMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (hours: Int, minutes: Int) -> Unit
) {
    var hours by remember { mutableIntStateOf(currentGoalMinutes / 60) }
    var minutes by remember { mutableIntStateOf(currentGoalMinutes % 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Set Daily Study Goal",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Choose your target pure study time for each day.",
                    style = MaterialTheme.typography.bodySmall.copy(color = FocusColors.TextSecondary)
                )

                Column {
                    Text(
                        text = "Target: ${hours}h ${minutes}m (${hours * 60 + minutes} mins)",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.Primary
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Hours: $hours",
                        style = MaterialTheme.typography.bodySmall.copy(color = FocusColors.TextSecondary)
                    )
                    Slider(
                        value = hours.toFloat(),
                        onValueChange = { hours = it.toInt() },
                        valueRange = 0f..12f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = FocusColors.Primary,
                            activeTrackColor = FocusColors.Primary
                        )
                    )

                    Text(
                        text = "Minutes: $minutes",
                        style = MaterialTheme.typography.bodySmall.copy(color = FocusColors.TextSecondary)
                    )
                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = { minutes = (it / 15).toInt() * 15 },
                        valueRange = 0f..45f,
                        steps = 2,
                        colors = SliderDefaults.colors(
                            thumbColor = FocusColors.Primary,
                            activeTrackColor = FocusColors.Primary
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(hours, minutes) },
                colors = ButtonDefaults.buttonColors(containerColor = FocusColors.Primary)
            ) {
                Text("Save Goal")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FocusColors.TextSecondary)
            }
        }
    )
}
