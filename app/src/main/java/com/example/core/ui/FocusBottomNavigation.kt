package com.example.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes

enum class BottomTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    FOCUS("Focus", Icons.Rounded.HourglassBottom, Icons.Outlined.HourglassEmpty),
    PLANNER("Planner", Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarToday),
    STATS("Stats", Icons.Rounded.Leaderboard, Icons.Outlined.Leaderboard),
    BLOCKS("Blocks", Icons.Rounded.Block, Icons.Outlined.Block)
}

/**
 * Modern floating translucent glass navigation bar.
 * Designed with optical glass layering, specular top-edge highlighting,
 * and adaptive opacity across Dark (48%) and Light (72%) themes.
 */
@Composable
fun FocusBottomNavigation(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = com.example.core.design.LocalFocusColors.current.isDark

    // Modern translucent glass surface:
    // Dark mode: ~48% opacity with subtle highlight reflection.
    // Light mode: ~72% opacity for crisp content legibility and soft refraction.
    val glassBg = if (isDark) {
        FocusColors.Surface.copy(alpha = 0.48f)
    } else {
        FocusColors.Surface.copy(alpha = 0.72f)
    }

    val glassBorderBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.22f), // Specular top sheen
                FocusColors.CardBorder.copy(alpha = 0.45f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.80f), // Crisp top light bounce
                FocusColors.CardBorder.copy(alpha = 0.35f)
            )
        }
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("focus_bottom_navigation"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Main Glass Pill: Focus, Planner, Stats
            Box(
                modifier = Modifier
                    .weight(3.2f)
                    .height(64.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(32.dp),
                        ambientColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.12f),
                        spotColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.18f)
                    )
                    .clip(RoundedCornerShape(32.dp))
                    .background(glassBg)
                    .border(
                        width = 1.dp,
                        brush = glassBorderBrush,
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val mainTabs = listOf(BottomTab.FOCUS, BottomTab.PLANNER, BottomTab.STATS)
                    mainTabs.forEach { tab ->
                        val isSelected = tab == selectedTab
                        val iconColor by animateColorAsState(
                            targetValue = if (isSelected) FocusColors.Primary else FocusColors.TextSecondary,
                            animationSpec = tween(180),
                            label = "tab_icon_color"
                        )

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { onTabSelected(tab) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .testTag("bottom_tab_${tab.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.title,
                                    tint = iconColor,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = tab.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = iconColor,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 10.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Blocks Glass Pill Button
            val isBlocksSelected = selectedTab == BottomTab.BLOCKS
            val blocksIconColor by animateColorAsState(
                targetValue = if (isBlocksSelected) FocusColors.Primary else FocusColors.TextSecondary,
                animationSpec = tween(180),
                label = "blocks_icon_color"
            )

            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .height(64.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.12f),
                        spotColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.18f)
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(glassBg)
                    .border(
                        width = 1.dp,
                        brush = glassBorderBrush,
                        shape = RoundedCornerShape(24.dp)
                    )
                    .clickable { onTabSelected(BottomTab.BLOCKS) }
                    .testTag("bottom_tab_blocks"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isBlocksSelected) BottomTab.BLOCKS.selectedIcon else BottomTab.BLOCKS.unselectedIcon,
                        contentDescription = "Blocks",
                        tint = blocksIconColor,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Blocks",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = blocksIconColor,
                            fontWeight = if (isBlocksSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}
