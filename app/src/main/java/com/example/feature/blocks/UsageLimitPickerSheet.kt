package com.example.feature.blocks

import android.graphics.drawable.Drawable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.core.design.FocusColors

/**
 * Usage limit scroll wheel picker bottom sheet matching the screenshot design.
 * Features dual-column scrolling wheel for Hours (0..23) and Minutes (0..55 in 5m steps),
 * highlighted selection band, suggested usage banner, and high-contrast Confirm action.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UsageLimitPickerSheet(
    appName: String,
    appIcon: Drawable?,
    initialHours: Int = 1,
    initialMinutes: Int = 30,
    onClose: () -> Unit,
    onConfirm: (hours: Int, minutes: Int) -> Unit
) {
    val hoursList = remember { (0..23).toList() }
    val minutesList = remember { (0..55 step 5).toList() }

    val itemHeight = 52.dp

    val initialHourIndex = remember { hoursList.indexOf(initialHours).coerceAtLeast(0) }
    val initialMinuteIndex = remember {
        val closest5 = (initialMinutes / 5) * 5
        minutesList.indexOf(closest5).coerceAtLeast(0)
    }

    val hoursLazyState = rememberLazyListState(initialFirstVisibleItemIndex = initialHourIndex)
    val minutesLazyState = rememberLazyListState(initialFirstVisibleItemIndex = initialMinuteIndex)

    val hoursFlingBehavior = rememberSnapFlingBehavior(lazyListState = hoursLazyState)
    val minutesFlingBehavior = rememberSnapFlingBehavior(lazyListState = minutesLazyState)

    val selectedHour by remember {
        derivedStateOf {
            val idx = hoursLazyState.firstVisibleItemIndex
            hoursList.getOrElse(idx) { 0 }
        }
    }

    val selectedMinute by remember {
        derivedStateOf {
            val idx = minutesLazyState.firstVisibleItemIndex
            minutesList.getOrElse(idx) { 0 }
        }
    }

    androidx.activity.compose.BackHandler(enabled = true) {
        onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.68f)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Header with App Icon, Title, and Close Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon.toBitmap(80, 80).asImageBitmap(),
                        contentDescription = appName,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(FocusColors.PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = "$appName Usage limit",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 19.sp
                    ),
                    maxLines = 1
                )
            }

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(FocusColors.SurfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = FocusColors.TextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Dual Scroll Wheel Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight * 5),
            contentAlignment = Alignment.Center
        ) {
            // Center Selection Highlight Band
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = FocusColors.PrimaryContainer.copy(alpha = 0.55f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, FocusColors.Primary.copy(alpha = 0.7f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
            ) {}

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Hours Scroll Column
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(itemHeight * 5),
                    contentAlignment = Alignment.Center
                ) {
                    LazyColumn(
                        state = hoursLazyState,
                        flingBehavior = hoursFlingBehavior,
                        contentPadding = PaddingValues(vertical = itemHeight * 2),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(hoursList.size) { index ->
                            val hour = hoursList[index]
                            val isSelected = hour == selectedHour
                            val diff = kotlin.math.abs(hoursList.indexOf(selectedHour) - index)

                            val alpha = when (diff) {
                                0 -> 1f
                                1 -> 0.45f
                                2 -> 0.2f
                                else -> 0.1f
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(itemHeight),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$hour",
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        fontSize = if (isSelected) 28.sp else 20.sp,
                                        color = if (isSelected) FocusColors.TextPrimary else FocusColors.TextMuted.copy(alpha = alpha),
                                        textAlign = TextAlign.Center
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (hour == 1) "hour" else "hours",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (isSelected) FocusColors.Primary else FocusColors.TextMuted.copy(alpha = alpha),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // Divider Line
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(itemHeight * 3)
                        .align(Alignment.CenterVertically)
                        .background(FocusColors.CardBorderSubtle)
                )

                // Minutes Scroll Column
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(itemHeight * 5),
                    contentAlignment = Alignment.Center
                ) {
                    LazyColumn(
                        state = minutesLazyState,
                        flingBehavior = minutesFlingBehavior,
                        contentPadding = PaddingValues(vertical = itemHeight * 2),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(minutesList.size) { index ->
                            val minute = minutesList[index]
                            val isSelected = minute == selectedMinute
                            val diff = kotlin.math.abs(minutesList.indexOf(selectedMinute) - index)

                            val alpha = when (diff) {
                                0 -> 1f
                                1 -> 0.45f
                                2 -> 0.2f
                                else -> 0.1f
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(itemHeight),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = String.format("%02d", minute),
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        fontSize = if (isSelected) 28.sp else 20.sp,
                                        color = if (isSelected) FocusColors.TextPrimary else FocusColors.TextMuted.copy(alpha = alpha),
                                        textAlign = TextAlign.Center
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "mins",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = if (isSelected) FocusColors.Primary else FocusColors.TextMuted.copy(alpha = alpha),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Top & Bottom Gradient overlays for smooth fading edges
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight * 1.5f)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                FocusColors.Surface,
                                FocusColors.Surface.copy(alpha = 0f)
                            )
                        )
                    )
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight * 1.5f)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                FocusColors.Surface.copy(alpha = 0f),
                                FocusColors.Surface
                            )
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Suggested Limit Banner
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(FocusColors.SurfaceVariant)
                .border(1.dp, FocusColors.CardBorderSubtle, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(FocusColors.PrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lightbulb,
                    contentDescription = null,
                    tint = FocusColors.Primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = buildAnnotatedString {
                    append("Suggested limit is ")
                    withStyle(SpanStyle(color = FocusColors.PrimaryLight, fontWeight = FontWeight.Bold)) {
                        append("1h 30m")
                    }
                    append(" based on daily habits.")
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = FocusColors.TextSecondary,
                    fontSize = 13.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Large Bottom Confirm Button
        val totalMinutes = (selectedHour * 60) + selectedMinute
        val isValid = totalMinutes > 0

        Button(
            onClick = {
                val effectiveMinutes = totalMinutes.coerceAtLeast(1)
                val finalHours = effectiveMinutes / 60
                val finalMinutes = effectiveMinutes % 60
                onConfirm(finalHours, finalMinutes)
            },
            enabled = isValid,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = FocusColors.Primary,
                contentColor = Color.White,
                disabledContainerColor = FocusColors.SurfaceVariant,
                disabledContentColor = FocusColors.TextMuted
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("wheel_confirm_limit_button")
        ) {
            Text(
                text = if (isValid) "Confirm Limit (${if (selectedHour > 0) "${selectedHour}h " else ""}${selectedMinute}m)" else "Select a valid limit",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
    }
}
