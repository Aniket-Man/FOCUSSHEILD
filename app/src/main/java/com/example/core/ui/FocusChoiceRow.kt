package com.example.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing

/** A single named radio action. The child indicator is decorative, not a second focus stop. */
@Composable
fun FocusChoiceRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth()
            .clip(FocusShapes.medium)
            .background(if (selected) FocusColors.PrimaryContainer else FocusColors.SurfaceVariant)
            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = FocusSpacing.base, vertical = FocusSpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FocusSpacing.md)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
            Text(
                title,
                color = if (selected) FocusColors.OnPrimaryContainer else FocusColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall
            )
            if (description != null) {
                Text(description, color = FocusColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            }
        }
        RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.clearAndSetSemantics { })
    }
}
