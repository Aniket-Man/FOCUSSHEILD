package com.example.feature.session.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.FocusShieldApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver triggered by AlarmManager when a scheduled Study Plan start time is reached.
 * Automatically posts a high-priority system notification with the subject, topic, and timings,
 * allowing the user to tap and jump straight into the study session.
 */
class StudyPlanReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "StudyPlanReminderReceiver received action: $action")

        if (action == StudyPlanAlarmScheduler.ACTION_STUDY_PLAN_REMINDER) {
            val planId = intent.getStringExtra(StudyPlanAlarmScheduler.EXTRA_PLAN_ID)
            val subject = intent.getStringExtra(StudyPlanAlarmScheduler.EXTRA_SUBJECT) ?: "Study Session"
            val topic = intent.getStringExtra(StudyPlanAlarmScheduler.EXTRA_TOPIC) ?: "Scheduled Focus Block"
            val startTime = intent.getStringExtra(StudyPlanAlarmScheduler.EXTRA_START_TIME) ?: "Now"
            val endTime = intent.getStringExtra(StudyPlanAlarmScheduler.EXTRA_END_TIME) ?: ""
            val durationMinutes = intent.getIntExtra(StudyPlanAlarmScheduler.EXTRA_DURATION_MINUTES, 60)
            val notes = intent.getStringExtra(StudyPlanAlarmScheduler.EXTRA_NOTES) ?: ""

            Log.d(TAG, "Posting study plan reminder notification for $subject ($topic) at $startTime")

            SessionNotificationHelper.notifyStudyPlanTiming(
                context = context,
                planId = planId,
                subject = subject,
                topic = topic,
                startTime = startTime,
                endTime = endTime,
                durationMinutes = durationMinutes,
                notes = notes
            )

            rearmIfOffsetShifted(context)
        }
    }

    /**
     * A daylight-saving transition changes the device's UTC offset without sending any broadcast,
     * so every reminder armed before it fires an hour off for the rest of the day. This receiver is
     * one of the few things guaranteed to run during that day, so it re-checks the offset and
     * re-plans the remaining alarms from the new one when it has moved (issue #28).
     */
    private fun rearmIfOffsetShifted(context: Context) {
        if (!TimeZoneOffsetGuard.refreshAndDetectShift(context)) return

        val app = context.applicationContext as? FocusShieldApp
        if (app == null) {
            Log.w(TAG, "UTC offset moved but the application is not available; cannot re-plan")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "UTC offset moved; re-planning today's reminders and schedules")
                StudyPlanAlarmScheduler.schedulePlanReminders(
                    context,
                    app.studyPlanRepository.ensureTodayPlansExist()
                )
                app.focusScheduleRepository.syncAllAlarms(context)
                TimeZoneOffsetGuard.record(context)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                // Boundary catch: a receiver that throws takes the process down, and the reminder
                // the user actually asked for has already been posted above.
                Log.e(TAG, "Could not re-plan alarms after the UTC offset moved", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "StudyPlanReminder"
    }
}
