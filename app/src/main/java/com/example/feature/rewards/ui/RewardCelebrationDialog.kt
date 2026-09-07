package com.example.feature.rewards.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.MilitaryTech
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.ui.FocusLottieAnimation
import com.example.feature.rewards.domain.BadgeIconType
import com.example.feature.rewards.domain.RewardBadge

@Composable
fun RewardCelebrationDialog(
    badge: RewardBadge = RewardBadge.ALL_BADGES.first { it.id == RewardBadge.ID_2_HOURS_STUDY },
    onClaimReward: () -> Unit,
    onNavigateToProfileRewards: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scalePulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    val goldPrimary = Color(badge.primaryColorHex)
    val goldAccent = Color(badge.accentColorHex)

    Dialog(
        onDismissRequest = onClaimReward,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("reward_celebration_dialog"),
            shape = FocusShapes.card,
            colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
            border = androidx.compose.foundation.BorderStroke(2.dp, goldPrimary.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Lottie Confetti Celebration Animation (top)
                FocusLottieAnimation(
                    rawRes = R.raw.confetti,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp),
                    iterations = 1
                )

                // Header Tag
                Surface(
                    shape = FocusShapes.pill,
                    color = goldPrimary.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, goldPrimary.copy(alpha = 0.4f))
                ) {
                    Text(
                        text = "🏆 NEW REWARD UNLOCKED!",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = goldPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            letterSpacing = 1.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Glowing Trophy Badge Container with Sparkle
                Box(
                    modifier = Modifier.size(110.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BadgeEmblemArt(
                        badge = badge,
                        isUnlocked = true,
                        size = 88.dp,
                        showGlow = true
                    )
                    // Lottie Star Sparkle Effect
                    FocusLottieAnimation(
                        rawRes = R.raw.star_sparkle,
                        modifier = Modifier.size(60.dp),
                        iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever,
                        speed = 0.8f
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title & Subtitle
                Text(
                    text = badge.title,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 22.sp,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = badge.subtitle,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = goldPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Primary Action Button: Claim & Go To Profile
                Button(
                    onClick = {
                        onClaimReward()
                        onNavigateToProfileRewards()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("reward_claim_profile_button"),
                    shape = FocusShapes.button,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = goldPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.WorkspacePremium,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "View Rewards in Profile",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Secondary Action Button: Dismiss & Keep Studying
                OutlinedButton(
                    onClick = onClaimReward,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("reward_keep_studying_button"),
                    shape = FocusShapes.button,
                    border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.CardBorderSubtle)
                ) {
                    Text(
                        text = "Awesome, Keep Studying",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = FocusColors.TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}
