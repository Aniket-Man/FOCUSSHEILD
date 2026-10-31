package com.example.feature.planner

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.FocusShieldApp
import com.example.core.accessibility.AccessibilityFeaturePromptInfo
import com.example.core.accessibility.AccessibilityHelper
import com.example.core.accessibility.AccessibilityPermissionRequiredDialog
import com.example.core.design.FocusColors
import com.example.core.design.LocalFocusColors
import com.example.core.util.InstalledAppItem
import com.example.core.util.InstalledAppsProvider
import com.example.data.local.entity.BlockedAppEntity
import com.example.data.local.entity.FocusScheduleEntity
import com.example.data.local.entity.SubjectEntity
import com.example.feature.channels.YouTubeStudyChannelContent
import com.example.feature.session.SpecialAppOption
import com.example.feature.session.ui.SelectAppsToBlockSheet
import com.example.feature.websiteblocker.ui.WebsiteBlockerSheetContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

private val DEFAULT_EMOJIS = listOf("🌅", "☀️", "🌇", "🌙", "⏳", "📚", "🎯", "💡", "💻", "🧠", "📝", "☕", "🔬", "📖")

private val DEFAULT_DISTRACTING_PACKAGES = setOf(
    "com.google.android.youtube",
    "com.android.chrome",
    "com.opera.browser",
    "com.brave.browser",
    "com.instagram.android"
)

private val BROWSER_PACKAGES = setOf(
    "com.android.chrome",
    "com.opera.browser",
    "com.opera.mini.native",
    "com.brave.browser",
    "org.mozilla.firefox",
    "com.microsoft.emmx",
    "com.sec.android.app.sbrowser",
    "com.duckduckgo.mobile.android",
    "com.UCMobile.intl",
    "com.kiwibrowser.browser",
    "com.vivaldi.browser"
)

private enum class ScheduleDialogSubView {
    MAIN,
    SELECT_APPS,
    YOUTUBE_CHANNELS,
    WEBSITE_BLOCKER
}

private fun ScheduleDialogSubView.backTarget(): ScheduleDialogSubView? = when (this) {
    ScheduleDialogSubView.MAIN -> null
    ScheduleDialogSubView.SELECT_APPS -> ScheduleDialogSubView.MAIN
    ScheduleDialogSubView.YOUTUBE_CHANNELS,
    ScheduleDialogSubView.WEBSITE_BLOCKER -> ScheduleDialogSubView.SELECT_APPS
}

