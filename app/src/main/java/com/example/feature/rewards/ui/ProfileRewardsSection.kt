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
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.feature.rewards.domain.BadgeTier
import com.example.feature.rewards.domain.RewardBadge
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * How a badge presents itself. Earned-but-claimed and earned-but-unclaimed are genuinely different
 * states to the student — one still has a reward waiting — so they are modelled separately rather
 * than collapsed into a single "unlocked" flag.
 */
enum class BadgeVisualState {
    LOCKED,
    NEWLY_UNLOCKED,
    CLAIMED
}

/**
 * The achievements section: an overall progress header, then tiered badge collections, then the
 * per-badge detail sheet that carries the reward and, when one is waiting, the claim action.
 *
 * @param claimedRewardIds badge ids whose reward the student has already claimed. Cloud-synced, so
 *   the claimed state follows the account rather than the handset.
 * @param badgeUnlockTimes badge id to the instant it was earned, derived from synced session
 *   history. Absent entries simply render without an earned date.
 */
@Composable
fun ProfileRewardsSection(
    totalLifetimeStudyMillis: Long,
    allTimeStudyTimeFormatted: String,
    claimedRewardIds: Set<String>,
    badgeUnlockTimes: Map<String, Long> = emptyMap(),
    onClaimReward: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val badges = RewardBadge.ALL_BADGES
    val unlockedCount = badges.count { it.isUnlocked(totalLifetimeStudyMillis) }
    val claimableBadges = badges.filter { it.isClaimable(totalLifetimeStudyMillis, claimedRewardIds) }
    val nextBadge = badges.firstOrNull { !it.isUnlocked(totalLifetimeStudyMillis) }

    var selectedBadgeForDetail by remember { mutableStateOf<RewardBadge?>(null) }
    var selectedFilter by remember { mutableStateOf(BadgeFilter.ALL) }

    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.getDefault()) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("profile_rewards_section")
    ) {
        AchievementsHeader(
            unlockedCount = unlockedCount,
            totalCount = badges.size,
            allTimeStudyTimeFormatted = allTimeStudyTimeFormatted,
            nextBadge = nextBadge,
            nextProgress = nextBadge?.getProgressRatio(totalLifetimeStudyMillis) ?: 1f
        )

        if (claimableBadges.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(modifier = Modifier.padding(horizontal = FocusSpacing.screenHorizontal)) {
                ClaimableCallout(
                    count = claimableBadges.size,
                    accentColor = Color(claimableBadges.first().primaryColorHex),
                    onClick = { selectedBadgeForDetail = claimableBadges.first() }
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        BadgeFilterRow(
            selected = selectedFilter,
            onSelect = { selectedFilter = it },
            totalCount = badges.size,
            unlockedCount = unlockedCount,
            claimableCount = claimableBadges.size
        )

        val sections = remember(selectedFilter, totalLifetimeStudyMillis, claimedRewardIds) {
            BADGE_SECTIONS.map { section ->
                section to badges
                    .filter { it.tier in section.tiers }
                    .filter { selectedFilter.matches(it, totalLifetimeStudyMillis, claimedRewardIds) }
            }
        }

        sections.forEach { (section, sectionBadges) ->
            if (sectionBadges.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                BadgeCollectionRow(
                    title = section.title,
                    badges = sectionBadges,
                    totalLifetimeStudyMillis = totalLifetimeStudyMillis,
                    claimedRewardIds = claimedRewardIds,
                    onBadgeClick = { selectedBadgeForDetail = it },
                    onClaim = onClaimReward
                )
            }
        }
    }

    selectedBadgeForDetail?.let { badge ->
        BadgeDetailDialog(
            badge = badge,
            totalLifetimeStudyMillis = totalLifetimeStudyMillis,
            isClaimed = claimedRewardIds.contains(badge.id),
            unlockedAtMillis = badgeUnlockTimes[badge.id],
            dateFormat = dateFormat,
            onClaim = { onClaimReward(badge.id) },
            onDismiss = { selectedBadgeForDetail = null }
        )
    }
}

/** Badge id is claimed when it is earned and its reward has not been collected yet. */
private fun RewardBadge.isClaimable(totalLifetimeStudyMillis: Long, claimedRewardIds: Set<String>) =
    isUnlocked(totalLifetimeStudyMillis) && !claimedRewardIds.contains(id)

private fun RewardBadge.visualState(
    totalLifetimeStudyMillis: Long,
    claimedRewardIds: Set<String>
): BadgeVisualState = when {
    !isUnlocked(totalLifetimeStudyMillis) -> BadgeVisualState.LOCKED
    claimedRewardIds.contains(id) -> BadgeVisualState.CLAIMED
    else -> BadgeVisualState.NEWLY_UNLOCKED
}

private enum class BadgeFilter(val label: String) {
    ALL("All"),
    UNLOCKED("Unlocked"),
    CLAIMABLE("Ready to Claim"),
    IN_PROGRESS("In Progress"),
    LEGENDARY("Legendary");

    fun matches(
        badge: RewardBadge,
        totalLifetimeStudyMillis: Long,
        claimedRewardIds: Set<String>
    ): Boolean = when (this) {
        ALL -> true
        UNLOCKED -> badge.isUnlocked(totalLifetimeStudyMillis)
        CLAIMABLE -> badge.isClaimable(totalLifetimeStudyMillis, claimedRewardIds)
        IN_PROGRESS -> !badge.isUnlocked(totalLifetimeStudyMillis)
        LEGENDARY -> badge.tier in LEGENDARY_TIERS
    }
}

private val LEGENDARY_TIERS = setOf(
    BadgeTier.SOLAR,
    BadgeTier.CELESTIAL,
    BadgeTier.MYTHIC,
    BadgeTier.DIAMOND
)

private data class BadgeSection(val title: String, val tiers: Set<BadgeTier>)

/** Collections mirror the badge ladder: early milestones, the titan band, then the legendary tail. */
private val BADGE_SECTIONS = listOf(
    BadgeSection(
        title = "🚀 Study Ignition & Scholars",
        tiers = setOf(BadgeTier.NOVICE, BadgeTier.BRONZE, BadgeTier.GOLD, BadgeTier.EMERALD)
    ),
    BadgeSection(
        title = "⚡ Titans & Grandmasters",
        tiers = setOf(BadgeTier.AMETHYST, BadgeTier.RUBY, BadgeTier.DIAMOND)
    ),
    BadgeSection(
        title = "👑 Century Legends & Apex Celestials",
        tiers = setOf(BadgeTier.SOLAR, BadgeTier.CELESTIAL, BadgeTier.MYTHIC)
    )
)

/**
 * Overall progress summary: how much focus time has been banked, how much of the collection is
 * complete, and — the part that actually drives the next session — what the next badge costs.
 */
@Composable
private fun AchievementsHeader(
    unlockedCount: Int,
    totalCount: Int,
    allTimeStudyTimeFormatted: String,
    nextBadge: RewardBadge?,
    nextProgress: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = FocusSpacing.screenHorizontal)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(FocusColors.CoralWarning.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = FocusColors.CoralWarning,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "ACHIEVEMENTS & BADGES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 0.6.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Surface(
                shape = FocusShapes.pill,
                color = FocusColors.CoralWarning.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, FocusColors.CoralWarning.copy(alpha = 0.35f))
            ) {
                Text(
                    text = "$unlockedCount of $totalCount",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.CoralWarning,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FocusShapes.card)
                .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card),
            color = FocusColors.Surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "LIFETIME FOCUS",
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

                    if (nextBadge != null) {
                        val targetColor = Color(nextBadge.primaryColorHex)
                        Surface(
                            shape = FocusShapes.pill,
                            color = targetColor.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, targetColor.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.WorkspacePremium,
                                    contentDescription = null,
                                    tint = targetColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "${nextBadge.requiredHoursFloat.toInt()}h next",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = targetColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    ),
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (nextBadge != null) {
                    val targetColor = Color(nextBadge.primaryColorHex)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = nextBadge.title,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 11.sp
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${(nextProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = targetColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { nextProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(FocusShapes.pill),
                        color = targetColor,
                        trackColor = FocusColors.SurfaceSubtle
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = FocusColors.EmeraldSuccess,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Every badge mastered 🏆",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.EmeraldSuccess,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

/** Shown only while a reward is genuinely waiting, so it stays a prompt rather than wallpaper. */
@Composable
private fun ClaimableCallout(
    count: Int,
    accentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.medium)
            .clickable(onClick = onClick)
            .border(1.dp, accentColor.copy(alpha = 0.45f), FocusShapes.medium),
        color = accentColor.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🎁", fontSize = 18.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (count == 1) "A reward is ready to claim" else "$count rewards are ready to claim",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                )
                Text(
                    text = "Tap to open it",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 10.sp
                    )
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
private fun BadgeFilterRow(
    selected: BadgeFilter,
    onSelect: (BadgeFilter) -> Unit,
    totalCount: Int,
    unlockedCount: Int,
    claimableCount: Int
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = FocusSpacing.screenHorizontal)
    ) {
        val options = listOf(
            BadgeFilter.ALL to "All ($totalCount)",
            BadgeFilter.UNLOCKED to "🏆 Unlocked ($unlockedCount)",
            BadgeFilter.CLAIMABLE to "🎁 To Claim ($claimableCount)",
            BadgeFilter.IN_PROGRESS to "⏳ In Progress (${totalCount - unlockedCount})",
            BadgeFilter.LEGENDARY to "👑 Legendary"
        )
        items(options) { (filter, label) ->
            val isSelected = selected == filter
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
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
}

@Composable
private fun BadgeCollectionRow(
    title: String,
    badges: List<RewardBadge>,
    totalLifetimeStudyMillis: Long,
    claimedRewardIds: Set<String>,
    onBadgeClick: (RewardBadge) -> Unit,
    onClaim: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FocusSpacing.screenHorizontal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = FocusColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (badges.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                val earned = badges.count { it.isUnlocked(totalLifetimeStudyMillis) }
                Text(
                    text = "$earned/${badges.size}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.TextMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = FocusSpacing.screenHorizontal, vertical = 4.dp)
        ) {
            items(badges, key = { it.id }) { badge ->
                RewardBadgeCard(
                    badge = badge,
                    state = badge.visualState(totalLifetimeStudyMillis, claimedRewardIds),
                    progressRatio = badge.getProgressRatio(totalLifetimeStudyMillis),
                    onOpen = { onBadgeClick(badge) },
                    onClaim = { onClaim(badge.id) }
                )
            }
        }
    }
}

/**
 * The badge itself: container, emblem, identity, requirement or progress, and the reward it pays
 * out. Named `RewardBadgeCard` because `RewardBadge` is the domain model this renders.
 */
@Composable
fun RewardBadgeCard(
    badge: RewardBadge,
    state: BadgeVisualState,
    progressRatio: Float,
    onOpen: () -> Unit,
    onClaim: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = Color(badge.primaryColorHex)
    val accentColor = Color(badge.accentColorHex)
    val isEarned = state != BadgeVisualState.LOCKED

    Card(
        modifier = modifier
            .width(232.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
            .border(
                width = if (state == BadgeVisualState.NEWLY_UNLOCKED) 2.dp else 1.dp,
                color = when (state) {
                    BadgeVisualState.LOCKED -> FocusColors.CardBorderSubtle
                    BadgeVisualState.NEWLY_UNLOCKED -> primaryColor.copy(alpha = 0.75f)
                    BadgeVisualState.CLAIMED -> primaryColor.copy(alpha = 0.35f)
                },
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("badge_card_${badge.id}"),
        colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (state == BadgeVisualState.NEWLY_UNLOCKED) 6.dp else 2.dp
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .background(
                        when (state) {
                            BadgeVisualState.LOCKED -> Brush.verticalGradient(
                                colors = listOf(FocusColors.SurfaceSubtle, FocusColors.Surface)
                            )
                            BadgeVisualState.CLAIMED -> Brush.verticalGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.18f),
                                    primaryColor.copy(alpha = 0.08f),
                                    FocusColors.Surface
                                )
                            )
                            BadgeVisualState.NEWLY_UNLOCKED -> Brush.verticalGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.42f),
                                    primaryColor.copy(alpha = 0.20f),
                                    FocusColors.Surface
                                )
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                BadgeStateChip(
                    state = state,
                    color = primaryColor,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                )

                Surface(
                    shape = FocusShapes.pill,
                    color = if (isEarned) primaryColor.copy(alpha = 0.2f) else FocusColors.SurfaceSubtle,
                    border = BorderStroke(
                        1.dp,
                        if (isEarned) primaryColor.copy(alpha = 0.4f) else FocusColors.CardBorderSubtle
                    ),
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
                            color = if (isEarned) primaryColor else FocusColors.TextMuted
                        )
                    )
                }

                BadgeEmblemArt(
                    badge = badge,
                    isUnlocked = isEarned,
                    size = 72.dp,
                    pulse = state == BadgeVisualState.NEWLY_UNLOCKED
                )
            }

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
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = badge.subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (isEarned) primaryColor else FocusColors.TextSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                // The reward this badge pays out — the reason to chase it, shown in every state.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = badge.reward.emoji, fontSize = 13.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badge.reward.title,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (isEarned) FocusColors.TextPrimary else FocusColors.TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                when (state) {
                    BadgeVisualState.CLAIMED -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = FocusColors.EmeraldSuccess,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Reward claimed",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.EmeraldSuccess,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }

                    BadgeVisualState.NEWLY_UNLOCKED -> Button(
                        onClick = onClaim,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp),
                        shape = FocusShapes.pill,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(
                            text = "Claim Reward",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            ),
                            maxLines = 1
                        )
                    }

                    BadgeVisualState.LOCKED -> Column {
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
                                    text = "${badge.requiredHoursFloat.toInt()}h target",
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

@Composable
private fun BadgeStateChip(
    state: BadgeVisualState,
    color: Color,
    modifier: Modifier = Modifier
) {
    val (text, chipColor) = when (state) {
        BadgeVisualState.LOCKED -> "LOCKED" to FocusColors.TextMuted
        BadgeVisualState.NEWLY_UNLOCKED -> "NEW" to color
        BadgeVisualState.CLAIMED -> "CLAIMED" to FocusColors.EmeraldSuccess
    }
    Surface(
        shape = FocusShapes.pill,
        color = chipColor.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, chipColor.copy(alpha = 0.5f)),
        modifier = modifier
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = chipColor
            )
        )
    }
}

