package com.example.feature.update.ui

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.feature.update.domain.DownloadProgress
import com.example.feature.update.domain.UpdateInfo
import com.example.feature.update.domain.UpdateState

/**
 * The dedicated update screen (prompt.txt §8), reached from Profile → New Updates.
 *
 * State comes entirely from [UpdateViewModel], which reads the application-scoped manager, so
 * rotating the device or leaving and returning never restarts a download and never loses the
 * "downloaded" state (prompt.txt §13).
 *
 * Release notes are rendered from the release body as native Compose text — never as HTML
 * (prompt.txt §12). Nothing here installs anything: the final step hands a content URI to the
 * system installer, which asks the user itself (prompt.txt §9/§21).
 */
@Composable
fun UpdateScreen(
    viewModel: UpdateViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Android only shows the installer UI for an app the user has allowed to install unknown apps.
    // We never bypass it — we explain it and deep-link into the setting (prompt.txt §10).
    var pendingInstall by remember { mutableStateOf<Intent?>(null) }
    val installLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // The user either completed the install or backed out; re-checking on return is what
        // eventually clears the red dot and moves the screen off "update available" (prompt.txt §7).
        viewModel.checkForUpdates()
    }
    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // Returning from the permission screen: continue straight to the installer when allowed.
        val intent = pendingInstall
        pendingInstall = null
        if (intent != null && viewModel.canInstallPackages()) {
            installLauncher.launch(intent)
        }
    }

    fun startInstall() {
        val intent = viewModel.createInstallIntent() ?: return
        if (viewModel.canInstallPackages()) {
            installLauncher.launch(intent)
        } else {
            pendingInstall = intent
            unknownSourcesLauncher.launch(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    android.net.Uri.parse("package:${context.packageName}")
                )
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FocusColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FocusSpacing.screenHorizontal)
        ) {
            Spacer(modifier = Modifier.height(FocusSpacing.sm))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("update_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = FocusColors.TextPrimary
                    )
                }
                Spacer(modifier = Modifier.width(FocusSpacing.xs))
                Text(
                    text = "New Updates",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(FocusSpacing.lg))

            when (val current = state) {
                is UpdateState.UpdateAvailable -> AvailableContent(
                    info = current.info,
                    installedVersion = viewModel.installedVersionName,
                    onDownload = { viewModel.download() },
                    onCheckAgain = { viewModel.checkForUpdates() }
                )

                is UpdateState.Downloading -> DownloadingContent(
                    progress = current.progress,
                    onCancel = { viewModel.cancelDownload() }
                )

                is UpdateState.Downloaded -> DownloadedContent(
                    info = current.info,
                    onInstall = { startInstall() },
                    onCheckAgain = { viewModel.checkForUpdates() }
                )

                UpdateState.Checking -> StatusContent(
                    icon = { CircularProgressIndicator(color = FocusColors.Primary, strokeWidth = 2.dp) },
                    title = "Checking for updates…",
                    message = "Looking for the latest FocusShield release.",
                    showCheckButton = false
                )

                UpdateState.Installing -> StatusContent(
                    // Reached only if a future build learns the installer's outcome. The handoff
                    // itself happens in the system installer, which the app cannot observe
                    // (prompt.txt §9).
                    icon = { CircularProgressIndicator(color = FocusColors.Primary, strokeWidth = 2.dp) },
                    title = "Waiting for the installer…",
                    message = "Confirm the installation in the Android dialog to finish updating FocusShield.",
                    showCheckButton = false
                )

                UpdateState.UpToDate -> StatusContent(
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = FocusColors.EmeraldSuccess,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    title = "You're using the latest version.",
                    message = "FocusShield v${viewModel.installedVersionName} is up to date.",
                    showCheckButton = true,
                    onCheck = { viewModel.checkForUpdates() }
                )

                is UpdateState.Failed -> StatusContent(
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            tint = FocusColors.CoralWarning,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    title = "Couldn't check for updates.",
                    message = current.message,
                    showCheckButton = true,
                    onCheck = {
                        viewModel.clearError()
                        viewModel.checkForUpdates()
                    }
                )

                UpdateState.Idle -> StatusContent(
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.NewReleases,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    title = "FocusShield v${viewModel.installedVersionName}",
                    message = "Check whether a newer version of FocusShield is available.",
                    showCheckButton = true,
                    onCheck = { viewModel.checkForUpdates() }
                )
            }

            Spacer(modifier = Modifier.height(FocusSpacing.xxl))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Sections
