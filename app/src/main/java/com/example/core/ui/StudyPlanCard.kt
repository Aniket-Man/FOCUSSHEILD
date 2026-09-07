package com.example.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.data.model.StudyPlanItem

private val PlanCardShape = RoundedCornerShape(18.dp)

/**
 * Pixel-accurate Study Plan Row matching the reference screenshot:
 * Left colored vertical accent strip, clean Time / Subject / Topic column,
 * light purple "▶ Start" pill button, and 3-dots overflow menu.
 */
@Composable
fun StudyPlanRow(
    item: StudyPlanItem,
    modifier: Modifier = Modifier,
    onStartClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onDeleteClick: () -> Unit = {},
    onToggleComplete: () -> Unit = {}
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val isDark = com.example.core.design.LocalFocusColors.current.isDark

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 0.dp else 2.dp,
                shape = PlanCardShape,
                ambientColor = Color.Black.copy(alpha = 0.03f),
                spotColor = Color.Black.copy(alpha = 0.04f)
            )
            .clip(PlanCardShape)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, PlanCardShape)
            .height(IntrinsicSize.Min)
            .testTag("study_plan_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Accent Strip
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(5.dp)
                    .background(item.accentColor)
            )

            // Middle Column: Time / Subject / Topic
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "${item.startTime} – ${item.endTime}",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = item.subject,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                        color = FocusColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (item.topic.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = item.topic,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right Controls: Pill "▶ Start" button + 3-dots menu
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 6.dp)
            ) {
                // Light purple Pill Button "▶ Start"
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isDark) Color(0xFF26183C) else Color(0xFFEDE8FF))
                        .clickable(onClick = onStartClick)
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                        .testTag("start_plan_${item.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Start",
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                                color = FocusColors.Primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        )
                    }
                }

                // 3-dots Menu Button
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("plan_menu_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Options",
                            tint = FocusColors.TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (item.isCompleted) "Mark as Incomplete" else "Mark as Complete") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = FocusColors.EmeraldSuccess
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onToggleComplete()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Edit Plan") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Edit,
                                    contentDescription = null,
                                    tint = FocusColors.Primary
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onEditClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete Plan") },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = null,
                                    tint = FocusColors.CoralWarning
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDeleteClick()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pixel-accurate Summary Card matching the reference screenshot:
 * Circular percentage badge on the left, completed vs planned duration + progress bar in the center,
 * and chevron right arrow on the right. Supports Daylight and Dark themes.
 */
@Composable
fun TodayPlanSummaryCard(
    totalPlannedTime: String,
    totalCompletedTime: String,
    remainingTime: String,
    completionPercentage: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val isDark = com.example.core.design.LocalFocusColors.current.isDark

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (isDark) 0.dp else 2.dp,
                shape = PlanCardShape,
                ambientColor = Color.Black.copy(alpha = 0.03f),
                spotColor = Color.Black.copy(alpha = 0.04f)
            )
            .clip(PlanCardShape)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, PlanCardShape)
            .clickable(onClick = onClick)
            .padding(16.dp)
            .testTag("today_plan_summary_card")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular Percentage Badge
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isDark) FocusColors.SurfaceSubtle else Color(0xFFF1F5F9)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$completionPercentage%",
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Duration & Progress Bar Column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = totalCompletedTime,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    )
                    Text(
                        text = " / ",
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                            color = FocusColors.TextMuted,
                            fontWeight = FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    )
                    Text(
                        text = totalPlannedTime,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                            color = FocusColors.TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LinearProgressIndicator(
                    progress = { (completionPercentage / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = FocusColors.Primary,
                    trackColor = if (isDark) Color(0xFF26183C) else Color(0xFFEDE8FF),
                    strokeCap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Right Chevron Arrow
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = "View Planner Details",
                tint = FocusColors.TextMuted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}



