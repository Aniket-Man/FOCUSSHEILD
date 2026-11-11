package com.example.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OndemandVideo
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
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
import com.example.core.permission.FocusPermissionManager
import com.example.core.ui.FocusPrimaryButton
import com.example.core.ui.FocusScreenHeader
import com.example.core.ui.FocusStatusMessage
import com.example.core.ui.sessionModeSubtitle
import com.example.core.ui.sessionModeTitle
import com.example.data.model.SessionMode
import com.example.feature.session.ui.ProtectionSetupDialog

/**
 * Screen 4: Session Setup Screen.
 * Configures focus duration, subject, topic, goal and protection shields.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SessionSetupScreen(
    viewModel: SessionViewModel,
    onNavigateBack: () -> Unit,
    onStartSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.setupState.collectAsStateWithLifecycle()
    val customSubjects by viewModel.customSubjects.collectAsStateWithLifecycle()
    val isSavingProtection by viewModel.isSavingProtection.collectAsStateWithLifecycle()
    val protectionError by viewModel.protectionError.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showProtectionSetupDialog by remember { mutableStateOf(false) }
    var permissionStatus by remember {
        mutableStateOf(FocusPermissionManager.getPermissionStatus(context))
    }

    val refreshPermissions = {
        permissionStatus = FocusPermissionManager.getPermissionStatus(
            context,
            requiresBlocking = uiState.isAppBlockingEnabled || uiState.isStudyChannelsEnabled
        )
    }

    val isAccessibilityActive by AccessibilityHelper.isServiceEnabledFlow.collectAsStateWithLifecycle()
    LaunchedEffect(isAccessibilityActive) {
        refreshPermissions()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                AccessibilityHelper.updateState(context)
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
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
    val cycleOptions = listOf(2, 3, 4, 6)

    Scaffold(
        topBar = {
            FocusScreenHeader(
                title = stringResource(R.string.session_setup_title),
                onBack = onNavigateBack,
                modifier = Modifier.statusBarsPadding(),
                backButtonModifier = Modifier.testTag("back_button")
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FocusColors.Surface)
                    .navigationBarsPadding()
                    .padding(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.base)
            ) {
                FocusPrimaryButton(
                    text = stringResource(R.string.session_setup_start),
                    onClick = {
                        val requiresProtection =
                            uiState.isAppBlockingEnabled || uiState.isStudyChannelsEnabled
                        val hasMandatory = FocusPermissionManager.areMandatoryPermissionsGranted(
                            context,
                            requiresBlocking = requiresProtection
                        )

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
            // Selected mode indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FocusShapes.medium)
                    .background(FocusColors.PrimaryContainer)
                    .padding(horizontal = FocusSpacing.base, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = uiState.selectedMode.icon,
                        contentDescription = null,
                        tint = FocusColors.Primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(
                            R.string.session_setup_mode,
                            sessionModeTitle(uiState.selectedMode),
                            sessionModeSubtitle(uiState.selectedMode)
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = FocusColors.OnPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }

            FocusStatusMessage(
                message = protectionError,
                isError = true,
                onDismiss = { viewModel.clearProtectionError() }
            )

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
                            contentDescription = null,
                            tint = FocusColors.CoralWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = uiState.validationError.orEmpty(),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.CoralText,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }

            // GROUP 1: FOCUS CONFIGURATION
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = stringResource(R.string.session_setup_focus_configuration),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )

                when (uiState.selectedMode) {
                    SessionMode.TIMER -> {
                        Column {
                            Text(
                                text = stringResource(R.string.session_setup_target_duration),
                                style = MaterialTheme.typography.titleSmall.copy(
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
                                        label = {
                                            Text(
                                                stringResource(
                                                    R.string.session_setup_minutes_short,
                                                    minutes
                                                )
                                            )
                                        },
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
                                    text = stringResource(R.string.session_setup_stopwatch_body),
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.TextSecondary,
                                        lineHeight = 16.sp
                                    )
                                )
                            }
                        }
                    }

                    SessionMode.POMODORO -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column {
                                Text(
                                    text = stringResource(R.string.session_setup_focus_interval),
                                    style = MaterialTheme.typography.titleSmall.copy(
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
                                            label = {
                                                Text(
                                                    stringResource(
                                                        R.string.session_setup_focus_minutes,
                                                        minutes
                                                    )
                                                )
                                            },
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

                            Column {
                                Text(
                                    text = stringResource(R.string.session_setup_short_break_interval),
                                    style = MaterialTheme.typography.titleSmall.copy(
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
                                            label = {
                                                Text(
                                                    stringResource(
                                                        R.string.session_setup_break_minutes,
                                                        minutes
                                                    )
                                                )
                                            },
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

                            Column {
                                Text(
                                    text = stringResource(R.string.session_setup_total_cycles),
                                    style = MaterialTheme.typography.titleSmall.copy(
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
                                            label = {
                                                Text(
                                                    stringResource(
                                                        R.string.session_setup_cycles,
                                                        count
                                                    )
                                                )
                                            },
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

                if (customSubjects.isNotEmpty()) {
                    Column {
                        Text(
                            text = stringResource(R.string.session_setup_subject_optional),
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = FocusColors.TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            customSubjects.forEach { subjectEntity ->
                                val isSelected = uiState.selectedSubject.equals(
                                    subjectEntity.name,
                                    ignoreCase = true
                                )
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.toggleSubjectTag(subjectEntity.name) },
                                    label = { Text(subjectEntity.name) },
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

                Column {
                    Text(
                        text = stringResource(R.string.session_setup_topic_optional),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = FocusColors.TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.selectedTopic,
                        onValueChange = { viewModel.setTopic(it) },
                        placeholder = { Text(stringResource(R.string.session_setup_topic_example)) },
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
                }

                Column {
                    Text(
                        text = stringResource(R.string.session_setup_session_goal),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = FocusColors.TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.sessionGoal,
                        onValueChange = { viewModel.setSessionGoal(it) },
                        placeholder = { Text(stringResource(R.string.session_setup_goal_example)) },
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
                    text = stringResource(R.string.session_setup_focus_protection),
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )

                ProtectionRow(
                    title = stringResource(R.string.session_setup_blocked_apps),
                    subtitle = stringResource(R.string.session_setup_blocked_apps_body),
                    icon = Icons.Rounded.Block,
                    iconColor = FocusColors.BlockedRed,
                    iconBackground = Color(0xFFFEECEB),
                    isChecked = uiState.isAppBlockingEnabled,
                    enabled = !isSavingProtection,
                    onCheckedChange = { viewModel.toggleAppBlocking(it) },
                    testTag = "blocked_apps_switch"
                )

                // Strict Mode is not available for open-ended Stopwatch sessions.
                if (uiState.selectedMode != SessionMode.STOPWATCH) {
                    ProtectionRow(
                        title = stringResource(R.string.session_setup_strict_mode),
                        subtitle = stringResource(R.string.session_setup_strict_body),
                        icon = Icons.Rounded.Lock,
                        iconColor = FocusColors.AmberOrange,
                        iconBackground = Color(0xFFFEF5E7),
                        isChecked = uiState.isStrictModeEnabled,
                        enabled = !isSavingProtection,
                        onCheckedChange = { viewModel.toggleStrictMode(it) },
                        testTag = "strict_mode_switch"
                    )
                }

                ProtectionRow(
                    title = stringResource(R.string.session_setup_approved_channels),
                    subtitle = stringResource(R.string.session_setup_approved_channels_body),
                    icon = Icons.Rounded.OndemandVideo,
                    iconColor = FocusColors.EmeraldSuccess,
                    iconBackground = Color(0xFFE8FAF3),
                    isChecked = uiState.isStudyChannelsEnabled,
                    enabled = !isSavingProtection,
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
    enabled: Boolean,
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
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = isChecked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                )
                .padding(horizontal = FocusSpacing.base, vertical = 14.dp)
                .testTag(testTag),
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
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(FocusSpacing.md))

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary,
                            fontSize = 14.sp
                        )
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 11.5.sp
                        )
                    )
                }
            }

            Switch(
                checked = isChecked,
                onCheckedChange = null,
                enabled = enabled,
                colors = FocusColors.SwitchColors,
                modifier = Modifier.clearAndSetSemantics { }
            )
        }
    }
}
