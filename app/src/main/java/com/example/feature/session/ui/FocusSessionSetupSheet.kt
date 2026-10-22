package com.example.feature.session.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.core.accessibility.AccessibilityFeaturePromptInfo
import com.example.core.accessibility.AccessibilityHelper
import com.example.core.accessibility.AccessibilityPermissionRequiredDialog
import com.example.feature.session.SpecialAppOption
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.design.FocusColors
import com.example.core.util.InstalledAppItem
import com.example.core.util.InstalledAppsProvider
import com.example.data.local.entity.SubjectEntity
import com.example.data.model.SessionMode
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.feature.session.SessionSetupState
import com.example.feature.session.SessionViewModel

/**
 * The pages of the session setup flow. All of them live inside the **one** ModalBottomSheet opened
 * by [FocusSessionSetupSheet].
 *
 * WEBSITE_BLOCKER and YOUTUBE_CHANNELS used to be nested `ModalBottomSheet`s opened from inside
 * SELECT_APPS (issue #52). That put a second modal window on top of the sheet's own window, and
 * removed it from the composition the instant dismissal was *requested* rather than when the sheet
 * was actually hidden — so a downward drag on the inner sheet handed the rest of the interaction
 * straight to the sheet underneath. Appending them here (ordinals of the existing entries are
 * unchanged, so saved state still restores) keeps every page in a single sheet with a single
 * gesture owner.
 */
internal enum class SetupSubView {
    MAIN,
    FOCUS_TIME,
    BREAK,
    SELECT_APPS,
    WEBSITE_BLOCKER,
    YOUTUBE_CHANNELS
}

/**
 * The page one step back, or null when there is nowhere left to go and the sheet itself should
 * close.
 *
 * One mapping, used by system back, the in-sheet close/back buttons **and** drag-to-dismiss, so
 * the three cannot disagree — the drag path used to be the odd one out, jumping straight to MAIN
 * from any page and leaving the sheet hidden-but-composed.
 */
internal fun SetupSubView.backTarget(): SetupSubView? = when (this) {
    SetupSubView.MAIN -> null
    SetupSubView.FOCUS_TIME,
    SetupSubView.BREAK,
    SetupSubView.SELECT_APPS -> SetupSubView.MAIN
    SetupSubView.WEBSITE_BLOCKER,
    SetupSubView.YOUTUBE_CHANNELS -> SetupSubView.SELECT_APPS
}

/**
 * Saves [SetupSubView] by ordinal so the open sub-sheet survives rotation and process death.
 */
