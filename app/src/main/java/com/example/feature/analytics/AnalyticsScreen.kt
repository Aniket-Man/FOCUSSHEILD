package com.example.feature.analytics

import android.content.Intent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.design.FocusCardStyle
import com.example.core.design.FocusColors
import com.example.core.ui.BottomTab
import com.example.core.ui.FocusBottomNavigation
import com.example.data.preferences.FocusPreferences
import com.example.feature.analytics.domain.AnalyticsPeriod
import com.example.feature.analytics.domain.PeriodAnalyticsSummary
import com.example.feature.analytics.domain.SubjectBreakdown
import com.example.feature.analytics.domain.TodayStudySummary
import com.example.feature.analytics.ui.FocusStreakChartCard
import com.example.feature.analytics.ui.WeeklyStudyHoursChartCard
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val FocusRingPalette = listOf(
    Color(0xFF2589F5), // Digital Wellbeing Blue
    Color(0xFFEA4335), // Digital Wellbeing Red
    Color(0xFFFBBC04), // Digital Wellbeing Amber/Yellow
    Color(0xFF1E8E3E), // Digital Wellbeing Green
    Color(0xFFA142F4), // Digital Wellbeing Purple
    Color(0xFF24C1E0)  // Digital Wellbeing Cyan
)

private data class FocusRingSlice(
    val label: String,
    val durationMillis: Long,
    val color: Color
)

@Composable
fun AnalyticsScreen(
    onNavigateToStartSession: () -> Unit,
    onTabSelected: (BottomTab) -> Unit = {},
    onNavigateToDigitalWellbeing: () -> Unit = {},
    viewModel: AnalyticsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showGoalDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = FocusColors.Background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = Modifier.testTag("analytics_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .align(Alignment.TopCenter),
                contentPadding = PaddingValues(
                    start = 24.dp,
                    end = 24.dp,
                    top = 20.dp,
                    bottom = FocusCardStyle.BottomNavClearance + 36.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 1. Minimalist Gridless Digital Wellbeing Header
                item {
                    AnalyticsDigitalWebHeader(
                        userName = uiState.preferences.userName,
                        onOpenGoalDialog = { showGoalDialog = true },
                        onShareReport = {
                            shareAnalyticsSummary(
                                context,
                                uiState.summary,
                                uiState.todaySummary,
                                uiState.preferences
                            )
                        }
                    )
                }

                // 2. Gridless Period Filter Tabs
                item {
                    PeriodFilterTabsGridless(
                        selectedPeriod = uiState.selectedPeriod,
                        onSelectPeriod = { viewModel.selectPeriod(it) }
                    )
                }

                // 3. Open Gridless Digital Wellbeing Multi-Segment Focus Ring + Floating Counters
                item {
                    FocusWellbeingHeroSection(
                        selectedPeriod = uiState.selectedPeriod,
                        summary = uiState.summary,
                        todaySummary = uiState.todaySummary,
                        preferences = uiState.preferences,
                        onStartSession = onNavigateToStartSession,
                        onNavigateToDigitalWellbeing = onNavigateToDigitalWellbeing
                    )
                }

                // 4. Gridless Focus Section: Daily Goal & Streak Completion
                item {
                    DailyGoalFocusCard(
                        todaySummary = uiState.todaySummary,
                        summary = uiState.summary,
                        dailyGoalMinutes = uiState.dailyGoalMinutes,
                        streakThresholdMinutes = uiState.streakThresholdMinutes,
                        onEditGoal = { showGoalDialog = true }
                    )
                }

                // 5. Gridless Focus Section: Study Sessions & Productivity
                item {
                    StudySessionsFocusCard(
                        summary = uiState.summary,
                        todaySummary = uiState.todaySummary
                    )
                }

                // 6. Gridless Focus Section: Planner & Schedule Consistency
                item {
                    PlannerConsistencyFocusCard(
                        summary = uiState.summary
                    )
                }

                // 7. Gridless Focus Section: YouTube Study Mode
                item {
                    YouTubeStudyFocusCard(
                        summary = uiState.summary
                    )
                }

                // 8. Gridless Focus Section: In-App / Study Browser
                item {
                    BrowserStudyFocusCard(
                        summary = uiState.summary
                    )
                }

                // 9. Gridless Visual Chart: Study Hours Bar/Line Chart
                item {
                    GridlessFocusSectionContainer {
                        WeeklyStudyHoursChartCard(
                            summary = uiState.summary,
                            dailyGoalMinutes = uiState.dailyGoalMinutes
                        )
                    }
                }

                // 10. Gridless Visual Chart: Focus Streak & Consistency Curve
                item {
                    GridlessFocusSectionContainer {
                        FocusStreakChartCard(
                            summary = uiState.summary
                        )
                    }
                }

                // 11. Gridless Focus Section: Overall Application Usage & Feature Engagement
                item {
                    OverallAppUsageFocusCard(
                        summary = uiState.summary
                    )
                }

                // 12. Gridless Focus Section: Subject Distribution
                item {
                    SubjectDistributionFocusCard(
                        subjects = uiState.summary.subjectBreakdowns,
                        totalSessions = uiState.summary.completedSessions
                    )
                }

                // 13. Gridless Focus Section: Distraction Shield & Breaks
                item {
                    DistractionShieldFocusCard(
                        summary = uiState.summary,
                        blockedNotificationsCount = uiState.preferences.blockedNotificationsCount
                    )
                }

                // 14. Gridless Export Report Action Row
                item {
                    ExportReportFocusCard(
                        onShare = {
                            shareAnalyticsSummary(
                                context,
                                uiState.summary,
                                uiState.todaySummary,
                                uiState.preferences
                            )
                        }
                    )
                }
            }

            FocusBottomNavigation(
                selectedTab = BottomTab.STATS,
                onTabSelected = onTabSelected,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (showGoalDialog) {
        GoalConfigDialog(
            currentGoalMinutes = uiState.dailyGoalMinutes,
            currentThresholdMinutes = uiState.streakThresholdMinutes,
            onDismiss = { showGoalDialog = false },
            onSave = { goalHours, goalMins, thresholdMins ->
                viewModel.updateDailyGoal(goalHours, goalMins)
                viewModel.updateStreakThreshold(thresholdMins)
                showGoalDialog = false
            }
        )
    }
}

/**
 * 100% Gridless Section Container: no boxes, no borders, no card backgrounds.
 * Separates statistical sections purely with open vertical breathing room and a subtle horizontal divider.
 */
@Composable
private fun GridlessFocusSectionContainer(
    onClick: (() -> Unit)? = null,
    testTagName: String? = null,
    showDivider: Boolean = true,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (testTagName != null) it.testTag(testTagName) else it }
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(top = 20.dp)
    ) {
        content()
        Spacer(modifier = Modifier.height(20.dp))
        if (showDivider) {
            HorizontalDivider(
                color = FocusColors.CardBorderSubtle.copy(alpha = 0.45f),
                thickness = 0.8.dp
            )
        }
    }
}

