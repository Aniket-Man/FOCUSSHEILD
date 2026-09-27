package com.example.feature.analytics.domain

import com.example.data.local.entity.SessionRecordEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class StreakResult(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalStudyDays: Int,
    val isStreakActiveToday: Boolean,
    val thresholdMinutes: Int
)

object StreakCalculator {

    private fun zone(): ZoneId = ZoneId.systemDefault()

    fun calculate(
        sessions: List<SessionRecordEntity>,
        minDailyThresholdMinutes: Int = 20
    ): StreakResult {
        if (sessions.isEmpty()) {
            return StreakResult(
                currentStreak = 0,
                longestStreak = 0,
                totalStudyDays = 0,
                isStreakActiveToday = false,
                thresholdMinutes = minDailyThresholdMinutes
            )
        }

        val minThresholdMillis = minDailyThresholdMinutes * 60 * 1000L

        // Group actual study time by the day the session ENDED. Bucketing by start time credited a
        // 23:30 -> 00:40 session entirely to yesterday, so a student who studied across midnight
        // got no streak credit for today's minutes — exactly when streaks broke for late-night
        // study. endTime falls back to start + actual duration for rows that predate endTime.
        val dailyStudyMap = mutableMapOf<String, Long>()
        for (session in sessions) {
            val endMillis = if (session.endTime > 0) {
                session.endTime
            } else {
                session.startTime + session.actualDurationMillis
            }
            val dateKey = formatDateKey(endMillis)
            val current = dailyStudyMap.getOrDefault(dateKey, 0L)
            dailyStudyMap[dateKey] = current + session.actualDurationMillis
        }

        val qualifyingDays = dailyStudyMap.filter { it.value >= minThresholdMillis }.keys.toSet()

        val today = LocalDate.now(zone())
        val yesterday = today.minusDays(1)
        val isTodayQualifying = qualifyingDays.contains(today.toString())

        // Calculate current streak: walk backwards from today if it already qualifies, otherwise
        // from yesterday (the streak is still alive until today's threshold is missed entirely).
        var currentStreak = 0
        if (isTodayQualifying || qualifyingDays.contains(yesterday.toString())) {
            var day = if (isTodayQualifying) today else yesterday
            while (qualifyingDays.contains(day.toString())) {
                currentStreak++
                day = day.minusDays(1)
            }
        }

        // Calculate longest streak from all historical sessions
        val longestStreak = calculateLongestStreak(qualifyingDays)

        return StreakResult(
            currentStreak = currentStreak,
            longestStreak = longestStreak.coerceAtLeast(currentStreak),
            totalStudyDays = qualifyingDays.size,
            isStreakActiveToday = isTodayQualifying,
            thresholdMinutes = minDailyThresholdMinutes
        )
    }

    private fun calculateLongestStreak(qualifyingDays: Set<String>): Int {
        if (qualifyingDays.isEmpty()) return 0
        if (qualifyingDays.size == 1) return 1

        val sortedDays = qualifyingDays.sorted()
        var maxStreak = 1
        var currentSequence = 1

        for (i in 0 until sortedDays.size - 1) {
            val d1Date = parseDateKey(sortedDays[i]) ?: continue
            val d2Date = parseDateKey(sortedDays[i + 1]) ?: continue

            if (d1Date.plusDays(1) == d2Date) {
                currentSequence++
                if (currentSequence > maxStreak) {
                    maxStreak = currentSequence
                }
            } else {
                currentSequence = 1
            }
        }

        return maxStreak
    }

    /**
     * ISO-8601 `yyyy-MM-dd` key for an instant. `LocalDate.toString()` is deterministic and
     * locale-independent (the previous shared `SimpleDateFormat` was neither thread-safe nor
     * guaranteed to produce stable keys, and its output did not sort chronologically under some
     * locales — which the longest-streak scan relies on).
     */
    fun formatDateKey(timeMillis: Long): String =
        Instant.ofEpochMilli(timeMillis).atZone(zone()).toLocalDate().toString()

    private fun parseDateKey(dateKey: String): LocalDate? {
        return try {
            LocalDate.parse(dateKey)
        } catch (e: Exception) {
            null
        }
    }
}