/**
 * Full badge detail: the emblem at size, what it took, what it pays, when it was earned, and the
 * claim action when a reward is still waiting.
 */
@Composable
private fun BadgeDetailDialog(
    badge: RewardBadge,
    totalLifetimeStudyMillis: Long,
    isClaimed: Boolean,
    unlockedAtMillis: Long?,
    dateFormat: SimpleDateFormat,
    onClaim: () -> Unit,
    onDismiss: () -> Unit
) {
    val state = when {
        !badge.isUnlocked(totalLifetimeStudyMillis) -> BadgeVisualState.LOCKED
        isClaimed -> BadgeVisualState.CLAIMED
        else -> BadgeVisualState.NEWLY_UNLOCKED
    }
    val primaryColor = Color(badge.primaryColorHex)
    val progressRatio = badge.getProgressRatio(totalLifetimeStudyMillis)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FocusColors.Surface,
        titleContentColor = FocusColors.TextPrimary,
        textContentColor = FocusColors.TextSecondary,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BadgeEmblemArt(badge = badge, isUnlocked = state != BadgeVisualState.LOCKED, size = 40.dp)
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BadgeEmblemArt(
                        badge = badge,
                        isUnlocked = state != BadgeVisualState.LOCKED,
                        size = 88.dp,
                        pulse = state == BadgeVisualState.NEWLY_UNLOCKED
                    )
                }

                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextPrimary,
                        lineHeight = 20.sp
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // What the badge pays out.
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FocusShapes.small)
                        .border(1.dp, primaryColor.copy(alpha = 0.35f), FocusShapes.small),
                    color = primaryColor.copy(alpha = 0.08f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = badge.reward.emoji, fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = badge.reward.title,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = FocusColors.TextPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = badge.reward.message,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.TextSecondary,
                                    fontSize = 11.sp
                                )
                            )
                            Text(
                                text = badge.reward.type,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = primaryColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.6.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

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
                            fontStyle = FontStyle.Italic
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                when (state) {
                    BadgeVisualState.CLAIMED -> Column {
                        DetailStatusRow(
                            text = "Reward claimed",
                            color = FocusColors.EmeraldSuccess,
                            icon = Icons.Rounded.CheckCircle
                        )
                        unlockedAtMillis?.let {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Earned ${dateFormat.format(Date(it))}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.TextMuted,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    BadgeVisualState.NEWLY_UNLOCKED -> Column {
                        DetailStatusRow(
                            text = "Earned — reward ready",
                            color = primaryColor,
                            icon = Icons.Rounded.CheckCircle
                        )
                        unlockedAtMillis?.let {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Earned ${dateFormat.format(Date(it))}",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.TextMuted,
                                    fontSize = 11.sp
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onClaim,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp),
                            shape = FocusShapes.pill,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryColor,
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = "Claim Reward",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    BadgeVisualState.LOCKED -> Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Required: ${(badge.requiredDurationMillis / 3_600_000.0).toInt()} hours",
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
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Close",
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.Primary
                )
            }
        }
    )
}

@Composable
private fun DetailStatusRow(
    text: String,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        shape = FocusShapes.pill,
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            )
        }
    }
}
