package com.example.feature.blockedapps

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusShieldApp
import com.example.core.accessibility.AccessibilityHelper
import com.example.data.repository.BlockedAppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean,
    val isSystemApp: Boolean,
    val icon: Drawable? = null
)

data class BlockedAppsUiState(
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isAccessibilityEnabled: Boolean = false,
    val installedApps: List<InstalledAppItem> = emptyList(),
    val totalBlockedCount: Int = 0
)

class BlockedAppsViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val blockedAppRepository: BlockedAppRepository =
        (application as FocusShieldApp).blockedAppRepository

    private val _searchQuery = MutableStateFlow("")
    private val _rawInstalledApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _isAccessibilityEnabled = MutableStateFlow(false)

    val uiState: StateFlow<BlockedAppsUiState> = combine(
        _rawInstalledApps,
        blockedAppRepository.allBlockedApps,
        _searchQuery,
        _isLoading,
        _isAccessibilityEnabled
    ) { rawApps, blockedEntities, query, loading, accessibilityEnabled ->
        val blockedMap = blockedEntities.associate { it.packageName to it.isEnabled }

        val appsWithStatus = rawApps.map { app ->
            val isBlocked = blockedMap[app.packageName] ?: false
            app.copy(isBlocked = isBlocked)
        }

        val filtered = if (query.isBlank()) {
            appsWithStatus
        } else {
            appsWithStatus.filter {
                it.appName.contains(query, ignoreCase = true) ||
                it.packageName.contains(query, ignoreCase = true)
            }
        }

        val blockedCount = appsWithStatus.count { it.isBlocked }

        BlockedAppsUiState(
            searchQuery = query,
            isLoading = loading,
            isAccessibilityEnabled = accessibilityEnabled,
            installedApps = filtered,
            totalBlockedCount = blockedCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = BlockedAppsUiState()
    )

    init {
        loadInstalledApps()
        checkAccessibilityStatus()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun checkAccessibilityStatus() {
        val enabled = AccessibilityHelper.isAccessibilityServiceEnabled(getApplication())
        _isAccessibilityEnabled.value = enabled
    }

    fun toggleAppBlocked(packageName: String, appName: String, isBlocked: Boolean) {
        viewModelScope.launch {
            blockedAppRepository.setAppBlocked(packageName, appName, isBlocked)
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoading.value = true
            val apps = withContext(Dispatchers.IO) {
                queryInstalledLaunchableApps()
            }
            _rawInstalledApps.value = apps
            _isLoading.value = false
        }
    }

    private fun queryInstalledLaunchableApps(): List<InstalledAppItem> {
        val context = getApplication<Application>()
        val pm = context.packageManager
        val myPackage = context.packageName

        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = pm.queryIntentActivities(launcherIntent, 0)
        val appList = mutableListOf<InstalledAppItem>()
        val seenPackages = mutableSetOf<String>()

        for (resolveInfo in resolveInfos) {
            val packageName = resolveInfo.activityInfo.packageName
            if (packageName == myPackage || seenPackages.contains(packageName)) continue
            seenPackages.add(packageName)

            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val appName = resolveInfo.loadLabel(pm).toString()
                val icon = resolveInfo.loadIcon(pm)

                appList.add(
                    InstalledAppItem(
                        packageName = packageName,
                        appName = appName,
                        isBlocked = false,
                        isSystemApp = isSystem,
                        icon = icon
                    )
                )
            } catch (e: Exception) {
                // Ignore missing package info
            }
        }

        // Sort: User apps first, then alphabetical by name
        return appList.sortedWith(
            compareBy<InstalledAppItem> { it.isSystemApp }
                .thenBy { it.appName.lowercase() }
        )
    }
}
