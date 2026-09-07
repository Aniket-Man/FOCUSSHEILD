package com.zenlock.focusguard.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focusguard_prefs")

/**
 * Manages user preferences using Jetpack DataStore.
 * Stores settings like default focus duration, strict mode, and YouTube filtering options.
 */
class UserPreferences(private val context: Context) {

    // Keys
    private object Keys {
        val FOCUS_DURATION_MINUTES = intPreferencesKey("focus_duration_minutes")
        val BREAK_DURATION_MINUTES = intPreferencesKey("break_duration_minutes")
        val YOUTUBE_FILTERING_ENABLED = booleanPreferencesKey("youtube_filtering_enabled")
        val SHORTS_BLOCKING_ENABLED = booleanPreferencesKey("shorts_blocking_enabled")
        val DEFAULT_BLOCK_UNKNOWN = booleanPreferencesKey("default_block_unknown")
        val STRICT_MODE = booleanPreferencesKey("strict_mode")
        val IS_FOCUS_ACTIVE = booleanPreferencesKey("is_focus_active")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val MOTIVATIONAL_QUOTES_ENABLED = booleanPreferencesKey("motivational_quotes_enabled")
        
        // Gamification
        val USER_XP = intPreferencesKey("user_xp")
        val USER_STREAK = intPreferencesKey("user_streak")
        
        // Features
        val DND_SYNC_ENABLED = booleanPreferencesKey("dnd_sync_enabled")
    }

    // Focus Duration (default 25 minutes - Pomodoro)
    val focusDurationMinutes: Flow<Int> = context.dataStore.data.map {
        it[Keys.FOCUS_DURATION_MINUTES] ?: 25
    }

    suspend fun setFocusDurationMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.FOCUS_DURATION_MINUTES] = minutes }
    }

    // Break Duration (default 5 minutes)
    val breakDurationMinutes: Flow<Int> = context.dataStore.data.map {
        it[Keys.BREAK_DURATION_MINUTES] ?: 5
    }

    suspend fun setBreakDurationMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.BREAK_DURATION_MINUTES] = minutes }
    }

    // YouTube Filtering
    val youtubeFilteringEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.YOUTUBE_FILTERING_ENABLED] ?: true
    }

    suspend fun setYoutubeFilteringEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.YOUTUBE_FILTERING_ENABLED] = enabled }
    }

    // Shorts Blocking
    val shortsBlockingEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.SHORTS_BLOCKING_ENABLED] ?: true
    }

    suspend fun setShortsBlockingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHORTS_BLOCKING_ENABLED] = enabled }
    }

    // Default block unknown videos (those not matching any keyword)
    val defaultBlockUnknown: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.DEFAULT_BLOCK_UNKNOWN] ?: true
    }

    suspend fun setDefaultBlockUnknown(block: Boolean) {
        context.dataStore.edit { it[Keys.DEFAULT_BLOCK_UNKNOWN] = block }
    }

    // Strict mode (prevents stopping session early)
    val strictMode: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.STRICT_MODE] ?: false
    }

    suspend fun setStrictMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.STRICT_MODE] = enabled }
    }

    // Focus active state
    val isFocusActive: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.IS_FOCUS_ACTIVE] ?: false
    }

    suspend fun setFocusActive(active: Boolean) {
        context.dataStore.edit { it[Keys.IS_FOCUS_ACTIVE] = active }
    }

    // Onboarding
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.ONBOARDING_COMPLETE] ?: false
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_COMPLETE] = complete }
    }

    // Motivational Quotes
    val motivationalQuotesEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.MOTIVATIONAL_QUOTES_ENABLED] ?: true
    }

    suspend fun setMotivationalQuotesEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MOTIVATIONAL_QUOTES_ENABLED] = enabled }
    }

    // Gamification: XP
    val userXP: Flow<Int> = context.dataStore.data.map {
        it[Keys.USER_XP] ?: 0
    }

    suspend fun addXP(amount: Int) {
        context.dataStore.edit {
            val current = it[Keys.USER_XP] ?: 0
            it[Keys.USER_XP] = current + amount
        }
    }

    // Gamification: Streak
    val userStreak: Flow<Int> = context.dataStore.data.map {
        it[Keys.USER_STREAK] ?: 0
    }

    suspend fun incrementStreak() {
        context.dataStore.edit {
            val current = it[Keys.USER_STREAK] ?: 0
            it[Keys.USER_STREAK] = current + 1
        }
    }

    suspend fun resetStreak() {
        context.dataStore.edit { it[Keys.USER_STREAK] = 0 }
    }

    // DND Sync
    val dndSyncEnabled: Flow<Boolean> = context.dataStore.data.map {
        it[Keys.DND_SYNC_ENABLED] ?: false
    }

    suspend fun setDndSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DND_SYNC_ENABLED] = enabled }
    }
}
