package com.example.feature.session.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

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
        }
    }

    companion object {
        private const val TAG = "StudyPlanReminder"
    }
}
