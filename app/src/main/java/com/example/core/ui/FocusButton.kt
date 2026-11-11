package com.example.core.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes

/**
 * Standard Primary Action Button for FocusShield.
 */
@Composable
fun FocusPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String = "focus_primary_button"
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = FocusShapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = FocusColors.Primary,
            contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = FocusColors.Primary.copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag(testTag)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
