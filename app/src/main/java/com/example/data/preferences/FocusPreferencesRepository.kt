package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_shield_preferences")

data class FocusPreferences(
    val defaultTimerMinutes: Int = 60,
    val pomodoroFocusMinutes: Int = 25,
    val pomodoroShortBreakMinutes: Int = 5,
    val pomodoroLongBreakMinutes: Int = 15,
    val pomodoroCycles: Int = 4,
    val defaultSubject: String = "Physics",
    val defaultTopic: String = "Electrostatics",
    val isAppBlockingDefault: Boolean = true,
    val isStrictModeDefault: Boolean = false,
    val isStudyChannelsDefault: Boolean = true,
    val isYouTubeShortsBlockingEnabled: Boolean = false,
    val isInstagramReelsBlockingEnabled: Boolean = false,
    val isFacebookReelsBlockingEnabled: Boolean = false,
    val isShortsReelsAlwaysBlocked: Boolean = false,
    val hasCompletedOnboarding: Boolean = false,
    val dailyGoalMinutes: Int = 240, // 4 hours daily study target
    val minimumStreakThresholdMinutes: Int = 20, // 20 mins minimum to count for streak
    val themeMode: String = "SYSTEM", // "SYSTEM", "LIGHT", "DARK" (Late-Night Eye Care)
    val isAutoAdultWebsiteBlockingEnabled: Boolean = true,
    val isManualWebsiteBlockingEnabled: Boolean = true,
    val isBlockUninstallEnabled: Boolean = false,
    val isBlockSplitScreenEnabled: Boolean = false,
    val isBlockFloatingWindowEnabled: Boolean = false,
    val isBlockNotificationsEnabled: Boolean = false,
    val notificationBlockMode: String = "SESSION_ONLY", // "SESSION_ONLY", "ALWAYS_SILENT", "SMART_HYBRID"
    val blockedNotificationPackages: Set<String> = emptySet(),
    val alwaysBlockedNotificationPackages: Set<String> = emptySet(),
    val blockedNotificationsCount: Int = 0,
    val claimedRewardIds: Set<String> = emptySet(),
    val userName: String = "Focus Scholar",
    val userPhotoUri: String? = null,
    val userAvatarPreset: String = "SHIELD",
    val userMotto: String = "Deep Work & Daily Mastery",
    val userAcademicGoal: String = "Exam Rank & Cognitive Stamina"
)

class FocusPreferencesRepository(private val context: Context) {

    private val syncPrefs = context.getSharedPreferences("focus_shield_sync_prefs", Context.MODE_PRIVATE)

    fun isCompletedOnboardingSync(): Boolean {
        return syncPrefs.getBoolean("onboarding_completed", false)
    }

    private object PreferencesKeys {
        val KEY_DEFAULT_TIMER_MINUTES = intPreferencesKey("default_timer_minutes")
        val KEY_POMODORO_FOCUS_MINUTES = intPreferencesKey("pomodoro_focus_minutes")
        val KEY_POMODORO_SHORT_BREAK_MINUTES = intPreferencesKey("pomodoro_short_break_minutes")
        val KEY_POMODORO_LONG_BREAK_MINUTES = intPreferencesKey("pomodoro_long_break_minutes")
        val KEY_POMODORO_CYCLES = intPreferencesKey("pomodoro_cycles")
        val KEY_DEFAULT_SUBJECT = stringPreferencesKey("default_subject")
        val KEY_DEFAULT_TOPIC = stringPreferencesKey("default_topic")
        val KEY_APP_BLOCKING_DEFAULT = booleanPreferencesKey("app_blocking_default")
        val KEY_STRICT_MODE_DEFAULT = booleanPreferencesKey("strict_mode_default")
        val KEY_STUDY_CHANNELS_DEFAULT = booleanPreferencesKey("study_channels_default")
        val KEY_YT_SHORTS_BLOCKING = booleanPreferencesKey("yt_shorts_blocking")
        val KEY_IG_REELS_BLOCKING = booleanPreferencesKey("ig_reels_blocking")
        val KEY_FB_REELS_BLOCKING = booleanPreferencesKey("fb_reels_blocking")
        val KEY_SHORTS_ALWAYS_BLOCKED = booleanPreferencesKey("shorts_always_blocked")
        val KEY_ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val KEY_DAILY_GOAL_MINUTES = intPreferencesKey("daily_goal_minutes")
        val KEY_MIN_STREAK_THRESHOLD_MINUTES = intPreferencesKey("min_streak_threshold_minutes")
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
        val KEY_AUTO_ADULT_WEBSITE_BLOCKING = booleanPreferencesKey("auto_adult_website_blocking")
        val KEY_MANUAL_WEBSITE_BLOCKING = booleanPreferencesKey("manual_website_blocking")
        val KEY_BLOCK_UNINSTALL = booleanPreferencesKey("block_uninstall")
        val KEY_BLOCK_SPLIT_SCREEN = booleanPreferencesKey("block_split_screen")
        val KEY_BLOCK_FLOATING_WINDOW = booleanPreferencesKey("block_floating_window")
        val KEY_BLOCK_NOTIFICATIONS = booleanPreferencesKey("block_notifications")
        val KEY_NOTIFICATION_BLOCK_MODE = stringPreferencesKey("notification_block_mode")
        val KEY_BLOCKED_NOTIFICATION_PACKAGES = androidx.datastore.preferences.core.stringSetPreferencesKey("blocked_notification_packages")
        val KEY_ALWAYS_BLOCKED_NOTIFICATION_PACKAGES = androidx.datastore.preferences.core.stringSetPreferencesKey("always_blocked_notification_packages")
        val KEY_BLOCKED_NOTIFICATIONS_COUNT = intPreferencesKey("blocked_notifications_count")
        val KEY_CLAIMED_REWARD_IDS = androidx.datastore.preferences.core.stringSetPreferencesKey("claimed_reward_ids")
        val KEY_USER_NAME = stringPreferencesKey("user_name")
        val KEY_USER_PHOTO_URI = stringPreferencesKey("user_photo_uri")
        val KEY_USER_AVATAR_PRESET = stringPreferencesKey("user_avatar_preset")
        val KEY_USER_MOTTO = stringPreferencesKey("user_motto")
        val KEY_USER_ACADEMIC_GOAL = stringPreferencesKey("user_academic_goal")
    }

