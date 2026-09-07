package com.example.core.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import com.example.feature.session.ActiveSessionScreen
import com.example.feature.session.ModeSelectionScreen
import com.example.feature.session.SessionSetupScreen
import com.example.feature.session.SessionViewModel
import com.example.feature.session.StartSessionScreen
import com.example.feature.session.notification.PlanLaunchPayload
import com.example.feature.settings.SettingsScreen
import com.example.feature.settings.SettingsViewModel

/**
 * Central FocusShield Navigation Graph.
 * Connects the primary tabs (Focus, Planner, Stats, Blocks), Profile hub, study modes, room persistence, and anti-distraction shield.
 */
import com.example.feature.websiteblocker.ui.WebsiteBlockerScreen
import com.example.feature.websiteblocker.ui.WebsiteBlockerViewModel

@Composable
fun FocusNavGraph(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    incomingPlanPayload: PlanLaunchPayload? = null,
    onPlanPayloadHandled: () -> Unit = {},
    homeViewModel: HomeViewModel = viewModel(),
    sessionViewModel: SessionViewModel = viewModel(),
    historyViewModel: HistoryViewModel = viewModel(),
    analyticsViewModel: AnalyticsViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel(),
    blockedAppsViewModel: BlockedAppsViewModel = viewModel(),
    appLimitsViewModel: AppLimitsViewModel = viewModel(),
    websiteBlockerViewModel: WebsiteBlockerViewModel = viewModel(),
    studyChannelsViewModel: StudyChannelsViewModel = viewModel {
        StudyChannelsViewModel(FocusShieldApp.instance.studyChannelRepository)
    }
) {
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    // Automatically navigate to the session page when launched from a Study Plan notification
    LaunchedEffect(incomingPlanPayload) {
        val payload = incomingPlanPayload ?: return@LaunchedEffect
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

    val context = androidx.compose.ui.platform.LocalContext.current
    val permissionStatus = androidx.compose.runtime.remember { FocusPermissionManager.getPermissionStatus(context) }
    val corePermissionsGranted = permissionStatus.isAccessibilityEnabled &&
            permissionStatus.isUsageAccessGranted &&
            permissionStatus.isOverlayGranted &&
            permissionStatus.isBatteryOptimizationIgnored

    val isCompletedOnboardingSync = androidx.compose.runtime.remember { FocusShieldApp.instance.preferencesRepository.isCompletedOnboardingSync() }
    val isCompletedOnboarding = isCompletedOnboardingSync || settingsState.preferences.hasCompletedOnboarding

    val startDestination = if (isCompletedOnboarding) Screen.Home.route else Screen.Onboarding.route

    NavHost(
        navController = navController,
        startDestination = startDestination,
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
            AnalyticsScreen(
                viewModel = analyticsViewModel,
                onNavigateToStartSession = { navController.navigate(Screen.StartSession.route) },
                onTabSelected = { tab ->
                    handleBottomTabNavigation(tab, navController)
                }
            )
        }

        // 4. BLOCKS TAB
        composable(Screen.Blocks.route) {
            BlocksScreen(
                appLimitsViewModel = appLimitsViewModel,
                settingsViewModel = settingsViewModel,
                onTabSelected = { tab ->
                    handleBottomTabNavigation(tab, navController)
                },
                onNavigateToWebsiteBlocker = { navController.navigate(Screen.WebsiteBlocker.route) }
            )
        }

        // 5. PROFILE & SETTINGS HUB
        composable(Screen.Profile.route) {
            ProfileScreen(
                settingsViewModel = settingsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHistory = { navController.navigate(Screen.SessionsHistory.route) },
                onNavigateToStudyChannels = { navController.navigate(Screen.StudyChannels.route) },
                onNavigateToStrictMode = { navController.navigate(Screen.StrictMode.route) },
                onNavigateToOnboarding = { navController.navigate(Screen.Onboarding.route) }
            )
        }

        // SUB-SCREEN: SESSIONS HISTORY
        composable(Screen.SessionsHistory.route) {
            SessionsHistoryScreen(
                viewModel = historyViewModel,
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
                onNavigateToWebsiteBlocker = { navController.navigate(Screen.WebsiteBlocker.route) }
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
                }
            )
        }

        // SUB-SCREEN: BLOCKED APPS
        composable(Screen.BlockedApps.route) {
            BlockedAppsScreen(
                viewModel = blockedAppsViewModel,
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
            StudyChannelsScreen(
                viewModel = studyChannelsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // SUB-SCREEN: WEBSITE & BROWSER BLOCKER
        composable(Screen.WebsiteBlocker.route) {
            WebsiteBlockerScreen(
                viewModel = websiteBlockerViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // SUB-SCREEN: APP LIMITS DASHBOARD
        composable(Screen.AppLimits.route) {
            AppLimitsDashboardScreen(
                viewModel = appLimitsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        // ONBOARDING & INTRODUCTION FLOW
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onCompleteOnboarding = {
                    settingsViewModel.setOnboardingCompleted(true)
                    navController.navigate(Screen.Home.route) {
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
