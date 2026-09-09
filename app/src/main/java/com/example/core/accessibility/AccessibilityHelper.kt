package com.example.core.accessibility

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AccessibilityHelper {

    private const val TAG = "AccessibilityHelper"

    // Feature keys for automatic toggle on upon permission grant
    const val FEATURE_YT_SHORTS = "yt_shorts"
    const val FEATURE_IG_REELS = "ig_reels"
    const val FEATURE_FB_REELS = "fb_reels"
    const val FEATURE_SHORTS_ALWAYS = "shorts_always"
    const val FEATURE_UNINSTALL_PROTECTION = "uninstall_protection"
    const val FEATURE_SPLIT_SCREEN = "split_screen"
    const val FEATURE_FLOATING_WINDOW = "floating_window"

    private val _isServiceEnabledState = MutableStateFlow(false)
    val isServiceEnabledFlow: StateFlow<Boolean> = _isServiceEnabledState.asStateFlow()

    @Volatile
    private var isObserverRegistered = false

    @Volatile
    private var hasRedirectedRecently = false

    private val pendingGrantCallbacks = mutableListOf<() -> Unit>()

    /**
     * Registers a callback to be automatically invoked once the accessibility service is granted.
     */
    fun registerPendingGrant(callback: () -> Unit) {
        synchronized(pendingGrantCallbacks) {
            pendingGrantCallbacks.add(callback)
        }
    }

    /**
     * Clears all pending grant callbacks.
     */
    fun clearPendingGrants() {
        synchronized(pendingGrantCallbacks) {
            pendingGrantCallbacks.clear()
        }
    }

    /**
     * Saves a pending feature toggle to persistent SharedPreferences so that even if the app process
     * is restarted while in Android Settings, the toggle is turned ON automatically.
     */
    fun setPendingFeatureToggle(context: Context, featureKey: String) {
        try {
            val prefs = context.getSharedPreferences("focus_shield_sync_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("pending_feature_toggle", featureKey).apply()
            Log.d(TAG, "Registered pending feature toggle: $featureKey")
        } catch (_: Exception) {}
    }

    /**
     * Executes all registered in-memory grant callbacks and applies any pending feature toggle
     * to the DataStore repository so the toggle button is ON immediately upon return.
     */
    fun executePendingGrants(context: Context) {
        // 1. Run all in-memory registered callbacks on Main Thread
        val callbacksToRun = synchronized(pendingGrantCallbacks) {
            val copy = ArrayList(pendingGrantCallbacks)
            pendingGrantCallbacks.clear()
            copy
        }
        if (callbacksToRun.isNotEmpty()) {
            Handler(Looper.getMainLooper()).post {
                for (callback in callbacksToRun) {
                    try {
                        callback.invoke()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error executing in-memory grant callback: ${e.message}", e)
                    }
                }
            }
        }

        // 2. Persistent pending feature toggle
        try {
            val prefs = context.getSharedPreferences("focus_shield_sync_prefs", Context.MODE_PRIVATE)
            val pendingFeature = prefs.getString("pending_feature_toggle", null)
            if (pendingFeature != null) {
                prefs.edit().remove("pending_feature_toggle").apply()
                Log.d(TAG, "Executing persistent feature toggle: $pendingFeature")
                val repo = (context.applicationContext as? com.example.FocusShieldApp)?.preferencesRepository
                    ?: com.example.data.preferences.FocusPreferencesRepository(context.applicationContext)

                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    when (pendingFeature) {
                        FEATURE_YT_SHORTS -> repo.updateYouTubeShortsBlocking(true)
                        FEATURE_IG_REELS -> repo.updateInstagramReelsBlocking(true)
                        FEATURE_FB_REELS -> repo.updateFacebookReelsBlocking(true)
                        FEATURE_SHORTS_ALWAYS -> repo.updateShortsReelsAlwaysBlocked(true)
                        FEATURE_UNINSTALL_PROTECTION -> repo.updateBlockUninstall(true)
                        FEATURE_SPLIT_SCREEN -> repo.updateBlockSplitScreen(true)
                        FEATURE_FLOATING_WINDOW -> repo.updateBlockFloatingWindow(true)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing persistent feature toggle: ${e.message}", e)
        }
    }

    /**
     * Automatically redirects the user back to FocusShield from Android Settings
     * immediately after the accessibility service permission is granted.
     */
    fun redirectBackToApp(context: Context) {
        if (hasRedirectedRecently) return
        hasRedirectedRecently = true
        Handler(Looper.getMainLooper()).postDelayed({
            hasRedirectedRecently = false
        }, 3500L)

        Handler(Looper.getMainLooper()).post {
            try {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    addCategory(Intent.CATEGORY_LAUNCHER)
                } ?: Intent(context, com.example.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(launchIntent)
                Log.d(TAG, "Successfully redirected user back to app after accessibility permission was granted.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to redirect back to app: ${e.message}", e)
            }
        }
    }

    /**
     * Initializes the reactive state and registers system observers so that permissions
     * granted in Android Settings immediately update the app's state in real time.
     */
    fun init(context: Context) {
        val appContext = context.applicationContext
        updateState(appContext)

        if (!isObserverRegistered) {
            try {
                // Register system AccessibilityStateChangeListener
                val am = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
                am?.addAccessibilityStateChangeListener { _ ->
                    updateState(appContext)
                }

                // Register ContentObserver for secure settings changes (toggled in Android Settings)
                val contentResolver = appContext.contentResolver
                val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        super.onChange(selfChange)
                        updateState(appContext)
                    }
                }

                val enabledUri = Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                contentResolver.registerContentObserver(enabledUri, false, observer)

                val masterUri = Settings.Secure.getUriFor(Settings.Secure.ACCESSIBILITY_ENABLED)
                contentResolver.registerContentObserver(masterUri, false, observer)

                isObserverRegistered = true
                Log.d(TAG, "Accessibility system state observers successfully registered.")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register system accessibility observers: ${e.message}")
            }
        }
    }

    /**
     * Evaluates current accessibility service status, updates the reactive StateFlow, and returns result.
     */
    fun updateState(context: Context? = null): Boolean {
        val enabled = if (context != null) {
            isAccessibilityServiceEnabled(context)
        } else {
            FocusAccessibilityService.isServiceRunning || FocusAccessibilityService.instance != null
        }
        val wasEnabled = _isServiceEnabledState.value
        _isServiceEnabledState.value = enabled
        if (enabled && context != null && (!wasEnabled || pendingGrantCallbacks.isNotEmpty())) {
            executePendingGrants(context)
        }
        return enabled
    }

    /**
     * Lifecycle bridge: called directly by FocusAccessibilityService.onServiceConnected
     */
    fun notifyServiceConnected(service: FocusAccessibilityService) {
        Log.d(TAG, "FocusAccessibilityService onServiceConnected lifecycle event received.")
        _isServiceEnabledState.value = true
        executePendingGrants(service)
        redirectBackToApp(service)
    }

    /**
     * Lifecycle bridge: called directly by FocusAccessibilityService.onUnbind and onDestroy
     */
    fun notifyServiceDisconnected() {
        Log.d(TAG, "FocusAccessibilityService disconnected lifecycle event received.")
        _isServiceEnabledState.value = false
    }

    /**
     * Highly resilient accessibility service check.
     * Combines 4 detection layers to guarantee accurate detection on all Android ROMs
     * (Xiaomi MIUI/HyperOS, Samsung OneUI, Oppo ColorOS, Vivo Funtouch, OnePlus, Pixel):
     * 1. Live process memory flag check (FocusAccessibilityService.isServiceRunning)
     * 2. AccessibilityManager enabled service query (checking ID and resolve info)
     * 3. Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES string parsing
     * 4. Global Settings.Secure.ACCESSIBILITY_ENABLED flag
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        // Layer 1: Instant live process verification
        if (FocusAccessibilityService.isServiceRunning || FocusAccessibilityService.instance != null) {
            return true
        }

        val appContext = context.applicationContext
        val targetPackageName = appContext.packageName
        val expectedComponentName = ComponentName(appContext, FocusAccessibilityService::class.java)
        val targetServiceName = FocusAccessibilityService::class.java.name
        val targetSimpleName = FocusAccessibilityService::class.java.simpleName

        // Layer 2: System AccessibilityManager check
        try {
            val am = appContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            if (am != null) {
                val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK) +
                        am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
                for (enabledService in enabledServices) {
                    val serviceId = enabledService.id ?: ""
                    if (serviceId.isNotEmpty()) {
                        val matchesPackage = serviceId.contains(targetPackageName) ||
                                serviceId.contains(expectedComponentName.packageName) ||
                                serviceId.contains("com.example")
                        val matchesService = serviceId.contains(targetServiceName) ||
                                serviceId.contains(targetSimpleName) ||
                                serviceId.contains("FocusAccessibilityService")

                        if (matchesPackage && matchesService) {
                            return true
                        }

                        val comp = ComponentName.unflattenFromString(serviceId)
                        if (comp != null) {
                            val pkgMatches = comp.packageName == targetPackageName ||
                                    comp.packageName == expectedComponentName.packageName ||
                                    comp.packageName.contains("com.example")
                            val clsMatches = comp.className == targetServiceName ||
                                    comp.className.contains(targetSimpleName) ||
                                    comp.className.contains("FocusAccessibilityService")
                            if (pkgMatches && clsMatches) {
                                return true
                            }
                        }
                    }

                    // Check resolveInfo if present
                    val serviceInfo = enabledService.resolveInfo?.serviceInfo
                    if (serviceInfo != null) {
                        val sPackage = serviceInfo.packageName ?: ""
                        val sName = serviceInfo.name ?: ""

                        val pkgMatches = sPackage == targetPackageName ||
                                sPackage == expectedComponentName.packageName ||
                                sPackage.contains("com.example")
                        val nameMatches = sName == targetServiceName ||
                                sName.contains(targetSimpleName) ||
                                sName.contains("FocusAccessibilityService")

                        if (pkgMatches && nameMatches) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "AccessibilityManager list check exception: ${e.message}")
        }

        // Layer 3: Settings.Secure string parsing (Handles full, relative, and OEM custom formats)
        try {
            val settingValue = Settings.Secure.getString(
                appContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )

            if (!settingValue.isNullOrBlank()) {
                val globalAccessibilityEnabled = try {
                    Settings.Secure.getInt(appContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                } catch (_: Exception) {
                    1
                }

                if (globalAccessibilityEnabled == 1) {
                    // Direct string matching for OEM ROM variations
                    val hasPackageMatch = settingValue.contains(targetPackageName) ||
                            settingValue.contains(expectedComponentName.packageName) ||
                            settingValue.contains("com.example")
                    val hasServiceMatch = settingValue.contains(targetServiceName) ||
                            settingValue.contains(targetSimpleName) ||
                            settingValue.contains("FocusAccessibilityService")

                    if (hasPackageMatch && hasServiceMatch) {
                        return true
                    }

                    // ComponentName unflattening check
                    val colonSplitter = TextUtils.SimpleStringSplitter(':')
                    colonSplitter.setString(settingValue)

                    while (colonSplitter.hasNext()) {
                        val componentNameString = colonSplitter.next().trim()
                        if (componentNameString.contains("FocusAccessibilityService")) {
                            return true
                        }
                        val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                        if (enabledComponent != null) {
                            val pkgMatches = enabledComponent.packageName == targetPackageName ||
                                    enabledComponent.packageName == expectedComponentName.packageName ||
                                    enabledComponent.packageName.contains("com.example")
                            val clsMatches = enabledComponent.className == targetServiceName ||
                                    enabledComponent.className.contains(targetSimpleName) ||
                                    enabledComponent.className.contains("FocusAccessibilityService")
                            if (pkgMatches && clsMatches) {
                                return true
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Settings.Secure string check exception: ${e.message}")
        }

        return false
    }

    /**
     * Safely opens Accessibility Settings for the user.
     * Optionally registers a pending feature key and a grant callback so the toggle
     * turns on automatically and the user is redirected back to the app once enabled.
     */
    fun openAccessibilitySettings(
        context: Context,
        featureKey: String? = null,
        onGranted: (() -> Unit)? = null
    ) {
        if (onGranted != null) {
            registerPendingGrant(onGranted)
        }
        if (featureKey != null) {
            setPendingFeatureToggle(context, featureKey)
        }
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open ACTION_ACCESSIBILITY_SETTINGS: ${e.message}")
            openAppDetailsSettings(context)
        }
    }

    /**
     * Opens App Details Settings page.
     * Essential for Android 13+ (API 33+) "Restricted Settings" unlock flow:
     * User taps App Info -> 3 dots menu -> "Allow restricted settings".
     */
    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fallbackIntent)
        }
    }
}

