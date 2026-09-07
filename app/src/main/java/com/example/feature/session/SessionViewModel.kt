package com.example.feature.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusShieldApp
import com.example.core.util.TimeFormatter
import com.example.data.model.SessionMode
import com.example.data.repository.SessionRepository
import com.example.feature.session.domain.FocusSession
import com.example.feature.session.domain.LapItem
import com.example.feature.session.domain.ManualBreakInfo
import com.example.feature.session.domain.PomodoroConfig
import com.example.feature.session.domain.PomodoroPhase
import com.example.feature.session.domain.SessionState
import com.example.feature.session.domain.StrictModeConfig
import com.example.feature.session.engine.FocusSessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Collections

enum class SpecialAppOption {
    BLOCK_COMPLETELY,
    ALLOW_COMPLETELY,
    STUDY_MODE
}

data class SessionSetupState(
    val selectedMode: SessionMode = SessionMode.TIMER,
    val durationMinutes: Int = 25,
    val numberOfBreaks: Int = 1,
    val breakDurationMinutes: Int = 5,
    val youtubeOption: SpecialAppOption = SpecialAppOption.BLOCK_COMPLETELY,
    val browserOption: SpecialAppOption = SpecialAppOption.BLOCK_COMPLETELY,
    val isDistractingMasterEnabled: Boolean = true,
    val blockedAppPackages: Set<String> = setOf("com.google.android.youtube", "com.android.chrome", "com.opera.browser", "com.brave.browser", "com.instagram.android"),
    val isYouTubeStudyModeEnabled: Boolean = false,
    val isBrowserStudyModeEnabled: Boolean = false,
    val isBlockHomeScreenEnabled: Boolean = false,
    val isBlockUninstallEnabled: Boolean = false,
    val isBlockSplitScreenEnabled: Boolean = false,
    val isBlockFloatingWindowEnabled: Boolean = false,
    val isDeepFocusExpanded: Boolean = true,
    val selectedSubject: String = "Physics",
    val selectedTopic: String = "Electrostatics",
    val sessionGoal: String = "Solve 20 JEE Advanced problems",
    val isAppBlockingEnabled: Boolean = true,
    val isStrictModeEnabled: Boolean = false,
    val strictModeConfig: StrictModeConfig = StrictModeConfig(),
    val isStudyChannelsEnabled: Boolean = false,
    val planId: String? = null,
    // Pomodoro specific configuration
    val pomodoroFocusMinutes: Int = 25,
    val pomodoroShortBreakMinutes: Int = 5,
    val pomodoroLongBreakMinutes: Int = 15,
    val pomodoroCycles: Int = 4,
    // Validation message
    val validationError: String? = null
)

data class ActiveSessionUiState(
    val hasActiveSession: Boolean = false,
    val activeSession: FocusSession? = null,
    val sessionState: SessionState = SessionState.IDLE,
    val mode: SessionMode = SessionMode.TIMER,
    val subject: String = "Physics",
    val topic: String = "Electrostatics",
    val goal: String = "",
    val activeTimeString: String = "01:00:00",
    val isSessionRunning: Boolean = false,
    val isSessionPaused: Boolean = false,
    val isSessionCompleted: Boolean = false,
    val progress: Float = 0f,
    val pomodoroPhase: PomodoroPhase? = null,
    val currentCycle: Int = 1,
    val totalCycles: Int = 1,
    val laps: List<LapItem> = emptyList(),
    val isStrictModeEnabled: Boolean = false,
    val strictModeConfig: StrictModeConfig = StrictModeConfig(),
    val isAppBlockingEnabled: Boolean = true,
    val isStudyChannelsEnabled: Boolean = false,
    // Break information
    val isBreakActive: Boolean = false,
    val activeBreak: ManualBreakInfo? = null,
    val breakRemainingFormatted: String = "00:00",
    val totalBreakDurationMillis: Long = 0L,
    // Formatted metrics for summary
    val elapsedDurationFormatted: String = "00:00:00"
)

