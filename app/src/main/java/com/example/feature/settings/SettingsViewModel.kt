package com.example.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusShieldApp
import com.example.cloud.auth.AuthState
import com.example.cloud.sync.SyncCycleOutcome
import com.example.cloud.sync.SyncTracker
import com.example.core.accessibility.AccessibilityHelper
import com.example.data.preferences.FocusPreferences
import com.example.data.preferences.FocusPreferencesRepository
import com.example.data.repository.BlockedAppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: FocusPreferences = FocusPreferences(),
    val blockedAppsCount: Int = 0,
    val isAccessibilityEnabled: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val isNotificationListenerGranted: Boolean = false,
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
        cloudState
    ) { inner, summary, cloud ->
        SettingsUiState(
            preferences = inner.prefs,
            blockedAppsCount = inner.blockedAppsCount,
            isAccessibilityEnabled = inner.isAccessibilityEnabled,
            isBatteryOptimizationIgnored = inner.isBatteryOptimizationIgnored,
            isNotificationListenerGranted = inner.isNotificationListenerGranted,
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
        viewModelScope.launch { authRepository.signOut() }
    }

    /** Pushes any pending changes to Firestore now, instead of waiting for the next auto-save tick. */
    fun syncNow() {
        viewModelScope.launch { syncGateway.flushPending() }
    }

    fun updateAutoSaveToCloud(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.updateAutoSaveToCloudEnabled(enabled) }
    }

    fun updateAutoSaveIntervalSeconds(seconds: Int) {
        viewModelScope.launch { preferencesRepository.updateAutoSaveIntervalSeconds(seconds) }
    }

    fun claimReward(rewardId: String) {
        viewModelScope.launch {
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
    }

    fun updateTimerDefault(minutes: Int) {
        viewModelScope.launch {
            preferencesRepository.updateTimerDefault(minutes)
        }
    }

    fun updatePomodoroDefaults(focus: Int, shortBreak: Int, longBreak: Int, cycles: Int) {
        viewModelScope.launch {
            preferencesRepository.updatePomodoroDefaults(focus, shortBreak, longBreak, cycles)
        }
    }

    fun updateProtectionDefaults(appBlocking: Boolean, strictMode: Boolean, studyChannels: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateProtectionDefaults(appBlocking, strictMode, studyChannels)
        }
    }

    fun updateThemeMode(themeMode: String) {
        viewModelScope.launch {
            preferencesRepository.updateThemeMode(themeMode)
        }
    }

    fun updateYouTubeShortsBlocking(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateYouTubeShortsBlocking(enabled)
        }
    }

    fun updateInstagramReelsBlocking(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateInstagramReelsBlocking(enabled)
        }
    }

    fun updateFacebookReelsBlocking(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateFacebookReelsBlocking(enabled)
        }
    }

    fun updateShortsReelsAlwaysBlocked(alwaysBlocked: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateShortsReelsAlwaysBlocked(alwaysBlocked)
        }
    }

    fun checkDeviceAdminStatus() {
        val engine = com.example.core.engine.AntiUninstallEngine.getInstance(getApplication())
        _isDeviceAdminActive.value = engine.isDeviceAdminActive()
    }

    fun updateBlockUninstall(enabled: Boolean) {
        viewModelScope.launch {
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
                        android.util.Log.e("SettingsViewModel", "Error removing device admin: ${e.message}")
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
        viewModelScope.launch {
            preferencesRepository.updateBlockSplitScreen(enabled)
        }
    }

    fun updateBlockFloatingWindow(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateBlockFloatingWindow(enabled)
        }
    }

    fun updateBlockNotifications(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateBlockNotifications(enabled)
        }
    }

    fun updateBlockedNotificationPackages(packages: Set<String>) {
        viewModelScope.launch {
            preferencesRepository.updateBlockedNotificationPackages(packages)
        }
    }

    fun toggleBlockedNotificationPackage(packageName: String, blocked: Boolean) {
        viewModelScope.launch {
            preferencesRepository.toggleBlockedNotificationPackage(packageName, blocked)
        }
    }

    fun setOnboardingCompleted(completed: Boolean = true) {
        viewModelScope.launch {
            preferencesRepository.setOnboardingCompleted(completed)
        }
    }

    /**
     * Writes the edited profile locally.
     *
     * Returns the [Job] doing the write so a caller that has follow-up work depending on the new
     * values — the cloud push in `ProfileScreen`, which would otherwise snapshot and upload the
     * pre-edit profile — can await it instead of racing it (issue #29).
     */
    fun updateUserProfile(
        userName: String,
        photoUri: String?,
        avatarPreset: String,
        motto: String,
        academicGoal: String,
        dailyGoalMinutes: Int
    ): Job = viewModelScope.launch {
        preferencesRepository.updateUserProfile(
            userName = userName,
            photoUri = photoUri,
            avatarPreset = avatarPreset,
            motto = motto,
            academicGoal = academicGoal,
            dailyGoalMinutes = dailyGoalMinutes
        )
    }

    fun updateUserPhoto(uri: String?) {
        viewModelScope.launch {
            preferencesRepository.updateUserPhotoUri(uri)
        }
    }

    fun updateUserName(name: String) {
        viewModelScope.launch {
            preferencesRepository.updateUserName(name)
        }
    }

    fun updateUserAvatarPreset(preset: String) {
        viewModelScope.launch {
            preferencesRepository.updateUserAvatarPreset(preset)
        }
    }

    fun updateUserMotto(motto: String) {
        viewModelScope.launch {
            preferencesRepository.updateUserMotto(motto)
        }
    }

    fun updateUserAcademicGoal(goal: String) {
        viewModelScope.launch {
            preferencesRepository.updateUserAcademicGoal(goal)
        }
    }

    fun updateDailyGoalMinutes(minutes: Int) {
        viewModelScope.launch {
            preferencesRepository.updateDailyGoalMinutes(minutes)
        }
    }
}
