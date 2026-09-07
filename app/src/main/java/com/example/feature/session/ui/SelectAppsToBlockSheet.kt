package com.example.feature.session.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonChecked
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.core.util.InstalledAppItem
import com.example.core.util.InstalledAppsProvider
import com.example.core.util.InstalledAppsProvider.toImageBitmap
import com.example.feature.session.SpecialAppOption

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectAppsToBlockSheet(
    youtubeOption: SpecialAppOption,
    browserOption: SpecialAppOption,
    isDistractingMasterEnabled: Boolean,
    blockedAppPackages: Set<String>,
    onYoutubeOptionChange: (SpecialAppOption) -> Unit,
    onBrowserOptionChange: (SpecialAppOption) -> Unit,
    onToggleDistractingMaster: (Boolean, Set<String>) -> Unit,
    onToggleAppBlocked: (String, Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    val handleClose: () -> Unit = {
        focusManager.clearFocus()
        keyboardController?.hide()
        onClose()
    }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    var searchQuery by remember { mutableStateOf("") }

    var installedBrowsers by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var installedUserApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }

    var isSpecialAppsExpanded by remember { mutableStateOf(true) }
    var isDistractingExpanded by remember { mutableStateOf(true) }
    var showYouTubeChannelsDialog by remember { mutableStateOf(false) }
    var showWebsiteBlockerDialog by remember { mutableStateOf(false) }

    // Nested BackHandler: step back dismisses sub-dialogs first before returning to setup
    androidx.activity.compose.BackHandler(enabled = showYouTubeChannelsDialog || showWebsiteBlockerDialog) {
        if (showYouTubeChannelsDialog) {
            showYouTubeChannelsDialog = false
        } else if (showWebsiteBlockerDialog) {
            showWebsiteBlockerDialog = false
        }
    }

    if (showYouTubeChannelsDialog) {
        ManageYouTubeChannelsDialog(onDismiss = { showYouTubeChannelsDialog = false })
    }

    if (showWebsiteBlockerDialog) {
        val websiteSheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        com.example.feature.websiteblocker.ui.WebsiteBlockerSheet(
            sheetState = websiteSheetState,
            onDismiss = { showWebsiteBlockerDialog = false }
        )
    }

    LaunchedEffect(Unit) {
        val limitEntities = try { com.example.FocusShieldApp.instance.appLimitRepository.getAllLimits() } catch (e: Exception) { emptyList() }
        val limitPkgs = limitEntities.filter { it.isEnabled }.map { it.packageName }.toSet()

        installedBrowsers = InstalledAppsProvider.getInstalledBrowsers(context)
        installedUserApps = InstalledAppsProvider.getInstalledUserApps(context, limitPkgs)

        // Ensure any app with active limit is selected in blockedAppPackages
        limitPkgs.forEach { pkg ->
            if (!blockedAppPackages.contains(pkg)) {
                onToggleAppBlocked(pkg, true)
            }
        }
    }

    val filteredUserApps = remember(installedUserApps, searchQuery) {
        if (searchQuery.isBlank()) {
            installedUserApps
        } else {
            installedUserApps.filter { it.appName.contains(searchQuery, ignoreCase = true) }
        }
    }

    val allUserAppPackages = remember(installedUserApps) {
        installedUserApps.map { it.packageName }.toSet()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.84f)
            .background(FocusColors.Surface)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header Row: Back + Title + Close Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = handleClose,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = FocusColors.TextPrimary
                    )
                }

                Text(
                    text = "Select Apps to Block",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(FocusColors.SurfaceVariant)
                    .clickable { handleClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = FocusColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            placeholder = {
                Text(
                    text = "Search apps",
                    style = MaterialTheme.typography.bodyMedium.copy(color = FocusColors.TextMuted)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search",
                    tint = FocusColors.TextMuted
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = FocusColors.SurfaceVariant,
                unfocusedContainerColor = FocusColors.SurfaceVariant,
                focusedBorderColor = FocusColors.Primary,
                unfocusedBorderColor = FocusColors.CardBorderSubtle,
                focusedTextColor = FocusColors.TextPrimary,
                unfocusedTextColor = FocusColors.TextPrimary
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SECTION 1: Special Apps (YouTube & Browsers)
            if (searchQuery.isBlank() || "youtube".contains(searchQuery, ignoreCase = true) || "browser".contains(searchQuery, ignoreCase = true)) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(FocusColors.SurfaceVariant)
                            .padding(16.dp)
                    ) {
                        // Section Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isSpecialAppsExpanded = !isSpecialAppsExpanded },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Special Apps",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = FocusColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )

                            Icon(
                                imageVector = if (isSpecialAppsExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Toggle",
                                tint = FocusColors.TextMuted
                            )
                        }

                        AnimatedVisibility(
                            visible = isSpecialAppsExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))

                                // Item 1: YouTube
                                SpecialAppCard(
                                    title = "YouTube",
                                    leftIcon = {
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFF0000)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = "YouTube",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    selectedOption = youtubeOption,
                                    onOptionSelect = onYoutubeOptionChange,
                                    studyModeSubtext = "Watch only study channels >",
                                    onSubtextClick = { showYouTubeChannelsDialog = true }
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                Divider(color = FocusColors.CardBorderSubtle, thickness = 1.dp)
                                Spacer(modifier = Modifier.height(16.dp))

                                // Item 2: Browser Apps
                                SpecialAppCard(
                                    title = "Browser apps",
                                    leftIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Language,
                                            contentDescription = "Browser apps",
                                            tint = FocusColors.Primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    },
                                    titleSuffix = {
                                        if (installedBrowsers.isNotEmpty()) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(start = 8.dp)
                                            ) {
                                                installedBrowsers.forEach { browser ->
                                                    if (browser.icon != null) {
                                                        Image(
                                                            bitmap = browser.icon.toImageBitmap(),
                                                            contentDescription = browser.appName,
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .clip(CircleShape)
                                                                .border(1.5.dp, FocusColors.SurfaceVariant, CircleShape)
                                                        )
                                                        Spacer(modifier = Modifier.width((-6).dp))
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    selectedOption = browserOption,
                                    onOptionSelect = onBrowserOptionChange,
                                    studyModeSubtext = "Blocks adult sites & manually added sites in browser >",
                                    onSubtextClick = { showWebsiteBlockerDialog = true }
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 2: Distracting Apps
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(FocusColors.SurfaceVariant)
                        .padding(16.dp)
                ) {
                    // Header with Master Toggle Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { isDistractingExpanded = !isDistractingExpanded }
                        ) {
                            Text(
                                text = "Distracting",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = FocusColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = if (isDistractingExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Toggle",
                                tint = FocusColors.TextMuted
                            )
                        }

                        Switch(
                            checked = isDistractingMasterEnabled,
                            onCheckedChange = { onToggleDistractingMaster(it, allUserAppPackages) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = FocusColors.Primary,
                                uncheckedThumbColor = FocusColors.TextMuted,
                                uncheckedTrackColor = FocusColors.Surface
                            )
                        )
                    }

                    AnimatedVisibility(
                        visible = isDistractingExpanded,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))

                            if (filteredUserApps.isEmpty()) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "No matching apps found." else "Scanning installed apps on device...",
                                    style = MaterialTheme.typography.bodyMedium.copy(color = FocusColors.TextSecondary),
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            } else {
                                filteredUserApps.forEach { app ->
                                    val isBlocked = blockedAppPackages.contains(app.packageName)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (app.icon != null) {
                                                Image(
                                                    bitmap = app.icon.toImageBitmap(),
                                                    contentDescription = app.appName,
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                )
                                            } else {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(FocusColors.PrimaryContainer),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = app.appName.take(1).uppercase(),
                                                        style = MaterialTheme.typography.titleMedium.copy(color = FocusColors.Primary)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.width(12.dp))

                                            Column {
                                                Text(
                                                    text = app.appName,
                                                    style = MaterialTheme.typography.bodyLarge.copy(
                                                        color = FocusColors.TextPrimary,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                )
                                                if (app.hasAppLimit) {
                                                    Text(
                                                        text = "App limit set",
                                                        style = MaterialTheme.typography.labelSmall.copy(color = FocusColors.Primary)
                                                    )
                                                }
                                            }
                                        }

                                        Switch(
                                            checked = isBlocked,
                                            onCheckedChange = { onToggleAppBlocked(app.packageName, it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = Color.White,
                                                checkedTrackColor = FocusColors.Primary,
                                                uncheckedThumbColor = FocusColors.TextMuted,
                                                uncheckedTrackColor = FocusColors.Surface
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
    }
}

@Composable
private fun SpecialAppCard(
    title: String,
    leftIcon: @Composable () -> Unit,
    titleSuffix: (@Composable () -> Unit)? = null,
    selectedOption: SpecialAppOption,
    onOptionSelect: (SpecialAppOption) -> Unit,
    studyModeSubtext: String,
    onSubtextClick: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // App Title Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            leftIcon()
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    color = FocusColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            )
            if (titleSuffix != null) {
                titleSuffix()
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Option 1: Block completely
        SpecialAppRadioRow(
            text = "Block completely",
            isSelected = selectedOption == SpecialAppOption.BLOCK_COMPLETELY,
            onClick = { onOptionSelect(SpecialAppOption.BLOCK_COMPLETELY) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Option 2: Allow completely
        SpecialAppRadioRow(
            text = "Allow completely",
            isSelected = selectedOption == SpecialAppOption.ALLOW_COMPLETELY,
            onClick = { onOptionSelect(SpecialAppOption.ALLOW_COMPLETELY) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Option 3: Study Mode
        SpecialAppRadioRow(
            text = "Study Mode",
            hasProBadge = true,
            subtext = studyModeSubtext,
            isSelected = selectedOption == SpecialAppOption.STUDY_MODE,
            onClick = { onOptionSelect(SpecialAppOption.STUDY_MODE) },
            onSubtextClick = onSubtextClick
        )
    }
}

@Composable
private fun SpecialAppRadioRow(
    text: String,
    hasProBadge: Boolean = false,
    subtext: String? = null,
    isSelected: Boolean,
    onClick: () -> Unit,
    onSubtextClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 16.sp
                    )
                )

                if (hasProBadge) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE5B800))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "PRO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.Black,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }

            if (subtext != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (onSubtextClick != null) FocusColors.Primary else FocusColors.TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (onSubtextClick != null) FontWeight.SemiBold else FontWeight.Normal
                    ),
                    modifier = if (onSubtextClick != null) Modifier.clickable { onSubtextClick() } else Modifier
                )
            }
        }

        // Custom Green Radio Circle matching reference design
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (isSelected) FocusColors.Primary else Color.Transparent)
                .border(
                    width = 2.dp,
                    color = if (isSelected) FocusColors.Primary else FocusColors.TextMuted,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }
        }
    }
}

