package com.example.feature.notificationblocker.engine

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.core.accessibility.ProtectionPolicy
import com.example.data.local.entity.BlockedEventSource
import com.example.data.local.entity.BlockedEventType
import com.example.data.preferences.FocusPreferencesRepository
import com.example.data.repository.BlockedAttemptRepository
import com.example.feature.blocker.FocusBlockerManager
import com.example.feature.notificationblocker.domain.NotificationBlockMode
import com.example.feature.notificationblocker.domain.SilencedNotificationRecord
import com.example.feature.session.domain.SessionState
import com.example.feature.session.engine.FocusSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Intelligent Real-time Notification Blocker Engine for FocusShield.
 * 
 * Capabilities:
 * - Session-Only Mode: Silences distracting notifications during active Focus Sessions, while allowing normal notifications when outside sessions.
 * - Always-Silent Mode: Permanently silences selected apps (24/7).
 * - Smart Session Sync: Automatically silences notifications for any app blocked in the current Focus Session or App Limit.
 * - Notification Vault: Intercepts and logs notification metadata (Title, Text, Timestamp, App Name) so user never permanently loses important context.
 * - Strict Safety & Whitelist: Never blocks incoming phone calls, dialer, alarms, emergency broadcasts, or FocusShield critical alerts.
 */
