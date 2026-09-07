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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.core.util.InstalledAppItem
import com.example.core.util.InstalledAppsProvider
import com.example.core.util.InstalledAppsProvider.toImageBitmap
import com.example.data.model.SessionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.feature.session.SessionSetupState
import com.example.feature.session.SessionViewModel

private enum class SetupSubView {
    MAIN,
    FOCUS_TIME,
    BREAK,
    SELECT_APPS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSessionSetupSheet(
    viewModel: SessionViewModel,
    onDismiss: () -> Unit,
    onStartFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val setupState by viewModel.setupState.collectAsStateWithLifecycle()

    var activeSubView by remember { mutableStateOf(SetupSubView.MAIN) }

    val pendingDeviceAdminRequest by viewModel.pendingDeviceAdminRequest.collectAsStateWithLifecycle()
    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onDeviceAdminRequestResult()
    }

    LaunchedEffect(pendingDeviceAdminRequest) {
        if (pendingDeviceAdminRequest) {
            deviceAdminLauncher.launch(viewModel.getDeviceAdminIntent())
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FocusColors.Surface,
        contentColor = FocusColors.TextPrimary,
        dragHandle = null,
        modifier = modifier
    ) {
        androidx.activity.compose.BackHandler(enabled = activeSubView != SetupSubView.MAIN) {
            activeSubView = SetupSubView.MAIN
        }

        when (activeSubView) {
            SetupSubView.MAIN -> {
                FocusSetupMainContent(
                    setupState = setupState,
                    onModeSelected = { viewModel.selectMode(it) },
                    onOpenFocusTimePicker = { activeSubView = SetupSubView.FOCUS_TIME },
                    onOpenBreakPicker = { activeSubView = SetupSubView.BREAK },
                    onOpenSelectApps = { activeSubView = SetupSubView.SELECT_APPS },
                    onToggleStrictMode = { viewModel.toggleStrictMode(it) },
                    onToggleYouTubeStudyMode = { viewModel.toggleYouTubeStudyMode(it) },
                    onToggleBrowserStudyMode = { viewModel.toggleBrowserStudyMode(it) },
                    onToggleBlockHomeScreen = { viewModel.toggleBlockHomeScreen(it) },
                    onToggleBlockUninstall = { viewModel.toggleBlockUninstall(it) },
                    onToggleBlockSplitScreen = { viewModel.toggleBlockSplitScreen(it) },
                    onToggleBlockFloatingWindow = { viewModel.toggleBlockFloatingWindow(it) },
                    onToggleDeepFocusExpanded = { viewModel.toggleDeepFocusExpanded() },
                    onStartFocusNow = {
                        val started = viewModel.startSession()
                        if (started) {
                            onStartFocus()
                        }
                    }
                )
            }

            SetupSubView.FOCUS_TIME -> {
                FocusTimePickerSheet(
                    selectedMinutes = setupState.durationMinutes,
                    onMinutesSelect = { viewModel.setDuration(it) },
                    onClose = { activeSubView = SetupSubView.MAIN }
                )
            }

            SetupSubView.BREAK -> {
                BreakSetupSheet(
                    numberOfBreaks = setupState.numberOfBreaks,
                    breakDurationMinutes = setupState.breakDurationMinutes,
                    onNumberOfBreaksChange = { viewModel.setNumberOfBreaks(it) },
                    onBreakDurationChange = { viewModel.setBreakDuration(it) },
                    onBack = { activeSubView = SetupSubView.MAIN }
                )
            }

            SetupSubView.SELECT_APPS -> {
                SelectAppsToBlockSheet(
                    youtubeOption = setupState.youtubeOption,
                    browserOption = setupState.browserOption,
                    isDistractingMasterEnabled = setupState.isDistractingMasterEnabled,
                    blockedAppPackages = setupState.blockedAppPackages,
                    onYoutubeOptionChange = { viewModel.setYoutubeOption(it) },
                    onBrowserOptionChange = { viewModel.setBrowserOption(it) },
                    onToggleDistractingMaster = { enabled, allPkgs -> viewModel.toggleDistractingMaster(enabled, allPkgs) },
                    onToggleAppBlocked = { pkg, blocked -> viewModel.toggleAppBlocked(pkg, blocked) },
                    onClose = { activeSubView = SetupSubView.MAIN }
                )
            }
        }
    }
}

@Composable
private fun FocusSetupMainContent(
    setupState: SessionSetupState,
    onModeSelected: (SessionMode) -> Unit,
    onOpenFocusTimePicker: () -> Unit,
    onOpenBreakPicker: () -> Unit,
    onOpenSelectApps: () -> Unit,
    onToggleStrictMode: (Boolean) -> Unit,
    onToggleYouTubeStudyMode: (Boolean) -> Unit,
    onToggleBrowserStudyMode: (Boolean) -> Unit,
    onToggleBlockHomeScreen: (Boolean) -> Unit,
    onToggleBlockUninstall: (Boolean) -> Unit,
    onToggleBlockSplitScreen: (Boolean) -> Unit,
    onToggleBlockFloatingWindow: (Boolean) -> Unit,
    onToggleDeepFocusExpanded: () -> Unit,
    onStartFocusNow: () -> Unit
) {
    val context = LocalContext.current
    var installedBrowsers by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        installedBrowsers = InstalledAppsProvider.getInstalledBrowsers(context)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.84f)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Grab handle bar
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(FocusColors.TextMuted.copy(alpha = 0.4f))
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Top Segmented Tab Picker: [ Timer | Stopwatch | Pomodoro ]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(FocusColors.SurfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                SessionMode.TIMER to "Timer",
                SessionMode.STOPWATCH to "Stopwatch",
                SessionMode.POMODORO to "Pomodoro"
            ).forEach { (mode, label) ->
                val isSelected = setupState.selectedMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .background(if (isSelected) FocusColors.PrimaryContainer else Color.Transparent)
                        .clickable { onModeSelected(mode) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = if (isSelected) FocusColors.Primary else FocusColors.TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // CARD 1: Core Session Settings Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            color = FocusColors.SurfaceVariant
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Row 1: Focus Time / Total Duration
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = setupState.selectedMode != SessionMode.STOPWATCH) { onOpenFocusTimePicker() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (setupState.selectedMode == SessionMode.STOPWATCH) "Total duration" else "Focus Time",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (setupState.selectedMode) {
                                SessionMode.TIMER -> "${setupState.durationMinutes} mins"
                                SessionMode.STOPWATCH -> "0 -> ∞"
                                SessionMode.POMODORO -> "${setupState.pomodoroFocusMinutes} mins"
                            },
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = FocusColors.Primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        if (setupState.selectedMode != SessionMode.STOPWATCH) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = "Select focus time",
                                tint = FocusColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = FocusColors.CardBorderSubtle, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Row 2: Breaks
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenBreakPicker() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Breaks",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${setupState.numberOfBreaks} break${if (setupState.numberOfBreaks != 1) "s" else ""}",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = FocusColors.Primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Configure breaks",
                            tint = FocusColors.TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = FocusColors.CardBorderSubtle, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                // Row 3: Blocked Apps
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenSelectApps() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Blocked Apps",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = FocusColors.TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Stack of icons
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            // YouTube Icon
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF0000)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width((-4).dp))

