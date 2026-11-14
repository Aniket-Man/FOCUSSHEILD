package com.example.feature.planner

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.FocusShieldApp
import com.example.R
import com.example.core.accessibility.AccessibilityFeaturePromptInfo
import com.example.core.accessibility.AccessibilityHelper
import com.example.core.accessibility.AccessibilityPermissionRequiredDialog
import com.example.core.design.FocusColors
import com.example.core.design.FocusSpacing
import com.example.core.design.FocusShapes
import com.example.core.design.LocalFocusMotion
import com.example.core.ui.FocusDestructiveDialog
import com.example.core.ui.FocusStatusMessage
import com.example.core.ui.FocusSwitchRow
import com.example.core.ui.FocusActionRow
import com.example.core.ui.FocusChoiceRow
import com.example.core.ui.FocusNameDialog
import com.example.core.ui.localizedSessionTag
import com.example.core.design.LocalFocusColors
import com.example.core.util.InstalledAppItem
import com.example.core.util.InstalledAppsProvider
import com.example.data.local.entity.BlockedAppEntity
import com.example.data.local.entity.FocusScheduleEntity
import com.example.data.local.entity.SubjectEntity
import com.example.data.repository.FocusScheduleRepository
import com.example.feature.channels.YouTubeStudyChannelContent
import com.example.feature.session.SpecialAppOption
import com.example.feature.session.ui.SelectAppsToBlockSheet
import com.example.feature.websiteblocker.ui.WebsiteBlockerSheetContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DAYS = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
private val CALENDAR_DAYS = listOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY, Calendar.SUNDAY)

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
    onDefaultsSaveFailed: () -> Unit = {},
    onFollowUpSaveFailed: () -> Unit = {},
    onSave: suspend (
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
        blockNotifications: Boolean,
        saveDefaults: suspend () -> Unit
    ) -> FocusScheduleRepository.SaveResult
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as FocusShieldApp }
    val preferencesRepository = remember(app) { app.preferencesRepository }
    val subjectRepository = remember(app) { app.subjectRepository }
    val approvedChannels by app.studyChannelRepository.approvedChannels
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val coroutineScope = rememberCoroutineScope()
    val scheduleKey = initialSchedule?.id ?: "new_schedule"

    val customSubjects by subjectRepository.activeSubjects.collectAsStateWithLifecycle(initialValue = emptyList())
    val prefs by preferencesRepository.preferencesFlow.collectAsStateWithLifecycle(initialValue = null)
    val locale = LocalConfiguration.current.locales[0] ?: Locale.getDefault()

    var activeSubView by rememberSaveable(scheduleKey) { mutableStateOf(ScheduleDialogSubView.MAIN) }
    var pendingAccessibilityPrompt by remember { mutableStateOf<AccessibilityFeaturePromptInfo?>(null) }

    var title by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule?.title ?: "") }
    var startTime by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule?.startTime ?: "21:00") }
    var endTime by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule?.endTime ?: "22:00") }
    var selectedEmoji by rememberSaveable(scheduleKey) { mutableStateOf(getEmojiForTime(initialSchedule?.startTime ?: "21:00")) }
    var userManuallySelectedEmoji by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule != null) }
    var tag by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule?.subjectName.takeIf { !it.isNullOrBlank() } ?: "Untagged") }
    var isAutoStart by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule?.isAutoStartSession ?: true) }
    var blockNotifications by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule?.blockNotifications ?: initialBlockNotifications) }
    var selectedMode by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule?.mode ?: "TIMER") }

    val effectiveScheduleDate = remember(initialSchedule?.scheduledDateMillis, selectedDateMillis) {
        val initMillis = initialSchedule?.scheduledDateMillis ?: 0L
        if (initMillis > 0L) Date(initMillis) else Date(selectedDateMillis)
    }
    var repeatEnabled by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule?.repeatEnabled ?: true) }
    var breakMinutes by rememberSaveable(scheduleKey) { mutableIntStateOf(initialSchedule?.breakMinutes ?: 5) }
    var description by rememberSaveable(scheduleKey) { mutableStateOf(initialSchedule?.description ?: "") }

    // Default blocked apps: use schedule's saved apps when editing, or default distracting apps + active app limits when creating
    var selectedBlockedPackages by rememberSaveable(
        scheduleKey,
        stateSaver = Saver<Set<String>, ArrayList<String>>(save = { ArrayList(it) }, restore = { it.toSet() })
    ) {
        val fromSchedule = initialSchedule?.blockedAppPackages
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
        val initialSet = if (initialSchedule != null) {
            fromSchedule.orEmpty()
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
    var isBlockUninstall by rememberSaveable(scheduleKey) { mutableStateOf(false) }
    var isBlockSplitScreen by rememberSaveable(scheduleKey) { mutableStateOf(false) }
    var isBlockFloatingWindow by rememberSaveable(scheduleKey) { mutableStateOf(false) }
    var youtubeOption by rememberSaveable(scheduleKey) { mutableStateOf(SpecialAppOption.STUDY_MODE) }
    var browserOption by rememberSaveable(scheduleKey) { mutableStateOf(SpecialAppOption.STUDY_MODE) }
    var prefsHydrated by rememberSaveable(scheduleKey) { mutableStateOf(false) }

    LaunchedEffect(prefs) {
        val prefs = prefs ?: return@LaunchedEffect
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

    var titleError by rememberSaveable(scheduleKey) { mutableStateOf<String?>(null) }
    var validationMessage by rememberSaveable(scheduleKey) {
        mutableStateOf(if (initialSchedule != null && com.example.feature.session.notification.ScheduleTime.parseDaysOfWeekOrNull(initialSchedule.daysOfWeek) == null) context.getString(R.string.schedule_days_error) else null)
    }
    var isSaving by remember { mutableStateOf(false) }
    val titleFocusRequester = remember { FocusRequester() }
    val mainScrollState = rememberScrollState()
    var activePicker by rememberSaveable(scheduleKey) { mutableStateOf<ActivePicker?>(null) }
    var showBreakPicker by rememberSaveable(scheduleKey) { mutableStateOf(false) }
    var showEmojiPicker by rememberSaveable(scheduleKey) { mutableStateOf(false) }
    var showAddTagDialog by rememberSaveable(scheduleKey) { mutableStateOf(false) }
    var editingSubject by remember { mutableStateOf<SubjectEntity?>(null) }
    var subjectToDelete by remember { mutableStateOf<SubjectEntity?>(null) }

    var selectedDaysList by rememberSaveable(scheduleKey) {
        val storedDays = initialSchedule?.let { com.example.feature.session.notification.ScheduleTime.parseDaysOfWeekOrNull(it.daysOfWeek) }
        mutableStateOf(if (initialSchedule == null) DAYS.toList() else DAYS.filterIndexed { index, _ -> CALENDAR_DAYS[index] in storedDays.orEmpty() })
    }

    fun saveSchedule() {
        if (isSaving || !prefsHydrated) return
        if (title.isBlank()) {
            titleError = context.getString(R.string.schedule_name_error)
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
            validationMessage = context.getString(R.string.schedule_days_error)
            coroutineScope.launch { mainScrollState.scrollTo(0) }
            return
        }
        val startMinutes = com.example.feature.session.notification.ScheduleTime
            .parseToMinutesOrNull(startTime)
        val endMinutes = com.example.feature.session.notification.ScheduleTime
            .parseToMinutesOrNull(endTime)
        if (startMinutes == null || endMinutes == null) {
            validationMessage = context.getString(R.string.schedule_time_error)
            coroutineScope.launch { mainScrollState.scrollTo(0) }
            return
        }
        if (startMinutes == endMinutes) {
            validationMessage = context.getString(R.string.schedule_same_time_error)
            coroutineScope.launch { mainScrollState.scrollTo(0) }
            return
        }
        if (selectedMode !in listOf("TIMER", "STOPWATCH", "POMODORO")) {
            validationMessage = context.getString(R.string.schedule_mode_error)
            coroutineScope.launch { mainScrollState.scrollTo(0) }
            return
        }
        if (breakMinutes !in 0..180) {
            validationMessage = context.getString(R.string.schedule_break_choice_error)
            coroutineScope.launch { mainScrollState.scrollTo(0) }
            return
        }

        validationMessage = null
        isSaving = true
        // Await the actual schedule write; a rejected or failed save keeps every entered value.
        coroutineScope.launch {
            try {
                when (val outcome = onSave(
                    title.trim(), days, startTime, endTime, isAutoStart, selectedMode,
                    tag.trim().ifBlank { "Untagged" }, repeatEnabled, effectiveScheduleDate.time,
                    breakMinutes, description.trim(), selectedBlockedPackages, blockNotifications,
                    {
                        preferencesRepository.updateSessionProtectionDefaults(
                            appBlocking = selectedBlockedPackages.isNotEmpty(),
                            strictMode = isStrictMode && selectedMode != "STOPWATCH",
                            studyChannels = isYouTubeStudyMode,
                            manualWebsites = isBrowserStudyMode,
                            blockUninstall = isBlockUninstall,
                            blockSplitScreen = isBlockSplitScreen,
                            blockFloatingWindow = isBlockFloatingWindow,
                            blockNotifications = blockNotifications
                        )
                    }
                )) {
                    is FocusScheduleRepository.SaveResult.Saved -> {
                        outcome.defaultsFailure?.let {
                            android.util.Log.e("ScheduleEditor", "Schedule saved but defaults failed", it)
                            onDefaultsSaveFailed()
                        }
                        outcome.followUpFailure?.let { onFollowUpSaveFailed() }
                        onBlockNotificationsChanged(blockNotifications)
                        onDismiss()
                    }
                    is FocusScheduleRepository.SaveResult.Rejected -> {
                        validationMessage = outcome.reason
                        mainScrollState.scrollTo(0)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                android.util.Log.e("ScheduleEditor", "Unable to save schedule settings", error)
                validationMessage = context.getString(R.string.schedule_save_error)
                mainScrollState.scrollTo(0)
            } finally {
                isSaving = false
            }
        }
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
    val saveBtnText = MaterialTheme.colorScheme.onPrimary

    val motion = LocalFocusMotion.current
    val transitionOffset = with(LocalDensity.current) { 12.dp.roundToPx() }
    val modalTitle = stringResource(if (initialSchedule != null) R.string.schedule_edit_title else R.string.schedule_new_title)

    val handleBack: () -> Unit = {
        if (!isSaving) {
            val back = activeSubView.backTarget()
            if (back != null) activeSubView = back else onDismiss()
        }
    }

    Dialog(
        onDismissRequest = handleBack,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .imePadding()
            .semantics { paneTitle = modalTitle }
            .testTag("focus_schedule_dialog"),
        color = bgColor
    ) {
        AnimatedContent(
            targetState = activeSubView,
            transitionSpec = {
                if (!motion.reduceMotion && (targetState == ScheduleDialogSubView.MAIN || initialState == ScheduleDialogSubView.MAIN)) {
                    ContentTransform(
                        targetContentEnter = slideInVertically(tween(220)) { transitionOffset } + fadeIn(tween(220)),
                        initialContentExit = slideOutVertically(tween(150)) { -transitionOffset } + fadeOut(tween(150)),
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
                        approvedChannelCount = approvedChannels.size,
                        onYoutubeOptionChange = { option ->
                            if (option == SpecialAppOption.STUDY_MODE && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                    title = context.getString(R.string.schedule_youtube_label),
                                    description = context.getString(R.string.schedule_youtube_permission),
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
                                    title = context.getString(R.string.schedule_browser_label),
                                    description = context.getString(R.string.schedule_browser_permission),
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
                                .padding(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = modalTitle,
                                color = textPrimary,
                                style = MaterialTheme.typography.headlineLarge,
                                modifier = Modifier.weight(1f).semantics { heading() }
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = onDismiss,
                                    enabled = !isSaving,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = stringResource(R.string.action_close),
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
                                .verticalScroll(mainScrollState)
                                .padding(horizontal = FocusSpacing.screenHorizontal),
                            verticalArrangement = Arrangement.spacedBy(FocusSpacing.base)
                        ) {
                            // 1. Emoji Box + Schedule Name Input Box
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { showEmojiPicker = true },
                                    enabled = !isSaving,
                                    modifier = Modifier
                                        .size(58.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(cardBg)
                                        .border(1.dp, FocusColors.ControlBorder, RoundedCornerShape(14.dp))
                                        .semantics { contentDescription = context.getString(R.string.schedule_choose_icon) }
                                ) {
                                    Text(
                                        text = selectedEmoji,
                                        fontSize = 28.sp
                                    )
                                }

                                    OutlinedTextField(
                                        value = title,
                                        onValueChange = {
                                            title = it
                                            titleError = null
                                        },
                                        label = { Text(stringResource(R.string.schedule_name_label)) },
                                        placeholder = { Text(stringResource(R.string.schedule_name_example)) },
                                        supportingText = { titleError?.let { Text(it) } },
                                         isError = titleError != null,
                                         enabled = !isSaving,
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodyLarge,
                                        shape = FocusShapes.medium,
                                        modifier = Modifier
                                            .weight(1f)
                                            .focusRequester(titleFocusRequester)
                                            .semantics { titleError?.let { error(it) } }
                                            .testTag("schedule_title_input")
                                    )
                            }
                            LaunchedEffect(titleError) {
                                if (titleError != null) titleFocusRequester.requestFocus()
                            }
                            FocusStatusMessage(validationMessage, isError = true, onDismiss = { validationMessage = null })
                            ScheduleModeSegmentedSelector(selectedMode, onModeSelected = { selectedMode = it }, enabled = !isSaving)

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
                                    Text(stringResource(R.string.schedule_subject_section), color = textPrimary,
                                        style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                                    val presetNames = remember(customSubjects) {
                                        val existing = customSubjects.map { it.name.lowercase(Locale.ENGLISH) }.toSet()
                                        listOf("Study", "Exam", "Revision", "Deep Work").filter { it.lowercase(Locale.ENGLISH) !in existing }
                                    }
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth().selectableGroup(),
                                        horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm),
                                        verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)
                                    ) {
                                        (listOf("Untagged") + presetNames).forEach { presetTag ->
                                            FilterChip(
                                                selected = tag.equals(presetTag, ignoreCase = true),
                                                onClick = { tag = presetTag },
                                                enabled = !isSaving,
                                                label = { Text(localizedSessionTag(presetTag)) }
                                            )
                                        }
                                    }
                                    customSubjects.forEach { subject ->
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                                            FilterChip(
                                                selected = tag.equals(subject.name, ignoreCase = true),
                                                onClick = { tag = subject.name },
                                                enabled = !isSaving,
                                                label = { Text(subject.name) },
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(onClick = { editingSubject = subject }, enabled = !isSaving) {
                                                Icon(Icons.Rounded.Edit, stringResource(R.string.schedule_edit_tag, subject.name), tint = textSecondary)
                                            }
                                        }
                                    }
                                    OutlinedButton(onClick = { showAddTagDialog = true }, enabled = !isSaving) {
                                        Icon(Icons.Rounded.Add, contentDescription = null)
                                        Spacer(Modifier.width(FocusSpacing.sm))
                                        Text(stringResource(R.string.schedule_add_tag))
                                    }

                                    // Topic / Chapter Input Field
                                    OutlinedTextField(
                                        value = description,
                                        onValueChange = { description = it },
                                        label = { Text(stringResource(R.string.schedule_topic_label)) },
                                        placeholder = { Text(stringResource(R.string.schedule_topic_example)) },
                                        enabled = !isSaving,
                                        minLines = 2,
                                        shape = FocusShapes.medium,
                                        textStyle = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // 4. Section: Schedule Timing & Repeat
                            Column(verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)) {
                                Text(
                                    text = stringResource(R.string.schedule_timing_section),
                                    color = textPrimary,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.semantics { heading() }
                                )
                                val durationMins = remember(startTime, endTime) {
                                    com.example.feature.session.notification.ScheduleTime.durationMinutesOrNull(startTime, endTime)
                                }
                                if (durationMins != null) {
                                    Text(
                                        text = pluralStringResource(R.plurals.schedule_window_minutes, durationMins, durationMins),
                                        color = FocusColors.Primary,
                                        style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = "tnum")
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
                                    FocusActionRow(
                                        title = stringResource(R.string.schedule_start_label),
                                        value = formatTimeDisplay(startTime),
                                        enabled = !isSaving,
                                        onClick = { activePicker = ActivePicker.START_TIME }
                                    )

                                    HorizontalDivider(thickness = 1.dp, color = dividerColor)

                                    FocusActionRow(
                                        title = stringResource(R.string.schedule_end_label),
                                        value = formatTimeDisplay(endTime),
                                        enabled = !isSaving,
                                        onClick = { activePicker = ActivePicker.END_TIME }
                                    )

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
                                                if (previewBounds != null) FocusColors.EmeraldLight else FocusColors.PrimaryContainer
                                            )
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = stringResource(R.string.schedule_window_summary, formatTimeDisplay(startTime), formatTimeDisplay(endTime)),
                                                color = if (previewBounds != null) FocusColors.EmeraldText else FocusColors.OnPrimaryContainer,
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            val subNote = if (previewBounds != null && isAutoStart) {
                                                val remMins = ((previewBounds.remainingMillis(System.currentTimeMillis()) + 59_999L) / 60_000L).coerceAtLeast(1L)
                                                stringResource(R.string.schedule_window_active, remMins, formatTimeDisplay(endTime))
                                            } else {
                                                stringResource(R.string.schedule_window_stop_note, formatTimeDisplay(endTime))
                                            }
                                            Text(
                                                text = subNote,
                                                color = textSecondary,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }

                                    HorizontalDivider(thickness = 1.dp, color = dividerColor)

                                    FocusActionRow(
                                        title = stringResource(R.string.schedule_manual_break_label),
                                        value = if (breakMinutes <= 0) stringResource(R.string.schedule_no_breaks)
                                            else pluralStringResource(R.plurals.schedule_break_minutes, breakMinutes, breakMinutes),
                                        description = stringResource(R.string.schedule_break_hint),
                                        enabled = !isSaving,
                                        onClick = { showBreakPicker = true }
                                    )
                                }
                            }

                            // Stored day keys stay canonical; only the visible day names are localized.
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
                                        text = stringResource(R.string.schedule_repeat_section),
                                        color = textPrimary,
                                        style = MaterialTheme.typography.titleMedium,
                                        modifier = Modifier.semantics { heading() }
                                    )

                                    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(FocusSpacing.sm)) {
                                        FocusChoiceRow(
                                            title = stringResource(R.string.schedule_repeat_label),
                                            description = stringResource(R.string.schedule_repeat_body),
                                            selected = repeatEnabled,
                                            onClick = { repeatEnabled = true },
                                            enabled = !isSaving,
                                            modifier = Modifier.testTag("schedule_option_repeat_week")
                                        )
                                        val selectedDateLabel = remember(effectiveScheduleDate, locale) {
                                            java.text.DateFormat.getDateInstance(java.text.DateFormat.FULL, locale).format(effectiveScheduleDate)
                                        }
                                        FocusChoiceRow(
                                            title = stringResource(R.string.schedule_once_label),
                                            description = stringResource(R.string.schedule_once_body, selectedDateLabel),
                                            selected = !repeatEnabled,
                                            onClick = { repeatEnabled = false },
                                            enabled = !isSaving,
                                            modifier = Modifier.testTag("schedule_option_one_time")
                                        )
                                    }

                                    if (repeatEnabled) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = stringResource(R.string.schedule_active_days, selectedDaysList.size),
                                                color = textSecondary,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (selectedDaysList.size < DAYS.size) {
                                                TextButton(onClick = { selectedDaysList = DAYS.toList() }, enabled = !isSaving) {
                                                    Text(stringResource(R.string.schedule_select_all_days))
                                                }
                                            }
                                        }

                                        val daySymbols = remember(locale) { DateFormatSymbols(locale) }
                                        FlowRow(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm),
                                            verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)
                                        ) {
                                            DAYS.forEachIndexed { index, day ->
                                                val isSelected = selectedDaysList.contains(day)
                                                val fullDay = daySymbols.weekdays[CALENDAR_DAYS[index]]
                                                FilterChip(
                                                    selected = isSelected,
                                                    enabled = !isSaving,
                                                    onClick = {
                                                            selectedDaysList = if (isSelected) {
                                                                selectedDaysList - day
                                                            } else {
                                                                DAYS.filter { it in (selectedDaysList + day) }
                                                            }
                                                    },
                                                    label = { Text(daySymbols.shortWeekdays[CALENDAR_DAYS[index]]) },
                                                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = fullDay }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // 5. Section: Block Settings (Fixed Blocked Apps Row opening full SelectAppsToBlockSheet!)
                            Text(
                                text = stringResource(R.string.schedule_block_section),
                                color = textPrimary,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(top = FocusSpacing.xs).semantics { heading() }
                            )

                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = BorderStroke(1.dp, cardBorder)
                            ) {
                                FocusActionRow(
                                    title = stringResource(R.string.schedule_block_apps),
                                    description = stringResource(R.string.schedule_block_apps_body),
                                    value = if (selectedBlockedPackages.isEmpty()) stringResource(R.string.schedule_select_apps)
                                        else pluralStringResource(R.plurals.schedule_blocked_app_count, selectedBlockedPackages.size, selectedBlockedPackages.size),
                                    enabled = !isSaving,
                                    onClick = { activeSubView = ScheduleDialogSubView.SELECT_APPS }
                                )
                            }

                            // 6. Deep Focus Settings (PRO) — Full parity with FocusSessionSetupSheet
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = cardBg),
                                border = BorderStroke(1.dp, cardBorder)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    val expandedState = stringResource(if (isDeepFocusExpanded) R.string.schedule_expanded_state else R.string.schedule_collapsed_state)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !isSaving, role = Role.Button) { isDeepFocusExpanded = !isDeepFocusExpanded }
                                            .semantics { stateDescription = expandedState }
                                            .padding(horizontal = 16.dp, vertical = 16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = stringResource(R.string.schedule_deep_section),
                                                color = textPrimary,
                                                style = MaterialTheme.typography.titleMedium,
                                                modifier = Modifier.weight(1f).semantics { heading() }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(FocusColors.ProGold)
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.pro_label),
                                                    color = FocusColors.ProGoldText,
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = if (isDeepFocusExpanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = textSecondary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }

                                    AnimatedVisibility(visible = isDeepFocusExpanded, enter = fadeIn(tween(150)), exit = fadeOut(tween(120))) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 8.dp)
                                        ) {
                                            HorizontalDivider(thickness = 1.dp, color = dividerColor)

                                            // Auto-Start Scheduled Session
                                            ScheduleToggleRow(
                                                title = stringResource(R.string.schedule_auto_start_label),
                                                subtitle = stringResource(R.string.schedule_auto_start_body),
                                                checked = isAutoStart,
                                                onCheckedChange = { isAutoStart = it },
                                                enabled = !isSaving
                                            )

                                            HorizontalDivider(thickness = 1.dp, color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                                            // Strict Mode (only for Timer / Pomodoro)
                                            if (selectedMode != "STOPWATCH") {
                                                ScheduleToggleRow(
                                                    title = stringResource(R.string.schedule_strict_label),
                                                    subtitle = stringResource(R.string.schedule_strict_body),
                                                    checked = isStrictMode,
                                                    onCheckedChange = {
                                                        isStrictMode = it
                                                        if (it) isBlockUninstall = true
                                                    },
                                                    enabled = !isSaving
                                                )
                                                HorizontalDivider(thickness = 1.dp, color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))
                                            }

                                            // YouTube Study Mode
                                            ScheduleToggleRow(
                                                title = stringResource(R.string.schedule_youtube_label),
                                                subtitle = stringResource(R.string.schedule_youtube_body),
                                                checked = isYouTubeStudyMode,
                                                onCheckedChange = { enabled ->
                                                    if (enabled && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                                        pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                                            title = context.getString(R.string.schedule_youtube_label),
                                                            description = context.getString(R.string.schedule_youtube_permission),
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
                                                enabled = !isSaving
                                            )

                                            HorizontalDivider(thickness = 1.dp, color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                                            // Browser Study Mode
                                            ScheduleToggleRow(
                                                title = stringResource(R.string.schedule_browser_label),
                                                subtitle = stringResource(R.string.schedule_browser_body),
                                                checked = isBrowserStudyMode,
                                                onCheckedChange = { enabled ->
                                                    if (enabled && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                                        pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                                            title = context.getString(R.string.schedule_browser_label),
                                                            description = context.getString(R.string.schedule_browser_permission),
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
                                                enabled = !isSaving
                                            )

                                            HorizontalDivider(thickness = 1.dp, color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                                            // Block Notifications
                                            ScheduleToggleRow(
                                                title = stringResource(R.string.schedule_notifications_label),
                                                subtitle = stringResource(R.string.schedule_notifications_body),
                                                checked = blockNotifications,
                                                onCheckedChange = {
                                                    blockNotifications = it
                                                },
                                                enabled = !isSaving
                                            )

                                            HorizontalDivider(thickness = 1.dp, color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                                            // Block FocusShield App Uninstall
                                            ScheduleToggleRow(
                                                title = stringResource(R.string.schedule_uninstall_label),
                                                subtitle = stringResource(R.string.schedule_uninstall_body),
                                                checked = isBlockUninstall,
                                                onCheckedChange = { enabled ->
                                                    if (enabled && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                                        pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                                            title = context.getString(R.string.schedule_uninstall_label),
                                                            description = context.getString(R.string.schedule_uninstall_permission),
                                                            onGranted = { isBlockUninstall = true }
                                                        )
                                                    } else {
                                                        isBlockUninstall = enabled
                                                    }
                                                },
                                                enabled = !isSaving
                                            )

                                            HorizontalDivider(thickness = 1.dp, color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                                            // Block Split Screen
                                            ScheduleToggleRow(
                                                title = stringResource(R.string.schedule_split_label),
                                                subtitle = stringResource(R.string.schedule_split_body),
                                                checked = isBlockSplitScreen,
                                                onCheckedChange = { enabled ->
                                                    if (enabled && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                                        pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                                            title = context.getString(R.string.schedule_split_label),
                                                            description = context.getString(R.string.schedule_split_permission),
                                                            onGranted = { isBlockSplitScreen = true }
                                                        )
                                                    } else {
                                                        isBlockSplitScreen = enabled
                                                    }
                                                },
                                                enabled = !isSaving
                                            )

                                            HorizontalDivider(thickness = 1.dp, color = dividerColor, modifier = Modifier.padding(horizontal = 16.dp))

                                            // Block Floating Window
                                            ScheduleToggleRow(
                                                title = stringResource(R.string.schedule_floating_label),
                                                subtitle = stringResource(R.string.schedule_floating_body),
                                                checked = isBlockFloatingWindow,
                                                onCheckedChange = { enabled ->
                                                    if (enabled && !AccessibilityHelper.isAccessibilityServiceEnabled(context)) {
                                                        pendingAccessibilityPrompt = AccessibilityFeaturePromptInfo(
                                                            title = context.getString(R.string.schedule_floating_label),
                                                            description = context.getString(R.string.schedule_floating_permission),
                                                            onGranted = { isBlockFloatingWindow = true }
                                                        )
                                                    } else {
                                                        isBlockFloatingWindow = enabled
                                                    }
                                                },
                                                enabled = !isSaving
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
                            ) {
                                            HorizontalDivider(thickness = 1.dp, color = dividerColor)

                                            Text(
                                                stringResource(R.string.schedule_device_defaults_note),
                                                color = textSecondary,
                                                style = MaterialTheme.typography.bodyMedium,
                                                modifier = Modifier.padding(FocusSpacing.base)
                                            )
                                Button(
                                     onClick = { saveSchedule() },
                                     enabled = !isSaving && prefsHydrated,
                                    shape = RoundedCornerShape(32.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = saveBtnBg,
                                        contentColor = saveBtnText
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = FocusSpacing.screenHorizontal, vertical = FocusSpacing.md)
                                        .heightIn(min = 54.dp)
                                        .testTag("save_schedule_button")
                                ) {
                                    Text(
                                        text = stringResource(when {
                                            isSaving -> R.string.schedule_saving
                                            !prefsHydrated -> R.string.schedule_loading_settings
                                            else -> R.string.action_save
                                        }),
                                        style = MaterialTheme.typography.labelLarge,
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
        ScheduleClockDialog(
            initialTime = if (isStart) startTime else endTime,
            title = stringResource(if (isStart) R.string.schedule_start_picker_title else R.string.schedule_end_picker_title),
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
        ScheduleBreakDialog(
            currentBreakMinutes = breakMinutes,
            onDismiss = { showBreakPicker = false },
            onSelect = { mins ->
                breakMinutes = mins
                showBreakPicker = false
            }
        )
    }

    if (showEmojiPicker) {
        ScheduleEmojiDialog(
            currentEmoji = selectedEmoji,
            emojis = DEFAULT_EMOJIS,
            onDismiss = { showEmojiPicker = false },
            onSelect = {
                selectedEmoji = it
                userManuallySelectedEmoji = true
                showEmojiPicker = false
            }
        )
    }

    if (showAddTagDialog) {
        FocusNameDialog(
            title = stringResource(R.string.schedule_tag_new_title),
            initialValue = "",
            fieldLabel = stringResource(R.string.schedule_tag_name_label),
            example = stringResource(R.string.schedule_tag_name_example),
            emptyError = stringResource(R.string.schedule_tag_empty_error),
            saveError = stringResource(R.string.schedule_tag_save_error),
            confirmLabel = stringResource(R.string.schedule_add_tag),
            onSave = { name ->
                subjectRepository.addSubject(name)
                tag = name
            },
            onDismiss = { showAddTagDialog = false }
        )
    }

    editingSubject?.let { subjectToEdit ->
        FocusNameDialog(
            title = stringResource(R.string.schedule_tag_manage_title),
            initialValue = subjectToEdit.name,
            fieldLabel = stringResource(R.string.schedule_tag_name_label),
            example = stringResource(R.string.schedule_tag_name_example),
            emptyError = stringResource(R.string.schedule_tag_empty_error),
            saveError = stringResource(R.string.schedule_tag_save_error),
            confirmLabel = stringResource(R.string.action_save),
            onSave = { name ->
                subjectRepository.renameSubject(subjectToEdit, name)
                if (tag.equals(subjectToEdit.name, ignoreCase = true)) tag = name
            },
            onDismiss = { editingSubject = null },
            onDelete = { subjectToDelete = subjectToEdit },
            deleteLabel = stringResource(R.string.schedule_tag_delete_action, subjectToEdit.name)
        )
    }

    subjectToDelete?.let { subject ->
        FocusDestructiveDialog(
            title = stringResource(R.string.schedule_tag_delete_title, subject.name),
            message = stringResource(R.string.schedule_tag_delete_body),
            confirmLabel = stringResource(R.string.schedule_tag_delete_confirm),
            onDismiss = { subjectToDelete = null },
            onConfirm = {
                editingSubject = null
                isSaving = true
                coroutineScope.launch {
                    try {
                        subjectRepository.deleteSubject(subject.id)
                        if (tag.equals(subject.name, ignoreCase = true)) tag = "Untagged"
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (exception: Exception) {
                        android.util.Log.e("ScheduleEditor", "Unable to delete subject tag", exception)
                        validationMessage = context.getString(R.string.schedule_tag_delete_error)
                        mainScrollState.scrollTo(0)
                    } finally {
                        isSaving = false
                    }
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

}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScheduleModeSegmentedSelector(
    selectedMode: String,
    onModeSelected: (String) -> Unit,
    enabled: Boolean = true
) {
    val modes = listOf(
        "TIMER" to R.string.schedule_mode_timer,
        "STOPWATCH" to R.string.schedule_mode_stopwatch,
        "POMODORO" to R.string.schedule_mode_pomodoro
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(FocusSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(FocusSpacing.xs)
    ) {
        modes.forEach { (modeKey, label) ->
            FilterChip(
                selected = selectedMode.equals(modeKey, ignoreCase = true),
                onClick = { onModeSelected(modeKey) },
                enabled = enabled,
                label = { Text(stringResource(label)) }
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
    enabled: Boolean = true
) {
    FocusSwitchRow(title = title, description = subtitle, checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
}

private enum class ActivePicker { START_TIME, END_TIME }

@Composable
private fun formatTimeDisplay(value: String): String = scheduleTimeDisplay(value)
