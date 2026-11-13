package com.example.feature.channels

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import com.example.FocusShieldApp
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusSpacing
import com.example.core.design.FocusShapes
import com.example.core.design.LocalFocusMotion
import com.example.core.ui.FocusDestructiveDialog
import com.example.core.ui.FocusSearchField
import com.example.core.ui.FocusStatusMessage
import com.example.core.design.LocalFocusColors
import com.example.core.util.ChannelLogoStorageManager
import com.example.core.util.YouTubeChannelSearchEngine
import com.example.core.util.YouTubeChannelSearchResult
import com.example.data.local.entity.StudyChannelEntity
import kotlinx.coroutines.CancellationException
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
    val handleText = if (channelId.startsWith("@")) channelId else "@$channelId"
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

    val sheetBg = FocusColors.OverlayBackground
    val textPrimary = FocusColors.OverlayTextPrimary

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
        containerColor = sheetBg,
        contentColor = textPrimary,
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

    val sheetBg = FocusColors.OverlayBackground
    val dragHandleColor = FocusColors.OverlayBorder
    val textPrimary = FocusColors.OverlayTextPrimary
    val textSecondary = FocusColors.OverlayTextSecondary
    val doneBtnBg = FocusColors.OverlayTextPrimary
    val doneBtnText = FocusColors.OverlayBackground

    val repo = try { FocusShieldApp.instance.studyChannelRepository } catch (e: Exception) { null }
    val approvedChannelsState = repo?.allChannels?.collectAsState(initial = emptyList())
    val existingChannels = (approvedChannelsState?.value ?: emptyList()).filter { it.isApproved }

    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<YouTubeChannelSearchResult>>(emptyList()) }
    var suggestedChannels by remember {
        mutableStateOf<List<YouTubeChannelSearchResult>>(YouTubeChannelSearchEngine.CURATED_CATALOG.take(5))
    }
    var isSearching by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var feedbackIsError by remember { mutableStateOf(false) }
    var addingChannels by remember { mutableStateOf(emptySet<String>()) }
    val context = LocalContext.current
    val listState = rememberLazyListState()

    fun report(message: String, isError: Boolean = false) {
        feedbackIsError = isError
        feedback = message
    }

    fun addChannel(name: String, handle: String, avatar: String = "", clearSearch: Boolean = false) {
        if (handle in addingChannels) return
        addingChannels = addingChannels + handle
        coroutineScope.launch {
            try {
                val channelRepo = repo
                if (channelRepo == null) {
                    report(context.getString(R.string.channel_repository_error), true)
                    return@launch
                }
                val result = channelRepo.addChannel(name, handle, isApproved = true, thumbnailUrl = avatar)
                if (result.isSuccess) {
                    report(context.getString(R.string.channel_added_message, name))
                    if (clearSearch) searchQuery = ""
                    focusManager.clearFocus()
                    keyboardController?.hide()
                } else {
                    report(context.getString(R.string.channel_add_error), true)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.e("StudyChannels", "Unable to add study channel", error)
                report(context.getString(R.string.channel_add_error), true)
            } finally {
                addingChannels = addingChannels - handle
            }
        }
    }

    fun removeChannel(channel: StudyChannelEntity) {
        coroutineScope.launch {
            try {
                val channelRepo = repo ?: error("Study channel repository unavailable")
                channelRepo.removeChannel(channel.id)
                report(context.getString(R.string.channel_removed_message, channel.channelName))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.e("StudyChannels", "Unable to remove study channel", error)
                report(context.getString(R.string.channel_remove_error), true)
            }
        }
    }

    LaunchedEffect(feedback) {
        if (feedback != null) listState.scrollToItem(1)
    }

    // Fetch live metadata (subscribers, logo, handle) directly from the internet on sheet open
    LaunchedEffect(Unit) {
        try {
            val live = YouTubeChannelSearchEngine.fetchSuggestedChannelsFromInternet()
            if (live.isNotEmpty()) suggestedChannels = live
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("StudyChannels", "Using local channel suggestions", error)
        }
        if (repo == null) report(context.getString(R.string.channel_repository_error), true)
    }

    LaunchedEffect(searchQuery) {
        val trimmed = searchQuery.trim()
        if (trimmed.isEmpty()) {
            isSearching = false
            searchResults = emptyList()
            return@LaunchedEffect
        }

        isSearching = true
        searchResults = YouTubeChannelSearchEngine.searchLocalCatalog(trimmed)
        try {
            // Debounce for live network search
            delay(300)
            val networkResults = YouTubeChannelSearchEngine.searchChannels(trimmed, "All")
            if (networkResults.isNotEmpty()) {
                searchResults = networkResults
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            android.util.Log.w("StudyChannels", "Unable to search channels", error)
            report(context.getString(R.string.channel_search_error), true)
        } finally {
            if (searchQuery.trim() == trimmed) isSearching = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(sheetBg)
            .testTag("youtube_channel_manager_sheet")
    ) {
        // Drag handle matching screenshot
        Box(
            modifier = Modifier
                .padding(top = 14.dp, bottom = 18.dp)
                .width(44.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(dragHandleColor)
                .align(Alignment.CenterHorizontally)
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.base),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Main title
            item {
                Text(
                    text = stringResource(R.string.channel_sheet_title),
                    color = textPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(bottom = FocusSpacing.base).semantics { heading() }
                )
            }

            item {
                FocusStatusMessage(feedback, isError = feedbackIsError, onDismiss = { feedback = null })
            }

            // Search bar
            item {
                FocusSearchField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = stringResource(R.string.channel_search_label),
                    placeholder = stringResource(R.string.channel_search_example),
                    isSearching = isSearching,
                    onSearch = { focusManager.clearFocus(); keyboardController?.hide() },
                    modifier = Modifier.testTag("youtube_live_search_field")
                )
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
                            text = stringResource(R.string.channel_list_title),
                            color = textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).semantics { heading() }
                        )
                        if (existingChannels.isNotEmpty()) {
                            TextButton(onClick = { showDeleteAllDialog = true }) {
                                Text(stringResource(R.string.action_delete_all), color = FocusColors.BlockedText)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                if (existingChannels.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.channel_list_empty),
                            color = textSecondary,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                } else {
                    items(existingChannels, key = { it.id }) { channel ->
                        ApprovedChannelItemRow(
                            channel = channel,
                            onDelete = { removeChannel(channel) },
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }

                // "Suggested for you" Header
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.channel_suggested_title),
                        color = textPrimary,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(bottom = 14.dp).semantics { heading() }
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
                        isAdding = channelResult.handle.ifBlank { channelResult.channelId } in addingChannels,
                        onAdd = { addChannel(channelResult.channelName, channelResult.handle.ifBlank { channelResult.channelId }, channelResult.avatarUrl) },
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
                            text = stringResource(R.string.channel_results_title, searchQuery),
                            color = textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 14.dp).semantics { heading() }
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
                                    color = textPrimary,
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 2.5.dp
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.channel_searching),
                                    color = textSecondary,
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
                                    text = stringResource(R.string.channel_no_results, searchQuery),
                                    color = textSecondary,
                                    fontSize = 14.sp
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Text(stringResource(R.string.channel_unverified_hint), color = textSecondary, style = MaterialTheme.typography.bodySmall)
                                Button(
                                    onClick = {
                                        val entered = searchQuery.trim()
                                        val exactHandle = entered.startsWith("@") && entered.length > 1 && entered.none { it.isWhitespace() || it == '/' }
                                        val channelUrl = runCatching {
                                            val uri = java.net.URI(entered)
                                            uri.scheme in listOf("http", "https") && uri.host in listOf("youtube.com", "www.youtube.com", "m.youtube.com") &&
                                                listOf("/@", "/channel/", "/c/", "/user/").any { uri.path?.startsWith(it) == true }
                                        }.getOrDefault(false)
                                        if (exactHandle || channelUrl) {
                                            addChannel(entered.removePrefix("@"), entered, clearSearch = true)
                                        } else {
                                            report(context.getString(R.string.channel_handle_error), true)
                                        }
                                    },
                                    enabled = searchQuery.trim() !in addingChannels,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.channel_add_unverified),
                                        style = MaterialTheme.typography.labelLarge
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
                            isAdding = channelResult.handle.ifBlank { channelResult.channelId } in addingChannels,
                            onAdd = { addChannel(channelResult.channelName, channelResult.handle.ifBlank { channelResult.channelId }, channelResult.avatarUrl) },
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
            }
        }

        // Bottom "Done" button matching screenshot
        Surface(
            color = sheetBg,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = FocusSpacing.screenHorizontal)
                .padding(top = FocusSpacing.sm, bottom = FocusSpacing.lg)
        ) {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = doneBtnBg,
                    contentColor = doneBtnText
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp)
                    .testTag("done_channels_button")
            ) {
                Text(
                    text = stringResource(R.string.action_done),
                    color = doneBtnText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp
                )
            }
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text(stringResource(R.string.delete_all_channels_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.channel_clear_body,
                        existingChannels.size,
                        existingChannels.size
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteAllDialog = false
                        coroutineScope.launch {
                            try {
                                val channelRepo = repo ?: error("Study channel repository unavailable")
                                existingChannels.forEach { channelRepo.removeChannel(it.id) }
                                report(context.getString(R.string.channel_cleared_message))
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                android.util.Log.e("StudyChannels", "Unable to remove study channels", error)
                                report(context.getString(R.string.channel_clear_error), true)
                            }
                        }
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_delete_all),
                        color = FocusColors.BlockedText,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
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
    modifier: Modifier = Modifier,
    isAdding: Boolean = false
) {
    val cardBg = FocusColors.OverlaySurface
    val dashedBorderColor = FocusColors.OverlayBorder
    val textPrimary = FocusColors.OverlayTextPrimary
    val textSecondary = FocusColors.OverlayTextSecondary
    val addLabel = stringResource(R.string.channel_add_named, channel.channelName)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .dashedBorder(
                strokeWidth = 1.2.dp,
                color = dashedBorderColor,
                cornerRadius = 16.dp,
                dashLength = 7.dp,
                gapLength = 5.dp
            )
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)) {
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
                    color = textPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = channel.subscriberCount.ifBlank { channel.handle },
                    color = textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

        }

            if (!isApproved) {
                Button(
                    onClick = onAdd,
                    enabled = !isAdding,
                    shape = FocusShapes.pill,
                    modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)
                        .semantics { contentDescription = addLabel }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(if (isAdding) R.string.channel_adding else R.string.channel_add_action),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .heightIn(min = 36.dp)
                        .clip(RoundedCornerShape(50))
                        .background(FocusColors.EmeraldLight)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .align(Alignment.End),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.channel_added_status),
                            color = FocusColors.EmeraldText,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = FocusColors.EmeraldText,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Approved channel item card with solid surface background matching theme
 */
