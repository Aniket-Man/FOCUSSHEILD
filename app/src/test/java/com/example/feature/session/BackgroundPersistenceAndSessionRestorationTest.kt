package com.example.feature.session

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.permission.FocusPermissionManager
import com.example.data.model.SessionMode
import com.example.feature.session.domain.PomodoroConfig
import com.example.feature.session.domain.SessionState
import com.example.feature.session.engine.FocusSessionManager
import com.example.feature.session.service.FocusSessionForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackgroundPersistenceAndSessionRestorationTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FocusSessionManager.instance.resetToIdle()
        FocusSessionManager.instance.clearActiveSessionFromDisk(context)
    }

    @Test
    fun testPermissionManager_batteryOptimizationMethods() {
        // Checking battery optimization status returns a valid boolean
        val isIgnored = FocusPermissionManager.isBatteryOptimizationIgnored(context)
        // Verify requesting doesn't throw exceptions
        FocusPermissionManager.requestIgnoreBatteryOptimization(context)
        FocusPermissionManager.openBatteryOptimizationSettings(context)

        val status = FocusPermissionManager.getPermissionStatus(context, requiresBlocking = false)
        assertEquals(isIgnored, status.isBatteryOptimizationIgnored)
        assertTrue(status.areMandatoryGranted)
    }

    @Test
    fun testActiveSession_persistenceAndRestoration_timerMode() {
        val manager = FocusSessionManager.instance

        // Start a 45-minute Timer session
        val startResult = manager.startSession(
            mode = SessionMode.TIMER,
            subject = "Physics",
            topic = "Thermodynamics",
            goal = "Solve 15 numericals",
            plannedDurationMillis = 45 * 60 * 1000L,
            isAppBlocking = true,
            isStrictMode = true,
            isStudyChannels = true,
            blockedAppPackages = setOf("com.instagram.android", "com.example.game")
        )
        assertTrue(startResult.isSuccess)
        val session = manager.activeSession.value
        assertNotNull(session)

        // Save session to disk snapshot
        manager.saveActiveSessionToDisk(context)

        // Simulate app kill / process termination by resetting in-memory state
        manager.resetToIdle()
        assertNull(manager.activeSession.value)
        assertEquals(SessionState.IDLE, manager.sessionState.value)

        // Restore session from disk as if newly launched
        val restored = manager.restoreActiveSessionFromDisk(context)
        assertNotNull(restored)
        assertEquals("Physics", restored!!.subject)
        assertEquals("Thermodynamics", restored.topic)
        assertEquals("Solve 15 numericals", restored.goal)
        assertEquals(SessionMode.TIMER, restored.mode)
        assertEquals(SessionState.RUNNING, restored.state)
        assertTrue(restored.isAppBlockingEnabled)
        assertTrue(restored.isStrictModeEnabled)
        assertTrue(restored.isStudyChannelsEnabled)
        assertEquals(
            setOf("com.instagram.android", "com.example.game"),
            restored.blockedAppPackages
        )
        assertEquals(45 * 60 * 1000L, restored.plannedDurationMillis)

        // Clear active session and verify restoration returns null
        manager.clearActiveSessionFromDisk(context)
        manager.resetToIdle()
        val restoredAfterClear = manager.restoreActiveSessionFromDisk(context)
        assertNull(restoredAfterClear)
    }

    @Test
    fun testActiveSession_persistenceAndRestoration_pomodoroMode() {
        val manager = FocusSessionManager.instance

        val pomodoroConfig = PomodoroConfig.fromMinutes(
            focusMinutes = 25,
            shortBreakMinutes = 5,
            longBreakMinutes = 15,
            cycles = 4
        )

        val startResult = manager.startSession(
            mode = SessionMode.POMODORO,
            subject = "Organic Chemistry",
            topic = "Reaction Mechanisms",
            pomodoroConfig = pomodoroConfig,
            isAppBlocking = true
        )
        assertTrue(startResult.isSuccess)
        manager.saveActiveSessionToDisk(context)

        // Reset in-memory state to simulate recents clear
        manager.resetToIdle()
        assertNull(manager.activeSession.value)

        // Restore from disk
        val restored = manager.restoreActiveSessionFromDisk(context)
        assertNotNull(restored)
        assertEquals("Organic Chemistry", restored!!.subject)
        assertEquals(SessionMode.POMODORO, restored.mode)
        assertEquals(4, restored.totalCycles)
        assertEquals(25 * 60 * 1000L, restored.pomodoroConfig.focusDurationMillis)
    }

    @Test
    fun testForegroundService_startAndStopCommands() {
        // Verify start and stop service helper methods execute safely without crash
        FocusSessionForegroundService.start(context)
        FocusSessionForegroundService.stop(context)
    }
}
