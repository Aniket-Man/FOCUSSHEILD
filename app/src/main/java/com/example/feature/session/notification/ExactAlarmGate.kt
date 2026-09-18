package com.example.feature.session.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import com.example.core.permission.FocusPermissionManager

/**
 * The single place where an alarm is handed to [AlarmManager], so the exact/inexact decision is
 * made identically for automated focus schedules and for study-plan reminders (they used to each
 * carry their own copy of this logic, and only one of them checked the permission first).
 *
 * Rules:
 *  - Android 12+: `setExactAndAllowWhileIdle` needs `SCHEDULE_EXACT_ALARM`
 *    ([FocusPermissionManager.isExactAlarmGranted]). The check happens *before* the call, so the
 *    common case is one clear warning instead of a thrown [SecurityException], and the exception is
 *    still caught because the grant can be revoked between the check and the call.
 *  - When exactness is unavailable the alarm is still armed — `setAndAllowWhileIdle`, which fires
 *    while the device is idle — but the caller learns that delivery may be late
 *    ([ArmResult.InexactFallback]) so the user can be told rather than left guessing. Android 12+
    * may defer an inexact alarm by up to an hour, and longer under battery saver / Doze.
 *  - Nothing is ever promised that the platform cannot deliver: a refused alarm reports
 *    [ArmResult.Failed] and logs why.
 */
object ExactAlarmGate {

    private const val TAG = "ExactAlarmGate"

    sealed interface ArmResult {
        /** Armed with `setExact*`; the platform will deliver it at (about) the requested time. */
        data object Exact : ArmResult

        /** Armed with `setAndAllowWhileIdle`; delivery can be delayed by the system. */
        data object InexactFallback : ArmResult

        /** Not armed at all; [reason] explains why (logged by the caller if it needs more context). */
        data class Failed(val reason: String) : ArmResult
    }

    /** True when the platform would honour an exact alarm request right now. */
    fun canSetExact(context: Context): Boolean = FocusPermissionManager.isExactAlarmGranted(context)

    /**
     * Arms [operation] for [triggerAtMillis] (RTC_WAKEUP), preferring an exact alarm.
     *
     * @param label short human-readable identification used in log lines ("plan 3f2…", "schedule …").
     */
    fun arm(
        context: Context,
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        operation: PendingIntent,
        label: String
    ): ArmResult {
        if (canSetExact(context)) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        operation
                    )
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
                }
                return ArmResult.Exact
            } catch (e: SecurityException) {
                Log.w(
                    TAG,
                    "Exact alarm for $label was refused between the permission check and the call; " +
                        "falling back to an inexact alarm.",
                    e
                )
            }
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            }
            Log.w(
                TAG,
                "$label armed with an INEXACT alarm (exact-alarm permission not granted): delivery " +
                    "may be later than the configured time."
            )
            ArmResult.InexactFallback
        } catch (e: SecurityException) {
            ArmResult.Failed("SecurityException: ${e.message}")
        } catch (e: IllegalStateException) {
            ArmResult.Failed("IllegalStateException: ${e.message}")
        }
    }
}
