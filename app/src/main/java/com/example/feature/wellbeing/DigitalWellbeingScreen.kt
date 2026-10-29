package com.example.feature.wellbeing

import android.content.pm.PackageManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.BarChart
import com.example.FocusShieldApp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private val WELLBEING_SEGMENT_COLORS = listOf(
    Color(0xFF8B5CF6), // Primary Violet
    Color(0xFF3B82F6), // Electric Blue
    Color(0xFF10B981), // Emerald
    Color(0xFFF59E0B), // Amber Orange
    Color(0xFFEC4899), // Pink Rose
    Color(0xFF06B6D4), // Cyan
    Color(0xFF6366F1)  // Indigo
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

    val loadSnapshot: () -> Unit = {
        scope.launch {
            isRefreshing = true
            val loaded = withContext(Dispatchers.IO) {
                val persistedUnlocks = try {
                    app.dailyUnlockRepository.getTodayUnlock()?.unlockCount ?: 0
                } catch (_: Exception) {
                    0
                }
                DeviceUsageStatsHelper.getTodayDigitalWellbeingSnapshot(
                    context = context,
                    persistedUnlocks = persistedUnlocks
                )
            }
            snapshot = loaded
            isRefreshing = false
            // Also keep AnalyticsRepository screen time in sync
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = FocusSpacing.screenHorizontal, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
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
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = "Digital Wellbeing",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary,
                                fontSize = 20.sp
                            )
                        )
                        Text(
                            text = "Screen time, app timers & phone storage",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = loadSnapshot,
                        modifier = Modifier.testTag("digital_wellbeing_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh Usage Data",
                            tint = FocusColors.Primary
                        )
                    }
                    IconButton(
                        onClick = { DeviceUsageStatsHelper.openDigitalWellbeingSettings(context) },
                        modifier = Modifier.testTag("system_wellbeing_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = "System Digital Wellbeing",
                            tint = FocusColors.TextSecondary
                        )
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

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = FocusSpacing.screenHorizontal,
                    end = FocusSpacing.screenHorizontal,
                    top = 6.dp,
                    bottom = 36.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Usage Permission Banner (if not granted)
                if (!currentSnapshot.hasUsagePermission) {
                    item(key = "permission_banner") {
                        UsagePermissionRequiredBanner(
                            onGrantClick = { DeviceUsageStatsHelper.openUsageAccessSettings(context) }
                        )
                    }
                }

                // 2. Hero Card: Modern Digital Wellbeing Multi-Segment Circular Donut Ring
                item(key = "wellbeing_donut_hero") {
                    DigitalWellbeingDonutCard(
                        snapshot = currentSnapshot,
                        todayStudyTimeFormatted = todayStudyTimeFormatted,
                        focusRatioPercentage = focusRatioPercentage,
                        onAppClick = { appItem -> selectedAppForTimer = appItem }
                    )
                }

                // 3. 3-Metric Wellbeing Strip (Unlocks, App Opens, Study Focus)
                item(key = "wellbeing_metrics_strip") {
                    WellbeingQuickMetricsRow(
                        unlocksCount = currentSnapshot.unlocksCount,
                        totalAppLaunches = currentSnapshot.totalAppLaunches,
                        todayStudyTime = todayStudyTimeFormatted,
                        focusRatioPercentage = focusRatioPercentage
                    )
                }

                // 4. 7-Day Daily App Usage History Bar Chart (Vico)
                item(key = "wellbeing_weekly_vico_chart") {
                    WeeklyAppUsageVicoBarChartCard(
                        weeklyUsage = currentSnapshot.weeklyDailyUsage
                    )
                }

                // 4b. 24-Hour Screen Time Activity Chart
                item(key = "wellbeing_hourly_chart") {
                    HourlyActivityChartCard(
                        hourlyMinutes = currentSnapshot.hourlyUsageMinutes
                    )
                }

                // 5. Phone Storage & System Footprint Card
                item(key = "phone_storage_card") {
                    PhoneStorageOverviewCard(
                        usedBytes = currentSnapshot.storageUsedBytes,
                        totalBytes = currentSnapshot.storageTotalBytes,
                        freeBytes = currentSnapshot.storageFreeBytes,
                        trackedAppsCount = currentSnapshot.apps.size,
                        onManageStorageClick = { DeviceUsageStatsHelper.openStorageSettings(context) }
                    )
                }

                // 6. App Activity & Timers Header + Filter Chips
                item(key = "app_activity_header") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "App Activity & Timers",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = FocusColors.TextPrimary,
                                        fontSize = 18.sp
                                    )
                                )
                                Text(
                                    text = "Tap the hourglass icon on any app to set a daily limit",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                            }

                            Surface(
                                shape = FocusShapes.pill,
                                color = FocusColors.Primary.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, FocusColors.Primary.copy(alpha = 0.35f)),
                                modifier = Modifier
                                    .clip(FocusShapes.pill)
                                    .clickable { onNavigateToAppLimits() }
                                    .testTag("manage_all_app_limits_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.HourglassBottom,
                                        contentDescription = null,
                                        tint = FocusColors.Primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "All Limits",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = FocusColors.Primary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp
                                        )
                                    )
                                }
                            }
                        }

                        // Filter Selector Chips
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(FocusShapes.medium)
                                .background(FocusColors.SurfaceSubtle)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            WellbeingAppFilter.entries.forEach { filter ->
                                val isSelected = selectedFilter == filter
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(FocusShapes.small)
                                        .background(if (isSelected) FocusColors.Primary else Color.Transparent)
                                        .clickable { selectedFilter = filter }
                                        .padding(vertical = 8.dp)
                                        .testTag("wellbeing_filter_${filter.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = filter.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = if (isSelected) Color.White else FocusColors.TextSecondary,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 7. Per-App Usage List
                if (filteredApps.isEmpty()) {
                    item(key = "empty_apps_state") {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = FocusShapes.card,
                            color = FocusColors.Surface,
                            border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.HourglassEmpty,
                                    contentDescription = null,
                                    tint = FocusColors.TextMuted,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (selectedFilter == WellbeingAppFilter.LIMITED) {
                                        "No app timers configured yet"
                                    } else {
                                        "No app usage recorded yet today"
                                    },
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = FocusColors.TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (selectedFilter == WellbeingAppFilter.LIMITED) {
                                        "Tap the hourglass button next to any app in 'All Activity' to set a daily usage timer."
                                    } else {
                                        "As you use apps throughout the day, your screen time breakdown will appear here."
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextSecondary,
                                        textAlign = TextAlign.Center
                                    )
                                )
                            }
                        }
                    }
                } else {
                    val maxAppMillis = (currentSnapshot.apps.firstOrNull()?.usageMillis ?: 1L).coerceAtLeast(1L)
                    itemsIndexed(
                        items = filteredApps,
                        key = { _, item -> item.packageName }
                    ) { index, appItem ->
                        val segmentColor = WELLBEING_SEGMENT_COLORS[index % WELLBEING_SEGMENT_COLORS.size]
                        val existingLimit = limitsByPackage[appItem.packageName]
                        WellbeingAppUsageRow(
                            item = appItem,
                            maxUsageMillis = maxAppMillis,
                            totalScreenTimeMillis = currentSnapshot.totalScreenTimeMillis,
                            accentColor = segmentColor,
                            existingLimit = existingLimit,
                            onTimerClick = { selectedAppForTimer = appItem }
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
                scope.launch {
                    app.appLimitRepository.deleteLimit(targetApp.packageName)
                    selectedAppForTimer = null
                }
            }
        )
    }
}

