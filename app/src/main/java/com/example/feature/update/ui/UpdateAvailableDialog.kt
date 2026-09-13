package com.example.feature.update.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.feature.update.domain.UpdateInfo

/**
 * The once-per-version "update available" popup (prompt.txt §6).
 *
 * Raised by the navigation host — not by the update screen — so it can appear wherever the user
 * happens to be when a check comes back. Visibility is owned by the manager via
 * `UpdateState.UpdateAvailable.showPrompt`, which is cleared permanently for a version once "Later"
 * is pressed, so navigating around cannot bring it back.
 */
@Composable
fun UpdateAvailableDialog(
    info: UpdateInfo,
    onDownload: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        modifier = Modifier.testTag("update_available_dialog"),
        shape = FocusShapes.card,
        containerColor = FocusColors.Surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(FocusColors.Primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = FocusColors.Primary,
                    modifier = Modifier.size(22.dp)
                )
            }
        },
        title = {
            Text(
                text = "Update available",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary
                )
            )
        },
        text = {
            Column {
                Text(
                    text = "FocusShield v${info.versionName} is available.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextSecondary
                    )
                )

                val notes = notesPreview(info)
                if (notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(FocusSpacing.md))
                    Text(
                        text = "WHAT'S NEW",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.6.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(FocusSpacing.sm))
                    Column(
                        // Bounded so a long release body cannot push the buttons off screen.
                        modifier = Modifier
                            .heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        notes.forEach { line ->
                            Row {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.Primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.width(FocusSpacing.sm))
                                Text(
                                    text = line,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextSecondary,
                                        lineHeight = 17.sp
                                    )
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDownload,
                modifier = Modifier.testTag("update_dialog_download_button")
            ) {
                Text(
                    text = "Download Update",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = FocusColors.Primary
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onLater,
                modifier = Modifier.testTag("update_dialog_later_button")
            ) {
                Text(
                    text = "Later",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = FocusColors.TextSecondary
                )
            }
        }
    )
}

/**
 * A short, plain-text preview of the release body for the dialog. The full formatted notes live on
 * the update screen; this only has to be scannable and never grow unbounded.
 */
private fun notesPreview(info: UpdateInfo): List<String> =
    parseReleaseNotes(info.releaseNotes)
        .filter { it !is NoteBlock.Heading }
        .map {
            when (it) {
                is NoteBlock.Bullet -> it.text
                is NoteBlock.Paragraph -> it.text
                is NoteBlock.Heading -> it.text
            }
        }
        .map { it.replace("**", "").replace("`", "") }
        .filter { it.isNotBlank() }
        .take(6)
