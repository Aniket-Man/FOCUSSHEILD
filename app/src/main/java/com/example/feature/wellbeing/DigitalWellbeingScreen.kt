package com.example.feature.wellbeing

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.FocusShieldApp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.util.DailyUsageHistoryItem
import com.example.core.util.DeviceUsageStatsHelper
import com.example.core.util.DigitalWellbeingSnapshot
import com.example.core.util.InstalledAppsProvider.toImageBitmapCached
import com.example.core.util.WellbeingAppUsageItem
import com.example.data.local.entity.AppLimitEntity
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.component.lineComponent
import com.patrykandpatrick.vico.compose.component.textComponent
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.component.shape.Shapes
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Authentic Android Digital Wellbeing segment palette (Blue, Red, Amber/Yellow, Green, Purple, Cyan)
private val WELLBEING_SEGMENT_COLORS = listOf(
    Color(0xFF2589F5), // Blue
    Color(0xFFEA4335), // Red
    Color(0xFFFBBC04), // Yellow / Amber
    Color(0xFF1E8E3E), // Green
    Color(0xFFA142F4), // Purple
    Color(0xFF24C1E0), // Cyan
    Color(0xFFEC4899)  // Rose
)

private enum class WellbeingAppFilter(val label: String) {
    ALL("All Activity"),
    MOST_USED("Most Used"),
    LIMITED("App Timers")
}

