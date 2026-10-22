package com.example.feature.update.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.feature.update.data.ExternalFirebaseUpdateConfig
import com.example.feature.update.domain.SemanticVersion
import com.example.feature.update.domain.UpdateInfo

/**
 * Displays the external Firebase Update Server & Token connection status and opens a configuration
 * modal where the user can paste a single `fsupd_...` Connection Token, Firebase JSON config, or
 * individual project credentials from their companion Release Admin Website.
 */
@Composable
fun FirebaseUpdateServerCard(
    config: ExternalFirebaseUpdateConfig?,
    onApplyTokenOrConfig: (String, (Result<ExternalFirebaseUpdateConfig>) -> Unit) -> Unit,
    onDisconnectToken: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showConfigModal by remember { mutableStateOf(false) }
    var statusBanner by remember { mutableStateOf<String?>(null) }

    val isConnected = config != null && config.isValid
    val accent = if (isConnected) FocusColors.EmeraldSuccess else FocusColors.Primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(FocusSpacing.lg)
            .testTag("firebase_update_server_card")
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isConnected) Icons.Rounded.CloudDone else Icons.Rounded.CloudQueue,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(FocusSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Firebase Release Server",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accent.copy(alpha = 0.16f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (isConnected) "CONNECTED" else "NOT LINKED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = accent,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isConnected && config != null) {
                            "Project: ${config.projectId} • ${config.sourceLabel} • Channel: ${config.normalizedChannel}"
                        } else {
                            "Link your external Firebase Release Portal via Connection Token or AI Studio Secrets (.env)."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            if (isConnected && config != null) {
                Spacer(modifier = Modifier.height(FocusSpacing.sm))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(FocusColors.SurfaceSubtle)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Token Fingerprint: ${config.tokenPreview}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.Monospace,
                            color = FocusColors.TextMuted,
                            fontSize = 11.sp
                        )
                    )
                }
            }

            if (!statusBanner.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(FocusSpacing.sm))
                Text(
                    text = statusBanner.orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.EmeraldSuccess,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(FocusSpacing.md))

            Row(horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                OutlinedButton(
                    onClick = { showConfigModal = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("update_configure_token_button"),
                    shape = FocusShapes.button,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FocusColors.Primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isConnected) "Update Token / Config" else "Connect Firebase Token",
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                }

                if (isConnected && config?.sourceLabel == "In-App Token") {
                    OutlinedButton(
                        onClick = {
                            onDisconnectToken()
                            statusBanner = "Disconnected in-app Firebase token."
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("update_disconnect_token_button"),
                        shape = FocusShapes.button,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = FocusColors.CoralWarning
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LinkOff,
                            contentDescription = "Disconnect Token",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }

    if (showConfigModal) {
        FirebaseTokenConfigDialog(
            currentConfig = config,
            onDismiss = { showConfigModal = false },
            onSaveRawInput = { rawInput, onError ->
                onApplyTokenOrConfig(rawInput) { result ->
                    result.onSuccess { saved ->
                        statusBanner = "Connected to Firebase project '${saved.projectId}' and checking for updates…"
                        showConfigModal = false
                    }.onFailure { err ->
                        onError(err.message ?: "Invalid Firebase token or configuration.")
                    }
                }
            }
        )
    }
}

@Composable
private fun FirebaseTokenConfigDialog(
    currentConfig: ExternalFirebaseUpdateConfig?,
    onDismiss: () -> Unit,
    onSaveRawInput: (String, onError: (String) -> Unit) -> Unit
) {
    var rawTokenOrJson by remember { mutableStateOf("") }
    var manualProjectId by remember { mutableStateOf(currentConfig?.projectId.orEmpty()) }
    var manualApiKey by remember { mutableStateOf(currentConfig?.apiKey.orEmpty()) }
    var manualBucket by remember { mutableStateOf(currentConfig?.storageBucket.orEmpty()) }
    var manualChannel by remember { mutableStateOf(currentConfig?.normalizedChannel ?: "production") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("firebase_token_config_dialog"),
        shape = FocusShapes.card,
        containerColor = FocusColors.Surface,
        title = {
            Text(
                text = "Connect External Firebase Updater",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                Text(
                    text = "Paste the Connection Token (fsupd_...), Firebase JSON, or .env block from your Release Admin Website — or enter your Project ID below.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        lineHeight = 17.sp
                    )
                )

                OutlinedTextField(
                    value = rawTokenOrJson,
                    onValueChange = {
                        rawTokenOrJson = it
                        errorMessage = null
                    },
                    label = { Text("Connection Token (fsupd_...) or Firebase JSON") },
                    placeholder = { Text("fsupd_eyJwcm9qZWN0SWQiOi...") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("firebase_token_input")
                )

                Text(
                    text = "OR ENTER MANUAL CREDENTIALS",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.TextMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                )

                OutlinedTextField(
                    value = manualProjectId,
                    onValueChange = {
                        manualProjectId = it
                        errorMessage = null
                    },
                    label = { Text("Firebase Project ID") },
                    placeholder = { Text("my-release-project-id") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("firebase_project_id_input")
                )

                OutlinedTextField(
                    value = manualApiKey,
                    onValueChange = {
                        manualApiKey = it
                        errorMessage = null
                    },
                    label = { Text("Firebase Web API Key (optional for public rules)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("firebase_api_key_input")
                )

                OutlinedTextField(
                    value = manualBucket,
                    onValueChange = {
                        manualBucket = it
                        errorMessage = null
                    },
                    label = { Text("Storage Bucket (optional)") },
                    placeholder = { Text("my-release-project-id.firebasestorage.app") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("firebase_bucket_input")
                )

                OutlinedTextField(
                    value = manualChannel,
                    onValueChange = { manualChannel = it },
                    label = { Text("Release Channel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.CoralWarning,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val payload = if (rawTokenOrJson.isNotBlank()) {
                        rawTokenOrJson.trim()
                    } else if (manualProjectId.isNotBlank()) {
                        ExternalFirebaseUpdateConfig(
                            projectId = manualProjectId.trim(),
                            apiKey = manualApiKey.trim(),
                            storageBucket = manualBucket.trim(),
                            channelToken = manualChannel.trim().ifBlank { "production" }
                        ).toJsonString()
                    } else {
                        ""
                    }
                    onSaveRawInput(payload) { err -> errorMessage = err }
                },
                shape = FocusShapes.button,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusColors.Primary,
                    contentColor = FocusColors.TextOnDark
                ),
                modifier = Modifier.testTag("firebase_token_save_button")
            ) {
                Text("Save & Check Updates")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = FocusColors.TextSecondary)
            }
        }
    )
}

/**
 * Displays all past releases published from the companion Firebase Release Admin Website.
 */
@Composable
fun ReleaseHistorySection(
    releases: List<UpdateInfo>,
    installedVersionName: String,
    modifier: Modifier = Modifier
) {
    if (releases.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("update_release_history_section"),
        verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                tint = FocusColors.Primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "RELEASE HISTORY (${releases.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = FocusColors.TextMuted,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 0.6.sp
                )
            )
        }

        releases.forEachIndexed { index, release ->
            ReleaseHistoryItemCard(
                release = release,
                isLatest = index == 0,
                isInstalled = SemanticVersion.normalize(release.versionName) ==
                    SemanticVersion.normalize(installedVersionName)
            )
        }
    }
}

@Composable
private fun ReleaseHistoryItemCard(
    release: UpdateInfo,
    isLatest: Boolean,
    isInstalled: Boolean
) {
    var expanded by remember { mutableStateOf(isLatest) }
    val statusColor = when {
        isInstalled -> FocusColors.EmeraldSuccess
        isLatest -> FocusColors.Primary
        else -> FocusColors.TextMuted
    }
    val statusLabel = when {
        isInstalled -> "INSTALLED"
        isLatest -> "LATEST"
        else -> "PAST RELEASE"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .clickable { expanded = !expanded }
            .padding(FocusSpacing.base)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "v${release.versionName}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary
                            )
                        )
                        release.versionCode?.let { code ->
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "(build $code)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.TextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(statusColor.copy(alpha = 0.14f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp
                                )
                            )
                        }
                    }
                    if (release.title.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = release.title,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                    formatPublishedDate(release.publishedAt)?.let { date ->
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Published $date",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.TextMuted,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = if (expanded) "Collapse release notes" else "Expand release notes",
                    tint = FocusColors.TextMuted
                )
            }

            AnimatedVisibility(visible = expanded && release.releaseNotes.isNotBlank()) {
                Column(
                    modifier = Modifier.padding(top = FocusSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    parseReleaseNotes(release.releaseNotes).forEach { block ->
                        when (block) {
                            is NoteBlock.Heading -> Text(
                                text = inlineMarkdown(block.text),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = FocusColors.TextPrimary
                                )
                            )
                            is NoteBlock.Bullet -> Row {
                                Text(
                                    text = "•",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.Primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Spacer(modifier = Modifier.width(FocusSpacing.sm))
                                Text(
                                    text = inlineMarkdown(block.text),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextSecondary,
                                        lineHeight = 17.sp
                                    )
                                )
                            }
                            is NoteBlock.Paragraph -> Text(
                                text = inlineMarkdown(block.text),
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
}
