package com.example.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusShieldApp
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
import kotlinx.coroutines.launch

data class SettingsUiState(
    val preferences: FocusPreferences = FocusPreferences(),
    val blockedAppsCount: Int = 0,
    val isAccessibilityEnabled: Boolean = false,
    val isBatteryOptimizationIgnored: Boolean = false,
    val isNotificationListenerGranted: Boolean = false,
    val allTimeStudyTimeMillis: Long = 0L,
    val formattedAllTimeStudyTime: String = "0m"
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

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            preferencesRepository.preferencesFlow,
            blockedAppRepository.enabledBlockedApps,
            _isAccessibilityEnabled,
            _isBatteryOptimizationIgnored,
            _isNotificationListenerGranted
        ) { prefs, blockedList, accessibilityEnabled, batteryIgnored, notifGranted ->
            InnerSettingsState(
                prefs = prefs,
                blockedAppsCount = blockedList.size,
                isAccessibilityEnabled = accessibilityEnabled,
                isBatteryOptimizationIgnored = batteryIgnored,
                isNotificationListenerGranted = notifGranted
            )
        },
        analyticsRepository.todaySummaryFlow
    ) { inner, summary ->
        SettingsUiState(
            preferences = inner.prefs,
            blockedAppsCount = inner.blockedAppsCount,
            isAccessibilityEnabled = inner.isAccessibilityEnabled,
            isBatteryOptimizationIgnored = inner.isBatteryOptimizationIgnored,
            isNotificationListenerGranted = inner.isNotificationListenerGranted,
            allTimeStudyTimeMillis = summary.allTimeStudyTimeMillis,
            formattedAllTimeStudyTime = summary.formattedAllTimeStudyTime
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

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

    fun updateUserProfile(
        userName: String,
        photoUri: String?,
        avatarPreset: String,
        motto: String,
        academicGoal: String,
        dailyGoalMinutes: Int
    ) {
        viewModelScope.launch {
            preferencesRepository.updateUserProfile(
                userName = userName,
                photoUri = photoUri,
                avatarPreset = avatarPreset,
                motto = motto,
                academicGoal = academicGoal,
                dailyGoalMinutes = dailyGoalMinutes
            )
        }
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
