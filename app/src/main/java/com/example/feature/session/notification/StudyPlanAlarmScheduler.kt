package com.example.feature.session.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.data.local.entity.StudyPlanEntity
import java.util.Calendar

/**
 * Robust Scheduler for Today's Study Plan Reminders and Midnight Daily Session Reset.
 * Schedules exact/inexact alarms that fire at the planned start time of each scheduled subject,
 * notifying the user with rich details and a direct-to-session action.
 */
object StudyPlanAlarmScheduler {

    private const val TAG = "StudyPlanScheduler"

    const val ACTION_STUDY_PLAN_REMINDER = "com.example.focusshield.ACTION_STUDY_PLAN_REMINDER"
    const val ACTION_OPEN_PLAN_SESSION = "com.example.focusshield.ACTION_OPEN_PLAN_SESSION"
    const val ACTION_DAILY_RESET = "com.example.focusshield.ACTION_DAILY_RESET"

    const val EXTRA_PLAN_ID = "extra_plan_id"
    const val EXTRA_SUBJECT = "extra_subject"
    const val EXTRA_TOPIC = "extra_topic"
    const val EXTRA_START_TIME = "extra_start_time"
    const val EXTRA_END_TIME = "extra_end_time"
    const val EXTRA_DURATION_MINUTES = "extra_duration_minutes"
    const val EXTRA_NOTES = "extra_notes"
    const val EXTRA_AUTO_START = "extra_auto_start"
    const val EXTRA_START_PLAN_SESSION = "extra_start_plan_session"

    private const val REQUEST_CODE_DAILY_RESET = 99999

    /**
     * Schedules alarms for all given study plan items that occur in the future today.
     */
    fun schedulePlanReminders(context: Context, plans: List<StudyPlanEntity>) {
        for (plan in plans) {
            scheduleSinglePlanReminder(context, plan)
        }
        scheduleMidnightDailyReset(context)
    }

    /**
     * Schedules a single alarm reminder for a specific study plan item at its start time.
     */
    fun scheduleSinglePlanReminder(context: Context, plan: StudyPlanEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        // If plan is already completed, do not trigger an alarm
        if (plan.isCompleted) {
            cancelPlanReminder(context, plan.id)
            return
        }

        val triggerMillis = calculateTriggerMillis(plan.targetDate, plan.startTime)
        if (triggerMillis == null) {
            // `startTime` is not a valid HH:mm clock time. Guessing a default here (the old code used
            // 08:00) would fire a reminder at a time the plan does not say, so the plan is skipped and
            // the invalid value is reported instead. Fixing the plan re-arms it.
            Log.w(
                TAG,
                "Plan '${plan.subjectName}' (${plan.id}) has an invalid startTime " +
                    "'${plan.startTime}'; no reminder scheduled."
            )
            cancelPlanReminder(context, plan.id)
            return
        }

        val now = System.currentTimeMillis()
        if (triggerMillis <= now) {
            // Today's slot has passed: arm nothing, and drop any earlier alarm so an edited plan
            // cannot leave a stale reminder behind.
            Log.d(TAG, "Skipping past plan: ${plan.subjectName} at ${plan.startTime}")
            cancelPlanReminder(context, plan.id)
            return
        }

        val intent = Intent(context, StudyPlanReminderReceiver::class.java).apply {
            action = ACTION_STUDY_PLAN_REMINDER
            putExtra(EXTRA_PLAN_ID, plan.id)
            putExtra(EXTRA_SUBJECT, plan.subjectName)
            putExtra(EXTRA_TOPIC, plan.topicName)
            putExtra(EXTRA_START_TIME, plan.startTime)
            putExtra(EXTRA_END_TIME, plan.endTime)
            putExtra(EXTRA_DURATION_MINUTES, plan.plannedDurationMinutes)
            putExtra(EXTRA_NOTES, plan.notes)
        }

        val requestCode = plan.id.hashCode()
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)

        // Shared with FocusScheduleAlarmScheduler: exact when the platform allows it, a logged
        // inexact fallback otherwise, never a silent success. See ExactAlarmGate.
        when (
            val result = ExactAlarmGate.arm(
                context = context,
                alarmManager = alarmManager,
                triggerAtMillis = triggerMillis,
                operation = pendingIntent,
                label = "study-plan reminder for '${plan.subjectName}'"
            )
        ) {
            is ExactAlarmGate.ArmResult.Failed ->
                Log.e(TAG, "Failed to schedule alarm for plan ${plan.id}: ${result.reason}")
            else ->
                Log.d(TAG, "Reminder for ${plan.subjectName} at $triggerMillis (${plan.startTime}) -> $result")
        }
    }

    /**
     * Cancels an existing scheduled reminder for a plan ID.
     */
    fun cancelPlanReminder(context: Context, planId: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, StudyPlanReminderReceiver::class.java).apply {
            action = ACTION_STUDY_PLAN_REMINDER
        }
        val requestCode = planId.hashCode()
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent, flags)
        alarmManager.cancel(pendingIntent)
    }

    /**
     * Schedules an alarm to fire right after midnight (00:00:05) every day
     * to execute daily session resets, prepare today's fresh plan, and reschedule notifications.
     */
    fun scheduleMidnightDailyReset(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

        val calendar = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }

        val triggerMillis = calendar.timeInMillis

        val intent = Intent(context, BootAndDailyResetReceiver::class.java).apply {
            action = ACTION_DAILY_RESET
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(context, REQUEST_CODE_DAILY_RESET, intent, flags)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
            Log.d(TAG, "Scheduled Midnight Daily Reset at $triggerMillis")
        } catch (e: SecurityException) {
            // No alarm permission on this build/OEM: the reset also runs on the next launch
            // (FocusShieldApp seeds today's plans), so this is reported rather than fatal.
            Log.e(TAG, "Midnight daily reset could not be armed (permission): ${e.message}")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Midnight daily reset could not be armed: ${e.message}")
        }
    }

    /**
     * Epoch millis of [startTime] on the calendar date of [targetDate], or null when [startTime] is
     * not a valid `HH:mm` clock time.
     *
     * Returning null (instead of the previous silent 08:00 fallback) is what lets the caller skip an
     * unusable plan and say so, rather than reminding the student at a time the plan never specified.
     * Parsing is delegated to [ScheduleTime], the single strict parser shared with the automated
     * schedule feature.
     */
    fun calculateTriggerMillis(targetDate: Long, startTime: String): Long? {
        val startMins = ScheduleTime.parseToMinutesOrNull(startTime) ?: return null
        return Calendar.getInstance().apply {
            timeInMillis = targetDate
            set(Calendar.HOUR_OF_DAY, startMins / 60)
            set(Calendar.MINUTE, startMins % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}