@Composable
fun DigitalWellbeingScreen(
    todayStudyTimeFormatted: String,
    focusRatioPercentage: Int,
    onNavigateBack: () -> Unit,
    onNavigateToAppLimits: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val app = remember { FocusShieldApp.instance }

    val appLimits by app.appLimitRepository.getAllLimitsFlow()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val limitsByPackage = remember(appLimits) {
        appLimits.associateBy { it.packageName }
    }

    var snapshot by remember {
        mutableStateOf<DigitalWellbeingSnapshot?>(null)
    }
    var isRefreshing by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf(WellbeingAppFilter.ALL) }
    var selectedAppForTimer by remember { mutableStateOf<WellbeingAppUsageItem?>(null) }
    var showAppActivityDetailsScreen by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = showAppActivityDetailsScreen) {
        showAppActivityDetailsScreen = false
    }

    val loadSnapshot: () -> Unit = {
        scope.launch {
            isRefreshing = true
            val loaded = withContext(Dispatchers.IO) {
                val persistedUnlocks = try {
                    app.dailyUnlockRepository.getTodayUnlock()?.unlockCount ?: 0
                } catch (_: Exception) {
                    0
                }
                val persistedNotifs = try {
                    app.preferencesRepository.preferencesFlow.first().blockedNotificationsCount
                } catch (_: Exception) {
                    0
                }
                DeviceUsageStatsHelper.getTodayDigitalWellbeingSnapshot(
                    context = context,
                    persistedUnlocks = persistedUnlocks,
                    persistedNotifications = persistedNotifs
                )
            }
            snapshot = loaded
            isRefreshing = false
            app.analyticsRepository.refreshScreenTimeAsync()
        }
    }

    LaunchedEffect(Unit) {
        loadSnapshot()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loadSnapshot()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            if (showAppActivityDetailsScreen) {
                // "App activity details" Top Bar (matches Screenshot 1)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FocusColors.SurfaceSubtle.copy(alpha = 0.85f))
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = { showAppActivityDetailsScreen = false },
                            modifier = Modifier.testTag("digital_wellbeing_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = FocusColors.TextPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "App activity details",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Normal,
                                color = FocusColors.TextPrimary,
                                fontSize = 21.sp
                            )
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showOverflowMenu = true },
                            modifier = Modifier.testTag("digital_wellbeing_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = "More options",
                                tint = FocusColors.TextPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                            containerColor = FocusColors.Surface
                        ) {
                            WellbeingAppFilter.entries.forEach { filter ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = filter.label,
                                            color = if (selectedFilter == filter) FocusColors.Primary else FocusColors.TextPrimary,
                                            fontWeight = if (selectedFilter == filter) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedFilter = filter
                                        showOverflowMenu = false
                                    }
                                )
                            }
                            HorizontalDivider(color = FocusColors.CardBorderSubtle)
                            DropdownMenuItem(
                                text = { Text("Refresh usage data", color = FocusColors.TextPrimary) },
                                onClick = {
                                    showOverflowMenu = false
                                    loadSnapshot()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Manage all app limits", color = FocusColors.TextPrimary) },
                                onClick = {
                                    showOverflowMenu = false
                                    onNavigateToAppLimits()
                                }
                            )
                        }
                    }
                }
            } else {
                // Main Digital Wellbeing Top Bar (matches Screenshot 2: clean back arrow + 3-dot menu)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("digital_wellbeing_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = FocusColors.TextPrimary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = loadSnapshot,
                            modifier = Modifier.testTag("digital_wellbeing_refresh_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh Usage Data",
                                tint = FocusColors.TextSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.testTag("system_wellbeing_settings_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "More options",
                                    tint = FocusColors.TextPrimary
                                )
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                                containerColor = FocusColors.Surface
                            ) {
                                DropdownMenuItem(
                                    text = { Text("App activity details", color = FocusColors.TextPrimary) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showAppActivityDetailsScreen = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Manage all app limits", color = FocusColors.TextPrimary) },
                                    onClick = {
                                        showOverflowMenu = false
                                        onNavigateToAppLimits()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("System Digital Wellbeing", color = FocusColors.TextPrimary) },
                                    onClick = {
                                        showOverflowMenu = false
                                        DeviceUsageStatsHelper.openDigitalWellbeingSettings(context)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = FocusColors.Background,
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("digital_wellbeing_screen")
    ) { innerPadding ->
        val currentSnapshot = snapshot
        if (currentSnapshot == null && isRefreshing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = FocusColors.Primary)
            }
        } else if (currentSnapshot != null) {
            val filteredApps = remember(currentSnapshot.apps, selectedFilter, limitsByPackage) {
                when (selectedFilter) {
                    WellbeingAppFilter.ALL -> currentSnapshot.apps
                    WellbeingAppFilter.MOST_USED -> currentSnapshot.apps.filter { it.usageMillis > 0L }
                    WellbeingAppFilter.LIMITED -> currentSnapshot.apps.filter { limitsByPackage.containsKey(it.packageName) }
                }
            }

            if (showAppActivityDetailsScreen) {
                // Dedicated "App activity details" view matching Screenshot 1 (100% gridless list)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 36.dp)
                ) {
                    if (filteredApps.isEmpty()) {
                        item(key = "empty_details_apps") {
                            GridlessEmptyAppsState(selectedFilter = selectedFilter)
                        }
                    } else {
                        itemsIndexed(
                            items = filteredApps,
                            key = { _, item -> "detail_${item.packageName}" }
                        ) { index, appItem ->
                            val segmentColor = WELLBEING_SEGMENT_COLORS[index % WELLBEING_SEGMENT_COLORS.size]
                            val existingLimit = limitsByPackage[appItem.packageName]
                            WellbeingAppUsageRow(
                                item = appItem,
                                accentColor = segmentColor,
                                existingLimit = existingLimit,
                                onTimerClick = { selectedAppForTimer = appItem },
                                onInfoClick = {
                                    openSystemAppDetails(context, appItem.packageName)
                                }
                            )
                        }
                    }
                }
            } else {
                // Main Digital Wellbeing Overview (Screenshot 2) + Gridless Stats & App Activity Details below
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 40.dp)
                ) {
                    // 1. Usage Permission Banner (if not granted)
                    if (!currentSnapshot.hasUsagePermission) {
                        item(key = "permission_banner") {
                            Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                                UsagePermissionRequiredBanner(
                                    onGrantClick = { DeviceUsageStatsHelper.openUsageAccessSettings(context) }
                                )
                            }
                        }
                    }

                    // 2. Hero Section: Large Title + "Your Digital Wellbeing tools" + Ring with Outer Labels + Unlocks & Notifications (Screenshot 2)
                    item(key = "wellbeing_donut_hero") {
                        DigitalWellbeingDonutSection(
                            snapshot = currentSnapshot,
                            todayStudyTimeFormatted = todayStudyTimeFormatted,
                            focusRatioPercentage = focusRatioPercentage,
                            onAppClick = { appItem -> selectedAppForTimer = appItem },
                            onOpenAppActivityDetails = { showAppActivityDetailsScreen = true }
                        )
                    }

                    // 3. Gridless Study Focus & Screen Ratio Strip
                    item(key = "wellbeing_metrics_strip") {
                        WellbeingQuickMetricsRow(
                            unlocksCount = currentSnapshot.unlocksCount,
                            notificationsCount = currentSnapshot.notificationsCount,
                            totalAppLaunches = currentSnapshot.totalAppLaunches,
                            todayStudyTime = todayStudyTimeFormatted,
                            focusRatioPercentage = focusRatioPercentage
                        )
                    }

                    // 4. Gridless App Activity Details Section (Screenshot 1 list directly accessible)
                    item(key = "app_activity_header") {
                        GridlessAppActivityHeader(
                            selectedFilter = selectedFilter,
                            onSelectFilter = { selectedFilter = it },
                            onNavigateToAppLimits = onNavigateToAppLimits,
                            onOpenFullScreenDetails = { showAppActivityDetailsScreen = true }
                        )
                    }

                    if (filteredApps.isEmpty()) {
                        item(key = "empty_apps_state") {
                            GridlessEmptyAppsState(selectedFilter = selectedFilter)
                        }
                    } else {
                        itemsIndexed(
                            items = filteredApps,
                            key = { _, item -> item.packageName }
                        ) { index, appItem ->
                            val segmentColor = WELLBEING_SEGMENT_COLORS[index % WELLBEING_SEGMENT_COLORS.size]
                            val existingLimit = limitsByPackage[appItem.packageName]
                            WellbeingAppUsageRow(
                                item = appItem,
                                accentColor = segmentColor,
                                existingLimit = existingLimit,
                                onTimerClick = { selectedAppForTimer = appItem },
                                onInfoClick = {
                                    openSystemAppDetails(context, appItem.packageName)
                                }
                            )
                        }
                    }

                    // 5. Gridless 7-Day Daily App Usage History
                    item(key = "wellbeing_weekly_vico_chart") {
                        WeeklyAppUsageGridlessSection(
                            weeklyUsage = currentSnapshot.weeklyDailyUsage
                        )
                    }

                    // 6. Gridless 24-Hour Screen Time Activity Timeline
                    item(key = "wellbeing_hourly_chart") {
                        HourlyActivityGridlessSection(
                            hourlyMinutes = currentSnapshot.hourlyUsageMinutes
                        )
                    }
                }
            }
        }
    }

    // Inline Digital Wellbeing "Set App Timer" Dialog
    selectedAppForTimer?.let { targetApp ->
        val existingLimit = limitsByPackage[targetApp.packageName]
        WellbeingAppTimerDialog(
            appItem = targetApp,
            existingLimit = existingLimit,
            onDismiss = { selectedAppForTimer = null },
            onSaveLimit = { limitMinutes ->
                if (existingLimit?.isStrictOverride == true) {
                    android.widget.Toast.makeText(
                        context,
                        "Strict Mode Active: ${targetApp.appName} timer is locked and cannot be modified or deleted.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    selectedAppForTimer = null
                    return@WellbeingAppTimerDialog
                }
                scope.launch {
                    app.appLimitRepository.saveLimit(
                        packageName = targetApp.packageName,
                        appName = targetApp.appName,
                        dailyLimitMinutes = limitMinutes,
                        isEnabled = true,
                        isStrictOverride = existingLimit?.isStrictOverride ?: false,
                        showRemindersBeforeLimit = existingLimit?.showRemindersBeforeLimit ?: true,
                        emergencyUsesAllowed = existingLimit?.emergencyUsesAllowed ?: 1
                    )
                    selectedAppForTimer = null
                }
            },
            onRemoveLimit = {
                if (existingLimit?.isStrictOverride == true) {
                    android.widget.Toast.makeText(
                        context,
                        "Strict Mode Active: You cannot delete ${targetApp.appName} app limit while Strict Mode is active.",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    selectedAppForTimer = null
                    return@WellbeingAppTimerDialog
                }
                scope.launch {
                    app.appLimitRepository.deleteLimit(targetApp.packageName)
                    selectedAppForTimer = null
                }
            }
        )
    }
}

private fun openSystemAppDetails(context: android.content.Context, packageName: String) {
    try {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

@Composable
private fun UsagePermissionRequiredBanner(
    onGrantClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("wellbeing_permission_banner")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = FocusColors.CoralWarning,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Usage Access Permission Needed",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Enable Usage Access so FocusShield can display your live Digital Wellbeing ring and per-app screen time.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 12.sp
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(
            onClick = onGrantClick,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("grant_usage_access_button"),
            colors = ButtonDefaults.buttonColors(containerColor = FocusColors.Primary),
            shape = FocusShapes.medium
        ) {
            Text(
                text = "Grant Usage Access",
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = FocusColors.CardBorderSubtle.copy(alpha = 0.5f))
    }
}

private data class DonutSegment(
    val label: String,
    val formattedDuration: String,
    val fraction: Float,
    val color: Color,
    val appItem: WellbeingAppUsageItem? = null
)

private data class PositionedArcLabel(
    val segment: DonutSegment,
    val midAngleDegrees: Float
)

/**
 * Gridless Digital Wellbeing Hero Section matching Screenshot 2:
 * - Large "Digital Wellbeing & parental controls" header
 * - "Your Digital Wellbeing tools" with amber bar chart icon
 * - Thin multi-segment circular ring with outer radial app labels around the circumference
 *   and "TODAY / 9 hr, 34 min" centered inside
 * - Gridless "Unlocks" and "Notifications" counters side-by-side
 * - Info row + "View app activity details" action row
 */
@Composable
private fun DigitalWellbeingDonutSection(
    snapshot: DigitalWellbeingSnapshot,
    todayStudyTimeFormatted: String,
    focusRatioPercentage: Int,
    onAppClick: (WellbeingAppUsageItem) -> Unit,
    onOpenAppActivityDetails: () -> Unit
) {
    val activeApps = remember(snapshot.apps) {
        snapshot.apps.filter { it.usageMillis > 0L }
    }
    val totalMillis = snapshot.totalScreenTimeMillis.coerceAtLeast(1L)

    val segments = remember(activeApps, totalMillis) {
        if (activeApps.isEmpty()) {
            emptyList()
        } else {
            val topApps = activeApps.take(4)
            val otherApps = activeApps.drop(4)
            val list = mutableListOf<DonutSegment>()
            topApps.forEachIndexed { index, item ->
                val frac = (item.usageMillis.toFloat() / totalMillis.toFloat()).coerceIn(0.03f, 1f)
                list.add(
                    DonutSegment(
                        label = item.appName,
                        formattedDuration = item.formattedUsage,
                        fraction = frac,
                        color = WELLBEING_SEGMENT_COLORS[index % WELLBEING_SEGMENT_COLORS.size],
                        appItem = item
                    )
                )
            }
            if (otherApps.isNotEmpty()) {
                val otherMillis = otherApps.sumOf { it.usageMillis }
                if (otherMillis > 0L) {
                    val frac = (otherMillis.toFloat() / totalMillis.toFloat()).coerceIn(0.03f, 1f)
                    list.add(
                        DonutSegment(
                            label = "Other",
                            formattedDuration = DeviceUsageStatsHelper.formatDurationHoursMins(otherMillis),
                            fraction = frac,
                            color = WELLBEING_SEGMENT_COLORS[4]
                        )
                    )
                }
            }
            val sumFrac = list.sumOf { it.fraction.toDouble() }.toFloat().coerceAtLeast(0.001f)
            list.map { it.copy(fraction = it.fraction / sumFrac) }
        }
    }

    val positionedLabels = remember(segments) {
        if (segments.isEmpty()) {
            emptyList()
        } else {
            val gapDegrees = if (segments.size > 1) 3.5f else 0f
            val totalGap = gapDegrees * segments.size
            val availableDegrees = (360f - totalGap).coerceAtLeast(10f)
            var currentStart = -90f
            segments.map { seg ->
                val sweep = (seg.fraction * availableDegrees).coerceAtLeast(2f)
                val midAngle = currentStart + sweep / 2f
                currentStart += sweep + gapDegrees
                PositionedArcLabel(segment = seg, midAngleDegrees = midAngle)
            }
        }
    }

    var startAnimation by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { startAnimation = true }
    val animProgress by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing),
        label = "donut_ring_anim"
    )

    val trackColor = FocusColors.SurfaceSubtle

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .testTag("digital_wellbeing_donut_card")
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Large Display Title matching Screenshot 2
        Text(
            text = "Digital Wellbeing &\nparental controls",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Normal,
                color = FocusColors.TextPrimary,
                fontSize = 33.sp,
                lineHeight = 41.sp
            )
        )

        Spacer(modifier = Modifier.height(26.dp))

        // Section Header: Amber BarChart Icon + "Your Digital Wellbeing tools"
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Rounded.BarChart,
                contentDescription = null,
                tint = Color(0xFFF5A623),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Your Digital Wellbeing tools",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Normal,
                    color = FocusColors.TextPrimary,
                    fontSize = 19.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Multi-Segment Thin Circular Ring with Outer Radial App Name Labels
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(276.dp)
                .clickable { onOpenAppActivityDetails() },
            contentAlignment = Alignment.Center
        ) {
            val density = LocalDensity.current
            val ringDiameterDp = 196.dp
            val strokeWidthDp = 11.dp
            val labelRadiusPx = with(density) { 126.dp.toPx() }

            // Circular Ring Canvas
            Canvas(
                modifier = Modifier.size(ringDiameterDp)
            ) {
                val strokeWidth = strokeWidthDp.toPx()
                val diameter = size.minDimension - strokeWidth
                val topLeft = Offset(
                    (size.width - diameter) / 2f,
                    (size.height - diameter) / 2f
                )
                val arcSize = Size(diameter, diameter)

                // Subtle Background Track Ring
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )

                if (segments.isNotEmpty()) {
                    val gapDegrees = if (segments.size > 1) 3.5f else 0f
                    val totalGap = gapDegrees * segments.size
                    val availableDegrees = (360f - totalGap).coerceAtLeast(10f) * animProgress
                    var currentStartAngle = -90f

                    segments.forEach { seg ->
                        val sweep = (seg.fraction * availableDegrees).coerceAtLeast(2f)
                        drawArc(
                            color = seg.color,
                            startAngle = currentStartAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Butt
                            )
                        )
                        currentStartAngle += sweep + gapDegrees
                    }
                }
            }

            // Outer Radial App Labels positioned around the circumference (like Screenshot 2)
            positionedLabels.forEach { item ->
                val rad = Math.toRadians(item.midAngleDegrees.toDouble())
                val cosVal = cos(rad).toFloat()
                val sinVal = sin(rad).toFloat()
                // Push horizontal offset slightly further so side labels never overlap the ring stroke
                val offsetX = (cosVal * labelRadiusPx * 1.08f).roundToInt()
                val offsetY = (sinVal * labelRadiusPx * 0.96f).roundToInt()

                Text(
                    text = item.segment.label,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontWeight = FontWeight.Normal,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .offset { IntOffset(offsetX, offsetY) }
                        .widthIn(max = 84.dp)
                        .then(
                            if (item.segment.appItem != null) {
                                Modifier.clickable { onAppClick(item.segment.appItem) }
                            } else {
                                Modifier
                            }
                        )
                )
            }

            // Center Content inside the Digital Wellbeing Ring: "TODAY" + "9 hr, 34 min"
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp)
            ) {
                Text(
                    text = "TODAY",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.8.sp,
                        fontSize = 12.sp
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = DeviceUsageStatsHelper.formatWellbeingRingDuration(snapshot.totalScreenTimeMillis),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Normal,
                        fontSize = 26.sp
                    ),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Two Gridless Stat Columns Below the Ring: Unlocks & Notifications (Screenshot 2)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = snapshot.unlocksCount.toString(),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Normal,
                        fontSize = 34.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Unlocks",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 15.sp
                    )
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = snapshot.notificationsCount.toString(),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Normal,
                        fontSize = 34.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 15.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Info Note Row matching Screenshot 2
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = FocusColors.TextSecondary,
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "This data is from your personal profile and doesn't include other profile activities.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = FocusColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // "View app activity details" Row matching Screenshot 2
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpenAppActivityDetails() }
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "Open App Activity Details",
                tint = FocusColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Gridless secondary metrics row (App Opens, Study Time, Focus Ratio) — no cards, no borders.
 */