private val SetupSubViewSaver: Saver<SetupSubView, Int> = Saver(
    save = { it.ordinal },
    restore = { ordinal -> SetupSubView.entries.getOrNull(ordinal) ?: SetupSubView.MAIN }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusSessionSetupSheet(
    viewModel: SessionViewModel,
    onDismiss: () -> Unit,
    onStartFocus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val setupState by viewModel.setupState.collectAsStateWithLifecycle()
    val customSubjects by viewModel.customSubjects.collectAsStateWithLifecycle()

    // rememberSaveable, not remember: a rotation (or any configuration change) used to throw the
    // user back to the main page of the sheet, losing whichever sub-sheet they were filling in
    // (issue #28). The enum is saved by ordinal so it survives process death too.
    var activeSubView by rememberSaveable(stateSaver = SetupSubViewSaver) {
        mutableStateOf(SetupSubView.MAIN)
    }
    // Deliberately NOT saveable: this holds callbacks, which cannot be written to a Bundle. A
    // pending prompt is re-derived from the toggle the user taps again.
    var pendingAccessibilityPrompt by remember { mutableStateOf<AccessibilityFeaturePromptInfo?>(null) }

    val pendingDeviceAdminRequest by viewModel.pendingDeviceAdminRequest.collectAsStateWithLifecycle()
    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onDeviceAdminRequestResult()
    }

    LaunchedEffect(pendingDeviceAdminRequest) {
        if (pendingDeviceAdminRequest) {
            // Consume the request *before* launching. The flag is ViewModel state that outlives the
            // composition, so on rotation this effect re-ran with it still true and shoved the user
            // back into the device-admin screen they had just dismissed (issue #28).
            viewModel.onDeviceAdminRequestLaunched()
            deviceAdminLauncher.launch(viewModel.getDeviceAdminIntent())
        }
    }

    val coroutineScope = rememberCoroutineScope()

    // Closing for real: play the exit animation, then leave the composition.
    val closeSheet: () -> Unit = {
        coroutineScope.launch {
            try {
                sheetState.hide()
            } finally {
                onDismiss()
            }
        }
    }

    // Material 3 reports `onDismissRequest` *after* it has already animated the sheet to Hidden,
    // so a request that only rewrites navigation state left the sheet composed and invisible: a
    // full-screen modal window showing nothing while swallowing every touch, with the setup UI
    // apparently frozen behind it (issue #52). Stepping back a page therefore has to put the sheet
    // back on screen; only the last step actually dismisses.
    val handleSheetDismissRequest: () -> Unit = {
        val back = activeSubView.backTarget()
        if (back != null) {
            activeSubView = back
            coroutineScope.launch { sheetState.show() }
        } else {
            // Already hidden by Material 3 — nothing left to animate.
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = handleSheetDismissRequest,
        sheetState = sheetState,
        properties = ModalBottomSheetProperties(
            shouldDismissOnBackPress = false
        ),
        containerColor = FocusColors.Surface,
        contentColor = FocusColors.TextPrimary,
        dragHandle = null,
        modifier = modifier
    ) {
        // The only back handler in the flow now that the nested sheets are gone: one step per
        // press, WEBSITE_BLOCKER/YOUTUBE_CHANNELS -> SELECT_APPS -> MAIN -> close (issue #52).
        androidx.activity.compose.BackHandler(enabled = true) {
            val back = activeSubView.backTarget()
            if (back != null) {
                activeSubView = back
            } else {
                closeSheet()
            }
        }

        AnimatedContent(
            targetState = activeSubView,
            transitionSpec = {
                // Never animate the sheet's size here. AnimatedContent defaults to a SizeTransform,
                // so every page change re-measured the whole 84%-height sheet frame by frame.
                // Moving between a sub-page and MAIN keeps a short slide; sub-page to sub-page is
                // a plain cross-fade.
                if (targetState == SetupSubView.MAIN || initialState == SetupSubView.MAIN) {
                    ContentTransform(
                        targetContentEnter = slideInVertically(tween(220)) { it / 6 } + fadeIn(tween(220)),
                        initialContentExit = slideOutVertically(tween(180)) { -it / 6 } + fadeOut(tween(180)),
                        sizeTransform = null
                    )
                } else {
                    ContentTransform(
                        targetContentEnter = fadeIn(tween(160)),
                        initialContentExit = fadeOut(tween(120)),
                        sizeTransform = null
                    )
                }
            },
            contentKey = { it },
            modifier = Modifier.fillMaxWidth()
        ) { subView ->
            when (subView) {
                SetupSubView.MAIN -> {
                    FocusSetupMainContent(
                        setupState = setupState,
                        customSubjects = customSubjects,
                        onModeSelected = { viewModel.selectMode(it) },
                        onToggleSubjectTag = { viewModel.toggleSubjectTag(it) },
                        onAddSubjectTag = { viewModel.addCustomSubjectTag(it) },
                        onRenameSubjectTag = { subject, newName -> viewModel.renameCustomSubjectTag(subject, newName) },
                        onDeleteSubjectTag = { viewModel.deleteCustomSubjectTag(it) },
                        onTopicChange = { viewModel.setTopic(it) },
                        onOpenFocusTimePicker = { activeSubView = SetupSubView.FOCUS_TIME },
                        onOpenBreakPicker = { activeSubView = SetupSubView.BREAK },
                        onOpenSelectApps = { activeSubView = SetupSubView.SELECT_APPS },
                        onToggleStrictMode = { viewModel.toggleStrictMode(it) },
                        onToggleYouTubeStudyMode = { enabled ->
                            if (enabled && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                    title = "YouTube Study Mode",
                                    description = "YouTube study channels and distractive video feeds",
                                    onGranted = { viewModel.toggleYouTubeStudyMode(true) }
                                )
                            } else {
                                viewModel.toggleYouTubeStudyMode(enabled)
                            }
                        },
                        onToggleBrowserStudyMode = { enabled ->
                            if (enabled && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                    title = "Browser Study Mode",
                                    description = "distracting websites while allowing study sites",
                                    onGranted = { viewModel.toggleBrowserStudyMode(true) }
                                )
                            } else {
                                viewModel.toggleBrowserStudyMode(enabled)
                            }
                        },
                        onToggleBlockHomeScreen = { viewModel.toggleBlockHomeScreen(it) },
                        onToggleBlockUninstall = { enabled ->
                            if (enabled && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                    title = "App Uninstall Protection",
                                    description = "system settings to prevent FocusShield from being uninstalled",
                                    onGranted = { viewModel.toggleBlockUninstall(true) }
                                )
                            } else {
                                viewModel.toggleBlockUninstall(enabled)
                            }
                        },
                        onToggleBlockSplitScreen = { enabled ->
                            if (enabled && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                    title = "Split Screen Blocker",
                                    description = "split screen multi-window mode",
                                    onGranted = { viewModel.toggleBlockSplitScreen(true) }
                                )
                            } else {
                                viewModel.toggleBlockSplitScreen(enabled)
                            }
                        },
                        onToggleBlockFloatingWindow = { enabled ->
                            if (enabled && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                    title = "Floating Window Blocker",
                                    description = "picture-in-picture and floating windows",
                                    onGranted = { viewModel.toggleBlockFloatingWindow(true) }
                                )
                            } else {
                                viewModel.toggleBlockFloatingWindow(enabled)
                            }
                        },
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
                        onYoutubeOptionChange = { option ->
                            if (option == SpecialAppOption.STUDY_MODE && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                    title = "YouTube Study Mode",
                                    description = "YouTube study channels and distractive video feeds",
                                    onGranted = { viewModel.setYoutubeOption(SpecialAppOption.STUDY_MODE) }
                                )
                            } else {
                                viewModel.setYoutubeOption(option)
                            }
                        },
                        onBrowserOptionChange = { option ->
                            if (option == SpecialAppOption.STUDY_MODE && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                    title = "Browser Study Mode",
                                    description = "distracting websites and search results",
                                    onGranted = { viewModel.setBrowserOption(SpecialAppOption.STUDY_MODE) }
                                )
                            } else {
                                viewModel.setBrowserOption(option)
                            }
                        },
                        onToggleDistractingMaster = { enabled, allPkgs -> viewModel.toggleDistractingMaster(enabled, allPkgs) },
                        onToggleAppBlocked = { pkg, blocked -> viewModel.toggleAppBlocked(pkg, blocked) },
                        onOpenYouTubeChannels = { activeSubView = SetupSubView.YOUTUBE_CHANNELS },
                        onOpenWebsiteBlocker = { activeSubView = SetupSubView.WEBSITE_BLOCKER },
                        onClose = { activeSubView = SetupSubView.MAIN }
                    )
                }

                SetupSubView.WEBSITE_BLOCKER -> {
                    // The same content the nested sheet used to host, and the same content
                    // WebsiteBlockerScreen renders standalone — just without a second modal window
                    // of its own (issue #52).
                    com.example.feature.websiteblocker.ui.WebsiteBlockerSheetContent(
                        onDismiss = { activeSubView = SetupSubView.SELECT_APPS },
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.84f)
                    )
                }

                SetupSubView.YOUTUBE_CHANNELS -> {
                    com.example.feature.channels.YouTubeStudyChannelContent(
                        onDismiss = { activeSubView = SetupSubView.SELECT_APPS },
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.84f)
                    )
                }
            }
        }
    }

    // Dynamic Accessibility Permission Prompt Dialog
    pendingAccessibilityPrompt?.let { promptInfo ->
        AccessibilityPermissionRequiredDialog(
            featureTitle = promptInfo.title,
            featureDescription = promptInfo.description,
            onDismissRequest = { pendingAccessibilityPrompt = null },
            onPermissionGranted = {
                promptInfo.onGranted()
                pendingAccessibilityPrompt = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusSetupMainContent(
    setupState: SessionSetupState,
    customSubjects: List<SubjectEntity>,
    onModeSelected: (SessionMode) -> Unit,
    onToggleSubjectTag: (String) -> Unit,
    onAddSubjectTag: (String) -> Unit,
    onRenameSubjectTag: (SubjectEntity, String) -> Unit,
    onDeleteSubjectTag: (SubjectEntity) -> Unit,
    onTopicChange: (String) -> Unit,
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
    var showAddTagDialog by remember { mutableStateOf(false) }
    var newTagName by remember { mutableStateOf("") }
    var editingSubject by remember { mutableStateOf<SubjectEntity?>(null) }
    var editTagName by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        installedBrowsers = InstalledAppsProvider.getInstalledBrowsers(context)
    }

    if (showAddTagDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddTagDialog = false
                newTagName = ""
            },
            containerColor = FocusColors.Surface,
            titleContentColor = FocusColors.TextPrimary,
            textContentColor = FocusColors.TextSecondary,
            title = {
                Text(
                    text = "New Subject Tag",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    placeholder = { Text("e.g. Coding, Math, Reading") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FocusColors.Primary,
                        unfocusedBorderColor = FocusColors.CardBorderSubtle,
                        focusedTextColor = FocusColors.TextPrimary,
                        unfocusedTextColor = FocusColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTagName.isNotBlank()) {
                            onAddSubjectTag(newTagName.trim())
                            newTagName = ""
                            showAddTagDialog = false
                        }
                    },
                    enabled = newTagName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FocusColors.Primary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Save Tag", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAddTagDialog = false
                        newTagName = ""
                    }
                ) {
                    Text("Cancel", color = FocusColors.TextSecondary)
                }
            }
        )
    }

    editingSubject?.let { subjectToEdit ->
        AlertDialog(
            onDismissRequest = { editingSubject = null },
            containerColor = FocusColors.Surface,
            titleContentColor = FocusColors.TextPrimary,
            textContentColor = FocusColors.TextSecondary,
            title = {
                Text(
                    text = "Edit Subject Tag",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editTagName,
                        onValueChange = { editTagName = it },
                        placeholder = { Text("Tag name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FocusColors.Primary,
                            unfocusedBorderColor = FocusColors.CardBorderSubtle,
                            focusedTextColor = FocusColors.TextPrimary,
                            unfocusedTextColor = FocusColors.TextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(FocusColors.CoralWarning.copy(alpha = 0.12f))
                            .clickable {
                                onDeleteSubjectTag(subjectToEdit)
                                editingSubject = null
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete tag",
                            tint = FocusColors.CoralWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Delete Tag",
                            style = MaterialTheme.typography.labelLarge.copy(
                                color = FocusColors.CoralWarning,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editTagName.isNotBlank()) {
                            onRenameSubjectTag(subjectToEdit, editTagName.trim())
                            editingSubject = null
                        }
                    },
                    enabled = editTagName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = FocusColors.Primary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSubject = null }) {
                    Text("Cancel", color = FocusColors.TextSecondary)
                }
            }
        )
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

        // CARD 0: Session Tags (Subject & Topic) Card
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            color = FocusColors.SurfaceVariant
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Label,
                            contentDescription = null,
                            tint = FocusColors.Primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Session Tag & Topic",
                            style = MaterialTheme.typography.titleSmall.copy(
                                color = FocusColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        )
                    }
                    if (setupState.selectedSubject.isNotBlank() || setupState.selectedTopic.isNotBlank()) {
                        Text(
                            text = "Clear",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = FocusColors.Primary,
                                fontWeight = FontWeight.SemiBold
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    if (setupState.selectedSubject.isNotBlank()) {
                                        onToggleSubjectTag(setupState.selectedSubject)
                                    }
                                    if (setupState.selectedTopic.isNotBlank()) {
                                        onTopicChange("")
                                    }
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    } else {
                        Text(
                            text = "Optional",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = FocusColors.TextMuted
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Subject Tag",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    customSubjects.forEach { subject ->
                        val isSelected = setupState.selectedSubject.equals(subject.name, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) FocusColors.PrimaryContainer else FocusColors.Surface
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) FocusColors.Primary else FocusColors.CardBorderSubtle,
                                    shape = CircleShape
                                )
                                .clickable { onToggleSubjectTag(subject.name) }
                                .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = subject.name,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isSelected) FocusColors.Primary else FocusColors.TextPrimary,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            editingSubject = subject
                                            editTagName = subject.name
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "Edit ${subject.name}",
                                        tint = if (isSelected) FocusColors.Primary else FocusColors.TextMuted,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }

                    // If a subject is set from a Study Plan that isn't in customSubjects yet, show it
                    val hasMatchingCustom = customSubjects.any {
                        it.name.equals(setupState.selectedSubject, ignoreCase = true)
                    }
                    if (setupState.selectedSubject.isNotBlank() && !hasMatchingCustom) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(FocusColors.PrimaryContainer)
                                .border(1.dp, FocusColors.Primary, CircleShape)
                                .clickable { onToggleSubjectTag(setupState.selectedSubject) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = setupState.selectedSubject,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.Primary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }

                    // "+ Add Tag" Chip
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(FocusColors.Surface)
                            .border(
                                width = 1.dp,
                                color = FocusColors.Primary.copy(alpha = 0.5f),
                                shape = CircleShape
                            )
                            .clickable {
                                newTagName = ""
                                showAddTagDialog = true
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add Tag",
                                tint = FocusColors.Primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add Tag",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = FocusColors.Primary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Topic",
                    style = MaterialTheme.typography.labelMedium.copy(
                        color = FocusColors.TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                )

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = setupState.selectedTopic,
                    onValueChange = onTopicChange,
                    placeholder = {
                        Text(
                            text = "Enter topic (e.g., Chapter 1, Problem Set)...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = FocusColors.TextMuted,
                                fontSize = 13.sp
                            )
                        )
                    },
                    singleLine = true,
                    trailingIcon = if (setupState.selectedTopic.isNotBlank()) {
                        {
                            IconButton(
                                onClick = { onTopicChange("") },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear topic",
                                    tint = FocusColors.TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = FocusColors.Primary,
                        unfocusedBorderColor = FocusColors.CardBorderSubtle,
                        focusedContainerColor = FocusColors.Surface,
                        unfocusedContainerColor = FocusColors.Surface,
                        focusedTextColor = FocusColors.TextPrimary,
                        unfocusedTextColor = FocusColors.TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
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
                            if (installedBrowsers.isNotEmpty()) {
                                val browserIcon = installedBrowsers.first().iconBitmap
                                if (browserIcon != null) {
                                    Image(
                                        bitmap = browserIcon,
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
                    // Fade only: expanding/collapsing seven switch rows re-measured the sheet on
                    // every frame. The content still fades, but the layout settles immediately.
                    enter = fadeIn(tween(150)),
                    exit = fadeOut(tween(120))
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
                checkedTrackColor = FocusColors.ToggleGreen,
                uncheckedThumbColor = FocusColors.TextMuted,
                uncheckedTrackColor = FocusColors.SurfaceVariant
            )
        )
    }
}
