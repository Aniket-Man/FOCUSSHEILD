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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.accessibility.AccessibilityHelper
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusScreenHeader
import com.example.core.ui.FocusSearchField
import com.example.core.ui.FocusStatusMessage

@Composable
fun BlockedAppsScreen(
    viewModel: BlockedAppsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savingPackages by viewModel.savingPackages.collectAsStateWithLifecycle()
    val operationError by viewModel.operationError.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Refresh accessibility status whenever the user returns from system settings.
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
            FocusScreenHeader(
                title = stringResource(R.string.blocked_apps_title),
                onBack = onNavigateBack,
                subtitle = pluralStringResource(
                    R.plurals.blocked_apps_subtitle,
                    uiState.totalBlockedCount,
                    uiState.totalBlockedCount
                ),
                modifier = Modifier.statusBarsPadding(),
                backButtonModifier = Modifier.testTag("back_button")
            )
        },
        containerColor = FocusColors.Background,
        modifier = modifier.testTag("blocked_apps_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = FocusSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.md),
            contentPadding = PaddingValues(bottom = FocusSpacing.xl)
        ) {
            if (operationError != null) {
                item {
                    FocusStatusMessage(
                        message = operationError,
                        isError = true,
                        onDismiss = viewModel::clearOperationError
                    )
                }
            }

            item {
                AccessibilityStatusCard(
                    isEnabled = uiState.isAccessibilityEnabled,
                    onEnableClick = { AccessibilityHelper.openAccessibilitySettings(context) }
                )
            }

            item {
                FocusSearchField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    label = stringResource(R.string.blocked_apps_search_label),
                    placeholder = stringResource(R.string.blocked_apps_search_placeholder),
                    modifier = Modifier.testTag("search_apps_input")
                )
            }

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
                                text = if (uiState.searchQuery.isNotBlank()) {
                                    stringResource(
                                        R.string.blocked_apps_empty_search,
                                        uiState.searchQuery
                                    )
                                } else {
                                    stringResource(R.string.blocked_apps_empty_all)
                                },
                                color = FocusColors.TextSecondary,
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
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
                        enabled = !savingPackages.contains(appItem.packageName),
                        onToggle = { isChecked ->
                            viewModel.toggleAppBlocked(
                                appItem.packageName,
                                appItem.appName,
                                isChecked
                            )
                        }
                    )
                }
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
                    tint = if (isEnabled) FocusColors.EmeraldText else FocusColors.CoralText,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(
                        if (isEnabled) R.string.blocked_apps_access_on_title
                        else R.string.blocked_apps_access_off_title
                    ),
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (isEnabled) FocusColors.EmeraldText else FocusColors.CoralText,
                        fontSize = 14.sp
                    )
                )
                Text(
                    text = stringResource(
                        if (isEnabled) R.string.blocked_apps_access_on_body
                        else R.string.blocked_apps_access_off_body
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(
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
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("enable_accessibility_button")
                ) {
                    Text(
                        text = stringResource(R.string.blocked_apps_enable_access),
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
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val switchDescription = stringResource(R.string.blocked_apps_switch_description, app.appName)
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
            AppIconImage(
                drawable = app.icon,
                modifier = Modifier.size(42.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = FocusColors.TextPrimary,
                        fontSize = 15.sp
                    ),
                    maxLines = 1
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(
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
                enabled = enabled,
                colors = FocusColors.SwitchColors,
                modifier = Modifier
                    .semantics { contentDescription = switchDescription }
                    .testTag("toggle_${app.packageName}")
            )
        }
    }
}

@Composable
fun AppIconImage(
    drawable: Drawable?,
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
            // The row already announces the app name; the icon is decorative.
            contentDescription = null,
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
