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
}
