package com.example.feature.session

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.design.rememberFocusInfiniteFloat
import com.example.core.permission.FocusPermissionManager
import com.example.core.ui.FocusLottieAnimation
import com.example.core.ui.sessionModeTitle
import com.example.core.util.TimeFormatter
import com.example.data.model.SessionMode
import com.example.feature.session.domain.PomodoroPhase
import com.example.feature.session.ui.BreakRequestDialog
import com.example.feature.session.ui.ProtectionSetupDialog

/**
 * Active session screen. The copy distinguishes the session's own strict lock from the general app
 * shields, because Strict Mode locks pausing/ending while breaks stay available.
 */
@Composable
fun ActiveSessionScreen(
    viewModel: SessionViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToSummary: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.activeSessionState.collectAsStateWithLifecycle()
    val pendingSummary by viewModel.pendingSessionSummary.collectAsStateWithLifecycle()
    val pendingScratchCard by viewModel.pendingScratchCard.collectAsStateWithLifecycle()
    var showEndSessionDialog by remember { mutableStateOf(false) }
    var showStrictLockDialog by remember { mutableStateOf(false) }
    var showBreakDialog by remember { mutableStateOf(false) }
    var showProtectionSetupDialog by remember { mutableStateOf(false) }
    var showFloatingOverlayPermissionDialog by remember { mutableStateOf(false) }
    val isFloatingOverlayShowing by
        com.example.feature.timer.FloatingTimerService.isOverlayShowing.collectAsStateWithLifecycle()

    // When the summary is prepared (upon ending or completing), navigate to the review screen.
    LaunchedEffect(pendingSummary) {
        if (pendingSummary != null) {
            onNavigateToSummary()
        }
    }

    val pulseScale by rememberFocusInfiniteFloat(
        initialValue = 1.0f,
        targetValue = 1.03f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val context = LocalContext.current
    var permissionStatus by remember {
        mutableStateOf(FocusPermissionManager.getPermissionStatus(context))
    }

    val requiresBlocking = uiState.isAppBlockingEnabled || uiState.isStudyChannelsEnabled
    val refreshPermissions = {
        permissionStatus = FocusPermissionManager.getPermissionStatus(
            context,
            requiresBlocking = requiresBlocking
        )
    }

    val hasMandatoryPermissions =
        !requiresBlocking || (permissionStatus.isOverlayGranted && permissionStatus.isAccessibilityEnabled)

    val isStrictSession =
        uiState.isStrictModeEnabled && uiState.mode != SessionMode.STOPWATCH
    val isStrictFocusPhaseLocked = isStrictSession &&
        uiState.mode == SessionMode.POMODORO &&
        uiState.pomodoroPhase == PomodoroPhase.FOCUS

    val handleSafeNavigateBack: () -> Unit = {
        if (uiState.isSessionCompleted) {
            viewModel.resetToIdle(force = true)
        } else if (uiState.isSessionRunning || uiState.isSessionPaused || uiState.isBreakActive) {
            com.example.feature.session.engine.FocusSessionManager.instance
                .saveActiveSessionToDisk(context)
            com.example.feature.session.service.FocusSessionForegroundService.start(context)
        }
        onNavigateBack()
    }

    // One back handler owns every layer: close the topmost dialog first, then leave the screen.
    androidx.activity.compose.BackHandler(enabled = true) {
        when {
            showFloatingOverlayPermissionDialog -> showFloatingOverlayPermissionDialog = false
            showStrictLockDialog -> showStrictLockDialog = false
            showEndSessionDialog -> showEndSessionDialog = false
            showBreakDialog -> showBreakDialog = false
            showProtectionSetupDialog -> showProtectionSetupDialog = false
            else -> handleSafeNavigateBack()
        }
    }

    if (showProtectionSetupDialog) {
        ProtectionSetupDialog(
            permissionStatus = permissionStatus,
            onRefreshPermissions = refreshPermissions,
            onConfirmStart = { showProtectionSetupDialog = false },
            onStartWithoutBlocking = { showProtectionSetupDialog = false },
            onDismiss = { showProtectionSetupDialog = false },
            confirmLabel = stringResource(R.string.session_active_permission_done),
            dismissActionLabel = stringResource(R.string.action_close)
        )
    }

    if (showFloatingOverlayPermissionDialog) {
        com.example.feature.timer.FloatingOverlayPermissionDialog(
            onDismiss = { showFloatingOverlayPermissionDialog = false },
            onPermissionGranted = {
                showFloatingOverlayPermissionDialog = false
                refreshPermissions()
                com.example.feature.timer.FloatingTimerService.start(context, forceShow = true)
            }
        )
    }

    if (showBreakDialog) {
        BreakRequestDialog(
            isStrictModeEnabled = isStrictSession,
            onDismiss = { showBreakDialog = false },
            onConfirmBreak = { minutes ->
                showBreakDialog = false
                viewModel.startBreak(minutes)
            },
            isBlockingEnabled = requiresBlocking && hasMandatoryPermissions
        )
    }

    if (showStrictLockDialog) {
        AlertDialog(
            onDismissRequest = { showStrictLockDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = FocusColors.CoralWarning,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.session_active_strict_lock_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.session_active_strict_lock_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusColors.TextSecondary,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { showStrictLockDialog = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FocusColors.Primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dismiss_strict_lock_dialog_button")
                ) {
                    Text(
                        text = stringResource(R.string.session_active_strict_lock_action),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }

    if (showEndSessionDialog) {
        AlertDialog(
            onDismissRequest = { showEndSessionDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.session_active_end_title),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    )
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.session_active_end_body),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEndSessionDialog = false
                        viewModel.endSession()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = FocusColors.CoralText
                    ),
                    modifier = Modifier.testTag("confirm_end_session_button")
                ) {
                    Text(
                        text = stringResource(R.string.session_active_end_confirm),
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEndSessionDialog = false },
                    modifier = Modifier.testTag("dismiss_end_session_button")
                ) {
                    Text(
                        text = stringResource(R.string.session_active_end_cancel),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }

    val statusBadge = when {
        uiState.isSessionCompleted ->
            stringResource(R.string.session_active_status_completed) to FocusColors.EmeraldText
        uiState.isBreakActive ->
            stringResource(R.string.session_active_status_break) to FocusColors.CyanText
        uiState.isSessionPaused ->
            stringResource(R.string.session_active_status_paused) to FocusColors.AmberText
        uiState.mode == SessionMode.POMODORO -> when (uiState.pomodoroPhase) {
            PomodoroPhase.FOCUS ->
                stringResource(R.string.session_active_status_pomodoro_focus) to FocusColors.EmeraldText
            PomodoroPhase.SHORT_BREAK ->
                stringResource(R.string.session_active_status_short_break) to FocusColors.CyanText
            PomodoroPhase.LONG_BREAK ->
                stringResource(R.string.session_active_status_long_break) to FocusColors.CyanText
            null ->
                stringResource(R.string.session_active_status_pomodoro) to FocusColors.EmeraldText
        }
        uiState.mode == SessionMode.STOPWATCH ->
            stringResource(R.string.session_active_status_stopwatch) to FocusColors.CyanText
        else ->
            stringResource(R.string.session_active_status_focus) to FocusColors.EmeraldText
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        horizontal = FocusSpacing.screenHorizontal,
                        vertical = FocusSpacing.sm
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Leaving this screen keeps the session running in the background, so the
                // navigation control is never presented as locked.
                IconButton(
                    onClick = handleSafeNavigateBack,
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.session_active_exit),
                        tint = FocusColors.TextPrimary
                    )
                }

                Crossfade(
                    targetState = statusBadge,
                    animationSpec = tween(350),
                    label = "badge_crossfade"
                ) { (badgeText, badgeColor) ->
                    Box(
                        modifier = Modifier
                            .clip(FocusShapes.pill)
                            .background(badgeColor.copy(alpha = 0.12f))
                            .border(1.dp, badgeColor.copy(alpha = 0.3f), FocusShapes.pill)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(badgeColor)
                            )
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = badgeColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (!uiState.isSessionCompleted) {
                    IconButton(
                        onClick = {
                            if (!FocusPermissionManager.isOverlayPermissionGranted(context)) {
                                showFloatingOverlayPermissionDialog = true
                            } else if (isFloatingOverlayShowing) {
                                // In a strict session the floating timer cannot be hidden.
                                if (isStrictSession) {
                                    showStrictLockDialog = true
                                } else {
                                    com.example.feature.timer.FloatingTimerService.stop(context)
                                }
                            } else {
                                com.example.feature.timer.FloatingTimerService.start(
                                    context,
                                    forceShow = true
                                )
                            }
                        },
                        modifier = Modifier.testTag("toggle_floating_timer_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PictureInPictureAlt,
                            contentDescription = stringResource(
                                if (isFloatingOverlayShowing) {
                                    R.string.session_active_hide_floating
                                } else {
                                    R.string.session_active_show_floating
                                }
                            ),
                            tint = if (isFloatingOverlayShowing) {
                                FocusColors.Primary
                            } else {
                                FocusColors.TextSecondary
                            }
                        )
                    }
                } else {
                    Box(modifier = Modifier.size(40.dp))
                }
            }
        },
        containerColor = FocusColors.Background,
        modifier = modifier
            .navigationBarsPadding()
            .testTag("active_session_screen")
    ) { innerPadding ->
        Crossfade(
            targetState = when {
                uiState.isSessionCompleted -> "COMPLETED"
                uiState.isBreakActive -> "BREAK"
                else -> "RUNNING"
            },
            animationSpec = tween(400),
            label = "screen_view_state_crossfade"
        ) { viewState ->
            when (viewState) {
                "COMPLETED" -> {
                    SessionCompletedView(
                        uiState = uiState,
                        isProtectionActive = requiresBlocking && hasMandatoryPermissions,
                        onReturnHome = {
                            viewModel.resetToIdle()
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = FocusSpacing.screenHorizontal)
                    )

                    pendingScratchCard?.let { card ->
                        com.example.feature.rewards.scratch.ScratchCardDialog(
                            card = card,
                            onRevealed = { viewModel.onScratchCardRevealed() },
                            onDismiss = { viewModel.dismissScratchCard() }
                        )
                    }
                }

                "BREAK" -> {
                    BreakActiveView(
                        uiState = uiState,
                        isProtectionActive = requiresBlocking && hasMandatoryPermissions,
                        onEndBreakEarly = { viewModel.endBreakEarly() },
                        onEndSession = {
                            if (isStrictSession) {
                                showStrictLockDialog = true
                            } else {
                                showEndSessionDialog = true
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = FocusSpacing.screenHorizontal)
                    )
                }

                else -> {
                    RunningSessionView(
                        uiState = uiState,
                        requiresBlocking = requiresBlocking,
                        hasMandatoryPermissions = hasMandatoryPermissions,
                        isStrictSession = isStrictSession,
                        isStrictFocusPhaseLocked = isStrictFocusPhaseLocked,
                        pulseScale = pulseScale,
                        onOpenPermissionHelp = {
                            refreshPermissions()
                            showProtectionSetupDialog = true
                        },
                        onTakeBreak = { showBreakDialog = true },
                        onRecordLap = { viewModel.recordLap() },
                        onSkipPhase = {
                            if (isStrictFocusPhaseLocked) {
                                showStrictLockDialog = true
                            } else {
                                viewModel.skipPomodoroPhase()
                            }
                        },
                        onTogglePause = {
                            if (uiState.isSessionPaused) {
                                viewModel.resumeSession()
                            } else {
                                viewModel.pauseSession()
                            }
                        },
                        onShowStrictLock = { showStrictLockDialog = true },
                        onEndSession = { showEndSessionDialog = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
private fun RunningSessionView(
    uiState: ActiveSessionUiState,
    requiresBlocking: Boolean,
    hasMandatoryPermissions: Boolean,
    isStrictSession: Boolean,
    isStrictFocusPhaseLocked: Boolean,
    pulseScale: Float,
    onOpenPermissionHelp: () -> Unit,
    onTakeBreak: () -> Unit,
    onRecordLap: () -> Unit,
    onSkipPhase: () -> Unit,
    onTogglePause: () -> Unit,
    onShowStrictLock: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = FocusSpacing.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        val hasHeaderPills = uiState.subject.isNotBlank() ||
            isStrictSession ||
            uiState.isStudyChannelsEnabled ||
            uiState.mode == SessionMode.POMODORO
        val hasHeaderContext = hasHeaderPills ||
            uiState.topic.isNotBlank() ||
            uiState.goal.isNotBlank()

        if (hasHeaderContext) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (hasHeaderPills) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (uiState.subject.isNotBlank()) {
                            Box(
                                modifier = Modifier
                                    .clip(FocusShapes.pill)
                                    .background(FocusColors.PrimaryContainer)
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = uiState.subject,
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = FocusColors.Primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (isStrictSession) {
                            Box(
                                modifier = Modifier
                                    .clip(FocusShapes.pill)
                                    .background(FocusColors.CoralWarning.copy(alpha = 0.12f))
                                    .border(
                                        1.dp,
                                        FocusColors.CoralWarning.copy(alpha = 0.3f),
                                        FocusShapes.pill
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Security,
                                        contentDescription = null,
                                        tint = FocusColors.CoralWarning,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.session_active_strict_pill),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = FocusColors.CoralText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                    )
                                }
                            }
                        }

                        if (uiState.isStudyChannelsEnabled) {
                            Box(
                                modifier = Modifier
                                    .clip(FocusShapes.pill)
                                    .background(FocusColors.EmeraldSuccess.copy(alpha = 0.12f))
                                    .border(
                                        1.dp,
                                        FocusColors.EmeraldSuccess.copy(alpha = 0.3f),
                                        FocusShapes.pill
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SmartDisplay,
                                        contentDescription = null,
                                        tint = FocusColors.EmeraldSuccess,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.session_active_study_pill),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = FocusColors.EmeraldText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                    )
                                }
                            }
                        }

                        if (uiState.mode == SessionMode.POMODORO) {
                            Crossfade(
                                targetState = uiState.currentCycle to uiState.totalCycles,
                                animationSpec = tween(350),
                                label = "pomodoro_cycle_crossfade"
                            ) { (cycle, totalCycles) ->
                                Box(
                                    modifier = Modifier
                                        .clip(FocusShapes.pill)
                                        .background(FocusColors.AmberOrange.copy(alpha = 0.15f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = stringResource(
                                            R.string.session_active_cycle,
                                            cycle,
                                            totalCycles
                                        ),
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = FocusColors.AmberText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                if (uiState.topic.isNotBlank()) {
                    if (hasHeaderPills) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = uiState.topic,
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary,
                            fontSize = 24.sp
                        ),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (uiState.goal.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.goal,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontSize = 13.sp
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val phaseStatus = when {
            uiState.isSessionPaused ->
                stringResource(R.string.session_active_phase_paused) to FocusColors.AmberText
            uiState.mode == SessionMode.POMODORO -> when (uiState.pomodoroPhase) {
                PomodoroPhase.FOCUS ->
                    stringResource(R.string.session_active_phase_focusing) to FocusColors.EmeraldText
                PomodoroPhase.SHORT_BREAK ->
                    stringResource(R.string.session_active_phase_short_rest) to FocusColors.CyanText
                PomodoroPhase.LONG_BREAK ->
                    stringResource(R.string.session_active_phase_long_rest) to FocusColors.CyanText
                null ->
                    stringResource(R.string.session_active_phase_studying) to FocusColors.EmeraldText
            }
            uiState.mode == SessionMode.STOPWATCH ->
                stringResource(R.string.session_active_phase_recording) to FocusColors.CyanText
            else ->
                stringResource(R.string.session_active_phase_studying) to FocusColors.EmeraldText
        }

        Box(
            modifier = Modifier
                .size(260.dp)
                .scale(if (uiState.isSessionRunning && !uiState.isSessionPaused) pulseScale else 1f),
            contentAlignment = Alignment.Center
        ) {
            val progressSweep = when (uiState.mode) {
                SessionMode.TIMER -> (1f - uiState.progress) * 360f
                SessionMode.POMODORO -> (1f - uiState.progress) * 360f
                SessionMode.STOPWATCH -> uiState.progress * 360f
            }

            val arcBrush = when (uiState.mode) {
                SessionMode.POMODORO -> {
                    if (uiState.pomodoroPhase == PomodoroPhase.SHORT_BREAK ||
                        uiState.pomodoroPhase == PomodoroPhase.LONG_BREAK
                    ) {
                        Brush.sweepGradient(
                            listOf(FocusColors.CyanBlue, FocusColors.EmeraldSuccess, FocusColors.CyanBlue)
                        )
                    } else {
                        Brush.sweepGradient(
                            listOf(
                                FocusColors.PrimaryLight,
                                FocusColors.Primary,
                                FocusColors.PrimaryDark,
                                FocusColors.PrimaryLight
                            )
                        )
                    }
                }
                SessionMode.STOPWATCH -> Brush.sweepGradient(
                    listOf(FocusColors.CyanBlue, FocusColors.Primary, FocusColors.CyanBlue)
                )
                SessionMode.TIMER -> Brush.sweepGradient(
                    listOf(
                        FocusColors.PrimaryLight,
                        FocusColors.Primary,
                        FocusColors.PrimaryDark,
                        FocusColors.PrimaryLight
                    )
                )
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 10.dp.toPx()
                drawCircle(
                    color = Color(0xFFE8E9F3),
                    style = Stroke(width = strokeWidth)
                )
                if (progressSweep > 0f) {
                    drawArc(
                        brush = arcBrush,
                        startAngle = -90f,
                        sweepAngle = progressSweep,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = uiState.activeTimeString,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.TextPrimary,
                        fontSize = if (uiState.activeTimeString.length > 5) 42.sp else 48.sp,
                        letterSpacing = 1.sp,
                        fontFeatureSettings = "tnum"
                    ),
                    modifier = Modifier.testTag("active_timer_value")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Crossfade(
                    targetState = phaseStatus,
                    animationSpec = tween(350),
                    label = "phase_status_crossfade"
                ) { (statusText, statusColor) ->
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 2.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.mode == SessionMode.STOPWATCH && uiState.laps.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FocusShapes.medium)
                    .background(FocusColors.Surface)
                    .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.medium)
                    .padding(FocusSpacing.base)
            ) {
                Text(
                    text = stringResource(
                        R.string.session_active_laps_title,
                        uiState.laps.size
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.TextSecondary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    uiState.laps.takeLast(3).reversed().forEach { lap ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.session_active_lap, lap.lapNumber),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = FocusColors.TextPrimary
                                )
                            )
                            Text(
                                text = "+${lap.formattedLapTime}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.Primary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                            Text(
                                text = lap.formattedTotalTime,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextSecondary
                                )
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (requiresBlocking && !hasMandatoryPermissions) {
            OutlinedButton(
                onClick = onOpenPermissionHelp,
                shape = FocusShapes.medium,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    FocusColors.CoralWarning.copy(alpha = 0.4f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = FocusColors.CoralWarning.copy(alpha = 0.12f),
                    contentColor = FocusColors.CoralText
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = FocusSpacing.base,
                    vertical = 10.dp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("permission_warning_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = FocusColors.CoralWarning,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.session_active_permission_warning),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.CoralText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(FocusShapes.medium)
                    .background(FocusColors.Surface)
                    .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.medium)
                    .padding(horizontal = FocusSpacing.base, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isStrictSession) {
                            Icons.Rounded.Security
                        } else {
                            Icons.Rounded.Shield
                        },
                        contentDescription = null,
                        tint = if (isStrictSession) {
                            FocusColors.CoralWarning
                        } else {
                            FocusColors.Primary
                        },
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(
                            when {
                                isStrictSession && requiresBlocking ->
                                    R.string.session_active_strict_active
                                isStrictSession ->
                                    R.string.session_active_strict_timer_only
                                requiresBlocking ->
                                    R.string.session_active_protection_active
                                else ->
                                    R.string.session_active_blocking_off
                            }
                        ),
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = FocusSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onTakeBreak,
                    shape = FocusShapes.large,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        FocusColors.CyanBlue.copy(alpha = 0.5f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FocusColors.CyanText
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 46.dp)
                        .testTag("take_break_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Coffee,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.session_active_take_break),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        )
                    }
                }

                if (uiState.mode == SessionMode.STOPWATCH) {
                    Button(
                        onClick = onRecordLap,
                        shape = FocusShapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FocusColors.CyanText,
                            contentColor = MaterialTheme.colorScheme.background
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                            .testTag("record_lap_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.session_active_record_lap),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            )
                        }
                    }
                } else if (uiState.mode == SessionMode.POMODORO) {
                    OutlinedButton(
                        onClick = onSkipPhase,
                        shape = FocusShapes.large,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            FocusColors.Primary.copy(alpha = 0.5f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = FocusColors.Primary
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp)
                            .testTag("skip_pomodoro_phase_button")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isStrictFocusPhaseLocked) {
                                    Icons.Outlined.Lock
                                } else {
                                    Icons.Default.SkipNext
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            if (isStrictFocusPhaseLocked) {
                                Text(
                                    text = stringResource(R.string.session_active_skip_locked),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                )
                            } else {
                                Crossfade(
                                    targetState = uiState.pomodoroPhase == PomodoroPhase.FOCUS,
                                    animationSpec = tween(300),
                                    label = "skip_phase_text_crossfade"
                                ) { isFocusPhase ->
                                    Text(
                                        text = stringResource(
                                            if (isFocusPhase) {
                                                R.string.session_active_skip
                                            } else {
                                                R.string.session_active_next_focus
                                            }
                                        ),
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 14.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isStrictSession) {
                OutlinedButton(
                    onClick = onShowStrictLock,
                    shape = FocusShapes.large,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        FocusColors.Primary.copy(alpha = 0.3f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = FocusColors.Primary.copy(alpha = 0.15f),
                        contentColor = FocusColors.Primary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.session_active_strict_in_progress),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.Primary,
                                fontSize = 16.sp
                            )
                        )
                    }
                }
            } else {
                Button(
                    onClick = onTogglePause,
                    shape = FocusShapes.large,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FocusColors.Primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .testTag("toggle_pause_button")
                ) {
                    Crossfade(
                        targetState = uiState.isSessionPaused,
                        animationSpec = tween(300),
                        label = "pause_resume_button_crossfade"
                    ) { isPaused ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isPaused) {
                                    Icons.Default.PlayArrow
                                } else {
                                    Icons.Default.Pause
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(
                                    if (isPaused) {
                                        R.string.session_active_resume
                                    } else {
                                        R.string.session_active_pause
                                    }
                                ),
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            )
                        }
                    }
                }
            }

            if (isStrictSession) {
                OutlinedButton(
                    onClick = onShowStrictLock,
                    shape = FocusShapes.large,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        FocusColors.CoralWarning.copy(alpha = 0.4f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FocusColors.CoralText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = FocusColors.CoralWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.session_active_end_locked),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.CoralText,
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onEndSession,
                    shape = FocusShapes.large,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        FocusColors.CoralWarning.copy(alpha = 0.5f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FocusColors.CoralText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("end_session_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.session_active_end),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * High-visibility temporary break countdown screen.
 */
@Composable
private fun BreakActiveView(
    uiState: ActiveSessionUiState,
    isProtectionActive: Boolean,
    onEndBreakEarly: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isStrictSession = uiState.isStrictModeEnabled && uiState.mode != SessionMode.STOPWATCH

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .clip(FocusShapes.pill)
                    .background(FocusColors.CyanBlue.copy(alpha = 0.15f))
                    .border(1.dp, FocusColors.CyanBlue.copy(alpha = 0.4f), FocusShapes.pill)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Coffee,
                        contentDescription = null,
                        tint = FocusColors.CyanBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.session_active_break_title),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = FocusColors.CyanText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = if (uiState.subject.isNotBlank()) {
                    stringResource(R.string.session_active_break_resting, uiState.subject)
                } else {
                    stringResource(R.string.session_active_break_generic)
                },
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary,
                    fontSize = 22.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(
                    if (isProtectionActive) {
                        R.string.session_active_break_body
                    } else {
                        R.string.session_active_break_body_no_blocking
                    }
                ),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = FocusColors.TextSecondary,
                    fontSize = 13.sp
                ),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        val cyanColor = FocusColors.CyanBlue
        val cyanBg = FocusColors.CyanLight.copy(alpha = 0.3f)

        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 10.dp.toPx()
                drawCircle(color = cyanBg, style = Stroke(width = strokeWidth))
                drawCircle(
                    color = cyanColor,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = uiState.breakRemainingFormatted,
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = FocusColors.CyanText,
                        fontSize = 46.sp,
                        letterSpacing = 1.sp,
                        fontFeatureSettings = "tnum"
                    ),
                    modifier = Modifier.testTag("break_timer_value")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.session_active_break_remaining),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.CyanText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(FocusShapes.medium)
                .background(FocusColors.Surface)
                .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.medium)
                .padding(FocusSpacing.base)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = FocusColors.CyanBlue,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = stringResource(
                        if (isProtectionActive) {
                            R.string.session_active_break_reengage
                        } else {
                            R.string.session_active_break_reengage_no_blocking
                        }
                    ),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 12.5.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = FocusSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onEndBreakEarly,
                shape = FocusShapes.large,
                colors = ButtonDefaults.buttonColors(
                    containerColor = FocusColors.Primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("return_to_focus_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.session_active_return_focus),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                }
            }

            if (isStrictSession) {
                OutlinedButton(
                    onClick = onEndSession,
                    shape = FocusShapes.large,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        FocusColors.CoralWarning.copy(alpha = 0.4f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FocusColors.CoralText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = FocusColors.CoralWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.session_active_end_locked),
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.CoralText,
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onEndSession,
                    shape = FocusShapes.large,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        FocusColors.CoralWarning.copy(alpha = 0.5f)
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = FocusColors.CoralText
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("end_session_from_break_button")
                ) {
                    Text(
                        text = stringResource(R.string.session_active_end),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp
                        )
                    )
                }
            }
        }
    }
}

/**
 * Session completion celebration view within Active Session screen.
 */
@Composable
private fun SessionCompletedView(
    uiState: ActiveSessionUiState,
    isProtectionActive: Boolean,
    onReturnHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val modeTitle = sessionModeTitle(uiState.mode)
    val isStrictSession = uiState.isStrictModeEnabled && uiState.mode != SessionMode.STOPWATCH

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        FocusLottieAnimation(
            rawRes = R.raw.focus_success,
            modifier = Modifier.size(120.dp),
            iterations = 1
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.session_active_completed_title),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = FocusColors.TextPrimary,
                fontSize = 24.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = stringResource(
                if (isProtectionActive) {
                    R.string.session_active_completed_body
                } else {
                    R.string.session_active_completed_body_no_blocking
                },
                modeTitle
            ),
            style = MaterialTheme.typography.bodyMedium.copy(
                color = FocusColors.TextSecondary,
                fontSize = 14.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 2.dp,
                    shape = FocusShapes.large,
                    ambientColor = Color.Black.copy(alpha = 0.03f)
                )
                .clip(FocusShapes.large)
                .background(FocusColors.Surface)
                .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.large)
                .padding(FocusSpacing.lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val subjectTopicSummary = listOf(uiState.subject, uiState.topic)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                if (subjectTopicSummary.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.session_active_completed_subject),
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary
                            )
                        )
                        Text(
                            text = subjectTopicSummary,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary
                            ),
                            textAlign = TextAlign.End,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }

                CompletionRow(
                    label = stringResource(R.string.session_active_completed_duration),
                    value = uiState.elapsedDurationFormatted,
                    valueColor = FocusColors.Primary
                )

                if (uiState.totalBreakDurationMillis > 0) {
                    CompletionRow(
                        label = stringResource(R.string.session_active_completed_break),
                        value = TimeFormatter.formatDigital(
                            uiState.totalBreakDurationMillis,
                            true
                        ),
                        valueColor = FocusColors.CyanText
                    )
                }

                CompletionRow(
                    label = stringResource(R.string.session_active_completed_mode),
                    value = modeTitle,
                    valueColor = FocusColors.TextPrimary
                )

                if (isStrictSession) {
                    CompletionRow(
                        label = stringResource(R.string.session_active_completed_protection),
                        value = stringResource(R.string.session_active_completed_strict),
                        valueColor = FocusColors.CoralText
                    )
                }

                if (uiState.mode == SessionMode.POMODORO) {
                    CompletionRow(
                        label = stringResource(R.string.session_active_completed_cycles),
                        value = stringResource(
                            R.string.session_active_completed_cycles_value,
                            uiState.totalCycles
                        ),
                        valueColor = FocusColors.EmeraldText
                    )
                }

                if (uiState.mode == SessionMode.STOPWATCH && uiState.laps.isNotEmpty()) {
                    CompletionRow(
                        label = stringResource(R.string.session_active_completed_laps),
                        value = stringResource(
                            R.string.session_active_completed_laps_value,
                            uiState.laps.size
                        ),
                        valueColor = FocusColors.CyanText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = onReturnHome,
            shape = FocusShapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = FocusColors.Primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .testTag("return_home_button")
        ) {
            Text(
                text = stringResource(R.string.session_active_completed_done),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun CompletionRow(
    label: String,
    value: String,
    valueColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                color = FocusColors.TextSecondary
            )
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.Bold,
                color = valueColor
            ),
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}
