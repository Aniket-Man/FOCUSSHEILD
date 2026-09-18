package com.example.feature.session.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.util.Log
import com.example.FocusShieldApp
import com.example.feature.session.engine.FocusSessionManager
import com.example.feature.session.service.FocusSessionForegroundService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives the alarm armed by [FocusScheduleAlarmScheduler] for one automated schedule.
 *
 * Responsibilities, in order:
 *  1. look the schedule up and confirm it is still enabled (the alarm may be a stale survivor of an
 *     edit or a disable that happened while the device was off);
 *  2. show the "scheduled session starting" notification;
 *  3. if the schedule auto-starts, build the session from it via [ScheduledSessionFactory] and start
 *     it — **only** when that factory accepted the configuration;
 *  4. re-arm the schedule for its next occurrence (repeating) or disable it (one-time).
 *
 * Failure discipline: nothing here invents a value. A schedule whose times or mode cannot be parsed
 * is logged and skipped, never started at a substituted time (see [ScheduledSessionFactory]).
 */
class FocusScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != ACTION_FOCUS_SCHEDULE) {
            Log.d(TAG, "Ignoring unsupported action=$action")
            return
        }
        val scheduleId = intent.getStringExtra(EXTRA_SCHEDULE_ID)
        if (scheduleId.isNullOrBlank()) {
            Log.w(TAG, "Received $ACTION_FOCUS_SCHEDULE without $EXTRA_SCHEDULE_ID")
            return
        }

        Log.d(TAG, "Received alarm for scheduleId=$scheduleId")

        val app = context.applicationContext as? FocusShieldApp
        if (app == null) {
            Log.e(TAG, "Application is not FocusShieldApp; cannot resolve schedule $scheduleId")
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                handle(app, context.applicationContext, scheduleId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: SQLException) {
                // Room/sqlite failure: the schedule could not be read. Nothing to do but report it;
                // the next boot/time-change broadcast re-arms whatever is still valid.
                Log.e(TAG, "Database error while handling schedule $scheduleId", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Application not ready while handling schedule $scheduleId", e)
            } catch (e: RuntimeException) {
                // Boundary catch: a BroadcastReceiver that throws takes the process down, and this
                // path runs while a study session is supposed to start. Fail loudly in the log.
                Log.e(TAG, "Unexpected error handling schedule $scheduleId", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handle(app: FocusShieldApp, context: Context, scheduleId: String) {
        val db = app.database
        val schedule = db.focusScheduleDao().getScheduleById(scheduleId)

        if (schedule == null) {
            Log.w(TAG, "Schedule $scheduleId no longer exists; dropping its alarm")
            FocusScheduleAlarmScheduler.cancelFocusSchedule(context, scheduleId)
            return
        }
        if (!schedule.isEnabled) {
            Log.d(TAG, "Schedule $scheduleId is disabled; dropping its alarm")
            FocusScheduleAlarmScheduler.cancelFocusSchedule(context, scheduleId)
            return
        }

        SessionNotificationHelper.showScheduleStartNotification(context = context, schedule = schedule)

        if (schedule.isAutoStartSession) {
            val sessionManager = FocusSessionManager.instance
            val active = sessionManager.activeSession.value
            if (active == null || active.isCompleted || active.isCancelled) {
                when (val draft = ScheduledSessionFactory.draftFor(schedule)) {
                    is ScheduledSessionFactory.Result.Rejected -> {
                        // Deliberately not started. The user configured something the app cannot
                        // honour; guessing (09:00 / TIMER / a 24-hour window) is worse than not
                        // starting, so the schedule is reported and left untouched.
                        Log.w(
                            TAG,
                            "Not auto-starting schedule '${schedule.title}' ($scheduleId): " +
                                draft.reason
                        )
                    }

                    is ScheduledSessionFactory.Result.Ready -> {
                        val started = sessionManager.startSession(
                            mode = draft.draft.mode,
                            subject = draft.draft.subject,
                            topic = draft.draft.topic,
                            goal = draft.draft.goal,
                            plannedDurationMillis = draft.draft.plannedDurationMillis,
                            isAppBlocking = true,
                            isStrictMode = false,
                            isStudyChannels = true,
                            blockedAppPackages = draft.draft.blockedAppPackages,
                            blockNotifications = draft.draft.blockNotifications,
                            defaultBreakMinutes = draft.draft.breakMinutes
                        )
                        if (started.isSuccess) {
                            FocusSessionForegroundService.start(context)
                            Log.d(
                                TAG,
                                "Auto-started ${draft.draft.mode} session for schedule " +
                                    "'${schedule.title}' (${draft.draft.plannedDurationMillis / 60_000} min)"
                            )
                        } else {
                            Log.e(
                                TAG,
                                "Session manager refused schedule '${schedule.title}': " +
                                    "${started.exceptionOrNull()?.message}"
                            )
                        }
                    }
                }
            } else {
                Log.d(TAG, "A session is already active; not auto-starting '${schedule.title}'")
            }
        }

        if (schedule.repeatEnabled) {
            FocusScheduleAlarmScheduler.scheduleSingleFocusSchedule(context, schedule)
        } else {
            // One-time schedule: it has fired, so it is switched off. Re-enabling it (or editing the
            // date) re-arms it; the explicit disable also keeps the list UI honest.
            db.focusScheduleDao().setScheduleEnabled(schedule.id, false)
            Log.d(TAG, "One-time schedule '${schedule.title}' fired and has been disabled")
        }
    }

    companion object {
        /**
         * Must match `FocusScheduleReceiver`'s `<intent-filter>` action in AndroidManifest.xml.
         * Kept as a constant so the scheduler and receiver can never drift apart by typo.
         */
        const val ACTION_FOCUS_SCHEDULE = "com.example.focusshield.ACTION_FOCUS_SCHEDULE"

        const val EXTRA_SCHEDULE_ID = "extra_focus_schedule_id"

        private const val TAG = "FocusScheduleReceiver"
    }
}
