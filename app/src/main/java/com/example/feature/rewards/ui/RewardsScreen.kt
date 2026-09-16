package com.example.feature.rewards.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.feature.rewards.domain.RewardBadge
import com.example.feature.settings.SettingsViewModel

/**
 * Dedicated Rewards & Milestones Screen.
 * Hosts the full achievements collection, unlock progression, tiered categories,
 * filters, and scratch card reward redemption dialogs.
 */
@Composable
fun RewardsScreen(
    settingsViewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val badges = RewardBadge.ALL_BADGES
    val unlockedCount = badges.count { it.isUnlocked(uiState.allTimeStudyTimeMillis) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = FocusColors.Background,
        topBar = {
            RewardsTopBar(
                unlockedCount = unlockedCount,
                totalCount = badges.size,
                onNavigateBack = onNavigateBack
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(top = 12.dp, bottom = FocusSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 640.dp)
                ) {
                    ProfileRewardsSection(
                        totalLifetimeStudyMillis = uiState.allTimeStudyTimeMillis,
                        allTimeStudyTimeFormatted = uiState.formattedAllTimeStudyTime,
                        claimedRewardIds = uiState.preferences.claimedRewardIds,
                        badgeUnlockTimes = uiState.badgeUnlockTimes,
                        onClaimReward = { rewardId -> settingsViewModel.claimReward(rewardId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun RewardsTopBar(
    unlockedCount: Int,
    totalCount: Int,
    onNavigateBack: () -> Unit
) {
    Surface(
        color = FocusColors.Background,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FocusSpacing.screenHorizontal, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(FocusColors.SurfaceVariant)
                    .testTag("rewards_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = FocusColors.TextPrimary
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Rewards & Milestones",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary
                    )
                )
                Text(
                    text = "All study achievements & unlocks",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary
                    )
                )
            }

            Surface(
                shape = FocusShapes.pill,
                color = FocusColors.Primary.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, FocusColors.Primary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$unlockedCount / $totalCount",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.Primary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }
    }
}
