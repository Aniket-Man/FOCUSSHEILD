package com.example.feature.blockedapps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.accessibility.AccessibilityHelper
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing

@Composable
fun BlockedAppsScreen(
    viewModel: BlockedAppsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh accessibility status whenever user returns from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkAccessibilityStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = FocusSpacing.sm, vertical = FocusSpacing.sm),
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
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Distracting Apps Shield",
                        style = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary,
                            fontSize = 20.sp
                        )
                    )
                    Text(
                        text = "${uiState.totalBlockedCount} applications configured to block",
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 12.sp
                        )
                    )
                }
            }
        },
        containerColor = FocusColors.Background,
        modifier = modifier.testTag("blocked_apps_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = FocusSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.md)
        ) {
            // 1. Accessibility Service Status Banner
            item {
                AccessibilityStatusCard(
                    isEnabled = uiState.isAccessibilityEnabled,
                    onEnableClick = {
                        AccessibilityHelper.openAccessibilitySettings(context)
                    }
                )
            }

            // 2. Search Filter Input
            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    placeholder = {
                        Text(
                            text = "Search installed applications...",
                            color = FocusColors.TextMuted,
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search",
                            tint = FocusColors.TextSecondary
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = FocusColors.TextSecondary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = FocusShapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = FocusColors.Surface,
                        unfocusedContainerColor = FocusColors.Surface,
                        focusedBorderColor = FocusColors.Primary,
                        unfocusedBorderColor = FocusColors.CardBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_apps_input")
                )
            }

            // 3. App List or Loading / Empty States
            if (uiState.isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = FocusColors.Primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            } else if (uiState.installedApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = FocusColors.TextMuted,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (uiState.searchQuery.isNotEmpty()) "No matching apps found" else "No applications available",
                                color = FocusColors.TextSecondary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            } else {
                items(
                    items = uiState.installedApps,
                    key = { it.packageName }
                ) { appItem ->
                    AppBlockRowItem(
                        app = appItem,
                        onToggle = { isChecked ->
                            viewModel.toggleAppBlocked(appItem.packageName, appItem.appName, isChecked)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(FocusSpacing.xl))
            }
        }
    }
}

@Composable
private fun AccessibilityStatusCard(
    isEnabled: Boolean,
    onEnableClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .background(
                if (isEnabled) FocusColors.EmeraldLight.copy(alpha = 0.5f)
                else FocusColors.CoralLight.copy(alpha = 0.6f)
            )
            .border(
                1.dp,
                if (isEnabled) FocusColors.EmeraldSuccess.copy(alpha = 0.4f)
                else FocusColors.CoralWarning.copy(alpha = 0.4f),
                FocusShapes.card
            )
            .padding(FocusSpacing.base)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isEnabled) FocusColors.EmeraldSuccess.copy(alpha = 0.15f)
                        else FocusColors.CoralWarning.copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isEnabled) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    contentDescription = null,
                    tint = if (isEnabled) FocusColors.EmeraldSuccess else FocusColors.CoralWarning,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isEnabled) "Accessibility Shield Active" else "Accessibility Permission Required",
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) Color(0xFF15803D) else Color(0xFFC2410C),
                        fontSize = 14.sp
                    )
                )
                Text(
                    text = if (isEnabled)
                        "Foreground distraction detection is running and ready."
                    else
                        "FocusShield needs Accessibility access to detect and block distracting apps during sessions.",
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                )
            }

            if (!isEnabled) {
                Spacer(modifier = Modifier.width(8.dp))
                ElevatedButton(
                    onClick = onEnableClick,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = FocusColors.CoralWarning,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("enable_accessibility_button")
                ) {
                    Text(
                        text = "Enable",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AppBlockRowItem(
    app: InstalledAppItem,
    onToggle: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(FocusShapes.card)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.card)
            .padding(horizontal = FocusSpacing.base, vertical = 12.dp)
            .testTag("app_row_${app.packageName}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // App Icon
            AppIconImage(
                drawable = app.icon,
                appName = app.appName,
                modifier = Modifier.size(42.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = FocusColors.TextPrimary,
                        fontSize = 15.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = app.packageName,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextMuted,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Switch(
                checked = app.isBlocked,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FocusColors.Primary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = FocusColors.CardBorder
                ),
                modifier = Modifier.testTag("toggle_${app.packageName}")
            )
        }
    }
}

@Composable
fun AppIconImage(
    drawable: Drawable?,
    appName: String,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(drawable) {
        drawable?.let { d ->
            if (d is BitmapDrawable && d.bitmap != null) {
                d.bitmap
            } else {
                try {
                    val width = if (d.intrinsicWidth > 0) d.intrinsicWidth else 96
                    val height = if (d.intrinsicHeight > 0) d.intrinsicHeight else 96
                    val b = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(b)
                    d.setBounds(0, 0, canvas.width, canvas.height)
                    d.draw(canvas)
                    b
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = appName,
            modifier = modifier.clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(FocusColors.PrimaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Security,
                contentDescription = null,
                tint = FocusColors.Primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
