package com.example.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusSpacing

/**
 * A content-height header: long titles and subtitles wrap without clipping native navigation
 * controls, and every screen gets the same 16 dp gutter.
 */
@Composable
fun FocusScreenHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backButtonModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm)
    ) {
        IconButton(onClick = onBack, modifier = backButtonModifier) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), tint = FocusColors.TextPrimary)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = FocusColors.TextPrimary, style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.semantics { heading() })
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = FocusColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        actions()
    }
}
