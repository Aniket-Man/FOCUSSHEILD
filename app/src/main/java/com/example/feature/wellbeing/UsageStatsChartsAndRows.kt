package com.example.feature.wellbeing

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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.util.DeviceUsageStatsHelper
import com.example.core.util.InstalledAppsProvider.toImageBitmapCached
import com.example.data.local.entity.AppLimitEntity
import kotlin.math.ceil

internal object UsageCategoryColors {
    val Distracting = Color(0xFFFF7A00)
    val Productive = Color(0xFF22C55E)
    val OthersLight = Color(0xFF94A3B8)
    val OthersDark = Color(0xFFA1A1AA)

    @Composable
    fun colorFor(category: AppUsageCategory): Color = when (category) {
        AppUsageCategory.DISTRACTING -> Distracting
        AppUsageCategory.PRODUCTIVE -> FocusColors.EmeraldSuccess
        AppUsageCategory.OTHERS -> if (com.example.core.design.LocalFocusColors.current.isDark) OthersDark else OthersLight
    }
}

@Composable
internal fun UsageStackedWeekChart(
    days: List<UsageDaySummary>,
    periodTab: UsagePeriodTab,
    selectedDayIndex: Int,
    selectedFilter: UsageCategoryFilter,
    onSelectDay: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val distractingColor = UsageCategoryColors.Distracting
    val productiveColor = UsageCategoryColors.colorFor(AppUsageCategory.PRODUCTIVE)
    val othersColor = UsageCategoryColors.colorFor(AppUsageCategory.OTHERS)
    val mutedBarColor = FocusColors.SurfaceSubtle
    val gridColor = FocusColors.CardBorderSubtle.copy(alpha = 0.7f)

    val maxDayHours = remember(days, selectedFilter) {
        val rawMaxHours = days.maxOfOrNull { it.millisForFilter(selectedFilter).toFloat() / 3_600_000f } ?: 0f
        when {
            rawMaxHours <= 3f -> 3f
            rawMaxHours <= 6f -> 6f
            rawMaxHours <= 9f -> 9f
            rawMaxHours <= 15f -> 15f
            else -> ceil((rawMaxHours / 3f).toDouble()).toFloat() * 3f
        }
    }
    val stepHours = (maxDayHours / 3f).toInt().coerceAtLeast(1)
    val yAxisLabels = remember(maxDayHours, stepHours) {
        listOf("${stepHours * 3}h", "${stepHours * 2}h", "${stepHours}h", "0h")
    }

    var startAnim by remember { mutableStateOf(false) }
    LaunchedEffect(days, periodTab, selectedFilter) {
        startAnim = true
    }
    val animProgress by animateFloatAsState(
        targetValue = if (startAnim) 1f else 0f,
        animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing),
        label = "usage_bars_anim"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FocusSpacing.screenHorizontal)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Left Y-Axis hour labels aligned to the 4 horizontal gridlines
            Column(
                modifier = Modifier
                    .width(34.dp)
                    .fillMaxHeight()
                    .padding(bottom = 2.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                yAxisLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.TextMuted,
                            fontSize = 11.sp,
                            fontFeatureSettings = "tnum"
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Grid + 7 tappable stacked bar columns
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val lineCount = 4
                    val stepY = size.height / (lineCount - 1)
                    for (i in 0 until lineCount) {
                        val y = stepY * i
                        drawLine(
                            color = gridColor,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    days.forEachIndexed { index, day ->
                        val isHighlighted = periodTab == UsagePeriodTab.WEEKLY || index == selectedDayIndex
                        val dMillis = if (selectedFilter == UsageCategoryFilter.ALL || selectedFilter == UsageCategoryFilter.DISTRACTING) day.distractingMillis else 0L
                        val pMillis = if (selectedFilter == UsageCategoryFilter.ALL || selectedFilter == UsageCategoryFilter.PRODUCTIVE) day.productiveMillis else 0L
                        val oMillis = if (selectedFilter == UsageCategoryFilter.ALL || selectedFilter == UsageCategoryFilter.OTHERS) day.othersMillis else 0L
                        val totalMillis = dMillis + pMillis + oMillis

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = !day.isFuture) { onSelectDay(index) },
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Canvas(
                                modifier = Modifier
                                    .width(24.dp)
                                    .fillMaxHeight()
                            ) {
                                if (totalMillis <= 0L) return@Canvas
                                val maxMillis = maxDayHours * 3_600_000f
                                val totalHeight = ((totalMillis.toFloat() / maxMillis).coerceIn(0.03f, 1f)) * size.height * animProgress
                                val cornerPx = 5.dp.toPx()

                                if (!isHighlighted) {
                                    drawRoundRect(
                                        color = mutedBarColor,
                                        topLeft = Offset(0f, size.height - totalHeight),
                                        size = Size(size.width, totalHeight),
                                        cornerRadius = CornerRadius(cornerPx, cornerPx)
                                    )
                                } else {
                                    // Stack from bottom to top: Distracting (orange) -> Productive (green) -> Others (gray)
                                    val segments = buildList {
                                        if (dMillis > 0L) add(dMillis to distractingColor)
                                        if (pMillis > 0L) add(pMillis to productiveColor)
                                        if (oMillis > 0L) add(oMillis to othersColor)
                                    }
                                    var currentBottom = size.height
                                    segments.forEachIndexed { segIdx, (segMillis, segColor) ->
                                        val segHeight = (segMillis.toFloat() / totalMillis.toFloat()) * totalHeight
                                        val topY = currentBottom - segHeight
                                        val isTop = segIdx == segments.lastIndex
                                        val isBottom = segIdx == 0
                                        if (isTop || isBottom) {
                                            val path = Path().apply {
                                                val topR = if (isTop) cornerPx else 0f
                                                val botR = if (isBottom) cornerPx else 0f
                                                addRoundRect(
                                                    androidx.compose.ui.geometry.RoundRect(
                                                        left = 0f,
                                                        top = topY,
                                                        right = size.width,
                                                        bottom = currentBottom,
                                                        topLeftCornerRadius = CornerRadius(topR, topR),
                                                        topRightCornerRadius = CornerRadius(topR, topR),
                                                        bottomLeftCornerRadius = CornerRadius(botR, botR),
                                                        bottomRightCornerRadius = CornerRadius(botR, botR)
                                                    )
                                                )
                                            }
                                            drawPath(path = path, color = segColor)
                                        } else {
                                            drawRect(
                                                color = segColor,
                                                topLeft = Offset(0f, topY),
                                                size = Size(size.width, segHeight)
                                            )
                                        }
                                        currentBottom = topY
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Day labels M T W T F S S aligned under the 7 bar columns
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width(44.dp))
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                days.forEachIndexed { index, day ->
                    val isSelected = periodTab == UsagePeriodTab.DAILY && index == selectedDayIndex
                    Text(
                        text = day.dayInitial,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = when {
                                isSelected -> FocusColors.TextPrimary
                                day.isFuture -> FocusColors.TextMuted.copy(alpha = 0.45f)
                                else -> FocusColors.TextSecondary
                            }
                        ),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .weight(1f)
                            .clip(FocusShapes.small)
                            .clickable(enabled = !day.isFuture) { onSelectDay(index) }
                            .padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun UsageCategoryLegendRow(
    distractingMillis: Long,
    productiveMillis: Long,
    othersMillis: Long,
    selectedFilter: UsageCategoryFilter,
    onToggleFilter: (UsageCategoryFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FocusSpacing.screenHorizontal),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryLegendItem(
            dotColor = UsageCategoryColors.Distracting,
            duration = formatCompactUsage(distractingMillis),
            label = stringResource(R.string.usage_filter_distracting),
            isSelected = selectedFilter == UsageCategoryFilter.DISTRACTING,
            onClick = { onToggleFilter(UsageCategoryFilter.DISTRACTING) },
            modifier = Modifier.weight(1f)
        )
        CategoryLegendItem(
            dotColor = UsageCategoryColors.colorFor(AppUsageCategory.PRODUCTIVE),
            duration = formatCompactUsage(productiveMillis),
            label = stringResource(R.string.usage_filter_productive),
            isSelected = selectedFilter == UsageCategoryFilter.PRODUCTIVE,
            onClick = { onToggleFilter(UsageCategoryFilter.PRODUCTIVE) },
            modifier = Modifier.weight(1f)
        )
        CategoryLegendItem(
            dotColor = UsageCategoryColors.colorFor(AppUsageCategory.OTHERS),
            duration = formatCompactUsage(othersMillis),
            label = stringResource(R.string.usage_filter_others),
            isSelected = selectedFilter == UsageCategoryFilter.OTHERS,
            onClick = { onToggleFilter(UsageCategoryFilter.OTHERS) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CategoryLegendItem(
    dotColor: Color,
    duration: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(FocusShapes.medium)
            .background(if (isSelected) FocusColors.SurfaceVariant else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = duration,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary,
                    fontFeatureSettings = "tnum"
                ),
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = FocusColors.TextSecondary,
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun UsageAppListRow(
    item: UsageAppBreakdownItem,
    periodTab: UsagePeriodTab,
    selectedFilter: UsageCategoryFilter,
    showDivider: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appIcon = remember(item.packageName) {
        try {
            context.packageManager.getApplicationIcon(item.packageName).toImageBitmapCached(item.packageName)
        } catch (_: Exception) {
            null
        }
    }

    val displayMillis = item.millisForFilter(selectedFilter)
    val trailingText = if (periodTab == UsagePeriodTab.WEEKLY && selectedFilter == UsageCategoryFilter.ALL) {
        "${formatCompactUsage(item.averageDailyMillis)}(avg)"
    } else if (periodTab == UsagePeriodTab.WEEKLY) {
        formatCompactUsage(displayMillis)
    } else {
        formatCompactUsage(displayMillis)
    }

    val hasSplit = item.distractingMillis > 0L && item.productiveMillis > 0L

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (appIcon != null) {
                Image(
                    bitmap = appIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                )
            } else {
                Surface(
                    color = FocusColors.SurfaceVariant,
                    shape = CircleShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = item.appName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = FocusColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (selectedFilter == UsageCategoryFilter.ALL && hasSplit) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DotLabel(
                            color = UsageCategoryColors.Distracting,
                            text = "${stringResource(R.string.usage_filter_distracting)}  ${formatCompactUsage(item.distractingMillis)}"
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DotLabel(
                            color = UsageCategoryColors.colorFor(AppUsageCategory.PRODUCTIVE),
                            text = "${stringResource(R.string.usage_filter_productive)}  ${formatCompactUsage(item.productiveMillis)}"
                        )
                    }
                } else {
                    val activeCategory = when (selectedFilter) {
                        UsageCategoryFilter.DISTRACTING -> AppUsageCategory.DISTRACTING
                        UsageCategoryFilter.PRODUCTIVE -> AppUsageCategory.PRODUCTIVE
                        UsageCategoryFilter.OTHERS -> AppUsageCategory.OTHERS
                        UsageCategoryFilter.ALL -> item.primaryCategory
                    }
                    val catText = when (activeCategory) {
                        AppUsageCategory.DISTRACTING -> stringResource(R.string.usage_filter_distracting)
                        AppUsageCategory.PRODUCTIVE -> stringResource(R.string.usage_filter_productive)
                        AppUsageCategory.OTHERS -> stringResource(R.string.usage_filter_others)
                    }
                    DotLabel(
                        color = UsageCategoryColors.colorFor(activeCategory),
                        text = catText
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = trailingText,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        fontFeatureSettings = "tnum"
                    ),
                    color = FocusColors.TextPrimary,
                    maxLines = 1
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = FocusColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(
                color = FocusColors.CardBorderSubtle.copy(alpha = 0.55f),
                modifier = Modifier.padding(start = 70.dp, end = 16.dp)
            )
        }
    }
}

@Composable
private fun DotLabel(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
            color = FocusColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun UsageAppDetailSheet(
    appItem: UsageAppBreakdownItem,
    isOverridden: Boolean,
    existingLimit: AppLimitEntity?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSelectCategory: (AppUsageCategory?) -> Unit,
    onEditLimit: () -> Unit,
    onOpenSystemInfo: () -> Unit
) {
    val context = LocalContext.current
    val appIcon = remember(appItem.packageName) {
        try {
            context.packageManager.getApplicationIcon(appItem.packageName).toImageBitmapCached(appItem.packageName)
        } catch (_: Exception) {
            null
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FocusColors.OverlayBackground,
        contentColor = FocusColors.OverlayTextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                } else {
                    Surface(
                        color = FocusColors.SurfaceVariant,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Apps, null, tint = FocusColors.Primary)
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = appItem.appName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = FocusColors.TextPrimary
                    )
                    Text(
                        text = appItem.systemCategoryLabel.ifBlank { appItem.packageName },
                        style = MaterialTheme.typography.bodySmall,
                        color = FocusColors.TextSecondary
                    )
                }
                Text(
                    text = formatCompactUsage(appItem.totalMillis),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum"
                    ),
                    color = FocusColors.TextPrimary
                )
            }

            // Category selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.usage_detail_category_title),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = FocusColors.TextPrimary
                    )
                    if (isOverridden) {
                        TextButton(onClick = { onSelectCategory(null) }) {
                            Text(stringResource(R.string.usage_detail_reset_auto))
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AppUsageCategory.entries.forEach { category ->
                        val selected = appItem.primaryCategory == category
                        val dotColor = UsageCategoryColors.colorFor(category)
                        val label = when (category) {
                            AppUsageCategory.DISTRACTING -> stringResource(R.string.usage_filter_distracting)
                            AppUsageCategory.PRODUCTIVE -> stringResource(R.string.usage_filter_productive)
                            AppUsageCategory.OTHERS -> stringResource(R.string.usage_filter_others)
                        }
                        Surface(
                            color = if (selected) dotColor.copy(alpha = 0.16f) else FocusColors.SurfaceVariant,
                            shape = FocusShapes.medium,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selected) dotColor else FocusColors.CardBorderSubtle
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .minimumInteractiveComponentSize()
                                .clip(FocusShapes.medium)
                                .clickable { onSelectCategory(category) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = dotColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = FocusColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Quick stats summary
            Surface(
                color = FocusColors.SurfaceVariant,
                shape = FocusShapes.medium,
                border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.usage_filter_distracting),
                            style = MaterialTheme.typography.labelSmall,
                            color = FocusColors.TextSecondary
                        )
                        Text(
                            text = formatCompactUsage(appItem.distractingMillis),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = UsageCategoryColors.Distracting
                        )
                    }
                    Column {
                        Text(
                            text = stringResource(R.string.usage_filter_productive),
                            style = MaterialTheme.typography.labelSmall,
                            color = FocusColors.TextSecondary
                        )
                        Text(
                            text = formatCompactUsage(appItem.productiveMillis),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = UsageCategoryColors.colorFor(AppUsageCategory.PRODUCTIVE)
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = stringResource(R.string.usage_detail_launches),
                            style = MaterialTheme.typography.labelSmall,
                            color = FocusColors.TextSecondary
                        )
                        Text(
                            text = appItem.launchCount.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = FocusColors.TextPrimary
                        )
                    }
                }
            }

            // Action buttons: App Limit + System Info
            Button(
                onClick = onEditLimit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = FocusShapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = FocusColors.Primary)
            ) {
                Icon(
                    imageVector = Icons.Rounded.HourglassBottom,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (existingLimit != null) {
                        stringResource(R.string.usage_detail_edit_limit, existingLimit.dailyLimitMinutes)
                    } else {
                        stringResource(R.string.usage_detail_set_limit)
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }

            OutlinedButton(
                onClick = onOpenSystemInfo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = FocusShapes.medium,
                border = BorderStroke(1.dp, FocusColors.CardBorder)
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.usage_detail_app_info))
            }
        }
    }
}

internal fun formatCompactUsage(millis: Long): String {
    if (millis <= 0L) return "0m"
    if (millis < 60_000L) return "1m"
    return DeviceUsageStatsHelper.formatDurationHoursMins(millis)
}
