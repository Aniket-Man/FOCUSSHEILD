package com.example.feature.session.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.FocusShieldApp
import com.example.data.model.SessionMode
import com.example.feature.session.engine.FocusSessionManager
import com.example.feature.session.service.FocusSessionForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FocusScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID) ?: return

        Log.d("FocusScheduleReceiver", "Received alarm action=$action for scheduleId=$scheduleId")

        val app = context.applicationContext as? FocusShieldApp ?: return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = app.database
                val schedule = db.focusScheduleDao().getScheduleById(scheduleId)
                if (schedule != null && schedule.isEnabled) {

                    // Notify user via high-priority notification channel
                    SessionNotificationHelper.showScheduleStartNotification(
                        context = context,
                        schedule = schedule
                    )

                    // If Auto-Start is enabled and no session is currently active, automatically launch focus session
                    if (schedule.isAutoStartSession) {
                        val sessionManager = FocusSessionManager.instance
                        val active = sessionManager.activeSession.value
                        if (active == null || active.isCompleted || active.isCancelled) {
                            val startMins = parseTimeToMinutes(schedule.startTime)
                            val endMins = parseTimeToMinutes(schedule.endTime)
                            var durationMins = endMins - startMins
                            if (durationMins <= 0) durationMins += 24 * 60

                            val sessionMode = when (schedule.mode.uppercase()) {
                                "POMODORO" -> SessionMode.POMODORO
                                "STOPWATCH" -> SessionMode.STOPWATCH
                                else -> SessionMode.TIMER
                            }

                            val blockedAppsSet = schedule.blockedAppPackages
                                .split(",")
                                .map { it.trim() }
                                .filter { it.isNotEmpty() }
                                .toSet()

                            sessionManager.startSession(
                                mode = sessionMode,
                                subject = schedule.subjectName.ifBlank { schedule.title },
                                topic = schedule.description.ifBlank { "Automated Schedule: ${schedule.title}" },
                                goal = schedule.description.ifBlank { "Automated Focus Window (${schedule.startTime} - ${schedule.endTime})" },
                                plannedDurationMillis = durationMins * 60 * 1000L,
                                isAppBlocking = true,
                                isStrictMode = false,
                                isStudyChannels = true,
                                blockedAppPackages = blockedAppsSet,
                                blockNotifications = schedule.blockNotifications,
                                defaultBreakMinutes = schedule.breakMinutes
                            )

                            FocusSessionForegroundService.start(context)
                            Log.d("FocusScheduleReceiver", "Auto-started focus session for schedule ${schedule.title}")
                        }
                    }

                    // One-time schedules fire once; repeating schedules are scheduled for their next occurrence.
                    if (schedule.repeatEnabled) {
                        FocusScheduleAlarmScheduler.scheduleSingleFocusSchedule(context, schedule)
                    } else {
                        db.focusScheduleDao().setScheduleEnabled(schedule.id, false)
                    }
                }
            } catch (e: Exception) {
                Log.e("FocusScheduleReceiver", "Error handling schedule broadcast: ${e.message}", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun parseTimeToMinutes(timeStr: String): Int {
        return try {
            val parts = timeStr.trim().split(":")
            parts[0].toInt() * 60 + parts[1].toInt()
        } catch (e: Exception) {
            9 * 60 // 09:00 default
        }
    }

    companion object {
        const val EXTRA_SCHEDULE_ID = "extra_focus_schedule_id"
    }
}
