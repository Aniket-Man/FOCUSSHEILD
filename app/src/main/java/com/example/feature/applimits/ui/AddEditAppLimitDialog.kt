package com.example.feature.applimits.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditAppLimitDialog(
    installedApps: List<InstalledAppChoice>,
    editingItem: AppLimitUiItem?,
    onDismiss: () -> Unit,
    onSave: (packageName: String, appName: String, dailyLimitMinutes: Int, isStrictOverride: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedApp by remember {
        mutableStateOf(
            if (editingItem != null) {
                InstalledAppChoice(
                    packageName = editingItem.packageName,
                    appName = editingItem.appName,
                    icon = editingItem.appIcon
                )
            } else null
        )
    }

    var searchQuery by remember { mutableStateOf("") }
    var dailyLimitMinutes by remember {
        mutableFloatStateOf((editingItem?.dailyLimitMinutes ?: 45).toFloat())
    }
    var isStrictOverride by remember {
        mutableStateOf(editingItem?.isStrictOverride ?: false)
    }
    var showStrictWarningDialog by remember { mutableStateOf(false) }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.appName.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val presetMinutes = listOf(10, 15, 20, 30, 45, 60, 90, 120)

    androidx.activity.compose.BackHandler(enabled = true) {
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FocusColors.Surface,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.80f)
                .navigationBarsPadding()
                .padding(FocusSpacing.xl)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(FocusColors.PrimaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = if (editingItem != null) "Edit App Limit" else "Set App Limit",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary
                        )
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = FocusColors.TextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Step 1: App Selection (if adding new)
            if (editingItem == null && selectedApp == null) {
                Text(
                    text = "1. Choose Application",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = FocusColors.TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps...") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Search, contentDescription = null, tint = FocusColors.TextSecondary)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_limit_search_input"),
                    shape = FocusShapes.medium,
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FocusColors.Primary,
                        unfocusedBorderColor = FocusColors.CardBorder
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(FocusShapes.medium)
                                .clickable { selectedApp = app }
                                .padding(vertical = 8.dp, horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (app.icon != null) {
                                Image(
                                    bitmap = app.icon.toBitmap(80, 80).asImageBitmap(),
                                    contentDescription = app.appName,
                                    modifier = Modifier.size(36.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(FocusColors.SurfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Rounded.Apps, contentDescription = null, tint = FocusColors.TextSecondary)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.appName,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = FocusColors.TextPrimary
                                    )
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        color = FocusColors.TextMuted
                                    ),
                                    maxLines = 1
                                )
                            }

                            if (app.isAlreadyLimited) {
                                Surface(
                                    shape = RoundedCornerShape(100.dp),
                                    color = FocusColors.AmberOrange.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = "Configured",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = FocusColors.AmberOrange,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (selectedApp != null) {
                // Selected App Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = FocusShapes.card,
                    colors = CardDefaults.cardColors(containerColor = FocusColors.SurfaceVariant),
                    border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(FocusSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedApp!!.icon != null) {
                            Image(
                                bitmap = selectedApp!!.icon!!.toBitmap(96, 96).asImageBitmap(),
                                contentDescription = selectedApp!!.appName,
                                modifier = Modifier.size(44.dp)
                            )
                        } else {
                            Icon(Icons.Rounded.Apps, contentDescription = null, tint = FocusColors.Primary, modifier = Modifier.size(36.dp))
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedApp!!.appName,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = FocusColors.TextPrimary
                                )
                            )
                            Text(
                                text = selectedApp!!.packageName,
                                style = MaterialTheme.typography.labelSmall.copy(color = FocusColors.TextMuted)
                            )
                        }

                        if (editingItem == null) {
                            TextButton(onClick = { selectedApp = null }) {
                                Text("Change", color = FocusColors.Primary)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Step 2: Daily Limit Configuration
                Text(
                    text = "Daily Limit Allowance",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = FocusColors.TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Preset Chips
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetMinutes.forEach { mins ->
                        val isSelected = dailyLimitMinutes.toInt() == mins
                        FilterChip(
                            selected = isSelected,
                            onClick = { dailyLimitMinutes = mins.toFloat() },
                            label = { Text("$mins min") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = FocusColors.Primary,
                                selectedLabelColor = FocusColors.TextOnDark
                            ),
                            shape = RoundedCornerShape(100.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Allowance:",
                        style = MaterialTheme.typography.bodyMedium.copy(color = FocusColors.TextSecondary)
                    )
                    Text(
                        text = "${dailyLimitMinutes.toInt()} minutes / day",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.Primary
                        )
                    )
                }

                Slider(
                    value = dailyLimitMinutes,
                    onValueChange = { dailyLimitMinutes = it },
                    valueRange = 1f..180f,
                    colors = SliderDefaults.colors(
                        thumbColor = FocusColors.Primary,
                        activeTrackColor = FocusColors.Primary,
                        inactiveTrackColor = FocusColors.CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Strict Mode Override Toggle
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FocusShapes.medium)
                        .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.medium),
                    color = FocusColors.SurfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = if (isStrictOverride) FocusColors.BlockedRed else FocusColors.TextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Strict Mode Override",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = FocusColors.TextPrimary
                                    )
                                )
                                Text(
                                    text = "Disables 5-min emergency uses and leave-block options when daily limit is reached.",
                                    style = MaterialTheme.typography.labelSmall.copy(color = FocusColors.TextSecondary)
                                )
                            }
                        }

                        Switch(
                            checked = isStrictOverride,
                            onCheckedChange = { isChecked ->
                                if (isChecked) {
                                    showStrictWarningDialog = true
                                } else {
                                    isStrictOverride = false
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = FocusColors.TextOnDark,
                                checkedTrackColor = FocusColors.Primary,
                                uncheckedThumbColor = FocusColors.TextMuted,
                                uncheckedTrackColor = FocusColors.SurfaceSubtle
                            )
                        )
                    }
                }

                // Strict Mode Warning Banner
                if (isStrictOverride) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = FocusShapes.medium,
                        color = FocusColors.BlockedRed.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, FocusColors.BlockedRed.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = FocusColors.BlockedRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Strict Mode active: This limit cannot be deleted or edited once saved. Choose your limit wisely!",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    lineHeight = 16.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Save Action
                ElevatedButton(
                    onClick = {
                        val app = selectedApp ?: return@ElevatedButton
                        onSave(
                            app.packageName,
                            app.appName,
                            dailyLimitMinutes.toInt(),
                            isStrictOverride
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("save_app_limit_button"),
                    shape = FocusShapes.medium,
                    colors = ButtonDefaults.elevatedButtonColors(
                        containerColor = FocusColors.Primary,
                        contentColor = FocusColors.TextOnDark
                    )
                ) {
                    Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (editingItem != null) "Update App Limit" else "Save App Limit",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }

    // Strict Mode Warning Modal Dialog
    if (showStrictWarningDialog) {
        AlertDialog(
            onDismissRequest = { showStrictWarningDialog = false },
            containerColor = FocusColors.Surface,
            titleContentColor = FocusColors.TextPrimary,
            textContentColor = FocusColors.TextSecondary,
            icon = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(FocusColors.BlockedRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = FocusColors.BlockedRed,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    text = "Strict Mode Warning",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "Once you enable Strict Mode and save, this app limit CANNOT be deleted, edited, or toggled off during the day. Choose your limit wisely!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        isStrictOverride = true
                        showStrictWarningDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FocusColors.BlockedRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("I Understand", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        isStrictOverride = false
                        showStrictWarningDialog = false
                    }
                ) {
                    Text("Cancel", color = FocusColors.TextSecondary)
                }
            }
        )
    }
}
