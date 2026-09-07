package com.example.feature.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.FocusShieldApp
import com.example.core.util.ChannelLogoStorageManager
import com.example.core.util.YouTubeChannelSearchEngine
import com.example.core.util.YouTubeChannelSearchResult
import com.example.data.local.entity.StudyChannelEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Draws a clean dashed border around a rounded rectangle to match the visual screenshot specification.
 */
fun Modifier.dashedBorder(
    strokeWidth: Dp = 1.2.dp,
    color: Color,
    cornerRadius: Dp = 16.dp,
    dashLength: Dp = 7.dp,
    gapLength: Dp = 5.dp
): Modifier = this.drawBehind {
    val widthPx = strokeWidth.toPx()
    val radiusPx = cornerRadius.toPx()
    val dashPx = dashLength.toPx()
    val gapPx = gapLength.toPx()

    val pathEffect = PathEffect.dashPathEffect(floatArrayOf(dashPx, gapPx), 0f)
    val stroke = Stroke(width = widthPx, pathEffect = pathEffect)
    drawRoundRect(
        color = color,
        topLeft = Offset(widthPx / 2f, widthPx / 2f),
        size = Size(size.width - widthPx, size.height - widthPx),
        cornerRadius = CornerRadius(radiusPx, radiusPx),
        style = stroke
    )
}

