package com.example.feature.channels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.FocusShieldApp
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.util.YouTubeChannelSearchEngine
import com.example.core.util.YouTubeChannelSearchResult
import com.example.data.local.entity.StudyChannelEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeStudyChannelManagerSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val handleDismiss: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onDismiss()
    }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    ModalBottomSheet(
        onDismissRequest = handleDismiss,
        sheetState = sheetState,
        containerColor = if (com.example.core.design.LocalFocusColors.current.isDark) Color.Black else Color.White,
        contentColor = if (com.example.core.design.LocalFocusColors.current.isDark) Color.White else Color(0xFF111827),
        shape = RoundedCornerShape(0.dp),
        tonalElevation = 0.dp,
        dragHandle = null,
        modifier = modifier
    ) {
        YouTubeStudyChannelContent(
            onDismiss = handleDismiss
        )
    }
}

@Composable
fun YouTubeStudyChannelContent(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val themePalette = com.example.core.design.LocalFocusColors.current
    val sheetBackground = if (themePalette.isDark) Color.Black else Color.White
    val sheetSurface = if (themePalette.isDark) FocusColors.SurfaceVariant else Color(0xFFF7F7F9)
    val sheetBorder = if (themePalette.isDark) FocusColors.CardBorder else Color(0xFFE2E5EA)
    val sheetPrimaryText = if (themePalette.isDark) Color.White else Color(0xFF111827)
    val sheetSecondaryText = if (themePalette.isDark) Color(0xFFB0B3BA) else Color(0xFF6B7280)
    val sheetMutedText = if (themePalette.isDark) Color(0xFF8B8F98) else Color(0xFF737780)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val repo = try { FocusShieldApp.instance.studyChannelRepository } catch (e: Exception) { null }
    val approvedChannelsState = repo?.allChannels?.collectAsState(initial = emptyList())
    val existingChannels = approvedChannelsState?.value ?: emptyList()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var searchResults by remember { mutableStateOf<List<YouTubeChannelSearchResult>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    val categories = listOf(
        "All", "JEE / NEET", "Coding", "Math & Science", "Academics",
        "UPSC & Govt", "Medical", "Self-Improvement"
    )

    // Keep the existing search behavior unchanged; only the presentation is changed.
    LaunchedEffect(searchQuery, selectedCategory) {
        searchJob?.cancel()
        searchJob = coroutineScope.launch {
            if (searchQuery.isNotBlank()) {
                isSearching = true
                delay(250)
            } else {
                isSearching = false
            }
            searchResults = YouTubeChannelSearchEngine.searchChannels(searchQuery, selectedCategory)
            isSearching = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(sheetBackground)
            .testTag("youtube_channel_manager_sheet")
    ) {
        // Reference-style top handle.
        Box(
            modifier = Modifier
                .padding(top = 104.dp, bottom = 64.dp)
                .width(56.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(sheetMutedText.copy(alpha = 0.85f))
                .align(Alignment.CenterHorizontally)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 42.dp, end = 42.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                Text(
                    text = "Add channels you study from and\nblock the rest",
                    color = sheetPrimaryText,
                    fontSize = 31.sp,
                    lineHeight = 38.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 58.dp)
                )
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search channel to add",
                            color = sheetMutedText,
                            fontSize = 20.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = FocusColors.TextMuted,
                            modifier = Modifier.size(30.dp)
                        )
                    },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = FocusColors.Primary,
                                strokeWidth = 2.dp
                            )
                        }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            focusManager.clearFocus()
                            keyboardController?.hide()
                        }
                    ),
                    shape = RoundedCornerShape(15.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = sheetSurface,
                        unfocusedContainerColor = sheetSurface,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = sheetPrimaryText,
                        unfocusedTextColor = sheetPrimaryText,
                        cursorColor = FocusColors.Primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(98.dp)
                        .testTag("youtube_live_search_field")
                )
            }

            item { Spacer(modifier = Modifier.height(52.dp)) }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Your Study Mode Channels",
                        color = sheetPrimaryText,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Delete all",
                        color = FocusColors.CoralWarning,
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
            }

            if (existingChannels.isEmpty()) {
                item {
                    Text(
                        text = "No study channels added yet",
                        color = sheetSecondaryText,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
            } else {
                items(existingChannels, key = { it.id }) { channel ->
                    ApprovedChannelItemRow(
                        channel = channel,
                        onToggle = { isChecked ->
                            coroutineScope.launch { repo?.toggleChannelApproval(channel.id, isChecked) }
                        },
                        onDelete = {
                            coroutineScope.launch { repo?.removeChannel(channel.id) }
                        },
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(34.dp))
                Text(
                    text = "Suggested for you",
                    color = sheetPrimaryText,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 26.dp)
                )
            }

            items(searchResults, key = { it.channelId + "_" + it.handle }) { channelResult ->
                val isAlreadyApproved = existingChannels.any {
                    it.channelId.equals(channelResult.handle, ignoreCase = true) ||
                        it.channelId.equals(channelResult.channelId, ignoreCase = true) ||
                        it.channelName.equals(channelResult.channelName, ignoreCase = true)
                }

                YouTubeChannelCard(
                    channel = channelResult,
                    isApproved = isAlreadyApproved,
                    onAdd = {
                        coroutineScope.launch {
                            val res = repo?.addChannel(
                                channelName = channelResult.channelName,
                                channelUrl = channelResult.handle,
                                isApproved = true,
                                thumbnailUrl = channelResult.avatarUrl
                            )
                            // Existing repository/search behavior remains unchanged.
                            if (res?.isSuccess == true) {
                                focusManager.clearFocus()
                            }
                        }
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }

        Surface(
            color = sheetBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 42.dp, end = 42.dp, bottom = 24.dp)
        ) {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (themePalette.isDark) Color.White else Color(0xFF111827),
                    contentColor = if (themePalette.isDark) Color.Black else Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(108.dp)
                    .testTag("done_channels_button")
            ) {
                Text(
                    text = "Done",
                    color = if (themePalette.isDark) Color.Black else Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 28.sp
                )
            }
        }
    }
}

