package com.example.feature.blocks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusDestructiveDialog
import com.example.feature.applimits.ui.AppLimitUiItem
import com.example.feature.applimits.ui.AppLimitsViewModel
import com.example.feature.channels.dashedBorder

@Composable
internal fun BlocksSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = FocusColors.TextPrimary,
            modifier = Modifier.semantics { heading() }
        )
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
internal fun DashedEmptyAppLimitCard(
    onAdd: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = FocusColors.Surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .dashedBorder(
                strokeWidth = 1.2.dp,
                color = FocusColors.CardBorder,
                cornerRadius = 18.dp,
                dashLength = 7.dp,
                gapLength = 5.dp
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = enabled, onClick = onAdd)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FocusSpacing.md)
        ) {
            Surface(
                color = FocusColors.SurfaceVariant,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.HourglassBottom,
                        contentDescription = null,
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.limits_empty_title),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = FocusColors.TextPrimary
                )
                Text(
                    text = stringResource(R.string.limits_empty_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = FocusColors.TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ActionPillChip(
                label = stringResource(R.string.blocks_pill_add),
                leadingIcon = Icons.Rounded.Add,
                isActive = false,
                enabled = enabled,
                onClick = onAdd
            )
        }
    }
}

@Composable
internal fun DashedAppLimitCard(
    item: AppLimitUiItem,
    viewModel: AppLimitsViewModel,
    busy: Boolean,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var confirmAllow by rememberSaveable { mutableStateOf(false) }
    val icon = remember(item.appIcon) {
        try {
            item.appIcon?.toBitmap(64, 64)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    val durationLabel = formatLimitDuration(item.dailyLimitMinutes)
    val usedLabel = stringResource(R.string.blocks_limit_used_compact, item.usedMinutes)
    val statusLabel = when {
        !item.isEnabled -> stringResource(R.string.limits_off)
        item.isBypassedToday -> stringResource(R.string.limits_allowed_today)
        item.isLimitReached -> stringResource(R.string.limits_reached)
        else -> usedLabel
    }

    Surface(
        color = FocusColors.Surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
            .fillMaxWidth()
            .dashedBorder(
                strokeWidth = 1.2.dp,
                color = if (item.isLimitReached && item.isEnabled) FocusColors.BlockedRed.copy(alpha = 0.65f) else FocusColors.CardBorder,
                cornerRadius = 18.dp,
                dashLength = 7.dp,
                gapLength = 5.dp
            )
            .clip(RoundedCornerShape(18.dp))
            .clickable(enabled = !busy && !item.isStrictOverride, onClick = onEdit)
            .testTag("app_limit_${item.packageName}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FocusSpacing.md)
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                } else {
                    Surface(
                        color = FocusColors.SurfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Apps,
                                contentDescription = null,
                                tint = FocusColors.Primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = item.appName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = FocusColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .semantics { heading() }
                        )
                        if (item.isStrictOverride) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = stringResource(R.string.limits_strict_status),
                                tint = FocusColors.AmberText,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                    Text(
                        text = "$durationLabel · $statusLabel",
                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum"),
                        color = if (item.isLimitReached && item.isEnabled) FocusColors.BlockedText else FocusColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Switch(
                        checked = item.isEnabled,
                        onCheckedChange = { viewModel.toggleLimit(item.packageName, it) },
                        enabled = !busy && !item.isStrictOverride,
                        colors = FocusColors.SwitchColors,
                        modifier = Modifier.testTag("toggle_limit_${item.packageName}")
                    )
                    if (!item.isStrictOverride) {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                enabled = !busy
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.limits_more_app, item.appName),
                                    tint = FocusColors.TextSecondary
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.limits_edit_title)) },
                                    onClick = {
                                        menuExpanded = false
                                        onEdit()
                                    }
                                )
                                if (item.isEnabled && !item.isBypassedToday) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.limits_allow_action)) },
                                        onClick = {
                                            menuExpanded = false
                                            confirmAllow = true
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(R.string.limits_delete_action),
                                            color = FocusColors.BlockedText
                                        )
                                    },
                                    onClick = {
                                        menuExpanded = false
                                        confirmDelete = true
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (item.isEnabled && !item.isBypassedToday) {
                LinearProgressIndicator(
                    progress = { item.usagePercentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(FocusShapes.pill),
                    color = if (item.isLimitReached) FocusColors.BlockedRed else FocusColors.EmeraldSuccess,
                    trackColor = FocusColors.SurfaceSubtle
                )
            }
        }
    }

    if (confirmDelete) {
        FocusDestructiveDialog(
            title = stringResource(R.string.limits_delete_app, item.appName),
            message = stringResource(R.string.limits_delete_body),
            onConfirm = { viewModel.deleteLimit(item.packageName) },
            onDismiss = { confirmDelete = false },
            confirmLabel = stringResource(R.string.limits_delete_action)
        )
    }
    if (confirmAllow) {
        AlertDialog(
            onDismissRequest = { confirmAllow = false },
            title = { Text(stringResource(R.string.limits_allow_title, item.appName)) },
            text = { Text(stringResource(R.string.limits_allow_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmAllow = false
                        viewModel.unlockForToday(item.packageName)
                    },
                    enabled = !busy
                ) {
                    Text(stringResource(R.string.limits_allow_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmAllow = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
internal fun BlockTogglePillCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    leadingBadge: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = FocusColors.Surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (checked) FocusColors.EmeraldSuccess.copy(alpha = 0.45f) else FocusColors.CardBorderSubtle
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onToggle
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FocusSpacing.md)
        ) {
            leadingBadge()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = FocusColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (checked) FocusColors.EmeraldText else FocusColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ActionPillChip(
                label = stringResource(if (checked) R.string.blocks_pill_active else R.string.blocks_pill_add),
                leadingIcon = if (checked) Icons.Rounded.Check else Icons.Rounded.Add,
                isActive = checked,
                enabled = enabled,
                onClick = null
            )
        }
    }
}

@Composable
internal fun BlockNavigationPillCard(
    title: String,
    subtitle: String,
    pillLabel: String,
    isActive: Boolean,
    onClick: () -> Unit,
    leadingBadge: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = FocusColors.Surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(FocusSpacing.md)
        ) {
            leadingBadge()
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = FocusColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = FocusColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            ActionPillChip(
                label = pillLabel,
                leadingIcon = if (isActive) null else Icons.Rounded.Add,
                trailingIcon = if (isActive) Icons.Rounded.ChevronRight else null,
                isActive = isActive,
                enabled = true,
                onClick = null
            )
        }
    }
}

