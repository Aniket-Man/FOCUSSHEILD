package com.example.feature.session.notification

import android.content.Context
import android.util.Log
import java.util.TimeZone

/**
 * Detects that the device's UTC offset has moved since alarms were last armed, so wall-clock
 * schedules can be re-planned instead of firing at the wrong minute.
 *
 * ### Why this exists
 *
 * Every alarm this app arms is an **absolute instant** (epoch millis) computed from a **wall-clock**
 * intention ("09:00 on weekdays"). The two only agree while the UTC offset that was in force when
 * the instant was computed is still in force when it fires.
 *
 * The app already re-arms everything on `BOOT_COMPLETED`, `TIME_SET` and `TIMEZONE_CHANGED`. A
 * **daylight-saving transition fires none of those** — the zone id never changes, only its offset
 * does, and the platform sends no broadcast an app can subscribe to. Any alarm armed before the
 * transition therefore fires an hour early or late for the rest of that day (issue #28).
 *
 * ### What it does
 *
 * The zone id and the current offset are recorded in app-private prefs each time alarms are armed
 * for the whole app. Every alarm receiver asks [refreshAndDetectShift] before doing its work; when
 * the stored pair no longer matches reality, the caller re-plans every schedule and every study-plan
 * reminder from the new offset. Nothing is guessed: a mismatch only ever triggers a *recomputation*
 * from the stored wall-clock configuration.
 *
 * The comparison itself is [hasShifted] — pure, so the behaviour is unit-tested without a device.
 */
object TimeZoneOffsetGuard {

    private const val TAG = "TimeZoneOffsetGuard"
    private const val PREFS = "focus_shield_timezone_guard"
    private const val KEY_ZONE_ID = "zone_id"
    private const val KEY_OFFSET_MILLIS = "offset_millis"
    private const val NO_OFFSET = Long.MIN_VALUE

    /**
     * True when [currentZoneId] / [currentOffsetMillis] differ from what was recorded.
     *
     * A missing record (first run, cleared data) is **not** a shift: there is nothing armed from a
     * stale offset to correct, and reporting one would make every cold start re-plan needlessly.
     */
    fun hasShifted(
        storedZoneId: String?,
        storedOffsetMillis: Long,
        currentZoneId: String,
        currentOffsetMillis: Long
    ): Boolean {
        if (storedZoneId == null || storedOffsetMillis == NO_OFFSET) return false
        return storedZoneId != currentZoneId || storedOffsetMillis != currentOffsetMillis
    }

    /** The device's UTC offset right now, in millis (raw offset plus any DST adjustment). */
    fun currentOffsetMillis(
        timeZone: TimeZone = TimeZone.getDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): Long = timeZone.getOffset(nowMillis).toLong()

    /**
     * Records the offset in force right now. Call this after a full re-arm, so the next receiver
     * compares against the offset the armed alarms were actually computed with.
     */
    fun record(
        context: Context,
        timeZone: TimeZone = TimeZone.getDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ) {
        try {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ZONE_ID, timeZone.id)
                .putLong(KEY_OFFSET_MILLIS, currentOffsetMillis(timeZone, nowMillis))
                .apply()
        } catch (e: RuntimeException) {
            // Losing the record only costs an unnecessary re-plan later; it must never take down
            // the alarm path that just succeeded.
            Log.w(TAG, "Could not record the current UTC offset: ${e.message}")
        }
    }

    /**
     * Compares the stored offset with the current one and updates the record.
     *
     * @return true when the offset moved (DST transition, or a zone change that arrived without a
     *   `TIMEZONE_CHANGED` broadcast) and the caller should re-plan every alarm.
     */
    fun refreshAndDetectShift(
        context: Context,
        timeZone: TimeZone = TimeZone.getDefault(),
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        return try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val storedZoneId = prefs.getString(KEY_ZONE_ID, null)
            val storedOffset = prefs.getLong(KEY_OFFSET_MILLIS, NO_OFFSET)
            val currentOffset = currentOffsetMillis(timeZone, nowMillis)

            val shifted = hasShifted(storedZoneId, storedOffset, timeZone.id, currentOffset)
            if (shifted) {
                Log.i(
                    TAG,
                    "UTC offset moved ($storedZoneId ${storedOffset}ms -> ${timeZone.id} " +
                        "${currentOffset}ms); alarms will be re-planned"
                )
            }

            prefs.edit()
                .putString(KEY_ZONE_ID, timeZone.id)
                .putLong(KEY_OFFSET_MILLIS, currentOffset)
                .apply()

            shifted
        } catch (e: RuntimeException) {
            Log.w(TAG, "Could not read the recorded UTC offset: ${e.message}")
            false
        }
    }
}
