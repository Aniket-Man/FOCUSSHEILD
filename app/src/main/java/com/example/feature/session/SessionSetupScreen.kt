package com.example.feature.session

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.ui.FocusPrimaryButton
import androidx.compose.ui.platform.LocalContext
import com.example.core.permission.FocusPermissionManager
import com.example.core.permission.ProtectionPermissionStatus
import com.example.feature.session.ui.ProtectionSetupDialog
import com.example.data.model.SessionMode

/**
 * Screen 4: Session Setup Screen.
 * Configures Focus settings (Duration, Subject, Topic, Goal, Pomodoro cycle options) and Protection shields.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SessionSetupScreen(
    viewModel: SessionViewModel,
    onNavigateBack: () -> Unit,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.setupState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showProtectionSetupDialog by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var permissionStatus by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(FocusPermissionManager.getPermissionStatus(context))
    }

    val refreshPermissions = {
        permissionStatus = FocusPermissionManager.getPermissionStatus(context, requiresBlocking = uiState.isAppBlockingEnabled || uiState.isStudyChannelsEnabled)
    }

    if (showProtectionSetupDialog) {
        ProtectionSetupDialog(
            permissionStatus = permissionStatus,
            onRefreshPermissions = refreshPermissions,
            onConfirmStart = {
                showProtectionSetupDialog = false
                val started = viewModel.startSession()
                if (started) {
                    onStartSession()
                }
            },
            onStartWithoutBlocking = {
                showProtectionSetupDialog = false
                viewModel.toggleAppBlocking(false)
                viewModel.toggleStudyChannels(false)
                val started = viewModel.startSession()
                if (started) {
                    onStartSession()
                }
            },
            onDismiss = { showProtectionSetupDialog = false }
        )
    }

    val durationOptions = listOf(25, 45, 60, 90, 120)
    val pomodoroFocusOptions = listOf(20, 25, 30, 45, 50)
    val pomodoroBreakOptions = listOf(3, 5, 10)
    val pomodoroLongBreakOptions = listOf(15, 20, 30)
    val cycleOptions = listOf(2, 3, 4, 6)

    val subjects = listOf("Physics", "Chemistry", "Mathematics", "Biology")
    val topicSuggestions = when (uiState.selectedSubject) {
        "Physics" -> listOf("Electrostatics", "Rotational Motion", "Ray Optics", "Thermodynamics")
        "Chemistry" -> listOf("Chemical Bonding", "Organic Reactions", "Coordination Compounds", "Equilibrium")
        "Mathematics" -> listOf("Calculus", "Coordinate Geometry", "Vectors & 3D", "Matrices")
        else -> listOf("Genetics", "Human Physiology", "Plant Biology", "Biomolecules")
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
                Spacer(modifier = Modifier.width(FocusSpacing.xs))
                Text(
                    text = "Session Setup",
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary
                    )
                )
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FocusColors.Surface)
                    .padding(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.base)
            ) {
                FocusPrimaryButton(
                    text = "START FOCUS SESSION",
                    onClick = {
                        val requiresProtection = uiState.isAppBlockingEnabled || uiState.isStudyChannelsEnabled
                        val hasMandatory = FocusPermissionManager.areMandatoryPermissionsGranted(context, requiresBlocking = requiresProtection)

                        if (requiresProtection && !hasMandatory) {
                            refreshPermissions()
                            showProtectionSetupDialog = true
                        } else {
                            val started = viewModel.startSession()
                            if (started) {
                                onStartSession()
                            }
                        }
                    },
                    testTag = "start_active_session_button"
                )
            }
        },
        containerColor = FocusColors.Background,
        modifier = modifier.testTag("session_setup_screen")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FocusSpacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(FocusSpacing.xl)
        ) {
            // Selected Mode indicator banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FocusShapes.medium)
                    .background(FocusColors.PrimaryContainer)
                    .padding(horizontal = FocusSpacing.base, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = uiState.selectedMode.icon,
                        contentDescription = null,
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Mode: ${uiState.selectedMode.title} (${uiState.selectedMode.subtitle})",
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                            color = FocusColors.OnPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            // Inline Validation Error Warning if any
            if (uiState.validationError != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(FocusShapes.medium)
                        .background(FocusColors.CoralWarning.copy(alpha = 0.12f))
                        .border(1.dp, FocusColors.CoralWarning.copy(alpha = 0.4f), FocusShapes.medium)
                        .padding(horizontal = FocusSpacing.base, vertical = 10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = "Warning",
                            tint = FocusColors.CoralWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = uiState.validationError ?: "",
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.CoralWarning,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            // GROUP 1: FOCUS CONFIGURATION
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "FOCUS CONFIGURATION",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )

                // 1. Mode Specific Duration Controls
                when (uiState.selectedMode) {
                    SessionMode.TIMER -> {
                        Column {
                            Text(
                                text = "Target Duration",
                                style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = FocusColors.TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                durationOptions.forEach { minutes ->
                                    val isSelected = uiState.durationMinutes == minutes
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setDuration(minutes) },
                                        label = { Text("${minutes}m") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = FocusColors.Primary,
                                            selectedLabelColor = Color.White,
                                            containerColor = FocusColors.Surface,
                                            labelColor = FocusColors.TextPrimary
                                        ),
                                        shape = FocusShapes.pill,
                                        border = FilterChipDefaults.filterChipBorder(
                                            enabled = true,
                                            selected = isSelected,
                                            borderColor = FocusColors.CardBorder,
                                            selectedBorderColor = FocusColors.Primary
                                        )
                                    )
                                }
                            }
                        }
                    }

                    SessionMode.STOPWATCH -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(FocusShapes.medium)
                                .background(FocusColors.Surface)
                                .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.medium)
                                .padding(horizontal = FocusSpacing.base, vertical = 12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = FocusColors.CyanBlue,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Stopwatch mode tracks open-ended study time. Record laps as you solve problems and stop whenever you are done.",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextSecondary,
                                        lineHeight = 16.sp
                                    )
                                )
                            }
                        }
                    }

                    SessionMode.POMODORO -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Focus Interval
                            Column {
                                Text(
                                    text = "Focus Interval",
                                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = FocusColors.TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pomodoroFocusOptions.forEach { minutes ->
                                        val isSelected = uiState.pomodoroFocusMinutes == minutes
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.setPomodoroConfig(
                                                    focusMinutes = minutes,
                                                    shortBreakMinutes = uiState.pomodoroShortBreakMinutes,
                                                    longBreakMinutes = uiState.pomodoroLongBreakMinutes,
                                                    cycles = uiState.pomodoroCycles
                                                )
                                            },
                                            label = { Text("${minutes}m Focus") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = FocusColors.Primary,
                                                selectedLabelColor = Color.White,
                                                containerColor = FocusColors.Surface,
                                                labelColor = FocusColors.TextPrimary
                                            ),
                                            shape = FocusShapes.pill,
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = isSelected,
                                                borderColor = FocusColors.CardBorder,
                                                selectedBorderColor = FocusColors.Primary
                                            )
                                        )
                                    }
                                }
                            }

                            // Short Break Interval
                            Column {
                                Text(
                                    text = "Short Break Interval",
                                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = FocusColors.TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pomodoroBreakOptions.forEach { minutes ->
                                        val isSelected = uiState.pomodoroShortBreakMinutes == minutes
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.setPomodoroConfig(
                                                    focusMinutes = uiState.pomodoroFocusMinutes,
                                                    shortBreakMinutes = minutes,
                                                    longBreakMinutes = uiState.pomodoroLongBreakMinutes,
                                                    cycles = uiState.pomodoroCycles
                                                )
                                            },
                                            label = { Text("${minutes}m Rest") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = FocusColors.EmeraldSuccess,
                                                selectedLabelColor = Color.White,
                                                containerColor = FocusColors.Surface,
                                                labelColor = FocusColors.TextPrimary
                                            ),
                                            shape = FocusShapes.pill,
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = isSelected,
                                                borderColor = FocusColors.CardBorder,
                                                selectedBorderColor = FocusColors.EmeraldSuccess
                                            )
                                        )
                                    }
                                }
                            }

                            // Total Cycles
                            Column {
                                Text(
                                    text = "Total Pomodoro Cycles",
                                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        color = FocusColors.TextPrimary
                                    )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    cycleOptions.forEach { count ->
                                        val isSelected = uiState.pomodoroCycles == count
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                viewModel.setPomodoroConfig(
                                                    focusMinutes = uiState.pomodoroFocusMinutes,
                                                    shortBreakMinutes = uiState.pomodoroShortBreakMinutes,
                                                    longBreakMinutes = uiState.pomodoroLongBreakMinutes,
                                                    cycles = count
                                                )
                                            },
                                            label = { Text("$count Cycles") },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = FocusColors.AmberOrange,
                                                selectedLabelColor = Color.White,
                                                containerColor = FocusColors.Surface,
                                                labelColor = FocusColors.TextPrimary
                                            ),
                                            shape = FocusShapes.pill,
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = isSelected,
                                                borderColor = FocusColors.CardBorder,
                                                selectedBorderColor = FocusColors.AmberOrange
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Subject Selection
                Column {
                    Text(
                        text = "Subject",
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = FocusColors.TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        subjects.forEach { subject ->
                            val isSelected = uiState.selectedSubject == subject
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    viewModel.setSubject(subject)
                                    viewModel.setTopic(
                                        when (subject) {
                                            "Physics" -> "Electrostatics"
                                            "Chemistry" -> "Chemical Bonding"
                                            "Mathematics" -> "Calculus"
                                            else -> "Genetics"
                                        }
                                    )
                                },
                                label = { Text(subject) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = FocusColors.Primary,
                                    selectedLabelColor = Color.White,
                                    containerColor = FocusColors.Surface,
                                    labelColor = FocusColors.TextPrimary
                                ),
                                shape = FocusShapes.pill,
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = FocusColors.CardBorder,
                                    selectedBorderColor = FocusColors.Primary
                                )
                            )
                        }
                    }
                }

                // 3. Topic Selection / Input
                Column {
                    Text(
                        text = "Topic",
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = FocusColors.TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.selectedTopic,
                        onValueChange = { viewModel.setTopic(it) },
                        placeholder = { Text("e.g. Electrostatics") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("topic_input_field"),
                        shape = FocusShapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FocusColors.Primary,
                            unfocusedBorderColor = FocusColors.CardBorder,
                            focusedContainerColor = FocusColors.Surface,
                            unfocusedContainerColor = FocusColors.Surface
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Topic quick suggestions chips
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        topicSuggestions.forEach { suggestion ->
                            Box(
                                modifier = Modifier
                                    .clip(FocusShapes.pill)
                                    .background(FocusColors.SurfaceVariant)
                                    .clickable { viewModel.setTopic(suggestion) }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = suggestion,
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextSecondary,
                                        fontSize = 11.5.sp
                                    )
                                )
                            }
                        }
                    }
                }

                // 4. Session Goal
                Column {
                    Text(
                        text = "Session Goal",
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = FocusColors.TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.sessionGoal,
                        onValueChange = { viewModel.setSessionGoal(it) },
                        placeholder = { Text("e.g. Solve 20 JEE Advanced questions") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("goal_input_field"),
                        shape = FocusShapes.medium,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FocusColors.Primary,
                            unfocusedBorderColor = FocusColors.CardBorder,
                            focusedContainerColor = FocusColors.Surface,
                            unfocusedContainerColor = FocusColors.Surface
                        )
                    )
                }
            }

            // GROUP 2: PROTECTION
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "FOCUS PROTECTION",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )

                // 1. Blocked Apps Switch
                ProtectionRow(
                    title = "Blocked Apps",
                    subtitle = "24 distracting apps shielded automatically",
                    icon = Icons.Rounded.Block,
                    iconColor = FocusColors.BlockedRed,
                    iconBackground = Color(0xFFFEECEB),
                    isChecked = uiState.isAppBlockingEnabled,
                    onCheckedChange = { viewModel.toggleAppBlocking(it) },
                    testTag = "blocked_apps_switch"
                )

                // 2. Strict Mode Switch
                ProtectionRow(
                    title = "Strict Mode",
                    subtitle = "Lockdown mode: no exit until session ends",
                    icon = Icons.Rounded.Lock,
                    iconColor = FocusColors.AmberOrange,
                    iconBackground = Color(0xFFFEF5E7),
                    isChecked = uiState.isStrictModeEnabled,
                    onCheckedChange = { viewModel.toggleStrictMode(it) },
                    testTag = "strict_mode_switch"
                )

                // 3. Study Channels Switch
                ProtectionRow(
                    title = "Approved Study Channels",
                    subtitle = "Allow educational YouTube channels only",
                    icon = Icons.Rounded.OndemandVideo,
                    iconColor = FocusColors.EmeraldSuccess,
                    iconBackground = Color(0xFFE8FAF3),
                    isChecked = uiState.isStudyChannelsEnabled,
                    onCheckedChange = { viewModel.toggleStudyChannels(it) },
                    testTag = "study_channels_switch"
                )
            }

            Spacer(modifier = Modifier.height(FocusSpacing.base))
        }
    }
}

@Composable
private fun ProtectionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconColor: Color,
    iconBackground: Color,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 1.dp,
                shape = FocusShapes.medium,
                ambientColor = Color.Black.copy(alpha = 0.02f),
                spotColor = Color.Black.copy(alpha = 0.03f)
            )
            .clip(FocusShapes.medium)
            .background(FocusColors.Surface)
            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.medium)
            .padding(horizontal = FocusSpacing.base, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(FocusSpacing.md))

                Column {
                    Text(
                        text = title,
                        style = androidx.compose.material3.MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary,
                            fontSize = 14.sp
                        )
                    )
                    Text(
                        text = subtitle,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 11.5.sp
                        )
                    )
                }
            }

            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = FocusColors.Primary,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = FocusColors.CardBorder
                ),
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}