// ---------------------------------------------------------------------------------------------

@Composable
private fun AvailableContent(
    info: UpdateInfo,
    installedVersion: String,
    onDownload: () -> Unit,
    onCheckAgain: () -> Unit
) {
    HeaderCard(
        title = "New update available",
        subtitle = "FocusShield v${info.versionName} is ready to download.",
        accent = FocusColors.Primary,
        icon = Icons.Rounded.SystemUpdate
    )

    Spacer(modifier = Modifier.height(FocusSpacing.base))

    VersionCard(installedVersion = installedVersion, latestVersion = info.versionName)

    Spacer(modifier = Modifier.height(FocusSpacing.base))

    if (info.releaseNotes.isNotBlank() || info.title.isNotBlank()) {
        ReleaseNotesCard(info = info)
        Spacer(modifier = Modifier.height(FocusSpacing.base))
    }

    if (info.isInstallable) {
        PrimaryAction(
            label = "Download Update",
            icon = Icons.Rounded.Download,
            testTag = "update_download_button",
            onClick = onDownload
        )
    } else {
        // prompt.txt §3/§23 TEST 8 — the release exists but carries no APK asset. Say so plainly
        // rather than offering a button that cannot work.
        InfoNotice(
            text = "Update is available, but the APK is not available for download yet.",
            tint = FocusColors.AmberOrange
        )
    }

    Spacer(modifier = Modifier.height(FocusSpacing.md))

    SecondaryAction(
        label = "Check again",
        testTag = "update_check_again_button",
        onClick = onCheckAgain
    )
}

@Composable
private fun DownloadingContent(progress: DownloadProgress, onCancel: () -> Unit) {
    val determinate = progress.isDeterminate
    val animated by animateFloatAsState(
        targetValue = progress.fraction,
        animationSpec = tween(300),
        label = "updateDownloadProgress"
    )

    HeaderCard(
        title = "Downloading update",
        subtitle = "Keep FocusShield open until the download finishes.",
        accent = FocusColors.CyanBlue,
        icon = Icons.Rounded.Download
    )

    Spacer(modifier = Modifier.height(FocusSpacing.base))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(FocusSpacing.lg)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (determinate) "Progress" else "Downloading",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = FocusColors.TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )
                // The exact percentage when the server gave us a total; otherwise the bytes received
                // so far. Never a fabricated number (prompt.txt §"UNKNOWN CONTENT LENGTH").
                Text(
                    text = progress.percent?.let { "$it%" } ?: formatBytes(progress.bytesReceived),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.Primary
                    ),
                    modifier = Modifier.testTag("update_download_percent")
                )
            }
            Spacer(modifier = Modifier.height(FocusSpacing.md))
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
                // No Content-Length: an honest indeterminate bar beats a bar frozen at 0%.
                LinearProgressIndicator(
                    modifier = barModifier,
                    color = FocusColors.Primary,
                    trackColor = FocusColors.SurfaceSubtle
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(FocusSpacing.base))

    SecondaryAction(
        label = "Cancel download",
        testTag = "update_cancel_download_button",
        onClick = onCancel
    )
}

