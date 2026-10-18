package com.example.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Local-day `[00:00:00.000, 23:59:59.999]` bounds as epoch millis, evaluated with `java.time`.
 *
 * [todayFlow] re-emits the bounds at every local midnight. "Today" queries used to capture their
 * bounds once when a flow was constructed, so after 00:00 the UI kept serving yesterday's window
 * until the process restarted — and a plain per-call [today] cannot fix that, because the DAO flows
 * bake their SQL parameters in at subscription time.
 *
 * `java.time` (not `Calendar`) also handles DST transitions: `atStartOfDay` is gap-aware, whereas
 * setting `HOUR_OF_DAY = 0` on a calendar can produce a non-existent local time.
 */
object DayBounds {

    fun today(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now(zone)
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start to end
    }

    /**
     * Emits [today] immediately, then again just past each following local midnight, forever.
     * Collectors pair this with [kotlinx.coroutines.flow.flatMapLatest] so their inner query flow
     * is rebuilt with fresh bounds when the date rolls over.
     */
    fun todayFlow(): Flow<Pair<Long, Long>> = flow {
        while (true) {
            val bounds = today()
            emit(bounds)
            val millisUntilNextMidnight =
                (bounds.second + 1) - System.currentTimeMillis() + 1_000L
            delay(millisUntilNextMidnight.coerceAtLeast(1_000L))
        }
    }

    /** The local calendar date of an epoch-millis instant (the key used to bucket by day). */
    fun localDate(timeMillis: Long): LocalDate =
        Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    /** Returns `[00:00:00.000, 23:59:59.999]` epoch millis for [date] in [zone]. */
    fun boundsForDate(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        return start to end
    }

    /**
     * Computes how much of a record's [actualDurationMillis] falls inside `[windowStart, windowEnd]`.
     * If the record is completely inside the window, returns [actualDurationMillis].
     * If the record crosses a boundary (e.g. midnight), scales [actualDurationMillis] proportionally
     * to the overlap with `[windowStart, windowEnd]`.
     */
    fun clampedDurationInWindow(
        startTime: Long,
        endTime: Long,
        actualDurationMillis: Long,
        windowStart: Long,
        windowEnd: Long
    ): Long {
        if (actualDurationMillis <= 0L) return 0L
        val effectiveEnd = if (endTime > startTime) endTime else (startTime + actualDurationMillis)
        if (startTime >= windowStart && effectiveEnd <= windowEnd) {
            return actualDurationMillis
        }
        val overlapStart = maxOf(startTime, windowStart)
        val overlapEnd = minOf(effectiveEnd, windowEnd)
        if (overlapEnd <= overlapStart) return 0L

        val wallSpan = (effectiveEnd - startTime).coerceAtLeast(1L)
        val overlapSpan = overlapEnd - overlapStart
        return ((actualDurationMillis.toDouble() * overlapSpan.toDouble()) / wallSpan.toDouble())
            .toLong()
            .coerceIn(0L, actualDurationMillis)
    }

    /**
     * Slices a session's [actualDurationMillis] across all local calendar days (`yyyy-MM-dd`) it spans.
     * Ensures Daily Chart and StreakCalculator attribute cross-midnight sessions identically.
     */
    fun sliceSessionDurationByDay(
        startTime: Long,
        endTime: Long,
        actualDurationMillis: Long,
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<String, Long> {
        if (actualDurationMillis <= 0L) return emptyMap()
        val effectiveEnd = if (endTime > startTime) endTime else (startTime + actualDurationMillis)
        val startDate = Instant.ofEpochMilli(startTime).atZone(zone).toLocalDate()
        val endDate = Instant.ofEpochMilli(effectiveEnd).atZone(zone).toLocalDate()

        if (startDate == endDate) {
            return mapOf(startDate.toString() to actualDurationMillis)
        }

        val slices = linkedMapOf<String, Long>()
        var current = startDate
        var allocated = 0L
        while (!current.isAfter(endDate)) {
            val (dayStart, dayEnd) = boundsForDate(current, zone)
            val slice = if (current == endDate) {
                (actualDurationMillis - allocated).coerceAtLeast(0L)
            } else {
                clampedDurationInWindow(startTime, effectiveEnd, actualDurationMillis, dayStart, dayEnd)
            }
            if (slice > 0L) {
                slices[current.toString()] = slice
                allocated += slice
            }
            current = current.plusDays(1)
        }
        return slices
    }
}
