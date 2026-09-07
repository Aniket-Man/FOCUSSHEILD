package com.example.feature.applimits.ui

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusShieldApp
import com.example.data.local.entity.AppLimitEntity
import com.example.data.local.entity.DailyAppUsageEntity
import com.example.data.repository.AppLimitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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

class AppLimitsViewModel(application: Application) : AndroidViewModel(application) {

    private val appLimitRepository: AppLimitRepository =
        (application as FocusShieldApp).appLimitRepository

    private val _installedApps = MutableStateFlow<List<InstalledAppChoice>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppChoice>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    val uiState: StateFlow<AppLimitsUiState> = combine(
        appLimitRepository.getAllLimitsFlow(),
        appLimitRepository.getUsageForDateFlow(appLimitRepository.getTodayDateString())
    ) { limits: List<AppLimitEntity>, usages: List<DailyAppUsageEntity> ->
        val usageMap = usages.associateBy { it.packageName }
        var totalMinutes = 0
        var activeCount = 0
        var reachedCount = 0

        val pm = application.packageManager

        val items = limits.map { limit ->
            val usage = usageMap[limit.packageName]
            val usedMillis = usage?.usedMillis ?: 0L
            val usedMin = (usedMillis / 60000L).toInt()
            val totalLimitMin = limit.dailyLimitMinutes
            val remainingMin = (totalLimitMin - usedMin).coerceAtLeast(0)
            val isBypassed = usage?.isBypassedForToday == true
            val isReached = !isBypassed && limit.isEnabled && remainingMin == 0
            val pct = if (totalLimitMin > 0) (usedMin.toFloat() / totalLimitMin.toFloat()).coerceIn(0f, 1f) else 0f

            if (limit.isEnabled) {
                activeCount++
                totalMinutes += usedMin
                if (isReached) reachedCount++
            }

            val iconDrawable = try {
                pm.getApplicationIcon(limit.packageName)
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AppLimitsUiState(isLoading = true)
    )

    init {
        loadInstalledApps()
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
            val usedMillis = usage?.usedMillis ?: 0L
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
            val usedMillis = usage?.usedMillis ?: 0L
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
            appLimitRepository.setBypassedForToday(packageName, appLimitRepository.getTodayDateString(), true)
        }
    }

    fun resetTodayUsage(packageName: String) {
        viewModelScope.launch {
            val dateStr = appLimitRepository.getTodayDateString()
            appLimitRepository.setBypassedForToday(packageName, dateStr, false)
            appLimitRepository.setEmergencyUsesCount(packageName, dateStr, 0)
            // Re-zero usage record
            val existing = appLimitRepository.getUsage(packageName, dateStr)
            if (existing != null) {
                // Delete or re-insert with 0
                val limit = appLimitRepository.getLimitByPackage(packageName)
                appLimitRepository.recordUsage(
                    packageName = packageName,
                    appName = limit?.appName ?: packageName,
                    dateString = dateStr,
                    deltaMillis = 0L
                )
            }
        }
    }
}
