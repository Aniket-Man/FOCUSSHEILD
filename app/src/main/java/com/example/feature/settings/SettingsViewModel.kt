package com.example.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusShieldApp
import com.example.R
import com.example.cloud.auth.AuthState
import com.example.cloud.sync.SyncCycleOutcome
import com.example.cloud.sync.SyncTracker
import com.example.core.accessibility.AccessibilityHelper
import com.example.core.util.ProfilePhotoStorage
import com.example.data.preferences.FocusPreferences
import com.example.data.preferences.FocusPreferencesRepository
import com.example.data.repository.BlockedAppRepository
import com.example.feature.profile.ProfileEditorSave
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val preferences: FocusPreferences = FocusPreferences(),
    val blockedAppsCount: Int = 0,
    val isAccessibilityEnabled: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val isNotificationListenerGranted: Boolean = false,
    val isOverlayPermissionGranted: Boolean = false,
    val allTimeStudyTimeMillis: Long = 0L,
    val formattedAllTimeStudyTime: String = "0m",
    /** Badge id to the instant it was earned, derived from synced session history. */
    val badgeUnlockTimes: Map<String, Long> = emptyMap(),
    /** Email of the signed-in Google account, or null while running local-only. */
    val signedInEmail: String? = null,
    val signedInDisplayName: String? = null,
    /** True while a sync cycle is in flight, for the live "Backing up…" affordance. */
    val isSyncRunning: Boolean = false,
    /** Outbox depth — edits waiting for the next push. */
    val pendingSyncCount: Int = 0,
    /** Wall-clock time of the last cycle that actually reached Firestore; 0 if never. */
    val lastSyncAtMillis: Long = 0L,
    /** Outcome of the most recent cycle attempt, for the status line. */
    val lastSyncOutcome: SyncCycleOutcome? = null
)

class SettingsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val preferencesRepository: FocusPreferencesRepository =
        (application as FocusShieldApp).preferencesRepository
    private val blockedAppRepository: BlockedAppRepository =
        (application as FocusShieldApp).blockedAppRepository
    private val analyticsRepository =
        (application as FocusShieldApp).analyticsRepository
    private val authRepository = (application as FocusShieldApp).authRepository
    private val syncGateway = (application as FocusShieldApp).syncGateway

    private val _isAccessibilityEnabled = MutableStateFlow(false)
    private val _isBatteryOptimizationIgnored = MutableStateFlow(false)
    private val _isNotificationListenerGranted = MutableStateFlow(false)
    private val _isOverlayPermissionGranted = MutableStateFlow(false)
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()
    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()
    private var pendingWrites = 0

    /** Result of the profile editor's copy → persist → retire transaction. */
    sealed interface ProfileSaveState {
        data object Idle : ProfileSaveState
        data object Saving : ProfileSaveState
        data class Saved(val queuedForCloud: Boolean, val followUpFailure: Exception? = null) : ProfileSaveState
        data class Failed(val exception: Exception) : ProfileSaveState
    }

    private val _profileSaveState = MutableStateFlow<ProfileSaveState>(ProfileSaveState.Idle)
    val profileSaveState: StateFlow<ProfileSaveState> = _profileSaveState.asStateFlow()

    fun clearProfileSaveState() {
        if (_profileSaveState.value !is ProfileSaveState.Saving) {
            _profileSaveState.value = ProfileSaveState.Idle
        }
    }

    fun clearOperationError() { _operationError.value = null }

    private suspend fun applySettingChange(block: suspend () -> Unit): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        pendingWrites++
        _isSaving.value = true
        try {
            block()
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (exception: Exception) {
            android.util.Log.e("SettingsViewModel", "Unable to apply setting", exception)
            _operationError.value = getApplication<Application>().getString(R.string.settings_save_error)
            Result.failure(exception)
        } finally {
            pendingWrites--
            _isSaving.value = pendingWrites > 0
        }
    }

    // Set busy before returning, so two rapid actions cannot both pass a stale busy check.
    private fun launchSettingChange(block: suspend () -> Unit): Job =
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { applySettingChange(block) }

    private data class InnerSettingsState(
        val prefs: FocusPreferences,
        val blockedAppsCount: Int,
        val isAccessibilityEnabled: Boolean,
        val isBatteryOptimizationIgnored: Boolean,
        val isNotificationListenerGranted: Boolean
    )

    /** Account + cloud-sync state shown by the "Account & Cloud Sync" section. */
    private data class CloudUiState(
        val signedInEmail: String? = null,
        val signedInDisplayName: String? = null,
        val isSyncRunning: Boolean = false,
        val pendingSyncCount: Int = 0,
        val lastSyncAtMillis: Long = 0L,
        val lastSyncOutcome: SyncCycleOutcome? = null
    )

    // Grouped in two steps because Kotlin's typed combine tops out at five flows; flattening six
    // independent sources into one combine would mean boxing them all into Pair.
    private val cloudState: StateFlow<CloudUiState> = combine(
        authRepository.authState,
        combine(syncGateway.running, SyncTracker.pendingCount) { running, pending -> running to pending },
        combine(syncGateway.lastSyncAtMillis, syncGateway.lastOutcome) { at, outcome -> at to outcome }
    ) { auth, (running, pending), (lastAt, outcome) ->
        val signedIn = auth as? AuthState.Authenticated
        CloudUiState(
            signedInEmail = signedIn?.email?.takeIf { it.isNotBlank() },
            signedInDisplayName = signedIn?.displayName?.takeIf { it.isNotBlank() },
            isSyncRunning = running,
            pendingSyncCount = pending,
            lastSyncAtMillis = lastAt,
            lastSyncOutcome = outcome
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CloudUiState()
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            preferencesRepository.preferencesFlow,
            blockedAppRepository.enabledBlockedApps,
            _isAccessibilityEnabled,
            _isBatteryOptimizationIgnored,
            _isNotificationListenerGranted
        ) { prefs, blockedList, accessibilityEnabled, batteryIgnored, notificationListener ->
            InnerSettingsState(
                prefs = prefs,
                blockedAppsCount = blockedList.size,
                isAccessibilityEnabled = accessibilityEnabled,
                isBatteryOptimizationIgnored = batteryIgnored,
                isNotificationListenerGranted = notificationListener
            )
        },
        analyticsRepository.todaySummaryFlow,
        cloudState,
        _isOverlayPermissionGranted
    ) { inner, summary, cloud, overlayGranted ->
        SettingsUiState(
            preferences = inner.prefs,
            blockedAppsCount = inner.blockedAppsCount,
            isAccessibilityEnabled = inner.isAccessibilityEnabled,
            isBatteryOptimizationIgnored = inner.isBatteryOptimizationIgnored,
            isNotificationListenerGranted = inner.isNotificationListenerGranted,
            isOverlayPermissionGranted = overlayGranted,
            allTimeStudyTimeMillis = summary.allTimeStudyTimeMillis,
            formattedAllTimeStudyTime = summary.formattedAllTimeStudyTime,
            badgeUnlockTimes = summary.badgeUnlockTimes,
            signedInEmail = cloud.signedInEmail,
            signedInDisplayName = cloud.signedInDisplayName,
            isSyncRunning = cloud.isSyncRunning,
            pendingSyncCount = cloud.pendingSyncCount,
            lastSyncAtMillis = cloud.lastSyncAtMillis,
            lastSyncOutcome = cloud.lastSyncOutcome
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    /**
     * Signs out of Google.
     *
     * Intentionally keeps both the local Room data and the cloud documents: signing back into the
     * same account restores everything, and signing into a different one does not destroy this
     * device's history. Firebase sign-out plus clearing the Credential Manager state is enough for
     * the next sign-in to pick a different account.
     */
    fun signOut() {
        if (syncGateway.running.value || _isSaving.value) return
        launchSettingChange { authRepository.signOut() }
    }

    /** Pushes any pending changes to Firestore now, instead of waiting for the next auto-save tick. */
    fun syncNow() {
        if (syncGateway.running.value || _isSaving.value) return
        launchSettingChange {
            when (syncGateway.runCycle()) {
                SyncCycleOutcome.Offline -> _operationError.value = getApplication<Application>().getString(R.string.account_sync_offline)
                is SyncCycleOutcome.Failed, SyncCycleOutcome.NoSession -> _operationError.value = getApplication<Application>().getString(R.string.account_sync_error)
                else -> Unit
            }
        }
    }

    fun updateAutoSaveToCloud(enabled: Boolean) {
        launchSettingChange { preferencesRepository.updateAutoSaveToCloudEnabled(enabled) }
    }

    fun updateAutoSaveIntervalSeconds(seconds: Int) {
        launchSettingChange { preferencesRepository.updateAutoSaveIntervalSeconds(seconds) }
    }

    fun claimReward(rewardId: String) {
        launchSettingChange {
            preferencesRepository.claimReward(rewardId)
        }
    }

    private val _isDeviceAdminActive = MutableStateFlow(false)
    val isDeviceAdminActive: StateFlow<Boolean> = _isDeviceAdminActive.asStateFlow()

    private val _pendingDeviceAdminRequest = MutableStateFlow(false)
    val pendingDeviceAdminRequest: StateFlow<Boolean> = _pendingDeviceAdminRequest.asStateFlow()

    init {
        checkAccessibilityStatus()
        checkDeviceAdminStatus()

        viewModelScope.launch {
            AccessibilityHelper.isServiceEnabledFlow.collect { enabled ->
                _isAccessibilityEnabled.value = enabled
            }
        }
    }

    fun checkAccessibilityStatus() {
        val enabled = AccessibilityHelper.isAccessibilityServiceEnabled(getApplication())
        _isAccessibilityEnabled.value = enabled
        _isBatteryOptimizationIgnored.value = com.example.core.permission.FocusPermissionManager.isBatteryOptimizationIgnored(getApplication())
        _isNotificationListenerGranted.value = com.example.core.permission.FocusPermissionManager.isNotificationListenerGranted(getApplication())
        _isOverlayPermissionGranted.value = com.example.core.permission.FocusPermissionManager.isOverlayPermissionGranted(getApplication())
    }

    fun updateFloatingTimerOverlay(enabled: Boolean) {
        launchSettingChange {
            if (!enabled && isStrictProtectionLocked()) {
                _strictLockMessage.value = getApplication<Application>().getString(R.string.settings_strict_lock_error)
                return@launchSettingChange
            }
            preferencesRepository.updateFloatingTimerOverlayEnabled(enabled)
            val context = getApplication<Application>()
            if (!enabled) {
                com.example.feature.timer.FloatingTimerService.stop(context)
            } else {
                val active = com.example.feature.session.engine.FocusSessionManager.instance.activeSession.value
                if (active != null && (active.isRunning || active.isPaused)) {
                    com.example.feature.timer.FloatingTimerService.start(context, forceShow = true)
                }
            }
        }
    }

    fun updateTimerDefault(minutes: Int) {
        launchSettingChange {
            preferencesRepository.updateTimerDefault(minutes)
        }
    }

    fun updatePomodoroDefaults(focus: Int, shortBreak: Int, longBreak: Int, cycles: Int) {
        launchSettingChange {
            preferencesRepository.updatePomodoroDefaults(focus, shortBreak, longBreak, cycles)
        }
    }

    fun updateProtectionDefaults(appBlocking: Boolean, strictMode: Boolean, studyChannels: Boolean) {
        launchSettingChange {
            preferencesRepository.updateProtectionDefaults(appBlocking, strictMode, studyChannels)
        }
    }

    fun updateThemeMode(themeMode: String) {
        launchSettingChange {
            preferencesRepository.updateThemeMode(themeMode)
        }
    }

    private val _strictLockMessage = MutableStateFlow<String?>(null)
    val strictLockMessage: StateFlow<String?> = _strictLockMessage.asStateFlow()

    fun clearStrictLockMessage() {
        _strictLockMessage.value = null
    }

    private suspend fun isStrictProtectionLocked(): Boolean {
        return try {
            com.example.feature.applimits.engine.AppLimitStrictModeEngine.instance.isStrictProtectionLocked()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            val active = com.example.feature.session.engine.FocusSessionManager.instance.activeSession.value
            active != null && (active.isRunning || active.isPaused) && active.isStrictModeEnabled
        }
    }

    fun updateYouTubeShortsBlocking(enabled: Boolean) {
        launchSettingChange {
            if (!enabled && isStrictProtectionLocked()) {
                _strictLockMessage.value = getApplication<Application>().getString(R.string.settings_strict_lock_error)
                return@launchSettingChange
            }
            preferencesRepository.updateYouTubeShortsBlocking(enabled)
        }
    }

    fun updateInstagramReelsBlocking(enabled: Boolean) {
        launchSettingChange {
            if (!enabled && isStrictProtectionLocked()) {
                _strictLockMessage.value = getApplication<Application>().getString(R.string.settings_strict_lock_error)
                return@launchSettingChange
            }
            preferencesRepository.updateInstagramReelsBlocking(enabled)
        }
    }

    fun updateFacebookReelsBlocking(enabled: Boolean) {
        launchSettingChange {
            if (!enabled && isStrictProtectionLocked()) {
                _strictLockMessage.value = getApplication<Application>().getString(R.string.settings_strict_lock_error)
                return@launchSettingChange
            }
            preferencesRepository.updateFacebookReelsBlocking(enabled)
        }
    }

    fun updateShortsReelsAlwaysBlocked(alwaysBlocked: Boolean) {
        launchSettingChange {
            if (!alwaysBlocked && isStrictProtectionLocked()) {
                _strictLockMessage.value = getApplication<Application>().getString(R.string.settings_strict_lock_error)
                return@launchSettingChange
            }
            preferencesRepository.updateShortsReelsAlwaysBlocked(alwaysBlocked)
        }
    }

    fun updateAutoAdultWebsiteBlocking(enabled: Boolean) {
        launchSettingChange {
            if (!enabled && isStrictProtectionLocked()) {
                _strictLockMessage.value = getApplication<Application>().getString(R.string.settings_strict_lock_error)
                return@launchSettingChange
            }
            preferencesRepository.updateAutoAdultWebsiteBlocking(enabled)
        }
    }

    fun checkDeviceAdminStatus() {
        val engine = com.example.core.engine.AntiUninstallEngine.getInstance(getApplication())
        _isDeviceAdminActive.value = engine.isDeviceAdminActive()
    }

    fun updateBlockUninstall(enabled: Boolean) {
        launchSettingChange {
            if (!enabled && isStrictProtectionLocked()) {
                _strictLockMessage.value = getApplication<Application>().getString(R.string.settings_strict_lock_error)
                return@launchSettingChange
            }
            preferencesRepository.updateBlockUninstall(enabled)
            if (enabled) {
                val engine = com.example.core.engine.AntiUninstallEngine.getInstance(getApplication())
                if (!engine.isDeviceAdminActive()) {
                    _pendingDeviceAdminRequest.value = true
                }
            } else {
                val engine = com.example.core.engine.AntiUninstallEngine.getInstance(getApplication())
                if (engine.isDeviceAdminActive()) {
                    try {
                        val dpm = getApplication<android.app.Application>()
                            .getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as? android.app.admin.DevicePolicyManager
                        val adminComponent = android.content.ComponentName(
                            getApplication(),
                            com.example.core.receiver.FocusDeviceAdminReceiver::class.java
                        )
                        dpm?.removeActiveAdmin(adminComponent)
                        _isDeviceAdminActive.value = false
                    } catch (e: Exception) {
                        android.util.Log.e("SettingsViewModel", "Error removing device admin", e)
                        checkDeviceAdminStatus()
                        _operationError.value = getApplication<Application>().getString(R.string.settings_device_admin_error)
                    }
                }
            }
        }
    }

    fun getDeviceAdminIntent(): android.content.Intent {
        val engine = com.example.core.engine.AntiUninstallEngine.getInstance(getApplication())
        return engine.createEnableAdminIntent()
    }

    /**
     * Marks the pending device-admin request as consumed at the moment the system screen is
     * launched, so a recomposition (rotation, theme change) cannot launch it a second time
     * (issue #28).
     */
    fun onDeviceAdminRequestLaunched() {
        _pendingDeviceAdminRequest.value = false
    }

    fun onDeviceAdminRequestResult() {
        _pendingDeviceAdminRequest.value = false
        checkDeviceAdminStatus()
    }

    fun updateBlockSplitScreen(enabled: Boolean) {
        launchSettingChange {
            if (!enabled && isStrictProtectionLocked()) {
                _strictLockMessage.value = getApplication<Application>().getString(R.string.settings_strict_lock_error)
                return@launchSettingChange
            }
            preferencesRepository.updateBlockSplitScreen(enabled)
        }
    }

    fun updateBlockFloatingWindow(enabled: Boolean) {
        launchSettingChange {
            if (!enabled && isStrictProtectionLocked()) {
                _strictLockMessage.value = getApplication<Application>().getString(R.string.settings_strict_lock_error)
                return@launchSettingChange
            }
            preferencesRepository.updateBlockFloatingWindow(enabled)
        }
    }

    fun updateBlockNotifications(enabled: Boolean) {
        launchSettingChange {
            if (!enabled && isStrictProtectionLocked()) {
                _strictLockMessage.value = getApplication<Application>().getString(R.string.settings_strict_lock_error)
                return@launchSettingChange
            }
            preferencesRepository.updateBlockNotifications(enabled)
        }
    }

    fun updateBlockedNotificationPackages(packages: Set<String>) {
        launchSettingChange {
            preferencesRepository.updateBlockedNotificationPackages(packages)
        }
    }

    fun toggleBlockedNotificationPackage(packageName: String, blocked: Boolean) {
        launchSettingChange {
            if (!blocked && isStrictProtectionLocked()) {
                _strictLockMessage.value = getApplication<Application>().getString(R.string.settings_strict_lock_error)
                return@launchSettingChange
            }
            preferencesRepository.toggleBlockedNotificationPackage(packageName, blocked)
        }
    }

    fun setOnboardingCompleted(completed: Boolean = true) {
        launchSettingChange {
            preferencesRepository.setOnboardingCompleted(completed)
        }
    }

    /**
     * Writes the edited profile locally.
     *
     * The explicit result distinguishes a completed write from a failed Job. Follow-up photo
     * cleanup and cloud work must only run after success.
     */
    suspend fun updateUserProfile(
        userName: String,
        photoUri: String?,
        avatarPreset: String,
        motto: String,
        academicGoal: String,
        dailyGoalMinutes: Int
    ): Result<Unit> = applySettingChange {
        preferencesRepository.updateUserProfile(
            userName = userName,
            photoUri = photoUri,
            avatarPreset = avatarPreset,
            motto = motto,
            academicGoal = academicGoal,
            dailyGoalMinutes = dailyGoalMinutes
        )
    }

    /**
     * Owns the whole profile-photo save in [viewModelScope], not the editor's composition scope.
     *
     * A rotation during the save must neither cancel the transaction nor let the recreated editor
     * submit stale values. The trailing writes stay non-cancellable inside [ProfileEditorSave], and
     * the completed result is observable by whichever composition is on screen.
     */
    fun saveProfileWithPhoto(
        userName: String,
        selectedPhoto: String?,
        avatarPreset: String,
        motto: String,
        academicGoal: String,
        dailyGoalMinutes: Int
    ) {
        if (_profileSaveState.value is ProfileSaveState.Saving) return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            _profileSaveState.value = ProfileSaveState.Saving
            val app = getApplication<Application>() as FocusShieldApp
            try {
                val uid = (authRepository.authState.value as? AuthState.Authenticated)?.userId
                val previousPhoto = preferencesRepository.preferencesFlow.first().userPhotoUri
                var queuedForCloud = false
                val outcome = ProfileEditorSave.run(
                    selectedPhoto = selectedPhoto,
                    preparePhoto = { ProfilePhotoStorage.preparePhoto(app, it) },
                    persistProfile = { prepared ->
                        // Direct repository write: the profile state below owns busy/error
                        // reporting, so this must not also post a generic settings error.
                        preferencesRepository.updateUserProfile(
                            userName = userName,
                            photoUri = prepared,
                            avatarPreset = avatarPreset,
                            motto = motto,
                            academicGoal = academicGoal,
                            dailyGoalMinutes = dailyGoalMinutes
                        )
                    },
                    discardPreparedPhoto = { ProfilePhotoStorage.discardOwnedPhoto(app, it) },
                    finishPhoto = { savedPhoto ->
                        if (uid != null) {
                            app.cloudInstallState.setPendingProfilePhoto(uid, savedPhoto)
                            queuedForCloud = true
                            app.cloudInstallState.setProfileDirty(true)
                        }
                        ProfilePhotoStorage.finishPhoto(
                            app,
                            previousPhoto,
                            savedPhoto,
                            app.cloudInstallState.pendingProfilePhotoUris()
                        )
                        // A backup request is follow-up work and must never undo the local save.
                        if (uid != null && app.authRepository.currentUserId() == uid) {
                            app.syncGateway.requestSync()
                        }
                    }
                )
                _profileSaveState.value = when (outcome) {
                    is ProfileEditorSave.Outcome.Saved ->
                        ProfileSaveState.Saved(queuedForCloud, outcome.followUpFailure)
                    is ProfileEditorSave.Outcome.Failed ->
                        ProfileSaveState.Failed(outcome.exception)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                _profileSaveState.value = ProfileSaveState.Failed(exception)
            }
        }
    }

    fun updateUserPhoto(uri: String?) {
        launchSettingChange {
            preferencesRepository.updateUserPhotoUri(uri)
        }
    }

    fun updateUserName(name: String) {
        launchSettingChange {
            preferencesRepository.updateUserName(name)
        }
    }

    fun updateUserAvatarPreset(preset: String) {
        launchSettingChange {
            preferencesRepository.updateUserAvatarPreset(preset)
        }
    }

    fun updateUserMotto(motto: String) {
        launchSettingChange {
            preferencesRepository.updateUserMotto(motto)
        }
    }

    fun updateUserAcademicGoal(goal: String) {
        launchSettingChange {
            preferencesRepository.updateUserAcademicGoal(goal)
        }
    }

    fun updateDailyGoalMinutes(minutes: Int) {
        launchSettingChange {
            preferencesRepository.updateDailyGoalMinutes(minutes)
        }
    }
}
