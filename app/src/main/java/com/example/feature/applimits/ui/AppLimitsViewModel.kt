package com.example.feature.applimits.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusShieldApp
import com.example.core.accessibility.AccessibilityHelper
import com.example.core.util.DeviceUsageStatsHelper
import com.example.data.local.entity.AppLimitEntity
import com.example.data.local.entity.DailyAppUsageEntity
import com.example.data.repository.AppLimitRepository
import com.example.feature.applimits.engine.AppLimitManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AppLimitUiItem(
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int,
    val usedMinutes: Int,
    val remainingMinutes: Int,
    val usagePercentage: Float,
    val isBypassedToday: Boolean,
    val isEnabled: Boolean,
    val isStrictOverride: Boolean,
    val showRemindersBeforeLimit: Boolean,
    val streakDays: Int,
    val isLimitReached: Boolean,
    val emergencyUsesCount: Int,
    val emergencyUsesAllowed: Int = 1,
    val appIcon: Drawable? = null
)

data class InstalledAppChoice(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isAlreadyLimited: Boolean = false
)

data class AppLimitsUiState(
    val items: List<AppLimitUiItem> = emptyList(),
    val totalUsedMinutesToday: Int = 0,
    val activeLimitsCount: Int = 0,
    val reachedLimitsCount: Int = 0,
    val isLoading: Boolean = false
)

class AppLimitsViewModel(private val app: Application) : AndroidViewModel(app) {

    private val appLimitRepository: AppLimitRepository =
        (app as FocusShieldApp).appLimitRepository

    private val _installedApps = MutableStateFlow<List<InstalledAppChoice>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppChoice>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val iconCache = java.util.concurrent.ConcurrentHashMap<String, Drawable>()

