package com.example.feature.applimits

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.feature.applimits.engine.ActiveAppUsageSession
import com.example.feature.applimits.engine.AppLimitSessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The App Limit session snapshot is what keeps the deadline that raises the "time's up" blocker
 * alive across a process restart. These tests pin the round-trip and the "clear means clear"
 * behaviour the restore path depends on.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLimitSessionStoreTest {

    private lateinit var context: Context
    private lateinit var store: AppLimitSessionStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = AppLimitSessionStore(context)
        store.clear()
    }

    @Test
    fun `an empty store loads nothing`() {
        assertNull(store.load())
    }

    @Test
    fun `a session round-trips with every field the restore path needs`() {
        val session = ActiveAppUsageSession(
            id = "session-1",
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            startedAt = 1_700_000_000_000L,
            selectedDurationMillis = 5 * 60 * 1000L,
            elapsedMillis = 90_000L,
            isEmergency = false,
            dailyLimitMinutes = 30,
            initialDailyUsedMillis = 8 * 60 * 1000L,
            isStrict = true,
            isPaused = true,
            totalPausedMillis = 42_000L,
            lastPauseTimestamp = 1_700_000_400_000L
        )

        store.save(session, dateString = "2026-09-16")

        val snapshot = store.load()
        assertEquals("2026-09-16", snapshot?.dateString)
        assertEquals(session, snapshot?.session)
    }

    @Test
    fun `saving again replaces the previous snapshot`() {
        val first = sampleSession(id = "first", packageName = "com.instagram.android")
        val second = sampleSession(id = "second", packageName = "com.google.android.youtube")

        store.save(first, "2026-09-16")
        store.save(second, "2026-09-16")

        val restored = store.load()?.session
        assertEquals("second", restored?.id)
        assertEquals("com.google.android.youtube", restored?.packageName)
    }

    @Test
    fun `clearing removes the snapshot so a finished session cannot be restored`() {
        store.save(sampleSession(id = "finished"), "2026-09-16")
        store.clear()

        assertNull(store.load())
    }

    @Test
    fun `a snapshot with no start time is treated as absent`() {
        context.getSharedPreferences("focus_app_limit_session_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("dateString", "2026-09-16")
            .putString("packageName", "com.instagram.android")
            .putLong("startedAt", 0L)
            .apply()

        assertNull(AppLimitSessionStore(context).load())
    }

    @Test
    fun `emergency sessions keep their flag and their own duration accounting`() {
        val session = sampleSession(id = "emergency").copy(
            isEmergency = true,
            selectedDurationMillis = 5 * 60 * 1000L,
            initialDailyUsedMillis = 30 * 60 * 1000L
        )

        store.save(session, "2026-09-16")
        val restored = store.load()?.session

        assertTrue(restored?.isEmergency == true)
        // The emergency minute is not charged against the daily allowance: 30 min of prior usage
        // stays 30 min.
        assertEquals(30 * 60 * 1000L, restored?.currentTotalDailyUsedMillis)
        assertFalse(restored?.isSessionExpired == true)
    }

    private fun sampleSession(
        id: String,
        packageName: String = "com.google.android.youtube"
    ) = ActiveAppUsageSession(
        id = id,
        packageName = packageName,
        appName = "YouTube",
        startedAt = 1_700_000_000_000L,
        selectedDurationMillis = 10 * 60 * 1000L,
        elapsedMillis = 60_000L,
        dailyLimitMinutes = 30,
        initialDailyUsedMillis = 0L
    )
}