internal fun getEmojiForTime(timeStr: String): String {
    val hour = try {
        timeStr.split(":").firstOrNull()?.trim()?.toIntOrNull() ?: 12
    } catch (e: Exception) {
        12
    }
    return when (hour) {
        in 4..11 -> "🌅" // Morning
        in 12..16 -> "☀️" // Afternoon
        in 17..20 -> "🌇" // Evening
        else -> "🌙" // Night (21:00 - 03:59)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusScheduleDialog(
    initialSchedule: FocusScheduleEntity?,
    initialBlockNotifications: Boolean = false,
    availableBlockedApps: List<BlockedAppEntity> = emptyList(),
    selectedDateMillis: Long = System.currentTimeMillis(),
    onOpenBlockedApps: () -> Unit = {},
    onDismiss: () -> Unit,
    onBlockNotificationsChanged: (Boolean) -> Unit = {},
    onSave: (
        title: String,
        daysOfWeek: String,
        startTime: String,
        endTime: String,
        isAutoStart: Boolean,
        mode: String,
        subjectName: String,
        repeatEnabled: Boolean,
        scheduledDateMillis: Long,
        breakMinutes: Int,
        description: String,
        blockedAppPackages: Set<String>,
        blockNotifications: Boolean
    ) -> Unit
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as FocusShieldApp }
    val preferencesRepository = remember(app) { app.preferencesRepository }
    val subjectRepository = remember(app) { app.subjectRepository }
    val coroutineScope = rememberCoroutineScope()
    val scheduleKey = initialSchedule?.id ?: "new_schedule"

    val customSubjects by subjectRepository.activeSubjects.collectAsStateWithLifecycle(initialValue = emptyList())
    val prefs by preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(
        initialValue = com.example.data.preferences.FocusPreferences()
    )

    var activeSubView by remember { mutableStateOf(ScheduleDialogSubView.MAIN) }
    var pendingAccessibilityPrompt by remember { mutableStateOf<AccessibilityFeaturePromptInfo?>(null) }

    var title by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule?.title ?: "") }
    var startTime by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule?.startTime ?: "21:00") }
    var endTime by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule?.endTime ?: "22:00") }
    var selectedEmoji by rememberSaveable(scheduleKey) { mutableStateOf(getEmojiForTime(initialSchedule?.startTime ?: "21:00")) }
    var userManuallySelectedEmoji by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule != null) }
    var tag by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule?.subjectName.takeIf { !it.isNullOrBlank() } ?: "Untagged") }
    var isAutoStart by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule?.isAutoStartSession ?: true) }
    var blockNotifications by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule?.blockNotifications ?: initialBlockNotifications) }
    var selectedMode by rememberSaveable(scheduleKey) { mutableStateOf("TIMER") }

    val effectiveScheduleDate = remember(initialSchedule?.scheduledDateMillis, selectedDateMillis) {
        val initMillis = initialSchedule?.scheduledDateMillis ?: 0L
        if (initMillis > 0L) Date(initMillis) else Date(selectedDateMillis)
    }
    var repeatEnabled by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule?.repeatEnabled ?: true) }
    var breakMinutes by rememberSaveable(scheduleKey) { mutableIntStateOf(initialSchedule?.breakMinutes ?: 5) }
    var numberOfBreaks by rememberSaveable(scheduleKey) { mutableIntStateOf(if ((initialSchedule?.breakMinutes ?: 5) > 0) 1 else 0) }
    var description by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule?.description ?: "") }

    // Default blocked apps: use schedule's saved apps when editing, or default distracting apps + active app limits when creating
    var selectedBlockedPackages by remember(scheduleKey) {
        val fromSchedule = initialSchedule?.blockedAppPackages
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
        val initialSet = if (!fromSchedule.isNullOrEmpty()) {
            fromSchedule
        } else {
            DEFAULT_DISTRACTING_PACKAGES + availableBlockedApps.map { it.packageName }
        }
        mutableStateOf(initialSet)
    }

    var isDistractingMasterEnabled by rememberSaveable(scheduleKey) {
        mutableStateOf(selectedBlockedPackages.isNotEmpty())
    }

    // Deep Focus Settings state (synced with FocusPreferencesRepository just like FocusSessionSetupSheet)
    var isDeepFocusExpanded by rememberSaveable(scheduleKey) { mutableStateOf(true) }
    var isStrictMode by rememberSaveable(scheduleKey) { mutableStateOf(false) }
    var isYouTubeStudyMode by rememberSaveable(scheduleKey) { mutableStateOf(true) }
    var isBrowserStudyMode by rememberSaveable(scheduleKey) { mutableStateOf(true) }
    var isBlockHomeScreen by rememberSaveable(scheduleKey) { mutableStateOf(false) }
    var isBlockUninstall by rememberSaveable(scheduleKey) { mutableStateOf(false) }
    var isBlockSplitScreen by rememberSaveable(scheduleKey) { mutableStateOf(false) }
    var isBlockFloatingWindow by rememberSaveable(scheduleKey) { mutableStateOf(false) }
    var youtubeOption by remember { mutableStateOf(SpecialAppOption.STUDY_MODE) }
    var browserOption by remember { mutableStateOf(SpecialAppOption.STUDY_MODE) }
    var prefsHydrated by rememberSaveable(scheduleKey) { mutableStateOf(false) }

    LaunchedEffect(prefs) {
        if (!prefsHydrated) {
            isStrictMode = prefs.isStrictModeDefault
            isYouTubeStudyMode = prefs.isStudyChannelsDefault
            isBrowserStudyMode = prefs.isManualWebsiteBlockingEnabled
            isBlockUninstall = prefs.isBlockUninstallEnabled
            isBlockSplitScreen = prefs.isBlockSplitScreenEnabled
            isBlockFloatingWindow = prefs.isBlockFloatingWindowEnabled
            if (initialSchedule == null) {
                blockNotifications = prefs.isBlockNotificationsEnabled || initialBlockNotifications
            }
            youtubeOption = when {
                prefs.isStudyChannelsDefault -> SpecialAppOption.STUDY_MODE
                selectedBlockedPackages.contains("com.google.android.youtube") -> SpecialAppOption.BLOCK_COMPLETELY
                else -> SpecialAppOption.ALLOW_COMPLETELY
            }
            browserOption = when {
                prefs.isManualWebsiteBlockingEnabled -> SpecialAppOption.STUDY_MODE
                selectedBlockedPackages.any { it in BROWSER_PACKAGES } -> SpecialAppOption.BLOCK_COMPLETELY
                else -> SpecialAppOption.ALLOW_COMPLETELY
            }
            prefsHydrated = true
        }
    }

    var titleError by remember { mutableStateOf<String?>(null) }
    var activePicker by remember { mutableStateOf<ActivePicker?>(null) }
    var showBreakPicker by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showAddTagDialog by remember { mutableStateOf(false) }
    var newTagText by remember { mutableStateOf("") }
    var editingSubject by remember { mutableStateOf<SubjectEntity?>(null) }
    var editSubjectText by remember { mutableStateOf("") }

    var selectedDaysList by rememberSaveable(scheduleKey) {
        val daysStr = initialSchedule?.daysOfWeek?.takeIf { it.isNotBlank() }
            ?: "MON,TUE,WED,THU,FRI,SAT,SUN"
        mutableStateOf(DAYS.filter { daysStr.contains(it) })
    }

    fun saveSchedule() {
        if (title.isBlank()) {
            titleError = "Enter a schedule name"
            return
        }
        val selectedDayCode = SimpleDateFormat("EEE", Locale.ENGLISH)
            .format(effectiveScheduleDate)
            .uppercase(Locale.ENGLISH)
        val days = if (repeatEnabled) {
            selectedDaysList.joinToString(",")
        } else {
            selectedDayCode
        }
        if (repeatEnabled && selectedDaysList.isEmpty()) {
            titleError = "Select at least one repeat day"
            return
        }
        val startMinutes = com.example.feature.session.notification.ScheduleTime
            .parseToMinutesOrNull(startTime)
        val endMinutes = com.example.feature.session.notification.ScheduleTime
            .parseToMinutesOrNull(endTime)
        if (startMinutes == null || endMinutes == null) {
            titleError = "Enter a start and end time as HH:mm"
            return
        }
        if (startMinutes == endMinutes) {
            titleError = "End time must differ from the start time"
            return
        }

        // Persist Deep Focus Settings to preferencesRepository so scheduled & manual sessions honor them
        coroutineScope.launch {
            preferencesRepository.updateProtectionDefaults(
                appBlocking = selectedBlockedPackages.isNotEmpty(),
                strictMode = isStrictMode && selectedMode != "STOPWATCH",
                studyChannels = isYouTubeStudyMode
            )
            preferencesRepository.updateManualWebsiteBlocking(isBrowserStudyMode)
            preferencesRepository.updateBlockUninstall(isBlockUninstall)
            preferencesRepository.updateBlockSplitScreen(isBlockSplitScreen)
            preferencesRepository.updateBlockFloatingWindow(isBlockFloatingWindow)
            preferencesRepository.updateBlockNotifications(blockNotifications)
        }

        val effectiveBreakMins = if (numberOfBreaks <= 0) 0 else breakMinutes

        onSave(
            title.trim(),
            days,
            startTime,
            endTime,
            isAutoStart,
            selectedMode,
            tag.trim().ifBlank { "Untagged" },
            repeatEnabled,
            effectiveScheduleDate.time,
            effectiveBreakMins,
            description.trim(),
            selectedBlockedPackages,
            blockNotifications
        )
    }

    val palette = LocalFocusColors.current
    val isDark = palette.isDark

    val bgColor = palette.background
    val cardBg = palette.surface
    val cardBorder = palette.cardBorderSubtle
    val textPrimary = palette.textPrimary
    val textSecondary = palette.textSecondary
    val dividerColor = palette.cardBorder
    val saveBtnBg = palette.primary
    val saveBtnText = Color.White
    val switchTrackChecked = FocusColors.ToggleGreen
    val switchTrackUnchecked = palette.surfaceVariant

    BackHandler(enabled = true) {
        val back = activeSubView.backTarget()
        if (back != null) {
            activeSubView = back
        } else {
            onDismiss()
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(bgColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .testTag("focus_schedule_dialog"),
        color = bgColor
    ) {
        AnimatedContent(
            targetState = activeSubView,
            transitionSpec = {
                if (targetState == ScheduleDialogSubView.MAIN || initialState == ScheduleDialogSubView.MAIN) {
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
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) { subView ->
            when (subView) {
                ScheduleDialogSubView.SELECT_APPS -> {
                    SelectAppsToBlockSheet(
                        youtubeOption = youtubeOption,
                        browserOption = browserOption,
                        isDistractingMasterEnabled = isDistractingMasterEnabled,
                        blockedAppPackages = selectedBlockedPackages,
                        onYoutubeOptionChange = { option ->
                            if (option == SpecialAppOption.STUDY_MODE && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                    title = "YouTube Study Mode",
                                    description = "YouTube study channels and distractive video feeds",
                                    onGranted = {
                                        youtubeOption = SpecialAppOption.STUDY_MODE
                                        isYouTubeStudyMode = true
                                        selectedBlockedPackages = selectedBlockedPackages + "com.google.android.youtube"
                                    }
                                )
                            } else {
                                youtubeOption = option
                                isYouTubeStudyMode = option == SpecialAppOption.STUDY_MODE
                                selectedBlockedPackages = when (option) {
                                    SpecialAppOption.ALLOW_COMPLETELY -> selectedBlockedPackages - "com.google.android.youtube"
                                    SpecialAppOption.BLOCK_COMPLETELY,
                                    SpecialAppOption.STUDY_MODE -> selectedBlockedPackages + "com.google.android.youtube"
                                }
                            }
                        },
                        onBrowserOptionChange = { option ->
                            if (option == SpecialAppOption.STUDY_MODE && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                    title = "Browser Study Mode",
                                    description = "distracting websites and search results",
                                    onGranted = {
                                        browserOption = SpecialAppOption.STUDY_MODE
                                        isBrowserStudyMode = true
                                        selectedBlockedPackages = selectedBlockedPackages + BROWSER_PACKAGES
                                    }
                                )
                            } else {
                                browserOption = option
                                isBrowserStudyMode = option == SpecialAppOption.STUDY_MODE
                                selectedBlockedPackages = when (option) {
                                    SpecialAppOption.ALLOW_COMPLETELY -> selectedBlockedPackages - BROWSER_PACKAGES
                                    SpecialAppOption.BLOCK_COMPLETELY,
                                    SpecialAppOption.STUDY_MODE -> selectedBlockedPackages + BROWSER_PACKAGES
                                }
                            }
                        },
                        onToggleDistractingMaster = { enabled, allPkgs ->
                            isDistractingMasterEnabled = enabled
                            selectedBlockedPackages = if (enabled) {
                                selectedBlockedPackages + allPkgs
                            } else {
                                val specialKept = mutableSetOf<String>()
                                if (youtubeOption != SpecialAppOption.ALLOW_COMPLETELY) {
                                    specialKept.add("com.google.android.youtube")
                                }
                                if (browserOption != SpecialAppOption.ALLOW_COMPLETELY) {
                                    specialKept.addAll(BROWSER_PACKAGES)
                                }
                                specialKept
                            }
                        },
                        onToggleAppBlocked = { pkg, blocked ->
                            selectedBlockedPackages = if (blocked) {
                                selectedBlockedPackages + pkg
                            } else {
                                selectedBlockedPackages - pkg
                            }
                            if (blocked) isDistractingMasterEnabled = true
                        },
                        onOpenYouTubeChannels = { activeSubView = ScheduleDialogSubView.YOUTUBE_CHANNELS },
                        onOpenWebsiteBlocker = { activeSubView = ScheduleDialogSubView.WEBSITE_BLOCKER },
                        onClose = { activeSubView = ScheduleDialogSubView.MAIN }
                    )
                }

                ScheduleDialogSubView.YOUTUBE_CHANNELS -> {
                    YouTubeStudyChannelContent(
                        onDismiss = { activeSubView = ScheduleDialogSubView.SELECT_APPS },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                ScheduleDialogSubView.WEBSITE_BLOCKER -> {
                    WebsiteBlockerSheetContent(
                        onDismiss = { activeSubView = ScheduleDialogSubView.SELECT_APPS },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                ScheduleDialogSubView.MAIN -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header: "New Schedule" / "Edit Schedule" + Save + ✕ Close
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (initialSchedule != null) "Edit Schedule" else "New Schedule",
                                color = textPrimary,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = { saveSchedule() },
                                    modifier = Modifier.testTag("save_schedule_header_button")
                                ) {
                                    Text(
                                        text = "Save",
                                        color = FocusColors.Primary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close",
                                        tint = textPrimary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }

                        // Scrollable Content
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // 1. Emoji Box + Schedule Name Input Box
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(58.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(cardBg)
                                        .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
                                        .clickable { showEmojiPicker = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = selectedEmoji,
                                        fontSize = 28.sp
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(58.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(cardBg)
                                        .border(
                                            1.dp,
                                            if (titleError != null) Color(0xFFEF4444) else cardBorder,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (title.isEmpty()) {
                                        Text(
                                            text = "Enter schedule name (e.g. Morning Physics)",
                                            color = textSecondary,
                                            fontSize = 16.sp
                                        )
                                    }
                                    BasicTextField(
                                        value = title,
                                        onValueChange = {
                                            title = it
                                            titleError = null
                                        },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = textPrimary,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        cursorBrush = SolidColor(if (isDark) Color.White else FocusColors.Primary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("schedule_title_input")
                                    )
                                }
                            }

                            if (titleError != null) {
                                Text(
                                    text = titleError!!,
                                    color = Color(0xFFEF4444),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(start = 70.dp)
                                )
                            }

                            // 3. Session Tag & Topic Section (Full parity with FocusSessionSetupSheet)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = BorderStroke(1.dp, cardBorder)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Session Tag & Topic",
                                            color = textPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = tag,
                                            color = FocusColors.Primary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        // Preset quick tags if user hasn't created custom ones yet
                                        val presetNames = remember(customSubjects) {
                                            val existing = customSubjects.map { it.name.lowercase(Locale.ENGLISH) }.toSet()
                                            listOf("Study", "Exam", "Revision", "Deep Work").filter {
                                                it.lowercase(Locale.ENGLISH) !in existing
                                            }
                                        }

                                        customSubjects.forEach { subjectEntity ->
                                            val isSelected = tag.equals(subjectEntity.name, ignoreCase = true)
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isSelected) FocusColors.Primary
                                                        else palette.surfaceVariant
                                                    )
                                                    .clickable {
                                                        tag = if (isSelected) "Untagged" else subjectEntity.name
                                                    }
                                                    .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Label,
                                                    contentDescription = null,
                                                    tint = if (isSelected) Color.White else textSecondary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = subjectEntity.name,
                                                    color = if (isSelected) Color.White else textPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Box(
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .clip(CircleShape)
                                                        .clickable {
                                                            editingSubject = subjectEntity
                                                            editSubjectText = subjectEntity.name
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Edit,
                                                        contentDescription = "Edit ${subjectEntity.name}",
                                                        tint = if (isSelected) Color.White.copy(alpha = 0.85f) else textSecondary,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                }
                                            }
                                        }

                                        presetNames.forEach { presetTag ->
                                            val isSelected = tag.equals(presetTag, ignoreCase = true)
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(
                                                        if (isSelected) FocusColors.Primary
                                                        else palette.surfaceVariant
                                                    )
                                                    .clickable {
                                                        tag = if (isSelected) "Untagged" else presetTag
                                                    }
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Label,
                                                    contentDescription = null,
                                                    tint = if (isSelected) Color.White else textSecondary,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(5.dp))
                                                Text(
                                                    text = presetTag,
                                                    color = if (isSelected) Color.White else textPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                        }

                                        // "+ Add Tag" Pill
                                        Row(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(palette.surfaceVariant)
                                                .clickable {
                                                    newTagText = ""
                                                    showAddTagDialog = true
                                                }
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Add,
                                                contentDescription = "Add Tag",
                                                tint = FocusColors.Primary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Add Tag",
                                                color = FocusColors.Primary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    // Topic / Chapter Input Field
                                    OutlinedTextField(
                                        value = description,
                                        onValueChange = { description = it },
                                        placeholder = {
                                            Text(
                                                text = "Enter study topic, chapter, or focus goal (optional)",
                                                fontSize = 13.sp,
                                                color = textSecondary
                                            )
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = palette.surfaceVariant.copy(alpha = 0.5f),
                                            unfocusedContainerColor = palette.surfaceVariant.copy(alpha = 0.35f),
                                            focusedBorderColor = FocusColors.Primary,
                                            unfocusedBorderColor = cardBorder,
                                            focusedTextColor = textPrimary,
                                            unfocusedTextColor = textPrimary
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // 4. Section: Schedule Timing & Repeat
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Schedule & Timing",
                                    color = textPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val durationMins = remember(startTime, endTime) {
                                    com.example.feature.session.notification.ScheduleTime.durationMinutesOrNull(startTime, endTime)
                                }
                                if (durationMins != null) {
                                    val hrs = durationMins / 60
                                    val mins = durationMins % 60
                                    val label = when {
                                        hrs > 0 && mins > 0 -> "${hrs}h ${mins}m window"
                                        hrs > 0 -> "${hrs}h window"
                                        else -> "${mins}m window"
                                    }
                                    Text(
                                        text = label,
                                        color = FocusColors.Primary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Combined Card: From / To / Date / Break
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = BorderStroke(1.dp, cardBorder)
                            ) {
                                Column {
                                    // "From" Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { activePicker = ActivePicker.START_TIME }
                                            .padding(horizontal = 16.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "From",
                                            color = textPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = formatTimeDisplay(startTime),
                                                color = textSecondary,
                                                fontSize = 15.sp
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = textSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    HorizontalDivider(thickness = 1.dp, color = dividerColor)

                                    // "To" Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { activePicker = ActivePicker.END_TIME }
                                            .padding(horizontal = 16.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "To",
                                            color = textPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = formatTimeDisplay(endTime),
                                                color = textSecondary,
                                                fontSize = 15.sp
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = textSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    HorizontalDivider(thickness = 1.dp, color = dividerColor)

                                    // Clock Window Summary Row (Start Time -> Stop Time)
                                    val previewBounds = remember(startTime, endTime, repeatEnabled, selectedDaysList, effectiveScheduleDate) {
                                        val selectedDayCode = SimpleDateFormat("EEE", Locale.ENGLISH)
                                            .format(effectiveScheduleDate)
                                            .uppercase(Locale.ENGLISH)
                                        val previewDays = if (repeatEnabled) selectedDaysList.joinToString(",") else selectedDayCode
                                        val previewSchedule = FocusScheduleEntity(
                                            id = "preview",
                                            title = title.ifBlank { "Schedule" },
                                            daysOfWeek = previewDays,
                                            startTime = startTime,
                                            endTime = endTime,
                                            repeatEnabled = repeatEnabled,
                                            scheduledDateMillis = effectiveScheduleDate.time,
                                            isEnabled = true
                                        )
                                        com.example.feature.session.notification.ScheduleValidation.activeWindowBoundsMillis(
                                            previewSchedule,
                                            System.currentTimeMillis()
                                        )
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (previewBounds != null) Color(0xFF22C55E).copy(alpha = 0.10f)
                                                else FocusColors.Primary.copy(alpha = 0.08f)
                                            )
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Starts at ${formatTimeDisplay(startTime)} • Stops at ${formatTimeDisplay(endTime)}",
                                                color = if (previewBounds != null) Color(0xFF22C55E) else FocusColors.Primary,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            val subNote = if (previewBounds != null && isAutoStart) {
                                                val remMins = ((previewBounds.remainingMillis(System.currentTimeMillis()) + 59_999L) / 60_000L).coerceAtLeast(1L)
                                                "Active window now • Auto-starts immediately for ${remMins}m until ${formatTimeDisplay(endTime)}"
                                            } else {
                                                "Timer automatically stops at ${formatTimeDisplay(endTime)} and logs pure study time"
                                            }
                                            Text(
                                                text = subNote,
                                                color = textSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    HorizontalDivider(thickness = 1.dp, color = dividerColor)

                                    // "Break" Row (matching FocusSessionSetupSheet)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showBreakPicker = true }
                                            .padding(horizontal = 16.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Break",
                                            color = textPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            val breakLabel = if (numberOfBreaks <= 0 || breakMinutes <= 0) {
                                                "No breaks"
                                            } else {
                                                "$numberOfBreaks break (${breakMinutes}m each)"
                                            }
                                            Text(
                                                text = breakLabel,
                                                color = textSecondary,
                                                fontSize = 15.sp
                                            )
                                            Icon(
                                                imageVector = Icons.Rounded.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = textSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Card: Schedule Repetition Options (Repeat for Entire Week vs One-Time Schedule)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = BorderStroke(1.dp, cardBorder)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Text(
                                        text = "Schedule Repetition",
                                        color = textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )

                                    // Option 1: Repeat for the Entire Week
                                    val isRepeatWeekSelected = repeatEnabled
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isRepeatWeekSelected) FocusColors.Primary.copy(alpha = 0.12f)
                                                else palette.surfaceVariant.copy(alpha = 0.45f)
                                            )
                                            .border(
                                                width = if (isRepeatWeekSelected) 1.5.dp else 1.dp,
                                                color = if (isRepeatWeekSelected) FocusColors.Primary else cardBorder,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                repeatEnabled = true
                                                selectedDaysList = DAYS.toList()
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                            .testTag("schedule_option_repeat_week"),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Repeat for the Entire Week",
                                                color = if (isRepeatWeekSelected) FocusColors.Primary else textPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Repeat this schedule throughout the week",
                                                color = textSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                        if (isRepeatWeekSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = FocusColors.Primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Option 2: One-Time Schedule
                                    val isOneTimeSelected = !repeatEnabled
                                    val selectedDayLabel = remember(effectiveScheduleDate) {
                                        SimpleDateFormat("EEEE", Locale.ENGLISH).format(effectiveScheduleDate)
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isOneTimeSelected) FocusColors.Primary.copy(alpha = 0.12f)
                                                else palette.surfaceVariant.copy(alpha = 0.45f)
                                            )
                                            .border(
                                                width = if (isOneTimeSelected) 1.5.dp else 1.dp,
                                                color = if (isOneTimeSelected) FocusColors.Primary else cardBorder,
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable {
                                                repeatEnabled = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                            .testTag("schedule_option_one_time"),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "One-Time Schedule",
                                                color = if (isOneTimeSelected) FocusColors.Primary else textPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Apply only to the selected day ($selectedDayLabel) without repeating",
                                                color = textSecondary,
                                                fontSize = 12.sp
                                            )
                                        }
                                        if (isOneTimeSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.Check,
                                                contentDescription = null,
                                                tint = FocusColors.Primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    if (repeatEnabled) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Active days (${selectedDaysList.size}/7)",
                                                color = textSecondary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (selectedDaysList.size < DAYS.size) {
                                                Text(
                                                    text = "Select all 7 days",
                                                    color = FocusColors.Primary,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    modifier = Modifier.clickable {
                                                        selectedDaysList = DAYS.toList()
                                                    }
                                                )
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            DAYS.forEachIndexed { index, day ->
                                                val isSelected = selectedDaysList.contains(day)
                                                val dayBg = if (isSelected) {
                                                    if (isDark) Color(0xFF1E4620) else Color(0xFFDCFCE7)
                                                } else {
                                                    if (isDark) Color(0xFF22252C) else Color(0xFFE2E8F0)
                                                }
                                                val dayBorder = if (isSelected) {
                                                    if (isDark) Color(0xFF2A612D) else Color(0xFF86EFAC)
                                                } else {
                                                    Color.Transparent
                                                }
                                                val dayTextColor = if (isSelected) {
                                                    if (isDark) Color(0xFF86EFAC) else Color(0xFF15803D)
                                                } else {
                                                    textSecondary
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(dayBg)
                                                        .border(1.dp, dayBorder, CircleShape)
                                                        .clickable {
                                                            selectedDaysList = if (isSelected) {
                                                                selectedDaysList - day
                                                            } else {
                                                                DAYS.filter { it in (selectedDaysList + day) }
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = DAY_LABELS[index],
                                                        color = dayTextColor,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // 5. Section: Block Settings (Fixed Blocked Apps Row opening full SelectAppsToBlockSheet!)
                            Text(
                                text = "Block settings",
                                color = textPrimary,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { activeSubView = ScheduleDialogSubView.SELECT_APPS },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = BorderStroke(1.dp, cardBorder)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(64.dp)
                                        .padding(horizontal = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Blocked Apps",
                                            color = textPrimary,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "YouTube, browsers & distracting apps",
                                            color = textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ScheduleBlockedAppsIconStack(
                                            blockedCount = selectedBlockedPackages.size
                                        )
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowRight,
                                            contentDescription = null,
                                            tint = textSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            // 6. Deep Focus Settings (PRO) — Full parity with FocusSessionSetupSheet
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = BorderStroke(1.dp, cardBorder)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { isDeepFocusExpanded = !isDeepFocusExpanded }
                                            .padding(horizontal = 16.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Deep Focus Settings",
                                                color = textPrimary,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(Color(0xFFFEF3C7))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = "PRO",
                                                    color = Color(0xFFB45309),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.ExtraBold
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = if (isDeepFocusExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = "Expand Deep Focus Settings",
                                            tint = textSecondary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    AnimatedVisibility(visible = isDeepFocusExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                        ) {
                                            HorizontalDivider(thickness = 1.dp, color = dividerColor)

                                            // Auto-Start Scheduled Session
                                            ScheduleToggleRow(
                                                title = "Auto-start session",
                                                subtitle = "Automatically start focus session at scheduled time",
                                                checked = isAutoStart,
                                                onCheckedChange = { isAutoStart = it },
                                                textPrimary = textPrimary,
                                                textSecondary = textSecondary,
                                                switchTrackChecked = switchTrackChecked,
                                                switchTrackUnchecked = switchTrackUnchecked
                                            )

                                            HorizontalDivider(thickness = 1.dp, color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                                            // Strict Mode (only for Timer / Pomodoro)
                                            if (selectedMode != "STOPWATCH") {
                                                ScheduleToggleRow(
                                                    title = "Strict mode",
                                                    subtitle = "You cannot end your session early",
                                                    checked = isStrictMode,
                                                    onCheckedChange = {
                                                        isStrictMode = it
                                                        if (it) isBlockUninstall = true
                                                    },
                                                    textPrimary = textPrimary,
                                                    textSecondary = textSecondary,
                                                    switchTrackChecked = switchTrackChecked,
                                                    switchTrackUnchecked = switchTrackUnchecked
                                                )
                                                HorizontalDivider(thickness = 1.dp, color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                                            }

                                            // YouTube Study Mode
                                            ScheduleToggleRow(
                                                title = "YouTube Study Mode",
                                                subtitle = "Allow verified study channels while blocking feed & shorts",
                                                checked = isYouTubeStudyMode,
                                                onCheckedChange = { enabled ->
                                                    if (enabled && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                                        pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                                            title = "YouTube Study Mode",
                                                            description = "YouTube study channels and distractive video feeds",
                                                            onGranted = {
                                                                isYouTubeStudyMode = true
                                                                youtubeOption = SpecialAppOption.STUDY_MODE
                                                                selectedBlockedPackages = selectedBlockedPackages + "com.google.android.youtube"
                                                            }
                                                        )
                                                    } else {
                                                        isYouTubeStudyMode = enabled
                                                        youtubeOption = if (enabled) SpecialAppOption.STUDY_MODE else SpecialAppOption.BLOCK_COMPLETELY
                                                        if (enabled) {
                                                            selectedBlockedPackages = selectedBlockedPackages + "com.google.android.youtube"
                                                        }
                                                    }
                                                },
                                                textPrimary = textPrimary,
                                                textSecondary = textSecondary,
                                                switchTrackChecked = switchTrackChecked,
                                                switchTrackUnchecked = switchTrackUnchecked
                                            )

                                            HorizontalDivider(thickness = 1.dp, color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                                            // Browser Study Mode
                                            ScheduleToggleRow(
                                                title = "Browser Study Mode",
                                                subtitle = "Block distracting websites while allowing study portals",
                                                checked = isBrowserStudyMode,
                                                onCheckedChange = { enabled ->
                                                    if (enabled && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                                        pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                                            title = "Browser Study Mode",
                                                            description = "distracting websites while allowing study sites",
                                                            onGranted = {
                                                                isBrowserStudyMode = true
                                                                browserOption = SpecialAppOption.STUDY_MODE
                                                                selectedBlockedPackages = selectedBlockedPackages + BROWSER_PACKAGES
                                                            }
                                                        )
                                                    } else {
                                                        isBrowserStudyMode = enabled
                                                        browserOption = if (enabled) SpecialAppOption.STUDY_MODE else SpecialAppOption.BLOCK_COMPLETELY
                                                        if (enabled) {
                                                            selectedBlockedPackages = selectedBlockedPackages + BROWSER_PACKAGES
                                                        }
                                                    }
                                                },
                                                textPrimary = textPrimary,
                                                textSecondary = textSecondary,
                                                switchTrackChecked = switchTrackChecked,
                                                switchTrackUnchecked = switchTrackUnchecked
                                            )

                                            HorizontalDivider(thickness = 1.dp, color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                                            // Block Notifications
                                            ScheduleToggleRow(
                                                title = "Block Notifications",
                                                subtitle = "Block notification from blocked apps",
                                                checked = blockNotifications,
                                                onCheckedChange = {
                                                    blockNotifications = it
                                                    onBlockNotificationsChanged(it)
                                                },
                                                textPrimary = textPrimary,
                                                textSecondary = textSecondary,
                                                switchTrackChecked = switchTrackChecked,
                                                switchTrackUnchecked = switchTrackUnchecked
                                            )

                                            HorizontalDivider(thickness = 1.dp, color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                                            // Block Phone Home Screen
                                            ScheduleToggleRow(
                                                title = "Block Phone home screen",
                                                subtitle = null,
                                                checked = isBlockHomeScreen,
                                                onCheckedChange = { isBlockHomeScreen = it },
                                                textPrimary = textPrimary,
                                                textSecondary = textSecondary,
                                                switchTrackChecked = switchTrackChecked,
                                                switchTrackUnchecked = switchTrackUnchecked
                                            )

                                            HorizontalDivider(thickness = 1.dp, color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                                            // Block FocusShield App Uninstall
                                            ScheduleToggleRow(
                                                title = "Block FocusShield app uninstall",
                                                subtitle = "Prevents uninstalling or tampering with FocusShield",
                                                checked = isBlockUninstall,
                                                onCheckedChange = { enabled ->
                                                    if (enabled && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                                        pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                                            title = "App Uninstall Protection",
                                                            description = "system settings to prevent FocusShield from being uninstalled",
                                                            onGranted = { isBlockUninstall = true }
                                                        )
                                                    } else {
                                                        isBlockUninstall = enabled
                                                    }
                                                },
                                                textPrimary = textPrimary,
                                                textSecondary = textSecondary,
                                                switchTrackChecked = switchTrackChecked,
                                                switchTrackUnchecked = switchTrackUnchecked
                                            )

                                            HorizontalDivider(thickness = 1.dp, color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                                            // Block Split Screen
                                            ScheduleToggleRow(
                                                title = "Block split screen",
                                                subtitle = "Cannot use blocked apps in split screen",
                                                checked = isBlockSplitScreen,
                                                onCheckedChange = { enabled ->
                                                    if (enabled && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                                        pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                                            title = "Split Screen Blocker",
                                                            description = "split screen multi-window mode",
                                                            onGranted = { isBlockSplitScreen = true }
                                                        )
                                                    } else {
                                                        isBlockSplitScreen = enabled
                                                    }
                                                },
                                                textPrimary = textPrimary,
                                                textSecondary = textSecondary,
                                                switchTrackChecked = switchTrackChecked,
                                                switchTrackUnchecked = switchTrackUnchecked
                                            )

                                            HorizontalDivider(thickness = 1.dp, color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                                            // Block Floating Window
                                            ScheduleToggleRow(
                                                title = "Block floating window",
                                                subtitle = "Cannot use blocked apps in floating window",
                                                checked = isBlockFloatingWindow,
                                                onCheckedChange = { enabled ->
                                                    if (enabled && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                                        pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                                            title = "Floating Window Blocker",
                                                            description = "picture-in-picture and floating windows",
                                                            onGranted = { isBlockFloatingWindow = true }
                                                        )
                                                    } else {
                                                        isBlockFloatingWindow = enabled
                                                    }
                                                },
                                                textPrimary = textPrimary,
                                                textSecondary = textSecondary,
                                                switchTrackChecked = switchTrackChecked,
                                                switchTrackUnchecked = switchTrackUnchecked
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        // Sticky Bottom Save Footer
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = bgColor,
                            tonalElevation = 6.dp,
                            shadowElevation = 10.dp
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .imePadding()
                            ) {
                                HorizontalDivider(thickness = 1.dp, color = dividerColor)
                                Button(
                                    onClick = { saveSchedule() },
                                    shape = RoundedCornerShape(32.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = saveBtnBg,
                                        contentColor = saveBtnText
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 20.dp, vertical = 12.dp)
                                        .padding(bottom = 12.dp)
                                        .height(54.dp)
                                        .testTag("save_schedule_button")
                                ) {
                                    Text(
                                        text = "Save",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = saveBtnText
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Pickers / Sub-dialogs
    if (activePicker == ActivePicker.START_TIME || activePicker == ActivePicker.END_TIME) {
        val isStart = activePicker == ActivePicker.START_TIME
        TimePickerDialog(
            initialTime = if (isStart) startTime else endTime,
            title = if (isStart) "Select Start Time" else "Select End Time",
            cardBg = cardBg,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onDismiss = { activePicker = null },
            onTimeSelected = { newTime ->
                if (isStart) {
                    startTime = newTime
                    if (!userManuallySelectedEmoji) {
                        selectedEmoji = getEmojiForTime(newTime)
                    }
                } else {
                    endTime = newTime
                }
                activePicker = null
            }
        )
    }

    if (showBreakPicker) {
        BreakPickerDialog(
            currentNumberOfBreaks = numberOfBreaks,
            currentBreakMinutes = breakMinutes,
            cardBg = cardBg,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            onDismiss = { showBreakPicker = false },
            onSelect = { breaksCount, mins ->
                numberOfBreaks = breaksCount
                breakMinutes = mins
                showBreakPicker = false
            }
        )
    }

    if (showEmojiPicker) {
        EmojiPickerDialog(
            currentEmoji = selectedEmoji,
            cardBg = cardBg,
            textPrimary = textPrimary,
            onDismiss = { showEmojiPicker = false },
            onSelect = {
                selectedEmoji = it
                userManuallySelectedEmoji = true
                showEmojiPicker = false
            }
        )
    }

    if (showAddTagDialog) {
        AlertDialog(
            onDismissRequest = { showAddTagDialog = false },
            containerColor = cardBg,
            title = {
                Text(
                    text = "New Subject Tag",
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it },
                    placeholder = { Text("e.g., Physics, Organic Chemistry, Coding") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newTagText.trim()
                        if (trimmed.isNotEmpty()) {
                            coroutineScope.launch {
                                subjectRepository.addSubject(trimmed)
                            }
                            tag = trimmed
                            showAddTagDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FocusColors.Primary)
                ) {
                    Text("Add Tag", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTagDialog = false }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }

    editingSubject?.let { subjectToEdit ->
        AlertDialog(
            onDismissRequest = { editingSubject = null },
            containerColor = cardBg,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Manage Subject Tag",
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                subjectRepository.deleteSubject(subjectToEdit.id)
                            }
                            if (tag.equals(subjectToEdit.name, ignoreCase = true)) {
                                tag = "Untagged"
                            }
                            editingSubject = null
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete Subject Tag",
                            tint = Color(0xFFEF4444)
                        )
                    }
                }
            },
            text = {
                OutlinedTextField(
                    value = editSubjectText,
                    onValueChange = { editSubjectText = it },
                    placeholder = { Text("Subject tag name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = editSubjectText.trim()
                        if (trimmed.isNotEmpty()) {
                            coroutineScope.launch {
                                subjectRepository.renameSubject(subjectToEdit, trimmed)
                            }
                            if (tag.equals(subjectToEdit.name, ignoreCase = true)) {
                                tag = trimmed
                            }
                            editingSubject = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FocusColors.Primary)
                ) {
                    Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSubject = null }) {
                    Text("Cancel", color = textSecondary)
                }
            }
        )
    }

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

@Composable
private fun ScheduleModeSegmentedSelector(
    selectedMode: String,
    onModeSelected: (String) -> Unit
) {
    val palette = LocalFocusColors.current
    val modes = listOf(
        "TIMER" to "Timer",
        "STOPWATCH" to "Stopwatch",
        "POMODORO" to "Pomodoro"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        modes.forEach { (modeKey, label) ->
            val isSelected = selectedMode.equals(modeKey, ignoreCase = true)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) palette.surface else Color.Transparent)
                    .clickable { onModeSelected(modeKey) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (isSelected) palette.textPrimary else palette.textSecondary,
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ScheduleBlockedAppsIconStack(blockedCount: Int) {
    val context = LocalContext.current
    var browserAppIcon by remember { mutableStateOf<InstalledAppItem?>(null) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val allApps = InstalledAppsProvider.getInstalledUserApps(context)
            val browserPackages = listOf(
                "com.android.chrome",
                "com.opera.browser",
                "com.brave.browser",
                "org.mozilla.firefox",
                "com.sec.android.app.sbrowser",
                "com.microsoft.emmx"
            )
            browserAppIcon = allApps.firstOrNull { it.packageName in browserPackages }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy((-6).dp)
    ) {
        // 1. YouTube badge
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color(0xFFEF4444))
                .border(1.5.dp, FocusColors.Surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp)
            )
        }

        // 2. Real Browser icon or Globe badge
        val currentBrowser = browserAppIcon
        if (currentBrowser?.iconBitmap != null) {
            Image(
                bitmap = currentBrowser.iconBitmap,
                contentDescription = currentBrowser.appName,
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, FocusColors.Surface, CircleShape)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2563EB))
                    .border(1.5.dp, FocusColors.Surface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Language,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // 3. Count pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(FocusColors.SurfaceSubtle)
                .border(1.5.dp, FocusColors.Surface, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (blockedCount == 0) "Select" else "$blockedCount apps",
                color = FocusColors.TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ScheduleToggleRow(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    textPrimary: Color,
    textSecondary: Color,
    switchTrackChecked: Color,
    switchTrackUnchecked: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                color = textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    color = textSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = switchTrackChecked,
                uncheckedThumbColor = textSecondary,
                uncheckedTrackColor = switchTrackUnchecked
            )
        )
    }
}

private enum class ActivePicker { START_TIME, END_TIME }

private fun formatTimeDisplay(value: String): String {
    return runCatching {
        SimpleDateFormat("HH:mm", Locale.getDefault()).parse(value)?.let {
            SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(it)
        } ?: value
    }.getOrDefault(value)
}

@Composable
private fun TimePickerDialog(
    initialTime: String,
    title: String,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onDismiss: () -> Unit,
    onTimeSelected: (String) -> Unit
) {
    val initialMinutes = com.example.feature.session.notification.ScheduleTime
        .parseToMinutesOrNull(initialTime)
    val parsedHour = initialMinutes?.let { it / 60 } ?: 21
    val parsedMinute = initialMinutes?.let { it % 60 } ?: 0

    var isAm by remember(initialTime) { mutableStateOf(parsedHour < 12) }
    var hour12 by remember(initialTime) {
        val h = parsedHour % 12
        mutableIntStateOf(if (h == 0) 12 else h)
    }
    var minute by remember(initialTime) { mutableIntStateOf(parsedMinute) }
    var hourInputText by remember(initialTime) {
        val h = parsedHour % 12
        mutableStateOf(String.format(Locale.US, "%02d", if (h == 0) 12 else h))
    }
    var minuteInputText by remember(initialTime) {
        mutableStateOf(String.format(Locale.US, "%02d", parsedMinute))
    }

    fun stepHour(delta: Int) {
        var newHour = hour12 + delta
        if (newHour > 12) {
            newHour = 1
        } else if (newHour < 1) {
            newHour = 12
        }
        // Toggle AM/PM when crossing 11 <-> 12
        if ((hour12 == 11 && newHour == 12) || (hour12 == 12 && newHour == 11)) {
            isAm = !isAm
        }
        hour12 = newHour
        hourInputText = String.format(Locale.US, "%02d", hour12)
    }

    fun stepMinute(delta: Int) {
        val totalDelta = minute + delta
        when {
            totalDelta < 0 -> {
                stepHour(-1)
                minute = (60 + (totalDelta % 60)) % 60
            }
            totalDelta >= 60 -> {
                stepHour(1)
                minute = totalDelta % 60
            }
            else -> {
                minute = totalDelta
            }
        }
        minuteInputText = String.format(Locale.US, "%02d", minute)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title, color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hour Column
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Hour", color = textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { stepHour(-1) }) {
                                Text("◀", color = textSecondary)
                            }
                            OutlinedTextField(
                                value = hourInputText,
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }.take(2)
                                    hourInputText = digits
                                    val parsed = digits.toIntOrNull()
                                    if (parsed != null && parsed in 1..12) {
                                        hour12 = parsed
                                    } else if (parsed != null && parsed in 13..23) {
                                        hour12 = parsed - 12
                                        isAm = false
                                    } else if (parsed == 0) {
                                        hour12 = 12
                                        isAm = true
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = TextStyle(
                                    color = textPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.width(68.dp)
                            )
                            IconButton(onClick = { stepHour(1) }) {
                                Text("▶", color = textSecondary)
                            }
                        }
                    }

                    Text(":", color = textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold)

                    // Minute Column (supports exact 1-min and 5-min steps + direct input)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Minute", color = textSecondary, fontSize = 12.sp)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { stepMinute(-1) }) {
                                Text("◀", color = textSecondary)
                            }
                            OutlinedTextField(
                                value = minuteInputText,
                                onValueChange = { raw ->
                                    val digits = raw.filter { it.isDigit() }.take(2)
                                    minuteInputText = digits
                                    val parsed = digits.toIntOrNull()
                                    if (parsed != null && parsed in 0..59) {
                                        minute = parsed
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = TextStyle(
                                    color = textPrimary,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.width(68.dp)
                            )
                            IconButton(onClick = { stepMinute(1) }) {
                                Text("▶", color = textSecondary)
                            }
                        }
                    }
                }

                // Quick minute presets & ±5m steppers
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf("-5m" to -5, ":00" to 0, ":15" to 15, ":30" to 30, ":45" to 45, "+5m" to 5).forEach { (label, value) ->
                        val isPreset = label.startsWith(":")
                        val isSelected = isPreset && minute == value
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) FocusColors.Primary
                                    else if (LocalFocusColors.current.isDark) Color(0xFF22242B) else Color(0xFFE2E8F0)
                                )
                                .clickable {
                                    if (isPreset) {
                                        minute = value
                                        minuteInputText = String.format(Locale.US, "%02d", minute)
                                    } else {
                                        stepMinute(value)
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // AM / PM Toggle
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (LocalFocusColors.current.isDark) Color(0xFF22242B) else Color(0xFFE2E8F0))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isAm) FocusColors.Primary else Color.Transparent)
                            .clickable { isAm = true }
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Text("AM", color = if (isAm) Color.White else textSecondary, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isAm) FocusColors.Primary else Color.Transparent)
                            .clickable { isAm = false }
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                    ) {
                        Text("PM", color = if (!isAm) Color.White else textSecondary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val safeHour12 = hourInputText.toIntOrNull()?.coerceIn(1, 12) ?: hour12.coerceIn(1, 12)
                    val safeMinute = minuteInputText.toIntOrNull()?.coerceIn(0, 59) ?: minute.coerceIn(0, 59)
                    val final24Hour = if (isAm) {
                        if (safeHour12 == 12) 0 else safeHour12
                    } else {
                        if (safeHour12 == 12) 12 else safeHour12 + 12
                    }
                    val formatted = String.format(Locale.US, "%02d:%02d", final24Hour, safeMinute)
                    onTimeSelected(formatted)
                }
            ) {
                Text("Set Time", color = FocusColors.Primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSecondary)
            }
        },
        containerColor = cardBg
    )
}

@Composable
private fun BreakPickerDialog(
    currentNumberOfBreaks: Int,
    currentBreakMinutes: Int,
    cardBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    onDismiss: () -> Unit,
    onSelect: (Int, Int) -> Unit
) {
    var breaksCount by remember { mutableIntStateOf(currentNumberOfBreaks) }
    var breakMins by remember { mutableIntStateOf(currentBreakMinutes.coerceAtLeast(5)) }
    val countOptions = listOf(0, 1, 2, 3, 4)
    val durationOptions = listOf(5, 10, 15, 20, 30)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Breaks", color = textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "Number of breaks",
                    color = textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    countOptions.forEach { count ->
                        val isSelected = breaksCount == count
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) FocusColors.Primary else FocusColors.SurfaceSubtle)
                                .clickable { breaksCount = count },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$count",
                                color = if (isSelected) Color.White else textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (breaksCount > 0) {
                    Text(
                        text = "Break duration",
                        color = textSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        durationOptions.forEach { mins ->
                            val isSelected = mins == breakMins
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) FocusColors.Primary.copy(alpha = 0.15f) else Color.Transparent)
                                    .clickable { breakMins = mins }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$mins mins",
                                    color = if (isSelected) FocusColors.Primary else textPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 15.sp
                                )
                                if (isSelected) {
                                    Icon(Icons.Rounded.Check, contentDescription = null, tint = FocusColors.Primary, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSelect(breaksCount, if (breaksCount == 0) 0 else breakMins) },
                colors = ButtonDefaults.buttonColors(containerColor = FocusColors.Primary)
            ) {
                Text("Confirm", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = textSecondary)
            }
        },
        containerColor = cardBg
    )
}

@Composable
private fun EmojiPickerDialog(
    currentEmoji: String,
    cardBg: Color,
    textPrimary: Color,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Icon", color = textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val chunked = DEFAULT_EMOJIS.chunked(4)
                chunked.forEach { rowEmojis ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        rowEmojis.forEach { emoji ->
                            val isSelected = emoji == currentEmoji
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) FocusColors.Primary.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 0.dp,
                                        color = if (isSelected) FocusColors.Primary else Color.Transparent,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSelect(emoji) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 24.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = FocusColors.Primary)
            }
        },
        containerColor = cardBg
    )
}
