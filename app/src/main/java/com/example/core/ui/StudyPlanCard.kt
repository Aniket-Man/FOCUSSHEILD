package com.example.core.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.design.FocusType
import com.example.data.model.StudyPlanItem

/**
 * A study block's lifecycle state, which drives its entire color treatment. Deriving one enum up
 * front — rather than scattering `if (isCompleted)` / `if (hasOverlap)` checks through the layout —
 * is what lets completed, overdue, in-progress and upcoming blocks read as a coherent color system
 * instead of four unrelated special cases.
 */
private enum class PlanState { COMPLETED, OVERDUE, IN_PROGRESS, UPCOMING }

private fun StudyPlanItem.planState(): PlanState = when {
    isCompleted -> PlanState.COMPLETED
    hasOverlap -> PlanState.OVERDUE
    status.equals("ACTIVE", ignoreCase = true) ||
        status.equals("IN_PROGRESS", ignoreCase = true) -> PlanState.IN_PROGRESS
    else -> PlanState.UPCOMING
}

/**
 * Premium study-block card.
 *
 * The left edge carries a rounded accent rail whose color is the block's own accent, dimmed once the
 * block is done. A single state pill (Done / Overdue / In progress / Upcoming) in the top-right is
 * the one place status is expressed, so the row never says the same thing three ways. Time range and
 * target duration read as quiet metadata; the subject is the loudest thing in the card.
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
    val state = item.planState()
    val accent = when (state) {
        PlanState.COMPLETED -> FocusColors.EmeraldSuccess
        PlanState.OVERDUE -> FocusColors.CoralWarning
        PlanState.IN_PROGRESS -> FocusColors.Primary
        PlanState.UPCOMING -> item.accentColor
    }
    val cardBackground by animateColorAsState(
        targetValue = if (item.isCompleted) FocusColors.SurfaceVariant.copy(alpha = 0.5f) else FocusColors.Surface,
        label = "card_bg"
    )
    val borderColor = if (state == PlanState.OVERDUE) {
        FocusColors.CoralWarning.copy(alpha = 0.5f)
    } else {
        FocusColors.CardBorderSubtle
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PlanCardShape)
            .background(cardBackground)
            .border(1.dp, borderColor, PlanCardShape)
            .height(IntrinsicSize.Min)
            .testTag("study_plan_${item.id}")
    ) {
        // Accent rail
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(5.dp)
                .background(if (item.isCompleted) accent.copy(alpha = 0.4f) else accent)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = FocusSpacing.base, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Top Row: time range + target, and the single status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = FocusColors.TextSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "${item.startTime} – ${item.endTime}",
                        style = FocusType.secondary.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Box(
                        modifier = Modifier
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(FocusColors.TextMuted)
                    )
                    Text(
                        text = item.targetTime,
                        style = FocusType.caption
                    )
                }

                PlanStatePill(state)
            }

            // Middle: subject + topic
            Column {
                Text(
                    text = item.subject,
                    style = FocusType.primary.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (item.isCompleted) FocusColors.TextSecondary else FocusColors.TextPrimary,
                        textDecoration = if (item.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.topic.isNotBlank()) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = item.topic,
                        style = FocusType.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (item.notes.isNotBlank()) {
                Text(
                    text = item.notes,
                    style = FocusType.caption,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Bottom action bar: complete toggle · edit · delete · start
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Complete toggle
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(
                            if (item.isCompleted) FocusColors.EmeraldSuccess else FocusColors.SurfaceVariant
                        )
                        .border(
                            1.dp,
                            if (item.isCompleted) FocusColors.EmeraldSuccess else FocusColors.CardBorderSubtle,
                            CircleShape
                        )
                        .clickable(onClick = onToggleComplete)
                        .testTag("toggle_complete_${item.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    if (item.isCompleted) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Completed",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(30.dp).testTag("edit_plan_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = "Edit Plan",
                        tint = FocusColors.TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(30.dp).testTag("delete_plan_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete Plan",
                        tint = FocusColors.CoralWarning.copy(alpha = 0.85f),
                        modifier = Modifier.size(17.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (!item.isCompleted) {
                    FilledTonalButton(
                        onClick = onStartClick,
                        shape = FocusShapes.pill,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = FocusColors.Primary,
                            contentColor = Color.White
                        ),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 6.dp
                        ),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("start_plan_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Start",
                            style = FocusType.secondary.copy(
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * The single source of status truth for a plan row.
 */
@Composable
private fun PlanStatePill(state: PlanState) {
    val (label, color) = when (state) {
        PlanState.COMPLETED -> "Done" to FocusColors.EmeraldSuccess
        PlanState.OVERDUE -> "Overdue" to FocusColors.CoralWarning
        PlanState.IN_PROGRESS -> "In progress" to FocusColors.Primary
        PlanState.UPCOMING -> "Upcoming" to FocusColors.TextSecondary
    }
    Box(
        modifier = Modifier
            .clip(FocusShapes.pill)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = FocusType.caption.copy(
                color = color,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            softWrap = false
        )
    }
}

private val PlanCardShape = RoundedCornerShape(18.dp)

/**
 * Summary Card displaying Planned vs Completed vs Remaining study blocks and plan completion rate.
 */
@Composable
fun TodayPlanSummaryCard(
    totalPlannedTime: String,
    totalCompletedTime: String,
    remainingTime: String,
    completionPercentage: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PlanCardShape,
        color = FocusColors.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.CardBorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "TODAY'S PLAN PROGRESS",
                        style = FocusType.sectionLabel
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$totalCompletedTime of $totalPlannedTime",
                        style = FocusType.statNumber
                    )
                    Text(
                        text = "$remainingTime remaining",
                        style = FocusType.caption
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(FocusShapes.pill)
                        .background(FocusColors.Primary.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "$completionPercentage%",
                        style = FocusType.primary.copy(
                            color = FocusColors.Primary,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (completionPercentage / 100f).coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = FocusColors.Primary,
                trackColor = FocusColors.SurfaceVariant,
                strokeCap = StrokeCap.Round
            )
        }
    }
}