                            // Browser / Chrome Icon
                            if (installedBrowsers.isNotEmpty() && installedBrowsers.first().icon != null) {
                                Image(
                                    bitmap = installedBrowsers.first().icon!!.toImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2B52B6)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Language,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }

                        Text(
                            text = "${setupState.blockedAppPackages.size} apps",
                            style = MaterialTheme.typography.bodyLarge.copy(
                                color = FocusColors.Primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )

                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = "Select blocked apps",
                            tint = FocusColors.TextMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // CARD 2: Deep Focus Settings PRO Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            color = FocusColors.SurfaceVariant
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Title Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleDeepFocusExpanded() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Deep Focus Settings",
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
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

                    Icon(
                        imageVector = if (setupState.isDeepFocusExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = FocusColors.TextMuted
                    )
                }

                AnimatedVisibility(
                    visible = setupState.isDeepFocusExpanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))

                        // Switch 1: Strict Mode (Only for Timer & Pomodoro, not for Stopwatch)
                        if (setupState.selectedMode != SessionMode.STOPWATCH) {
                            DeepFocusSwitchRow(
                                title = "Strict mode",
                                subtitle = "You cannot end your session early",
                                checked = setupState.isStrictModeEnabled,
                                onCheckedChange = onToggleStrictMode
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = FocusColors.CardBorderSubtle, thickness = 1.dp)
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // Switch 2: YouTube Study Mode
                        DeepFocusSwitchRow(
                            title = "YouTube Study Mode",
                            checked = setupState.isYouTubeStudyModeEnabled,
                            onCheckedChange = onToggleYouTubeStudyMode
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = FocusColors.CardBorderSubtle, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Switch 3: Browser Study Mode
                        DeepFocusSwitchRow(
                            title = "Browser Study Mode",
                            checked = setupState.isBrowserStudyModeEnabled,
                            onCheckedChange = onToggleBrowserStudyMode
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = FocusColors.CardBorderSubtle, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Switch 4: Block Phone home screen
                        DeepFocusSwitchRow(
                            title = "Block Phone home screen",
                            checked = setupState.isBlockHomeScreenEnabled,
                            onCheckedChange = onToggleBlockHomeScreen
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = FocusColors.CardBorderSubtle, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Switch 5: Block FocusShield app uninstall
                        DeepFocusSwitchRow(
                            title = "Block FocusShield app uninstall",
                            subtitle = "Prevents uninstalling or tampering with FocusShield",
                            checked = setupState.isBlockUninstallEnabled,
                            onCheckedChange = onToggleBlockUninstall
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = FocusColors.CardBorderSubtle, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Switch 6: Block split screen
                        DeepFocusSwitchRow(
                            title = "Block split screen",
                            subtitle = "Cannot use blocked apps in split screen",
                            checked = setupState.isBlockSplitScreenEnabled,
                            onCheckedChange = onToggleBlockSplitScreen
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Divider(color = FocusColors.CardBorderSubtle, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Switch 7: Block floating window
                        DeepFocusSwitchRow(
                            title = "Block floating window",
                            subtitle = "Cannot use blocked apps in floating window",
                            checked = setupState.isBlockFloatingWindowEnabled,
                            onCheckedChange = onToggleBlockFloatingWindow
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // BOTTOM ACTION BUTTON: "Start Focus Now"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CircleShape)
                .background(FocusColors.Primary)
                .clickable { onStartFocusNow() }
                .padding(vertical = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Start Focus Now",
                style = MaterialTheme.typography.titleMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            )
        }
    }
}

@Composable
private fun DeepFocusSwitchRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = FocusColors.TextPrimary,
                    fontWeight = FontWeight.Medium
                )
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = FocusColors.TextSecondary,
                        fontSize = 12.sp
                    )
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = FocusColors.Primary,
                uncheckedThumbColor = FocusColors.TextMuted,
                uncheckedTrackColor = FocusColors.SurfaceVariant
            )
        )
    }
}
