package com.example.feature.planner

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusType
import com.example.data.local.entity.FocusScheduleEntity

private val DAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

@Composable
fun AutomatedFocusSchedulesSection(
    schedules: List<FocusScheduleEntity>,
    onAddClick: () -> Unit,
    onEditClick: (FocusScheduleEntity) -> Unit,
    onDeleteClick: (FocusScheduleEntity) -> Unit,
    onToggleEnabled: (id: String, isEnabled: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("automated_schedules_section"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RECURRING FOCUS SCHEDULES",
                    style = FocusType.sectionLabel
                )
                Text(
                    text = "Auto-enforces app blocking on your chosen study windows",
                    style = FocusType.secondary
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = onAddClick,
                shape = FocusShapes.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusColors.Primary,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("add_schedule_chip")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Schedule", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        if (schedules.isEmpty()) {
            Surface(
                shape = FocusShapes.medium,
                color = FocusColors.Surface,
                border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(FocusColors.PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EventRepeat,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No recurring schedules set",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Set daily or weekly study times to automatically lock distracting apps.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                schedules.forEach { schedule ->
                    FocusScheduleCard(
                        schedule = schedule,
                        onEditClick = { onEditClick(schedule) },
                        onDeleteClick = { onDeleteClick(schedule) },
                        onToggleEnabled = { enabled -> onToggleEnabled(schedule.id, enabled) }
                    )
                }
            }
        }
    }
}

@Composable
fun FocusScheduleCard(
    schedule: FocusScheduleEntity,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = FocusShapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (schedule.isEnabled) FocusColors.Surface else FocusColors.SurfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            if (schedule.isEnabled) FocusColors.Primary.copy(alpha = 0.3f) else FocusColors.CardBorderSubtle
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Title + Toggle Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(
                                try { Color(android.graphics.Color.parseColor(schedule.colorHex)) }
                                catch (e: Exception) { FocusColors.Primary }
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = schedule.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = if (schedule.isEnabled) FocusColors.TextPrimary else FocusColors.TextSecondary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                        Text(
                            text = "${schedule.subjectName} • ${schedule.mode} Mode",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = schedule.isEnabled,
                        onCheckedChange = onToggleEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = FocusColors.Primary
                        )
                    )
                }
            }

            // Time & Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AccessTime,
                        contentDescription = null,
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "${schedule.startTime} – ${schedule.endTime}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                    )
                }

                if (schedule.isAutoStartSession) {
                    Surface(
                        shape = FocusShapes.pill,
                        color = FocusColors.PrimaryContainer,
                        modifier = Modifier.padding(start = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = FocusColors.Primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Auto Shield",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.Primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }
            }

            // Days of week pill row & Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DAYS.forEachIndexed { index, day ->
                        val isDaySelected = schedule.daysOfWeek.contains(day)
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isDaySelected && schedule.isEnabled) FocusColors.Primary.copy(alpha = 0.25f)
                                    else FocusColors.SurfaceVariant.copy(alpha = 0.5f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = DAY_LABELS[index],
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = if (isDaySelected && schedule.isEnabled) FocusColors.Primary else FocusColors.TextSecondary.copy(alpha = 0.5f),
                                    fontWeight = if (isDaySelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onEditClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit",
                            tint = FocusColors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(onClick = onDeleteClick, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete",
                            tint = FocusColors.CoralWarning,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
