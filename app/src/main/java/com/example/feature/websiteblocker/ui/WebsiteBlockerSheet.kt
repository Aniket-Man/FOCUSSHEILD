package com.example.feature.websiteblocker.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PublicOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.core.design.FocusColors
import com.example.R
import com.example.core.design.FocusSpacing
import com.example.core.design.LocalFocusMotion
import com.example.core.design.FocusShapes
import com.example.core.design.LocalFocusColors
import com.example.data.local.entity.BlockedWebsiteEntity
import com.example.core.ui.FocusDestructiveDialog
import com.example.core.ui.FocusStatusMessage
import com.example.core.ui.FocusSwitchRow

/**
 * Clean dashed border around rounded rectangle matching the YouTube Channel popup theme.
 */
private fun Modifier.sheetDashedBorder(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebsiteBlockerSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WebsiteBlockerViewModel = viewModel()
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

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

    androidx.activity.compose.BackHandler(enabled = true) {
        handleDismiss()
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
        WebsiteBlockerSheetContent(
            onDismiss = handleDismiss,
            viewModel = viewModel
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WebsiteBlockerSheetContent(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WebsiteBlockerViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val urlFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val sheetBg = FocusColors.OverlayBackground
    val textPrimary = FocusColors.OverlayTextPrimary
    val textSecondary = FocusColors.OverlayTextSecondary
    val dragHandleColor = FocusColors.OverlayBorder
    val cardBg = FocusColors.OverlaySurface
    val cardBorder = FocusColors.OverlayBorder
    val doneBtnBg = FocusColors.OverlayTextPrimary
    val doneBtnText = FocusColors.OverlayBackground

    LaunchedEffect(state.errorMessage) {
        if (state.errorMessage != null) listState.scrollToItem(1)
    }
    LaunchedEffect(state.successMessage) {
        if (state.successMessage != null && state.addWebsiteInput.isEmpty()) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val samplePills = listOf(
        "facebook.com", "instagram.com", "reddit.com",
        "twitter.com", "tiktok.com", "gambling.com"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(sheetBg)
            .testTag("website_blocker_sheet")
    ) {
        // Drag handle matching YouTube channels popup
        Box(
            modifier = Modifier
                .padding(top = 14.dp, bottom = 16.dp)
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
            // Main title matching YouTube popup typography
            item {
                Text(
                    text = stringResource(R.string.website_sheet_title),
                    color = textPrimary,
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.padding(bottom = FocusSpacing.base).semantics { heading() }
                )
            }

            item {
                FocusStatusMessage(
                    message = state.errorMessage ?: state.successMessage,
                    isError = state.errorMessage != null,
                    onDismiss = viewModel::clearMessages,
                    modifier = Modifier.padding(bottom = if (state.errorMessage != null || state.successMessage != null) FocusSpacing.base else 0.dp)
                )
            }

            // Permission status card (Accessibility)
            item {
                if (!state.isAccessibilityEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp)
                            .sheetDashedBorder(
                                color = FocusColors.CoralText,
                                cornerRadius = 16.dp
                            )
                            .background(
                                color = FocusColors.CoralLight,
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(16.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(FocusColors.CoralWarning.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Security,
                                        contentDescription = null,
                                        tint = FocusColors.CoralText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.website_permission_title),
                                        color = textPrimary,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }

                            Text(
                                text = stringResource(R.string.website_permission_body),
                                color = textSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )

                            OutlinedButton(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        android.util.Log.w("WebsiteBlocker", "Unable to open accessibility settings", e)
                                        viewModel.reportPermissionLaunchFailure()
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = textPrimary),
                                border = BorderStroke(1.dp, FocusColors.ControlBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("website_blocker_grant_permission_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = FocusColors.CoralText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.website_permission_grant),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp)
                            .sheetDashedBorder(
                                color = FocusColors.EmeraldText,
                                cornerRadius = 14.dp
                            )
                            .background(
                                color = FocusColors.EmeraldLight,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = FocusColors.EmeraldText,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = stringResource(R.string.website_permission_active),
                                modifier = Modifier.weight(1f),
                                color = FocusColors.EmeraldText,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Adult Site Blocking (18+ Shield) On/Off Toggle Card
            item {
                val isAdultOn = state.isAdultBlockingEnabled
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = FocusSpacing.base)
                        .testTag("adult_site_blocking_card")
                        .sheetDashedBorder(
                            color = if (isAdultOn) FocusColors.Primary else cardBorder,
                            cornerRadius = 16.dp
                        ),
                    shape = FocusShapes.medium,
                    color = cardBg
                ) {
                    FocusSwitchRow(
                        title = stringResource(R.string.website_adult_label),
                        checked = isAdultOn,
                        onCheckedChange = viewModel::toggleAdultBlocking,
                        description = stringResource(if (isAdultOn) R.string.website_adult_on else R.string.website_adult_off),
                        modifier = Modifier.testTag("adult_site_blocking_toggle")
                    )
                }
            }

            item {
                Surface(color = cardBg, shape = FocusShapes.medium, modifier = Modifier.padding(bottom = FocusSpacing.base)) {
                    FocusSwitchRow(
                        title = stringResource(R.string.website_manual_label),
                        description = stringResource(R.string.website_manual_body),
                        checked = state.isManualBlockingEnabled,
                        onCheckedChange = viewModel::toggleManualBlocking,
                        modifier = Modifier.testTag("manual_website_blocking_toggle")
                    )
                }
            }

            // Custom Blocklist & Quick Add
                // Input box styled like YouTube channels search bar
                item {
                    OutlinedTextField(
                        value = state.addWebsiteInput,
                        onValueChange = viewModel::updateAddWebsiteInput,
                        label = { Text(stringResource(R.string.website_url_label)) },
                        placeholder = { Text(stringResource(R.string.website_url_example)) },
                        supportingText = { Text(state.inputErrorMessage ?: stringResource(R.string.website_url_hint)) },
                        isError = state.inputErrorMessage != null,
                        enabled = !state.isAdding,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr),
                        shape = FocusShapes.medium,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { viewModel.addWebsite(state.addWebsiteInput) }),
                        modifier = Modifier.fillMaxWidth().focusRequester(urlFocusRequester)
                            .semantics { state.inputErrorMessage?.let { error(it) } }
                            .testTag("add_website_text_input")
                    )
                    Button(
                        onClick = { viewModel.addWebsite(state.addWebsiteInput) },
                        enabled = !state.isAdding,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("add_website_button")
                    ) {
                        Text(stringResource(if (state.isAdding) R.string.website_adding else R.string.website_add_action))
                    }
                    LaunchedEffect(state.inputErrorMessage) {
                        if (state.inputErrorMessage != null) urlFocusRequester.requestFocus()
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Quick Add Pills
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.website_quick_add),
                            color = textSecondary,
                            style = MaterialTheme.typography.labelLarge
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            samplePills.forEach { pill ->
                                val isAdded = state.manualBlockedWebsites.any { it.domain.equals(pill, ignoreCase = true) }
                                OutlinedButton(
                                    onClick = { viewModel.addWebsite(pill) },
                                    enabled = !isAdded && !state.isAdding,
                                    border = BorderStroke(1.dp, FocusColors.ControlBorder),
                                    shape = FocusShapes.pill
                                ) {
                                    Text(stringResource(if (isAdded) R.string.website_quick_added else R.string.website_quick_add_action, pill))
                                }
                            }
                        }
                    }
                }

                // Blocklist Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.website_list_title),
                            color = textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f).semantics { heading() }
                        )
                        if (state.manualBlockedWebsites.isNotEmpty()) {
                            Text(
                                text = pluralStringResource(R.plurals.website_count, state.manualBlockedWebsites.size, state.manualBlockedWebsites.size),
                                color = textSecondary,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                // Blocklist Items with YouTube popup dashed border & cards styling
                if (state.manualBlockedWebsites.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .sheetDashedBorder(
                                    color = cardBorder,
                                    cornerRadius = 16.dp
                                )
                                .background(cardBg, RoundedCornerShape(16.dp))
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Language,
                                    contentDescription = null,
                                    tint = textSecondary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = stringResource(R.string.website_list_empty_title),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = textPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Text(
                                    text = stringResource(R.string.website_list_empty_body),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = textSecondary,
                                        fontSize = 12.sp
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    items(state.manualBlockedWebsites, key = { it.domain }) { website ->
                        WebsiteItemCard(
                            website = website,
                            onToggle = { isEnabled -> viewModel.toggleWebsite(website.domain, isEnabled) },
                            onDelete = { viewModel.deleteWebsite(website.domain) },
                            isBlockingAvailable = state.isManualBlockingEnabled && state.isAccessibilityEnabled,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                }
        }

        // Bottom "Done" button matching YouTube study channels manager sheet
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
                    .heightIn(min = 52.dp)
                    .testTag("website_blocker_done_button")
            ) {
                Text(
                    text = stringResource(R.string.action_done),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun WebsiteFaviconView(
    domain: String,
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val motion = LocalFocusMotion.current
    val iconBg = FocusColors.SurfaceSubtle
    val textSecondary = FocusColors.OverlayTextSecondary

    val cleanDomain = remember(domain) {
        domain.trim().lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .split("/").firstOrNull() ?: domain
    }

    val faviconUrl = remember(cleanDomain) {
        "https://www.google.com/s2/favicons?domain=$cleanDomain&sz=128"
    }

    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(iconBg),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(faviconUrl)
                .crossfade(!motion.reduceMotion)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Fit,
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        tint = if (isEnabled) FocusColors.Primary else textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = null,
                        tint = if (isEnabled) FocusColors.Primary else textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        )
    }
}

/**
 * Clean website item card with dashed border matching the YouTube Channel card styling.
 */
@Composable
private fun WebsiteItemCard(
    website: BlockedWebsiteEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    isBlockingAvailable: Boolean,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val cardBg = FocusColors.OverlaySurface
    val cardBorder = FocusColors.OverlayBorder
    val textPrimary = FocusColors.OverlayTextPrimary
    val textSecondary = FocusColors.OverlayTextSecondary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .sheetDashedBorder(
                color = if (website.isEnabled) cardBorder else cardBorder.copy(alpha = 0.5f),
                cornerRadius = 16.dp
            )
            .background(cardBg, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clip(FocusShapes.medium)
                .toggleable(value = website.isEnabled, role = Role.Switch, onValueChange = onToggle)
                .padding(vertical = FocusSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WebsiteFaviconView(
                domain = website.domain,
                isEnabled = website.isEnabled
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = website.domain,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = textPrimary,
                        textDirection = TextDirection.Ltr
                    )
                )
                Text(
                    text = stringResource(if (website.isEnabled && isBlockingAvailable) R.string.website_blocking_active else R.string.website_blocking_paused),
                    color = if (website.isEnabled && isBlockingAvailable) FocusColors.EmeraldText else textSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Switch(
                checked = website.isEnabled,
                onCheckedChange = null,
                colors = FocusColors.SwitchColors,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }

            IconButton(
                onClick = { showDeleteConfirmation = true },
                modifier = Modifier.size(48.dp).align(Alignment.End)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.website_delete_action, website.domain),
                    tint = FocusColors.BlockedText,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
    if (showDeleteConfirmation) {
        FocusDestructiveDialog(
            title = stringResource(R.string.website_delete_title, website.domain),
            message = stringResource(R.string.website_delete_body),
            confirmLabel = stringResource(R.string.website_remove_action),
            onConfirm = onDelete,
            onDismiss = { showDeleteConfirmation = false }
        )
    }
}
