package com.example.feature.websiteblocker.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PublicOff
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.example.core.design.FocusShapes
import com.example.core.design.LocalFocusColors
import com.example.data.local.entity.BlockedWebsiteEntity

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

    val isDark = LocalFocusColors.current.isDark
    val sheetBg = if (isDark) Color(0xFF121214) else Color(0xFFFFFFFF)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)

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

    val isDark = LocalFocusColors.current.isDark
    val sheetBg = if (isDark) Color(0xFF121214) else Color(0xFFFFFFFF)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF64748B)
    val dragHandleColor = if (isDark) Color(0xFF4E4E52) else Color(0xFFCBD5E1)
    val cardBg = if (isDark) Color(0xFF18181B) else Color(0xFFF8FAFC)
    val cardBorder = if (isDark) Color(0xFF27272A) else Color(0xFFE2E8F0)
    val searchBg = if (isDark) Color(0xFF1E1E20) else Color(0xFFF1F5F9)
    val searchBorder = if (isDark) Color(0xFF2E2E32) else Color(0xFFE2E8F0)
    val searchPlaceholder = if (isDark) Color(0xFF7E7E82) else Color(0xFF94A3B8)
    val doneBtnBg = if (isDark) Color.White else Color(0xFF0F172A)
    val doneBtnText = if (isDark) Color.Black else Color.White
    val stoneBtnBg = if (isDark) Color(0xFF27272A) else Color(0xFFE2E8F0)
    val stoneBtnBorder = if (isDark) Color(0xFF3F3F46) else Color(0xFFCBD5E1)

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Auto Adult Shield, 1: Custom Websites

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
    val protectedBrowsers = listOf("Chrome", "Firefox", "Edge", "Brave", "Opera", "Samsung", "DuckDuckGo")

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
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // Main title matching YouTube popup typography
            item {
                Text(
                    text = "Block adult sites and websites\nyou distract from",
                    color = textPrimary,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
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
                                color = Color(0xFFFF6D2C),
                                cornerRadius = 16.dp
                            )
                            .background(
                                color = if (isDark) Color(0xFF2A1B14) else Color(0xFFFFF7ED),
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
                                        .background(Color(0xFFFF6D2C).copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Security,
                                        contentDescription = null,
                                        tint = Color(0xFFFF6D2C),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Accessibility Permission Required",
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            color = textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    )
                                    Text(
                                        text = "Inspect browser address bars in real time",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = textSecondary,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }

                            Text(
                                text = "FocusShield needs Accessibility Service to detect URL changes and block restricted web pages.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = textSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            )

                            Button(
                                onClick = {
                                    try {
                                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (e: Exception) {
                                        // Fallback
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = stoneBtnBg,
                                    contentColor = textPrimary
                                ),
                                border = BorderStroke(1.dp, stoneBtnBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .testTag("website_blocker_grant_permission_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Shield,
                                        contentDescription = null,
                                        tint = Color(0xFFFF6D2C),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Grant Accessibility Permission",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
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
                                color = Color(0xFF10B981),
                                cornerRadius = 14.dp
                            )
                            .background(
                                color = if (isDark) Color(0xFF0E2218) else Color(0xFFECFDF5),
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
                                tint = Color(0xFF10B981),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Browser address bar inspector active & protecting",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF10B981),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            // Tabs Selector: Auto 18+ Blocker vs Custom Website Blocklist
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 18.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(searchBg)
                        .padding(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selectedTab == 0) cardBg else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "18+ Adult Shield",
                                color = if (selectedTab == 0) textPrimary else textSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                            )
                            if (state.isAutoAdultBlockingEnabled) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (selectedTab == 1) cardBg else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Custom Blocklist (${state.manualBlockedWebsites.size})",
                                color = if (selectedTab == 1) textPrimary else textSecondary,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Tab 0: Automatic Adult Blocker Section
            if (selectedTab == 0) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .sheetDashedBorder(
                                color = if (state.isAutoAdultBlockingEnabled) Color(0xFFEF4444) else cardBorder,
                                cornerRadius = 18.dp
                            )
                            .background(cardBg, RoundedCornerShape(18.dp))
                            .padding(18.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Adult18PlusBadgeNew(isEnabled = state.isAutoAdultBlockingEnabled)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Block adult & NSFW sites",
                                            style = MaterialTheme.typography.titleMedium.copy(
                                                color = textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                        )
                                        Text(
                                            text = if (state.isAutoAdultBlockingEnabled) "Active Real-Time Protection" else "Protection Disabled",
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = if (state.isAutoAdultBlockingEnabled) Color(0xFFEF4444) else textSecondary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                        )
                                    }
                                }

                                Switch(
                                    checked = state.isAutoAdultBlockingEnabled,
                                    onCheckedChange = { viewModel.toggleAutoAdultBlocking(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = FocusColors.Primary,
                                        uncheckedThumbColor = textSecondary,
                                        uncheckedTrackColor = searchBg
                                    ),
                                    modifier = Modifier.testTag("toggle_auto_adult_blocker")
                                )
                            }

                            Text(
                                text = "Automatically blocks explicit, 18+ pornographic, and adult content across all supported browsers with zero configuration.",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = textSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp
                                )
                            )

                            // Protected Browsers Tags
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Supported Browsers:",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = textSecondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    protectedBrowsers.forEach { browser ->
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(searchBg)
                                                .border(1.dp, searchBorder, RoundedCornerShape(8.dp))
                                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                        ) {
                                            Text(
                                                text = browser,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    color = textSecondary,
                                                    fontSize = 11.sp
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Tab 1: Custom Blocklist & Quick Add
            if (selectedTab == 1) {
                // Input box styled like YouTube channels search bar
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(searchBg)
                            .border(1.dp, searchBorder, RoundedCornerShape(14.dp))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Website URL",
                                tint = searchPlaceholder,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(modifier = Modifier.weight(1f)) {
                                if (state.addWebsiteInput.isEmpty()) {
                                    Text(
                                        text = "Enter website link (e.g. reddit.com)",
                                        color = searchPlaceholder,
                                        fontSize = 14.sp
                                    )
                                }
                                BasicTextField(
                                    value = state.addWebsiteInput,
                                    onValueChange = { viewModel.updateAddWebsiteInput(it) },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = textPrimary,
                                        fontSize = 14.sp
                                    ),
                                    cursorBrush = SolidColor(if (isDark) Color.White else FocusColors.Primary),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (state.addWebsiteInput.isNotBlank()) {
                                                viewModel.addWebsite(state.addWebsiteInput)
                                                focusManager.clearFocus()
                                                keyboardController?.hide()
                                            }
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("add_website_text_input")
                                )
                            }

                            if (state.addWebsiteInput.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        viewModel.addWebsite(state.addWebsiteInput)
                                        focusManager.clearFocus()
                                        keyboardController?.hide()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "Add Website",
                                        tint = textPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
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
                            text = "Quick add distracting sites:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = textSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            samplePills.forEach { pill ->
                                val isAdded = state.manualBlockedWebsites.any { it.domain.equals(pill, ignoreCase = true) }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isAdded) searchBg.copy(alpha = 0.5f) else searchBg)
                                        .border(
                                            1.dp,
                                            if (isAdded) searchBorder.copy(alpha = 0.4f) else searchBorder,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable(enabled = !isAdded) {
                                            viewModel.addWebsite(pill)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (isAdded) "✓ $pill" else "+ $pill",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = if (isAdded) textSecondary else textPrimary,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 11.sp
                                            )
                                        )
                                    }
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
                            text = "Blocked Websites",
                            color = textPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (state.manualBlockedWebsites.isNotEmpty()) {
                            Text(
                                text = "${state.manualBlockedWebsites.size} Total",
                                color = textSecondary,
                                fontSize = 13.sp
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
                                    text = "No custom websites added yet",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = textPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                )
                                Text(
                                    text = "Enter a URL above to block it across browsers.",
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
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                }
            }
        }

        // Bottom "Done" button matching YouTube study channels manager sheet
        Surface(
            color = sheetBg,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
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
                    .height(52.dp)
                    .testTag("website_blocker_done_button")
            ) {
                Text(
                    text = "Done",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
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
    val isDark = LocalFocusColors.current.isDark
    val iconBg = if (isDark) Color(0xFF242426) else Color(0xFFE2E8F0)
    val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF64748B)

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
                .crossfade(true)
                .build(),
            contentDescription = "$domain icon",
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
    modifier: Modifier = Modifier
) {
    val isDark = LocalFocusColors.current.isDark
    val cardBg = if (isDark) Color(0xFF18181B) else Color(0xFFF8FAFC)
    val cardBorder = if (isDark) Color(0xFF27272A) else Color(0xFFE2E8F0)
    val textPrimary = if (isDark) Color.White else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFF8E8E93) else Color(0xFF64748B)

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
        Row(
            modifier = Modifier.fillMaxWidth(),
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
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (website.isEnabled) "Blocking active" else "Paused",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (website.isEnabled) Color(0xFF10B981) else textSecondary,
                        fontSize = 12.sp
                    )
                )
            }

            Switch(
                checked = website.isEnabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FocusColors.Primary,
                    uncheckedThumbColor = textSecondary,
                    uncheckedTrackColor = if (isDark) Color(0xFF242426) else Color(0xFFE2E8F0)
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete",
                    tint = Color(0xFFFF6D2C),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun Adult18PlusBadgeNew(
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val isDark = LocalFocusColors.current.isDark
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (isEnabled) Color(0xFF321218) else if (isDark) Color(0xFF242426) else Color(0xFFE2E8F0))
            .border(
                1.5.dp,
                if (isEnabled) Color(0xFFEF4444) else Color.Transparent,
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "18+",
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (isEnabled) Color(0xFFFCA5A5) else FocusColors.TextMuted,
                fontWeight = FontWeight.Black,
                fontSize = 12.sp,
                letterSpacing = (-0.5).sp
            )
        )
    }
}
