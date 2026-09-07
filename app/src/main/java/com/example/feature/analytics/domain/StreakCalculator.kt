package com.example.feature.analytics.domain

import com.example.data.local.entity.SessionRecordEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class StreakResult(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalStudyDays: Int,
    val isStreakActiveToday: Boolean,
    val thresholdMinutes: Int
)

object StreakCalculator {

    private val localDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

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

        // Group actual study time by calendar day
        val dailyStudyMap = mutableMapOf<String, Long>()
        for (session in sessions) {
            val dateKey = formatDateKey(session.startTime)
            val current = dailyStudyMap.getOrDefault(dateKey, 0L)
            dailyStudyMap[dateKey] = current + session.actualDurationMillis
        }

        val qualifyingDays = dailyStudyMap.filter { it.value >= minThresholdMillis }.keys.toSet()

        val todayKey = formatDateKey(System.currentTimeMillis())
        val isTodayQualifying = qualifyingDays.contains(todayKey)

        val cal = Calendar.getInstance()
        val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
        val yesterdayKey = formatDateKey(yesterdayCal.timeInMillis)

        // Calculate current streak
        var currentStreak = 0
        val startCal = Calendar.getInstance()

        if (isTodayQualifying) {
            // Count backwards starting today
            while (true) {
                val key = formatDateKey(startCal.timeInMillis)
                if (qualifyingDays.contains(key)) {
                    currentStreak++
                    startCal.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
            }
        } else if (qualifyingDays.contains(yesterdayKey)) {
            // Today not yet reached threshold, but yesterday was active
            startCal.timeInMillis = yesterdayCal.timeInMillis
            while (true) {
                val key = formatDateKey(startCal.timeInMillis)
                if (qualifyingDays.contains(key)) {
                    currentStreak++
                    startCal.add(Calendar.DAY_OF_YEAR, -1)
                } else {
                    break
                }
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

        val calendar = Calendar.getInstance()

        for (i in 0 until sortedDays.size - 1) {
            val d1Str = sortedDays[i]
            val d2Str = sortedDays[i + 1]

            val d1Date = parseDateKey(d1Str) ?: continue
            val d2Date = parseDateKey(d2Str) ?: continue

            calendar.time = d1Date
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val expectedNext = formatDateKey(calendar.timeInMillis)

            if (expectedNext == d2Str) {
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

    fun formatDateKey(timeMillis: Long): String {
        return localDateFormat.format(Date(timeMillis))
    }

    private fun parseDateKey(dateKey: String): Date? {
        return try {
            localDateFormat.parse(dateKey)
        } catch (e: Exception) {
            null
        }
    }
}