@Composable
internal fun StrictProtectionCard(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Surface(
        color = FocusColors.Surface,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Switch,
                        onValueChange = onCheckedChange
                    )
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(FocusSpacing.md)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = FocusColors.TextPrimary
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = FocusColors.TextSecondary
                    )
                }
                Switch(
                    checked = checked,
                    onCheckedChange = null,
                    enabled = enabled,
                    colors = FocusColors.SwitchColors,
                    modifier = Modifier.clearAndSetSemantics { }
                )
            }
            if (actionLabel != null && onActionClick != null) {
                HorizontalDivider(color = FocusColors.CardBorderSubtle, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled, onClick = onActionClick)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = FocusColors.EmeraldText
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = FocusColors.EmeraldText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionPillChip(
    label: String,
    isActive: Boolean,
    enabled: Boolean,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    val containerColor = if (isActive) FocusColors.EmeraldLight else FocusColors.SurfaceVariant
    val contentColor = if (isActive) FocusColors.EmeraldText else FocusColors.TextPrimary
    val borderColor = if (isActive) FocusColors.EmeraldSuccess.copy(alpha = 0.4f) else FocusColors.CardBorderSubtle

    val baseModifier = Modifier
        .minimumInteractiveComponentSize()
        .clip(FocusShapes.pill)
        .let { if (onClick != null) it.clickable(enabled = enabled, onClick = onClick) else it }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = FocusShapes.pill,
        border = BorderStroke(1.dp, borderColor),
        modifier = baseModifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1
            )
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
internal fun YouTubeShortsBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(38.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(34.dp)) {
            val w = size.width
            val h = size.height
            val shortsRed = Color(0xFFFF0033)

            // Stylized tilted Shorts lightning/ribbon badge
            val ribbonPath = Path().apply {
                moveTo(w * 0.62f, h * 0.08f)
                lineTo(w * 0.24f, h * 0.30f)
                cubicTo(w * 0.14f, h * 0.36f, w * 0.14f, h * 0.50f, w * 0.24f, h * 0.56f)
                lineTo(w * 0.42f, h * 0.66f)
                lineTo(w * 0.24f, h * 0.76f)
                cubicTo(w * 0.16f, h * 0.81f, w * 0.20f, h * 0.94f, w * 0.32f, h * 0.92f)
                lineTo(w * 0.76f, h * 0.70f)
                cubicTo(w * 0.86f, h * 0.64f, w * 0.86f, h * 0.50f, w * 0.76f, h * 0.44f)
                lineTo(w * 0.58f, h * 0.34f)
                lineTo(w * 0.76f, h * 0.24f)
                cubicTo(w * 0.84f, h * 0.19f, w * 0.76f, h * 0.04f, w * 0.62f, h * 0.08f)
                close()
            }
            drawPath(path = ribbonPath, color = shortsRed)

            // Centered white play triangle
            val playPath = Path().apply {
                moveTo(w * 0.44f, h * 0.40f)
                lineTo(w * 0.44f, h * 0.60f)
                lineTo(w * 0.61f, h * 0.50f)
                close()
            }
            drawPath(path = playPath, color = Color.White)
        }
    }
}

