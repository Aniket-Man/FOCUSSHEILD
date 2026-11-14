package com.example.feature.wellbeing

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.FocusShieldApp
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.util.DeviceUsageStatsHelper
import com.example.feature.applimits.ui.AppLimitEditorHost
import com.example.feature.applimits.ui.AppLimitFeedback
import com.example.feature.applimits.ui.AppLimitsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
    val repository = remember { UsageStatsRepository(context.applicationContext) }
    val appLimitsViewModel: AppLimitsViewModel = viewModel()

    val appLimits by app.appLimitRepository.getAllLimitsFlow()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val limitsByPackage = remember(appLimits) { appLimits.associateBy { it.packageName } }

    var selectedTab by rememberSaveable { mutableStateOf(UsagePeriodTab.DAILY) }
    var selectedFilter by rememberSaveable { mutableStateOf(UsageCategoryFilter.ALL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showZeroUsageApps by rememberSaveable { mutableStateOf(false) }
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var selectedDayIndex by rememberSaveable { mutableIntStateOf(-1) }
    var showHelp by rememberSaveable { mutableStateOf(false) }

    var hasPermission by remember { mutableStateOf(DeviceUsageStatsHelper.hasUsageStatsPermission(context)) }
    var isLoading by remember { mutableStateOf(true) }
    var weekSummary by remember { mutableStateOf<UsageWeekSummary?>(null) }
    var categoryOverrides by remember { mutableStateOf(repository.getCategoryOverrides()) }

    var detailPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var editorPackage by rememberSaveable { mutableStateOf<String?>(null) }
    var editorName by rememberSaveable { mutableStateOf<String?>(null) }
    var editorRequiresExisting by rememberSaveable { mutableStateOf(false) }

    fun loadWeek(targetOffset: Int, preferredDayIndex: Int? = null) {
        scope.launch {
            isLoading = true
            val currentOverrides = repository.getCategoryOverrides()
            categoryOverrides = currentOverrides
            val (perm, summary) = withContext(Dispatchers.IO) {
                repository.loadWeekSummary(targetOffset, currentOverrides)
            }
            hasPermission = perm
            weekSummary = summary
            val defaultIdx = when {
                preferredDayIndex != null -> preferredDayIndex.coerceIn(0, 6)
                selectedDayIndex in 0..6 && targetOffset == weekOffset -> selectedDayIndex
                targetOffset == 0 -> summary.days.lastOrNull { !it.isFuture }?.dayIndexInWeek ?: 0
                else -> 6
            }
            val resolvedDay = if (summary.days.getOrNull(defaultIdx)?.isFuture == true) {
                summary.days.lastOrNull { !it.isFuture }?.dayIndexInWeek ?: 0
            } else {
                defaultIdx
            }
            selectedDayIndex = resolvedDay
            isLoading = false
            if (perm) {
                app.analyticsRepository.refreshScreenTimeAsync()
            }
        }
    }

    LaunchedEffect(Unit) {
        loadWeek(weekOffset)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                loadWeek(weekOffset)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentWeek = weekSummary
    val activeDayIndex = selectedDayIndex.coerceIn(0, 6)
    val activeDay = currentWeek?.days?.getOrNull(activeDayIndex)

    val canStepForward = when (selectedTab) {
        UsagePeriodTab.WEEKLY -> weekOffset < 0
        UsagePeriodTab.DAILY -> weekOffset < 0 || (currentWeek?.days?.getOrNull(activeDayIndex + 1)?.isFuture == false)
    }

    fun stepPeriod(delta: Int) {
        if (selectedTab == UsagePeriodTab.WEEKLY) {
            val nextWeek = (weekOffset + delta).coerceAtMost(0)
            if (nextWeek != weekOffset) {
                weekOffset = nextWeek
                loadWeek(nextWeek, preferredDayIndex = 6)
            }
        } else {
            val nextDay = activeDayIndex + delta
            if (nextDay in 0..6) {
                val targetDay = currentWeek?.days?.getOrNull(nextDay)
                if (targetDay != null && !targetDay.isFuture) {
                    selectedDayIndex = nextDay
                }
            } else if (nextDay < 0) {
                val prevWeek = weekOffset - 1
                weekOffset = prevWeek
                loadWeek(prevWeek, preferredDayIndex = 6)
            } else if (nextDay > 6 && weekOffset < 0) {
                val nextWeek = weekOffset + 1
                weekOffset = nextWeek
                loadWeek(nextWeek, preferredDayIndex = 0)
            }
        }
    }

    val widthModifier = Modifier.widthIn(max = 680.dp).fillMaxWidth()

    Scaffold(
        containerColor = FocusColors.Background,
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .testTag("digital_wellbeing_screen"),
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FocusSpacing.sm, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = widthModifier,
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.testTag("digital_wellbeing_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = stringResource(R.string.action_back),
                                    tint = FocusColors.TextPrimary
                                )
                            }
                            Text(
                                text = stringResource(R.string.usage_stats_title),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = FocusColors.TextPrimary
                            )
                        }
                        Surface(
                            color = FocusColors.Surface,
                            contentColor = FocusColors.TextPrimary,
                            shape = FocusShapes.pill,
                            border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                            modifier = Modifier
                                .padding(end = FocusSpacing.sm)
                                .minimumInteractiveComponentSize()
                                .clip(FocusShapes.pill)
                                .clickable { showHelp = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.HelpOutline,
                                    contentDescription = null,
                                    tint = FocusColors.TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.blocks_help_pill),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                    }
                }

                // Daily / Weekly Full-Width Tab Bar
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = FocusColors.Background,
                    contentColor = FocusColors.TextPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                            height = 2.5.dp,
                            color = FocusColors.TextPrimary
                        )
                    },
                    divider = {
                        HorizontalDivider(color = FocusColors.CardBorderSubtle, thickness = 1.dp)
                    }
                ) {
                    Tab(
                        selected = selectedTab == UsagePeriodTab.DAILY,
                        onClick = { selectedTab = UsagePeriodTab.DAILY },
                        text = {
                            Text(
                                text = stringResource(R.string.usage_tab_daily),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (selectedTab == UsagePeriodTab.DAILY) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (selectedTab == UsagePeriodTab.DAILY) FocusColors.TextPrimary else FocusColors.TextSecondary
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == UsagePeriodTab.WEEKLY,
                        onClick = { selectedTab = UsagePeriodTab.WEEKLY },
                        text = {
                            Text(
                                text = stringResource(R.string.usage_tab_weekly),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (selectedTab == UsagePeriodTab.WEEKLY) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (selectedTab == UsagePeriodTab.WEEKLY) FocusColors.TextPrimary else FocusColors.TextSecondary
                            )
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        if (currentWeek == null && isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = FocusColors.Primary)
            }
        } else if (currentWeek != null) {
            val baseApps = if (selectedTab == UsagePeriodTab.DAILY) {
                activeDay?.apps.orEmpty()
            } else {
                currentWeek.apps
            }

            val trimmedQuery = searchQuery.trim().lowercase(Locale.getDefault())
            val filterMatchedApps = remember(baseApps, selectedFilter, trimmedQuery) {
                baseApps.filter { item ->
                    val matchesFilter = item.matchesFilter(selectedFilter)
                    val matchesQuery = trimmedQuery.isEmpty() ||
                        item.appName.lowercase(Locale.getDefault()).contains(trimmedQuery) ||
                        item.packageName.lowercase(Locale.getDefault()).contains(trimmedQuery)
                    matchesFilter && matchesQuery
                }
            }

            val nonZeroApps = remember(filterMatchedApps, selectedFilter) {
                filterMatchedApps.filter { it.millisForFilter(selectedFilter) > 0L }
            }
            val zeroApps = remember(filterMatchedApps, selectedFilter) {
                filterMatchedApps.filter { it.millisForFilter(selectedFilter) <= 0L }
            }
            val displayedApps = remember(nonZeroApps, zeroApps, showZeroUsageApps, trimmedQuery) {
                if (showZeroUsageApps || trimmedQuery.isNotEmpty() || nonZeroApps.isEmpty()) {
                    filterMatchedApps
                } else {
                    nonZeroApps
                }
            }

            val heroTotalMillis = if (selectedTab == UsagePeriodTab.DAILY) {
                activeDay?.millisForFilter(selectedFilter) ?: 0L
            } else {
                currentWeek.millisForFilter(selectedFilter)
            }

            val distractingTotal = if (selectedTab == UsagePeriodTab.DAILY) {
                activeDay?.distractingMillis ?: 0L
            } else {
                currentWeek.distractingMillis
            }
            val productiveTotal = if (selectedTab == UsagePeriodTab.DAILY) {
                activeDay?.productiveMillis ?: 0L
            } else {
                currentWeek.productiveMillis
            }
            val othersTotal = if (selectedTab == UsagePeriodTab.DAILY) {
                activeDay?.othersMillis ?: 0L
            } else {
                currentWeek.othersMillis
            }

            val periodSubtitle = if (selectedTab == UsagePeriodTab.DAILY) {
                when {
                    activeDay == null -> ""
                    activeDay.isToday -> stringResource(R.string.usage_today_prefix, activeDay.shortDateFormatted)
                    activeDay.isYesterday -> stringResource(R.string.usage_yesterday_prefix, activeDay.shortDateFormatted)
                    else -> activeDay.shortDateFormatted
                }
            } else {
                currentWeek.rangeLabel
            }

            val weekDeltaPercent = if (selectedTab == UsagePeriodTab.WEEKLY && selectedFilter == UsageCategoryFilter.ALL) {
                currentWeek.deltaPercentVsLastWeek()
            } else {
                null
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item(key = "app_limit_feedback") {
                    Box(modifier = widthModifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                        AppLimitFeedback(appLimitsViewModel)
                    }
                }

                if (!hasPermission) {
                    item(key = "usage_permission_banner") {
                        Box(modifier = widthModifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                            UsageAccessPermissionCard(
                                onGrantClick = { DeviceUsageStatsHelper.openUsageAccessSettings(context) }
                            )
                        }
                    }
                }

                // Category Filter Chips Row
                item(key = "category_filter_chips") {
                    Row(
                        modifier = widthModifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = FocusSpacing.screenHorizontal),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UsageFilterChip(
                            label = stringResource(R.string.usage_filter_all),
                            selected = selectedFilter == UsageCategoryFilter.ALL,
                            onClick = { selectedFilter = UsageCategoryFilter.ALL }
                        )
                        UsageFilterChip(
                            label = stringResource(R.string.usage_filter_distracting),
                            selected = selectedFilter == UsageCategoryFilter.DISTRACTING,
                            onClick = { selectedFilter = UsageCategoryFilter.DISTRACTING }
                        )
                        UsageFilterChip(
                            label = stringResource(R.string.usage_filter_productive),
                            selected = selectedFilter == UsageCategoryFilter.PRODUCTIVE,
                            onClick = { selectedFilter = UsageCategoryFilter.PRODUCTIVE }
                        )
                        UsageFilterChip(
                            label = stringResource(R.string.usage_filter_others),
                            selected = selectedFilter == UsageCategoryFilter.OTHERS,
                            onClick = { selectedFilter = UsageCategoryFilter.OTHERS }
                        )
                    }
                }

                // Date Stepper + Hero Duration + Weekly Comparison Badge
                item(key = "hero_period_header") {
                    Row(
                        modifier = widthModifier.padding(horizontal = FocusSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { stepPeriod(-1) }) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronLeft,
                                contentDescription = stringResource(R.string.usage_prev_period),
                                tint = FocusColors.TextPrimary
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = formatCompactUsage(heroTotalMillis),
                                style = MaterialTheme.typography.headlineLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 32.sp,
                                    fontFeatureSettings = "tnum"
                                ),
                                color = FocusColors.TextPrimary
                            )
                            Text(
                                text = periodSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = FocusColors.TextSecondary
                            )
                            if (weekDeltaPercent != null) {
                                val deltaText = when {
                                    weekDeltaPercent < 0 -> stringResource(R.string.usage_vs_last_week_down, kotlin.math.abs(weekDeltaPercent))
                                    weekDeltaPercent > 0 -> stringResource(R.string.usage_vs_last_week_up, weekDeltaPercent)
                                    else -> stringResource(R.string.usage_vs_last_week_same)
                                }
                                Surface(
                                    color = FocusColors.SurfaceVariant,
                                    shape = FocusShapes.small,
                                    border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = deltaText,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.Medium,
                                            fontFeatureSettings = "tnum"
                                        ),
                                        color = when {
                                            weekDeltaPercent < 0 -> FocusColors.EmeraldText
                                            weekDeltaPercent > 0 -> FocusColors.CoralText
                                            else -> FocusColors.TextSecondary
                                        },
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { stepPeriod(1) },
                            enabled = canStepForward
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = stringResource(R.string.usage_next_period),
                                tint = if (canStepForward) FocusColors.TextPrimary else FocusColors.TextMuted.copy(alpha = 0.35f)
                            )
                        }
                    }
                }

                // Stacked 7-Day Bar Chart
                item(key = "stacked_bar_chart") {
                    UsageStackedWeekChart(
                        days = currentWeek.days,
                        periodTab = selectedTab,
                        selectedDayIndex = activeDayIndex,
                        selectedFilter = selectedFilter,
                        onSelectDay = { dayIdx ->
                            selectedDayIndex = dayIdx
                            if (selectedTab == UsagePeriodTab.WEEKLY) {
                                selectedTab = UsagePeriodTab.DAILY
                            }
                        },
                        modifier = widthModifier
                    )
                }

                // 3-Column Category Breakdown Row
                item(key = "category_legend") {
                    UsageCategoryLegendRow(
                        distractingMillis = distractingTotal,
                        productiveMillis = productiveTotal,
                        othersMillis = othersTotal,
                        selectedFilter = selectedFilter,
                        onToggleFilter = { clicked ->
                            selectedFilter = if (selectedFilter == clicked) {
                                UsageCategoryFilter.ALL
                            } else {
                                clicked
                            }
                        },
                        modifier = widthModifier
                    )
                }

                // Search Bar
                item(key = "usage_search_bar") {
                    UsageSearchField(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        modifier = widthModifier.padding(horizontal = FocusSpacing.screenHorizontal)
                    )
                }

                // App Usage List inside single sleek rounded container card
                item(key = "usage_apps_card") {
                    Surface(
                        color = FocusColors.Surface,
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                        modifier = widthModifier.padding(horizontal = FocusSpacing.screenHorizontal)
                    ) {
                        if (displayedApps.isEmpty()) {
                            Text(
                                text = if (trimmedQuery.isNotEmpty()) {
                                    stringResource(R.string.usage_empty_search, searchQuery.trim())
                                } else {
                                    stringResource(R.string.usage_empty_filter)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = FocusColors.TextSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            )
                        } else {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                displayedApps.forEachIndexed { index, item ->
                                    UsageAppListRow(
                                        item = item,
                                        periodTab = selectedTab,
                                        selectedFilter = selectedFilter,
                                        showDivider = index < displayedApps.lastIndex,
                                        onClick = { detailPackage = item.packageName }
                                    )
                                }
                            }
                        }
                    }
                }

                // Footer toggle: Show / Hide apps with 0m of usage
                if (zeroApps.isNotEmpty() && trimmedQuery.isEmpty() && nonZeroApps.isNotEmpty()) {
                    item(key = "toggle_zero_apps") {
                        Row(
                            modifier = widthModifier
                                .padding(horizontal = FocusSpacing.screenHorizontal)
                                .minimumInteractiveComponentSize()
                                .clip(FocusShapes.medium)
                                .clickable { showZeroUsageApps = !showZeroUsageApps }
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(
                                    if (showZeroUsageApps) R.string.usage_hide_zero_apps else R.string.usage_show_zero_apps
                                ),
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = FocusColors.TextSecondary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = if (showZeroUsageApps) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = FocusColors.TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    val selectedDetailItem = remember(detailPackage, currentWeek, selectedTab, activeDayIndex) {
        val pkg = detailPackage ?: return@remember null
        val source = if (selectedTab == UsagePeriodTab.DAILY) {
            currentWeek?.days?.getOrNull(activeDayIndex)?.apps.orEmpty()
        } else {
            currentWeek?.apps.orEmpty()
        }
        source.firstOrNull { it.packageName == pkg }
    }

    if (selectedDetailItem != null) {
        UsageAppDetailSheet(
            appItem = selectedDetailItem,
            isOverridden = categoryOverrides.containsKey(selectedDetailItem.packageName),
            existingLimit = limitsByPackage[selectedDetailItem.packageName],
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismiss = { detailPackage = null },
            onSelectCategory = { newCategory ->
                repository.setAppCategoryOverride(selectedDetailItem.packageName, newCategory)
                detailPackage = null
                loadWeek(weekOffset)
            },
            onEditLimit = {
                val pkg = selectedDetailItem.packageName
                val name = selectedDetailItem.appName
                detailPackage = null
                editorPackage = pkg
                editorName = name
                editorRequiresExisting = limitsByPackage[pkg] != null
                appLimitsViewModel.loadInstalledApps()
            },
            onOpenSystemInfo = {
                detailPackage = null
                openSystemAppDetails(context, selectedDetailItem.packageName)
            }
        )
    }

    if (editorPackage != null) {
        AppLimitEditorHost(
            viewModel = appLimitsViewModel,
            editingPackage = editorPackage,
            onDismiss = {
                editorPackage = null
                editorName = null
                editorRequiresExisting = false
            },
            initialAppName = editorName,
            requireExisting = editorRequiresExisting
        )
    }

    if (showHelp) {
        AlertDialog(
            onDismissRequest = { showHelp = false },
            title = { Text(stringResource(R.string.usage_help_title)) },
            text = { Text(stringResource(R.string.usage_help_body)) },
            confirmButton = {
                TextButton(onClick = { showHelp = false }) {
                    Text(stringResource(R.string.action_done))
                }
            }
        )
    }
}

@Composable
private fun UsageFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) FocusColors.SurfaceVariant else FocusColors.Surface,
        contentColor = if (selected) FocusColors.TextPrimary else FocusColors.TextSecondary,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) FocusColors.ControlBorder else FocusColors.CardBorderSubtle
        ),
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                ),
                maxLines = 1
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = FocusColors.TextPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun UsageSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = FocusColors.Surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = FocusColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(R.string.usage_search_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = FocusColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = FocusColors.TextPrimary),
                    cursorBrush = SolidColor(FocusColors.Primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (query.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_clear_search),
                    tint = FocusColors.TextSecondary,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onQueryChange("") }
                )
            }
        }
    }
}

@Composable
private fun UsageAccessPermissionCard(
    onGrantClick: () -> Unit
) {
    Surface(
        color = FocusColors.SurfaceVariant,
        shape = FocusShapes.card,
        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("wellbeing_permission_banner")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = FocusColors.CoralWarning,
                    modifier = Modifier.size(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.usage_permission_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = FocusColors.TextPrimary
                    )
                    Text(
                        text = stringResource(R.string.usage_permission_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = FocusColors.TextSecondary
                    )
                }
            }
            Button(
                onClick = onGrantClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("grant_usage_access_button"),
                colors = ButtonDefaults.buttonColors(containerColor = FocusColors.Primary),
                shape = FocusShapes.medium
            ) {
                Text(
                    text = stringResource(R.string.usage_permission_cta),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }
        }
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
