package com.example.feature.update.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.feature.update.domain.DownloadProgress
import com.example.feature.update.domain.UpdateInfo
import com.example.feature.update.domain.UpdateState

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
            UpdateAvailableDialogBody(info = info, installedVersion = null)
        },
        confirmButton = {
            Button(
                onClick = onDownload,
                shape = FocusShapes.button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusColors.Primary,
                    contentColor = FocusColors.TextOnDark
                ),
                modifier = Modifier.testTag("update_dialog_download_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Download Update",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
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
 * Multi-stage interactive Update Push Modal supporting:
 * 1. UpdateAvailable (with release notes & inline download trigger)
 * 2. Downloading (with live progress bar & "Run in Background" option)
 * 3. Downloaded (with "Install & Restart App" prompt)
 */
@Composable
fun UpdatePushModal(
    state: UpdateState,
    installedVersion: String,
    onDownload: () -> Unit,
    onLater: () -> Unit,
    onHideModal: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallAndRestart: () -> Unit,
    onOpenDetails: () -> Unit
) {
    when (state) {
        is UpdateState.UpdateAvailable -> {
            if (!state.showPrompt) return
            AlertDialog(
                onDismissRequest = onLater,
                modifier = Modifier.testTag("update_available_dialog"),
                shape = FocusShapes.card,
                containerColor = FocusColors.Surface,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(FocusColors.Primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = "New FocusShield Update Available",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary
                        )
                    )
                },
                text = {
                    UpdateAvailableDialogBody(
                        info = state.info,
                        installedVersion = installedVersion
                    )
                },
                confirmButton = {
                    if (state.info.isInstallable) {
                        Button(
                            onClick = onDownload,
                            shape = FocusShapes.button,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FocusColors.Primary,
                                contentColor = FocusColors.TextOnDark
                            ),
                            modifier = Modifier.testTag("update_dialog_download_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Download Update",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                    } else {
                        TextButton(
                            onClick = {
                                onLater()
                                onOpenDetails()
                            },
                            modifier = Modifier.testTag("update_dialog_details_button")
                        ) {
                            Text(
                                text = "View Details",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = FocusColors.Primary
                            )
                        }
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

        is UpdateState.Downloading -> {
            if (!state.showInModal) return
            val progress = state.progress
            val determinate = progress.isDeterminate
            val animated by animateFloatAsState(
                targetValue = progress.fraction,
                animationSpec = tween(250),
                label = "modalDownloadProgress"
            )
            AlertDialog(
                onDismissRequest = onHideModal,
                modifier = Modifier.testTag("update_downloading_dialog"),
                shape = FocusShapes.card,
                containerColor = FocusColors.Surface,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(FocusColors.CyanBlue.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = FocusColors.CyanBlue,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = state.info?.let { "Downloading v${it.versionName}…" } ?: "Downloading Update…",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary
                        )
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Fetching the verified release package from your update server. You can wait here or let it finish in the background.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                lineHeight = 17.sp
                            )
                        )
                        Spacer(modifier = Modifier.height(FocusSpacing.base))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (determinate) "Progress" else "Received",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = FocusColors.TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = buildProgressLabel(progress),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = FocusColors.Primary,
                                    fontWeight = FontWeight.Bold
                                ),
                                modifier = Modifier.testTag("update_modal_download_percent")
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        val barModifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                        if (determinate) {
                            LinearProgressIndicator(
                                progress = { animated },
                                modifier = barModifier,
                                color = FocusColors.Primary,
                                trackColor = FocusColors.SurfaceSubtle
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = barModifier,
                                color = FocusColors.Primary,
                                trackColor = FocusColors.SurfaceSubtle
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = onHideModal,
                        modifier = Modifier.testTag("update_modal_background_button")
                    ) {
                        Text(
                            text = "Run in Background",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = FocusColors.Primary
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.testTag("update_modal_cancel_button")
                    ) {
                        Text(
                            text = "Cancel",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = FocusColors.TextSecondary
                        )
                    }
                }
            )
        }

        is UpdateState.Downloaded -> {
            if (!state.showInModal) return
            AlertDialog(
                onDismissRequest = onHideModal,
                modifier = Modifier.testTag("update_downloaded_dialog"),
                shape = FocusShapes.card,
                containerColor = FocusColors.Surface,
                icon = {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(FocusColors.EmeraldSuccess.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = FocusColors.EmeraldSuccess,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                title = {
                    Text(
                        text = "Update Complete — Ready to Restart",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary
                        )
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "FocusShield v${state.info.versionName} has finished downloading and passed integrity verification.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = FocusColors.TextSecondary
                            )
                        )
                        Spacer(modifier = Modifier.height(FocusSpacing.sm))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(FocusColors.EmeraldSuccess.copy(alpha = 0.10f))
                                .border(1.dp, FocusColors.EmeraldSuccess.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Tap Install & Restart below to apply the update and relaunch FocusShield with the latest version.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextPrimary,
                                    lineHeight = 17.sp
                                )
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = onInstallAndRestart,
                        shape = FocusShapes.button,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FocusColors.EmeraldSuccess,
                            contentColor = FocusColors.TextOnDark
                        ),
                        modifier = Modifier.testTag("update_modal_install_restart_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Install & Restart",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = onHideModal,
                        modifier = Modifier.testTag("update_modal_install_later_button")
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

        else -> {}
    }
}

/**
 * Post-restart confirmation dialog displayed once the user launches the newly updated version.
 */
@Composable
fun WhatsNewDialog(
    info: UpdateInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("whats_new_dialog"),
        shape = FocusShapes.card,
        containerColor = FocusColors.Surface,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(FocusColors.EmeraldSuccess.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.NewReleases,
                    contentDescription = null,
                    tint = FocusColors.EmeraldSuccess,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Text(
                text = "Updated to FocusShield v${info.versionName}",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary
                )
            )
        },
        text = {
            Column {
                Text(
                    text = "Your app has been restarted and updated to the latest release.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = FocusColors.TextSecondary
                    )
                )
                val notes = notesPreview(info)
                if (notes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(FocusSpacing.md))
                    Text(
                        text = "WHAT'S NEW IN V${info.versionName.uppercase()}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = FocusColors.TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            letterSpacing = 0.6.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(FocusSpacing.sm))
                    Column(
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
                                        color = FocusColors.EmeraldSuccess,
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
            Button(
                onClick = onDismiss,
                shape = FocusShapes.button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusColors.Primary,
                    contentColor = FocusColors.TextOnDark
                ),
                modifier = Modifier.testTag("whats_new_dismiss_button")
            ) {
                Text(
                    text = "Awesome",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }
    )
}

@Composable
private fun UpdateAvailableDialogBody(
    info: UpdateInfo,
    installedVersion: String?
) {
    Column {
        Text(
            text = if (!installedVersion.isNullOrBlank()) {
                "FocusShield v${info.versionName} is ready to install (currently v$installedVersion)."
            } else {
                "FocusShield v${info.versionName} is available."
            },
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
}

private fun buildProgressLabel(progress: DownloadProgress): String {
    val pct = progress.percent
    return if (pct != null && progress.totalBytes > 0L) {
        "$pct% (${formatModalBytes(progress.bytesReceived)} / ${formatModalBytes(progress.totalBytes)})"
    } else if (pct != null) {
        "$pct%"
    } else {
        formatModalBytes(progress.bytesReceived)
    }
}

private fun formatModalBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
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
