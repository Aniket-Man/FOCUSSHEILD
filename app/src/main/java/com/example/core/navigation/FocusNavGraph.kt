package com.example.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.FocusShieldApp
import com.example.core.permission.FocusPermissionManager
import com.example.core.permission.ProtectionPermissionStatus
import com.example.core.ui.BottomTab
import com.example.feature.analytics.AnalyticsScreen
import com.example.feature.analytics.AnalyticsViewModel
import com.example.feature.applimits.ui.AppLimitsDashboardScreen
import com.example.feature.applimits.ui.AppLimitsViewModel
import com.example.feature.blockedapps.BlockedAppsScreen
import com.example.feature.blockedapps.BlockedAppsViewModel
import com.example.feature.blocks.BlocksScreen
import com.example.feature.channels.StudyChannelsScreen
import com.example.feature.channels.StudyChannelsViewModel
import com.example.feature.history.HistoryViewModel
import com.example.feature.history.SessionsHistoryScreen
import com.example.feature.home.HomeScreen
import com.example.feature.home.HomeViewModel
import com.example.feature.onboarding.OnboardingScreen
import com.example.feature.planner.PlannerScreen
import com.example.feature.profile.ProfileScreen
import com.example.feature.rewards.ui.RewardsScreen
import com.example.feature.session.ActiveSessionScreen
import com.example.feature.session.ModeSelectionScreen
import com.example.feature.session.SessionSetupScreen
import com.example.feature.session.SessionViewModel
import com.example.feature.session.StartSessionScreen
import com.example.feature.session.ui.FocusSessionSummaryScreen
import com.example.feature.session.notification.PlanLaunchPayload
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.SettingsViewModel
import com.example.feature.account.AccountScreen
import com.example.feature.account.AccountViewModel
import com.example.feature.update.domain.UpdateState
import com.example.feature.update.ui.UpdateAvailableDialog
import com.example.feature.update.ui.UpdateScreen
import com.example.feature.update.ui.UpdateViewModel

/**
 * Central FocusShield Navigation Graph.
 * Connects the primary tabs (Focus, Planner, Stats, Blocks), Profile hub, study modes, room persistence, and anti-distraction shield.
 */
import com.example.feature.splash.SplashScreen
import com.example.feature.websiteblocker.ui.WebsiteBlockerScreen
import com.example.feature.websiteblocker.ui.WebsiteBlockerViewModel