@Composable
fun ApprovedChannelItemRow(
    channel: StudyChannelEntity,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val rowBg = FocusColors.OverlaySurface
    val rowBorder = FocusColors.OverlayBorder
    val textPrimary = FocusColors.OverlayTextPrimary
    val textSecondary = FocusColors.OverlayTextSecondary

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = rowBg,
        border = BorderStroke(1.dp, rowBorder),
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
                    color = textPrimary,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = getChannelSubscriberDisplay(channel.channelName, channel.channelId),
                    color = textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            IconButton(
                onClick = { showDeleteConfirmation = true },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.channel_remove_action, channel.channelName),
                    tint = FocusColors.BlockedText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
    if (showDeleteConfirmation) {
        FocusDestructiveDialog(
            title = stringResource(R.string.channel_remove_title, channel.channelName),
            message = stringResource(R.string.channel_remove_body),
            confirmLabel = stringResource(R.string.channel_remove_confirm),
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirmation = false }
        )
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
    val isDark = LocalFocusColors.current.isDark
    val motion = LocalFocusMotion.current
    val normalizedName = name.trim().lowercase()

    val (bgColor, monogram, isSpecialLogo) = when {
        normalizedName.contains("competition wallah") -> Triple(if (isDark) Color(0xFF111111) else Color(0xFF0F172A), "CW", true)
        normalizedName.contains("jee wallah") -> Triple(if (isDark) Color(0xFF111111) else Color(0xFF0F172A), "JW", true)
        normalizedName.contains("physics wallah") || handle.equals("@PhysicsWallah", true) -> Triple(if (isDark) Color(0xFF111111) else Color(0xFF0F172A), "PW", true)
        normalizedName.contains("magnet brains") -> Triple(Color(0xFF166534), "mb", false)
        normalizedName.contains("exphub") || normalizedName.contains("prashant") -> Triple(Color(0xFFD97706), "EH", false)
        normalizedName.contains("khan academy") -> Triple(Color(0xFF0D9488), "KA", false)
        normalizedName.contains("unacademy") -> Triple(Color(0xFF059669), "U", false)
        normalizedName.contains("vedantu") -> Triple(Color(0xFFEA580C), "V", false)
        else -> Triple(if (isDark) Color(0xFF262628) else Color(0xFFE2E8F0), name.take(1).uppercase(), false)
    }

    val textColor = when {
        isSpecialLogo -> Color.White
        normalizedName.contains("magnet brains") ||
            normalizedName.contains("exphub") ||
            normalizedName.contains("prashant") ||
            normalizedName.contains("khan academy") ||
            normalizedName.contains("unacademy") ||
            normalizedName.contains("vedantu") -> Color.White
        else -> if (isDark) Color.White else Color(0xFF0F172A)
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
                color = textColor,
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
            .background(bgColor)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center
    ) {
        if (effectiveUri.isNotBlank()) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(effectiveUri)
                    .crossfade(!motion.reduceMotion)
                    .build(),
                contentDescription = null,
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