/**
 * `15_728_640` → `15.0 MB`. Used only on the indeterminate path, where the honest thing to show is
 * how much has arrived rather than a percentage nobody can compute.
 */
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun DownloadedContent(
    info: UpdateInfo,
    onInstall: () -> Unit,
    onCheckAgain: () -> Unit
) {
    HeaderCard(
        title = "Download complete",
        subtitle = "Tap install, then confirm in the Android installer.",
        accent = FocusColors.EmeraldSuccess,
        icon = Icons.Rounded.CheckCircle
    )

    Spacer(modifier = Modifier.height(FocusSpacing.base))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(FocusSpacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = FocusColors.TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(FocusSpacing.sm))
            Text(
                text = "Android will ask you to confirm the installation. FocusShield never installs " +
                    "anything on its own.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = FocusColors.TextSecondary,
                    fontSize = 12.sp
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(FocusSpacing.base))

    PrimaryAction(
        label = "Install Update",
        icon = Icons.Rounded.SystemUpdate,
        testTag = "update_install_button",
        onClick = onInstall
    )

    Spacer(modifier = Modifier.height(FocusSpacing.md))

    SecondaryAction(
        label = "Check again",
        testTag = "update_downloaded_check_again_button",
        onClick = onCheckAgain
    )
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

@Composable
private fun HeaderCard(
    title: String,
    subtitle: String,
    accent: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.25f), FocusShapes.card)
            .padding(FocusSpacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(FocusSpacing.base))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary
                    ),
                    modifier = Modifier.testTag("update_status_title")
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun VersionCard(installedVersion: String, latestVersion: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(FocusSpacing.lg)
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            VersionColumn(
                label = "INSTALLED",
                value = installedVersion,
                tint = FocusColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            VersionColumn(
                label = "LATEST",
                value = latestVersion,
                tint = FocusColors.Primary
            )
        }
    }
}

@Composable
private fun VersionColumn(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = FocusColors.TextMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.6.sp
            )
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = tint,
                fontSize = 18.sp
            )
        )
    }
}

/** Release title, published date and the notes themselves (prompt.txt §8/§12). */
@Composable
private fun ReleaseNotesCard(info: UpdateInfo) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(FocusSpacing.lg)
    ) {
        Column {
            if (info.title.isNotBlank()) {
                Text(
                    text = info.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary
                    ),
                    modifier = Modifier.testTag("update_release_title")
                )
            }
            val published = formatPublishedDate(info.publishedAt)
            if (published != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Published $published",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextMuted,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.testTag("update_published_date")
                )
            }

            if (info.releaseNotes.isNotBlank()) {
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
                    modifier = Modifier.testTag("update_release_notes"),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    parseReleaseNotes(info.releaseNotes).forEach { block ->
                        when (block) {
                            is NoteBlock.Heading -> Text(
                                text = inlineMarkdown(block.text),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
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
                                        lineHeight = 18.sp
                                    )
                                )
                            }

                            is NoteBlock.Paragraph -> Text(
                                text = inlineMarkdown(block.text),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextSecondary,
                                    lineHeight = 18.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoNotice(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.button)
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.30f), FocusShapes.button)
            .padding(FocusSpacing.base)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(
                color = FocusColors.TextPrimary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            ),
            modifier = Modifier.testTag("update_no_apk_notice")
        )
    }
}

@Composable
private fun StatusContent(
    icon: @Composable () -> Unit,
    title: String,
    message: String,
    showCheckButton: Boolean,
    onCheck: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(FocusSpacing.xl)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            icon()
            Spacer(modifier = Modifier.height(FocusSpacing.md))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary
                ),
                modifier = Modifier.testTag("update_status_title")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = FocusColors.TextSecondary,
                    fontSize = 12.sp
                )
            )
            if (showCheckButton) {
                Spacer(modifier = Modifier.height(FocusSpacing.lg))
                PrimaryAction(
                    label = "Check for updates",
                    icon = Icons.Rounded.Refresh,
                    testTag = "update_check_button",
                    onClick = onCheck
                )
            }
        }
    }
}

