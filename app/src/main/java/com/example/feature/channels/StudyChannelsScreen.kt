package com.example.feature.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.data.local.entity.StudyChannelEntity

import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

@Composable
fun StudyChannelsScreen(
    viewModel: StudyChannelsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
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
                        .padding(horizontal = FocusSpacing.md, vertical = FocusSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = FocusColors.TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Approved Channels",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary
                            )
                        )
                        Text(
                            text = "YouTube Study Mode",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(FocusColors.PrimaryLight.copy(alpha = 0.3f))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "${uiState.channels.count { it.isApproved }} Active",
                            color = FocusColors.Primary,
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
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.testTag("add_channel_fab")
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = FocusSpacing.screenHorizontal)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search approved channels...", color = FocusColors.TextMuted) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = FocusColors.TextSecondary
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
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

            Spacer(modifier = Modifier.height(14.dp))

            // Info Card explaining Study Mode
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FocusShapes.medium)
                    .background(Color(0xFFEDE9FE))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        tint = FocusColors.Primary,
                        modifier = Modifier
                            .size(20.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Focus Protection Rule",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.Primary
                            )
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Only approved channels play during Focus Sessions. YouTube Shorts are strictly blocked at all times.",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = Color(0xFF4C1D95),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Channels List or Empty State
            if (uiState.channels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Rounded.SmartDisplay,
                            contentDescription = null,
                            tint = FocusColors.TextMuted,
                            modifier = Modifier.size(54.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "No channels match \"${uiState.searchQuery}\"" else "No study channels added",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the + button to add educational YouTube channels",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary
                            )
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.channels, key = { it.id }) { channel ->
                        ChannelItemCard(
                            channel = channel,
                            onToggleApproval = { viewModel.toggleApproval(channel) },
                            onDelete = { viewModel.deleteChannel(channel) }
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(80.dp)) // Padding for FAB
                    }
                }
            }
        }
    }

    // Add Channel Bottom Sheet / Pop-up with in-app YouTube Search
    if (uiState.isAddDialogVisible) {
        YouTubeStudyChannelManagerSheet(
            onDismiss = { viewModel.dismissAddDialog() }
        )
    }
}

@Composable
private fun ChannelItemCard(
    channel: StudyChannelEntity,
    onToggleApproval: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = androidx.compose.ui.platform.LocalContext.current

            // Channel Avatar / Icon
            val avatarModel = remember(channel.thumbnailUrl, channel.channelId) {
                com.example.core.util.ChannelLogoStorageManager.getEffectiveLogoUri(
                    context = context,
                    channelId = channel.channelId,
                    thumbnailUrl = channel.thumbnailUrl,
                    handle = if (channel.channelId.startsWith("@")) channel.channelId else ""
                )
            }

            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (channel.isApproved) FocusColors.PrimaryLight.copy(alpha = 0.3f)
                        else FocusColors.CardBorder
                    )
                    .border(1.dp, FocusColors.CardBorderSubtle, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (avatarModel.isNotBlank()) {
                    AsyncImage(
                        model = coil.request.ImageRequest.Builder(context)
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
                        text = channel.channelName.take(1).uppercase(),
                        color = if (channel.isApproved) FocusColors.Primary else FocusColors.TextMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Channel Name and Handle
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.channelName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = 15.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = channel.channelId,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 12.sp
                    )
                )
            }

            // Switch
            Switch(
                checked = channel.isApproved,
                onCheckedChange = { onToggleApproval() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FocusColors.Primary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = FocusColors.CardBorder
                ),
                modifier = Modifier.testTag("channel_switch_${channel.id}")
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Delete action
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
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
