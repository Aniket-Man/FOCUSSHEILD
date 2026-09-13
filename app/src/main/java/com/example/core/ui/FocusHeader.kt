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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.design.LocalFocusColors
import com.example.feature.profile.ui.UserProfileAvatar

/**
 * Top App Header matching the FocusShield Clean Minimalism design.
 * Features brand shield logo badge, title, quick Light/Dark theme switcher, notification bell, and user avatar.
 */
@Composable
fun FocusHeader(
    modifier: Modifier = Modifier,
    title: String = "Home",
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.md)
            .testTag("focus_header"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Left: Shield Icon + Brand Name + Subtitle
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Shield Logo Badge
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                FocusColors.PrimaryLight,
                                FocusColors.Primary
                            )
                        )
                    )
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(FocusSpacing.md))

            Column {
                Text(
                    text = "FOCUSSHIELD",
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.Primary,
                        fontSize = 10.sp,
                        letterSpacing = 1.2.sp
                    )
                )
                Text(
                    text = title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 20.sp
                    )
                )
            }
        }

        // Right: Theme Mode Switcher + Notification Bell + Purple Shield Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Theme Mode Toggle button (Light / Dark)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(if (isDark) 0.dp else 2.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.04f))
                    .clip(CircleShape)
                    .background(FocusColors.Surface)
                    .border(1.dp, FocusColors.CardBorderSubtle, CircleShape)
                    .clickable(onClick = onThemeToggle)
                    .testTag("theme_toggle_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = if (isDark) "Switch to Light Theme" else "Switch to Dark Theme",
                    tint = if (isDark) FocusColors.AmberOrange else FocusColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Notification button. Deliberately styled as a *notification* rather than as one more
            // neutral icon button in the row: a filled bell, and while an update is pending the
            // whole button takes the accent fill so the unread state is legible before the badge is.
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(if (isDark) 0.dp else 2.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.04f))
                    .clip(CircleShape)
                    .background(if (showNotificationDot) FocusColors.Primary else FocusColors.Surface)
                    .border(
                        width = 1.dp,
                        color = if (showNotificationDot) FocusColors.Primary else FocusColors.CardBorderSubtle,
                        shape = CircleShape
                    )
                    .clickable(onClick = onNotificationClick)
                    .testTag("notification_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Notifications,
                    contentDescription = "Notifications",
                    tint = if (showNotificationDot) FocusColors.TextOnDark else FocusColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )

                // Unread-update badge. Sits on the bell's top-end rim and only occupies space when
                // lit, so the bell itself never shifts. The ring is drawn in the button's own fill
                // colour so the badge reads as punched out of it either way.
                if (showNotificationDot) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 4.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(FocusColors.BlockedRed)
                            .border(1.5.dp, FocusColors.Primary, CircleShape)
                            .testTag("notification_dot")
                    )
                }
            }

            // Profile button — the user's own avatar rather than a generic shield: the photo they
            // uploaded from the phone when there is one, otherwise the preset emblem they picked
            // (UserProfileAvatar owns that fallback, so the header and the profile screen cannot
            // disagree about which image represents the user).
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onProfileClick)
                    .testTag("profile_avatar"),
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

                // Same pending-update badge as the bell, off the same state.
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