/**
 * Animated Progress Bar with smooth fill for daily goals and activity shares.
 */
@Composable
private fun FocusCardProgressBar(
    progress: Float,
    color: Color,
    height: androidx.compose.ui.unit.Dp = 6.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
        label = "focus_card_progress"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(FocusColors.SurfaceVariant.copy(alpha = 0.65f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress.coerceAtLeast(if (progress > 0f) 0.03f else 0f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(color.copy(alpha = 0.85f), color)
                    )
                )
        )
    }
}

/**
 * Clean inline percentage indicator without heavy box borders.
 */
@Composable
private fun FocusPercentageBadge(
    percentage: Int,
    color: Color,
    label: String? = null
) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = CircleShape
    ) {
        Text(
            text = if (label != null) "$percentage% $label" else "$percentage%",
            style = MaterialTheme.typography.labelMedium.copy(
                color = color,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp
            ),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * Gridless floating metric counter matching Digital Wellbeing's "47 Unlocks / 145 Notifications" style.
 * Zero background box, zero border, pure open typography.
 */
@Composable
private fun GridlessMetricCounter(
    value: String,
    label: String,
    accentColor: Color = FocusColors.TextPrimary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall.copy(
                color = accentColor,
                fontWeight = FontWeight.Normal,
                fontSize = 24.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = FocusColors.TextSecondary,
                fontSize = 13.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Gridless activity row matching Digital Wellbeing's "App activity details" row style:
 * Icon/dot on the left, title + duration/progress in the center, vertical separator line + metric on the right.
 */
@Composable
private fun GridlessActivityDetailRow(
    title: String,
    subtitle: String,
    rightValue: String,
    progress: Float,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(accentColor)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = FocusColors.TextPrimary,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = FocusColors.TextSecondary,
                    fontSize = 13.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
            FocusCardProgressBar(
                progress = progress,
                color = accentColor,
                height = 4.dp
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        // Vertical separator line matching Digital Wellbeing's right-side divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(FocusColors.CardBorderSubtle.copy(alpha = 0.65f))
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = rightValue,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = FocusColors.TextSecondary,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp
            )
        )
    }
}

@Composable
private fun AnalyticsDigitalWebHeader(
    userName: String,
    onOpenGoalDialog: () -> Unit,
    onShareReport: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Top action bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDone,
                    contentDescription = "Cloud Synced",
                    tint = FocusColors.EmeraldSuccess,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${userName.ifBlank { "Focus Scholar" }} • Cloud synced",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 13.sp
                    )
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onShareReport,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("share_analytics_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share Focus Report",
                        tint = FocusColors.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onOpenGoalDialog,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("edit_daily_goal_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Configure Daily Goal",
                        tint = FocusColors.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Large Digital Wellbeing style title
        Text(
            text = "Digital Wellbeing &\nfocus analytics",
            style = MaterialTheme.typography.headlineLarge.copy(
                color = FocusColors.TextPrimary,
                fontWeight = FontWeight.Normal,
                fontSize = 32.sp,
                lineHeight = 38.sp
            )
        )

        Spacer(modifier = Modifier.height(18.dp))

        // "Your Digital Wellbeing tools" subtitle row with amber bar chart icon
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.BarChart,
                contentDescription = null,
                tint = Color(0xFFFBBC04),
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = "Your Focus & Wellbeing tools",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = FocusColors.TextPrimary,
                    fontWeight = FontWeight.Normal,
                    fontSize = 19.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun PeriodFilterTabsGridless(
    selectedPeriod: AnalyticsPeriod,
    onSelectPeriod: (AnalyticsPeriod) -> Unit
) {
    val visiblePeriods = listOf(
        AnalyticsPeriod.TODAY,
        AnalyticsPeriod.LAST_7_DAYS,
        AnalyticsPeriod.LAST_30_DAYS,
        AnalyticsPeriod.THIS_MONTH
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        visiblePeriods.forEach { period ->
            val isSelected = period == selectedPeriod
            Column(
                modifier = Modifier
                    .clickable { onSelectPeriod(period) }
                    .padding(vertical = 6.dp)
                    .testTag("period_tab_${period.name.lowercase()}"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = period.label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = if (isSelected) FocusColors.Primary else FocusColors.TextSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        fontSize = 14.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .background(if (isSelected) FocusColors.Primary else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun FocusWellbeingHeroSection(
    selectedPeriod: AnalyticsPeriod,
    summary: PeriodAnalyticsSummary,
    todaySummary: TodayStudySummary,
    preferences: FocusPreferences,
    onStartSession: () -> Unit,
    onNavigateToDigitalWellbeing: () -> Unit
) {
    val isTodaySelected = selectedPeriod == AnalyticsPeriod.TODAY
    val centerHeadlineTime = if (isTodaySelected) {
        formatWellbeingDuration(todaySummary.totalStudyTimeMillis)
    } else {
        formatWellbeingDuration(summary.totalStudyTimeMillis)
    }
    val centerSubLabel = if (isTodaySelected) "TODAY" else summary.dateRangeLabel.uppercase()

    val ringSlices = remember(summary.subjectBreakdowns, summary.sourceBreakdowns, summary.totalStudyTimeMillis) {
        buildFocusRingSlices(summary)
    }

    val ringSize = 228.dp
    val density = LocalDensity.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circular Multi-Segment Ring with Outer Labels directly on background (no box!)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp),
            contentAlignment = Alignment.Center
        ) {
            val totalSliceMillis = ringSlices.sumOf { it.durationMillis }.coerceAtLeast(1L)
            val sweepProgress by animateFloatAsState(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
                label = "wellbeing_ring_sweep"
            )

            val emptyRingColor = FocusColors.SurfaceVariant
            Canvas(modifier = Modifier.size(ringSize)) {
                val strokeWidth = 13.dp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f
                )
                val arcSize = Size(diameter, diameter)

                if (ringSlices.isEmpty() || summary.totalStudyTimeMillis <= 0L) {
                    drawArc(
                        color = emptyRingColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                } else if (ringSlices.size == 1) {
                    drawArc(
                        color = ringSlices.first().color,
                        startAngle = -90f,
                        sweepAngle = 360f * sweepProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                } else {
                    val gapDegrees = 3.5f
                    val totalGap = gapDegrees * ringSlices.size
                    val availableSweep = (360f - totalGap).coerceAtLeast(180f)
                    var currentStartAngle = -90f

                    ringSlices.forEach { slice ->
                        val rawProportion = slice.durationMillis.toFloat() / totalSliceMillis.toFloat()
                        val sliceSweep = (rawProportion * availableSweep).coerceAtLeast(6f) * sweepProgress
                        drawArc(
                            color = slice.color,
                            startAngle = currentStartAngle,
                            sweepAngle = sliceSweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                        )
                        currentStartAngle += sliceSweep + gapDegrees
                    }
                }
            }

            // Outer Labels Positioned Around the Ring (matching Screenshot 2)
            if (ringSlices.isNotEmpty() && summary.totalStudyTimeMillis > 0L) {
                val labelRadiusPx = with(density) { 134.dp.toPx() }
                val gapDegrees = if (ringSlices.size > 1) 3.5f else 0f
                val availableSweep = (360f - gapDegrees * ringSlices.size).coerceAtLeast(180f)
                var runningAngle = -90f

                ringSlices.forEach { slice ->
                    val rawProportion = slice.durationMillis.toFloat() / totalSliceMillis.toFloat()
                    val sliceSweep = if (ringSlices.size == 1) 360f else (rawProportion * availableSweep).coerceAtLeast(6f)
                    val midAngleDeg = runningAngle + sliceSweep / 2f
                    val midAngleRad = Math.toRadians(midAngleDeg.toDouble())
                    val offsetX = (cos(midAngleRad) * labelRadiusPx).roundToInt()
                    val offsetY = (sin(midAngleRad) * labelRadiusPx).roundToInt()

                    Text(
                        text = truncateRingLabel(slice.label, 11),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = FocusColors.TextSecondary,
                            fontWeight = FontWeight.Normal,
                            fontSize = 13.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.offset { IntOffset(offsetX, offsetY) }
                    )

                    runningAngle += sliceSweep + gapDegrees
                }
            }

            // Center Text inside the Ring (TODAY / 9 hr, 34 min)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .size(ringSize - 44.dp)
                    .clip(CircleShape)
                    .clickable { onStartSession() }
                    .padding(8.dp)
            ) {
                Text(
                    text = centerSubLabel,
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontWeight = FontWeight.Normal,
                        letterSpacing = 0.8.sp,
                        fontSize = 12.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = centerHeadlineTime,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Normal,
                        fontSize = 26.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Open Gridless Counters Row (matching "47 Unlocks   145 Notifications" in Screenshot 2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GridlessMetricCounter(
                value = "${summary.completedSessions}",
                label = "Sessions"
            )
            GridlessMetricCounter(
                value = "${summary.blockedAttemptsCount + preferences.blockedNotificationsCount}",
                label = "Interceptions"
            )
            GridlessMetricCounter(
                value = "${summary.streakInfo.currentStreak}d",
                label = "Focus streak"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Subtle info note matching Screenshot 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = FocusColors.TextSecondary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Text(
                text = "This data is synced across your personal FocusShield profile and includes study sessions, YouTube study mode, and browser activity.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = FocusColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Unboxed "View app activity details" text link matching Screenshot 2
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToDigitalWellbeing() }
                .padding(vertical = 8.dp)
                .testTag("open_digital_wellbeing_row"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "View app activity details",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = FocusColors.TextPrimary,
                    fontWeight = FontWeight.Normal,
                    fontSize = 18.sp
                )
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Open Digital Wellbeing",
                tint = FocusColors.TextSecondary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(
            color = FocusColors.CardBorderSubtle.copy(alpha = 0.45f),
            thickness = 0.8.dp
        )
    }
}

/**
 * Gridless Focus Section #1: Daily Goal Progress & Streak Completion
 */
@Composable
private fun DailyGoalFocusCard(
    todaySummary: TodayStudySummary,
    summary: PeriodAnalyticsSummary,
    dailyGoalMinutes: Int,
    streakThresholdMinutes: Int,
    onEditGoal: () -> Unit
) {
    val goalHours = dailyGoalMinutes / 60
    val goalMins = dailyGoalMinutes % 60
    val goalFormatted = when {
        goalHours > 0 && goalMins > 0 -> "${goalHours}h ${goalMins}m"
        goalHours > 0 -> "${goalHours}h"
        else -> "${goalMins}m"
    }
    val progressFraction = (todaySummary.goalProgressPercentage / 100f).coerceIn(0f, 1f)
    val accentColor = if (todaySummary.isGoalAchieved) FocusColors.EmeraldSuccess else FocusColors.Primary
    val daysMetGoal = summary.dailyChart.count { it.isGoalMet }
    val totalDays = summary.dailyChart.size.coerceAtLeast(1)

    GridlessFocusSectionContainer(
        onClick = onEditGoal,
        testTagName = "daily_goal_focus_card"
    ) {
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
                    imageVector = Icons.Filled.EmojiEvents,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Daily focus goal",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Normal,
                            fontSize = 18.sp
                        )
                    )
                    Text(
                        text = "${todaySummary.formattedStudyTime} of $goalFormatted target",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            FocusPercentageBadge(
                percentage = todaySummary.goalProgressPercentage,
                color = accentColor
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        FocusCardProgressBar(
            progress = progressFraction,
            color = accentColor,
            height = 7.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GridlessMetricCounter(
                value = "$daysMetGoal/$totalDays",
                label = "Goal days"
            )
            GridlessMetricCounter(
                value = "${summary.streakInfo.longestStreak}d",
                label = "Best streak"
            )
            GridlessMetricCounter(
                value = "${streakThresholdMinutes}m",
                label = "Min threshold"
            )
        }
    }
}

/**
 * Gridless Focus Section #2: Study Sessions & Productivity
 */
@Composable
private fun StudySessionsFocusCard(
    summary: PeriodAnalyticsSummary,
    todaySummary: TodayStudySummary
) {
    val completionRate = if (summary.totalSessions > 0) {
        ((summary.completedSessions.toFloat() / summary.totalSessions.toFloat()) * 100f).toInt().coerceIn(0, 100)
    } else 0
    val avgSessionMinutes = if (summary.completedSessions > 0) {
        (summary.totalStudyTimeMillis / 60_000L / summary.completedSessions).toInt()
    } else 0
    val accentColor = Color(0xFF2589F5)

    GridlessFocusSectionContainer(
        testTagName = "study_sessions_focus_card"
    ) {
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
                    imageVector = Icons.Filled.Timer,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Study sessions",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Normal,
                            fontSize = 18.sp
                        )
                    )
                    Text(
                        text = "${summary.completedSessions} of ${summary.totalSessions} completed • All-time ${todaySummary.formattedAllTimeStudyTime}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            FocusPercentageBadge(
                percentage = completionRate,
                color = accentColor,
                label = "Done"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        FocusCardProgressBar(
            progress = completionRate / 100f,
            color = accentColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GridlessMetricCounter(
                value = summary.formattedTotalStudyTime,
                label = "Period total"
            )
            GridlessMetricCounter(
                value = summary.formattedAverageStudyPerDay,
                label = "Daily average"
            )
            GridlessMetricCounter(
                value = "${avgSessionMinutes}m",
                label = "Avg session"
            )
        }
    }
}

/**
 * Gridless Focus Section #3: Planner & Schedule Consistency
 */
@Composable
private fun PlannerConsistencyFocusCard(
    summary: PeriodAnalyticsSummary
) {
    val planner = summary.plannerStats
    val accentColor = Color(0xFFA142F4)

    GridlessFocusSectionContainer(
        testTagName = "planner_consistency_focus_card"
    ) {
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
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Planner consistency",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Normal,
                            fontSize = 18.sp
                        )
                    )
                    Text(
                        text = "${planner.activeSchedulesCount} active schedules • ${planner.autoStartSchedulesCount} Auto-Start",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            FocusPercentageBadge(
                percentage = planner.consistencyPercentage,
                color = accentColor
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        FocusCardProgressBar(
            progress = planner.consistencyPercentage / 100f,
            color = accentColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GridlessMetricCounter(
                value = "${planner.scheduledCount}",
                label = "Scheduled"
            )
            GridlessMetricCounter(
                value = "${planner.completedCount}",
                label = "Completed"
            )
            GridlessMetricCounter(
                value = "${planner.missedCount}",
                label = "Missed"
            )
        }
    }
}

/**
 * Gridless Focus Section #4: YouTube Study Mode
 */
@Composable
private fun YouTubeStudyFocusCard(
    summary: PeriodAnalyticsSummary
) {
    val ytStats = summary.youTubeStats
    val totalYtBlocked = ytStats.blockedShortsCount + ytStats.blockedUnapprovedCount
    val ytSharePct = if (summary.totalStudyTimeMillis > 0L) {
        ((ytStats.verifiedWatchTimeMillis.toFloat() / summary.totalStudyTimeMillis.toFloat()) * 100f)
            .toInt()
            .coerceIn(0, 100)
    } else 0
    val accentColor = Color(0xFFEA4335)

    GridlessFocusSectionContainer(
        testTagName = "youtube_study_focus_card"
    ) {
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
                    imageVector = Icons.Filled.PlayCircleFilled,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "YouTube study mode",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Normal,
                            fontSize = 18.sp
                        )
                    )
                    Text(
                        text = "${ytStats.formattedWatchTime} verified study watch time",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            FocusPercentageBadge(
                percentage = ytSharePct,
                color = accentColor,
                label = "Share"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        FocusCardProgressBar(
            progress = ytSharePct / 100f,
            color = accentColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GridlessMetricCounter(
                value = "${ytStats.verifiedActivityCount}",
                label = "Study videos"
            )
            GridlessMetricCounter(
                value = "${ytStats.blockedShortsCount}",
                label = "Shorts blocked"
            )
            GridlessMetricCounter(
                value = "$totalYtBlocked",
                label = "Total shielded"
            )
        }

        if (ytStats.topChannels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            val maxChanDuration = ytStats.topChannels.maxOf { it.second }.coerceAtLeast(1L)
            Column {
                ytStats.topChannels.take(3).forEach { (channelName, durationMillis) ->
                    val ratio = (durationMillis.toFloat() / maxChanDuration.toFloat()).coerceIn(0f, 1f)
                    GridlessActivityDetailRow(
                        title = channelName,
                        subtitle = formatWellbeingDuration(durationMillis),
                        rightValue = "${(ratio * 100).roundToInt()}%",
                        progress = ratio,
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

/**
 * Gridless Focus Section #5: In-App / Study Browser Analytics
 */
@Composable
private fun BrowserStudyFocusCard(
    summary: PeriodAnalyticsSummary
) {
    val browser = summary.browserStats
    val browserSharePct = if (summary.totalStudyTimeMillis > 0L) {
        ((browser.studyBrowseTimeMillis.toFloat() / summary.totalStudyTimeMillis.toFloat()) * 100f)
            .toInt()
            .coerceIn(0, 100)
    } else 0
    val accentColor = Color(0xFF24C1E0)

    GridlessFocusSectionContainer(
        testTagName = "browser_study_focus_card"
    ) {
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
                    imageVector = Icons.Filled.Language,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Study browser",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Normal,
                            fontSize = 18.sp
                        )
                    )
                    Text(
                        text = "${browser.formattedBrowseTime} educational web browsing",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            FocusPercentageBadge(
                percentage = browserSharePct,
                color = accentColor,
                label = "Share"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        FocusCardProgressBar(
            progress = browserSharePct / 100f,
            color = accentColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GridlessMetricCounter(
                value = browser.formattedBrowseTime,
                label = "Browse time"
            )
            GridlessMetricCounter(
                value = "${browser.studyBrowseSessionsCount}",
                label = "Web sessions"
            )
            GridlessMetricCounter(
                value = "${browser.blockedWebsitesCount}",
                label = "Sites blocked"
            )
        }

        if (browser.topDomains.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            val maxDomainDuration = browser.topDomains.maxOf { it.second }.coerceAtLeast(1L)
            Column {
                browser.topDomains.take(3).forEach { (domain, durationMillis) ->
                    val ratio = (durationMillis.toFloat() / maxDomainDuration.toFloat()).coerceIn(0f, 1f)
                    GridlessActivityDetailRow(
                        title = domain,
                        subtitle = formatWellbeingDuration(durationMillis),
                        rightValue = "${(ratio * 100).roundToInt()}%",
                        progress = ratio,
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

/**
 * Gridless Focus Section #6: Overall Application Usage & Feature Engagement
 */
@Composable
private fun OverallAppUsageFocusCard(
    summary: PeriodAnalyticsSummary
) {
    GridlessFocusSectionContainer(
        testTagName = "overall_usage_focus_card"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Feature engagement",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Normal,
                        fontSize = 18.sp
                    )
                )
                Text(
                    text = "${summary.studyDaysCount} of ${summary.totalDaysInRange} active days",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 13.sp
                    )
                )
            }

            FocusPercentageBadge(
                percentage = summary.consistencyPercentage,
                color = FocusColors.EmeraldSuccess,
                label = "Active"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (summary.sourceBreakdowns.isEmpty()) {
            FocusCardProgressBar(progress = 0f, color = FocusColors.EmeraldSuccess)
        } else {
            Column {
                summary.sourceBreakdowns.forEachIndexed { index, src ->
                    val color = FocusRingPalette[index % FocusRingPalette.size]
                    val pct = (src.percentage * 100).roundToInt()
                    GridlessActivityDetailRow(
                        title = src.sourceLabel,
                        subtitle = "${src.formattedDuration} • ${src.activityCount} sessions",
                        rightValue = "$pct%",
                        progress = src.percentage,
                        accentColor = color
                    )
                }
            }
        }
    }
}

/**
 * Gridless Focus Section #7: Subject Distribution
 */
@Composable
private fun SubjectDistributionFocusCard(
    subjects: List<SubjectBreakdown>,
    totalSessions: Int
) {
    GridlessFocusSectionContainer(
        testTagName = "subject_distribution_focus_card"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Subject activity details",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Normal,
                        fontSize = 18.sp
                    )
                )
                Text(
                    text = "$totalSessions total sessions across ${subjects.size} subjects",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 13.sp
                    )
                )
            }
            FocusPercentageBadge(
                percentage = if (subjects.isNotEmpty()) 100 else 0,
                color = FocusColors.Primary,
                label = "${subjects.size} Subjects"
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (subjects.isEmpty()) {
            Text(
                text = "No subject sessions recorded in this period.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = FocusColors.TextSecondary,
                    fontSize = 13.sp
                )
            )
        } else {
            Column {
                subjects.take(6).forEachIndexed { idx, item ->
                    val color = FocusRingPalette[idx % FocusRingPalette.size]
                    val pct = (item.percentage * 100).roundToInt()
                    GridlessActivityDetailRow(
                        title = item.subject,
                        subtitle = item.formattedDuration,
                        rightValue = "$pct%",
                        progress = item.percentage,
                        accentColor = color
                    )
                }
            }
        }
    }
}

/**
 * Gridless Focus Section #8: Distraction Shield & Break Balance
 */
@Composable
private fun DistractionShieldFocusCard(
    summary: PeriodAnalyticsSummary,
    blockedNotificationsCount: Int
) {
    val totalShielded = summary.blockedAttemptsCount + blockedNotificationsCount
    val accentColor = FocusColors.BlockedRed

    GridlessFocusSectionContainer(
        testTagName = "distraction_shield_focus_card"
    ) {
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
                    imageVector = Icons.Filled.Shield,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(22.dp)
                )
                Column {
                    Text(
                        text = "Distraction shield & breaks",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Normal,
                            fontSize = 18.sp
                        )
                    )
                    Text(
                        text = "$totalShielded total interceptions",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            FocusPercentageBadge(
                percentage = if (totalShielded > 0 || summary.completedSessions > 0) 100 else 0,
                color = accentColor,
                label = "Protected"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            GridlessMetricCounter(
                value = "${summary.blockedAttemptsCount}",
                label = "Apps & reels"
            )
            GridlessMetricCounter(
                value = "$blockedNotificationsCount",
                label = "Notifs muted"
            )
            GridlessMetricCounter(
                value = summary.formattedTotalBreakTime,
                label = "${summary.breakCount} breaks"
            )
        }

        if (summary.blockedAppStats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Column {
                summary.blockedAppStats.take(4).forEach { app ->
                    val pct = (app.percentage * 100).roundToInt()
                    GridlessActivityDetailRow(
                        title = app.appName,
                        subtitle = "${app.attemptCount} blocked attempts",
                        rightValue = "$pct%",
                        progress = app.percentage,
                        accentColor = accentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportReportFocusCard(
    onShare: () -> Unit
) {
    GridlessFocusSectionContainer(
        onClick = onShare,
        showDivider = false
    ) {
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
                    imageVector = Icons.Filled.Share,
                    contentDescription = null,
                    tint = FocusColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Export & share analytics report",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Normal,
                        fontSize = 17.sp
                    )
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = FocusColors.TextSecondary
            )
        }
    }
}

@Composable
private fun GoalConfigDialog(
    currentGoalMinutes: Int,
    currentThresholdMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int) -> Unit
) {
    var goalMinutesSlider by remember { mutableFloatStateOf(currentGoalMinutes.toFloat().coerceIn(30f, 720f)) }
    var thresholdMinutesSlider by remember { mutableFloatStateOf(currentThresholdMinutes.toFloat().coerceIn(5f, 120f)) }

    val hours = (goalMinutesSlider.toInt()) / 60
    val mins = (goalMinutesSlider.toInt()) % 60

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FocusColors.Surface,
        title = {
            Text(
                text = "Focus Goal & Streak",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = FocusColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Daily study goal",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = "${hours}h ${mins}m",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = FocusColors.Primary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Slider(
                        value = goalMinutesSlider,
                        onValueChange = { goalMinutesSlider = (it / 15f).roundToInt() * 15f },
                        valueRange = 30f..720f,
                        colors = SliderDefaults.colors(
                            thumbColor = FocusColors.Primary,
                            activeTrackColor = FocusColors.Primary
                        )
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Min streak threshold",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = "${thresholdMinutesSlider.toInt()} mins",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = FocusColors.AmberOrange,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    Slider(
                        value = thresholdMinutesSlider,
                        onValueChange = { thresholdMinutesSlider = (it / 5f).roundToInt() * 5f },
                        valueRange = 5f..120f,
                        colors = SliderDefaults.colors(
                            thumbColor = FocusColors.AmberOrange,
                            activeTrackColor = FocusColors.AmberOrange
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(hours, mins, thresholdMinutesSlider.toInt()) },
                colors = ButtonDefaults.buttonColors(containerColor = FocusColors.Primary)
            ) {
                Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FocusColors.TextSecondary)
            }
        }
    )
}

private fun buildFocusRingSlices(summary: PeriodAnalyticsSummary): List<FocusRingSlice> {
    if (summary.totalStudyTimeMillis <= 0L) return emptyList()

    val subjects = summary.subjectBreakdowns.filter { it.durationMillis > 0L }
    if (subjects.size >= 2) {
        val topFour = subjects.take(4)
        val otherMillis = subjects.drop(4).sumOf { it.durationMillis }
        val result = mutableListOf<FocusRingSlice>()
        topFour.forEachIndexed { idx, subj ->
            result.add(
                FocusRingSlice(
                    label = subj.subject,
                    durationMillis = subj.durationMillis,
                    color = FocusRingPalette[idx % FocusRingPalette.size]
                )
            )
        }
        if (otherMillis > 0L) {
            result.add(
                FocusRingSlice(
                    label = "Other",
                    durationMillis = otherMillis,
                    color = FocusRingPalette[4]
                )
            )
        }
        return result
    }

    val sources = summary.sourceBreakdowns.filter { it.durationMillis > 0L }
    if (sources.isNotEmpty()) {
        return sources.take(5).mapIndexed { idx, src ->
            FocusRingSlice(
                label = src.sourceLabel,
                durationMillis = src.durationMillis,
                color = FocusRingPalette[idx % FocusRingPalette.size]
            )
        }
    }

    if (subjects.isNotEmpty()) {
        val first = subjects.first()
        return listOf(FocusRingSlice(first.subject, first.durationMillis, FocusRingPalette[0]))
    }

    return listOf(FocusRingSlice("Focus", summary.totalStudyTimeMillis, FocusRingPalette[0]))
}

private fun truncateRingLabel(text: String, maxChars: Int): String {
    return if (text.length <= maxChars) text else text.take(maxChars - 1).trimEnd() + "…"
}

private fun formatWellbeingDuration(millis: Long): String {
    val totalMinutes = (millis / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "$hours hr, $minutes min"
        hours > 0 -> "$hours hr"
        totalMinutes > 0 -> "$minutes min"
        millis > 0L -> "< 1 min"
        else -> "0 min"
    }
}

private fun shareAnalyticsSummary(
    context: android.content.Context,
    summary: PeriodAnalyticsSummary,
    todaySummary: TodayStudySummary,
    preferences: FocusPreferences
) {
    val topSubjects = if (summary.subjectBreakdowns.isEmpty()) {
        "None recorded yet"
    } else {
        summary.subjectBreakdowns.take(3).joinToString(", ") { "${it.subject} (${it.formattedDuration})" }
    }

    val shareText = """
        FocusShield Study Report (${summary.dateRangeLabel} • ${preferences.userName})
        • Total Focus Time: ${summary.formattedTotalStudyTime} (All-Time: ${todaySummary.formattedAllTimeStudyTime})
        • Daily Goal Progress: ${todaySummary.goalProgressPercentage}%
        • Completed Sessions: ${summary.completedSessions}/${summary.totalSessions}
        • Schedule Consistency: ${summary.plannerStats.consistencyPercentage}%
        • Focus Streak: ${summary.streakInfo.currentStreak} days (Best: ${summary.streakInfo.longestStreak} days)
        • YouTube Study Time: ${summary.youTubeStats.formattedWatchTime}
        • Study Browser Time: ${summary.browserStats.formattedBrowseTime}
        • Distractions Shielded: ${summary.blockedAttemptsCount}
        • Top Subjects: $topSubjects
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "FocusShield Study Analytics Report")
        putExtra(Intent.EXTRA_TEXT, shareText)
    }
    context.startActivity(Intent.createChooser(intent, "Share Focus Report"))
}
