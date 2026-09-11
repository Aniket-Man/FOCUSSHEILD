package com.example.feature.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.example.R
import com.example.core.permission.FocusPermissionManager
import com.example.core.permission.ProtectionPermissionStatus
import com.example.core.ui.FocusLottieAnimation
import com.example.feature.session.ui.ProtectionSetupDialog
import com.example.core.design.FocusColors
import com.example.core.design.FocusShapes
import com.example.core.design.FocusSpacing
import com.example.core.util.TimeFormatter
import com.example.data.model.SessionMode
import com.example.feature.session.domain.PomodoroPhase
import com.example.feature.session.domain.SessionState
import com.example.feature.session.ui.BreakRequestDialog

/**
 * Screen: Active Session Screen with complete Break Management and Strict Mode enforcement.
 */
@Composable
fun ActiveSessionScreen(
    viewModel: SessionViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.activeSessionState.collectAsStateWithLifecycle()
    val pendingScratchCard by viewModel.pendingScratchCard.collectAsStateWithLifecycle()
    var showEndSessionDialog by remember { mutableStateOf(false) }
    var showStrictPauseDialog by remember { mutableStateOf(false) }
    var showStrictLockDialog by remember { mutableStateOf(false) }
    var showBreakDialog by remember { mutableStateOf(false) }
    var showProtectionSetupDialog by remember { mutableStateOf(false) }

    val hasOpenDialog = showProtectionSetupDialog || showBreakDialog || showStrictPauseDialog || showStrictLockDialog || showEndSessionDialog

    // 1. If any dialog is open, step back dismisses the topmost active dialog
    androidx.activity.compose.BackHandler(enabled = hasOpenDialog) {
        when {
            showStrictLockDialog -> showStrictLockDialog = false
            showEndSessionDialog -> showEndSessionDialog = false
            showStrictPauseDialog -> showStrictPauseDialog = false
            showBreakDialog -> showBreakDialog = false
            showProtectionSetupDialog -> showProtectionSetupDialog = false
        }
    }

    // Subtle breathing pulse animation for active focus ring
    val infiniteTransition = rememberInfiniteTransition(label = "focus_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val context = LocalContext.current
    var permissionStatus by remember {
        mutableStateOf(FocusPermissionManager.getPermissionStatus(context))
    }

    val refreshPermissions = {
        permissionStatus = FocusPermissionManager.getPermissionStatus(context, requiresBlocking = uiState.isAppBlockingEnabled || uiState.isStudyChannelsEnabled)
    }

    val requiresBlocking = uiState.isAppBlockingEnabled || uiState.isStudyChannelsEnabled
    val hasMandatoryPermissions = !requiresBlocking || (permissionStatus.isOverlayGranted && permissionStatus.isAccessibilityEnabled)

    if (showProtectionSetupDialog) {
        ProtectionSetupDialog(
            permissionStatus = permissionStatus,
            onRefreshPermissions = refreshPermissions,
            onConfirmStart = { showProtectionSetupDialog = false },
            onStartWithoutBlocking = { showProtectionSetupDialog = false },
            onDismiss = { showProtectionSetupDialog = false }
        )
    }

    // Break Request Dialog
    if (showBreakDialog) {
        BreakRequestDialog(
            isStrictModeEnabled = uiState.isStrictModeEnabled,
            onDismiss = { showBreakDialog = false },
            onConfirmBreak = { minutes ->
                showBreakDialog = false
                viewModel.startBreak(minutes)
            }
        )
    }

    // Strict Mode Pause Confirmation Dialog
    if (showStrictPauseDialog) {
        AlertDialog(
            onDismissRequest = { showStrictPauseDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = FocusColors.AmberOrange,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "Pause Strict Session?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "Strict Mode is active. Staying in continuous deep focus helps build strong study discipline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = FocusColors.TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStrictPauseDialog = false
                        viewModel.pauseSession()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FocusColors.AmberOrange,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("confirm_strict_pause_button")
                ) {
                    Text("Pause Anyway")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStrictPauseDialog = false },
                    modifier = Modifier.testTag("dismiss_strict_pause_button")
                ) {
                    Text("Keep Focusing", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Strict Mode Non-Dismissible / Locked Information Dialog
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
                    text = "Strict Mode Locked",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    text = "In Strict Mode, the session cannot be ended early even with discipline confirmation. The session will stay active until the timer completes.",
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
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dismiss_strict_lock_dialog_button")
                ) {
                    Text("Stay Focused", fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // End Session Confirmation Dialog
    if (showEndSessionDialog) {
        val isStrict = uiState.isStrictModeEnabled && uiState.mode != SessionMode.STOPWATCH
        if (isStrict) {
            showEndSessionDialog = false
            showStrictLockDialog = true
        } else {
            AlertDialog(
                onDismissRequest = { showEndSessionDialog = false },
                title = {
                    Text(
                        text = "End Focus Session?",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                text = {
                    Text(
                        text = "Are you sure you want to end this study session? Your focus time will be saved.",
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
                            contentColor = FocusColors.CoralWarning
                        ),
                        modifier = Modifier.testTag("confirm_end_session_button")
                    ) {
                        Text("End Session", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showEndSessionDialog = false },
                        modifier = Modifier.testTag("dismiss_end_session_button")
                    ) {
                        Text("Continue Studying", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = FocusSpacing.base, vertical = FocusSpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = {
                        if (uiState.isSessionCompleted || (!uiState.isSessionRunning && !uiState.isBreakActive)) {
                            viewModel.resetToIdle()
                        }
                        onNavigateBack()
                    },
                    modifier = Modifier.testTag("back_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isStrictModeEnabled && !uiState.isSessionCompleted && uiState.mode != SessionMode.STOPWATCH) Icons.Outlined.Lock else Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Exit Session",
                        tint = if (uiState.isStrictModeEnabled && !uiState.isSessionCompleted && uiState.mode != SessionMode.STOPWATCH) FocusColors.CoralWarning else FocusColors.TextPrimary
                    )
                }

                // FocusShield Active Status Badge with Crossfade
                Crossfade(
                    targetState = when {
                        uiState.isSessionCompleted -> "Session Complete" to FocusColors.EmeraldSuccess
                        uiState.isBreakActive -> "Break Active" to FocusColors.CyanBlue
                        uiState.isSessionPaused -> "Session Paused" to FocusColors.AmberOrange
                        uiState.mode == SessionMode.POMODORO -> {
                            when (uiState.pomodoroPhase) {
                                PomodoroPhase.FOCUS -> "Pomodoro Focus" to FocusColors.EmeraldSuccess
                                PomodoroPhase.SHORT_BREAK -> "Short Break" to FocusColors.CyanBlue
                                PomodoroPhase.LONG_BREAK -> "Long Break" to FocusColors.CyanBlue
                                null -> "Pomodoro Active" to FocusColors.EmeraldSuccess
                            }
                        }
                        uiState.mode == SessionMode.STOPWATCH -> "Stopwatch Active" to FocusColors.CyanBlue
                        else -> "FocusShield Active" to FocusColors.EmeraldSuccess
                    },
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
                                )
                            )
                        }
                    }
                }

                Box(modifier = Modifier.size(40.dp)) // balance spacer
            }
        },
        containerColor = FocusColors.Background,
        modifier = modifier.navigationBarsPadding().testTag("active_session_screen")
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
                    // SESSION COMPLETED STATE VIEW
                    SessionCompletedView(
                        uiState = uiState,
                        onReturnHome = {
                            viewModel.resetToIdle()
                            onNavigateBack()
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = FocusSpacing.screenHorizontal)
                    )

                    // Scratch Card Reward for eligible completed sessions
                    pendingScratchCard?.let { card ->
                        com.example.feature.rewards.scratch.ScratchCardDialog(
                            card = card,
                            onRevealed = { viewModel.onScratchCardRevealed() },
                            onDismiss = { viewModel.dismissScratchCard() }
                        )
                    }
                }
                "BREAK" -> {
                    // BREAK ACTIVE VIEW
                    BreakActiveView(
                        uiState = uiState,
                        onEndBreakEarly = { viewModel.endBreakEarly() },
                        onEndSession = { showEndSessionDialog = true },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(horizontal = FocusSpacing.screenHorizontal)
                    )
                }
                else -> {
                    // ACTIVE RUNNING / PAUSED STATE VIEW
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = FocusSpacing.screenHorizontal),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                Spacer(modifier = Modifier.height(6.dp))

                // 1. Session Context (Subject & Topic & Mode)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Subject Pill
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
                                )
                            )
                        }

                        // Strict Mode Pill
                        if (uiState.isStrictModeEnabled) {
                            Box(
                                modifier = Modifier
                                    .clip(FocusShapes.pill)
                                    .background(FocusColors.CoralWarning.copy(alpha = 0.12f))
                                    .border(1.dp, FocusColors.CoralWarning.copy(alpha = 0.3f), FocusShapes.pill)
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
                                        text = "Strict Shield",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = FocusColors.CoralWarning,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                    )
                                }
                            }
                        }

                        // YouTube Study Mode Pill
                        if (uiState.isStudyChannelsEnabled) {
                            Box(
                                modifier = Modifier
                                    .clip(FocusShapes.pill)
                                    .background(FocusColors.EmeraldSuccess.copy(alpha = 0.12f))
                                    .border(1.dp, FocusColors.EmeraldSuccess.copy(alpha = 0.3f), FocusShapes.pill)
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
                                        text = "Study Mode",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            color = FocusColors.EmeraldSuccess,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.5.sp
                                        )
                                    )
                                }
                            }
                        }

                        // Pomodoro Cycle Pill (if Pomodoro) with Crossfade
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
                                        text = "Cycle $cycle of $totalCycles",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            color = FocusColors.AmberOrange,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.5.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

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

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Large Central Timer with Dynamic Progress Arc
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
                            if (uiState.pomodoroPhase == PomodoroPhase.SHORT_BREAK || uiState.pomodoroPhase == PomodoroPhase.LONG_BREAK) {
                                Brush.sweepGradient(
                                    listOf(FocusColors.CyanBlue, FocusColors.EmeraldSuccess, FocusColors.CyanBlue)
                                )
                            } else {
                                Brush.sweepGradient(
                                    listOf(FocusColors.PrimaryLight, FocusColors.Primary, FocusColors.PrimaryDark, FocusColors.PrimaryLight)
                                )
                            }
                        }
                        SessionMode.STOPWATCH -> Brush.sweepGradient(
                            listOf(FocusColors.CyanBlue, FocusColors.Primary, FocusColors.CyanBlue)
                        )
                        SessionMode.TIMER -> Brush.sweepGradient(
                            listOf(FocusColors.PrimaryLight, FocusColors.Primary, FocusColors.PrimaryDark, FocusColors.PrimaryLight)
                        )
                    }

                    // Canvas Progress Arc
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 10.dp.toPx()
                        // Track circle
                        drawCircle(
                            color = Color(0xFFE8E9F3),
                            style = Stroke(width = strokeWidth)
                        )
                        // Progress arc
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

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = uiState.activeTimeString,
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.TextPrimary,
                                fontSize = if (uiState.activeTimeString.length > 5) 42.sp else 48.sp,
                                letterSpacing = 1.sp
                            ),
                            modifier = Modifier.testTag("active_timer_value")
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Crossfade(
                            targetState = when {
                                uiState.isSessionPaused -> "PAUSED" to FocusColors.AmberOrange
                                uiState.mode == SessionMode.POMODORO -> {
                                    when (uiState.pomodoroPhase) {
                                        PomodoroPhase.FOCUS -> "FOCUSING" to FocusColors.EmeraldSuccess
                                        PomodoroPhase.SHORT_BREAK -> "SHORT REST" to FocusColors.CyanBlue
                                        PomodoroPhase.LONG_BREAK -> "LONG REST" to FocusColors.CyanBlue
                                        null -> "STUDYING" to FocusColors.EmeraldSuccess
                                    }
                                }
                                uiState.mode == SessionMode.STOPWATCH -> "RECORDING" to FocusColors.CyanBlue
                                else -> "STUDYING" to FocusColors.EmeraldSuccess
                            },
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

                // 3. Stopwatch Laps Display (if Stopwatch mode and laps exist)
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
                            text = "RECORDED LAPS (${uiState.laps.size})",
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
                                        text = "Lap ${lap.lapNumber}",
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

                // 4. Status Protection Information / Permission Warning
                if (requiresBlocking && !hasMandatoryPermissions) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(FocusShapes.medium)
                            .background(FocusColors.CoralWarning.copy(alpha = 0.12f))
                            .border(1.dp, FocusColors.CoralWarning.copy(alpha = 0.4f), FocusShapes.medium)
                            .clickable {
                                refreshPermissions()
                                showProtectionSetupDialog = true
                            }
                            .padding(horizontal = FocusSpacing.base, vertical = 10.dp)
                            .testTag("permission_warning_banner")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Security,
                                    contentDescription = "Permission Warning",
                                    tint = FocusColors.CoralWarning,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Protection inactive: Required permissions missing. Tap to enable.",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = FocusColors.CoralWarning,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(FocusShapes.pill)
                            .background(FocusColors.Surface)
                            .border(1.dp, FocusColors.CardBorderSubtle, FocusShapes.pill)
                            .padding(horizontal = FocusSpacing.base, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isStrictModeEnabled) Icons.Rounded.Security else Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = if (uiState.isStrictModeEnabled) FocusColors.CoralWarning else FocusColors.Primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = if (uiState.isStrictModeEnabled) "Strict Shield Active • Distractions Blocked" else "Focus Shield Active • Distractions Blocked",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.TextSecondary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 12.sp
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 5. Main Action Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = FocusSpacing.xl),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Secondary Actions Row: Take a Break / Lap / Skip Phase
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Take a Break Button
                        OutlinedButton(
                            onClick = { showBreakDialog = true },
                            shape = FocusShapes.large,
                            border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.CyanBlue.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = FocusColors.CyanBlue
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("take_break_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Coffee,
                                    contentDescription = "Take a Break",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Take Break",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }

                        // Mode Specific Secondary Action
                        if (uiState.mode == SessionMode.STOPWATCH) {
                            Button(
                                onClick = { viewModel.recordLap() },
                                shape = FocusShapes.large,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FocusColors.CyanBlue,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("record_lap_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Flag,
                                        contentDescription = "Lap",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Record Lap",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                    )
                                }
                            }
                        } else if (uiState.mode == SessionMode.POMODORO) {
                            OutlinedButton(
                                onClick = { viewModel.skipPomodoroPhase() },
                                shape = FocusShapes.large,
                                border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.Primary.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = FocusColors.Primary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .testTag("skip_pomodoro_phase_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Skip Phase",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                Crossfade(
                                    targetState = uiState.pomodoroPhase == PomodoroPhase.FOCUS,
                                    animationSpec = tween(300),
                                    label = "skip_phase_text_crossfade"
                                ) { isFocusPhase ->
                                    Text(
                                        text = if (isFocusPhase) "Skip" else "Next Focus",
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

                    // Pause / Resume Primary Button (Guarded in Strict Mode)
                    if (uiState.isStrictModeEnabled && uiState.mode != SessionMode.STOPWATCH) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(FocusShapes.large)
                                .background(FocusColors.Primary.copy(alpha = 0.15f))
                                .border(1.dp, FocusColors.Primary.copy(alpha = 0.3f), FocusShapes.large)
                                .clickable { showStrictLockDialog = true },
                            contentAlignment = Alignment.Center
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
                                    text = "Strict Focus In Progress",
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
                            onClick = {
                                if (uiState.isSessionPaused) {
                                    viewModel.resumeSession()
                                } else {
                                    viewModel.pauseSession()
                                }
                            },
                            shape = FocusShapes.large,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = FocusColors.Primary,
                                contentColor = Color.White
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
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
                                        imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = if (isPaused) "Resume" else "Pause",
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (isPaused) "Resume Session" else "Pause Session",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // End Session Button / Strict Mode Locked Indicator
                    if (uiState.isStrictModeEnabled && uiState.mode != SessionMode.STOPWATCH) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(FocusShapes.large)
                                .background(Color(0xFF231618))
                                .border(1.dp, FocusColors.CoralWarning.copy(alpha = 0.4f), FocusShapes.large)
                                .clickable { showStrictLockDialog = true }
                                .padding(vertical = 14.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
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
                                    text = "Strict Mode Locked • Cannot End Early",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = FocusColors.CoralWarning,
                                        fontSize = 14.sp
                                    )
                                )
                            }
                        }
                    } else {
                        OutlinedButton(
                            onClick = { showEndSessionDialog = true },
                            shape = FocusShapes.large,
                            border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.CoralWarning.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = FocusColors.CoralWarning
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("end_session_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "End Session",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "End Session",
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
    onEndBreakEarly: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Break Header Info
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
                        text = "Temporary Study Break",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = FocusColors.CyanBlue,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Resting from ${uiState.subject}",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = FocusColors.TextPrimary,
                    fontSize = 22.sp
                ),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "App shields are temporarily lowered. Hydrate, stretch, and relax.",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = FocusColors.TextSecondary,
                    fontSize = 13.sp
                ),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Center Break Timer Dial
        val cyanColor = FocusColors.CyanBlue
        val cyanBg = FocusColors.CyanLight.copy(alpha = 0.3f)

        Box(
            modifier = Modifier.size(240.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 10.dp.toPx()
                drawCircle(
                    color = cyanBg,
                    style = Stroke(width = strokeWidth)
                )
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
                        color = FocusColors.CyanBlue,
                        fontSize = 46.sp,
                        letterSpacing = 1.sp
                    ),
                    modifier = Modifier.testTag("break_timer_value")
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "BREAK TIME REMAINING",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = FocusColors.CyanBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Break Protection Notice
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
                    text = "When this countdown reaches 0:00, your FocusShield will automatically re-engage.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 12.5.sp
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Action Controls
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
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("return_to_focus_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Return to Focus",
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Return to Focus Early",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                }
            }

            OutlinedButton(
                onClick = onEndSession,
                shape = FocusShapes.large,
                border = androidx.compose.foundation.BorderStroke(1.dp, FocusColors.CoralWarning.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = FocusColors.CoralWarning
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("end_session_from_break_button")
            ) {
                Text(
                    text = "End Session",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                )
            }
        }
    }
}

/**
 * Session Completion celebration view within Active Session screen.
 */
@Composable
private fun SessionCompletedView(
    uiState: ActiveSessionUiState,
    onReturnHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(20.dp))

        // Lottie Focus Success Animation
        FocusLottieAnimation(
            rawRes = R.raw.focus_success,
            modifier = Modifier.size(120.dp),
            iterations = 1
        )

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "Focus Goal Achieved!",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Bold,
                color = FocusColors.TextPrimary,
                fontSize = 24.sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Great work! You stayed distraction-free during your ${uiState.mode.title} study session.",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = FocusColors.TextSecondary,
                fontSize = 14.sp
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Session Stats Summary Card
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Subject & Topic",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary
                        )
                    )
                    Text(
                        text = "${uiState.subject} • ${uiState.topic}",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Study Duration",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary
                        )
                    )
                    Text(
                        text = uiState.elapsedDurationFormatted,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.Primary
                        )
                    )
                }

                if (uiState.totalBreakDurationMillis > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Break Time",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary
                            )
                        )
                        Text(
                            text = TimeFormatter.formatDigital(uiState.totalBreakDurationMillis, true),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.CyanBlue
                            )
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Mode",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = FocusColors.TextSecondary
                        )
                    )
                    Text(
                        text = uiState.mode.title,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = FocusColors.TextPrimary
                        )
                    )
                }

                if (uiState.isStrictModeEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Protection Level",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary
                            )
                        )
                        Text(
                            text = "Strict Mode Enforced",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.CoralWarning
                            )
                        )
                    }
                }

                if (uiState.mode == SessionMode.POMODORO) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cycles Completed",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary
                            )
                        )
                        Text(
                            text = "${uiState.totalCycles} Cycles",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.EmeraldSuccess
                            )
                        )
                    }
                }

                if (uiState.mode == SessionMode.STOPWATCH && uiState.laps.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Laps Recorded",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextSecondary
                            )
                        )
                        Text(
                            text = "${uiState.laps.size} Laps",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = FocusColors.CyanBlue
                            )
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(30.dp))

        // Return Home Button
        Button(
            onClick = onReturnHome,
            shape = FocusShapes.large,
            colors = ButtonDefaults.buttonColors(
                containerColor = FocusColors.Primary,
                contentColor = Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("return_home_button")
        ) {
            Text(
                text = "Done • Return to Home",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}

