package com.example.feature.rewards.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.feature.rewards.domain.RewardBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Focused Rewards & Milestones overview for the Profile hub.
 *
 * Rather than displaying all badges and tiers on the profile screen,
 * this component displays exclusively:
 * 1. The student's current achieved milestone badge.
 * 2. The immediate upcoming reward badge with live progress.
 * 3. Direct navigation to the dedicated Rewards section where all badges live.
 */
@Composable
fun ProfileRewardsSummarySection(
    totalLifetimeStudyMillis: Long,
    allTimeStudyTimeFormatted: String,
    claimedRewardIds: Set<String>,
    badgeUnlockTimes: Map<String, Long> = emptyMap(),
    onNavigateToAllRewards: () -> Unit,
    onClaimReward: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val badges = RewardBadge.ALL_BADGES
    val unlockedBadges = remember(totalLifetimeStudyMillis) {
        badges.filter { it.isUnlocked(totalLifetimeStudyMillis) }
    }
    val currentAchievedBadge = unlockedBadges.lastOrNull()
    val upcomingBadge = remember(totalLifetimeStudyMillis) {
        badges.firstOrNull { !it.isUnlocked(totalLifetimeStudyMillis) }
    }
    val claimableBadges = remember(totalLifetimeStudyMillis, claimedRewardIds) {
        badges.filter { it.isClaimable(totalLifetimeStudyMillis, claimedRewardIds) }
    }

    var selectedBadgeForDetail by remember { mutableStateOf<RewardBadge?>(null) }
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FocusSpacing.screenHorizontal)
            .testTag("profile_rewards_summary_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Section Header Row with link to full Rewards Section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(FocusColors.Primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "REWARDS & MILESTONES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.5.sp
                    )
                )
            }

            TextButton(
                onClick = onNavigateToAllRewards,
                modifier = Modifier.testTag("profile_rewards_view_all_header_btn")
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "View All (${unlockedBadges.size}/${badges.size})",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = FocusColors.Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = "View all",
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }

        // Unclaimed Rewards Alert Banner (if any)
        if (claimableBadges.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FocusShapes.card)
                    .clickable { selectedBadgeForDetail = claimableBadges.first() }
                    .testTag("profile_rewards_claimable_banner"),
                color = FocusColors.AmberOrange.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, FocusColors.AmberOrange.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🎁", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "${claimableBadges.size} ${if (claimableBadges.size == 1) "Reward" else "Rewards"} Ready to Claim!",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = FocusColors.AmberOrange,
                                    fontSize = 13.sp
                                )
                            )
                            Text(
                                text = "Tap to scratch & unlock your bonus perk",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Surface(
                        shape = FocusShapes.pill,
                        color = FocusColors.AmberOrange,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "Claim",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }

        // 1. CURRENT ACHIEVED BADGE CARD
        CurrentAchievedBadgeCard(
            badge = currentAchievedBadge,
            isClaimed = currentAchievedBadge?.let { claimedRewardIds.contains(it.id) } ?: false,
            unlockedAtMillis = currentAchievedBadge?.let { badgeUnlockTimes[it.id] },
            dateFormat = dateFormat,
            onClick = {
                if (currentAchievedBadge != null) {
                    selectedBadgeForDetail = currentAchievedBadge
                } else {
                    onNavigateToAllRewards()
                }
            },
            onClaim = {
                currentAchievedBadge?.let { onClaimReward(it.id) }
            }
        )

        // 2. UPCOMING REWARD BADGE CARD
        UpcomingRewardBadgeCard(
            badge = upcomingBadge,
            totalLifetimeStudyMillis = totalLifetimeStudyMillis,
            allTimeStudyTimeFormatted = allTimeStudyTimeFormatted,
            onClick = {
                if (upcomingBadge != null) {
                    selectedBadgeForDetail = upcomingBadge
                } else {
                    onNavigateToAllRewards()
                }
            }
        )

        // 3. EXPLORE ALL BADGES NAVIGATION ROW
        ExploreAllBadgesRow(
            unlockedCount = unlockedBadges.size,
            totalCount = badges.size,
            onClick = onNavigateToAllRewards
        )
    }

    // Detail & Claim Dialog
    selectedBadgeForDetail?.let { badge ->
        BadgeDetailDialog(
            badge = badge,
            totalLifetimeStudyMillis = totalLifetimeStudyMillis,
            isClaimed = claimedRewardIds.contains(badge.id),
            unlockedAtMillis = badgeUnlockTimes[badge.id],
            dateFormat = dateFormat,
            onClaim = {
                onClaimReward(badge.id)
                selectedBadgeForDetail = null
            },
            onDismiss = { selectedBadgeForDetail = null }
        )
    }
}

/**
 * Visual card displaying the student's current achieved milestone badge.
 */
