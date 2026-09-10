package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.cloud.auth.AuthRepository
import com.example.cloud.net.ConnectivityMonitor
import com.example.cloud.sync.CloudInstallState
import com.example.cloud.sync.SyncEngine
import com.example.cloud.sync.SyncGateway
import com.example.cloud.sync.SyncTracker
import com.example.cloud.sync.SyncedPreferencesObserver
import com.example.core.util.ChannelLogoStorageManager
import com.example.data.local.FocusShieldDatabase
import com.example.data.preferences.FocusPreferencesRepository
import com.example.data.repository.BlockedAppRepository
import com.example.data.repository.BlockedAttemptRepository
import com.example.data.repository.BreakRepository
import com.example.data.repository.SessionRepository
import com.example.data.repository.StudyChannelRepository
import com.example.data.repository.StudyPlanRepository
import com.example.data.repository.SubjectRepository
import com.example.feature.blocker.FocusBlockerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FocusShieldApp : Application(), ImageLoaderFactory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { FocusShieldDatabase.getInstance(this) }
    val sessionRepository by lazy { SessionRepository(database.sessionDao(), database.studyActivityDao()) }
    val subjectRepository by lazy { SubjectRepository(database.subjectDao(), database.topicDao()) }
    val studyPlanRepository by lazy { StudyPlanRepository(database.studyPlanDao()) }
    val breakRepository by lazy { BreakRepository(database.breakRecordDao()) }
    val blockedAppRepository by lazy { BlockedAppRepository(database.blockedAppDao()) }
    val blockedAttemptRepository by lazy { BlockedAttemptRepository(database.blockedAttemptDao()) }
    val studyChannelRepository by lazy { StudyChannelRepository(database.studyChannelDao(), applicationScope, this) }
    val preferencesRepository by lazy { FocusPreferencesRepository(this) }
    val appLimitRepository by lazy {
        com.example.data.repository.AppLimitRepository(
            appLimitDao = database.appLimitDao(),
            dailyAppUsageDao = database.dailyAppUsageDao(),
            appLimitSessionDao = database.appLimitSessionDao()
        )
    }
    val analyticsRepository by lazy {
        com.example.data.repository.AnalyticsRepository(
            sessionDao = database.sessionDao(),
            breakRecordDao = database.breakRecordDao(),
            blockedAttemptDao = database.blockedAttemptDao(),
            studyActivityDao = database.studyActivityDao(),
            preferencesRepository = preferencesRepository,
            context = this
        )
    }
    val blockedWebsiteRepository by lazy {
        com.example.data.repository.BlockedWebsiteRepository(database.blockedWebsiteDao())
    }
    val focusScheduleRepository by lazy {
        com.example.data.repository.FocusScheduleRepository(
            dao = database.focusScheduleDao(),
            context = this,
            coroutineScope = applicationScope
        )
    }
    val keywordRepository by lazy {
        com.example.data.repository.KeywordRepository(database.keywordDao(), applicationScope)
    }
    val dailyUnlockRepository by lazy {
        com.example.data.repository.DailyUnlockRepository(database.dailyUnlockDao(), applicationScope)
    }
    val scratchCardRepository by lazy {
        com.example.data.repository.ScratchCardRepository(database.scratchCardDao(), applicationScope)
    }

    // Optional Supabase cloud account layer. AuthRepository is cheap to construct (it does no
    // network I/O until restoreOnStart()); connectivityMonitor powers sync retry gating.
    val authRepository by lazy { AuthRepository(this) }
    val connectivityMonitor by lazy { ConnectivityMonitor(this) }

    // Cloud-sync stack. Constructed lazily so a local-only install pays nothing until something first
    // requests a sync (e.g. the first enqueue after SyncTracker.init below).
    val cloudInstallState by lazy { CloudInstallState(this) }
    val syncEngine by lazy {
        SyncEngine(database, cloudInstallState, preferencesRepository, authRepository)
    }
    val syncGateway by lazy {
        SyncGateway(applicationScope, authRepository, connectivityMonitor, syncEngine)
    }
    val preferencesObserver by lazy {
        SyncedPreferencesObserver(preferencesRepository, cloudInstallState)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize AccessibilityHelper reactive state & system observers
        com.example.core.accessibility.AccessibilityHelper.init(this)

        // Connectivity monitoring (drives cloud sync retries)
        connectivityMonitor.start()

        // Cloud sync wiring: the outbox needs the database before any repository write can enqueue,
        // and the gateway + document-dirty observer must be live before the first seed-init write.
        // onPendingChanged is set last so a repository enqueue always finds a wired trigger.
        SyncTracker.init(database)
        syncGateway.start()
        preferencesObserver.start(applicationScope)
        SyncTracker.onPendingChanged = { syncGateway.requestSync() }

        // Restore any previously-signed-in cloud session (no-op when not configured / signed out)
        applicationScope.launch {
            try {
                authRepository.restoreOnStart()
            } catch (_: Exception) {
            }
        }

        // Initialize blocker manager with dependencies
        FocusBlockerManager.initialize(
            appContext = this,
            blockedAppRepository = blockedAppRepository,
            blockedAttemptRepository = blockedAttemptRepository,
            studyChannelRepository = studyChannelRepository
        )

        // Initialize App Limit Manager & Strict Mode Engine
        com.example.feature.applimits.engine.AppLimitManager.initialize(
            appContext = this,
            appLimitRepository = appLimitRepository,
            preferencesRepository = preferencesRepository
        )
        com.example.feature.applimits.engine.AppLimitStrictModeEngine.initialize(
            context = this,
            repository = appLimitRepository
        )

        // Initialize Website Blocker Engine
        com.example.feature.websiteblocker.engine.WebsiteBlockerEngine.initialize(
            appContext = this,
            websiteRepository = blockedWebsiteRepository,
            preferencesRepository = preferencesRepository
        )

        // Initialize Notification Blocker Engine
        com.example.feature.notificationblocker.engine.NotificationBlockerEngine.initialize(
            appContext = this,
            preferencesRepository = preferencesRepository,
            blockedAttemptRepository = blockedAttemptRepository
        )

        // Initialize Anti-Uninstall Engine (Device Admin protection)
        com.example.core.engine.AntiUninstallEngine.getInstance(this)

        // Initialize Block Protection (always-on tamper guard during active sessions)
        com.example.feature.blocker.protection.BlockProtectionManager.initialize()

        // Initialize session and protection notification channels
        com.example.feature.session.notification.SessionNotificationHelper.initialize(this)
        com.example.core.notification.FocusShieldBlockNotificationHelper.initialize(this)

        // Restore any active session from disk that was running prior to process restart
        try {
            val restored = com.example.feature.session.engine.FocusSessionManager.instance.restoreActiveSessionFromDisk(this)
            if (restored != null && (restored.isRunning || restored.isPaused)) {
                com.example.feature.session.service.FocusSessionForegroundService.start(this)
            }
        } catch (e: Exception) {
            android.util.Log.e("FocusShieldApp", "Error restoring active session on startup: ${e.message}")
        }

        // Prepopulate default initial subjects, study plans, and approved study channels if empty
        applicationScope.launch {
            try {
                subjectRepository.initializeDefaultSubjectsIfEmpty()
                studyPlanRepository.initializeDefaultPlansIfEmpty()
                studyChannelRepository.initializeDefaultChannelsIfEmpty()
                focusScheduleRepository.initializeDefaultSchedulesIfEmpty()
                keywordRepository.initializeDefaultKeywordsIfEmpty()

                // Ensure today's fresh plans exist and schedule all start time reminder notifications
                val todayPlans = studyPlanRepository.ensureTodayPlansExist()
                com.example.feature.session.notification.StudyPlanAlarmScheduler.schedulePlanReminders(
                    this@FocusShieldApp,
                    todayPlans
                )
                com.example.feature.session.notification.StudyPlanAlarmScheduler.scheduleMidnightDailyReset(
                    this@FocusShieldApp
                )
                // Pre-cache educational channel logos to internal storage disk for instant offline loading
                ChannelLogoStorageManager.precacheAllChannels(this@FocusShieldApp, database.studyChannelDao())
            } catch (e: Exception) {
                android.util.Log.e("FocusShieldApp", "Error during background startup initialization: ${e.message}")
            }
        }

        // Keep home screen widgets fresh: react to session state + today's study time
        applicationScope.launch {
            try {
                com.example.feature.session.engine.FocusSessionManager.instance.sessionState.collect {
                    refreshWidgets()
                }
            } catch (_: Exception) {
            }
        }
        applicationScope.launch {
            try {
                sessionRepository.getTodayTotalStudyTimeMillisFlow().collect {
                    refreshWidgets()
                }
            } catch (_: Exception) {
            }
        }
        // Periodic keep-alive refresh (every 30 minutes) for countdown widgets
        applicationScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30 * 60 * 1000L)
                refreshWidgets()
            }
        }
    }

    /**
     * Requests a throttled refresh of all home screen widgets. Safe to call from
     * any thread; runs on the application scope with a small delay to let
     * underlying data writes settle first.
     */
    fun refreshWidgets() {
        applicationScope.launch {
            kotlinx.coroutines.delay(500)
            try {
                com.example.feature.widgets.FocusShieldWidgetUpdater.updateAll(this@FocusShieldApp)
            } catch (_: Exception) {
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100 MB persistent disk cache
                    .build()
            }
            .respectCacheHeaders(false) // Ensures cached images are preserved even when offline
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: FocusShieldApp
            private set
    }
}
