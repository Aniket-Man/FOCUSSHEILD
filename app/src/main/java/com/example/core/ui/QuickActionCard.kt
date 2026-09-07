package com.example.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.design.FocusColors
import com.example.core.design.FocusSpacing
import com.example.core.design.FocusType
import com.example.data.model.QuickActionItem
import com.example.data.model.QuickActionType

/**
 * Quick Action Card component matching the reference image grid.
 */
@Composable
fun QuickActionCard(
    item: QuickActionItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    // Color coding is derived from the theme accent for each action type, not ad-hoc pastel hexes,
    // so the icon tint and its container tint are always the same hue at two opacities. This keeps
    // the four tiles visually consistent and theme-aware (they adapt in light and dark).
    val (accent, iconVector) = when (item.iconType) {
        QuickActionType.START_POMODORO -> FocusColors.EmeraldSuccess to Icons.Rounded.Timer
        QuickActionType.STUDY_CHANNELS -> FocusColors.AmberOrange to Icons.Rounded.OndemandVideo
        QuickActionType.BLOCKED_APPS -> FocusColors.BlockedRed to Icons.Rounded.Block
        QuickActionType.SESSION_HISTORY -> FocusColors.Primary to Icons.Rounded.History
    }

    Box(
        modifier = modifier
            .clip(ContainerShape)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, ContainerShape)
            .clickable(onClick = onClick)
            .padding(horizontal = FocusSpacing.base, vertical = FocusSpacing.base)
            .testTag("quick_action_${item.id}")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon square: uniform 44dp, uniform 12dp radius, accent-tinted container.
            Box(
                modifier = Modifier
                    .size(IconContainerSize)
                    .clip(IconContainerShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = item.title,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(FocusSpacing.md))

            // Title and subtitle
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    style = FocusType.primary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = item.subtitle,
                    style = FocusType.caption,
                    maxLines = 1
                )
            }
        }
    }
}

private val ContainerShape = RoundedCornerShape(20.dp)
private val IconContainerShape = RoundedCornerShape(12.dp)
private val IconContainerSize = 44.dp