    val uiState: StateFlow<AppLimitsUiState> = combine(
        appLimitRepository.getAllLimitsFlow(),
        appLimitRepository.getUsageForDateFlow(appLimitRepository.getTodayDateString())
    ) { limits: List<AppLimitEntity>, usages: List<DailyAppUsageEntity> ->
        val usageMap = usages.associateBy { it.packageName }
        val systemUsageMap = DeviceUsageStatsHelper.getTodayAllAppsUsageMillis(app)
        var totalMinutes = 0
        var activeCount = 0
        var reachedCount = 0

        val pm = app.packageManager

        val items = limits.map { limit ->
            val usage = usageMap[limit.packageName]
            val systemUsedMillis = systemUsageMap[limit.packageName] ?: 0L
            val dbUsedMillis = usage?.usedMillis ?: 0L
            // Trust the OS-level usage as source of truth
            val usedMillis = systemUsedMillis
            val usedMin = (usedMillis / 60000L).toInt()
            val totalLimitMin = limit.dailyLimitMinutes
            // Floor the remaining millis, not the limit minus floored used minutes — that
            // rounded the remaining *up*, so the row promised "1m remaining" while seconds were
            // left and the engine was about to block (issue #49, C2). The reached flag is the
            // same millisecond comparison the engine makes.
            val remainingMin = ((totalLimitMin * 60000L - usedMillis).coerceAtLeast(0L) / 60000L).toInt()
            val isBypassed = usage?.isBypassedForToday == true
            val isReached = !isBypassed && limit.isEnabled && usedMillis >= totalLimitMin * 60000L
            val pct = if (totalLimitMin > 0) (usedMin.toFloat() / totalLimitMin.toFloat()).coerceIn(0f, 1f) else 0f

            if (limit.isEnabled) {
                activeCount++
                totalMinutes += usedMin
                if (isReached) reachedCount++
            }

            val iconDrawable = iconCache[limit.packageName] ?: try {
                pm.getApplicationIcon(limit.packageName)?.also { iconCache[limit.packageName] = it }
            } catch (e: Exception) {
                null
            }

            AppLimitUiItem(
                packageName = limit.packageName,
                appName = limit.appName,
                dailyLimitMinutes = totalLimitMin,
                usedMinutes = usedMin,
                remainingMinutes = remainingMin,
                usagePercentage = pct,
                isBypassedToday = isBypassed,
                isEnabled = limit.isEnabled,
                isStrictOverride = limit.isStrictOverride,
                showRemindersBeforeLimit = limit.showRemindersBeforeLimit,
                streakDays = limit.streakDays,
                isLimitReached = isReached,
                emergencyUsesCount = usage?.emergencyUsesCount ?: 0,
                emergencyUsesAllowed = limit.emergencyUsesAllowed,
                appIcon = iconDrawable
            )
        }.sortedWith(compareByDescending<AppLimitUiItem> { it.isLimitReached }.thenByDescending { it.usedMinutes })

        AppLimitsUiState(
            items = items,
            totalUsedMinutesToday = totalMinutes,
            activeLimitsCount = activeCount,
            reachedLimitsCount = reachedCount,
            isLoading = false
        )
    }.flowOn(Dispatchers.IO).stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppLimitsUiState(isLoading = true)
    )

    /**
     * Accessibility service status. App-limit enforcement is performed by that service, so the
     * dashboard must be able to warn continuously while it is off — checking only at
     * create-limit time let users believe they were protected while limits never fired.
     */
    private val _isAccessibilityEnabled = MutableStateFlow(true)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    /**
     * Set when the blocker pipeline exhausted its retries without the block screen ever landing
     * (see [AppLimitManager.overlayLaunchWarning]); null while blocks are landing normally.
     */
    private val _overlayLaunchWarning = MutableStateFlow<String?>(null)
    val overlayLaunchWarning: StateFlow<String?> = _overlayLaunchWarning.asStateFlow()

    init {
        loadInstalledApps()
        checkAccessibilityStatus()
        viewModelScope.launch {
            AccessibilityHelper.isServiceEnabledFlow.collect { enabled ->
                _isAccessibilityEnabled.value = enabled
            }
        }
        viewModelScope.launch {
            val manager = try { AppLimitManager.instance } catch (e: Exception) { null }
            manager?.overlayLaunchWarning?.collect { warning ->
                _overlayLaunchWarning.value = warning
            }
        }
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(intent, 0)
                val currentLimits = appLimitRepository.getAllLimits().map { it.packageName }.toSet()

                val apps = resolveInfos.mapNotNull { resolveInfo ->
                    val pkg = resolveInfo.activityInfo.packageName
                    if (pkg == getApplication<Application>().packageName) return@mapNotNull null

                    val name = resolveInfo.loadLabel(pm).toString()
                    val icon = try { resolveInfo.loadIcon(pm) } catch (e: Exception) { null }

                    InstalledAppChoice(
                        packageName = pkg,
                        appName = name,
                        icon = icon,
                        isAlreadyLimited = currentLimits.contains(pkg)
                    )
                }.sortedBy { it.appName.lowercase() }

                _installedApps.value = apps
            }
            _isLoadingApps.value = false
        }
    }

    private val _strictLockMessage = MutableStateFlow<String?>(null)
    val strictLockMessage: StateFlow<String?> = _strictLockMessage.asStateFlow()

    fun clearStrictLockMessage() {
        _strictLockMessage.value = null
    }

    /** Re-reads the service state directly; called when the dashboard is entered. */
    fun checkAccessibilityStatus() {
        _isAccessibilityEnabled.value = AccessibilityHelper.isAccessibilityServiceEnabled(app)
    }

    fun saveLimit(
        packageName: String,
        appName: String,
        dailyLimitMinutes: Int,
        isStrictOverride: Boolean = false,
        showRemindersBeforeLimit: Boolean = true,
        emergencyUsesAllowed: Int = 1
    ) {
        viewModelScope.launch {
            appLimitRepository.saveLimit(
                packageName = packageName,
                appName = appName,
                dailyLimitMinutes = dailyLimitMinutes,
                isEnabled = true,
                isStrictOverride = isStrictOverride,
                showRemindersBeforeLimit = showRemindersBeforeLimit,
                emergencyUsesAllowed = emergencyUsesAllowed
            )
            loadInstalledApps()
        }
    }

    fun updateStrictMode(packageName: String, isStrict: Boolean) {
        viewModelScope.launch {
            appLimitRepository.updateStrictMode(packageName, isStrict)
        }
    }

    fun updateRemindersSetting(packageName: String, showReminders: Boolean) {
        viewModelScope.launch {
            appLimitRepository.updateRemindersSetting(packageName, showReminders)
        }
    }

    fun toggleLimit(packageName: String, isEnabled: Boolean) {
        viewModelScope.launch {
            val limit = appLimitRepository.getLimitByPackage(packageName)
            val todayDate = appLimitRepository.getTodayDateString()
            val usage = appLimitRepository.getUsage(packageName, todayDate)
            // Use UsageStatsManager as source of truth for actual usage
            val usageResult = DeviceUsageStatsHelper.getTodayAppUsageResult(app, packageName)
            val usedMillis = when (usageResult.status) {
                com.example.core.util.UsageResult.UsageStatus.VALID -> usageResult.usageMillis
                else -> (usage?.usedMillis ?: 0L)
            }
            val emergencyUsesCount = usage?.emergencyUsesCount ?: 0
            val isExhausted = (limit != null && usedMillis >= limit.dailyLimitMinutes * 60000L && emergencyUsesCount >= limit.emergencyUsesAllowed)

            val isGlobalStrict = FocusShieldApp.instance.preferencesRepository.preferencesFlow.first().isStrictModeDefault
            val isStrict = limit?.isStrictOverride == true || isGlobalStrict

            if (!isEnabled && isStrict) {
                _strictLockMessage.value = "Strict Mode Active: ${limit?.appName ?: "App"} limit is locked and cannot be disabled."
                return@launch
            }
            appLimitRepository.setLimitEnabled(packageName, isEnabled)
        }
    }

    fun deleteLimit(packageName: String) {
        viewModelScope.launch {
            val limit = appLimitRepository.getLimitByPackage(packageName)
            val todayDate = appLimitRepository.getTodayDateString()
            val usage = appLimitRepository.getUsage(packageName, todayDate)
            // Use UsageStatsManager as source of truth for actual usage
            val usageResult = DeviceUsageStatsHelper.getTodayAppUsageResult(app, packageName)
            val usedMillis = when (usageResult.status) {
                com.example.core.util.UsageResult.UsageStatus.VALID -> usageResult.usageMillis
                else -> (usage?.usedMillis ?: 0L)
            }
            val emergencyUsesCount = usage?.emergencyUsesCount ?: 0
            val isExhausted = (limit != null && usedMillis >= limit.dailyLimitMinutes * 60000L && emergencyUsesCount >= limit.emergencyUsesAllowed)

            val isGlobalStrict = FocusShieldApp.instance.preferencesRepository.preferencesFlow.first().isStrictModeDefault
            val isStrict = limit?.isStrictOverride == true || isGlobalStrict

            if (isStrict) {
                _strictLockMessage.value = "Strict Mode Active: You cannot delete ${limit?.appName ?: "this"} app limit while Strict Mode is active."
                return@launch
            }
            appLimitRepository.deleteLimit(packageName)
            loadInstalledApps()
        }
    }

    fun unlockForToday(packageName: String) {
        viewModelScope.launch {
            val limit = appLimitRepository.getLimitByPackage(packageName)
            val isGlobalStrict = FocusShieldApp.instance.preferencesRepository.preferencesFlow.first().isStrictModeDefault
            val isStrict = limit?.isStrictOverride == true || isGlobalStrict

            // Strict mode's whole promise is that today's limit cannot be waived from this menu.
            // Without this guard a single tap set isBypassedForToday and the manager honours the
            // bypass unconditionally, silently defeating strict mode.
            if (isStrict) {
                _strictLockMessage.value = "Strict Mode Active: ${limit?.appName ?: "App"} cannot be unlocked for today."
                return@launch
            }
            appLimitRepository.setBypassedForToday(packageName, appLimitRepository.getTodayDateString(), true)
        }
    }

    fun resetTodayUsage(packageName: String) {
        viewModelScope.launch {
            val limit = appLimitRepository.getLimitByPackage(packageName)
            val isGlobalStrict = FocusShieldApp.instance.preferencesRepository.preferencesFlow.first().isStrictModeDefault
            val isStrict = limit?.isStrictOverride == true || isGlobalStrict

            // Resetting restores the day's allowance (and the emergency passes), which is the
            // same bypass as unlocking under a different name — strict mode blocks it too.
            if (isStrict) {
                _strictLockMessage.value = "Strict Mode Active: You cannot reset ${limit?.appName ?: "this"} app's usage while Strict Mode is active."
                return@launch
            }
            val dateStr = appLimitRepository.getTodayDateString()
            appLimitRepository.setBypassedForToday(packageName, dateStr, false)
            appLimitRepository.setEmergencyUsesCount(packageName, dateStr, 0)
            // Actually reset usage to 0
            appLimitRepository.resetUsage(packageName, dateStr)
        }
    }
}
