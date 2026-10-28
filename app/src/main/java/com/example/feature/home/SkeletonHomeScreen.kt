package com.example.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.ui.SkeletonBox

/**
 * Skeleton placeholder screen matching the HomeScreen layout structure to prevent layout jumps
 * while initial dashboard data is loading.
 */
@Composable
fun SkeletonHomeScreen(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = FocusSpacing.screenHorizontal)
            .testTag("skeleton_home_screen"),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 1. Header Skeleton
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SkeletonBox(
                    modifier = Modifier.size(40.dp),
                    shape = CircleShape
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    SkeletonBox(
                        modifier = Modifier
                            .width(120.dp)
                            .height(18.dp),
                        shape = FocusShapes.small
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SkeletonBox(
                        modifier = Modifier
                            .width(80.dp)
                            .height(12.dp),
                        shape = FocusShapes.small
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SkeletonBox(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape
                )
                SkeletonBox(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape
                )
            }
        }

        // 2. Focus Session Banner Card Skeleton
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            shape = FocusShapes.card
        )

        // 3. Stats Row Skeleton (Today's Study & Phone Usage)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SkeletonBox(
                modifier = Modifier
                    .weight(1f)
                    .height(110.dp),
                shape = FocusShapes.card
            )
            SkeletonBox(
                modifier = Modifier
                    .weight(1f)
                    .height(110.dp),
                shape = FocusShapes.card
            )
        }

        // 4. Quick Actions Section Skeleton
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBox(
                modifier = Modifier
                    .width(130.dp)
                    .height(20.dp),
                shape = FocusShapes.small
            )
            repeat(3) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SkeletonBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp),
                        shape = FocusShapes.card
                    )
                    SkeletonBox(
                        modifier = Modifier
                            .weight(1f)
                            .height(68.dp),
                        shape = FocusShapes.card
                    )
                }
            }
        }

        // 5. Today's Plan Skeleton
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkeletonBox(
                modifier = Modifier
                    .width(120.dp)
                    .height(20.dp),
                shape = FocusShapes.small
            )
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp),
                shape = FocusShapes.card
            )
            SkeletonBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                shape = FocusShapes.card
            )
        }
    }
}