    val preferencesFlow: Flow<FocusPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
        FocusPreferences(
            defaultTimerMinutes = preferences[PreferencesKeys.KEY_DEFAULT_TIMER_MINUTES] ?: 60,
            pomodoroFocusMinutes = preferences[PreferencesKeys.KEY_POMODORO_FOCUS_MINUTES] ?: 25,
            pomodoroShortBreakMinutes = preferences[PreferencesKeys.KEY_POMODORO_SHORT_BREAK_MINUTES] ?: 5,
            pomodoroLongBreakMinutes = preferences[PreferencesKeys.KEY_POMODORO_LONG_BREAK_MINUTES] ?: 15,
            pomodoroCycles = preferences[PreferencesKeys.KEY_POMODORO_CYCLES] ?: 4,
            defaultSubject = preferences[PreferencesKeys.KEY_DEFAULT_SUBJECT] ?: "Physics",
            defaultTopic = preferences[PreferencesKeys.KEY_DEFAULT_TOPIC] ?: "Electrostatics",
            isAppBlockingDefault = preferences[PreferencesKeys.KEY_APP_BLOCKING_DEFAULT] ?: true,
            isStrictModeDefault = preferences[PreferencesKeys.KEY_STRICT_MODE_DEFAULT] ?: false,
            isStudyChannelsDefault = preferences[PreferencesKeys.KEY_STUDY_CHANNELS_DEFAULT] ?: true,
            isYouTubeShortsBlockingEnabled = preferences[PreferencesKeys.KEY_YT_SHORTS_BLOCKING] ?: false,
            isInstagramReelsBlockingEnabled = preferences[PreferencesKeys.KEY_IG_REELS_BLOCKING] ?: false,
            isFacebookReelsBlockingEnabled = preferences[PreferencesKeys.KEY_FB_REELS_BLOCKING] ?: false,
            isShortsReelsAlwaysBlocked = preferences[PreferencesKeys.KEY_SHORTS_ALWAYS_BLOCKED] ?: false,
            hasCompletedOnboarding = preferences[PreferencesKeys.KEY_ONBOARDING_COMPLETED] ?: false,
            dailyGoalMinutes = preferences[PreferencesKeys.KEY_DAILY_GOAL_MINUTES] ?: 240,
            minimumStreakThresholdMinutes = preferences[PreferencesKeys.KEY_MIN_STREAK_THRESHOLD_MINUTES] ?: 20,
            themeMode = preferences[PreferencesKeys.KEY_THEME_MODE] ?: "SYSTEM",
            isAutoAdultWebsiteBlockingEnabled = preferences[PreferencesKeys.KEY_AUTO_ADULT_WEBSITE_BLOCKING] ?: true,
            isManualWebsiteBlockingEnabled = preferences[PreferencesKeys.KEY_MANUAL_WEBSITE_BLOCKING] ?: true,
            isBlockUninstallEnabled = preferences[PreferencesKeys.KEY_BLOCK_UNINSTALL] ?: false,
            isBlockSplitScreenEnabled = preferences[PreferencesKeys.KEY_BLOCK_SPLIT_SCREEN] ?: false,
            isBlockFloatingWindowEnabled = preferences[PreferencesKeys.KEY_BLOCK_FLOATING_WINDOW] ?: false,
            isBlockNotificationsEnabled = preferences[PreferencesKeys.KEY_BLOCK_NOTIFICATIONS] ?: false,
            notificationBlockMode = preferences[PreferencesKeys.KEY_NOTIFICATION_BLOCK_MODE] ?: "SESSION_ONLY",
            blockedNotificationPackages = preferences[PreferencesKeys.KEY_BLOCKED_NOTIFICATION_PACKAGES] ?: emptySet(),
            alwaysBlockedNotificationPackages = preferences[PreferencesKeys.KEY_ALWAYS_BLOCKED_NOTIFICATION_PACKAGES] ?: emptySet(),
            blockedNotificationsCount = preferences[PreferencesKeys.KEY_BLOCKED_NOTIFICATIONS_COUNT] ?: 0,
            claimedRewardIds = preferences[PreferencesKeys.KEY_CLAIMED_REWARD_IDS] ?: emptySet(),
            userName = preferences[PreferencesKeys.KEY_USER_NAME] ?: "Focus Scholar",
            userPhotoUri = preferences[PreferencesKeys.KEY_USER_PHOTO_URI],
            userAvatarPreset = preferences[PreferencesKeys.KEY_USER_AVATAR_PRESET] ?: "SHIELD",
            userMotto = preferences[PreferencesKeys.KEY_USER_MOTTO] ?: "Deep Work & Daily Mastery",
            userAcademicGoal = preferences[PreferencesKeys.KEY_USER_ACADEMIC_GOAL] ?: "Exam Rank & Cognitive Stamina"
        )
    }

    suspend fun updateThemeMode(themeMode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_THEME_MODE] = themeMode
        }
    }

    suspend fun updateTimerDefault(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_DEFAULT_TIMER_MINUTES] = minutes
        }
    }

    suspend fun updatePomodoroDefaults(focus: Int, shortBreak: Int, longBreak: Int, cycles: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_POMODORO_FOCUS_MINUTES] = focus
            preferences[PreferencesKeys.KEY_POMODORO_SHORT_BREAK_MINUTES] = shortBreak
            preferences[PreferencesKeys.KEY_POMODORO_LONG_BREAK_MINUTES] = longBreak
            preferences[PreferencesKeys.KEY_POMODORO_CYCLES] = cycles
        }
    }

    suspend fun updateDefaultSubjectAndTopic(subject: String, topic: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_DEFAULT_SUBJECT] = subject
            preferences[PreferencesKeys.KEY_DEFAULT_TOPIC] = topic
        }
    }

    suspend fun updateProtectionDefaults(appBlocking: Boolean, strictMode: Boolean, studyChannels: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_APP_BLOCKING_DEFAULT] = appBlocking
            preferences[PreferencesKeys.KEY_STRICT_MODE_DEFAULT] = strictMode
            preferences[PreferencesKeys.KEY_STUDY_CHANNELS_DEFAULT] = studyChannels
            if (strictMode) {
                preferences[PreferencesKeys.KEY_BLOCK_UNINSTALL] = true
            }
        }
    }

    suspend fun updateDailyGoalMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_DAILY_GOAL_MINUTES] = minutes
        }
    }

    suspend fun updateMinimumStreakThresholdMinutes(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_MIN_STREAK_THRESHOLD_MINUTES] = minutes
        }
    }

    suspend fun updateYouTubeShortsBlocking(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_YT_SHORTS_BLOCKING] = enabled
        }
    }

    suspend fun updateInstagramReelsBlocking(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_IG_REELS_BLOCKING] = enabled
        }
    }

    suspend fun updateFacebookReelsBlocking(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_FB_REELS_BLOCKING] = enabled
        }
    }

    suspend fun updateShortsReelsAlwaysBlocked(alwaysBlocked: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_SHORTS_ALWAYS_BLOCKED] = alwaysBlocked
        }
    }

    suspend fun setOnboardingCompleted(completed: Boolean = true) {
        syncPrefs.edit().putBoolean("onboarding_completed", completed).apply()
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_ONBOARDING_COMPLETED] = completed
        }
    }

    suspend fun updateAutoAdultWebsiteBlocking(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_AUTO_ADULT_WEBSITE_BLOCKING] = enabled
        }
    }

    suspend fun updateManualWebsiteBlocking(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_MANUAL_WEBSITE_BLOCKING] = enabled
        }
    }

    suspend fun updateBlockUninstall(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_BLOCK_UNINSTALL] = enabled
        }
    }

    suspend fun updateBlockSplitScreen(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_BLOCK_SPLIT_SCREEN] = enabled
        }
    }

    suspend fun updateBlockFloatingWindow(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_BLOCK_FLOATING_WINDOW] = enabled
        }
    }

    suspend fun updateBlockNotifications(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_BLOCK_NOTIFICATIONS] = enabled
        }
    }

    suspend fun updateNotificationBlockMode(mode: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_NOTIFICATION_BLOCK_MODE] = mode
        }
    }

    suspend fun updateBlockedNotificationPackages(packages: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_BLOCKED_NOTIFICATION_PACKAGES] = packages
        }
    }

    suspend fun updateAlwaysBlockedNotificationPackages(packages: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_ALWAYS_BLOCKED_NOTIFICATION_PACKAGES] = packages
        }
    }

    suspend fun toggleBlockedNotificationPackage(packageName: String, blocked: Boolean) {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.KEY_BLOCKED_NOTIFICATION_PACKAGES]?.toMutableSet()
                ?: mutableSetOf()
            if (blocked) {
                current.add(packageName)
            } else {
                current.remove(packageName)
            }
            preferences[PreferencesKeys.KEY_BLOCKED_NOTIFICATION_PACKAGES] = current
        }
    }

    suspend fun toggleAlwaysBlockedNotificationPackage(packageName: String, alwaysBlocked: Boolean) {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.KEY_ALWAYS_BLOCKED_NOTIFICATION_PACKAGES]?.toMutableSet()
                ?: mutableSetOf()
            if (alwaysBlocked) {
                current.add(packageName)
            } else {
                current.remove(packageName)
            }
            preferences[PreferencesKeys.KEY_ALWAYS_BLOCKED_NOTIFICATION_PACKAGES] = current
        }
    }

    suspend fun incrementBlockedNotificationsCount() {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.KEY_BLOCKED_NOTIFICATIONS_COUNT] ?: 0
            preferences[PreferencesKeys.KEY_BLOCKED_NOTIFICATIONS_COUNT] = current + 1
        }
    }

    suspend fun clearBlockedNotificationsCount() {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_BLOCKED_NOTIFICATIONS_COUNT] = 0
        }
    }

    suspend fun claimReward(rewardId: String) {
        context.dataStore.edit { preferences ->
            val current = preferences[PreferencesKeys.KEY_CLAIMED_REWARD_IDS]?.toMutableSet() ?: mutableSetOf()
            current.add(rewardId)
            preferences[PreferencesKeys.KEY_CLAIMED_REWARD_IDS] = current
        }
    }

    suspend fun updateUserProfile(
        userName: String,
        photoUri: String?,
        avatarPreset: String,
        motto: String,
        academicGoal: String,
        dailyGoalMinutes: Int
    ) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_USER_NAME] = userName
            if (photoUri != null) {
                preferences[PreferencesKeys.KEY_USER_PHOTO_URI] = photoUri
            } else {
                preferences.remove(PreferencesKeys.KEY_USER_PHOTO_URI)
            }
            preferences[PreferencesKeys.KEY_USER_AVATAR_PRESET] = avatarPreset
            preferences[PreferencesKeys.KEY_USER_MOTTO] = motto
            preferences[PreferencesKeys.KEY_USER_ACADEMIC_GOAL] = academicGoal
            preferences[PreferencesKeys.KEY_DAILY_GOAL_MINUTES] = dailyGoalMinutes
        }
    }

    suspend fun updateUserName(userName: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_USER_NAME] = userName
        }
    }

    suspend fun updateUserPhotoUri(photoUri: String?) {
        context.dataStore.edit { preferences ->
            if (photoUri != null) {
                preferences[PreferencesKeys.KEY_USER_PHOTO_URI] = photoUri
            } else {
                preferences.remove(PreferencesKeys.KEY_USER_PHOTO_URI)
            }
        }
    }

    suspend fun updateUserAvatarPreset(preset: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_USER_AVATAR_PRESET] = preset
        }
    }

    suspend fun updateUserMotto(motto: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_USER_MOTTO] = motto
        }
    }

    suspend fun updateUserAcademicGoal(goal: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_USER_ACADEMIC_GOAL] = goal
        }
    }
}
