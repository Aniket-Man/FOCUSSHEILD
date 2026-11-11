package com.example.feature.session.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusSearchField
import com.example.core.util.InstalledAppItem
import com.example.core.util.InstalledAppsProvider
import com.example.feature.session.SpecialAppOption

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
    onOpenYouTubeChannels: () -> Unit,
    onOpenWebsiteBlocker: () -> Unit,
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
    var hasScannedApps by remember { mutableStateOf(false) }

    var isSpecialAppsExpanded by remember { mutableStateOf(true) }
    var isDistractingExpanded by remember { mutableStateOf(true) }

    // The YouTube channel manager and the website blocker are pages of the single setup sheet
    // (SetupSubView.YOUTUBE_CHANNELS / SetupSubView.WEBSITE_BLOCKER), so this screen only reports
    // the navigation intent upward (issue #52).

    LaunchedEffect(Unit) {
        val limitEntities = try {
            com.example.FocusShieldApp.instance.appLimitRepository.getAllLimits()
        } catch (e: Exception) {
            emptyList()
        }
        val limitPkgs = limitEntities.filter { it.isEnabled }.map { it.packageName }.toSet()

        // One off-main-thread pass for both inventories (the provider also avoids querying the
        // browser list twice). Icons arrive already rasterised; the UI only draws the bitmaps.
        val snapshot = InstalledAppsProvider.getInstalledAppsSnapshot(context, limitPkgs)
        installedBrowsers = snapshot.browsers
        installedUserApps = snapshot.userApps
        hasScannedApps = true
        // Daily app limits only label rows here. Session blocking stays an explicit choice and
        // never inherits (or silently adds) app-limit packages.
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

    val searchLabel = stringResource(R.string.session_apps_search)
    val distractingAppsLabel = stringResource(R.string.session_apps_distracting)
    val clearSearchLabel = stringResource(R.string.action_clear_search)
    val toggleSectionLabel = stringResource(R.string.session_apps_toggle_section)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.84f)
            .background(FocusColors.Surface)
            .padding(horizontal = FocusSpacing.screenHorizontal, vertical = 16.dp)
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
                        contentDescription = stringResource(R.string.action_back),
                        tint = FocusColors.TextPrimary
                    )
                }

                Text(
                    text = stringResource(R.string.session_apps_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = FocusColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp
                    )
                )
            }

            IconButton(
                onClick = handleClose,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(FocusColors.SurfaceVariant)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_close),
                    tint = FocusColors.TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        FocusSearchField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = searchLabel,
            onSearch = {
                focusManager.clearFocus()
                keyboardController?.hide()
            },
            modifier = Modifier.testTag("blocked_apps_search_field")
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // SECTION 1: Special Apps (YouTube & Browsers)
            if (
                searchQuery.isBlank() ||
                "youtube".contains(searchQuery, ignoreCase = true) ||
                "browser".contains(searchQuery, ignoreCase = true)
            ) {
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
                                .clickable(role = Role.Button) {
                                    isSpecialAppsExpanded = !isSpecialAppsExpanded
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.session_apps_special),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = FocusColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )

                            Icon(
                                imageVector = if (isSpecialAppsExpanded) {
                                    Icons.Rounded.KeyboardArrowUp
                                } else {
                                    Icons.Rounded.KeyboardArrowDown
                                },
                                contentDescription = toggleSectionLabel,
                                tint = FocusColors.TextMuted
                            )
                        }

                        AnimatedVisibility(
                            visible = isSpecialAppsExpanded,
                            // Fade only: animating expand/shrink re-measures the whole sheet every
                            // frame while the launcher icons settle, which is visible as a stutter.
                            enter = fadeIn(tween(150)),
                            exit = fadeOut(tween(120))
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))

                                // Item 1: YouTube
                                SpecialAppCard(
                                    title = stringResource(R.string.session_apps_youtube),
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
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    },
                                    selectedOption = youtubeOption,
                                    onOptionSelect = onYoutubeOptionChange,
                                    studyModeSubtext = stringResource(R.string.session_apps_youtube_study),
                                    onSubtextClick = onOpenYouTubeChannels
                                )

                                Spacer(modifier = Modifier.height(16.dp))
                                Divider(color = FocusColors.CardBorderSubtle, thickness = 1.dp)
                                Spacer(modifier = Modifier.height(16.dp))

                                // Item 2: Browser Apps
                                SpecialAppCard(
                                    title = stringResource(R.string.session_apps_browser),
                                    leftIcon = {
                                        Icon(
                                            imageVector = Icons.Rounded.Language,
                                            contentDescription = null,
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
                                                    val browserIcon = browser.iconBitmap
                                                    if (browserIcon != null) {
                                                        Image(
                                                            bitmap = browserIcon,
                                                            contentDescription = null,
                                                            modifier = Modifier
                                                                .size(24.dp)
                                                                .clip(CircleShape)
                                                                .border(
                                                                    1.5.dp,
                                                                    FocusColors.SurfaceVariant,
                                                                    CircleShape
                                                                )
                                                        )
                                                        Spacer(modifier = Modifier.width((-6).dp))
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    selectedOption = browserOption,
                                    onOptionSelect = onBrowserOptionChange,
                                    studyModeSubtext = stringResource(R.string.session_apps_browser_study),
                                    onSubtextClick = onOpenWebsiteBlocker
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
                            modifier = Modifier
                                .weight(1f)
                                .clickable(role = Role.Button) {
                                    isDistractingExpanded = !isDistractingExpanded
                                }
                        ) {
                            Text(
                                text = distractingAppsLabel,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    color = FocusColors.TextPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = if (isDistractingExpanded) {
                                    Icons.Rounded.KeyboardArrowUp
                                } else {
                                    Icons.Rounded.KeyboardArrowDown
                                },
                                contentDescription = toggleSectionLabel,
                                tint = FocusColors.TextMuted
                            )
                        }

                        Switch(
                            checked = isDistractingMasterEnabled,
                            onCheckedChange = {
                                onToggleDistractingMaster(it, allUserAppPackages)
                            },
                            colors = FocusColors.SwitchColors,
                            modifier = Modifier.semantics {
                                contentDescription = distractingAppsLabel
                            }
                        )
                    }

                    AnimatedVisibility(
                        visible = isDistractingExpanded,
                        // Fade only — see the Special Apps section above. This one holds the full
                        // installed-app list, so a height animation is especially expensive.
                        enter = fadeIn(tween(150)),
                        exit = fadeOut(tween(120))
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))

                            if (filteredUserApps.isEmpty()) {
                                Text(
                                    text = stringResource(
                                        if (searchQuery.isNotBlank() || hasScannedApps) {
                                            R.string.session_apps_no_results
                                        } else {
                                            R.string.session_apps_scanning
                                        }
                                    ),
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = FocusColors.TextSecondary
                                    ),
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            } else {
                                filteredUserApps.forEach { app ->
                                    val isBlocked = blockedAppPackages.contains(app.packageName)
                                    val appIcon = app.iconBitmap
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .toggleable(
                                                value = isBlocked,
                                                role = Role.Switch,
                                                onValueChange = {
                                                    onToggleAppBlocked(app.packageName, it)
                                                }
                                            )
                                            .padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            if (appIcon != null) {
                                                Image(
                                                    bitmap = appIcon,
                                                    contentDescription = null,
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
                                                        style = MaterialTheme.typography.titleMedium.copy(
                                                            color = FocusColors.Primary
                                                        )
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
                                                        text = stringResource(R.string.session_apps_limit_set),
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            color = FocusColors.Primary
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        Switch(
                                            checked = isBlocked,
                                            onCheckedChange = null,
                                            colors = FocusColors.SwitchColors,
                                            modifier = Modifier.clearAndSetSemantics { }
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

        SpecialAppRadioRow(
            text = stringResource(R.string.session_apps_block_all),
            isSelected = selectedOption == SpecialAppOption.BLOCK_COMPLETELY,
            onClick = { onOptionSelect(SpecialAppOption.BLOCK_COMPLETELY) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        SpecialAppRadioRow(
            text = stringResource(R.string.session_apps_allow_all),
            isSelected = selectedOption == SpecialAppOption.ALLOW_COMPLETELY,
            onClick = { onOptionSelect(SpecialAppOption.ALLOW_COMPLETELY) }
        )

        Spacer(modifier = Modifier.height(10.dp))

        SpecialAppRadioRow(
            text = stringResource(R.string.session_apps_study_mode),
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
    isSelected: Boolean,
    onClick: () -> Unit,
    subtext: String? = null,
    onSubtextClick: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FocusShapes.small)
                .selectable(
                    selected = isSelected,
                    role = Role.RadioButton,
                    onClick = onClick
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = FocusColors.TextPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 16.sp
                ),
                modifier = Modifier.weight(1f)
            )

            RadioButton(
                selected = isSelected,
                onClick = null,
                colors = RadioButtonDefaults.colors(
                    selectedColor = FocusColors.Primary,
                    unselectedColor = FocusColors.TextMuted
                ),
                modifier = Modifier.clearAndSetSemantics { }
            )
        }

        if (subtext != null) {
            if (onSubtextClick != null) {
                TextButton(
                    onClick = onSubtextClick,
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = subtext,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.Primary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 13.sp
                    ),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
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
