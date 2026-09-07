package com.example.feature.session.domain

/**
 * Lifecycle states of a user-requested temporary break.
 */
enum class BreakStatus {
    NO_BREAK,
    BREAK_REQUESTED,
    BREAK_ACTIVE,
    BREAK_ENDED_EARLY,
    BREAK_EXPIRED
}

/**
 * Domain model representing a temporary manual break taken during an active Focus Session.
 * Uses strict timestamp math for accurate wall-clock countdown and actual duration calculation.
 */
data class ManualBreakInfo(
    val id: String,
    val sessionId: String,
    val requestedDurationMillis: Long,
    val startedAt: Long,
    val endsAt: Long,
    val status: BreakStatus = BreakStatus.BREAK_ACTIVE,
    val actualDurationMillis: Long = 0L
) {
    val isActive: Boolean
        get() = status == BreakStatus.BREAK_ACTIVE

    fun calculateRemainingMillis(now: Long): Long {
        if (!isActive) return 0L
        return (endsAt - now).coerceAtLeast(0L)
    }

    fun calculateElapsedBreakMillis(now: Long): Long {
        if (now <= startedAt) return 0L
        val rawElapsed = now - startedAt
        return rawElapsed.coerceAtMost(requestedDurationMillis)
    }

    fun isExpired(now: Long): Boolean {
        return now >= endsAt
    }
}