class NotificationBlockerEngine private constructor(
    private val appContext: Context,
    private val preferencesRepository: FocusPreferencesRepository,
    private val blockedAttemptRepository: BlockedAttemptRepository,
    private val sessionManager: FocusSessionManager = FocusSessionManager.instance
) {
    private val tag = "NotifBlockerEngine"
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Fast in-memory caches
    private val selectedPackages = ConcurrentHashMap.newKeySet<String>()
    private val alwaysBlockedPackages = ConcurrentHashMap.newKeySet<String>()
    private val silencedVaultItems = CopyOnWriteArrayList<SilencedNotificationRecord>()

    private val _silencedVaultFlow = MutableStateFlow<List<SilencedNotificationRecord>>(emptyList())
    val silencedVaultFlow: StateFlow<List<SilencedNotificationRecord>> = _silencedVaultFlow.asStateFlow()

    private val _isMasterEnabled = MutableStateFlow(false)
    val isMasterEnabled: StateFlow<Boolean> = _isMasterEnabled.asStateFlow()

    private val _blockMode = MutableStateFlow(NotificationBlockMode.SESSION_ONLY)
    val blockMode: StateFlow<NotificationBlockMode> = _blockMode.asStateFlow()

    private val _selectedPackagesFlow = MutableStateFlow<Set<String>>(emptySet())
    val selectedPackagesFlow: StateFlow<Set<String>> = _selectedPackagesFlow.asStateFlow()

    private val _alwaysBlockedPackagesFlow = MutableStateFlow<Set<String>>(emptySet())
    val alwaysBlockedPackagesFlow: StateFlow<Set<String>> = _alwaysBlockedPackagesFlow.asStateFlow()

    private val _silencedCountToday = MutableStateFlow(0)
    val silencedCountToday: StateFlow<Int> = _silencedCountToday.asStateFlow()

    private val _isSessionActive = MutableStateFlow(false)
    val isSessionActive: StateFlow<Boolean> = _isSessionActive.asStateFlow()

    init {
        // Observe preferences and sync fast cache
        engineScope.launch {
            preferencesRepository.preferencesFlow.collectLatest { prefs ->
                _isMasterEnabled.value = prefs.isBlockNotificationsEnabled
                _blockMode.value = try {
                    NotificationBlockMode.valueOf(prefs.notificationBlockMode)
                } catch (e: Exception) {
                    NotificationBlockMode.SESSION_ONLY
                }

                selectedPackages.clear()
                selectedPackages.addAll(prefs.blockedNotificationPackages)
                _selectedPackagesFlow.value = prefs.blockedNotificationPackages

                alwaysBlockedPackages.clear()
                alwaysBlockedPackages.addAll(prefs.alwaysBlockedNotificationPackages)
                _alwaysBlockedPackagesFlow.value = prefs.alwaysBlockedNotificationPackages
            }
        }

        // Today's silenced count is derived from the event rows, not from a running counter, so it
        // resets at midnight on its own and is reconstructed correctly after a cloud restore.
        engineScope.launch {
            blockedAttemptRepository.getTodayEventCountFlow(BlockedEventType.NOTIFICATION_SILENCED)
                .collectLatest { _silencedCountToday.value = it }
        }

        // Observe session active state
        engineScope.launch {
            sessionManager.sessionState.collectLatest { state ->
                val active = (state == SessionState.RUNNING || state == SessionState.PAUSED)
                _isSessionActive.value = active
            }
        }
    }

    /**
     * Determines whether an incoming notification should be intercepted and cancelled.
     */
    fun shouldBlockNotification(packageName: String): Boolean {
        // 1. Safety Check: never block system, emergency, dialer, or FocusShield itself
        if (isProtectedPackage(packageName)) {
            return false
        }

        // 2. Either the global notification blocker or the active schedule can enable session blocking.
        val currentSession = sessionManager.activeSession.value
        val scheduleEnabled = currentSession?.blockNotifications == true
        if (!_isMasterEnabled.value && !scheduleEnabled) {
            return false
        }

        // 3. Always-Silent Check (silenced 24/7 regardless of focus session)
        if (alwaysBlockedPackages.contains(packageName)) {
            return true
        }

        val sessionActive = _isSessionActive.value

        // 4. Mode-based Evaluation
        return when (_blockMode.value) {
            NotificationBlockMode.SESSION_ONLY -> {
                if (sessionActive) {
                    // In session: block if in user-selected list OR blocked in active session
                    val isExplicitlySelected = selectedPackages.contains(packageName)
                    val isSessionAppBlocked = isPackageBlockedInActiveSession(packageName)
                    isExplicitlySelected || isSessionAppBlocked
                } else {
                    // No active session: allow normal notifications!
                    false
                }
            }
            NotificationBlockMode.ALWAYS_SILENT -> {
                // In always-silent mode, any package in selectedPackages is silenced 24/7
                selectedPackages.contains(packageName)
            }
            NotificationBlockMode.SMART_HYBRID -> {
                if (sessionActive) {
                    selectedPackages.contains(packageName) || isPackageBlockedInActiveSession(packageName)
                } else {
                    false
                }
            }
        }
    }

    /**
     * Intercepts, cancels, and safely vaults an incoming notification.
     */
    fun processIncomingNotification(
        sbn: StatusBarNotification,
        cancelAction: () -> Unit
    ): Boolean {
        val rawPkg = sbn.packageName ?: return false
        if (!shouldBlockNotification(rawPkg)) {
            return false
        }

        try {
            // Cancel notification immediately from status bar
            cancelAction()

            val extras = sbn.notification?.extras
            val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                ?: extras?.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
                ?: "Notification"
            val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                ?: extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                ?: extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
                ?: ""

            val conversationTitle = extras?.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            val senderPerson = extras?.getCharSequence("android.messagingUser")?.toString()
                ?: extras?.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()

            val appName = getAppName(rawPkg)

            // For messaging apps like WhatsApp, Telegram, Signal, Messages: title is usually sender name or group name
            val senderName = when {
                !conversationTitle.isNullOrBlank() -> conversationTitle
                !senderPerson.isNullOrBlank() -> senderPerson
                rawPkg.contains("whatsapp", ignoreCase = true) -> title.ifBlank { "WhatsApp Contact" }
                rawPkg.contains("telegram", ignoreCase = true) -> title.ifBlank { "Telegram Contact" }
                rawPkg.contains("messaging", ignoreCase = true) || rawPkg.contains("mms", ignoreCase = true) -> title.ifBlank { "Sender" }
                rawPkg.contains("instagram", ignoreCase = true) -> title.ifBlank { "Instagram Direct" }
                else -> if (title.isNotBlank() && title != appName) title else null
            }

            val sessionActive = _isSessionActive.value
            val currentSession = sessionManager.activeSession.value

            val record = SilencedNotificationRecord(
                id = UUID.randomUUID().toString(),
                packageName = rawPkg,
                appName = appName,
                title = title,
                text = text,
                senderName = senderName,
                timestamp = System.currentTimeMillis(),
                wasDuringSession = sessionActive,
                sessionId = currentSession?.id
            )

            // Add to in-memory vault (capped at 150 items)
            silencedVaultItems.add(0, record)
            if (silencedVaultItems.size > 150) {
                silencedVaultItems.removeAt(silencedVaultItems.size - 1)
            }
            _silencedVaultFlow.value = silencedVaultItems.toList()

            // Update stats. The counter is only a local cache for the "today" chip; the durable
            // record is the NOTIFICATION_SILENCED event, which is what syncs and what a restore
            // rebuilds the figure from. Only the fact of silencing is recorded — never the title,
            // text or sender of the notification itself.
            engineScope.launch {
                preferencesRepository.incrementBlockedNotificationsCount()
                try {
                    blockedAttemptRepository.recordAttempt(
                        packageName = rawPkg,
                        appName = appName,
                        eventType = BlockedEventType.NOTIFICATION_SILENCED,
                        source = BlockedEventSource.NOTIFICATION_ENGINE,
                        sessionId = currentSession?.id
                    )
                } catch (e: Exception) {
                    Log.w(tag, "Failed to record blocked attempt: ${e.message}")
                }
            }

            Log.i(tag, "Silenced notification from $appName ($rawPkg): '$title' -> '$text'")
            return true
        } catch (e: Exception) {
            Log.e(tag, "Error intercepting notification: ${e.message}", e)
            return false
        }
    }

    /**
     * Checks if an app is protected from notification blocking.
     */
    private fun isProtectedPackage(pkg: String): Boolean {
        if (pkg.isBlank()) return true
        if (pkg == appContext.packageName ||
            pkg.startsWith("com.example") ||
            pkg.startsWith("com.aistudio.focusshield")
        ) {
            return true
        }
        if (pkg == "android" || pkg == "com.android.systemui") {
            return true
        }
        if (ProtectionPolicy.SAFE_SYSTEM_PACKAGES.contains(pkg)) {
            return true
        }
        // Telecom / Dialers / Emergency alerts
        if (pkg.contains("telecom") || pkg.contains("dialer") || pkg.contains("phone") || pkg.contains("emergency")) {
            return true
        }
        return false
    }

    /**
     * Checks if the package is in the active Focus Session's blocked apps list.
     */
    private fun isPackageBlockedInActiveSession(pkg: String): Boolean {
        return try {
            val protectionState = FocusBlockerManager.instance.getCurrentProtectionState()
            protectionState.isSessionActive && protectionState.blockedPackages.contains(pkg)
        } catch (e: Exception) {
            false
        }
    }

    private fun getAppName(packageName: String): String {
        return try {
            val pm = appContext.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: Exception) {
            packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }

    // Public Management API for UI & ViewModels

    fun setMasterEnabled(enabled: Boolean) {
        engineScope.launch {
            preferencesRepository.updateBlockNotifications(enabled)
        }
    }

    fun setBlockMode(mode: NotificationBlockMode) {
        engineScope.launch {
            preferencesRepository.updateNotificationBlockMode(mode.name)
        }
    }

    fun toggleApp(packageName: String, blocked: Boolean) {
        engineScope.launch {
            preferencesRepository.toggleBlockedNotificationPackage(packageName, blocked)
        }
    }

    fun toggleAlwaysSilent(packageName: String, alwaysSilent: Boolean) {
        engineScope.launch {
            preferencesRepository.toggleAlwaysBlockedNotificationPackage(packageName, alwaysSilent)
        }
    }

    fun setBlockedPackages(packages: Set<String>) {
        engineScope.launch {
            preferencesRepository.updateBlockedNotificationPackages(packages)
        }
    }

    fun setAlwaysBlockedPackages(packages: Set<String>) {
        engineScope.launch {
            preferencesRepository.updateAlwaysBlockedNotificationPackages(packages)
        }
    }

    /**
     * Clears the in-memory vault of silenced notification *contents* (title / text / sender).
     *
     * This deliberately no longer resets the "silenced today" figure: that figure counts events
     * that really happened and now comes from the event rows, which are the durable record. A
     * privacy clear of message contents does not make the silencing not have occurred.
     */
    fun clearVault() {
        silencedVaultItems.clear()
        _silencedVaultFlow.value = emptyList()
    }

    fun deleteVaultItem(id: String) {
        silencedVaultItems.removeAll { it.id == id }
        _silencedVaultFlow.value = silencedVaultItems.toList()
    }

    /**
     * Simulates a test silenced notification to verify engine and vault UI.
     */
    fun simulateTestNotification(
        packageName: String = "com.whatsapp",
        appName: String = "WhatsApp",
        senderName: String = "Alex Rivera",
        messageText: String = "Hey, let's review the chapter notes together when you finish your study session!"
    ) {
        val record = SilencedNotificationRecord(
            id = UUID.randomUUID().toString(),
            packageName = packageName,
            appName = appName,
            title = senderName,
            text = messageText,
            senderName = senderName,
            timestamp = System.currentTimeMillis(),
            wasDuringSession = _isSessionActive.value,
            sessionId = sessionManager.activeSession.value?.id
        )
        silencedVaultItems.add(0, record)
        _silencedVaultFlow.value = silencedVaultItems.toList()
        engineScope.launch {
            preferencesRepository.incrementBlockedNotificationsCount()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: NotificationBlockerEngine? = null

        val instance: NotificationBlockerEngine
            get() = INSTANCE ?: throw IllegalStateException("NotificationBlockerEngine is not initialized!")

        fun initialize(
            appContext: Context,
            preferencesRepository: FocusPreferencesRepository,
            blockedAttemptRepository: BlockedAttemptRepository,
            sessionManager: FocusSessionManager = FocusSessionManager.instance
        ): NotificationBlockerEngine {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: NotificationBlockerEngine(
                    appContext = appContext.applicationContext,
                    preferencesRepository = preferencesRepository,
                    blockedAttemptRepository = blockedAttemptRepository,
                    sessionManager = sessionManager
                ).also { INSTANCE = it }
            }
        }
    }
}
