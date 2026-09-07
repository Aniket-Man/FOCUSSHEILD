package com.example.feature.notificationblocker.ui

import android.app.Application
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusShieldApp
import com.example.core.permission.FocusPermissionManager
import com.example.feature.notificationblocker.domain.NotificationBlockMode
import com.example.feature.notificationblocker.domain.SilencedNotificationRecord
import com.example.feature.notificationblocker.engine.NotificationBlockerEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null
)

data class NotificationBlockerUiState(
    val isMasterEnabled: Boolean = false,
    val blockMode: NotificationBlockMode = NotificationBlockMode.SESSION_ONLY,
    val blockedPackages: Set<String> = emptySet(),
    val alwaysBlockedPackages: Set<String> = emptySet(),
    val silencedVault: List<SilencedNotificationRecord> = emptyList(),
    val silencedCountToday: Int = 0,
    val isSessionActive: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val installedApps: List<InstalledAppItem> = emptyList(),
    val isLoadingApps: Boolean = true
)

class NotificationBlockerViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val engine: NotificationBlockerEngine = try {
        NotificationBlockerEngine.instance
    } catch (e: Exception) {
        val app = application as FocusShieldApp
        NotificationBlockerEngine.initialize(
            appContext = app,
            preferencesRepository = app.preferencesRepository,
            blockedAttemptRepository = app.blockedAttemptRepository
        )
    }

    private val _isPermissionGranted = MutableStateFlow(false)
    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    private val _isLoadingApps = MutableStateFlow(true)

    val uiState: StateFlow<NotificationBlockerUiState> = combine(
        engine.isMasterEnabled,
        engine.blockMode,
        engine.selectedPackagesFlow,
        engine.alwaysBlockedPackagesFlow,
        engine.silencedVaultFlow,
        engine.silencedCountToday,
        engine.isSessionActive,
        _isPermissionGranted,
        _installedApps,
        _isLoadingApps
    ) { params: Array<Any> ->
        @Suppress("UNCHECKED_CAST")
        NotificationBlockerUiState(
            isMasterEnabled = params[0] as Boolean,
            blockMode = params[1] as NotificationBlockMode,
            blockedPackages = params[2] as Set<String>,
            alwaysBlockedPackages = params[3] as Set<String>,
            silencedVault = params[4] as List<SilencedNotificationRecord>,
            silencedCountToday = params[5] as Int,
            isSessionActive = params[6] as Boolean,
            isPermissionGranted = params[7] as Boolean,
            installedApps = params[8] as List<InstalledAppItem>,
            isLoadingApps = params[9] as Boolean
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = NotificationBlockerUiState()
    )

    init {
        checkPermission()
        loadInstalledApps()
    }

    fun checkPermission() {
        _isPermissionGranted.value = FocusPermissionManager.isNotificationListenerGranted(getApplication())
    }

    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            val apps = withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val activities = pm.queryIntentActivities(intent, 0)
                val seen = mutableSetOf<String>()
                val list = mutableListOf<InstalledAppItem>()

                for (info in activities) {
                    val pkg = info.activityInfo.packageName
                    if (pkg == context.packageName || pkg == "android" || pkg.startsWith("com.android.systemui")) {
                        continue
                    }
                    if (seen.add(pkg)) {
                        val name = info.loadLabel(pm).toString()
                        val icon = try { info.loadIcon(pm) } catch (e: Exception) { null }
                        list.add(InstalledAppItem(packageName = pkg, appName = name, icon = icon))
                    }
                }
                list.sortedBy { it.appName.lowercase() }
            }
            _installedApps.value = apps
            _isLoadingApps.value = false
        }
    }

    fun toggleMaster(enabled: Boolean) {
        engine.setMasterEnabled(enabled)
    }

    fun setBlockMode(mode: NotificationBlockMode) {
        engine.setBlockMode(mode)
    }

    fun toggleApp(packageName: String, blocked: Boolean) {
        engine.toggleApp(packageName, blocked)
    }

    fun toggleAlwaysSilent(packageName: String, alwaysSilent: Boolean) {
        engine.toggleAlwaysSilent(packageName, alwaysSilent)
    }

    fun setAllAppsBlocked(packages: Set<String>) {
        engine.setBlockedPackages(packages)
    }

    fun clearVault() {
        engine.clearVault()
    }

    fun deleteVaultItem(id: String) {
        engine.deleteVaultItem(id)
    }

    fun simulateTestNotification() {
        engine.simulateTestNotification()
    }
}