data class PresetChannel(
    val name: String,
    val handle: String,
    val category: String
)

val PRESET_STUDY_CHANNELS = listOf(
    // JEE & NEET Prep
    PresetChannel("Physics Wallah", "@PhysicsWallah", "JEE / NEET"),
    PresetChannel("Unacademy JEE", "@UnacademyJEE", "JEE / NEET"),
    PresetChannel("Vedantu JEE", "@VedantuJEE", "JEE / NEET"),
    PresetChannel("Mohit Tyagi", "@MohitTyagi", "JEE / NEET"),
    PresetChannel("Physics Galaxy", "@PhysicsGalaxyOfficial", "JEE / NEET"),
    PresetChannel("Competishun", "@Competishun", "JEE / NEET"),
    
    // CS & Programming
    PresetChannel("freeCodeCamp.org", "@freecodecamp", "Coding"),
    PresetChannel("Fireship", "@Fireship", "Coding"),
    PresetChannel("CodeWithHarry", "@CodeWithHarry", "Coding"),
    PresetChannel("takeUforward (Striver)", "@takeUforward", "Coding"),
    PresetChannel("Gate Smashers", "@GateSmashers", "Coding"),
    PresetChannel("Apna College", "@ApnaCollegeOfficial", "Coding"),
    PresetChannel("CS50", "@cs50", "Coding"),
    PresetChannel("Traversy Media", "@TraversyMedia", "Coding"),

    // Math & Science
    PresetChannel("Khan Academy", "@KhanAcademy", "Math & Science"),
    PresetChannel("3Blue1Brown", "@3blue1brown", "Math & Science"),
    PresetChannel("Veritasium", "@veritasium", "Math & Science"),
    PresetChannel("MinutePhysics", "@minutephysics", "Math & Science"),

    // Academics & Exams
    PresetChannel("MIT OpenCourseWare", "@mitocw", "Academics"),
    PresetChannel("NPTEL", "@nptelhrd", "Academics"),
    PresetChannel("CrashCourse", "@crashcourse", "Academics"),
    PresetChannel("StudyIQ Education", "@StudyIQEducation", "Academics"),
    PresetChannel("TED-Ed", "@TEDEd", "Academics"),

    // Medical & Biology
    PresetChannel("Osmosis", "@osmosis", "Medical"),
    PresetChannel("Ninja Nerd", "@NinjaNerdLectures", "Medical")
)

@Composable
fun ManageYouTubeChannelsDialog(
    onDismiss: () -> Unit
) {
    com.example.feature.channels.YouTubeStudyChannelManagerSheet(
        onDismiss = onDismiss
    )
}

