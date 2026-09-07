package com.example.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.core.design.LocalFocusColors
import com.example.data.model.QuickActionItem
import com.example.data.model.QuickActionType

/**
 * Quick Action Card component matching the reference image:
 * Circular pastel-tinted icon container, clean bold title, subtitle, and chevron right arrow.
 * Supports both Daylight (clean white) and AMOLED dark themes.
 */
@Composable
fun QuickActionCard(
    item: QuickActionItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val isDark = LocalFocusColors.current.isDark

    val (iconBg, iconTint, iconVector) = when (item.iconType) {
        QuickActionType.START_POMODORO -> Triple(
            if (isDark) Color(0xFF132F20) else Color(0xFFE6F9EE),
            FocusColors.EmeraldSuccess,
            Icons.Rounded.PlayArrow
        )
        QuickActionType.STUDY_CHANNELS -> Triple(
            if (isDark) Color(0xFF33230A) else Color(0xFFFEF3E2),
            FocusColors.AmberOrange,
            Icons.Rounded.AutoStories
        )
        QuickActionType.BLOCKED_APPS -> Triple(
            if (isDark) Color(0xFF351515) else Color(0xFFFEECEB),
            FocusColors.BlockedRed,
            Icons.Rounded.Block
        )
        QuickActionType.APP_LIMITS -> Triple(
            if (isDark) Color(0xFF26183C) else Color(0xFFF3E8FF),
            Color(0xFFA855F7),
            Icons.Rounded.HourglassBottom
        )
        QuickActionType.STRICT_MODE -> Triple(
            if (isDark) Color(0xFF13253D) else Color(0xFFE0F2FE),
            Color(0xFF0284C7),
            Icons.Rounded.Shield
        )
        QuickActionType.SESSION_HISTORY -> Triple(
            if (isDark) Color(0xFF26183C) else Color(0xFFEDE8FF),
            FocusColors.Primary,
            Icons.Rounded.Leaderboard
        )
    }

    val cardShape = RoundedCornerShape(18.dp)

    Box(
        modifier = modifier
            .shadow(
                elevation = if (isDark) 0.dp else 2.dp,
                shape = cardShape,
                ambientColor = Color.Black.copy(alpha = 0.03f),
                spotColor = Color.Black.copy(alpha = 0.04f)
            )
            .clip(cardShape)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, cardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp)
            .testTag("quick_action_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle icon container
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = item.title,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Title and Subtitle
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = FocusColors.TextPrimary
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.subtitle.isNotBlank()) {
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Normal,
                            fontSize = 11.sp,
                            color = FocusColors.TextSecondary
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Chevron right arrow
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = FocusColors.TextMuted,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