@Composable
fun FocusNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    incomingPlanPayload: PlanLaunchPayload? = null,
    onPlanPayloadHandled: () -> Unit = {},
    openUpdatesRequest: Boolean = false,
    onOpenUpdatesHandled: () -> Unit = {},
    homeViewModel: HomeViewModel = viewModel(),
    sessionViewModel: SessionViewModel = viewModel(),
    historyViewModel: HistoryViewModel? = null,
    analyticsViewModel: AnalyticsViewModel? = null,
    settingsViewModel: SettingsViewModel = viewModel(),
    blockedAppsViewModel: BlockedAppsViewModel? = null,
    appLimitsViewModel: AppLimitsViewModel? = null,
    websiteBlockerViewModel: WebsiteBlockerViewModel? = null,
    studyChannelsViewModel: StudyChannelsViewModel? = null,
    accountViewModel: AccountViewModel? = null,
    updateViewModel: UpdateViewModel? = null
) {
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    // Collected rather than read once from the StateFlow: restoring the snapshot in
    // SessionViewModel.init and the splash gate must agree even across a recomposition.
    val pendingSessionSummary by sessionViewModel.pendingSessionSummary.collectAsStateWithLifecycle()
    val resolvedUpdateVm: UpdateViewModel = updateViewModel ?: hiltViewModel()
    val updateState by resolvedUpdateVm.state.collectAsStateWithLifecycle()
    val showUpdateDot by resolvedUpdateVm.showDot.collectAsStateWithLifecycle()
    val completedUpdate by resolvedUpdateVm.completedUpdate.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current

    // App-wide installer launcher so "Install & Restart" works directly from the push modal or
    // the Home banner as well as the dedicated UpdateScreen.
    var pendingModalInstall by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<android.content.Intent?>(null)
    }
    val modalInstallLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        resolvedUpdateVm.cancelInstall()
    }
    val modalUnknownSourcesLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) {
        val intent = pendingModalInstall
        pendingModalInstall = null
        if (intent != null && resolvedUpdateVm.canInstallPackages()) {
            try {
                modalInstallLauncher.launch(intent)
            } catch (_: Exception) {
                resolvedUpdateVm.cancelInstall()
            }
        } else {
            resolvedUpdateVm.cancelInstall()
        }
    }

    val triggerDirectInstall: () -> Unit = {
        val intent = resolvedUpdateVm.createInstallIntent()
        if (intent != null) {
            resolvedUpdateVm.beginInstall()
            if (resolvedUpdateVm.canInstallPackages()) {
                try {
                    modalInstallLauncher.launch(intent)
                } catch (_: Exception) {
                    resolvedUpdateVm.cancelInstall()
                }
            } else {
                pendingModalInstall = intent
                try {
                    modalUnknownSourcesLauncher.launch(
                        android.content.Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            android.net.Uri.parse("package:${context.packageName}")
                        )
                    )
                } catch (_: Exception) {
                    pendingModalInstall = null
                    resolvedUpdateVm.cancelInstall()
                }
            }
        } else {
            navController.navigate(Screen.Update.route) { launchSingleTop = true }
        }
    }

    // Root-redirect gate (issue #18): every launch intent below must wait until the
    // splash→Home/Onboarding redirect has run. Two independent LaunchedEffects racing the
    // splash redirect used to interleave navigate() calls and corrupt the back stack
    // (deep-linked session buried under Home, or onboarding pushed over an active session).
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val isSplashRoute = currentRoute == null || currentRoute == Screen.Splash.route

    // Show the interactive Update Push Modal (Available → Inline Downloading → Ready to Install & Restart)
    // whenever not on the Splash or Update screen.
    if (!isSplashRoute && currentRoute != Screen.Update.route) {
        com.example.feature.update.ui.UpdatePushModal(
            state = updateState,
            installedVersion = resolvedUpdateVm.installedVersionName,
            onDownload = { resolvedUpdateVm.download() },
            onLater = { resolvedUpdateVm.onLater() },
            onHideModal = { resolvedUpdateVm.hideDownloadModal() },
            onCancelDownload = { resolvedUpdateVm.cancelDownload() },
            onInstallAndRestart = triggerDirectInstall,
            onOpenDetails = {
                navController.navigate(Screen.Update.route) { launchSingleTop = true }
            }
        )
    }

    // Post-restart "What's New" dialog once the newly installed update boots for the first time
    val justUpdatedInfo = completedUpdate
    if (!isSplashRoute && justUpdatedInfo != null) {
        com.example.feature.update.ui.WhatsNewDialog(
            info = justUpdatedInfo,
            onDismiss = { resolvedUpdateVm.dismissCompletedUpdate() }
        )
    }

    // Automatically navigate to the session page when launched from a Study Plan notification.
    // Queued while Splash is visible; consumed on the first recomposition after the root
    // redirect (isSplashRoute flips), so it always lands ON TOP of Home/Onboarding — never
    // underneath the redirect and never with Splash still in the back stack.
    LaunchedEffect(incomingPlanPayload, isSplashRoute) {
        val payload = incomingPlanPayload ?: return@LaunchedEffect
        if (isSplashRoute) return@LaunchedEffect
        sessionViewModel.configureForStudyPlan(
            subjectName = payload.subject,
            topicName = payload.topic,
            durationMinutes = payload.durationMinutes,
            planId = payload.planId,
            notes = payload.notes
        )
        if (payload.autoStart) {
            val started = sessionViewModel.startSession()
            if (started) {
                navController.navigate(Screen.ActiveSession.route) {
                    launchSingleTop = true
                }
            } else {
                navController.navigate(Screen.SessionSetup.route) {
                    launchSingleTop = true
                }
            }
        } else {
            navController.navigate(Screen.SessionSetup.route) {
                launchSingleTop = true
            }
        }
        onPlanPayloadHandled()
    }

    // Opened by tapping the "update available" notification (prompt.txt §5). Consumed once so a
    // configuration change or a later navigation cannot re-trigger it. Same splash gate as above.
    LaunchedEffect(openUpdatesRequest, isSplashRoute) {
        if (!openUpdatesRequest) return@LaunchedEffect
        if (isSplashRoute) return@LaunchedEffect
        navController.navigate(Screen.Update.route) { launchSingleTop = true }
        onOpenUpdatesHandled()
    }

    val permissionStatus = androidx.compose.runtime.remember { FocusPermissionManager.getPermissionStatus(context) }
    val corePermissionsGranted = permissionStatus.isAccessibilityEnabled &&
            permissionStatus.isUsageAccessGranted &&
            permissionStatus.isOverlayGranted &&
            permissionStatus.isBatteryOptimizationIgnored

    // Bootstrap snapshot covering the frames before preferencesFlow emits. Keyed to the
    // authoritative flag so it re-reads instead of pinning a stale one-shot value captured
    // at first composition (e.g. after process recreation, issue #18).
    val isCompletedOnboardingSync = androidx.compose.runtime.remember(settingsState.preferences.hasCompletedOnboarding) {
        FocusShieldApp.instance.preferencesRepository.isCompletedOnboardingSync()
    }
    val isCompletedOnboarding = isCompletedOnboardingSync || settingsState.preferences.hasCompletedOnboarding

    val nextDestinationAfterSplash = if (isCompletedOnboarding) Screen.Home.route else Screen.Onboarding.route

    // A completed session awaiting Save / Don't Save survives process death. Re-open its summary
    // after the splash instead of dropping the user on Home with an unrecorded session.
    val splashDestination = if (
        isCompletedOnboarding && pendingSessionSummary != null
    ) {
        Screen.SessionSummary.route
    } else {
        nextDestinationAfterSplash
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        enterTransition = {
            fadeIn(animationSpec = tween(200)) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(200)
            )
        },
        exitTransition = {
            fadeOut(animationSpec = tween(160))
        },
        popEnterTransition = {
            fadeIn(animationSpec = tween(200))
        },
        popExitTransition = {
            fadeOut(animationSpec = tween(160)) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(160)
            )
        },
        modifier = modifier
    ) {
        // 1. HOME / FOCUS TAB
        composable(Screen.Home.route) {
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToStartSession = { navController.navigate(Screen.StartSession.route) },
                onNavigateToActiveSession = { navController.navigate(Screen.ActiveSession.route) },
                onNavigateToModeSelection = { navController.navigate(Screen.ModeSelection.route) },
                onNavigateToPlannerTab = { navController.navigate(Screen.Planner.route) },
                onNavigateToStatsTab = { navController.navigate(Screen.StatsDetail.route) },
                onNavigateToBlocksTab = { navController.navigate(Screen.Blocks.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToStudyChannels = { navController.navigate(Screen.StudyChannels.route) },
                onNavigateToBlockedApps = { navController.navigate(Screen.BlockedApps.route) },
                onNavigateToAppLimits = { navController.navigate(Screen.AppLimits.route) },
                onNavigateToStrictMode = { navController.navigate(Screen.StrictMode.route) },
                onNavigateToUpdate = { navController.navigate(Screen.Update.route) },
                showUpdateDot = showUpdateDot,
                updateState = updateState,
                onUpdateBannerAction = {
                    when (updateState) {
                        is UpdateState.Downloaded -> triggerDirectInstall()
                        is UpdateState.Downloading, is UpdateState.UpdateAvailable -> {
                            resolvedUpdateVm.showModalPrompt()
                        }
                        else -> navController.navigate(Screen.Update.route)
                    }
                },
                onStartPlanSession = { planItem ->
                    sessionViewModel.configureForStudyPlan(
                        subjectName = planItem.subject,
                        topicName = planItem.topic,
                        durationMinutes = planItem.durationMinutes,
                        planId = planItem.id,
                        notes = planItem.notes
                    )
                    navController.navigate(Screen.SessionSetup.route)
                }
            )
        }

        // 2. PLANNER TAB
        composable(Screen.Planner.route) {
            PlannerScreen(
                viewModel = homeViewModel,
                onTabSelected = { tab ->
                    handleBottomTabNavigation(tab, navController)
                },
                onStartPlanSession = { planItem ->
                    sessionViewModel.configureForStudyPlan(
                        subjectName = planItem.subject,
                        topicName = planItem.topic,
                        durationMinutes = planItem.durationMinutes,
                        planId = planItem.id,
                        notes = planItem.notes
                    )
                    navController.navigate(Screen.SessionSetup.route)
                },
                onOpenBlockedApps = { navController.navigate(Screen.BlockedApps.route) }
            )
        }

        // 3. STATS TAB
        composable(Screen.StatsDetail.route) {
            val resolvedAnalyticsVm = analyticsViewModel ?: viewModel()
            AnalyticsScreen(
                viewModel = resolvedAnalyticsVm,
                onNavigateToStartSession = { navController.navigate(Screen.StartSession.route) },
                onTabSelected = { tab ->
                    handleBottomTabNavigation(tab, navController)
                }
            )
        }

        // 4. BLOCKS TAB
        composable(Screen.Blocks.route) {
            val resolvedAppLimitsVm = appLimitsViewModel ?: viewModel()
            BlocksScreen(
                appLimitsViewModel = resolvedAppLimitsVm,
                settingsViewModel = settingsViewModel,
                onTabSelected = { tab ->
                    handleBottomTabNavigation(tab, navController)
                },
                onNavigateToWebsiteBlocker = { navController.navigate(Screen.WebsiteBlocker.route) }
            )
        }

        // 5. PROFILE & SETTINGS HUB
        composable(Screen.Profile.route) {
            val resolvedAccountVm = accountViewModel ?: hiltViewModel()
            val resolvedUpdateVm = updateViewModel ?: hiltViewModel()
            ProfileScreen(
                settingsViewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHistory = { navController.navigate(Screen.SessionsHistory.route) },
                onNavigateToStudyChannels = { navController.navigate(Screen.StudyChannels.route) },
                onNavigateToStrictMode = { navController.navigate(Screen.StrictMode.route) },
                onNavigateToAccount = { navController.navigate(Screen.Account.route) },
                onNavigateToRewards = { navController.navigate(Screen.Rewards.route) },
                accountViewModel = resolvedAccountVm,
                onNavigateToOnboarding = { navController.navigate(Screen.Onboarding.route) },
                updateViewModel = resolvedUpdateVm,
                onNavigateToUpdates = { navController.navigate(Screen.Update.route) }
            )
        }

        // SUB-SCREEN: SESSIONS HISTORY
        composable(Screen.SessionsHistory.route) {
            val resolvedHistoryVm = historyViewModel ?: viewModel()
            SessionsHistoryScreen(
                viewModel = resolvedHistoryVm,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToStartSession = { navController.navigate(Screen.StartSession.route) }
            )
        }

        // SUB-SCREEN: SETTINGS SCREEN
        composable(Screen.SettingsScreen.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBlockedApps = { navController.navigate(Screen.BlockedApps.route) },
                onNavigateToAppLimits = { navController.navigate(Screen.AppLimits.route) },
                onNavigateToStrictMode = { navController.navigate(Screen.StrictMode.route) },
                onNavigateToStudyChannels = { navController.navigate(Screen.StudyChannels.route) },
                onNavigateToWebsiteBlocker = { navController.navigate(Screen.WebsiteBlocker.route) },
                onNavigateToAccount = { navController.navigate(Screen.Account.route) }
            )
        }

        // SUB-SCREEN: START SESSION
        composable(Screen.StartSession.route) {
            StartSessionScreen(
                viewModel = sessionViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToModeSelection = { navController.navigate(Screen.ModeSelection.route) },
                onNavigateToSetup = { navController.navigate(Screen.SessionSetup.route) }
            )
        }

        // SUB-SCREEN: MODE SELECTION
        composable(Screen.ModeSelection.route) {
            ModeSelectionScreen(
                viewModel = sessionViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToSetup = { navController.navigate(Screen.SessionSetup.route) }
            )
        }

        // SUB-SCREEN: SESSION SETUP
        composable(Screen.SessionSetup.route) {
            SessionSetupScreen(
                viewModel = sessionViewModel,
                onNavigateBack = { navController.popBackStack() },
                onStartSession = {
                    navController.navigate(Screen.ActiveSession.route) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        // SUB-SCREEN: ACTIVE SESSION
        composable(Screen.ActiveSession.route) {
            ActiveSessionScreen(
                viewModel = sessionViewModel,
                onNavigateBack = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
                onNavigateToSummary = {
                    navController.navigate(Screen.SessionSummary.route) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                        launchSingleTop = true
                    }
                }
            )
        }

        // SUB-SCREEN: SESSION SUMMARY (POST-SESSION METRICS & SAVE / DON'T SAVE DECISION)
        composable(Screen.SessionSummary.route) {
            val pendingSummary by sessionViewModel.pendingSessionSummary.collectAsStateWithLifecycle()
            // Retain the last valid summary during exit animations so the screen never blanks out
            val cachedSummary = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.example.feature.session.domain.SessionSummaryData?>(null) }
            if (pendingSummary != null) {
                cachedSummary.value = pendingSummary
            }
            val displaySummary = pendingSummary ?: cachedSummary.value

            val navigateBackHome: () -> Unit = {
                val popped = navController.popBackStack(Screen.Home.route, inclusive = false)
                if (!popped) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            }

            if (displaySummary != null) {
                FocusSessionSummaryScreen(
                    summaryData = displaySummary,
                    onSaveSession = {
                        sessionViewModel.savePendingSession {
                            val isSignedIn = FocusShieldApp.instance.authRepository.currentUserEmail() != null
                            // Always remove the answered summary first. Otherwise a signed-out user
                            // sent to Account could press Back and land on a stale summary that is
                            // already recorded (or, after a restore, leave Home off the back stack).
                            navigateBackHome()
                            if (!isSignedIn) {
                                navController.navigate(Screen.Account.route) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    },
                    onDiscardSession = {
                        sessionViewModel.discardPendingSession {
                            navigateBackHome()
                        }
                    }
                )
            } else {
                // If summary is null (e.g. back navigation or cleared), return cleanly to Home
                LaunchedEffect(Unit) {
                    navigateBackHome()
                }
            }
        }

        // SUB-SCREEN: BLOCKED APPS
        composable(Screen.BlockedApps.route) {
            val resolvedBlockedAppsVm = blockedAppsViewModel ?: viewModel()
            BlockedAppsScreen(
                viewModel = resolvedBlockedAppsVm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // SUB-SCREEN: STRICT MODE
        composable(Screen.StrictMode.route) {
            com.example.feature.strictmode.StrictModeScreen(
                sessionViewModel = sessionViewModel,
                settingsViewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // SUB-SCREEN: APPROVED STUDY CHANNELS
        composable(Screen.StudyChannels.route) {
            val resolvedStudyChannelsVm = studyChannelsViewModel ?: hiltViewModel()
            StudyChannelsScreen(
                viewModel = resolvedStudyChannelsVm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // SUB-SCREEN: WEBSITE & BROWSER BLOCKER
        composable(Screen.WebsiteBlocker.route) {
            val resolvedWebsiteBlockerVm = websiteBlockerViewModel ?: viewModel()
            WebsiteBlockerScreen(
                viewModel = resolvedWebsiteBlockerVm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // SUB-SCREEN: APP LIMITS DASHBOARD
        composable(Screen.AppLimits.route) {
            val resolvedAppLimitsVm = appLimitsViewModel ?: viewModel()
            AppLimitsDashboardScreen(
                viewModel = resolvedAppLimitsVm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // SUB-SCREEN: ACCOUNT & CLOUD BACKUP (optional; local-first until the user signs in)
        composable(Screen.Account.route) {
            val resolvedAccountVm = accountViewModel ?: hiltViewModel()
            AccountScreen(
                accountViewModel = resolvedAccountVm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // SUB-SCREEN: APP UPDATE (GitHub Releases APK updater)
        composable(Screen.Update.route) {
            val resolvedUpdateVm = updateViewModel ?: hiltViewModel()
            UpdateScreen(
                viewModel = resolvedUpdateVm,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // SUB-SCREEN: REWARDS & MILESTONES (All badges, tiers, and claims)
        composable(Screen.Rewards.route) {
            RewardsScreen(
                settingsViewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // SPLASH SCREEN
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashFinished = {
                    navController.navigate(splashDestination) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // ONBOARDING & INTRODUCTION FLOW
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onCompleteOnboarding = {
                    settingsViewModel.setOnboardingCompleted(true)
                    val destination = if (pendingSessionSummary != null) {
                        Screen.SessionSummary.route
                    } else {
                        Screen.Home.route
                    }
                    navController.navigate(destination) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}

private fun handleBottomTabNavigation(tab: BottomTab, navController: NavHostController) {
    when (tab) {
        BottomTab.FOCUS -> {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Home.route) { inclusive = false }
                launchSingleTop = true
            }
        }
        BottomTab.PLANNER -> {
            navController.navigate(Screen.Planner.route) {
                popUpTo(Screen.Home.route) { inclusive = false }
                launchSingleTop = true
            }
        }
        BottomTab.STATS -> {
            navController.navigate(Screen.StatsDetail.route) {
                popUpTo(Screen.Home.route) { inclusive = false }
                launchSingleTop = true
            }
        }
        BottomTab.BLOCKS -> {
            navController.navigate(Screen.Blocks.route) {
                popUpTo(Screen.Home.route) { inclusive = false }
                launchSingleTop = true
            }
        }
    }
}
