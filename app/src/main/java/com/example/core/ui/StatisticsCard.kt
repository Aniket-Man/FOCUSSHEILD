package com.example.core.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.design.FocusColors
import com.example.core.design.FocusType
import com.example.data.model.FocusMetric

private val StatCardShape = RoundedCornerShape(16.dp)

/**
 * Statistics overview row displaying 3 compact metric cards.
 */
@Composable
fun StatisticsOverview(
    metrics: List<FocusMetric>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("statistics_overview"),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        metrics.forEach { metric ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(StatCardShape)
                    .background(FocusColors.Surface)
                    .border(1.dp, FocusColors.CardBorderSubtle, StatCardShape)
                    .padding(vertical = 16.dp, horizontal = 10.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = metric.value + metric.unit,
                        style = FocusType.statNumber.copy(color = metric.accentColor)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = metric.title,
                        style = FocusType.secondary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
