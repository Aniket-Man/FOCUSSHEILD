package com.example.feature.applimits.ui

import android.app.Application
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusShieldApp
import com.example.R
import com.example.core.accessibility.AccessibilityHelper
import com.example.core.util.DeviceUsageStatsHelper
import com.example.data.local.entity.AppLimitEntity
import com.example.data.local.entity.DailyAppUsageEntity
import com.example.data.repository.AppLimitRepository
import com.example.data.repository.AppLimitWriteResult
import com.example.feature.applimits.engine.AppLimitManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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
    val isAlreadyLimited: Boolean = false,
    val isStrictLocked: Boolean = false
)

data class AppLimitsUiState(
    val items: List<AppLimitUiItem> = emptyList(),
    val totalUsedMinutesToday: Int = 0,
    val activeLimitsCount: Int = 0,
    val reachedLimitsCount: Int = 0,
    val isLoading: Boolean = false,
    val loadError: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class AppLimitsViewModel(private val app: Application) : AndroidViewModel(app) {
    private val appLimitRepository: AppLimitRepository = (app as FocusShieldApp).appLimitRepository
    private val iconCache = java.util.concurrent.ConcurrentHashMap<String, Drawable>()
    private val reload = MutableStateFlow(0)
    private val today = MutableStateFlow(appLimitRepository.getTodayDateString())
    private var lastLoaded = AppLimitsUiState(isLoading = true)

    private val _installedApps = MutableStateFlow<List<InstalledAppChoice>>(emptyList())
    val installedApps = _installedApps.asStateFlow()
    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps = _isLoadingApps.asStateFlow()
    private val _appsLoadError = MutableStateFlow<String?>(null)
    val appsLoadError = _appsLoadError.asStateFlow()
    private val _isSaving = MutableStateFlow(false)
    val isSaving = _isSaving.asStateFlow()
    private val _completedEditors = MutableStateFlow<Set<String>>(emptySet())
    val completedEditors = _completedEditors.asStateFlow()

    fun acknowledgeEditor(editorId: String) { _completedEditors.value -= editorId }
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError = _operationError.asStateFlow()
    private val _infoMessage = MutableStateFlow<String?>(null)
    val infoMessage = _infoMessage.asStateFlow()
    private val _strictLockMessage = MutableStateFlow<String?>(null)
    val strictLockMessage = _strictLockMessage.asStateFlow()
    private val _isAccessibilityEnabled = MutableStateFlow(false)
    val isAccessibilityEnabled = _isAccessibilityEnabled.asStateFlow()
    private val _hasUsageAccess = MutableStateFlow(false)
    val hasUsageAccess = _hasUsageAccess.asStateFlow()
    private val _overlayLaunchWarning = MutableStateFlow<String?>(null)
    val overlayLaunchWarning = _overlayLaunchWarning.asStateFlow()

    val uiState: StateFlow<AppLimitsUiState> = combine(today, reload) { date, _ -> date }
        .flatMapLatest { date ->
            combine(appLimitRepository.getAllLimitsFlow(), appLimitRepository.getUsageForDateFlow(date)) {
                    limits: List<AppLimitEntity>, usages: List<DailyAppUsageEntity> ->
                buildState(limits, usages).also { lastLoaded = it }
            }.onStart { emit(lastLoaded.copy(isLoading = true, loadError = null)) }
                .catch { exception ->
                    if (exception is CancellationException) throw exception
                    Log.e("AppLimits", "Unable to load app limits", exception)
                    emit(lastLoaded.copy(isLoading = false, loadError = app.getString(R.string.limits_load_error)))
                }
        }.flowOn(Dispatchers.IO).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppLimitsUiState(isLoading = true))

    private fun buildState(limits: List<AppLimitEntity>, usages: List<DailyAppUsageEntity>): AppLimitsUiState {
        val usageMap = usages.associateBy { it.packageName }
        val systemUsage = DeviceUsageStatsHelper.getTodayAllAppsUsageMillis(app)
        val items = limits.map { limit ->
            val usage = usageMap[limit.packageName]
            // A permission flap must not display a fresh allowance after time was already recorded.
            val usedMillis = maxOf(systemUsage[limit.packageName] ?: 0L, usage?.usedMillis ?: 0L)
            val remaining = (limit.dailyLimitMinutes * 60_000L - usedMillis).coerceAtLeast(0L)
            val bypassed = usage?.isBypassedForToday == true
            val icon = iconCache[limit.packageName] ?: try {
                app.packageManager.getApplicationIcon(limit.packageName).also { iconCache[limit.packageName] = it }
            } catch (_: Exception) { null }
            AppLimitUiItem(
                packageName = limit.packageName, appName = limit.appName,
                dailyLimitMinutes = limit.dailyLimitMinutes, usedMinutes = (usedMillis / 60_000L).toInt(),
                remainingMinutes = (remaining / 60_000L).toInt(),
                usagePercentage = if (limit.dailyLimitMinutes > 0) (usedMillis.toFloat() / (limit.dailyLimitMinutes * 60_000L)).coerceIn(0f, 1f) else 0f,
                isBypassedToday = bypassed, isEnabled = limit.isEnabled, isStrictOverride = limit.isStrictOverride,
                showRemindersBeforeLimit = limit.showRemindersBeforeLimit, streakDays = limit.streakDays,
                isLimitReached = !bypassed && limit.isEnabled && remaining == 0L,
                emergencyUsesCount = usage?.emergencyUsesCount ?: 0, emergencyUsesAllowed = limit.emergencyUsesAllowed,
                appIcon = icon
            )
        }.sortedWith(compareByDescending<AppLimitUiItem> { it.isLimitReached }.thenByDescending { it.usedMinutes })
        return AppLimitsUiState(items, items.filter { it.isEnabled }.sumOf { it.usedMinutes },
            items.count { it.isEnabled }, items.count { it.isLimitReached })
    }

    init {
        loadInstalledApps()
        checkAccessibilityStatus()
        viewModelScope.launch {
            AccessibilityHelper.isServiceEnabledFlow.collect { _isAccessibilityEnabled.value = it }
        }
        viewModelScope.launch {
            try { AppLimitManager.instance.overlayLaunchWarning.collect { _overlayLaunchWarning.value = it } }
            catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                Log.w("AppLimits", "Enforcement status is unavailable", exception)
            }
        }
    }

    fun refresh() {
        checkAccessibilityStatus()
        today.value = appLimitRepository.getTodayDateString()
        reload.value++
    }

    fun loadInstalledApps() {
        if (_isLoadingApps.value) return
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            _isLoadingApps.value = true
            try {
                val apps = withContext(Dispatchers.IO) {
                    val pm = app.packageManager
                    val limits = appLimitRepository.getAllLimits().associateBy { it.packageName }
                    pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                        .mapNotNull { info ->
                            val pkg = info.activityInfo.packageName
                            if (pkg == app.packageName) return@mapNotNull null
                            InstalledAppChoice(pkg, info.loadLabel(pm).toString(),
                                try { info.loadIcon(pm) } catch (_: Exception) { null },
                                isAlreadyLimited = pkg in limits, isStrictLocked = limits[pkg]?.isStrictOverride == true)
                        }.distinctBy { it.packageName }.sortedBy { it.appName.lowercase(Locale.getDefault()) }
                }
                _installedApps.value = apps
                _appsLoadError.value = null
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                Log.e("AppLimits", "Unable to load installed apps", exception)
                _appsLoadError.value = app.getString(R.string.limits_apps_load_error)
            } finally { _isLoadingApps.value = false }
        }
    }

    fun checkAccessibilityStatus() {
        _isAccessibilityEnabled.value = AccessibilityHelper.isAccessibilityServiceEnabled(app)
        _hasUsageAccess.value = DeviceUsageStatsHelper.hasUsageStatsPermission(app)
    }

    fun notifyStrictLock(message: String = app.getString(R.string.limits_strict_locked)) { _strictLockMessage.value = message }
    fun clearStrictLockMessage() { _strictLockMessage.value = null }
    fun clearFeedback() { _operationError.value = null; _infoMessage.value = null; _strictLockMessage.value = null }

    private suspend fun applyMutation(block: suspend () -> AppLimitWriteResult): AppLimitWriteResult {
        if (_isSaving.value) return AppLimitWriteResult.Rejected(AppLimitWriteResult.Reason.BUSY)
        _isSaving.value = true
        try {
            val result = try {
                withContext(Dispatchers.IO) { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (exception: Exception) {
                AppLimitWriteResult.Failed(exception)
            }
            clearFeedback()
            when (result) {
                is AppLimitWriteResult.Applied -> {
                    if (result.followUpFailure != null) {
                        Log.w("AppLimits", "Local change saved; enforcement follow-up failed", result.followUpFailure)
                        _operationError.value = app.getString(R.string.limits_follow_up_error)
                    } else _infoMessage.value = app.getString(R.string.limits_change_saved)
                    loadInstalledApps()
                }
                is AppLimitWriteResult.Rejected -> _operationError.value = result.feedbackMessage(app)
                is AppLimitWriteResult.Failed -> {
                    Log.e("AppLimits", "App limit write failed", result.exception)
                    _operationError.value = result.feedbackMessage(app)
                }
            }
            return result
        } finally { _isSaving.value = false }
    }

    /** Owned by the ViewModel, so a rotation cannot lose a committed result or close a failed form. */
    suspend fun saveLimit(
        packageName: String, appName: String, dailyLimitMinutes: Int,
        isStrictOverride: Boolean = false, showRemindersBeforeLimit: Boolean = true,
        emergencyUsesAllowed: Int = 1, requireExisting: Boolean = false, editorId: String? = null
    ): AppLimitWriteResult = viewModelScope.async(start = CoroutineStart.UNDISPATCHED) {
        applyMutation {
            appLimitRepository.saveLimit(packageName, appName, dailyLimitMinutes, isEnabled = true,
                isStrictOverride = isStrictOverride, showRemindersBeforeLimit = showRemindersBeforeLimit,
                emergencyUsesAllowed = emergencyUsesAllowed, requireExisting = requireExisting)
        }.also { result ->
            if (result is AppLimitWriteResult.Applied && editorId != null) _completedEditors.value += editorId
        }
    }.await()

    private fun launchMutation(block: suspend () -> AppLimitWriteResult) =
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) { applyMutation(block) }

    fun toggleLimit(packageName: String, isEnabled: Boolean) = launchMutation {
        appLimitRepository.setLimitEnabledResult(packageName, isEnabled)
    }
    fun deleteLimit(packageName: String) = launchMutation { appLimitRepository.deleteLimitResult(packageName) }
    fun updateStrictMode(packageName: String, isStrict: Boolean) = launchMutation {
        appLimitRepository.updateStrictMode(packageName, isStrict)
    }
    fun updateRemindersSetting(packageName: String, showReminders: Boolean) = launchMutation {
        appLimitRepository.updateRemindersSetting(packageName, showReminders)
    }
    fun unlockForToday(packageName: String) = launchMutation { appLimitRepository.bypassLimitForToday(packageName) }
    fun resetTodayUsage(packageName: String) = launchMutation { appLimitRepository.resetUsage(packageName) }
}
