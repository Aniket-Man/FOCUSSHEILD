package com.example.feature.session.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.design.FocusColors
import kotlinx.coroutines.launch

/**
 * Robust, freeze-proof Focus Time Picker Sheet with scroll wheels, quick duration presets,
 * and reliable Back/Cancel navigation.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusTimePickerSheet(
    selectedMinutes: Int,
    onMinutesSelect: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var hours by remember { mutableIntStateOf((selectedMinutes / 60).coerceIn(0, 23)) }
    var minutes by remember { mutableIntStateOf((selectedMinutes % 60).coerceIn(0, 59)) }

    val totalMins = (hours * 60 + minutes).coerceAtLeast(1)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(FocusColors.Surface)
            .padding(horizontal = 20.dp, vertical = 14.dp)
            .testTag("focus_time_picker_sheet"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Grab Handle
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(FocusColors.TextMuted.copy(alpha = 0.4f))
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Header with Back Button, Title, and Cancel
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("time_picker_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = FocusColors.TextPrimary
                    )
                }

                Text(
                    text = stringResource(R.string.session_picker_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                )
            }

            TextButton(
                onClick = onClose,
                modifier = Modifier.testTag("time_picker_cancel_button")
            ) {
                Text(
                    text = stringResource(R.string.action_cancel),
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = FocusColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Quick Preset Chips (15m, 25m, 45m, 1h, 90m, 2h)
        val presets = listOf(15, 25, 45, 60, 90, 120)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            presets.forEach { presetMins ->
                val isSelected = totalMins == presetMins
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        hours = presetMins / 60
                        minutes = presetMins % 60
                    },
                    label = {
                        Text(
                            text = if (presetMins >= 60 && presetMins % 60 == 0) {
                                pluralStringResource(
                                    R.plurals.session_picker_hours,
                                    presetMins / 60,
                                    presetMins / 60
                                )
                            } else {
                                stringResource(R.string.session_setup_minutes_short, presetMins)
                            },
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp
                            )
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Time Picker Wheel Container
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hours Wheel
            WheelNumberPicker(
                items = (0..23).toList(),
                initialValue = hours,
                label = stringResource(R.string.session_picker_hours_label),
                onValueChange = { hours = it },
                modifier = Modifier.weight(1f)
            )

            Text(
                text = ":",
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = FocusColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                ),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 20.dp)
            )

            // Minutes Wheel
            WheelNumberPicker(
                items = (0..59).toList(),
                initialValue = minutes,
                label = stringResource(R.string.session_picker_minutes_label),
                onValueChange = { minutes = it },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Calculated Time Summary Badge
        val summaryHours = totalMins / 60
        val summaryMinutes = totalMins % 60
        val formattedSummary = listOfNotNull(
            if (summaryHours > 0) {
                pluralStringResource(
                    R.plurals.session_picker_hours,
                    summaryHours,
                    summaryHours
                )
            } else {
                null
            },
            if (summaryMinutes > 0 || summaryHours == 0) {
                pluralStringResource(
                    R.plurals.session_picker_minutes,
                    summaryMinutes,
                    summaryMinutes
                )
            } else {
                null
            }
        ).joinToString(" ")

        Surface(
            shape = RoundedCornerShape(16.dp),
            color = FocusColors.PrimaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = FocusColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.session_picker_total, formattedSummary),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.Primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Action Buttons Row: Cancel and Apply
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onClose,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = FocusColors.TextSecondary
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
            ) {
                Text(
                    text = stringResource(R.string.action_back),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                )
            }

            Button(
                onClick = {
                    onMinutesSelect(totalMins)
                    onClose()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusColors.Primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .weight(1.6f)
                    .height(50.dp)
                    .testTag("apply_focus_time_button")
            ) {
                Text(
                    text = stringResource(R.string.session_picker_apply),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun WheelNumberPicker(
    items: List<Int>,
    initialValue: Int,
    label: String,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val itemHeight = 44.dp
    val density = LocalDensity.current
    val itemHeightPx = with(density) { itemHeight.toPx() }
    val coroutineScope = rememberCoroutineScope()
    val targetIndex = items.indexOf(initialValue).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = targetIndex)
    val snapFlingBehavior = rememberSnapFlingBehavior(lazyListState = listState)

    val selectedIndex by remember(items, itemHeightPx) {
        derivedStateOf {
            val baseIndex = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val roundedIndex = if (itemHeightPx > 0f && offset >= itemHeightPx / 2f) {
                baseIndex + 1
            } else {
                baseIndex
            }
            roundedIndex.coerceIn(0, items.lastIndex)
        }
    }

    // Keep wheel position in sync when preset chips change initialValue externally
    LaunchedEffect(initialValue) {
        val idx = items.indexOf(initialValue)
        if (idx >= 0 && idx != selectedIndex && !listState.isScrollInProgress) {
            listState.scrollToItem(idx)
        }
    }

    // Emit user scroll changes when wheel settles or moves
    LaunchedEffect(selectedIndex, listState.isScrollInProgress) {
        if (selectedIndex in items.indices && listState.isScrollInProgress) {
            onValueChange(items[selectedIndex])
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = FocusColors.TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .height(itemHeight * 3)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Selection Highlight Card
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .height(itemHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FocusColors.PrimaryContainer)
                    .border(1.dp, FocusColors.Primary.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            )

            LazyColumn(
                state = listState,
                flingBehavior = snapFlingBehavior,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = itemHeight),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                itemsIndexed(items) { index, itemValue ->
                    val isSelected = itemValue == initialValue || (listState.isScrollInProgress && index == selectedIndex)
                    Box(
                        modifier = Modifier
                            .height(itemHeight)
                            .fillMaxWidth()
                            .clickable {
                                onValueChange(itemValue)
                                coroutineScope.launch {
                                    listState.animateScrollToItem(index)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format(java.util.Locale.getDefault(), "%02d", itemValue),
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = if (isSelected) FocusColors.TextPrimary else FocusColors.TextMuted,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = if (isSelected) 22.sp else 16.sp
                            )
                        )
                    }
                }
            }
        }
    }
}
