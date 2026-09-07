package com.example.feature.session.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.local.entity.FocusScheduleEntity
import java.util.Calendar

object FocusScheduleAlarmScheduler {

    private const val TAG = "FocusScheduleScheduler"

    /**
     * Reschedules system alarms for all given enabled focus schedules.
     */
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
     * Calculates the next upcoming epoch millis for a schedule based on its daysOfWeek and startTime.
     */
    fun calculateNextTriggerMillis(daysOfWeekStr: String, startTimeStr: String): Long {
        val now = Calendar.getInstance()
        val nowMillis = now.timeInMillis

        val parts = startTimeStr.trim().split(":")
        val targetHour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val targetMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        // Parse target days of week (1=SUN, 2=MON, ..., 7=SAT)
        val selectedDays = parseDaysOfWeek(daysOfWeekStr)

        var bestMillis: Long? = null

        // Check the next 14 days to find the earliest matching day & time in the future
        for (dayOffset in 0..14) {
            val candidate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, targetHour)
                set(Calendar.MINUTE, targetMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
            if (selectedDays.contains(dayOfWeek)) {
                if (candidate.timeInMillis > nowMillis + 2000L) { // At least 2 sec in future
                    if (bestMillis == null || candidate.timeInMillis < bestMillis) {
                        bestMillis = candidate.timeInMillis
                    }
                }
            }
        }

        return bestMillis ?: (nowMillis + 24 * 60 * 60 * 1000L)
    }

    private fun parseDaysOfWeek(daysStr: String): Set<Int> {
        val uppercase = daysStr.uppercase()
        val set = mutableSetOf<Int>()

        if (uppercase.contains("MON") || uppercase.contains("2")) set.add(Calendar.MONDAY)
        if (uppercase.contains("TUE") || uppercase.contains("3")) set.add(Calendar.TUESDAY)
        if (uppercase.contains("WED") || uppercase.contains("4")) set.add(Calendar.WEDNESDAY)
        if (uppercase.contains("THU") || uppercase.contains("5")) set.add(Calendar.THURSDAY)
        if (uppercase.contains("FRI") || uppercase.contains("6")) set.add(Calendar.FRIDAY)
        if (uppercase.contains("SAT") || uppercase.contains("7")) set.add(Calendar.SATURDAY)
        if (uppercase.contains("SUN") || uppercase.contains("1")) set.add(Calendar.SUNDAY)

        if (set.isEmpty()) {
            // Default to Mon-Fri if empty
            return setOf(Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY, Calendar.THURSDAY, Calendar.FRIDAY)
        }
        return set
    }

    fun scheduleSingleFocusSchedule(context: Context, schedule: FocusScheduleEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        if (!schedule.isEnabled) {
            cancelFocusSchedule(context, schedule.id)
            return
        }

        val triggerMillis = if (!schedule.repeatEnabled && schedule.scheduledDateMillis > 0L) {
            calculateOneTimeTriggerMillis(schedule.scheduledDateMillis, schedule.startTime)
        } else {
            calculateNextTriggerMillis(schedule.daysOfWeek, schedule.startTime)
        }

        if (triggerMillis <= System.currentTimeMillis() + 1000L) {
            Log.d(TAG, "Skipping past one-time schedule '${schedule.title}'")
            return
        }

        val intent = Intent(context, FocusScheduleReceiver::class.java).apply {
            action = "com.example.focusshield.ACTION_FOCUS_SCHEDULE"
            putExtra(FocusScheduleReceiver.EXTRA_SCHEDULE_ID, schedule.id)
        }

        val requestCode = ("schedule_" + schedule.id).hashCode()
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerMillis,
                        pendingIntent
                    )
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled focus schedule '${schedule.title}' for $triggerMillis")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule alarm for focus schedule ${schedule.id}", e)
        }
    }

    /** Calculates the exact trigger for a one-time schedule date + local clock time. */
    private fun calculateOneTimeTriggerMillis(dateMillis: Long, startTimeStr: String): Long {
        val source = Calendar.getInstance().apply { timeInMillis = dateMillis }
        val parts = startTimeStr.trim().split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return Calendar.getInstance().apply {
            set(Calendar.YEAR, source.get(Calendar.YEAR))
            set(Calendar.MONTH, source.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, source.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun cancelFocusSchedule(context: Context, scheduleId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, FocusScheduleReceiver::class.java).apply {
            action = "com.example.focusshield.ACTION_FOCUS_SCHEDULE"
        }
        val requestCode = ("schedule_" + scheduleId).hashCode()
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)
        alarmManager.cancel(pendingIntent)
    }
}
