package com.example.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            contentColor = Color.White,
            disabledContainerColor = FocusColors.Primary.copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(testTag)
    ) {
        Text(
            text = text,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        )
    }
}

/**
 * Standard Secondary / Outlined Button for FocusShield.
 */
@Composable
fun FocusSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "focus_secondary_button"
) {
    OutlinedButton(
        onClick = onClick,
        shape = FocusShapes.large,
        border = BorderStroke(1.5.dp, FocusColors.CardBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = FocusColors.TextPrimary
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(testTag)
    ) {
        Text(
            text = text,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            )
        )
    }
}
