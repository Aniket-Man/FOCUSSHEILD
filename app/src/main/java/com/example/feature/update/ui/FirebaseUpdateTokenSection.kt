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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.example.feature.update.domain.SemanticVersion
import com.example.feature.update.domain.UpdateInfo

/**
 * Displays past releases published for FocusShield.
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
