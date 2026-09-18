package com.example.feature.session.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.core.permission.FocusPermissionManager
import com.example.data.local.entity.FocusScheduleEntity
import java.util.TimeZone

/**
 * Arms the system alarm that fires one automated focus schedule.
 *
 * Correctness rules this class exists to enforce:
 *
 *  - **A schedule is only armed when it was understood.** The "when" decision lives in
 *    [ScheduleAlarmPlanner]; when it cannot be made (bad `startTime`, bad `daysOfWeek`, a one-time
 *    date in the past) the alarm for that schedule is *cancelled* and the reason is logged. Nothing
 *    is scheduled at a guessed time — an earlier revision silently defaulted a malformed time to
 *    09:00 and a malformed day list to Mon–Fri.
 *  - **No stale alarm survives a change.** Disabling, deleting, editing into an invalid state, or
 *    shortening a schedule always cancels first, so the old `PendingIntent` cannot fire later with
 *    the previous configuration. The request code is derived from the schedule id, so it is stable
 *    across edits.
 *  - **Exactness is a permission, not an assumption.** On Android 12+ the user can deny
 *    `SCHEDULE_EXACT_ALARM` (and on Android 14+ it is denied by default for most apps). The exact
 *    request is attempted only when [AlarmManager.canScheduleExactAlarms] says it can succeed; the
 *    inexact `setAndAllowWhileIdle` fallback is used otherwise and logged prominently, because that
 *    path means the session may start **later** than the configured minute — the user is told in the
 *    UI (see `PermissionItemCard` "Exact alarms" row) rather than the app pretending otherwise.
 *  - **Reboot / time-change / permission-grant re-arm** is handled by [BootAndDailyResetReceiver],
 *    which calls [rescheduleAllSchedules] for every action that can invalidate an armed alarm.
 */
object FocusScheduleAlarmScheduler {

    private const val TAG = "FocusScheduleScheduler"

    /** Reschedules alarms for all given schedules (arming the enabled ones, cancelling the rest). */
    fun rescheduleAllSchedules(context: Context, schedules: List<FocusScheduleEntity>) {
        for (schedule in schedules) {
            if (schedule.isEnabled) {
                scheduleSingleFocusSchedule(context, schedule)
            } else {
                cancelFocusSchedule(context, schedule.id)
            }
        }
    }

    /**
     * Plans and arms the next alarm for [schedule]. Safe to call for a disabled schedule: it cancels
     * instead of arming.
     */
    fun scheduleSingleFocusSchedule(context: Context, schedule: FocusScheduleEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        if (alarmManager == null) {
            Log.e(TAG, "AlarmManager unavailable; schedule '${schedule.title}' not armed")
            return
        }

        // Always drop the previous alarm first: an edit that shortens or invalidates the schedule
        // must not leave the old trigger behind.
        cancelFocusSchedule(context, schedule.id)

        when (val plan = ScheduleAlarmPlanner.plan(
            schedule = schedule,
            nowMillis = System.currentTimeMillis(),
            timeZone = TimeZone.getDefault()
        )) {
            is ScheduleAlarmPlanner.Plan.Skip -> {
                // Not an error for a disabled schedule; anything else is a configuration problem the
                // user needs to be able to see, so it is logged at warning level with the raw value.
                val level = if (plan.reason == ScheduleAlarmPlanner.Reason.DISABLED) Log.DEBUG else Log.WARN
                Log.println(level, TAG, "Not scheduling '${schedule.title}' (${schedule.id}): ${plan.detail}")
            }

            is ScheduleAlarmPlanner.Plan.Arm -> {
                if (plan.degradedReason != null) {
                    Log.w(TAG, "Scheduling '${schedule.title}' with a fallback: ${plan.degradedReason}")
                }
                val result = arm(
                    alarmManager = alarmManager,
                    context = context,
                    scheduleId = schedule.id,
                    label = schedule.title,
                    triggerAtMillis = plan.triggerAtMillis
                )
                Log.d(
                    TAG,
                    "Schedule '${schedule.title}' (${schedule.id}) -> $result at " +
                        "${plan.triggerAtMillis} (${ScheduleTime.format(
                            ScheduleTime.parseToMinutesOrNull(schedule.startTime) ?: 0
                        )} local)"
                )
            }
        }
    }

    /** True when the platform will honour an exact alarm request right now. */
    fun canScheduleExactAlarms(context: Context): Boolean =
        FocusPermissionManager.isExactAlarmGranted(context)

    // ---- internals --------------------------------------------------------------------

    /**
     * Arms the alarm through [ExactAlarmGate] and reports what actually happened, so the log line for
     * each schedule states whether the user can rely on the exact minute.
     */
    private fun arm(
        alarmManager: AlarmManager,
        context: Context,
        scheduleId: String,
        label: String,
        triggerAtMillis: Long
    ): ExactAlarmGate.ArmResult {
        val pendingIntent = pendingIntent(context, scheduleId, create = true)
            ?: return ExactAlarmGate.ArmResult.Failed("PendingIntent could not be created")
        return ExactAlarmGate.arm(
            context = context,
            alarmManager = alarmManager,
            triggerAtMillis = triggerAtMillis,
            operation = pendingIntent,
            label = "focus schedule '$label' ($scheduleId)"
        )
    }

    /** Cancels the alarm previously armed for [scheduleId]. Idempotent. */
    fun cancelFocusSchedule(context: Context, scheduleId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        // FLAG_NO_CREATE: cancelling a schedule that was never armed must not create a PendingIntent
        // (which would then be a live, if never-fired, registration for the process).
        val existing = pendingIntent(context, scheduleId, create = false) ?: return
        alarmManager.cancel(existing)
    }

    /**
     * The trigger intent for one schedule. `requestCode` is the schedule id's hash, so it is stable
     * for the lifetime of the row and unique per schedule (a collision would need two ids whose
     * `hashCode()` collide — the ids are UUID strings).
     */
    private fun pendingIntent(context: Context, scheduleId: String, create: Boolean): PendingIntent? {
        val intent = Intent(context, FocusScheduleReceiver::class.java).apply {
            action = FocusScheduleReceiver.ACTION_FOCUS_SCHEDULE
            putExtra(FocusScheduleReceiver.EXTRA_SCHEDULE_ID, scheduleId)
        }
        val flags = if (create) {
            PendingIntent.FLAG_UPDATE_CURRENT or immutabilityFlag()
        } else {
            PendingIntent.FLAG_NO_CREATE or immutabilityFlag()
        }
        return PendingIntent.getBroadcast(context, requestCodeFor(scheduleId), intent, flags)
    }

    private fun immutabilityFlag(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    /** Stable per-schedule request code shared by arm/cancel. */
    fun requestCodeFor(scheduleId: String): Int = ("schedule_" + scheduleId).hashCode()
}
