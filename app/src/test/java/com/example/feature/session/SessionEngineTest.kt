package com.example.feature.session

import com.example.core.accessibility.FocusProtectionState
import com.example.core.accessibility.ProtectionDecision
import com.example.core.accessibility.ProtectionPolicy
import com.example.core.util.TimeFormatter
import com.example.data.model.SessionMode
import com.example.feature.session.domain.PomodoroConfig
import com.example.feature.session.domain.PomodoroPhase
import com.example.feature.session.domain.SessionState
import com.example.feature.session.engine.FocusSessionManager
import com.example.feature.session.engine.PomodoroEngine
import com.example.feature.session.engine.StopwatchEngine
import com.example.feature.session.engine.TimerEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit test suite verifying the authoritative timing engines and session manager.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SessionEngineTest {

    @Test
    fun testTimerEngine_accurateCountdownAndPauseResume() {
        var currentTime = 1000000L
        val engine = TimerEngine(currentTimeProvider = { currentTime })

        // 60 minutes = 3,600,000 ms
        engine.start(durationMillis = 3600000L, now = currentTime)
        assertTrue(engine.isRunning)
        assertFalse(engine.isPaused)
        assertEquals(3600000L, engine.calculateRemaining(currentTime))

        // Advance 10 minutes (600,000 ms)
        currentTime += 600000L
        assertEquals(600000L, engine.calculateElapsed(currentTime))
        assertEquals(3000000L, engine.calculateRemaining(currentTime))
        assertFalse(engine.updateAndCheckCompletion(currentTime))

        // Pause for 5 minutes (300,000 ms)
        engine.pause(currentTime)
        assertTrue(engine.isPaused)
        currentTime += 300000L

        // Elapsed during pause should remain unchanged
        assertEquals(600000L, engine.calculateElapsed(currentTime))
        assertEquals(3000000L, engine.calculateRemaining(currentTime))

        // Resume and advance another 10 minutes
        engine.resume(currentTime)
        assertFalse(engine.isPaused)
        currentTime += 600000L

        assertEquals(1200000L, engine.calculateElapsed(currentTime))
        assertEquals(2400000L, engine.calculateRemaining(currentTime))

        // Advance to exact end of timer
        currentTime += 2400000L
        assertTrue(engine.updateAndCheckCompletion(currentTime))
        assertEquals(3600000L, engine.calculateElapsed(currentTime))
        assertEquals(0L, engine.calculateRemaining(currentTime))
    }

    @Test
    fun testStopwatchEngine_lapsAndElapsedTracking() {
        var currentTime = 5000000L
        val engine = StopwatchEngine(currentTimeProvider = { currentTime })

        engine.start(now = currentTime)
        assertTrue(engine.isRunning)

        // Advance 45 seconds (45,000 ms) and record Lap 1
        currentTime += 45000L
        val lap1 = engine.recordLap(currentTime)
        assertNotNull(lap1)
        assertEquals(1, lap1!!.lapNumber)
        assertEquals(45000L, lap1.lapTimeMillis)
        assertEquals(45000L, lap1.totalTimeMillis)

        // Advance another 30 seconds and record Lap 2
        currentTime += 30000L
        val lap2 = engine.recordLap(currentTime)
        assertNotNull(lap2)
        assertEquals(2, lap2!!.lapNumber)
        assertEquals(30000L, lap2.lapTimeMillis)
        assertEquals(75000L, lap2.totalTimeMillis)
        assertEquals(2, engine.laps.size)
    }

    @Test
    fun testPomodoroEngine_deterministicCycleTransitions() {
        var currentTime = 1000000L
        val engine = PomodoroEngine(currentTimeProvider = { currentTime })

        val config = PomodoroConfig.fromMinutes(
            focusMinutes = 25,
            shortBreakMinutes = 5,
            longBreakMinutes = 15,
            cycles = 2
        )

        engine.start(config, now = currentTime)
        assertEquals(PomodoroPhase.FOCUS, engine.currentPhase)
        assertEquals(1, engine.currentCycle)

        // Advance full focus duration (25m = 1,500,000ms)
        currentTime += 25 * 60 * 1000L
        val transitioned1 = engine.updateAndCheckTransitions(currentTime)
        assertTrue(transitioned1)
        assertEquals(PomodoroPhase.SHORT_BREAK, engine.currentPhase)
        assertEquals(1, engine.currentCycle)

        // Advance short break duration (5m = 300,000ms)
        currentTime += 5 * 60 * 1000L
        val transitioned2 = engine.updateAndCheckTransitions(currentTime)
        assertTrue(transitioned2)
        assertEquals(PomodoroPhase.FOCUS, engine.currentPhase)
        assertEquals(2, engine.currentCycle)

        // Advance second focus duration (25m = 1,500,000ms)
        currentTime += 25 * 60 * 1000L
        val transitioned3 = engine.updateAndCheckTransitions(currentTime)
        assertTrue(transitioned3)
        assertEquals(PomodoroPhase.LONG_BREAK, engine.currentPhase)
        assertEquals(2, engine.currentCycle)

        // Advance long break duration (15m = 900,000ms)
        currentTime += 15 * 60 * 1000L
        val transitioned4 = engine.updateAndCheckTransitions(currentTime)
        assertTrue(transitioned4)
        assertTrue(engine.isCompleted)
    }

    @Test
    fun testTimeFormatter_digitalAndHumanReadable() {
        assertEquals("01:00:00", TimeFormatter.formatDigital(3600000L))
        assertEquals("00:25:30", TimeFormatter.formatDigital(1530000L))
        assertEquals("25:30", TimeFormatter.formatDigital(1530000L, alwaysShowHours = false))
        assertEquals("1h 30m", TimeFormatter.formatHumanReadable(90 * 60 * 1000L))
        assertEquals("45m", TimeFormatter.formatHumanReadable(45 * 60 * 1000L))
    }

    @Test
    fun testFocusSessionManager_startPauseResumeEnd() {
        val testDispatcher = UnconfinedTestDispatcher()
        val testScope = TestScope(testDispatcher)
        var currentTime = 2000000L

        val manager = FocusSessionManager(
            coroutineScope = testScope,
            currentTimeProvider = { currentTime }
        )

        assertEquals(SessionState.IDLE, manager.sessionState.value)

        val startResult = manager.startSession(
            mode = SessionMode.TIMER,
            subject = "Physics",
            topic = "Electrostatics",
            plannedDurationMillis = 3600000L
        )

        assertTrue(startResult.isSuccess)
        assertEquals(SessionState.RUNNING, manager.sessionState.value)
        assertNotNull(manager.activeSession.value)

        val paused = manager.pauseSession()
        assertTrue(paused)
        assertEquals(SessionState.PAUSED, manager.sessionState.value)

        val resumed = manager.resumeSession()
        assertTrue(resumed)
        assertEquals(SessionState.RUNNING, manager.sessionState.value)

        val completed = manager.endSession()
        assertNotNull(completed)
        assertEquals(SessionState.COMPLETED, manager.sessionState.value)

        manager.resetToIdle()
        assertEquals(SessionState.IDLE, manager.sessionState.value)
    }

    @Test
    fun testSessionBreakLifecycle_startBreakAndAutomaticResume() {
        val testDispatcher = UnconfinedTestDispatcher()
        val testScope = TestScope(testDispatcher)
        var currentTime = 1000000L

        val manager = FocusSessionManager(
            coroutineScope = testScope,
            currentTimeProvider = { currentTime }
        )

        manager.startSession(
            mode = SessionMode.TIMER,
            subject = "Chemistry",
            topic = "Thermodynamics",
            plannedDurationMillis = 3600000L,
            isAppBlocking = true
        )

        assertEquals(SessionState.RUNNING, manager.sessionState.value)

        // Advance 15 minutes of study
        currentTime += 15 * 60 * 1000L
        manager.syncStateFromEngines()

        // Start a 5-minute break
        val breakInfo = manager.startBreak(durationMinutes = 5)
        assertNotNull(breakInfo)
        assertTrue(manager.breakManager.isBreakActive)
        assertEquals(5 * 60 * 1000L, manager.breakManager.activeBreak.value?.requestedDurationMillis)

        // Protection state during break should NOT block apps
        val activeBreak = manager.breakManager.activeBreak.value
        val isBreakActive = activeBreak != null && activeBreak.isActive && currentTime < activeBreak.endsAt
        val protectionState = FocusProtectionState(
            sessionId = manager.activeSession.value?.id,
            sessionState = manager.sessionState.value,
            isAppBlockingEnabled = true,
            isBreakActive = isBreakActive,
            breakEndsAt = activeBreak?.endsAt ?: 0L,
            blockedPackages = setOf("com.instagram.android")
        )

        val decision = ProtectionPolicy.evaluate(
            packageName = "com.instagram.android",
            state = protectionState,
            currentTime = currentTime
        )
        assertEquals(ProtectionDecision.ALLOW_DURING_BREAK, decision)

        // Advance 3 minutes (break ongoing)
        currentTime += 3 * 60 * 1000L
        manager.syncStateFromEngines()
        assertTrue(manager.breakManager.isBreakActive)

        // Advance 3 more minutes (total 6 minutes -> break expires)
        currentTime += 3 * 60 * 1000L
        manager.syncStateFromEngines()
        assertFalse(manager.breakManager.isBreakActive)

        // App access should now be Blocked again!
        val postBreakState = FocusProtectionState(
            sessionId = manager.activeSession.value?.id,
            sessionState = manager.sessionState.value,
            isAppBlockingEnabled = true,
            isBreakActive = manager.breakManager.isBreakActive,
            breakEndsAt = 0L,
            blockedPackages = setOf("com.instagram.android")
        )
        val blockedDecision = ProtectionPolicy.evaluate(
            packageName = "com.instagram.android",
            state = postBreakState,
            currentTime = currentTime
        )
        assertEquals(ProtectionDecision.BLOCK, blockedDecision)
    }

    @Test
    fun testProtectionPolicy_safeSystemPackagesNeverBlocked() {
        val activeProtection = FocusProtectionState(
            sessionId = "session-1",
            sessionState = SessionState.RUNNING,
            isAppBlockingEnabled = true,
            isStrictModeEnabled = true,
            isBreakActive = false,
            blockedPackages = setOf("com.google.android.dialer", "com.android.settings", "com.instagram.android")
        )

        // Safe phone dialer must NEVER be blocked
        val dialerDecision = ProtectionPolicy.evaluate(
            packageName = "com.google.android.dialer",
            state = activeProtection
        )
        assertEquals(ProtectionDecision.ALLOW_SYSTEM, dialerDecision)

        // Safe settings app must NEVER be blocked
        val settingsDecision = ProtectionPolicy.evaluate(
            packageName = "com.android.settings",
            state = activeProtection
        )
        assertEquals(ProtectionDecision.ALLOW_SYSTEM, settingsDecision)

        // Blocklisted social media MUST be blocked
        val instaDecision = ProtectionPolicy.evaluate(
            packageName = "com.instagram.android",
            state = activeProtection
        )
        assertEquals(ProtectionDecision.BLOCK, instaDecision)
    }

    @Test
    fun testPomodoro_stopsAfter30Seconds_recordsAccurate30SecondsStudyTime() {
        val testDispatcher = UnconfinedTestDispatcher()
        val testScope = TestScope(testDispatcher)
        var currentTime = 1000000L

        val manager = FocusSessionManager(
            coroutineScope = testScope,
            currentTimeProvider = { currentTime }
        )

        val pomodoroConfig = PomodoroConfig.fromMinutes(
            focusMinutes = 25,
            shortBreakMinutes = 5,
            longBreakMinutes = 15,
            cycles = 4
        )

        manager.startSession(
            mode = SessionMode.POMODORO,
            subject = "Physics",
            topic = "Thermodynamics",
            pomodoroConfig = pomodoroConfig
        )

        // Session starts: remaining phase is 25 minutes, elapsed study time is 0s
        assertEquals(25 * 60 * 1000L, manager.activeSession.value?.remainingDurationMillis)
        assertEquals(0L, manager.activeSession.value?.elapsedDurationMillis)

        // Advance 30 seconds (30,000 ms)
        currentTime += 30000L
        manager.syncStateFromEngines(currentTime)

        // Elapsed study time MUST be 30 seconds (30,000 ms), remaining countdown MUST be 24m 30s
        assertEquals(30000L, manager.activeSession.value?.elapsedDurationMillis)
        assertEquals(25 * 60 * 1000L - 30000L, manager.activeSession.value?.remainingDurationMillis)

        // End session after 30 seconds
        val completed = manager.endSession()
        assertNotNull(completed)
        assertEquals(30000L, completed?.elapsedDurationMillis)

        // ActualStudyTimeCalculator MUST calculate exactly 30 seconds of pure study time, NOT 25 minutes or 100 minutes
        val pureStudyTime = com.example.feature.analytics.domain.ActualStudyTimeCalculator.calculatePureStudyDuration(
            session = completed!!,
            isCompleted = true
        )
        assertEquals(30000L, pureStudyTime)
    }

    @Test
    fun testTimer_stopsAfter30Seconds_recordsAccurate30SecondsStudyTime() {
        val testDispatcher = UnconfinedTestDispatcher()
        val testScope = TestScope(testDispatcher)
        var currentTime = 2000000L

        val manager = FocusSessionManager(
            coroutineScope = testScope,
            currentTimeProvider = { currentTime }
        )

        // 25 minutes planned timer
        manager.startSession(
            mode = SessionMode.TIMER,
            subject = "Mathematics",
            topic = "Calculus",
            plannedDurationMillis = 25 * 60 * 1000L
        )

        // Advance 30 seconds
        currentTime += 30000L
        manager.syncStateFromEngines(currentTime)

        assertEquals(30000L, manager.activeSession.value?.elapsedDurationMillis)
        assertEquals(25 * 60 * 1000L - 30000L, manager.activeSession.value?.remainingDurationMillis)

        val completed = manager.endSession()
        assertNotNull(completed)
        assertEquals(30000L, completed?.elapsedDurationMillis)

        val pureStudyTime = com.example.feature.analytics.domain.ActualStudyTimeCalculator.calculatePureStudyDuration(
            session = completed!!,
            isCompleted = true
        )
        assertEquals(30000L, pureStudyTime)
    }
}
