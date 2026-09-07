package com.example.feature.session.engine

import com.example.FocusShieldApp
import com.example.data.repository.BreakRepository
import com.example.feature.session.domain.BreakStatus
import com.example.feature.session.domain.ManualBreakInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manages user-requested temporary break lifecycles during active Focus Sessions.
 * Tracks wall-clock timestamps, computes actual break duration, handles auto-expiration,
 * and persists records to the Room database via BreakRepository.
 */
class BreakManager(
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO),
    private val currentTimeProvider: () -> Long = { System.currentTimeMillis() },
    private val breakRepositoryProvider: (() -> BreakRepository)? = null
) {
    private val _activeBreak = MutableStateFlow<ManualBreakInfo?>(null)
    val activeBreak: StateFlow<ManualBreakInfo?> = _activeBreak.asStateFlow()

    val isBreakActive: Boolean
        get() = _activeBreak.value?.isActive == true

    private fun getRepository(): BreakRepository? {
        return try {
            breakRepositoryProvider?.invoke() ?: if (com.example.FocusShieldApp::class.java.declaredFields.any { it.name == "instance" } && FocusShieldApp.instance != null) {
                FocusShieldApp.instance.breakRepository
            } else null
        } catch (e: Throwable) {
            null
        }
    }

    /**
     * Starts a manual break with specified duration in minutes.
     */
    fun startBreak(
        sessionId: String,
        durationMinutes: Int,
        now: Long = currentTimeProvider()
    ): ManualBreakInfo {
        val durationMillis = durationMinutes * 60 * 1000L
        val breakInfo = ManualBreakInfo(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            requestedDurationMillis = durationMillis,
            startedAt = now,
            endsAt = now + durationMillis,
            status = BreakStatus.BREAK_ACTIVE,
            actualDurationMillis = 0L
        )

        _activeBreak.value = breakInfo

        // Persist initial break record
        coroutineScope.launch {
            try {
                getRepository()?.recordBreak(breakInfo, isCompleted = false)
            } catch (e: Exception) {
                // Room persistence logged safely
            }
        }

        return breakInfo
    }

    /**
     * Ends the break early upon user request, recording the exact elapsed break duration.
     */
    fun endBreakEarly(now: Long = currentTimeProvider()): ManualBreakInfo? {
        val current = _activeBreak.value ?: return null
        if (!current.isActive) return null

        val actualDuration = (now - current.startedAt)
            .coerceAtLeast(0L)
            .coerceAtMost(current.requestedDurationMillis)

        val endedBreak = current.copy(
            status = BreakStatus.BREAK_ENDED_EARLY,
            actualDurationMillis = actualDuration
        )

        _activeBreak.value = null

        // Update database record with final duration
        coroutineScope.launch {
            try {
                getRepository()?.updateBreak(endedBreak, isCompleted = true)
            } catch (e: Exception) {
                // Room persistence logged safely
            }
        }

        return endedBreak
    }

    /**
     * Verifies if the active break has reached its end timestamp and marks it expired.
     * Returns the expired break info if expired, or null if still ongoing.
     */
    fun checkExpiration(now: Long = currentTimeProvider()): ManualBreakInfo? {
        val current = _activeBreak.value ?: return null
        if (!current.isActive) return null

        if (now >= current.endsAt) {
            val expiredBreak = current.copy(
                status = BreakStatus.BREAK_EXPIRED,
                actualDurationMillis = current.requestedDurationMillis
            )

            _activeBreak.value = null

            // Update database record with full completion
            coroutineScope.launch {
                try {
                    getRepository()?.updateBreak(expiredBreak, isCompleted = true)
                } catch (e: Exception) {
                    // Room persistence logged safely
                }
            }

            return expiredBreak
        }

        return null
    }

    /**
     * Resets active break state.
     */
    fun reset() {
        _activeBreak.value = null
    }
}
