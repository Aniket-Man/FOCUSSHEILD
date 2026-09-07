package com.example.feature.rewards.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.feature.rewards.domain.BadgeTier
import com.example.feature.rewards.domain.RewardBadge

@Composable
fun ProfileRewardsSection(
    totalLifetimeStudyMillis: Long,
    allTimeStudyTimeFormatted: String,
    claimedRewardIds: Set<String>,
    onClaimReward: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val badges = RewardBadge.ALL_BADGES
    val unlockedCount = badges.count { it.isUnlocked(totalLifetimeStudyMillis) }
    var selectedBadgeForDetail by remember { mutableStateOf<RewardBadge?>(null) }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val filteredBadges = remember(selectedFilter, totalLifetimeStudyMillis) {
        when (selectedFilter) {
            "UNLOCKED" -> badges.filter { it.isUnlocked(totalLifetimeStudyMillis) }
            "IN_PROGRESS" -> badges.filter { !it.isUnlocked(totalLifetimeStudyMillis) }
            "LEGENDARY" -> badges.filter {
                it.tier == BadgeTier.SOLAR || it.tier == BadgeTier.CELESTIAL || it.tier == BadgeTier.MYTHIC || it.tier == BadgeTier.DIAMOND
            }
            else -> badges
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("profile_rewards_section")
    ) {
        // Section Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(FocusColors.CoralWarning.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = FocusColors.CoralWarning,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ACHIEVEMENT TROPHIES & BADGES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.8.sp
                    )
                )
            }

            Surface(
                shape = FocusShapes.pill,
                color = FocusColors.CoralWarning.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, FocusColors.CoralWarning.copy(alpha = 0.3f))
            ) {
                Text(
                    text = "$unlockedCount / ${badges.size} Badges",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.CoralWarning,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Lifetime Study Counter Summary Banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FocusShapes.card)
                .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card),
            color = FocusColors.Surface
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "LIFETIME STUDY HOURS",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = allTimeStudyTimeFormatted,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    )
                }

                val nextTargetBadge = badges.firstOrNull { !it.isUnlocked(totalLifetimeStudyMillis) }
                val targetBadge = nextTargetBadge ?: badges.last()
                val targetColor = Color(targetBadge.primaryColorHex)

                Surface(
                    shape = FocusShapes.pill,
                    color = targetColor.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, targetColor.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WorkspacePremium,
                            contentDescription = null,
                            tint = targetColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (nextTargetBadge != null) "Next: ${targetBadge.subtitle}" else "All Badges Mastered! 🏆",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = targetColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Horizontal Filter Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            val filterOptions = listOf(
                "ALL" to "All Badges (${badges.size})",
                "UNLOCKED" to "🏆 Unlocked ($unlockedCount)",
                "IN_PROGRESS" to "⏳ In Progress (${badges.size - unlockedCount})",
                "LEGENDARY" to "👑 Legendary Tier"
            )
            items(filterOptions) { (key, label) ->
                val isSelected = selectedFilter == key
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = key },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = FocusColors.Surface,
                        selectedContainerColor = FocusColors.Primary.copy(alpha = 0.15f),
                        labelColor = FocusColors.TextSecondary,
                        selectedLabelColor = FocusColors.Primary
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = FocusColors.CardBorderSubtle,
                        selectedBorderColor = FocusColors.Primary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Horizontal Scrolling Showcase Cards Row
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reward_badges_horizontal_carousel"),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp)
        ) {
            items(filteredBadges, key = { it.id }) { badge ->
                val isUnlocked = badge.isUnlocked(totalLifetimeStudyMillis)
                val progressRatio = badge.getProgressRatio(totalLifetimeStudyMillis)

                HorizontalBadgeShowcaseCard(
                    badge = badge,
                    isUnlocked = isUnlocked,
                    progressRatio = progressRatio,
                    onClick = { selectedBadgeForDetail = badge }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tiered Milestone Collections (Categorized Horizontal Rows)
        Text(
            text = "TIER COLLECTIONS",
            style = MaterialTheme.typography.labelSmall.copy(
                color = FocusColors.TextSecondary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp
            )
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Foundation Tier Row
        BadgeTierHorizontalRow(
            title = "🚀 Study Ignition & Scholars",
            badges = badges.filter { it.tier == BadgeTier.NOVICE || it.tier == BadgeTier.BRONZE || it.tier == BadgeTier.GOLD || it.tier == BadgeTier.EMERALD },
            totalLifetimeStudyMillis = totalLifetimeStudyMillis,
            onBadgeClick = { selectedBadgeForDetail = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Titans & Master Tier Row
        BadgeTierHorizontalRow(
            title = "⚡ Titans & Grandmasters",
            badges = badges.filter { it.tier == BadgeTier.AMETHYST || it.tier == BadgeTier.RUBY || it.tier == BadgeTier.DIAMOND },
            totalLifetimeStudyMillis = totalLifetimeStudyMillis,
            onBadgeClick = { selectedBadgeForDetail = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Legendary Tier Row
        BadgeTierHorizontalRow(
            title = "👑 Century Legends & Apex Celestials",
            badges = badges.filter { it.tier == BadgeTier.SOLAR || it.tier == BadgeTier.CELESTIAL || it.tier == BadgeTier.MYTHIC },
            totalLifetimeStudyMillis = totalLifetimeStudyMillis,
            onBadgeClick = { selectedBadgeForDetail = it }
        )
    }

    // Detail Dialog when user taps a badge (Theme Aware)
    selectedBadgeForDetail?.let { badge ->
        val isUnlocked = badge.isUnlocked(totalLifetimeStudyMillis)
        val progressRatio = badge.getProgressRatio(totalLifetimeStudyMillis)
        val primaryColor = Color(badge.primaryColorHex)
        val accentColor = Color(badge.accentColorHex)

        AlertDialog(
            onDismissRequest = { selectedBadgeForDetail = null },
            containerColor = FocusColors.Surface,
            titleContentColor = FocusColors.TextPrimary,
            textContentColor = FocusColors.TextSecondary,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BadgeEmblemArt(
                        badge = badge,
                        isUnlocked = isUnlocked,
                        size = 40.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = badge.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary
                            )
                        )
                        Text(
                            text = badge.tier.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = primaryColor,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            },
            text = {
                Column {
                    // Badge artwork showcase in center
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        BadgeEmblemArt(
                            badge = badge,
                            isUnlocked = isUnlocked,
                            size = 80.dp
                        )
                    }

                    Text(
                        text = badge.description,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = FocusColors.TextPrimary,
                            lineHeight = 20.sp
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Lore Quote Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = FocusColors.SurfaceSubtle,
                        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)
                    ) {
                        Text(
                            text = "\"${badge.quote}\"",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (isUnlocked) {
                        Surface(
                            shape = FocusShapes.pill,
                            color = FocusColors.EmeraldSuccess.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, FocusColors.EmeraldSuccess.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = FocusColors.EmeraldSuccess,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "OFFICIALLY UNLOCKED IN TROPHY CASE",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = FocusColors.EmeraldSuccess,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )
                            }
                        }
                    } else {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Required Target: ${(badge.requiredDurationMillis / 3600000.0).toInt()} Hours",
                                    style = MaterialTheme.typography.labelSmall.copy(color = FocusColors.TextSecondary)
                                )
                                Text(
                                    text = "${(progressRatio * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = primaryColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { progressRatio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(FocusShapes.pill),
                                color = primaryColor,
                                trackColor = FocusColors.SurfaceSubtle
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedBadgeForDetail = null }) {
                    Text(
                        text = "Close",
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.Primary
                    )
                }
            }
        )
    }
}

/**
 * High-Impact Horizontal Showcase Card for Featured Badges
 */
@Composable
fun HorizontalBadgeShowcaseCard(
    badge: RewardBadge,
    isUnlocked: Boolean,
    progressRatio: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = Color(badge.primaryColorHex)
    val accentColor = Color(badge.accentColorHex)

    Card(
        modifier = modifier
            .width(220.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .border(
                1.5.dp,
                if (isUnlocked) primaryColor.copy(alpha = 0.6f) else FocusColors.CardBorderSubtle,
                RoundedCornerShape(20.dp)
            )
            .testTag("badge_card_${badge.id}"),
        colors = CardDefaults.cardColors(
            containerColor = FocusColors.Surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Card Gradient Banner Header with Emblem
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(115.dp)
                    .background(
                        if (isUnlocked) {
                            Brush.verticalGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.35f),
                                    primaryColor.copy(alpha = 0.15f),
                                    FocusColors.Surface
                                )
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(
                                    FocusColors.SurfaceSubtle,
                                    FocusColors.Surface
                                )
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Tier Chip at top right
                Surface(
                    shape = FocusShapes.pill,
                    color = if (isUnlocked) primaryColor.copy(alpha = 0.2f) else FocusColors.SurfaceSubtle,
                    border = BorderStroke(1.dp, if (isUnlocked) primaryColor.copy(alpha = 0.4f) else FocusColors.CardBorderSubtle),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                ) {
                    Text(
                        text = badge.tier.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isUnlocked) primaryColor else FocusColors.TextMuted
                        )
                    )
                }

                // Centered Distinct Badge Emblem
                BadgeEmblemArt(
                    badge = badge,
                    isUnlocked = isUnlocked,
                    size = 64.dp
                )
            }

            // Card Body Information
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = badge.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 14.sp
                    ),
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = badge.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isUnlocked) primaryColor else FocusColors.TextSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (isUnlocked) {
                    Surface(
                        shape = FocusShapes.pill,
                        color = FocusColors.EmeraldSuccess.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, FocusColors.EmeraldSuccess.copy(alpha = 0.3f))
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
                                text = "UNLOCKED 🏆",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.EmeraldSuccess,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                } else {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = FocusColors.TextMuted,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Target: ${badge.requiredHoursFloat.toInt()}h",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = FocusColors.TextMuted,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                            Text(
                                text = "${(progressRatio * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progressRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(FocusShapes.pill),
                            color = primaryColor,
                            trackColor = FocusColors.SurfaceSubtle
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact Horizontal Row for Tiered Badge Collections
 */
@Composable
fun BadgeTierHorizontalRow(
    title: String,
    badges: List<RewardBadge>,
    totalLifetimeStudyMillis: Long,
    onBadgeClick: (RewardBadge) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                color = FocusColors.TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        )
        Spacer(modifier = Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(badges, key = { it.id }) { badge ->
                val isUnlocked = badge.isUnlocked(totalLifetimeStudyMillis)
                val primaryColor = Color(badge.primaryColorHex)

                Surface(
                    modifier = Modifier
                        .width(160.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onBadgeClick(badge) }
                        .border(
                            1.dp,
                            if (isUnlocked) primaryColor.copy(alpha = 0.5f) else FocusColors.CardBorderSubtle,
                            RoundedCornerShape(14.dp)
                        ),
                    color = if (isUnlocked) primaryColor.copy(alpha = 0.06f) else FocusColors.Surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BadgeEmblemArt(
                            badge = badge,
                            isUnlocked = isUnlocked,
                            size = 38.dp,
                            showGlow = false
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = badge.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isUnlocked) FocusColors.TextPrimary else FocusColors.TextSecondary,
                                    fontSize = 11.sp
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = if (isUnlocked) "Unlocked" else "${badge.requiredHoursFloat.toInt()}h goal",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = if (isUnlocked) FocusColors.EmeraldSuccess else FocusColors.TextMuted,
                                    fontSize = 10.sp
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
