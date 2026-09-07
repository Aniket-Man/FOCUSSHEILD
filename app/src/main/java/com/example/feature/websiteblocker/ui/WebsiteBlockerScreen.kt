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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.PublicOff
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.data.local.entity.BlockedWebsiteEntity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WebsiteBlockerScreen(
    viewModel: WebsiteBlockerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.checkPermissions()
    }

    LaunchedEffect(state.errorMessage, state.successMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessages()
        }
    }

    val samplePills = listOf(
        "facebook.com", "instagram.com", "reddit.com",
        "twitter.com", "tiktok.com", "gambling.com"
    )

    val protectedBrowsers = listOf("Chrome", "Firefox", "Edge", "Brave", "Opera", "Samsung", "DuckDuckGo")

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = FocusSpacing.screenHorizontal, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(FocusColors.SurfaceVariant)
                        .testTag("website_blocker_back_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = FocusColors.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Browser & Website Shield",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = FocusColors.Background,
        modifier = modifier.testTag("website_blocker_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = FocusSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 0. Permission Warning Banner if Accessibility is OFF
            if (!state.isAccessibilityEnabled) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E1C12)),
                        border = BorderStroke(1.dp, FocusColors.AmberOrange.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = FocusColors.AmberOrange,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Accessibility Service Required",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        color = FocusColors.TextPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                                Text(
                                    text = "To inspect browser address bars in real-time, please enable FocusShield in Accessibility Settings.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextSecondary,
                                        fontSize = 12.sp
                                    )
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        try {
                                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            // Fallback
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = FocusColors.AmberOrange),
                                    shape = FocusShapes.pill,
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(
                                        text = "Enable Accessibility",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF102A1E),
                        border = BorderStroke(1.dp, FocusColors.EmeraldSuccess.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = FocusColors.EmeraldSuccess,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Accessibility Active — Browser URL address bar inspector active",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.EmeraldSuccess,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            )
                        }
                    }
                }
            }

            // 1. AUTOMATIC ADULT SITES BLOCKER CARD
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
                    border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Adult18PlusBadge(isEnabled = state.isAutoAdultBlockingEnabled)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Automatically block adult sites",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = FocusColors.TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    )
                                    Text(
                                        text = if (state.isAutoAdultBlockingEnabled) "Active Real-Time Protection" else "Protection Disabled",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (state.isAutoAdultBlockingEnabled) Color(0xFFEF4444) else FocusColors.TextMuted,
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
                                    checkedTrackColor = Color(0xFFEF4444),
                                    uncheckedThumbColor = FocusColors.TextMuted,
                                    uncheckedTrackColor = FocusColors.SurfaceVariant
                                ),
                                modifier = Modifier.testTag("toggle_auto_adult_blocker")
                            )
                        }

                        Text(
                            text = "Automatically detects and blocks 18+ explicit, adult, pornographic, and NSFW websites across Chrome and all supported browsers in real time.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = FocusColors.TextSecondary,
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        )

                        // Protected Browsers Badges
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Protected Browsers:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                protectedBrowsers.forEach { browser ->
                                    Surface(
                                        shape = FocusShapes.pill,
                                        color = FocusColors.SurfaceVariant,
                                        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle)
                                    ) {
                                        Text(
                                            text = browser,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = FocusColors.TextSecondary,
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. CHOOSE THE WEBSITES THAT DISTRACT YOU MOST CARD
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = FocusColors.Surface),
                    border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (state.isManualBlockingEnabled) Color(0xFF1E293B) else FocusColors.SurfaceVariant
                                        )
                                        .border(
                                            1.5.dp,
                                            if (state.isManualBlockingEnabled) FocusColors.Primary else FocusColors.CardBorderSubtle,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PublicOff,
                                        contentDescription = null,
                                        tint = if (state.isManualBlockingEnabled) FocusColors.Primary else FocusColors.TextMuted,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Choose the websites that distract you most",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            color = FocusColors.TextPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                    )
                                    Text(
                                        text = if (state.isManualBlockingEnabled) "Custom Distraction Filter Active" else "Custom Filter Paused",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = if (state.isManualBlockingEnabled) FocusColors.Primary else FocusColors.TextMuted,
                                            fontSize = 12.sp
                                        )
                                    )
                                }
                            }

                            Switch(
                                checked = state.isManualBlockingEnabled,
                                onCheckedChange = { viewModel.toggleManualBlocking(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = FocusColors.Primary,
                                    uncheckedThumbColor = FocusColors.TextMuted,
                                    uncheckedTrackColor = FocusColors.SurfaceVariant
                                ),
                                modifier = Modifier.testTag("toggle_manual_website_blocker")
                            )
                        }

                        // Add Website Input Field
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = state.addWebsiteInput,
                                onValueChange = { viewModel.updateAddWebsiteInput(it) },
                                placeholder = {
                                    Text(
                                        text = "Enter link (e.g. reddit.com)",
                                        color = FocusColors.TextMuted,
                                        fontSize = 13.sp
                                    )
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = Color(0xFF18181C),
                                    unfocusedContainerColor = Color(0xFF18181C),
                                    focusedBorderColor = FocusColors.Primary,
                                    unfocusedBorderColor = Color(0xFF2A2A32),
                                    focusedTextColor = FocusColors.TextPrimary,
                                    unfocusedTextColor = FocusColors.TextPrimary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("add_website_text_input")
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = { viewModel.addWebsite(state.addWebsiteInput) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = FocusColors.Primary),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("add_website_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Add,
                                    contentDescription = "Add",
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Add",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }
                        }

                        // Suggestion Pills
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Quick Add Distraction Suggestions:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = FocusColors.TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            )

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                samplePills.forEach { pill ->
                                    Surface(
                                        shape = FocusShapes.pill,
                                        color = FocusColors.SurfaceVariant,
                                        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                                        modifier = Modifier.clickable {
                                            viewModel.addWebsite(pill)
                                        }
                                    ) {
                                        Text(
                                            text = "+ $pill",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = FocusColors.Primary,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 11.sp
                                            ),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. MANUALLY BLOCKED WEBSITES LIST
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Distraction Website Blocklist (${state.manualBlockedWebsites.size})",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                }
            }

            if (state.manualBlockedWebsites.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = FocusColors.Surface,
                        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Language,
                                contentDescription = null,
                                tint = FocusColors.TextMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No custom websites added yet",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = FocusColors.TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                            Text(
                                text = "Paste a link above to block specific websites in browsers.",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextSecondary,
                                    fontSize = 12.sp
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(state.manualBlockedWebsites, key = { it.domain }) { website ->
                    ManualWebsiteItemRow(
                        website = website,
                        onToggle = { isEnabled -> viewModel.toggleWebsite(website.domain, isEnabled) },
                        onDelete = { viewModel.deleteWebsite(website.domain) }
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
private fun ManualWebsiteItemRow(
    website: BlockedWebsiteEntity,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = FocusColors.Surface,
        border = BorderStroke(1.dp, FocusColors.CardBorderSubtle),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(FocusColors.SurfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Public,
                    contentDescription = null,
                    tint = FocusColors.Primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = website.domain,
                    style = MaterialTheme.typography.titleSmall.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
                Text(
                    text = if (website.isEnabled) "Blocking active" else "Paused",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (website.isEnabled) FocusColors.EmeraldSuccess else FocusColors.TextMuted,
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
                    uncheckedThumbColor = FocusColors.TextMuted,
                    uncheckedTrackColor = FocusColors.SurfaceVariant
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun Adult18PlusBadge(
    isEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(if (isEnabled) Color(0xFF321218) else FocusColors.SurfaceVariant)
            .border(
                1.5.dp,
                if (isEnabled) Color(0xFFEF4444) else FocusColors.CardBorderSubtle,
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            if (isEnabled) {
                drawLine(
                    color = Color(0xFFEF4444).copy(alpha = 0.55f),
                    start = androidx.compose.ui.geometry.Offset(size.width * 0.2f, size.height * 0.8f),
                    end = androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.2f),
                    strokeWidth = 2.5f
                )
            }
        }
        Text(
            text = "18+",
            style = MaterialTheme.typography.labelMedium.copy(
                color = if (isEnabled) Color(0xFFFCA5A5) else FocusColors.TextMuted,
                fontWeight = FontWeight.Black,
                fontSize = 13.sp,
                letterSpacing = (-0.5).sp
            )
        )
    }
}