@Composable
private fun CurrentAchievedBadgeCard(
    badge: RewardBadge?,
    isClaimed: Boolean,
    unlockedAtMillis: Long?,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit,
    onClaim: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag("profile_current_achieved_card")
    ) {
        if (badge != null) {
            val primaryColor = Color(badge.primaryColorHex)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Pill header: Status & Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = FocusShapes.pill,
                        color = FocusColors.EmeraldSuccess.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, FocusColors.EmeraldSuccess.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = FocusColors.EmeraldSuccess,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "CURRENT ACHIEVED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.EmeraldSuccess,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                    }

                    if (unlockedAtMillis != null) {
                        Text(
                            text = "Earned ${dateFormat.format(Date(unlockedAtMillis))}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.TextMuted,
                                fontSize = 10.5.sp
                            )
                        )
                    }
                }

                // Badge Content Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BadgeEmblemArt(
                        badge = badge,
                        isUnlocked = true,
                        size = 52.dp,
                        showGlow = true
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = badge.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary,
                                fontSize = 14.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = badge.tier.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = primaryColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp
                                )
                            )
                            Text(
                                text = " • ${(badge.requiredDurationMillis / 60000)}m milestone",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.TextSecondary,
                                    fontSize = 10.5.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${badge.reward.emoji} ${badge.reward.title} • ${badge.reward.type}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 11.5.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    if (!isClaimed) {
                        Button(
                            onClick = onClaim,
                            shape = FocusShapes.pill,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryColor,
                                contentColor = Color.White
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                horizontal = 12.dp,
                                vertical = 4.dp
                            ),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                text = "Claim",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    } else {
                        Surface(
                            shape = FocusShapes.pill,
                            color = FocusColors.SurfaceSubtle,
                            border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)
                        ) {
                            Text(
                                text = "Claimed ✓",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        } else {
            // Empty state for current achieved
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(FocusColors.SurfaceSubtle)
                        .border(1.dp, FocusColors.CardBorderSubtle, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.WorkspacePremium,
                        contentDescription = null,
                        tint = FocusColors.TextSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "First Milestone Awaits",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary,
                            fontSize = 14.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Complete your first 30-minute focus session to unlock your Spark badge!",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 11.5.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Visual card displaying the immediate upcoming reward badge with live progress.
 */
@Composable
private fun UpcomingRewardBadgeCard(
    badge: RewardBadge?,
    totalLifetimeStudyMillis: Long,
    allTimeStudyTimeFormatted: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .clickable(onClick = onClick)
            .padding(14.dp)
            .testTag("profile_upcoming_reward_card")
    ) {
        if (badge != null) {
            val primaryColor = Color(badge.primaryColorHex)
            val progressRatio = badge.getProgressRatio(totalLifetimeStudyMillis)
            val remainingMillis = (badge.requiredDurationMillis - totalLifetimeStudyMillis).coerceAtLeast(0L)
            val remainingMinutes = remainingMillis / (60 * 1000L)
            val remainingText = if (remainingMinutes >= 60) {
                "${remainingMinutes / 60}h ${remainingMinutes % 60}m left"
            } else {
                "${remainingMinutes}m left"
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Pill header: Tag & Progress percentage
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = FocusShapes.pill,
                        color = FocusColors.AmberOrange.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, FocusColors.AmberOrange.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.HourglassBottom,
                                contentDescription = null,
                                tint = FocusColors.AmberOrange,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "UPCOMING REWARD",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.AmberOrange,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.5.sp,
                                    letterSpacing = 0.5.sp
                                )
                            )
                        }
                    }

                    Text(
                        text = "${(progressRatio * 100).toInt()}% Unlocked",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = primaryColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }

                // Badge Content Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BadgeEmblemArt(
                        badge = badge,
                        isUnlocked = false,
                        size = 52.dp,
                        showGlow = false
                    )

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = badge.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary,
                                fontSize = 14.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = badge.tier.label,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = primaryColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.5.sp
                                )
                            )
                            Text(
                                text = " • ${badge.requiredHoursFloat}h requirement",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.TextSecondary,
                                    fontSize = 10.5.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${badge.reward.emoji} Unlocks: ${badge.reward.title}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.AmberOrange,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Progress Bar & Stats
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(FocusShapes.pill),
                        color = primaryColor,
                        trackColor = FocusColors.SurfaceSubtle
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Current: $allTimeStudyTimeFormatted",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 10.5.sp
                            )
                        )
                        Text(
                            text = remainingText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp
                            )
                        )
                    }
                }
            }
        } else {
            // Completed all badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(FocusColors.PrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "All Milestones Conquered! 🏆",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary,
                            fontSize = 14.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "You have unlocked every achievement in the hall of fame. Legendary focus!",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 11.5.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Prominent navigation card leading to the dedicated Rewards section.
 */
@Composable
private fun ExploreAllBadgesRow(
    unlockedCount: Int,
    totalCount: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .clickable(onClick = onClick)
            .testTag("profile_explore_all_rewards_card"),
        color = FocusColors.Surface,
        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(FocusColors.Primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.WorkspacePremium,
                    contentDescription = null,
                    tint = FocusColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "View All Badges & Rewards",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = FocusColors.TextPrimary,
                        fontSize = 13.5.sp
                    )
                )
                Text(
                    text = "Browse all $totalCount tiered milestones, perks & rules ($unlockedCount earned)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 11.sp
                    )
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                contentDescription = null,
                tint = FocusColors.TextSecondary,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
