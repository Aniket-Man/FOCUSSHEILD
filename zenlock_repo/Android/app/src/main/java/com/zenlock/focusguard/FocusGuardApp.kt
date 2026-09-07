package com.zenlock.focusguard

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.zenlock.focusguard.data.db.FocusGuardDatabase
import com.zenlock.focusguard.data.preferences.UserPreferences
import com.zenlock.focusguard.data.repository.BlockedAppRepositoryImpl
import com.zenlock.focusguard.data.repository.FocusSessionRepositoryImpl
import com.zenlock.focusguard.data.repository.KeywordRepositoryImpl

/**
 * Application class for FocusGuard.
 * Initializes core dependencies like the database, preferences, and repositories.
 * Uses manual dependency injection for simplicity (no Hilt/Dagger).
 */
class FocusGuardApp : Application() {

    // Lazy-initialized database instance (singleton)
    val database: FocusGuardDatabase by lazy {
        FocusGuardDatabase.getInstance(this)
    }

    // User preferences backed by DataStore
    val userPreferences: UserPreferences by lazy {
        UserPreferences(this)
    }

    // Repositories
    val blockedAppRepository by lazy {
        BlockedAppRepositoryImpl(database.blockedAppDao())
    }

    val focusSessionRepository by lazy {
        FocusSessionRepositoryImpl(database.focusSessionDao())
    }

    val keywordRepository by lazy {
        KeywordRepositoryImpl(database.keywordDao())
    }

    val digitalWellbeingRepository by lazy {
        com.zenlock.focusguard.data.repository.DigitalWellbeingRepositoryImpl(this, blockedAppRepository)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
    }

    /**
     * Creates notification channels required for Android O+ foreground services.
     */
    private fun createNotificationChannels() {
        val focusChannel = NotificationChannel(
            FOCUS_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(focusChannel)
    }

    companion object {
        const val FOCUS_CHANNEL_ID = "focus_session_channel"
        const val FOCUS_NOTIFICATION_ID = 1001

        @Volatile
        private var instance: FocusGuardApp? = null

        fun getInstance(): FocusGuardApp =
            instance ?: throw IllegalStateException("FocusGuardApp not initialized")
    }
}
