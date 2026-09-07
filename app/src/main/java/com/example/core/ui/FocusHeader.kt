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
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
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
    onThemeToggle: () -> Unit = {}
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

            // Notification button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(if (isDark) 0.dp else 2.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.04f))
                    .clip(CircleShape)
                    .background(FocusColors.Surface)
                    .border(1.dp, FocusColors.CardBorderSubtle, CircleShape)
                    .clickable(onClick = onNotificationClick)
                    .testTag("notification_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = "Notifications",
                    tint = FocusColors.TextPrimary,
                    modifier = Modifier.size(19.dp)
                )
            }

            // Purple Shield Action Button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .shadow(3.dp, CircleShape, ambientColor = FocusColors.Primary.copy(alpha = 0.3f))
                    .clip(CircleShape)
                    .background(FocusColors.Primary)
                    .clickable(onClick = onProfileClick)
                    .testTag("profile_avatar"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = "Protection Status & Profile",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