@Composable
private fun WellbeingQuickMetricsRow(
    unlocksCount: Int,
    notificationsCount: Int,
    totalAppLaunches: Int,
    todayStudyTime: String,
    focusRatioPercentage: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        HorizontalDivider(color = FocusColors.CardBorderSubtle.copy(alpha = 0.45f))
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GridlessStatColumn(
                value = totalAppLaunches.toString(),
                label = "App opens",
                modifier = Modifier.weight(1f)
            )
            GridlessStatColumn(
                value = todayStudyTime,
                label = "Study time",
                modifier = Modifier.weight(1f)
            )
            GridlessStatColumn(
                value = "$focusRatioPercentage%",
                label = "Focus ratio",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(color = FocusColors.CardBorderSubtle.copy(alpha = 0.45f))
    }
}

@Composable
private fun GridlessStatColumn(
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
            ),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = FocusColors.TextSecondary,
                fontSize = 13.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun GridlessAppActivityHeader(
    selectedFilter: WellbeingAppFilter,
    onSelectFilter: (WellbeingAppFilter) -> Unit,
    onNavigateToAppLimits: () -> Unit,
    onOpenFullScreenDetails: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp, bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "App activity details",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Normal,
                    color = FocusColors.TextPrimary,
                    fontSize = 20.sp
                ),
                modifier = Modifier.clickable { onOpenFullScreenDetails() }
            )

            Text(
                text = "All Limits",
                style = MaterialTheme.typography.labelLarge.copy(
                    color = FocusColors.Primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                ),
                modifier = Modifier
                    .clickable { onNavigateToAppLimits() }
                    .padding(vertical = 4.dp)
                    .testTag("manage_all_app_limits_button")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Clean Gridless Text Filter Tabs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            WellbeingAppFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                Column(
                    modifier = Modifier
                        .clickable { onSelectFilter(filter) }
                        .padding(vertical = 4.dp)
                        .testTag("wellbeing_filter_${filter.name.lowercase()}"),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            color = if (isSelected) FocusColors.Primary else FocusColors.TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(2.dp)
                            .background(if (isSelected) FocusColors.Primary else Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
private fun GridlessEmptyAppsState(
    selectedFilter: WellbeingAppFilter
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.HourglassEmpty,
            contentDescription = null,
            tint = FocusColors.TextMuted,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (selectedFilter == WellbeingAppFilter.LIMITED) {
                "No app timers configured yet"
            } else {
                "No app usage recorded yet today"
            },
            style = MaterialTheme.typography.titleSmall.copy(
                fontWeight = FontWeight.Medium,
                color = FocusColors.TextPrimary
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (selectedFilter == WellbeingAppFilter.LIMITED) {
                "Tap the hourglass icon next to any app to set a daily usage timer."
            } else {
                "As you use apps throughout the day, your activity details will appear here."
            },
            style = MaterialTheme.typography.bodySmall.copy(
                color = FocusColors.TextSecondary,
                textAlign = TextAlign.Center
            )
        )
    }
}

/**
 * Gridless App Activity Details Row matching Screenshot 1:
 * - Left: App Icon (42.dp)
 * - Middle: App Name (18.sp) + natural duration text ("4 minutes", "1 minute", "Less than 1 minute")
 * - Right: Thin vertical divider line (1.dp x 28.dp) + Hourglass icon (or Info (i) icon for system apps)
 */
@Composable
private fun WellbeingAppUsageRow(
    item: WellbeingAppUsageItem,
    accentColor: Color,
    existingLimit: AppLimitEntity?,
    onTimerClick: () -> Unit,
    onInfoClick: () -> Unit
) {
    val context = LocalContext.current
    val appIconBitmap: ImageBitmap? = remember(item.packageName) {
        try {
            context.packageManager.getApplicationIcon(item.packageName)
                .toImageBitmapCached(item.packageName)
        } catch (_: Exception) {
            null
        }
    }

    val isSystemInfoOnly = remember(item.packageName) {
        DeviceUsageStatsHelper.isSystemExemptFromTimer(item.packageName)
    }

    val hasActiveLimit = existingLimit != null && existingLimit.isEnabled
    val limitMinutes = existingLimit?.dailyLimitMinutes ?: 0
    val usedMinutes = (item.usageMillis / 60_000L).toInt()
    val isLimitReached = hasActiveLimit && usedMinutes >= limitMinutes

    val durationSubtitle = remember(item.usageMillis, hasActiveLimit, limitMinutes, isLimitReached) {
        val base = DeviceUsageStatsHelper.formatWellbeingAppRowDuration(item.usageMillis)
        if (hasActiveLimit) {
            val limitFormatted = DeviceUsageStatsHelper.formatDurationHoursMins(limitMinutes * 60_000L)
            if (isLimitReached) "$base • Limit reached ($limitFormatted)" else "$base • Timer: $limitFormatted"
        } else {
            base
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isSystemInfoOnly) onInfoClick() else onTimerClick()
            }
            .padding(start = 24.dp, end = 14.dp, top = 14.dp, bottom = 14.dp)
            .testTag("wellbeing_app_row_${item.packageName}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Icon (matching Screenshot 1)
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (appIconBitmap != null) {
                Image(
                    bitmap = appIconBitmap,
                    contentDescription = item.appName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Smartphone,
                    contentDescription = item.appName,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(20.dp))

        // App Name + Duration Subtitle (clean two-line layout without progress bar clutter)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.appName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Normal,
                    color = FocusColors.TextPrimary,
                    fontSize = 18.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = durationSubtitle,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = if (isLimitReached) FocusColors.CoralWarning else FocusColors.TextSecondary,
                    fontWeight = FontWeight.Normal,
                    fontSize = 14.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Vertical Divider Line before Right Icon (matching Screenshot 1)
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(28.dp)
                .background(FocusColors.TextSecondary.copy(alpha = 0.32f))
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Right Action Icon: Info (i) for system apps, Hourglass for user apps (min 48dp touch target)
        IconButton(
            onClick = {
                if (isSystemInfoOnly) onInfoClick() else onTimerClick()
            },
            modifier = Modifier
                .size(48.dp)
                .testTag("wellbeing_timer_btn_${item.packageName}")
        ) {
            if (isSystemInfoOnly) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = "App info for ${item.appName}",
                    tint = FocusColors.TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Icon(
                    imageVector = if (hasActiveLimit) Icons.Rounded.HourglassBottom else Icons.Rounded.HourglassEmpty,
                    contentDescription = "Set App Timer for ${item.appName}",
                    tint = when {
                        isLimitReached -> FocusColors.CoralWarning
                        hasActiveLimit -> FocusColors.Primary
                        else -> FocusColors.TextSecondary
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/**
 * Gridless 7-Day Daily App Usage History Section (no card border, no chart gridlines, no boxed chips).
 */
@Composable
private fun WeeklyAppUsageGridlessSection(
    weeklyUsage: List<DailyUsageHistoryItem>,
    modifier: Modifier = Modifier
) {
    var selectedDayIndex by remember(weeklyUsage) {
        mutableIntStateOf(weeklyUsage.indexOfLast { it.isToday }.takeIf { it >= 0 } ?: (weeklyUsage.size - 1))
    }

    val totalWeekMillis = remember(weeklyUsage) {
        weeklyUsage.sumOf { it.usageMillis }
    }
    val activeDaysCount = remember(weeklyUsage) {
        weeklyUsage.count { it.usageMillis > 0L }.coerceAtLeast(1)
    }
    val avgDailyMillis = remember(totalWeekMillis, activeDaysCount) {
        totalWeekMillis / activeDaysCount
    }
    val peakDay = remember(weeklyUsage) {
        weeklyUsage.maxByOrNull { it.usageMillis }
    }

    val useMinutesScale = remember(weeklyUsage) {
        (weeklyUsage.maxOfOrNull { it.usageMillis } ?: 0L) in 1L until 3_600_000L
    }

    val entries = remember(weeklyUsage, useMinutesScale) {
        weeklyUsage.mapIndexed { index, day ->
            val yVal = if (useMinutesScale) {
                (day.usageMillis / 60_000f).coerceAtLeast(0f)
            } else {
                day.usageHours.coerceAtLeast(0f)
            }
            entryOf(index.toFloat(), yVal)
        }
    }

    val chartModelProducer = remember(entries) {
        ChartEntryModelProducer(entries)
    }

    val horizontalAxisValueFormatter = remember(weeklyUsage) {
        AxisValueFormatter<com.patrykandpatrick.vico.core.axis.AxisPosition.Horizontal.Bottom> { value, _ ->
            val index = value.toInt()
            if (index in weeklyUsage.indices) {
                val item = weeklyUsage[index]
                if (item.isToday) "Today" else item.dayOfWeekShort.take(3)
            } else {
                ""
            }
        }
    }

    val verticalAxisValueFormatter = remember(useMinutesScale) {
        AxisValueFormatter<com.patrykandpatrick.vico.core.axis.AxisPosition.Vertical.Start> { value, _ ->
            if (useMinutesScale) {
                "${value.roundToInt()}m"
            } else {
                if (value == 0f) "0h" else String.format(Locale.US, "%.1fh", value)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("wellbeing_weekly_vico_bar_chart_card"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HorizontalDivider(color = FocusColors.CardBorderSubtle.copy(alpha = 0.45f))

        Text(
            text = "Daily app usage history",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Normal,
                color = FocusColors.TextPrimary,
                fontSize = 20.sp
            )
        )

        // Gridless Weekly Summary Numbers (Weekly Total, Daily Average, Peak Day)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            GridlessStatColumn(
                value = DeviceUsageStatsHelper.formatDurationHoursMins(totalWeekMillis),
                label = "Weekly total",
                modifier = Modifier.weight(1f)
            )
            GridlessStatColumn(
                value = DeviceUsageStatsHelper.formatDurationHoursMins(avgDailyMillis),
                label = "Daily average",
                modifier = Modifier.weight(1f)
            )
            GridlessStatColumn(
                value = if (peakDay != null && peakDay.usageMillis > 0L) {
                    peakDay.formattedUsage
                } else {
                    "0m"
                },
                label = if (peakDay != null && peakDay.usageMillis > 0L) {
                    "Peak (${if (peakDay.isToday) "Today" else peakDay.dayOfWeekShort})"
                } else {
                    "Peak day"
                },
                modifier = Modifier.weight(1f)
            )
        }

        // Gridless Vico Column Chart (guideline = null, tick = null)
        if (weeklyUsage.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(165.dp)
                    .testTag("wellbeing_weekly_vico_bar_chart")
            ) {
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

                val columnComponent = lineComponent(
                    color = Color(0xFF2589F5),
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

            // Gridless Day Selector Row (no borders or boxes)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weeklyUsage.forEachIndexed { index, day ->
                    val isSelected = selectedDayIndex == index
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedDayIndex = index }
                            .padding(vertical = 6.dp)
                            .testTag("wellbeing_weekly_day_chip_$index"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (day.isToday) "Today" else day.dayOfWeekShort.take(3),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isSelected) FocusColors.TextPrimary else FocusColors.TextSecondary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 11.sp
                            ),
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = day.formattedUsage,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isSelected) Color(0xFF2589F5) else FocusColors.TextMuted,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontSize = 10.sp
                            ),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Gridless 24-Hour Screen Time Activity Timeline.
 */
@Composable
private fun HourlyActivityGridlessSection(
    hourlyMinutes: List<Int>
) {
    val peakHourIndex = remember(hourlyMinutes) {
        hourlyMinutes.indices.maxByOrNull { hourlyMinutes[it] } ?: 0
    }
    val peakMinutes = hourlyMinutes.getOrElse(peakHourIndex) { 0 }
    val peakHourLabel = remember(peakHourIndex) {
        when {
            peakHourIndex == 0 -> "12 AM"
            peakHourIndex < 12 -> "$peakHourIndex AM"
            peakHourIndex == 12 -> "12 PM"
            else -> "${peakHourIndex - 12} PM"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag("wellbeing_hourly_timeline_card")
    ) {
        HorizontalDivider(color = FocusColors.CardBorderSubtle.copy(alpha = 0.45f))
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hourly activity",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Normal,
                    color = FocusColors.TextPrimary,
                    fontSize = 20.sp
                )
            )
            if (peakMinutes > 0) {
                Text(
                    text = "Peak: $peakHourLabel (${peakMinutes}m)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 13.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val maxVal = (hourlyMinutes.maxOrNull() ?: 1).coerceAtLeast(15)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            for (hour in 0 until 24) {
                val mins = hourlyMinutes.getOrElse(hour) { 0 }
                val fraction = (mins.toFloat() / maxVal.toFloat()).coerceIn(0.05f, 1f)
                val isPeak = mins > 0 && hour == peakHourIndex
                val barColor = when {
                    isPeak -> Color(0xFF2589F5)
                    mins > 0 -> Color(0xFF2589F5).copy(alpha = 0.6f)
                    else -> FocusColors.SurfaceSubtle.copy(alpha = 0.5f)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(fraction)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            .background(barColor)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            listOf("12 AM", "6 AM", "12 PM", "6 PM", "11 PM").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.TextMuted,
                        fontSize = 10.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun WellbeingAppTimerDialog(
    appItem: WellbeingAppUsageItem,
    existingLimit: AppLimitEntity?,
    onDismiss: () -> Unit,
    onSaveLimit: (Int) -> Unit,
    onRemoveLimit: () -> Unit
) {
    val presetMinutes = listOf(15, 30, 45, 60, 90, 120)
    var selectedMinutes by remember(existingLimit) {
        mutableIntStateOf(existingLimit?.dailyLimitMinutes ?: 30)
    }

    val isStrictLocked = existingLimit?.isStrictOverride == true

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FocusColors.Surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(FocusColors.Primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HourglassBottom,
                        contentDescription = null,
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "App Timer • ${appItem.appName}",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary
                        )
                    )
                    Text(
                        text = "Used ${appItem.formattedUsage} today",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (isStrictLocked) {
                    Surface(
                        shape = FocusShapes.medium,
                        color = FocusColors.CoralWarning.copy(alpha = 0.14f),
                        border = BorderStroke(1.dp, FocusColors.CoralWarning.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Strict Mode Active: This app limit is locked and cannot be deleted or modified.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.CoralWarning,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            ),
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    Text(
                        text = "Set a daily Digital Wellbeing timer for ${appItem.appName}. Once reached, FocusShield pauses the app for the rest of the day.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "DAILY ALLOWANCE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = DeviceUsageStatsHelper.formatDurationHoursMins(selectedMinutes * 60_000L),
                        style = MaterialTheme.typography.headlineMedium.copy(
                            color = FocusColors.Primary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    )
                }

                if (!isStrictLocked) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        presetMinutes.chunked(3).forEach { rowPresets ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowPresets.forEach { mins ->
                                    val isSelected = selectedMinutes == mins
                                    val label = DeviceUsageStatsHelper.formatDurationHoursMins(mins * 60_000L)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(FocusShapes.pill)
                                            .background(if (isSelected) FocusColors.Primary else FocusColors.SurfaceSubtle)
                                            .clickable { selectedMinutes = mins }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isSelected) Color.White else FocusColors.TextPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Column {
                        Slider(
                            value = selectedMinutes.toFloat(),
                            onValueChange = { selectedMinutes = (it / 5f).roundToInt() * 5 },
                            valueRange = 5f..240f,
                            colors = SliderDefaults.colors(
                                thumbColor = FocusColors.Primary,
                                activeTrackColor = FocusColors.Primary
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("5m", style = MaterialTheme.typography.labelSmall.copy(color = FocusColors.TextMuted))
                            Text("4h", style = MaterialTheme.typography.labelSmall.copy(color = FocusColors.TextMuted))
                        }
                    }

                    if (existingLimit != null) {
                        OutlinedButton(
                            onClick = onRemoveLimit,
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, FocusColors.CoralWarning.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = FocusColors.CoralWarning)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Delete App Timer", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isStrictLocked) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = FocusColors.Primary),
                    modifier = Modifier.testTag("save_wellbeing_timer_button")
                ) {
                    Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { onSaveLimit(selectedMinutes.coerceAtLeast(5)) },
                    colors = ButtonDefaults.buttonColors(containerColor = FocusColors.Primary),
                    modifier = Modifier.testTag("save_wellbeing_timer_button")
                ) {
                    Text("Save Timer", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            if (!isStrictLocked) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = FocusColors.TextSecondary)
                }
            }
        }
    )
}