class SessionViewModel(
    val sessionManager: FocusSessionManager = FocusSessionManager.instance,
    private val sessionRepositoryProvider: () -> SessionRepository = { FocusShieldApp.instance.sessionRepository }
) : ViewModel() {

    private val persistedSessionIds = Collections.synchronizedSet(mutableSetOf<String>())

    private val _setupState = MutableStateFlow(SessionSetupState())
    val setupState: StateFlow<SessionSetupState> = _setupState

    init {
        // Observe global preferences to keep setup state in sync
        viewModelScope.launch {
            try {
                FocusShieldApp.instance.preferencesRepository.preferencesFlow.collectLatest { prefs ->
                    _setupState.update {
                        it.copy(
                            isBlockUninstallEnabled = prefs.isBlockUninstallEnabled,
                            isBlockSplitScreenEnabled = prefs.isBlockSplitScreenEnabled,
                            isBlockFloatingWindowEnabled = prefs.isBlockFloatingWindowEnabled
                        )
                    }
                }
            } catch (e: Exception) { }
        }

        // Observe active app limits from AppLimitRepository and merge into setupState.blockedAppPackages
        viewModelScope.launch {
            try {
                FocusShieldApp.instance.appLimitRepository.getActiveLimitsFlow().collectLatest { limits ->
                    val limitPkgs = limits.filter { it.isEnabled }.map { it.packageName }.toSet()
                    if (limitPkgs.isNotEmpty()) {
                        _setupState.update { it.copy(blockedAppPackages = it.blockedAppPackages + limitPkgs) }
                    }
                }
            } catch (e: Exception) {
                // Ignore in isolated unit test environments without Application context
            }
        }

        // Automatically persist sessions when natural completion occurs (e.g. Timer 0 reached)
        viewModelScope.launch {
            sessionManager.activeSession.collectLatest { session ->
                if (session != null && session.state == SessionState.COMPLETED) {
                    persistSessionRecord(session, isCompleted = true, isCancelled = false)
                    try {
                        val context = FocusShieldApp.instance
                        sessionManager.clearActiveSessionFromDisk(context)
                        com.example.feature.session.service.FocusSessionForegroundService.stop(context)
                    } catch (e: Exception) { }
                }
            }
        }

        // Attach notification triggers for Timer & Pomodoro completions
        sessionManager.onNotificationEvent = { event ->
            try {
                val context = FocusShieldApp.instance
                when (event) {
                    is com.example.feature.session.engine.SessionNotificationEvent.TimerCompleted -> {
                        val durationMinutes = (event.session.plannedDurationMillis / 60000L).toInt().coerceAtLeast(1)
                        com.example.feature.session.notification.SessionNotificationHelper.notifyTimerCompleted(
                            context = context,
                            subject = event.session.subject,
                            topic = event.session.topic,
                            durationMinutes = durationMinutes
                        )
                    }
                    is com.example.feature.session.engine.SessionNotificationEvent.PomodoroPhaseTransition -> {
                        com.example.feature.session.notification.SessionNotificationHelper.notifyPomodoroPhaseTransition(
                            context = context,
                            completedPhase = event.previousPhase,
                            newPhase = event.newPhase,
                            cycle = event.currentCycle,
                            totalCycles = event.totalCycles,
                            isFullComplete = event.isFullCompleted,
                            subject = event.subject
                        )
                    }
                    is com.example.feature.session.engine.SessionNotificationEvent.BreakExpired -> {
                        com.example.feature.session.notification.SessionNotificationHelper.notifyBreakExpired(context)
                    }
                }
            } catch (e: Exception) {
                // Ignore in isolated unit test environments without Application context
            }
        }
    }

    val activeSessionState: StateFlow<ActiveSessionUiState> = combine(
        sessionManager.activeSession,
        sessionManager.sessionState,
        _setupState
    ) { session, state, setup ->
        if (session != null) {
            val displayTime = TimeFormatter.formatDisplayTime(
                remainingMillis = session.remainingDurationMillis,
                elapsedMillis = session.elapsedDurationMillis,
                mode = session.mode
            )
            val breakInfo = session.manualBreak
            val isBreakActive = breakInfo?.isActive == true
            val breakRemainingMillis = breakInfo?.calculateRemainingMillis(System.currentTimeMillis()) ?: 0L
            val breakDisplay = TimeFormatter.formatDigital(breakRemainingMillis, alwaysShowHours = false)

            ActiveSessionUiState(
                hasActiveSession = true,
                activeSession = session,
                sessionState = state,
                mode = session.mode,
                subject = session.subject,
                topic = session.topic,
                goal = session.goal,
                activeTimeString = displayTime,
                isSessionRunning = state == SessionState.RUNNING && !isBreakActive,
                isSessionPaused = state == SessionState.PAUSED,
                isSessionCompleted = state == SessionState.COMPLETED,
                progress = session.progress,
                pomodoroPhase = session.pomodoroPhase,
                currentCycle = session.currentCycle,
                totalCycles = session.totalCycles,
                laps = session.laps,
                isStrictModeEnabled = session.isStrictModeEnabled,
                strictModeConfig = session.strictModeConfig,
                isAppBlockingEnabled = session.isAppBlockingEnabled,
                isStudyChannelsEnabled = session.isStudyChannelsEnabled,
                isBreakActive = isBreakActive,
                activeBreak = breakInfo,
                breakRemainingFormatted = breakDisplay,
                totalBreakDurationMillis = session.totalBreakDurationMillis,
                elapsedDurationFormatted = TimeFormatter.formatDigital(session.elapsedDurationMillis, true)
            )
        } else {
            ActiveSessionUiState(
                hasActiveSession = false,
                activeSession = null,
                sessionState = SessionState.IDLE,
                mode = setup.selectedMode,
                subject = setup.selectedSubject,
                topic = setup.selectedTopic,
                goal = setup.sessionGoal,
                activeTimeString = when (setup.selectedMode) {
                    SessionMode.TIMER -> String.format("%02d:00:00", setup.durationMinutes / 60)
                    SessionMode.POMODORO -> String.format("%02d:00", setup.pomodoroFocusMinutes)
                    SessionMode.STOPWATCH -> "00:00:00"
                },
                isSessionRunning = false,
                isSessionPaused = false,
                isSessionCompleted = false,
                progress = 0f,
                pomodoroPhase = if (setup.selectedMode == SessionMode.POMODORO) PomodoroPhase.FOCUS else null,
                currentCycle = 1,
                totalCycles = setup.pomodoroCycles,
                laps = emptyList(),
                isStrictModeEnabled = setup.isStrictModeEnabled,
                strictModeConfig = setup.strictModeConfig,
                isAppBlockingEnabled = setup.isAppBlockingEnabled,
                isStudyChannelsEnabled = setup.isStudyChannelsEnabled,
                isBreakActive = false,
                activeBreak = null,
                breakRemainingFormatted = "00:00",
                totalBreakDurationMillis = 0L,
                elapsedDurationFormatted = "00:00:00"
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ActiveSessionUiState()
    )

    fun selectMode(mode: SessionMode) {
        _setupState.update {
            it.copy(
                selectedMode = mode,
                durationMinutes = if (mode.defaultMinutes > 0) mode.defaultMinutes else it.durationMinutes,
                validationError = null
            )
        }
    }

    /**
     * Pre-configures the session setup using details from a scheduled Study Plan notification or user selection.
     */
    fun configureForStudyPlan(
        subjectName: String,
        topicName: String,
        durationMinutes: Int,
        planId: String? = null,
        notes: String = ""
    ) {
        _setupState.update {
            it.copy(
                selectedSubject = subjectName,
                selectedTopic = topicName,
                durationMinutes = durationMinutes.coerceAtLeast(5),
                sessionGoal = if (notes.isNotBlank()) notes else "Focus on $subjectName ($topicName)",
                planId = planId,
                validationError = null
            )
        }
    }

    fun setDuration(minutes: Int) {
        _setupState.update { it.copy(durationMinutes = minutes, validationError = null) }
    }

    fun setSubject(subject: String) {
        _setupState.update { it.copy(selectedSubject = subject) }
    }

    fun setTopic(topic: String) {
        _setupState.update { it.copy(selectedTopic = topic) }
    }

    fun setSessionGoal(goal: String) {
        _setupState.update { it.copy(sessionGoal = goal) }
    }

    fun setPomodoroConfig(
        focusMinutes: Int,
        shortBreakMinutes: Int,
        longBreakMinutes: Int,
        cycles: Int
    ) {
        _setupState.update {
            it.copy(
                pomodoroFocusMinutes = focusMinutes,
                pomodoroShortBreakMinutes = shortBreakMinutes,
                pomodoroLongBreakMinutes = longBreakMinutes,
                pomodoroCycles = cycles,
                validationError = null
            )
        }
    }

    fun toggleAppBlocking(enabled: Boolean) {
        _setupState.update { it.copy(isAppBlockingEnabled = enabled) }
    }

    fun toggleStrictMode(enabled: Boolean) {
        _setupState.update {
            val newConfig = it.strictModeConfig.copy(enabled = enabled)
            it.copy(
                isStrictModeEnabled = enabled,
                strictModeConfig = newConfig,
                isBlockUninstallEnabled = if (enabled) true else it.isBlockUninstallEnabled
            )
        }
        viewModelScope.launch {
            try {
                val prefs = FocusShieldApp.instance.preferencesRepository
                prefs.updateProtectionDefaults(
                    appBlocking = _setupState.value.isAppBlockingEnabled,
                    strictMode = enabled,
                    studyChannels = _setupState.value.isStudyChannelsEnabled
                )
                if (enabled) {
                    prefs.updateBlockUninstall(true)
                }
            } catch (e: Exception) { }
        }
    }

    fun updateStrictModeConfig(config: StrictModeConfig) {
        _setupState.update {
            it.copy(
                isStrictModeEnabled = config.enabled,
                strictModeConfig = config,
                isBlockUninstallEnabled = if (config.enabled && config.blockUninstallTamper) true else it.isBlockUninstallEnabled,
                isBlockSplitScreenEnabled = if (config.enabled && config.blockSplitScreen) true else it.isBlockSplitScreenEnabled,
                isBlockFloatingWindowEnabled = if (config.enabled && config.blockFloatingWindow) true else it.isBlockFloatingWindowEnabled
            )
        }
        viewModelScope.launch {
            try {
                val prefs = FocusShieldApp.instance.preferencesRepository
                prefs.updateProtectionDefaults(
                    appBlocking = _setupState.value.isAppBlockingEnabled,
                    strictMode = config.enabled,
                    studyChannels = _setupState.value.isStudyChannelsEnabled
                )
                if (config.enabled) {
                    if (config.blockUninstallTamper) prefs.updateBlockUninstall(true)
                    if (config.blockSplitScreen) prefs.updateBlockSplitScreen(true)
                    if (config.blockFloatingWindow) prefs.updateBlockFloatingWindow(true)
                    if (config.blockAdultAndDistractingWebsites) prefs.updateAutoAdultWebsiteBlocking(true)
                    if (config.blockShortsAndReels) {
                        prefs.updateYouTubeShortsBlocking(true)
                        prefs.updateInstagramReelsBlocking(true)
                        prefs.updateFacebookReelsBlocking(true)
                    }
                }
            } catch (e: Exception) { }
        }
    }

    fun toggleStudyChannels(enabled: Boolean) {
        _setupState.update { it.copy(isStudyChannelsEnabled = enabled) }
    }

    fun setNumberOfBreaks(count: Int) {
        _setupState.update { it.copy(numberOfBreaks = count.coerceAtLeast(0)) }
    }

    fun setBreakDuration(minutes: Int) {
        _setupState.update { it.copy(breakDurationMinutes = minutes.coerceAtLeast(1)) }
    }

    fun setYoutubeOption(option: SpecialAppOption) {
        val isStudyMode = (option == SpecialAppOption.STUDY_MODE)
        _setupState.update {
            it.copy(
                youtubeOption = option,
                isYouTubeStudyModeEnabled = isStudyMode,
                isStudyChannelsEnabled = isStudyMode
            )
        }
    }

    fun setBrowserOption(option: SpecialAppOption) {
        val isStudyMode = (option == SpecialAppOption.STUDY_MODE)
        _setupState.update {
            it.copy(
                browserOption = option,
                isBrowserStudyModeEnabled = isStudyMode
            )
        }
        if (isStudyMode) {
            viewModelScope.launch {
                try {
                    val prefs = FocusShieldApp.instance.preferencesRepository
                    prefs.updateAutoAdultWebsiteBlocking(true)
                    prefs.updateManualWebsiteBlocking(true)
                } catch (e: Exception) { }
            }
        }
    }

    fun toggleDistractingMaster(enabled: Boolean, allAppPackages: Set<String>) {
        _setupState.update {
            val newBlocked = if (enabled) {
                it.blockedAppPackages + allAppPackages
            } else {
                it.blockedAppPackages - allAppPackages
            }
            it.copy(isDistractingMasterEnabled = enabled, blockedAppPackages = newBlocked)
        }
    }

    fun toggleAppBlocked(packageName: String, isBlocked: Boolean) {
        _setupState.update {
            val updated = if (isBlocked) {
                it.blockedAppPackages + packageName
            } else {
                it.blockedAppPackages - packageName
            }
            it.copy(blockedAppPackages = updated)
        }
    }

    fun toggleYouTubeStudyMode(enabled: Boolean) {
        _setupState.update {
            val newOpt = if (enabled) SpecialAppOption.STUDY_MODE else SpecialAppOption.BLOCK_COMPLETELY
            it.copy(
                isYouTubeStudyModeEnabled = enabled,
                youtubeOption = newOpt,
                isStudyChannelsEnabled = enabled
            )
        }
    }

    fun toggleBrowserStudyMode(enabled: Boolean) {
        _setupState.update {
            val newOpt = if (enabled) SpecialAppOption.STUDY_MODE else SpecialAppOption.BLOCK_COMPLETELY
            it.copy(isBrowserStudyModeEnabled = enabled, browserOption = newOpt)
        }
        if (enabled) {
            viewModelScope.launch {
                try {
                    val prefs = FocusShieldApp.instance.preferencesRepository
                    prefs.updateAutoAdultWebsiteBlocking(true)
                    prefs.updateManualWebsiteBlocking(true)
                } catch (e: Exception) { }
            }
        }
    }

    fun toggleBlockHomeScreen(enabled: Boolean) {
        _setupState.update { it.copy(isBlockHomeScreenEnabled = enabled) }
    }

    fun toggleBlockUninstall(enabled: Boolean) {
        _setupState.update { it.copy(isBlockUninstallEnabled = enabled) }
        viewModelScope.launch {
            try {
                FocusShieldApp.instance.preferencesRepository.updateBlockUninstall(enabled)
                if (enabled) {
                    val engine = com.example.core.engine.AntiUninstallEngine.getInstance(
                        FocusShieldApp.instance.applicationContext
                    )
                    if (!engine.isDeviceAdminActive()) {
                        _pendingDeviceAdminRequest.value = true
                    }
                }
            } catch (e: Exception) { }
        }
    }

    private val _pendingDeviceAdminRequest = MutableStateFlow(false)
    val pendingDeviceAdminRequest: StateFlow<Boolean> = _pendingDeviceAdminRequest

    fun getDeviceAdminIntent(): android.content.Intent {
        val engine = com.example.core.engine.AntiUninstallEngine.getInstance(
            FocusShieldApp.instance.applicationContext
        )
        return engine.createEnableAdminIntent()
    }

    fun onDeviceAdminRequestResult() {
        _pendingDeviceAdminRequest.value = false
    }

    fun toggleBlockSplitScreen(enabled: Boolean) {
        _setupState.update { it.copy(isBlockSplitScreenEnabled = enabled) }
        viewModelScope.launch {
            try {
                FocusShieldApp.instance.preferencesRepository.updateBlockSplitScreen(enabled)
            } catch (e: Exception) { }
        }
    }

    fun toggleBlockFloatingWindow(enabled: Boolean) {
        _setupState.update { it.copy(isBlockFloatingWindowEnabled = enabled) }
        viewModelScope.launch {
            try {
                FocusShieldApp.instance.preferencesRepository.updateBlockFloatingWindow(enabled)
            } catch (e: Exception) { }
        }
    }

    fun toggleDeepFocusExpanded() {
        _setupState.update { it.copy(isDeepFocusExpanded = !it.isDeepFocusExpanded) }
    }

    /**
     * Validates and starts the configured study session.
     * Returns true if successfully started, false otherwise.
     */
    fun startSession(): Boolean {
        val state = _setupState.value

        val pomodoroConfig = PomodoroConfig.fromMinutes(
            focusMinutes = state.pomodoroFocusMinutes,
            shortBreakMinutes = state.pomodoroShortBreakMinutes,
            longBreakMinutes = state.pomodoroLongBreakMinutes,
            cycles = state.pomodoroCycles
        )

        val effectiveStrictMode = if (state.selectedMode == SessionMode.STOPWATCH) false else state.isStrictModeEnabled

        val plannedDurationMillis = when (state.selectedMode) {
            SessionMode.TIMER -> state.durationMinutes * 60 * 1000L
            SessionMode.POMODORO -> pomodoroConfig.focusDurationMillis
            SessionMode.STOPWATCH -> 0L
        }

        val result = sessionManager.startSession(
            mode = state.selectedMode,
            subject = state.selectedSubject,
            topic = state.selectedTopic,
            goal = state.sessionGoal,
            plannedDurationMillis = plannedDurationMillis,
            pomodoroConfig = pomodoroConfig,
            isAppBlocking = state.isAppBlockingEnabled,
            isStrictMode = effectiveStrictMode,
            strictModeConfig = state.strictModeConfig.copy(enabled = effectiveStrictMode),
            isStudyChannels = state.isStudyChannelsEnabled,
            isBrowserStudyMode = state.isBrowserStudyModeEnabled,
            blockedAppPackages = state.blockedAppPackages
        )

        return result.fold(
            onSuccess = { session ->
                _setupState.update { it.copy(validationError = null) }
                try {
                    val context = FocusShieldApp.instance
                    if (effectiveStrictMode) {
                        val strictDuration = when (state.selectedMode) {
                            SessionMode.TIMER -> state.durationMinutes * 60 * 1000L
                            SessionMode.POMODORO -> pomodoroConfig.focusDurationMillis * pomodoroConfig.totalCycles
                            SessionMode.STOPWATCH -> 0L
                        }
                        com.example.feature.session.engine.StrictModeEngine.instance.startStrictMode(
                            context = context,
                            sessionId = session.id,
                            mode = state.selectedMode,
                            durationMillis = strictDuration
                        )
                    }
                    sessionManager.saveActiveSessionToDisk(context)
                    com.example.feature.session.service.FocusSessionForegroundService.start(context)
                    val durationMin = if (session.plannedDurationMillis > 0) (session.plannedDurationMillis / 60000L).toInt() else state.durationMinutes
                    com.example.feature.session.notification.SessionNotificationHelper.notifySessionStarted(
                        context = context,
                        subject = session.subject,
                        topic = session.topic,
                        durationMinutes = durationMin
                    )
                } catch (e: Exception) {
                    // Ignore in headless test environments
                }
                true
            },
            onFailure = { error ->
                _setupState.update { it.copy(validationError = error.message) }
                false
            }
        )
    }

    fun startBreak(durationMinutes: Int = sessionManager.activeSession.value?.defaultBreakMinutes ?: 5): ManualBreakInfo? {
        val result = sessionManager.startBreak(durationMinutes)
        try {
            sessionManager.saveActiveSessionToDisk(FocusShieldApp.instance)
        } catch (e: Exception) { }
        return result
    }

    fun endBreakEarly(): ManualBreakInfo? {
        val result = sessionManager.endBreakEarly()
        try {
            sessionManager.saveActiveSessionToDisk(FocusShieldApp.instance)
        } catch (e: Exception) { }
        return result
    }

    fun togglePause() {
        val active = activeSessionState.value
        if (active.isStrictModeEnabled && active.mode != SessionMode.STOPWATCH && !active.isSessionCompleted) {
            // Strict Mode: pause is guarded
            return
        }
        sessionManager.togglePause()
        try {
            sessionManager.saveActiveSessionToDisk(FocusShieldApp.instance)
        } catch (e: Exception) { }
    }

    fun pauseSession() {
        val active = activeSessionState.value
        if (active.isStrictModeEnabled && active.mode != SessionMode.STOPWATCH && !active.isSessionCompleted) {
            return
        }
        sessionManager.pauseSession()
        try {
            sessionManager.saveActiveSessionToDisk(FocusShieldApp.instance)
        } catch (e: Exception) { }
    }

    fun resumeSession() {
        sessionManager.resumeSession()
        try {
            sessionManager.saveActiveSessionToDisk(FocusShieldApp.instance)
        } catch (e: Exception) { }
    }

    fun recordLap(): LapItem? {
        return sessionManager.recordLap()
    }

    fun skipPomodoroPhase() {
        sessionManager.skipPomodoroPhase()
        try {
            sessionManager.saveActiveSessionToDisk(FocusShieldApp.instance)
        } catch (e: Exception) { }
    }

    fun endSession(): FocusSession? {
        val active = activeSessionState.value
        if (active.isStrictModeEnabled && active.mode != SessionMode.STOPWATCH && !active.isSessionCompleted) {
            if (!com.example.feature.session.engine.StrictModeEngine.instance.canEndSession()) {
                return null
            }
        }
        val current = sessionManager.endSession()
        try {
            val context = FocusShieldApp.instance
            com.example.feature.session.engine.StrictModeEngine.instance.onSessionCompleted(context)
            sessionManager.clearActiveSessionFromDisk(context)
            com.example.feature.session.service.FocusSessionForegroundService.stop(context)
        } catch (e: Exception) { }
        if (current != null) {
            persistSessionRecord(current, isCompleted = true, isCancelled = false)
        }
        return current
    }

    fun cancelSession(): FocusSession? {
        val active = activeSessionState.value
        if (active.isStrictModeEnabled && active.mode != SessionMode.STOPWATCH && !active.isSessionCompleted) {
            if (!com.example.feature.session.engine.StrictModeEngine.instance.canEndSession()) {
                return null
            }
        }
        val current = sessionManager.cancelSession()
        try {
            val context = FocusShieldApp.instance
            com.example.feature.session.engine.StrictModeEngine.instance.onSessionCompleted(context)
            sessionManager.clearActiveSessionFromDisk(context)
            com.example.feature.session.service.FocusSessionForegroundService.stop(context)
        } catch (e: Exception) { }
        if (current != null) {
            persistSessionRecord(current, isCompleted = false, isCancelled = true)
        }
        return current
    }

    fun resetToIdle() {
        sessionManager.resetToIdle()
        try {
            val context = FocusShieldApp.instance
            sessionManager.clearActiveSessionFromDisk(context)
            com.example.feature.session.service.FocusSessionForegroundService.stop(context)
        } catch (e: Exception) { }
    }

    private fun persistSessionRecord(session: FocusSession, isCompleted: Boolean, isCancelled: Boolean) {
        if (persistedSessionIds.contains(session.id)) return
        persistedSessionIds.add(session.id)

        val actualStudyTime = com.example.feature.analytics.domain.ActualStudyTimeCalculator.calculatePureStudyDuration(
            session = session,
            isCompleted = isCompleted
        )

        viewModelScope.launch {
            try {
                sessionRepositoryProvider().recordSession(
                    session = session,
                    actualStudyDurationMillis = actualStudyTime,
                    isCompleted = isCompleted,
                    isCancelled = isCancelled
                )
            } catch (e: Exception) {
                // Room persistence logged safely
            }

            // Offer a scratch card reward for eligible completed sessions
            if (isCompleted) {
                try {
                    val app = FocusShieldApp.instance
                    val studyMinutes = (actualStudyTime / 60_000L).toInt()
                    val summary = app.analyticsRepository.todaySummaryFlow.first()
                    val card = app.scratchCardRepository.createCardIfEligible(
                        sessionId = session.id,
                        subject = session.subject,
                        studyMinutes = studyMinutes,
                        currentStreak = summary.currentStreakDays,
                        allTimeStudyMillis = summary.allTimeStudyTimeMillis
                    )
                    if (card != null) {
                        _pendingScratchCard.value = card
                    }
                } catch (e: Exception) {
                    // Scratch card is a bonus feature; never let it break session persistence
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    // Scratch Card Rewards
    // ---------------------------------------------------------------------

    private val _pendingScratchCard = MutableStateFlow<com.example.data.local.entity.ScratchCardEntity?>(null)

    /** Latest un-revealed scratch card earned by a completed session. */
    val pendingScratchCard: StateFlow<com.example.data.local.entity.ScratchCardEntity?> = _pendingScratchCard.asStateFlow()

    /** Called when the user finishes scratching: persists the reveal state. */
    fun onScratchCardRevealed() {
        val card = _pendingScratchCard.value ?: return
        try {
            FocusShieldApp.instance.scratchCardRepository.markRevealedAsync(card.sessionId)
        } catch (e: Exception) {
            // Ignore in isolated unit test environments
        }
    }

    /** Clears the dialog without marking it revealed (used on Collect/dismiss). */
    fun dismissScratchCard() {
        _pendingScratchCard.value = null
    }
}