@Composable
internal fun InstagramReelsBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(38.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(34.dp)) {
            val w = size.width
            val h = size.height
            val gradient = Brush.linearGradient(
                colors = listOf(Color(0xFFF58529), Color(0xFFDD2A7B), Color(0xFF8134AF)),
                start = Offset(0f, h),
                end = Offset(w, 0f)
            )
            drawRoundRect(
                brush = gradient,
                cornerRadius = CornerRadius(w * 0.30f, w * 0.30f)
            )
            // Reels clapperboard inner outline
            val inset = w * 0.22f
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(inset, inset),
                size = Size(w - inset * 2f, h - inset * 2f),
                cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
                style = Stroke(width = w * 0.07f)
            )
            drawLine(
                color = Color.White,
                start = Offset(inset, h * 0.38f),
                end = Offset(w - inset, h * 0.38f),
                strokeWidth = w * 0.06f,
                cap = StrokeCap.Round
            )
            val playPath = Path().apply {
                moveTo(w * 0.45f, h * 0.47f)
                lineTo(w * 0.45f, h * 0.67f)
                lineTo(w * 0.62f, h * 0.57f)
                close()
            }
            drawPath(path = playPath, color = Color.White)
        }
    }
}

@Composable
internal fun FacebookReelsBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.size(38.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(34.dp)) {
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = Color(0xFF1877F2),
                cornerRadius = CornerRadius(w * 0.30f, w * 0.30f)
            )
            val inset = w * 0.22f
            drawRoundRect(
                color = Color.White,
                topLeft = Offset(inset, inset),
                size = Size(w - inset * 2f, h - inset * 2f),
                cornerRadius = CornerRadius(w * 0.14f, w * 0.14f),
                style = Stroke(width = w * 0.07f)
            )
            drawLine(
                color = Color.White,
                start = Offset(inset, h * 0.38f),
                end = Offset(w - inset, h * 0.38f),
                strokeWidth = w * 0.06f,
                cap = StrokeCap.Round
            )
            val playPath = Path().apply {
                moveTo(w * 0.45f, h * 0.47f)
                lineTo(w * 0.45f, h * 0.67f)
                lineTo(w * 0.62f, h * 0.57f)
                close()
            }
            drawPath(path = playPath, color = Color.White)
        }
    }
}

@Composable
internal fun WebsiteGlobeBadge(modifier: Modifier = Modifier) {
    Surface(
        color = FocusColors.SurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.TravelExplore,
                contentDescription = null,
                tint = FocusColors.TextSecondary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
internal fun SilenceNotificationsBadge(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (isActive) FocusColors.EmeraldLight else FocusColors.SurfaceVariant,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.size(38.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.NotificationsOff,
                contentDescription = null,
                tint = if (isActive) FocusColors.EmeraldText else FocusColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun formatLimitDuration(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> stringResource(R.string.blocks_limit_duration_hours_minutes, hours, minutes)
        hours > 0 -> stringResource(R.string.blocks_limit_duration_hours, hours)
        else -> stringResource(R.string.blocks_limit_duration_minutes, minutes)
    }
}
