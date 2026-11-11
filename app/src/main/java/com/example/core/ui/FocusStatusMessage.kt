package com.example.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing

/** Stable polite region. Feedback remains available until dismissed; no timed errors. */
@Composable
fun FocusStatusMessage(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false
) {
    Box(modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        if (message != null) {
            val foreground = if (isError) FocusColors.BlockedText else FocusColors.EmeraldText
            Surface(
                color = if (isError) FocusColors.BlockedLight else FocusColors.EmeraldLight,
                contentColor = foreground,
                shape = FocusShapes.medium
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = FocusSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (isError) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle, contentDescription = null)
                    Text(message, modifier = Modifier.weight(1f).padding(vertical = FocusSpacing.md), style = MaterialTheme.typography.bodyMedium)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, stringResource(R.string.action_dismiss_message))
                    }
                }
            }
        }
    }
}
