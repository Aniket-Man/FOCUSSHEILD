package com.example.core.navigation

/**
 * FocusShield navigation routes.
 */
sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Planner : Screen("planner")
    object StatsDetail : Screen("stats_detail")
    object Blocks : Screen("blocks")
    object Profile : Screen("profile")

    object StartSession : Screen("start_session")
    object ModeSelection : Screen("mode_selection")
    object SessionSetup : Screen("session_setup")
    object ActiveSession : Screen("active_session")

    // Sub-screens
    object SessionsHistory : Screen("sessions_history")
    object SettingsScreen : Screen("settings_screen")
    object BlockedApps : Screen("blocked_apps")
    object AppLimits : Screen("app_limits")
    object StrictMode : Screen("strict_mode")
    object StudyChannels : Screen("study_channels")
    object WebsiteBlocker : Screen("website_blocker")
    object Onboarding : Screen("onboarding")
}