@Composable
private fun PrimaryAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    testTag: String,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(testTag),
        shape = FocusShapes.button,
        colors = ButtonDefaults.buttonColors(
            containerColor = FocusColors.Primary,
            contentColor = FocusColors.TextOnDark
        )
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(FocusSpacing.sm))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
private fun SecondaryAction(label: String, testTag: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag(testTag),
        shape = FocusShapes.button,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = FocusColors.TextSecondary
        )
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Release-notes rendering
// ---------------------------------------------------------------------------------------------

/**
 * Internal rather than file-private because [UpdateAvailableDialog] reuses the same parse for its
 * short preview, so both screens present the release body identically.
 */
internal sealed interface NoteBlock {
    data class Heading(val text: String) : NoteBlock
    data class Bullet(val text: String) : NoteBlock
    data class Paragraph(val text: String) : NoteBlock
}

/**
 * Minimal, deliberately narrow Markdown reader for release notes.
 *
 * It understands the shapes GitHub release bodies actually use — headings, `-`/`*` bullets,
 * `[ ]`/`[x]` checkboxes, and paragraphs — and nothing more. The input is plain text from the API;
 * no HTML is ever parsed or rendered, and anything unrecognised falls through as literal text
 * (prompt.txt §12).
 */
internal fun parseReleaseNotes(markdown: String): List<NoteBlock> {
    val blocks = mutableListOf<NoteBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        if (paragraph.isNotBlank()) blocks += NoteBlock.Paragraph(paragraph.toString().trim())
        paragraph.clear()
    }

    markdown.lines().forEach { rawLine ->
        val line = rawLine.trimEnd()
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> flushParagraph()

            trimmed.startsWith("###") ->
                { flushParagraph(); blocks += NoteBlock.Heading(trimmed.removePrefix("###").trim()) }

            trimmed.startsWith("##") ->
                { flushParagraph(); blocks += NoteBlock.Heading(trimmed.removePrefix("##").trim()) }

            trimmed.startsWith("#") ->
                { flushParagraph(); blocks += NoteBlock.Heading(trimmed.removePrefix("#").trim()) }

            // Bullets and GitHub task-list items, which share the same visual treatment.
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") ->
                {
                    flushParagraph()
                    val body = trimmed.drop(2).trim()
                    // `- [x] done` / `- [ ] todo` — drop the checkbox marker, keep the sentence.
                    val cleaned = body
                        .removePrefix("[x]").removePrefix("[X]").removePrefix("[ ]").trim()
                    blocks += NoteBlock.Bullet(cleaned)
                }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(trimmed)
            }
        }
    }
    flushParagraph()
    return blocks
}

/**
 * Renders `**bold**`, `` `code` `` and `_italic_` spans; every other character is passed through
 * literally. An unmatched delimiter is left as-is rather than swallowing the rest of the line.
 */
@Composable
internal fun inlineMarkdown(text: String) = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        val char = text[index]
        val marker = when {
            text.startsWith("**", index) -> "**"
            char == '`' -> "`"
            else -> null
        }
        if (marker == null) {
            append(char)
            index++
            continue
        }
        val close = text.indexOf(marker, index + marker.length)
        if (close == -1) {
            append(char)
            index++
        } else {
            val inner = text.substring(index + marker.length, close)
            withStyle(
                SpanStyle(
                    fontWeight = if (marker == "`") FontWeight.Normal else FontWeight.Bold,
                    fontFamily = if (marker == "`") FontFamily.Monospace else null,
                    color = if (marker == "`") FocusColors.Primary else FocusColors.TextPrimary
                )
            ) {
                append(inner)
            }
            index = close + marker.length
        }
    }
}

/** `2026-09-11T10:00:00Z` → `11 Sep 2026`. Returns null when the timestamp is absent or unusable. */
internal fun formatPublishedDate(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return try {
        val datePart = raw.substringBefore('T')
        val parts = datePart.split('-')
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        val monthName = listOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
        ).getOrNull(month - 1) ?: return null
        "$day $monthName $year"
    } catch (_: Exception) {
        null
    }
}
