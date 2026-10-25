package com.example

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.cloud.auth.AuthRepository
import com.example.cloud.auth.AuthState
import com.example.cloud.net.ConnectivityMonitor
import com.example.cloud.sync.AutoSaveScheduler
import com.example.cloud.sync.CloudInstallState
import com.example.cloud.sync.SyncEngine
import com.example.cloud.sync.SyncGateway
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
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
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltAndroidApp
class FocusShieldApp : Application(), ImageLoaderFactory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database by lazy { FocusShieldDatabase.getInstance(this) }
    val sessionRepository by lazy { SessionRepository(database.sessionDao(), database.studyActivityDao()) }
    val subjectRepository by lazy { SubjectRepository(database.subjectDao(), database.topicDao()) }
    val studyPlanRepository by lazy { StudyPlanRepository(database.studyPlanDao()) }
    val breakRepository by lazy { BreakRepository(database.breakRecordDao()) }
    val blockedAppRepository by lazy { BlockedAppRepository(database.blockedAppDao()) }
    val blockedAttemptRepository by lazy {
        BlockedAttemptRepository(database.blockedAttemptDao(), cloudInstallState)
    }
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
            context = this
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

    // Optional Firebase cloud account layer. AuthRepository is cheap to construct (it does no
    // network I/O until restoreOnStart()); connectivityMonitor powers sync retry gating.
    val authRepository by lazy { AuthRepository(this) }
    val connectivityMonitor by lazy { ConnectivityMonitor(this) }

    // Release-feed update checker. Constructed from the build's `UPDATE_FEED_URL` /
    // `UPDATE_REPO_SLUG` (see .env.example): a feed URL is the supported way to serve releases while
    // the GitHub repository is private. No GitHub credential is ever shipped, so a private GitHub
    // repository reached over the API answers 404 — which the updater maps to "nothing published"
    // (UpToDate), never to a failure. See UpdateRepository / docs/UPDATE_SYSTEM.md §1.
    val updatePreferences by lazy {
        com.example.feature.update.data.UpdatePreferences(this)
    }
    val updateRepository by lazy {
        com.example.feature.update.data.GitHubReleaseRepository.fromConfig()
    }
    val updateDownloadManager by lazy {
        com.example.feature.update.data.UpdateDownloadManager(
            this,
            allowedHosts = updateRepository.apkHostAllowList()
        )
    }
    val updateManager by lazy {
        com.example.feature.update.engine.UpdateManager(
            context = this,
            checker = com.example.feature.update.data.UpdateChecker(
                repository = updateRepository,
                preferences = updatePreferences,
                installedVersionName = BuildConfig.VERSION_NAME,
                installedVersionCode = BuildConfig.VERSION_CODE,
                isOnline = { connectivityMonitor.isOnline.value }
            ),
            preferences = updatePreferences,
            downloadManager = updateDownloadManager,
            scope = applicationScope,
            installedVersionName = BuildConfig.VERSION_NAME,
            installedVersionCode = BuildConfig.VERSION_CODE
        )
    }

    // Cloud-sync stack. Constructed lazily so a local-only install pays nothing until something first
    // requests a sync (e.g. the first enqueue after SyncTracker.init below).
    val cloudInstallState by lazy { CloudInstallState(this) }
    val syncEngine by lazy {
        SyncEngine(database, cloudInstallState, preferencesRepository, authRepository)
    }
    val syncGateway by lazy {
        SyncGateway(
            applicationScope,
            authRepository,
            connectivityMonitor,
            syncEngine,
            cloudInstallState,
            preferencesRepository
        )
    }
    val preferencesObserver by lazy {
        SyncedPreferencesObserver(preferencesRepository, cloudInstallState)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Install process-wide crash logger so any unexpected fatal exception is preserved for diagnostics
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("FocusShieldApp", "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)
            try {
                val crashFile = java.io.File(filesDir, "crash_log.txt")
                crashFile.writeText(android.util.Log.getStackTraceString(throwable))
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // The cloud layer is optional: a fresh clone (and CI) has no google-services.json,
        // so the FirebaseInitProvider never runs and wiring sync below would throw from
        // FirebaseApp.getInstance(). Register a placeholder app in that case.
        runCatching {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(
                    this,
                    FirebaseOptions.Builder()
                        .setApplicationId("com.example.app")
                        .setProjectId("focusshield-offline")
                        .setApiKey("offline-placeholder-key")
                        .build()
                )
            }
        }.onFailure {
            android.util.Log.w("FocusShieldApp", "FirebaseApp initialization skipped: ${it.message}")
        }

        // Initialize AccessibilityHelper reactive state & system observers
        runCatching {
            com.example.core.accessibility.AccessibilityHelper.init(this)
        }.onFailure {
            android.util.Log.w("FocusShieldApp", "AccessibilityHelper init error: ${it.message}")
        }

        // Connectivity monitoring (drives cloud sync retries)
        runCatching {
            connectivityMonitor.start()
        }.onFailure {
            android.util.Log.w("FocusShieldApp", "ConnectivityMonitor start error: ${it.message}")
        }

        // Cloud sync wiring: safely initialize outbox, observers, and periodic API SyncWorker
        runCatching {
            SyncTracker.init(database, applicationScope)
            syncGateway.start()
            preferencesObserver.start(applicationScope)
            SyncTracker.onPendingChanged = { syncGateway.requestSync() }
            com.example.cloud.sync.SyncWorker.schedulePeriodicSync(this)
        }.onFailure {
            android.util.Log.w("FocusShieldApp", "Cloud sync initialization error: ${it.message}")
        }

        applicationScope.launch {
            try {
                syncGateway.lastSyncAtMillis.collect { lastAt ->
                    if (lastAt > 0L) {
                        com.example.cloud.sync.SyncWorker.recordSyncSuccess(this@FocusShieldApp, lastAt)
                    }
                }
            } catch (_: Throwable) {}
        }


        // Automatically update Scholar Profile picture & display name from Gmail / Google account upon sign-in
        applicationScope.launch {
            try {
                authRepository.authState.collect { authState ->
                    if (authState is AuthState.Authenticated) {
                        val photoUrl = authState.photoUrl
                        val displayName = authState.displayName
                        if (!photoUrl.isNullOrBlank()) {
                            preferencesRepository.updateUserPhotoUri(photoUrl)
                        }
                        if (displayName.isNotBlank()) {
                            val currentName = preferencesRepository.preferencesFlow.first().userName
                            if (currentName.isBlank() || currentName == "Focus Scholar") {
                                preferencesRepository.updateUserName(displayName)
                            }
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        // Periodic cloud auto-save scheduler
        applicationScope.launch {
            try {
                combine(
                    preferencesRepository.preferencesFlow,
                    authRepository.authState
                ) { prefs, authState ->
                    prefs.autoSaveIntervalSeconds to
                        (prefs.isAutoSaveToCloudEnabled && authState is AuthState.Authenticated)
                }
                    .distinctUntilChanged()
                    .collect { (seconds, shouldRun) ->
                        if (shouldRun) {
                            AutoSaveScheduler.schedule(this@FocusShieldApp, seconds)
                        } else {
                            AutoSaveScheduler.cancel(this@FocusShieldApp)
                        }
                    }
            } catch (_: Throwable) {}
        }

        // Lifecycle observer for sync flushing
        runCatching {
            ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onStop(owner: LifecycleOwner) {
                        applicationScope.launch { syncGateway.flushPending() }
                    }
                }
            )
        }.onFailure {
            android.util.Log.w("FocusShieldApp", "ProcessLifecycleOwner observer registration error: ${it.message}")
        }

        // Restore any previously-signed-in cloud session first; defer heavy Firestore quote seeding
        // until after initial UI frames have settled.
        applicationScope.launch {
            try {
                authRepository.restoreOnStart()
            } catch (_: Throwable) {}
            kotlinx.coroutines.delay(2200L)
            try {
                com.example.data.repository.SessionQuoteRepository.ensurePreloadedInFirestore()
            } catch (_: Throwable) {}
        }

        // Initialize blocker manager with dependencies
        runCatching {
            FocusBlockerManager.initialize(
                appContext = this,
                blockedAppRepository = blockedAppRepository,
                blockedAttemptRepository = blockedAttemptRepository,
                studyChannelRepository = studyChannelRepository
            )
        }.onFailure {
            android.util.Log.e("FocusShieldApp", "FocusBlockerManager init error: ${it.message}")
        }

        // Initialize App Limit Manager & Strict Mode Engine
        runCatching {
            com.example.feature.applimits.engine.AppLimitManager.initialize(
                appContext = this,
                appLimitRepository = appLimitRepository,
                preferencesRepository = preferencesRepository,
                blockedAttemptRepository = blockedAttemptRepository
            )
        }.onFailure {
            android.util.Log.e("FocusShieldApp", "AppLimitManager init error: ${it.message}")
        }

        applicationScope.launch {
            runCatching {
                com.example.feature.applimits.engine.AppLimitManager.instance.restoreSessionFromDisk()
            }.onFailure { e ->
                android.util.Log.e("FocusShieldApp", "Error restoring app limit session on startup: ${e.message}")
            }
        }

        runCatching {
            com.example.feature.applimits.engine.AppLimitStrictModeEngine.initialize(
                context = this,
                repository = appLimitRepository
            )
        }.onFailure {
            android.util.Log.e("FocusShieldApp", "AppLimitStrictModeEngine init error: ${it.message}")
        }

        // Initialize Website Blocker Engine
        runCatching {
            com.example.feature.websiteblocker.engine.WebsiteBlockerEngine.initialize(
                appContext = this,
                websiteRepository = blockedWebsiteRepository,
                preferencesRepository = preferencesRepository
            )
        }.onFailure {
            android.util.Log.e("FocusShieldApp", "WebsiteBlockerEngine init error: ${it.message}")
        }

        // Initialize Notification Blocker Engine
        runCatching {
            com.example.feature.notificationblocker.engine.NotificationBlockerEngine.initialize(
                appContext = this,
                preferencesRepository = preferencesRepository,
                blockedAttemptRepository = blockedAttemptRepository
            )
        }.onFailure {
            android.util.Log.e("FocusShieldApp", "NotificationBlockerEngine init error: ${it.message}")
        }

        // Initialize YouTube Study Mode dwell capture
        runCatching {
            com.example.feature.youtube.engine.YouTubeStudyDwellTracker.initialize(
                analyticsRepository = analyticsRepository,
                blockedAttemptRepository = blockedAttemptRepository
            )
        }.onFailure {
            android.util.Log.e("FocusShieldApp", "YouTubeStudyDwellTracker init error: ${it.message}")
        }

        // Initialize Anti-Uninstall Engine (Device Admin protection)
        runCatching {
            com.example.core.engine.AntiUninstallEngine.getInstance(this)
        }.onFailure {
            android.util.Log.e("FocusShieldApp", "AntiUninstallEngine init error: ${it.message}")
        }

        // Initialize Block Protection
        runCatching {
            com.example.feature.blocker.protection.BlockProtectionManager.initialize()
        }.onFailure {
            android.util.Log.e("FocusShieldApp", "BlockProtectionManager init error: ${it.message}")
        }

        // Initialize session, protection, and update notification channels
        runCatching { com.example.feature.session.notification.SessionNotificationHelper.initialize(this) }
        runCatching { com.example.core.notification.FocusShieldBlockNotificationHelper.initialize(this) }
        runCatching { com.example.feature.update.notification.UpdateNotificationHelper.initialize(this) }

        // Restore cached update state quickly, then defer network update check & WorkManager scheduling
        // until after Splash-to-Home transition completes.
        applicationScope.launch {
            try {
                updateManager.restore().join()
                kotlinx.coroutines.delay(2500L)
                updateManager.checkInBackground(forceForeground = true)
                runCatching {
                    com.example.feature.update.work.UpdateCheckScheduler.schedule(this@FocusShieldApp)
                }
            } catch (e: Throwable) {
                android.util.Log.w("FocusShieldApp", "Update check startup error: ${e.message}")
            }
        }

        // Re-check automatically when connectivity returns
        applicationScope.launch {
            try {
                connectivityMonitor.isOnline.collect { online ->
                    if (online) {
                        updateManager.checkInBackground(forceForeground = false)
                    }
                }
            } catch (_: Throwable) {}
        }

        // Restore any active session from disk that was running prior to process restart
        try {
            val restored = com.example.feature.session.engine.FocusSessionManager.instance.restoreActiveSessionFromDisk(this)
            if (restored != null && (restored.isRunning || restored.isPaused)) {
                com.example.feature.session.service.FocusSessionForegroundService.start(this)
            }
        } catch (e: Throwable) {
            android.util.Log.e("FocusShieldApp", "Error restoring active session on startup: ${e.message}")
        }

        // Prepopulate default initial subjects, study plans, and approved study channels if empty,
        // and stagger network logo pre-caching so it never competes with initial Room queries.
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

                // Wait for the Home screen to finish rendering before downloading/precaching channel logos
                kotlinx.coroutines.delay(3000L)
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