@Composable
fun YouTubeChannelCard(
    channel: YouTubeChannelSearchResult,
    isApproved: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val themePalette = com.example.core.design.LocalFocusColors.current
    val sheetBorder = if (themePalette.isDark) FocusColors.CardBorder else Color(0xFFE2E5EA)
    val sheetSurface = if (themePalette.isDark) FocusColors.SurfaceVariant else Color(0xFFF7F7F9)
    val sheetPrimaryText = if (themePalette.isDark) Color.White else Color(0xFF111827)
    val sheetSecondaryText = if (themePalette.isDark) Color(0xFFB0B3BA) else Color(0xFF6B7280)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(134.dp)
            .drawBehind {
                val strokeWidth = 1.5.dp.toPx()
                val radius = 16.dp.toPx()
                drawRoundRect(
                    color = sheetBorder,
                    style = Stroke(
                        width = strokeWidth,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(12.dp.toPx(), 10.dp.toPx()),
                            0f
                        )
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius)
                )
            }
            .padding(horizontal = 34.dp, vertical = 20.dp)
    ) {
        val avatarModel = remember(channel.avatarUrl, channel.handle, channel.channelId) {
            com.example.core.util.ChannelLogoStorageManager.getEffectiveLogoUri(
                context = context,
                channelId = channel.channelId.ifBlank { channel.handle },
                thumbnailUrl = channel.avatarUrl,
                handle = channel.handle
            )
        }

        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(sheetSurface),
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
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = channel.channelName.take(1).uppercase(),
                        color = sheetPrimaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(28.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.channelName,
                    color = sheetPrimaryText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (channel.subscriberCount.isNotBlank()) {
                    Text(
                        text = channel.subscriberCount,
                        color = sheetSecondaryText,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = channel.handle,
                        color = sheetSecondaryText,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = onAdd,
                enabled = !isApproved,
                shape = RoundedCornerShape(50.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (themePalette.isDark) Color.White else Color(0xFF111827),
                    contentColor = if (themePalette.isDark) Color.Black else Color.White,
                    disabledContainerColor = if (themePalette.isDark) FocusColors.SurfaceSubtle else Color(0xFFEDEEF1),
                    disabledContentColor = FocusColors.TextSecondary
                ),
                contentPadding = PaddingValues(horizontal = 24.dp),
                modifier = Modifier
                    .width(146.dp)
                    .height(64.dp)
            ) {
                Text(
                    text = if (isApproved) "Added" else "Add",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium
                )
                if (!isApproved) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ApprovedChannelItemRow(
    channel: StudyChannelEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val themePalette = com.example.core.design.LocalFocusColors.current
    val sheetBackground = if (themePalette.isDark) Color.Black else Color.White
    val sheetSurface = if (themePalette.isDark) FocusColors.SurfaceVariant else Color(0xFFF7F7F9)
    val sheetPrimaryText = if (themePalette.isDark) Color.White else Color(0xFF111827)
    val sheetSecondaryText = if (themePalette.isDark) Color(0xFFB0B3BA) else Color(0xFF6B7280)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = sheetSurface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .padding(horizontal = 34.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(sheetBackground),
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
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = channel.channelName.take(1).uppercase(),
                        color = sheetPrimaryText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 25.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(28.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.channelName,
                    color = sheetPrimaryText,
                    fontSize = 28.sp,
                    lineHeight = 34.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = channel.channelId,
                    color = sheetSecondaryText,
                    fontSize = 19.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tint = FocusColors.TextMuted,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}

enum class ChannelTab {
    SEARCH_EXPLORE,
    APPROVED_WHITELIST
}
