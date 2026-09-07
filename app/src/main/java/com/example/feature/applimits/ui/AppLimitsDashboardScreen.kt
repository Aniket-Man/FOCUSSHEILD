package com.example.feature.applimits.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HourglassBottom
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppLimitsDashboardScreen(
    viewModel: AppLimitsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<AppLimitUiItem?>(null) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("app_limits_dashboard_screen"),
        containerColor = FocusColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "App Limits",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Daily usage allowances & temporary sessions",
                            style = MaterialTheme.typography.labelSmall.copy(color = FocusColors.TextSecondary)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("app_limits_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = FocusColors.TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = FocusColors.Surface,
                    titleContentColor = FocusColors.TextPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    editingItem = null
                    showAddDialog = true
                },
                containerColor = FocusColors.Primary,
                contentColor = FocusColors.TextOnDark,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("fab_add_app_limit")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Set App Limit", fontWeight = FontWeight.Bold)
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = FocusSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(6.dp))
                // Summary Stats Banner
                AppLimitsSummaryCard(
                    totalUsedMinutesToday = uiState.totalUsedMinutesToday,
                    activeLimitsCount = uiState.activeLimitsCount,
                    reachedLimitsCount = uiState.reachedLimitsCount
                )
            }

            if (uiState.items.isEmpty()) {
                item {
                    EmptyLimitsView(
                        onAddLimit = {
                            editingItem = null
                            showAddDialog = true
                        }
                    )
                }
            } else {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Configured Limits (${uiState.items.size})",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary
                            )
                        )
                    }
                }

                items(uiState.items, key = { it.packageName }) { item ->
                    AppLimitCard(
                        item = item,
                        onToggle = { isEnabled -> viewModel.toggleLimit(item.packageName, isEnabled) },
                        onEdit = {
                            editingItem = item
                            showAddDialog = true
                        },
                        onDelete = { viewModel.deleteLimit(item.packageName) },
                        onUnlockToday = { viewModel.unlockForToday(item.packageName) },
                        onResetToday = { viewModel.resetTodayUsage(item.packageName) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    if (showAddDialog) {
        AddEditAppLimitDialog(
            installedApps = installedApps,
            editingItem = editingItem,
            onDismiss = {
                showAddDialog = false
                editingItem = null
            },
            onSave = { packageName, appName, dailyLimitMinutes, isStrictOverride ->
                viewModel.saveLimit(
                    packageName = packageName,
                    appName = appName,
                    dailyLimitMinutes = dailyLimitMinutes,
                    isStrictOverride = isStrictOverride
                )
                showAddDialog = false
                editingItem = null
            }
        )
    }
}

@Composable
private fun AppLimitsSummaryCard(
    totalUsedMinutesToday: Int,
    activeLimitsCount: Int,
    reachedLimitsCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = FocusShapes.card,
        colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)
    ) {
        Column(
            modifier = Modifier.padding(FocusSpacing.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Today's Limited App Usage",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = FocusColors.TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    val hours = totalUsedMinutesToday / 60
                    val mins = totalUsedMinutesToday % 60
                    val formattedTime = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = FocusColors.Primary
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(FocusColors.PrimaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HourglassBottom,
                        contentDescription = null,
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = FocusShapes.medium,
                    color = FocusColors.SurfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(FocusColors.EmeraldSuccess)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$activeLimitsCount Active Limits",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = FocusColors.TextPrimary
                            )
                        )
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f),
                    shape = FocusShapes.medium,
                    color = if (reachedLimitsCount > 0) FocusColors.BlockedRed.copy(alpha = 0.1f) else FocusColors.SurfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (reachedLimitsCount > 0) FocusColors.BlockedRed else FocusColors.TextMuted)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$reachedLimitsCount Limit Exceeded",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = if (reachedLimitsCount > 0) FocusColors.BlockedRed else FocusColors.TextSecondary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLimitCard(
    item: AppLimitUiItem,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onUnlockToday: () -> Unit,
    onResetToday: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val progressColor = when {
        item.isLimitReached -> FocusColors.BlockedRed
        item.usagePercentage >= 0.75f -> FocusColors.AmberOrange
        else -> FocusColors.EmeraldSuccess
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("app_limit_card_${item.packageName}"),
        shape = FocusShapes.card,
        colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
        border = BorderStroke(
            1.dp,
            if (item.isLimitReached) FocusColors.BlockedRed.copy(alpha = 0.4f) else FocusColors.CardBorderSubtle
        )
    ) {
        Column(
            modifier = Modifier.padding(FocusSpacing.md)
        ) {
            // Header Row: Icon, Name, Badges, Switch, Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.appIcon != null) {
                    Image(
                        bitmap = item.appIcon.toBitmap(96, 96).asImageBitmap(),
                        contentDescription = item.appName,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(FocusColors.SurfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Rounded.Apps, contentDescription = null, tint = FocusColors.Primary)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = item.appName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary
                            )
                        )
                        if (item.isStrictOverride) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = "Strict Override",
                                tint = FocusColors.BlockedRed,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Sub-badges
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        if (item.isLimitReached) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = FocusColors.BlockedRed.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "BLOCKED FOR TODAY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FocusColors.BlockedRed
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else if (item.isBypassedToday) {
                            Surface(
                                shape = RoundedCornerShape(100.dp),
                                color = FocusColors.AmberOrange.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "UNLOCKED TODAY",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = FocusColors.AmberOrange
                                    ),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Switch(
                    checked = item.isEnabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = FocusColors.TextOnDark,
                        checkedTrackColor = FocusColors.Primary,
                        uncheckedThumbColor = FocusColors.TextMuted,
                        uncheckedTrackColor = FocusColors.SurfaceSubtle
                    )
                )

                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Options",
                            tint = FocusColors.TextSecondary
                        )
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = FocusColors.Surface
                    ) {
                        DropdownMenuItem(
                            text = { Text("Edit Limit") },
                            onClick = {
                                showMenu = false
                                onEdit()
                            },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Reset Today's Usage") },
                            onClick = {
                                showMenu = false
                                onResetToday()
                            },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) }
                        )
                        if (!item.isBypassedToday) {
                            DropdownMenuItem(
                                text = { Text("Unlock for Rest of Today") },
                                onClick = {
                                    showMenu = false
                                    onUnlockToday()
                                },
                                leadingIcon = { Icon(Icons.Rounded.LockOpen, contentDescription = null) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Delete Limit", color = FocusColors.BlockedRed) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = FocusColors.BlockedRed) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress Bar & Usage Indicators
            LinearProgressIndicator(
                progress = { item.usagePercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(100.dp)),
                color = progressColor,
                trackColor = FocusColors.CardBorderSubtle,
                strokeCap = StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Used ${item.usedMinutes}m of ${item.dailyLimitMinutes}m",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Medium,
                        color = FocusColors.TextPrimary
                    )
                )

                Text(
                    text = if (item.isLimitReached) "0m remaining" else "${item.remainingMinutes}m remaining",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (item.isLimitReached) FocusColors.BlockedRed else FocusColors.TextSecondary
                    )
                )
            }
        }
    }
}

@Composable
private fun EmptyLimitsView(
    onAddLimit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        shape = FocusShapes.card,
        colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(FocusColors.PrimaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Timer,
                    contentDescription = null,
                    tint = FocusColors.Primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "No App Limits Set",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Set daily time budgets for social media, gaming, and shopping apps. When opened, you choose short usage sessions (2m, 5m, 10m, 20m) that automatically tick down and keep you intentional.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = FocusColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            ElevatedButton(
                onClick = onAddLimit,
                shape = FocusShapes.medium,
                colors = ButtonDefaults.elevatedButtonColors(
                    containerColor = FocusColors.Primary,
                    contentColor = FocusColors.TextOnDark
                )
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Set First App Limit", fontWeight = FontWeight.Bold)
            }
        }
    }
}
