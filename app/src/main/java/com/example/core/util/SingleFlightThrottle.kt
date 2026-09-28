package com.example.core.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A rate limiter that **never drops the last request**.
 *
 * The naive throttle this replaces did three things wrong at once (issue #30):
 *
 *  1. it committed the "last run" timestamp *before* doing the work, so a crash or a slow update
 *     still counted as a completed refresh;
 *  2. it returned early for any call inside the window with no trailing run, so the most important
 *     update of all — the final one, e.g. "the session just ended" — was the one most likely to be
 *     thrown away, leaving stale numbers on screen until something else happened to trigger a
 *     refresh;
 *  3. it used check-then-set on a single `@Volatile` field, which is not atomic, so two concurrent
 *     callers could both pass the check and double-fire.
 *
 * This implementation instead:
 *
 *  - **coalesces**: calls arriving inside the window are collapsed into exactly one trailing run
 *    that happens as soon as the window closes;
 *  - **serialises**: the work itself runs under a [Mutex], so concurrent callers can never
 *    double-fire;
 *  - **stamps last**: the timestamp is written in a `finally` after the work, so the window starts
 *    when the refresh really finished.
 *
 * Android-free and clock-injectable, so the behaviour above is unit-tested directly.
 *
 * @param minIntervalMillis minimum distance between two completed runs.
 * @param now source of "now"; injected for tests.
 */
class SingleFlightThrottle(
    private val minIntervalMillis: Long,
    private val now: () -> Long = { System.currentTimeMillis() }
) {

    private val mutex = Mutex()

    /** When the last run *finished*; null until the first run completes. */
    private var lastRunEndedAt: Long? = null

    /** True while one caller has taken ownership of the trailing run others are coalescing into. */
    private var trailingClaimed = false

    /**
     * Runs [block], immediately when the window has elapsed (or [force] is true), otherwise once the
     * window closes.
     *
     * Suspends until the run this call is responsible for has happened. A call that is coalesced
     * into an already-owed trailing run returns immediately — the work is still guaranteed to
     * happen, it is simply owned by the earlier caller.
     */
    suspend fun submit(force: Boolean = false, block: suspend () -> Unit) {
        // Phase 1 — decide atomically whether this caller owns a run, and how long it must wait.
        val waitMillis = mutex.withLock {
            val last = lastRunEndedAt
            val remaining = when {
                force || last == null -> 0L
                else -> (minIntervalMillis - (now() - last)).coerceAtLeast(0L)
            }
            if (remaining > 0L) {
                // Someone is already going to run this when the window closes: coalesce into it
                // instead of queueing a second identical refresh.
                if (trailingClaimed) return
                trailingClaimed = true
            }
            remaining
        }

        if (waitMillis > 0L) {
            delay(waitMillis)
        }

        // Phase 2 — run exclusively. Re-check the window: while this caller waited (for the delay
        // or for the lock) another caller may have completed the very same refresh.
        mutex.withLock {
            if (waitMillis > 0L) {
                trailingClaimed = false
            }
            val last = lastRunEndedAt
            val mayRun = force || last == null || now() - last >= minIntervalMillis
            if (!mayRun) return
            try {
                block()
            } finally {
                // After, never before: an update that threw or took two seconds must not pretend
                // the window started when it was requested.
                lastRunEndedAt = now()
            }
        }
    }
}
