package com.example.feature.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.util.ChannelLogoStorageManager
import com.example.data.local.entity.StudyChannelEntity
import java.util.Locale

@Composable
fun StudyChannelsScreen(
    viewModel: StudyChannelsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        val msg = uiState.feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearFeedbackMessage()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding()
            )
        },
        topBar = {
            Surface(
                color = FocusColors.Surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = FocusSpacing.md, vertical = FocusSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = FocusColors.TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "YouTube Study Guard",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "Auto-Pilot Channel Memory",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(FocusColors.EmeraldLight)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${uiState.totalAllowedCount} Allowed",
                            color = FocusColors.EmeraldText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddDialog() },
                containerColor = FocusColors.Primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("add_channel_fab")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add Study Channel"
                )
            }
        },
        containerColor = FocusColors.Background,
        modifier = modifier.testTag("study_channels_screen")
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(
                    start = FocusSpacing.screenHorizontal,
                    end = FocusSpacing.screenHorizontal,
                    top = 14.dp,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "hero_card") {
                    AutoPilotGuardHeroCard(
                        allowedCount = uiState.totalAllowedCount,
                        blockedCount = uiState.totalBlockedCount,
                        savedPercent = uiState.aiCallsSavedPercent
                    )
                }

                item(key = "tab_switcher") {
                    ChannelSegmentTabs(
                        selectedTab = uiState.selectedTab,
                        allowedCount = uiState.totalAllowedCount,
                        blockedCount = uiState.totalBlockedCount,
                        onSelectTab = viewModel::onTabSelected
                    )
                }

                item(key = "search_bar") {
                    val searchPlaceholder = if (uiState.selectedTab == StudyChannelTab.ALLOWED) {
                        "Search allowed channels..."
                    } else {
                        "Search blocked channels..."
                    }
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        placeholder = {
                            Text(
                                text = searchPlaceholder,
                                color = FocusColors.TextMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = FocusColors.TextSecondary
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.onSearchQueryChanged("") },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Clear,
                                        contentDescription = "Clear search",
                                        tint = FocusColors.TextSecondary
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = FocusShapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FocusColors.Primary,
                            unfocusedBorderColor = FocusColors.CardBorder,
                            focusedContainerColor = FocusColors.Surface,
                            unfocusedContainerColor = FocusColors.Surface
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("channel_search_input")
                    )
                }

                val displayed = uiState.displayedChannels
                if (displayed.isEmpty()) {
                    item(key = "empty_state") {
                        ChannelEmptyStateCard(
                            selectedTab = uiState.selectedTab,
                            searchQuery = uiState.searchQuery,
                            onBrowsePacksClick = { viewModel.showAddDialog() }
                        )
                    }
                } else {
                    items(displayed, key = { it.id }) { channel ->
                        SmartChannelItemCard(
                            channel = channel,
                            onToggleApproval = { viewModel.toggleApproval(channel) },
                            onDelete = { viewModel.deleteChannel(channel) }
                        )
                    }
                }
            }
        }
    }

    if (uiState.isAddDialogVisible) {
        YouTubeStudyChannelManagerSheet(
            onDismiss = { viewModel.dismissAddDialog() }
        )
    }
}

