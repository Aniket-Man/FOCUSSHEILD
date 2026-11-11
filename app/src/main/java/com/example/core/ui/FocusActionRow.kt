package com.example.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.core.design.FocusColors
import com.example.core.design.FocusSpacing

/** A visible disclosure with native feedback and text that grows instead of competing for row width. */
@Composable
fun FocusActionRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    value: String? = null,
    description: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp).padding(FocusSpacing.base),
        horizontalArrangement = Arrangement.spacedBy(FocusSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
            Text(title, color = FocusColors.TextPrimary, style = MaterialTheme.typography.titleMedium)
            if (value != null) {
                Text(value, color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"))
            }
            if (description != null) {
                Text(description, color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = FocusColors.TextSecondary)
    }
}
