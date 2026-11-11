package com.example.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusSpacing
import com.example.core.design.LocalFocusColors
import com.example.feature.profile.ui.UserProfileAvatar

/**
 * Top App Header matching the FocusShield Clean Minimalism design.
 * The title can wrap independently of the theme and profile actions.
 */
@Composable
fun FocusHeader(
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.header_home),
    photoUri: String? = null,
    avatarPresetId: String = "SHIELD",
    onNotificationClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onThemeToggle: () -> Unit = {},
    /**
     * Draws the small red "new notification" dot on the bell. Driven by the single application-wide
     * update state (`UpdateManager.showDot`), so it can never disagree with the Profile → New Updates
     * indicator. It decorates the existing bell rather than adding a second icon.
     */
    showNotificationDot: Boolean = false,
    /**
     * The same pending-update dot, mirrored onto the avatar. Two entry points to one piece of state:
     * whichever the user reaches for, the badge agrees, because both are fed by `UpdateManager.showDot`.
     */
    showProfileDot: Boolean = false
) {
    val isDark = LocalFocusColors.current.isDark
    val profileLabel = stringResource(R.string.header_open_profile)
    val updateLabel = stringResource(R.string.update_available_status)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.md)
            .testTag("focus_header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: folded-map brand mark + name + subtitle
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Official FocusShield logo badge
            Image(
                painter = painterResource(R.drawable.ic_focus_shield_app_icon),
                contentDescription = null,
                modifier = Modifier.size(42.dp)
            )

            Spacer(modifier = Modifier.width(FocusSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.brand_wordmark),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.Primary,
                        letterSpacing = 1.2.sp
                    )
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary
                    ),
                    modifier = Modifier.semantics { heading() }
                )
            }
        }

        Spacer(modifier = Modifier.width(FocusSpacing.sm))

        // Right: theme switcher and profile avatar button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FocusSpacing.xs)
        ) {
            // Theme Mode Toggle button (Light / Dark)
            IconButton(
                onClick = onThemeToggle,
                modifier = Modifier
                    .size(48.dp)
                    .shadow(if (isDark) 0.dp else 2.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.04f))
                    .clip(CircleShape)
                    .background(FocusColors.Surface)
                    .border(1.dp, FocusColors.CardBorderSubtle, CircleShape)
                    .testTag("theme_toggle_button")
            ) {
                Icon(
                    imageVector = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = stringResource(
                        if (isDark) R.string.header_switch_to_light else R.string.header_switch_to_dark
                    ),
                    tint = if (isDark) FocusColors.AmberOrange else FocusColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Profile button — displays the user's Google Gmail account profile picture or selected preset
            IconButton(
                onClick = onProfileClick,
                modifier = Modifier
                    .size(48.dp)
                    .semantics {
                        contentDescription = profileLabel
                        if (showProfileDot) stateDescription = updateLabel
                    }
                    .testTag("profile_avatar")
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clearAndSetSemantics { },
                    contentAlignment = Alignment.Center
                ) {
                    UserProfileAvatar(
                        photoUri = photoUri,
                        avatarPresetId = avatarPresetId,
                        size = 40.dp,
                        modifier = Modifier.shadow(
                            3.dp,
                            CircleShape,
                            ambientColor = FocusColors.Primary.copy(alpha = 0.3f)
                        )
                    )

                    if (showProfileDot) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(11.dp)
                                .clip(CircleShape)
                                .background(FocusColors.BlockedRed)
                                .border(2.dp, FocusColors.Background, CircleShape)
                                .testTag("profile_update_dot")
                        )
                    }
                }
            }
        }
    }
}