@Composable
private fun UsagePermissionRequiredBanner(
    onGrantClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wellbeing_permission_banner"),
        shape = FocusShapes.card,
        color = FocusColors.CoralWarning.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, FocusColors.CoralWarning.copy(alpha = 0.45f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(FocusColors.CoralWarning.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = null,
                        tint = FocusColors.CoralWarning,
                        modifier = Modifier.size(22.dp)
                    )
                }
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
        }
    }
}

private data class DonutSegment(
    val label: String,
    val formattedDuration: String,
    val fraction: Float,
    val color: Color,
    val appItem: WellbeingAppUsageItem? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DigitalWellbeingDonutCard(
    snapshot: DigitalWellbeingSnapshot,
    todayStudyTimeFormatted: String,
    focusRatioPercentage: Int,
    onAppClick: (WellbeingAppUsageItem) -> Unit
) {
    val activeApps = remember(snapshot.apps) {
        snapshot.apps.filter { it.usageMillis > 0L }
    }
    val totalMillis = snapshot.totalScreenTimeMillis.coerceAtLeast(1L)

    val segments = remember(activeApps, totalMillis) {
        if (activeApps.isEmpty()) {
            emptyList()
        } else {
            val topApps = activeApps.take(5)
            val otherApps = activeApps.drop(5)
            val list = mutableListOf<DonutSegment>()
            topApps.forEachIndexed { index, item ->
                val frac = (item.usageMillis.toFloat() / totalMillis.toFloat()).coerceIn(0.02f, 1f)
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
                    val frac = (otherMillis.toFloat() / totalMillis.toFloat()).coerceIn(0.02f, 1f)
                    list.add(
                        DonutSegment(
                            label = "Other (${otherApps.size})",
                            formattedDuration = DeviceUsageStatsHelper.formatDurationHoursMins(otherMillis),
                            fraction = frac,
                            color = WELLBEING_SEGMENT_COLORS[5]
                        )
                    )
                }
            }
            // Normalize fractions to sum to 1f
            val sumFrac = list.sumOf { it.fraction.toDouble() }.toFloat().coerceAtLeast(0.001f)
            list.map { it.copy(fraction = it.fraction / sumFrac) }
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("digital_wellbeing_donut_card"),
        shape = FocusShapes.card,
        color = FocusColors.Surface,
        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Header Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(FocusColors.Primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PieChart,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DIGITAL WELLBEING OVERVIEW",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                    )
                }

                Surface(
                    shape = FocusShapes.pill,
                    color = FocusColors.EmeraldSuccess.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, FocusColors.EmeraldSuccess.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = "Focus $focusRatioPercentage%",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.EmeraldSuccess,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Multi-Segment Circular Digital Wellbeing Ring
            Box(
                modifier = Modifier.size(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    val strokeWidth = 22.dp.toPx()
                    val diameter = size.minDimension - strokeWidth
                    val topLeft = Offset(
                        (size.width - diameter) / 2f,
                        (size.height - diameter) / 2f
                    )
                    val arcSize = Size(diameter, diameter)

                    // Background Track Ring
                    drawArc(
                        color = trackColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    if (segments.isNotEmpty()) {
                        val gapDegrees = if (segments.size > 1) 4.5f else 0f
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
                                    cap = if (segments.size == 1) StrokeCap.Round else StrokeCap.Butt
                                )
                            )
                            currentStartAngle += sweep + gapDegrees
                        }
                    }
                }

                // Center Content inside the Digital Wellbeing Ring
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = "TODAY",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.TextSecondary,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            fontSize = 11.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = snapshot.formattedTotalScreenTime,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 34.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = FocusShapes.pill,
                        color = FocusColors.PrimaryContainer.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = FocusColors.Primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Study: $todayStudyTimeFormatted",
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

            Spacer(modifier = Modifier.height(20.dp))

            // Legend Chips for Top Apps inside the Ring
            if (segments.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    segments.forEach { seg ->
                        val pct = (seg.fraction * 100f).roundToInt()
                        Surface(
                            shape = FocusShapes.pill,
                            color = FocusColors.SurfaceSubtle,
                            border = BorderStroke(1.dp, seg.color.copy(alpha = 0.35f)),
                            modifier = Modifier
                                .clip(FocusShapes.pill)
                                .then(
                                    if (seg.appItem != null) {
                                        Modifier.clickable { onAppClick(seg.appItem) }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(seg.color)
                                )
                                Text(
                                    text = seg.label,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = FocusColors.TextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${seg.formattedDuration} ($pct%)",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = FocusColors.TextSecondary,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = if (snapshot.hasUsagePermission) {
                        "No foreground app screen time recorded yet today"
                    } else {
                        "Grant Usage Access above to view your per-app Digital Wellbeing ring"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }
    }
}

@Composable
private fun WellbeingQuickMetricsRow(
    unlocksCount: Int,
    totalAppLaunches: Int,
    todayStudyTime: String,
    focusRatioPercentage: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        WellbeingMetricTile(
            title = "Unlocks",
            value = unlocksCount.toString(),
            subtitle = "Device pickups",
            icon = Icons.Rounded.LockOpen,
            accentColor = Color(0xFF3B82F6),
            modifier = Modifier.weight(1f)
        )
        WellbeingMetricTile(
            title = "App Opens",
            value = totalAppLaunches.toString(),
            subtitle = "Sessions today",
            icon = Icons.Rounded.Apps,
            accentColor = Color(0xFFF59E0B),
            modifier = Modifier.weight(1f)
        )
        WellbeingMetricTile(
            title = "Study Focus",
            value = todayStudyTime,
            subtitle = "$focusRatioPercentage% focus ratio",
            icon = Icons.Rounded.Timer,
            accentColor = FocusColors.Primary,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun WellbeingMetricTile(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = FocusShapes.card,
        color = FocusColors.Surface,
        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
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
                    maxLines = 1
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    color = FocusColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                maxLines = 1
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
private fun WeeklyAppUsageVicoBarChartCard(
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
    val todayItem = remember(weeklyUsage) {
        weeklyUsage.lastOrNull { it.isToday } ?: weeklyUsage.lastOrNull()
    }

    // Use minutes if max usage across the week is < 1 hour so small usage values still render clear bars
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

    // Progress comparison badge (Today vs Daily Avg)
    val primaryAccent = FocusColors.Primary
    val emeraldAccent = FocusColors.EmeraldSuccess
    val coralAccent = FocusColors.CoralWarning
    val progressLabelAndColor = remember(todayItem, avgDailyMillis, primaryAccent, emeraldAccent, coralAccent) {
        val todayMs = todayItem?.usageMillis ?: 0L
        if (avgDailyMillis <= 0L || todayMs <= 0L) {
            "7-Day Trend" to primaryAccent
        } else {
            val diffPct = (((todayMs - avgDailyMillis).toFloat() / avgDailyMillis.toFloat()) * 100f).roundToInt()
            when {
                diffPct <= -5 -> "↓ ${-diffPct}% vs avg" to emeraldAccent
                diffPct >= 10 -> "↑ +$diffPct% vs avg" to coralAccent
                else -> "On Weekly Avg" to primaryAccent
            }
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("wellbeing_weekly_vico_bar_chart_card"),
        shape = FocusShapes.card,
        color = FocusColors.Surface,
        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Card Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(FocusColors.Primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.BarChart,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Daily App Usage History",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary,
                                fontSize = 15.sp
                            )
                        )
                        Text(
                            text = "Weekly screen time progress over the last 7 days",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = FocusShapes.pill,
                    color = progressLabelAndColor.second.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, progressLabelAndColor.second.copy(alpha = 0.35f))
                ) {
                    Text(
                        text = progressLabelAndColor.first,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = progressLabelAndColor.second,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
            }

            // Summary Stat Chips (Weekly Total, Daily Avg, Peak Day)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WellbeingWeeklyStatChip(
                    label = "Weekly Total",
                    value = DeviceUsageStatsHelper.formatDurationHoursMins(totalWeekMillis),
                    accentColor = FocusColors.Primary,
                    modifier = Modifier.weight(1f)
                )
                WellbeingWeeklyStatChip(
                    label = "Daily Average",
                    value = DeviceUsageStatsHelper.formatDurationHoursMins(avgDailyMillis),
                    accentColor = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f)
                )
                WellbeingWeeklyStatChip(
                    label = "Peak Day",
                    value = if (peakDay != null && peakDay.usageMillis > 0L) {
                        "${if (peakDay.isToday) "Today" else peakDay.dayOfWeekShort} (${peakDay.formattedUsage})"
                    } else {
                        "0m"
                    },
                    accentColor = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
            }

            // Vico Column (Bar) Chart
            if (weeklyUsage.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .padding(top = 4.dp)
                        .testTag("wellbeing_weekly_vico_bar_chart")
                ) {
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

                    val columnComponent = lineComponent(
                        color = FocusColors.Primary,
                        thickness = 16.dp,
                        shape = Shapes.roundedCornerShape(allPercent = 28)
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

                // Interactive Day Selector Chips under Vico Bar Chart
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    weeklyUsage.forEachIndexed { index, day ->
                        val isSelected = selectedDayIndex == index
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(FocusShapes.small)
                                .background(
                                    if (isSelected) FocusColors.Primary.copy(alpha = 0.15f)
                                    else Color.Transparent
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) FocusColors.Primary.copy(alpha = 0.4f)
                                    else Color.Transparent,
                                    shape = FocusShapes.small
                                )
                                .clickable { selectedDayIndex = index }
                                .padding(vertical = 6.dp, horizontal = 2.dp)
                                .testTag("wellbeing_weekly_day_chip_$index"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (day.isToday) "Today" else day.dayOfWeekShort.take(3),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isSelected || day.isToday) FocusColors.Primary else FocusColors.TextSecondary,
                                    fontWeight = if (isSelected || day.isToday) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 10.sp
                                ),
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = day.formattedUsage,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (day.usageMillis > 0L) FocusColors.TextPrimary else FocusColors.TextMuted,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 9.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }

                // Selected Day Detail Strip
                if (selectedDayIndex in weeklyUsage.indices) {
                    val selectedDay = weeklyUsage[selectedDayIndex]
                    Surface(
                        shape = FocusShapes.small,
                        color = FocusColors.SurfaceSubtle,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedDay.dateFormatted} (${if (selectedDay.isToday) "Today" else selectedDay.dayOfWeekShort})",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                text = buildString {
                                    append("Screen time: ${selectedDay.formattedUsage}")
                                    if (!selectedDay.topAppName.isNullOrBlank()) {
                                        append(" • Top: ${selectedDay.topAppName}")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.Primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WellbeingWeeklyStatChip(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(FocusShapes.small)
            .background(FocusColors.SurfaceSubtle)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = FocusColors.TextMuted,
                    fontSize = 10.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = accentColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HourlyActivityChartCard(
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

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wellbeing_hourly_timeline_card"),
        shape = FocusShapes.card,
        color = FocusColors.Surface,
        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Hourly Activity Timeline",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary,
                            fontSize = 15.sp
                        )
                    )
                    Text(
                        text = "Screen usage distribution across 24 hours",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }

                if (peakMinutes > 0) {
                    Surface(
                        shape = FocusShapes.pill,
                        color = FocusColors.PrimaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "Peak: $peakHourLabel (${peakMinutes}m)",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val maxVal = (hourlyMinutes.maxOrNull() ?: 1).coerceAtLeast(15)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                for (hour in 0 until 24) {
                    val mins = hourlyMinutes.getOrElse(hour) { 0 }
                    val fraction = (mins.toFloat() / maxVal.toFloat()).coerceIn(0.06f, 1f)
                    val isPeak = mins > 0 && hour == peakHourIndex
                    val barColor = when {
                        isPeak -> FocusColors.Primary
                        mins > 0 -> FocusColors.Primary.copy(alpha = 0.6f)
                        else -> FocusColors.SurfaceSubtle
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
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
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
}

@Composable
private fun PhoneStorageOverviewCard(
    usedBytes: Long,
    totalBytes: Long,
    freeBytes: Long,
    trackedAppsCount: Int,
    onManageStorageClick: () -> Unit
) {
    val totalGb = totalBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    val usedGb = usedBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    val freeGb = freeBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
    val usedFraction = if (totalBytes > 0L) {
        (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val usedPct = (usedFraction * 100f).roundToInt()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .clickable { onManageStorageClick() }
            .testTag("phone_storage_overview_card"),
        shape = FocusShapes.card,
        color = FocusColors.Surface,
        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF06B6D4).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Storage,
                            contentDescription = "Phone Storage",
                            tint = Color(0xFF06B6D4),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Phone Storage & App Footprint",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary,
                                fontSize = 15.sp
                            )
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "%.1f GB used of %.1f GB • %.1f GB free",
                                usedGb,
                                totalGb,
                                freeGb
                            ),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 12.sp
                            )
                        )
                    }
                }

                Surface(
                    shape = FocusShapes.pill,
                    color = Color(0xFF06B6D4).copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "$usedPct%",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color(0xFF06B6D4),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Multi-segment Storage Bar
            LinearProgressIndicator(
                progress = { usedFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = if (usedPct > 85) FocusColors.CoralWarning else Color(0xFF06B6D4),
                trackColor = FocusColors.SurfaceSubtle,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$trackedAppsCount installed apps monitored by Digital Wellbeing",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.TextMuted,
                        fontSize = 11.sp
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Storage Details",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.Primary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WellbeingAppUsageRow(
    item: WellbeingAppUsageItem,
    maxUsageMillis: Long,
    totalScreenTimeMillis: Long,
    accentColor: Color,
    existingLimit: AppLimitEntity?,
    onTimerClick: () -> Unit
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

    val progressFraction = if (item.usageMillis > 0L && maxUsageMillis > 0L) {
        (item.usageMillis.toFloat() / maxUsageMillis.toFloat()).coerceIn(0.04f, 1f)
    } else {
        0f
    }

    val sharePct = if (item.usageMillis > 0L && totalScreenTimeMillis > 0L) {
        ((item.usageMillis.toFloat() / totalScreenTimeMillis.toFloat()) * 100f).roundToInt()
    } else {
        0
    }

    val hasActiveLimit = existingLimit != null && existingLimit.isEnabled
    val limitMinutes = existingLimit?.dailyLimitMinutes ?: 0
    val usedMinutes = (item.usageMillis / 60_000L).toInt()
    val isLimitReached = hasActiveLimit && usedMinutes >= limitMinutes

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .clickable { onTimerClick() }
            .testTag("wellbeing_app_row_${item.packageName}"),
        shape = FocusShapes.card,
        color = FocusColors.Surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isLimitReached) FocusColors.CoralWarning.copy(alpha = 0.45f)
            else if (hasActiveLimit) FocusColors.Primary.copy(alpha = 0.3f)
            else FocusColors.CardBorderSubtle
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FocusColors.SurfaceSubtle),
                contentAlignment = Alignment.Center
            ) {
                if (appIconBitmap != null) {
                    Image(
                        bitmap = appIconBitmap,
                        contentDescription = item.appName,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Smartphone,
                        contentDescription = item.appName,
                        tint = accentColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // App Name, Category, Progress Bar, and Usage Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.appName,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary,
                            fontSize = 14.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.formattedUsage,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = if (item.usageMillis > 0L) FocusColors.TextPrimary else FocusColors.TextMuted,
                            fontSize = 14.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isLimitReached) FocusColors.CoralWarning else accentColor,
                    trackColor = FocusColors.SurfaceSubtle,
                    strokeCap = StrokeCap.Round
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = buildString {
                            append(item.categoryLabel)
                            if (item.launchCount > 0) {
                                append(" • ${item.launchCount} ")
                                append(if (item.launchCount == 1) "open" else "opens")
                            }
                            if (sharePct > 0) {
                                append(" • $sharePct%")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 11.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (hasActiveLimit) {
                        val limitText = DeviceUsageStatsHelper.formatDurationHoursMins(limitMinutes * 60_000L)
                        Text(
                            text = if (isLimitReached) "Limit reached ($limitText)" else "Timer: $limitText",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = if (isLimitReached) FocusColors.CoralWarning else FocusColors.Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Digital Wellbeing Hourglass Timer Action Button (min 48dp touch target)
            IconButton(
                onClick = onTimerClick,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("wellbeing_timer_btn_${item.packageName}")
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isLimitReached -> FocusColors.CoralWarning.copy(alpha = 0.16f)
                                hasActiveLimit -> FocusColors.Primary.copy(alpha = 0.16f)
                                else -> FocusColors.SurfaceSubtle
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (hasActiveLimit) Icons.Rounded.HourglassBottom else Icons.Rounded.HourglassEmpty,
                        contentDescription = "Set App Timer for ${item.appName}",
                        tint = when {
                            isLimitReached -> FocusColors.CoralWarning
                            hasActiveLimit -> FocusColors.Primary
                            else -> FocusColors.TextSecondary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
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
                Text(
                    text = "Set a daily Digital Wellbeing timer for ${appItem.appName}. Once reached, FocusShield pauses the app for the rest of the day.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 12.sp
                    )
                )

                // Selected duration display
                Surface(
                    shape = FocusShapes.medium,
                    color = FocusColors.PrimaryContainer.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, FocusColors.Primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
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
                }

                // Quick Preset Chips (2 rows of 3)
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

                // Fine-tune Slider
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
        },
        confirmButton = {
            Button(
                onClick = { onSaveLimit(selectedMinutes.coerceAtLeast(5)) },
                colors = ButtonDefaults.buttonColors(containerColor = FocusColors.Primary),
                modifier = Modifier.testTag("save_wellbeing_timer_button")
            ) {
                Text("Save Timer", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FocusColors.TextSecondary)
            }
        }
    )
}