fun getChannelSubscriberDisplay(channelName: String, channelId: String): String {
    val curated = YouTubeChannelSearchEngine.CURATED_CATALOG.find {
        it.channelName.equals(channelName, ignoreCase = true) ||
            it.handle.equals(channelId, ignoreCase = true) ||
            it.channelId.equals(channelId, ignoreCase = true) ||
            (channelName.contains("Physics Wallah", ignoreCase = true) && it.channelName.contains("Physics Wallah", ignoreCase = true)) ||
            (channelName.contains("Competition Wallah", ignoreCase = true) && it.channelName.contains("Competition Wallah", ignoreCase = true)) ||
            (channelName.contains("JEE Wallah", ignoreCase = true) && it.channelName.contains("JEE Wallah", ignoreCase = true)) ||
            (channelName.contains("Magnet Brains", ignoreCase = true) && it.channelName.contains("Magnet Brains", ignoreCase = true)) ||
            (channelName.contains("ExpHub", ignoreCase = true) && it.channelName.contains("ExpHub", ignoreCase = true))
    }
    if (curated != null && curated.subscriberCount.isNotBlank()) {
        return curated.subscriberCount
    }
    return if (channelId.startsWith("@")) channelId else "@$channelId"
}

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
        containerColor = Color(0xFF121212),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
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
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val repo = try { FocusShieldApp.instance.studyChannelRepository } catch (e: Exception) { null }
    val approvedChannelsState = repo?.allChannels?.collectAsState(initial = emptyList())
    val existingChannels = approvedChannelsState?.value ?: emptyList()

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<YouTubeChannelSearchResult>>(emptyList()) }
    var suggestedChannels by remember {
        mutableStateOf<List<YouTubeChannelSearchResult>>(YouTubeChannelSearchEngine.CURATED_CATALOG.take(5))
    }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    // Fetch live metadata (subscribers, logo, handle) directly from the internet on sheet open
    LaunchedEffect(Unit) {
        val live = YouTubeChannelSearchEngine.fetchSuggestedChannelsFromInternet()
        if (live.isNotEmpty()) {
            suggestedChannels = live
        }
    }

    LaunchedEffect(searchQuery) {
        searchJob?.cancel()
        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) {
            isSearching = false
            searchResults = emptyList()
            return@LaunchedEffect
        }

        searchJob = coroutineScope.launch {
            isSearching = true
            // Instant local catalog response
            val localInstant = YouTubeChannelSearchEngine.searchLocalCatalog(trimmed)
            if (localInstant.isNotEmpty()) {
                searchResults = localInstant
            }
            // Debounce for live network search
            delay(300)
            val networkResults = YouTubeChannelSearchEngine.searchChannels(trimmed, "All")
            if (networkResults.isNotEmpty()) {
                searchResults = networkResults
            }
            isSearching = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(Color(0xFF121212))
            .testTag("youtube_channel_manager_sheet")
    ) {
        // Drag handle matching screenshot
        Box(
            modifier = Modifier
                .padding(top = 14.dp, bottom = 18.dp)
                .width(44.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF4E4E52))
                .align(Alignment.CenterHorizontally)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Main title matching screenshot
            item {
                Text(
                    text = "Add channels you study from and\nblock the rest",
                    color = Color.White,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }

            // Search bar matching screenshot
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E1E20))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = Color(0xFF7E7E82),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search channel to add",
                                    color = Color(0xFF7E7E82),
                                    fontSize = 15.sp
                                )
                            }
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 15.sp
                                ),
                                cursorBrush = SolidColor(Color.White),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("youtube_live_search_field")
                            )
                        }
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear",
                                    tint = Color(0xFF7E7E82),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(28.dp)) }

            if (searchQuery.isBlank()) {
                // "Your Study Mode Channels" Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Your Study Mode Channels",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (existingChannels.isNotEmpty()) {
                            Text(
                                text = "Delete all",
                                color = Color(0xFFFF6D2C),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.clickable {
                                    coroutineScope.launch {
                                        repo?.clearAllChannels()
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (existingChannels.isEmpty()) {
                    item {
                        Text(
                            text = "No study channels added yet",
                            color = Color(0xFF7E7E82),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                } else {
                    items(existingChannels, key = { it.id }) { channel ->
                        ApprovedChannelItemRow(
                            channel = channel,
                            onDelete = {
                                coroutineScope.launch { repo?.removeChannel(channel.id) }
                            },
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }

                // "Suggested for you" Header
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Suggested for you",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 14.dp)
                    )
                }

                items(suggestedChannels, key = { "suggested_" + it.channelId + "_" + it.handle }) { channelResult ->
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
                                if (res?.isSuccess == true) {
                                    focusManager.clearFocus()
                                }
                            }
                        },
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
            } else {
                // Actively searching
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Results for \"$searchQuery\"",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )
                    }
                }

                if (isSearching && searchResults.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Searching YouTube channels...",
                                    color = Color(0xFF8E8E93),
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                } else if (!isSearching && searchResults.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No channels found matching \"$searchQuery\"",
                                    color = Color(0xFF8E8E93),
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            val handle = if (searchQuery.startsWith("@")) searchQuery.trim() else "@${searchQuery.trim().replace(" ", "")}"
                                            repo?.addChannel(
                                                channelName = searchQuery.trim().removePrefix("@"),
                                                channelUrl = handle,
                                                isApproved = true,
                                                thumbnailUrl = ""
                                            )
                                            searchQuery = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF242426),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "Whitelist \"$searchQuery\" anyway",
                                        fontSize = 13.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(searchResults, key = { "search_" + it.channelId + "_" + it.handle }) { channelResult ->
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
                                    if (res?.isSuccess == true) {
                                        focusManager.clearFocus()
                                    }
                                }
                            },
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            }
        }

        // Bottom "Done" button matching screenshot
        Surface(
            color = Color(0xFF121212),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
        ) {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("done_channels_button")
            ) {
                Text(
                    text = "Done",
                    color = Color.Black,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

/**
 * Suggested channel item card with dashed border matching screenshot
 */
@Composable
fun YouTubeChannelCard(
    channel: YouTubeChannelSearchResult,
    isApproved: Boolean,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .dashedBorder(
                strokeWidth = 1.2.dp,
                color = Color(0xFF333336),
                cornerRadius = 16.dp,
                dashLength = 7.dp,
                gapLength = 5.dp
            )
            .clip(RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudyChannelAvatar(
                name = channel.channelName,
                handle = channel.handle,
                thumbnailUrl = channel.avatarUrl,
                size = 50.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.channelName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = channel.subscriberCount.ifBlank { channel.handle },
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            if (!isApproved) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onAdd
                        )
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Add",
                            color = Color.Black,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Add",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF242426))
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Added",
                            color = Color(0xFF9E9EA3),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "Added",
                            tint = Color(0xFF9E9EA3),
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Approved channel item card with solid dark surface background matching screenshot
 */
@Composable
fun ApprovedChannelItemRow(
    channel: StudyChannelEntity,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1C1C1E),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudyChannelAvatar(
                name = channel.channelName,
                handle = channel.channelId,
                thumbnailUrl = channel.thumbnailUrl,
                size = 50.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.channelName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = getChannelSubscriberDisplay(channel.channelName, channel.channelId),
                    color = Color(0xFF8E8E93),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFF7E7E82),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Dedicated visual channel avatar supporting PW/CW/JW/mb badges and network fallback
 */
@Composable
fun StudyChannelAvatar(
    name: String,
    handle: String,
    thumbnailUrl: String,
    size: Dp = 50.dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val normalizedName = name.trim().lowercase()

    val (bgColor, monogram, isSpecialLogo) = when {
        normalizedName.contains("competition wallah") -> Triple(Color(0xFF111111), "CW", true)
        normalizedName.contains("jee wallah") -> Triple(Color(0xFF111111), "JW", true)
        normalizedName.contains("physics wallah") || handle.equals("@PhysicsWallah", true) -> Triple(Color(0xFF111111), "PW", true)
        normalizedName.contains("magnet brains") -> Triple(Color(0xFF166534), "mb", false)
        normalizedName.contains("exphub") || normalizedName.contains("prashant") -> Triple(Color(0xFFD97706), "EH", false)
        normalizedName.contains("khan academy") -> Triple(Color(0xFF0D9488), "KA", false)
        normalizedName.contains("unacademy") -> Triple(Color(0xFF059669), "U", false)
        normalizedName.contains("vedantu") -> Triple(Color(0xFFEA580C), "V", false)
        else -> Triple(Color(0xFF262628), name.take(1).uppercase(), false)
    }

    val fallbackContent: @Composable () -> Unit = {
        if (isSpecialLogo) {
            Box(
                modifier = Modifier
                    .size(size - 8.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = monogram,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (size.value * 0.35f).sp
                )
            }
        } else {
            Text(
                text = monogram,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.38f).sp
            )
        }
    }

    val effectiveUri = remember(thumbnailUrl, handle, name) {
        val raw = ChannelLogoStorageManager.getEffectiveLogoUri(
            context = context,
            channelId = handle.ifBlank { name },
            thumbnailUrl = thumbnailUrl,
            handle = handle
        )
        if (raw.startsWith("//")) "https:$raw" else raw
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        if (effectiveUri.isNotBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(effectiveUri)
                    .crossfade(true)
                    .build(),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { fallbackContent() },
                error = { fallbackContent() }
            )
        } else {
            fallbackContent()
        }
    }
}