@Composable
private fun AutoPilotGuardHeroCard(
    allowedCount: Int,
    blockedCount: Int,
    savedPercent: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.large)
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        FocusColors.HeroGradientStart,
                        FocusColors.HeroGradientMid,
                        FocusColors.HeroGradientEnd
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.16f), FocusShapes.large)
            .padding(18.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4ADE80))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "AUTO-PILOT ACTIVE",
                        color = FocusColors.HeroOnColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.14f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFDE68A),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "0ms Memory",
                        color = FocusColors.HeroOnColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Watch lectures freely. Distractions stay out.",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = FocusColors.HeroOnColor,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "AI verifies unseen channels once and saves them locally—repeat visits use 0 AI credits. Shorts always stay blocked.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.HeroOnColorMuted.copy(alpha = 0.9f),
                        lineHeight = 17.sp
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeroStatPill(
                    label = "Allowed",
                    value = allowedCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                HeroStatPill(
                    label = "Blocked",
                    value = blockedCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                HeroStatPill(
                    label = "AI Saved",
                    value = "$savedPercent%",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HeroStatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(FocusShapes.medium)
            .background(Color.White.copy(alpha = 0.14f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), FocusShapes.medium)
            .padding(vertical = 10.dp, horizontal = 8.dp)
    ) {
        Text(
            text = value,
            color = FocusColors.HeroOnColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
        Text(
            text = label,
            color = FocusColors.HeroOnColorMuted.copy(alpha = 0.85f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun ChannelSegmentTabs(
    selectedTab: StudyChannelTab,
    allowedCount: Int,
    blockedCount: Int,
    onSelectTab: (StudyChannelTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.medium)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorder, FocusShapes.medium)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SegmentTabButton(
            label = "Allowed ($allowedCount)",
            icon = Icons.Rounded.CheckCircle,
            isSelected = selectedTab == StudyChannelTab.ALLOWED,
            selectedBg = FocusColors.EmeraldLight,
            selectedContent = FocusColors.EmeraldText,
            onClick = { onSelectTab(StudyChannelTab.ALLOWED) },
            modifier = Modifier
                .weight(1f)
                .testTag("tab_allowed_channels")
        )
        SegmentTabButton(
            label = "Blocked ($blockedCount)",
            icon = Icons.Rounded.Block,
            isSelected = selectedTab == StudyChannelTab.BLOCKED,
            selectedBg = FocusColors.BlockedLight,
            selectedContent = FocusColors.BlockedText,
            onClick = { onSelectTab(StudyChannelTab.BLOCKED) },
            modifier = Modifier
                .weight(1f)
                .testTag("tab_blocked_channels")
        )
    }
}

@Composable
private fun SegmentTabButton(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    selectedBg: Color,
    selectedContent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isSelected) selectedBg else Color.Transparent
    val contentColor = if (isSelected) selectedContent else FocusColors.TextSecondary

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SmartChannelItemCard(
    channel: StudyChannelEntity,
    onToggleApproval: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        color = FocusColors.Surface,
        shape = FocusShapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, FocusColors.CardBorder, FocusShapes.medium)
            .testTag("channel_card_${channel.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarModel = remember(channel.thumbnailUrl, channel.channelId) {
                ChannelLogoStorageManager.getEffectiveLogoUri(
                    context = context,
                    channelId = channel.channelId,
                    thumbnailUrl = channel.thumbnailUrl,
                    handle = if (channel.channelId.startsWith("@")) channel.channelId else ""
                )
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(
                        if (channel.isApproved) FocusColors.EmeraldLight
                        else FocusColors.BlockedLight
                    )
                    .border(1.dp, FocusColors.CardBorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (avatarModel.isNotBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(avatarModel)
                            .crossfade(true)
                            .build(),
                        contentDescription = channel.channelName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = channel.channelName.take(1).uppercase(Locale.ROOT),
                        color = if (channel.isApproved) FocusColors.EmeraldText else FocusColors.BlockedText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = channel.channelName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (channel.channelId.isNotBlank() &&
                    !channel.channelId.equals(channel.channelName, ignoreCase = true)
                ) {
                    Text(
                        text = channel.channelId,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (channel.verifiedByAi) {
                        ChannelBadgeChip(
                            text = "AI Auto",
                            icon = Icons.Rounded.AutoAwesome,
                            containerColor = FocusColors.PrimaryContainer,
                            contentColor = FocusColors.OnPrimaryContainer
                        )
                    } else {
                        ChannelBadgeChip(
                            text = "Manual",
                            icon = Icons.Rounded.Tune,
                            containerColor = FocusColors.SurfaceSubtle,
                            contentColor = FocusColors.TextSecondary
                        )
                    }

                    val categoryLabel = formatCategoryLabel(channel.category, channel.isApproved)
                    if (categoryLabel != null) {
                        ChannelBadgeChip(
                            text = categoryLabel,
                            icon = null,
                            containerColor = if (channel.isApproved) FocusColors.EmeraldLight else FocusColors.BlockedLight,
                            contentColor = if (channel.isApproved) FocusColors.EmeraldText else FocusColors.BlockedText
                        )
                    }
                }

                if (channel.aiReason.isNotBlank()) {
                    Text(
                        text = channel.aiReason,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextMuted,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            Switch(
                checked = channel.isApproved,
                onCheckedChange = { onToggleApproval() },
                colors = FocusColors.SwitchColors,
                modifier = Modifier.testTag("channel_switch_${channel.id}")
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete ${channel.channelName}",
                    tint = FocusColors.TextMuted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ChannelBadgeChip(
    text: String,
    icon: ImageVector?,
    containerColor: Color,
    contentColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            color = contentColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun ChannelEmptyStateCard(
    selectedTab: StudyChannelTab,
    searchQuery: String,
    onBrowsePacksClick: () -> Unit
) {
    Surface(
        color = FocusColors.Surface,
        shape = FocusShapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, FocusColors.CardBorder, FocusShapes.large)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(
                        if (selectedTab == StudyChannelTab.ALLOWED) FocusColors.PrimaryContainer
                        else FocusColors.SurfaceSubtle
                    )
            ) {
                Icon(
                    imageVector = if (selectedTab == StudyChannelTab.ALLOWED) {
                        Icons.Rounded.SmartDisplay
                    } else {
                        Icons.Rounded.Shield
                    },
                    contentDescription = null,
                    tint = if (selectedTab == StudyChannelTab.ALLOWED) FocusColors.Primary else FocusColors.TextSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            val titleText = when {
                searchQuery.isNotBlank() -> "No channels match \"$searchQuery\""
                selectedTab == StudyChannelTab.ALLOWED -> "No educational channels learned yet"
                else -> "No blocked entertainment channels yet"
            }
            val bodyText = when {
                searchQuery.isNotBlank() -> "Try a different channel name or @handle, or tap + to add a new study channel."
                selectedTab == StudyChannelTab.ALLOWED -> "Start studying on YouTube—AI auto-learns study channels on your first visit—or add channels manually below."
                else -> "When AI blocks an entertainment, gaming, or vlog channel during a focus session, it appears here automatically."
            }

            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = FocusColors.TextPrimary,
                    fontWeight = FontWeight.Bold
                ),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = FocusColors.TextSecondary,
                    lineHeight = 18.sp
                ),
                textAlign = TextAlign.Center
            )

            if (selectedTab == StudyChannelTab.ALLOWED && searchQuery.isBlank()) {
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onBrowsePacksClick,
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FocusColors.Primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Browse Study Packs",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private fun formatCategoryLabel(rawCategory: String, isApproved: Boolean): String? {
    val clean = rawCategory.trim().uppercase(Locale.ROOT)
    if (clean.isBlank()) return null
    return when (clean) {
        "STUDY", "SUBJECT_MATCH", "EDUCATIONAL" -> "Educational"
        "ENTERTAINMENT" -> "Entertainment"
        "GAMING" -> "Gaming"
        "MUSIC" -> "Music"
        "VLOG" -> "Vlog"
        "DISTRACTION" -> "Distraction"
        "CHANNEL_RULE" -> if (isApproved) "Educational" else "Entertainment"
        else -> clean.lowercase(Locale.ROOT)
            .split("_")
            .joinToString(" ") { part -> part.replaceFirstChar { it.uppercase() } }
    }
}
