package com.example.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Leaderboard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.annotation.StringRes
import com.example.R
import com.example.core.design.FocusCardStyle
import com.example.core.design.FocusColors

enum class BottomTab(
    @StringRes val titleRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    FOCUS(R.string.nav_focus, FocusBrandMark, FocusBrandMark),
    PLANNER(R.string.nav_planner, Icons.Rounded.CalendarMonth, Icons.Outlined.CalendarToday),
    STATS(R.string.nav_stats, Icons.Rounded.Leaderboard, Icons.Outlined.Leaderboard),
    BLOCKS(R.string.nav_blocks, Icons.Rounded.Block, Icons.Outlined.Block)
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

    val glassBg = if (isDark) {
        FocusColors.Surface.copy(alpha = 0.48f)
    } else {
        FocusColors.Surface.copy(alpha = 0.72f)
    }

    val glassBorderBrush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.22f),
                FocusColors.CardBorder.copy(alpha = 0.45f)
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.80f),
                FocusColors.CardBorder.copy(alpha = 0.35f)
            )
        }
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(
                horizontal = FocusCardStyle.BottomDockHorizontalInset,
                vertical = FocusCardStyle.BottomDockVerticalInset
            )
            .testTag("focus_bottom_navigation"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = FocusCardStyle.BottomDockMaxWidth)
                .fillMaxWidth()
                .heightIn(min = FocusCardStyle.BottomDockHeight)
                .shadow(
                    elevation = 12.dp,
                    shape = FocusCardStyle.BottomDockShape,
                    ambientColor = Color.Black.copy(alpha = if (isDark) 0.35f else 0.12f),
                    spotColor = Color.Black.copy(alpha = if (isDark) 0.45f else 0.18f)
                )
                .clip(FocusCardStyle.BottomDockShape)
                .background(glassBg)
                .border(
                    width = 1.dp,
                    brush = glassBorderBrush,
                    shape = FocusCardStyle.BottomDockShape
                )
                .padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .heightIn(min = FocusCardStyle.BottomDockHeight)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomTab.entries.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val title = stringResource(tab.titleRes)
                    // Selection and theme changes are immediate, with a persistent selected fill.
                    val iconColor = if (isSelected) FocusColors.Primary else FocusColors.TextSecondary

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) FocusColors.Primary.copy(alpha = 0.12f) else Color.Transparent)
                            .selectable(
                                selected = isSelected,
                                role = Role.Tab,
                                onClick = { onTabSelected(tab) }
                            )
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                            .testTag("bottom_tab_${tab.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = title,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = iconColor,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
