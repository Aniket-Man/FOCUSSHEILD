package com.example.feature.session.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.util.Log
import com.example.FocusShieldApp
import com.example.data.local.entity.FocusScheduleEntity
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

        val nowMillis = System.currentTimeMillis()
        val windowBounds = ScheduleValidation.activeWindowBoundsMillis(schedule, nowMillis)
            ?: ScheduleValidation.resolveWindowBoundsForDate(schedule, nowMillis)
        val slotIdentity = "${windowBounds?.startMillis ?: 0L}_${schedule.startTime.trim()}_${schedule.endTime.trim()}"

        if (!markSlotFiredIfNotAlready(context, schedule.id, slotIdentity)) {
            Log.d(TAG, "Schedule '${schedule.title}' (${schedule.id}) already fired for window $slotIdentity; skipping duplicate trigger")
            return
        }

        // Re-arming is done in a `finally` around everything that can throw below. Previously the
        // re-arm was the last statement on the success path, so a notification that could not be
        // posted or a ForegroundServiceStartNotAllowedException left the schedule with no next
        // alarm at all — a recurring schedule silently became a one-off (issue #28).
        try {
            runSchedule(app, context, schedule, scheduleId, nowMillis)
        } finally {
            if (schedule.repeatEnabled) {
                try {
                    FocusScheduleAlarmScheduler.scheduleSingleFocusSchedule(context, schedule)
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Could not re-arm repeating schedule '${schedule.title}'", e)
                }
            } else {
                // One-time schedule: cancel its alarm immediately, and once its active window is over,
                // mark it disabled in Room so it stays locked/active during the window and turns off after.
                try {
                    FocusScheduleAlarmScheduler.cancelFocusSchedule(context, schedule.id)
                    if (!ScheduleValidation.isCurrentlyActiveWindow(schedule, System.currentTimeMillis())) {
                        db.focusScheduleDao().setScheduleEnabled(schedule.id, false)
                        Log.d(TAG, "One-time schedule '${schedule.title}' window finished and has been disabled")
                    }
                } catch (e: SQLException) {
                    Log.e(TAG, "Could not update fired one-time schedule '${schedule.title}'", e)
                }
            }
        }
    }

    /** The fallible part: notify, and auto-start when the schedule asks for it. */
    private suspend fun runSchedule(
        app: FocusShieldApp,
        context: Context,
        schedule: FocusScheduleEntity,
        scheduleId: String,
        nowMillis: Long = System.currentTimeMillis()
    ) {
        // A DST transition moves the UTC offset without any broadcast an app can hear, so every
        // absolute alarm armed before it is now an hour out. Detect it here and re-plan everything
        // from the new offset (issue #28).
        if (TimeZoneOffsetGuard.refreshAndDetectShift(context)) {
            Log.i(TAG, "UTC offset changed since these alarms were armed; re-planning all schedules")
            try {
                app.focusScheduleRepository.syncAllAlarms(context)
                StudyPlanAlarmScheduler.schedulePlanReminders(
                    context,
                    app.studyPlanRepository.ensureTodayPlansExist()
                )
                TimeZoneOffsetGuard.record(context)
            } catch (e: SQLException) {
                Log.e(TAG, "Could not re-plan alarms after the offset change", e)
            }
        }

        SessionNotificationHelper.showScheduleStartNotification(context = context, schedule = schedule)

        if (schedule.isAutoStartSession) {
            val sessionManager = FocusSessionManager.instance
            val active = sessionManager.activeSession.value
            if (active == null || active.isCompleted || active.isCancelled) {
                when (val draft = ScheduledSessionFactory.draftFor(schedule, nowMillis = nowMillis)) {
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
                        val prefs = app.preferencesRepository.snapshot()
                        val effectiveBlockedPackages = draft.draft.blockedAppPackages.ifEmpty {
                            setOf(
                                "com.google.android.youtube",
                                "com.android.chrome",
                                "com.opera.browser",
                                "com.brave.browser",
                                "com.instagram.android"
                            )
                        }
                        val isStrict = prefs.isStrictModeDefault &&
                            draft.draft.mode != com.example.data.model.SessionMode.STOPWATCH
                        val started = sessionManager.startSession(
                            mode = draft.draft.mode,
                            subject = draft.draft.subject,
                            topic = draft.draft.topic,
                            goal = draft.draft.goal,
                            plannedDurationMillis = draft.draft.plannedDurationMillis,
                            isAppBlocking = prefs.isAppBlockingDefault,
                            isStrictMode = isStrict,
                            isStudyChannels = prefs.isStudyChannelsDefault,
                            isBrowserStudyMode = prefs.isManualWebsiteBlockingEnabled,
                            blockedAppPackages = effectiveBlockedPackages,
                            blockNotifications = draft.draft.blockNotifications || prefs.isBlockNotificationsEnabled,
                            defaultBreakMinutes = draft.draft.breakMinutes,
                            scheduledEndTimeMillis = draft.draft.scheduledEndTimeMillis
                        )
                        if (started.isSuccess) {
                            val startedSession = started.getOrNull()
                            if (isStrict && startedSession != null) {
                                com.example.feature.session.engine.StrictModeEngine.instance.startStrictMode(
                                    context = context,
                                    sessionId = startedSession.id,
                                    mode = draft.draft.mode,
                                    durationMillis = startedSession.plannedDurationMillis
                                )
                            }
                            sessionManager.saveActiveSessionToDisk(context)
                            FocusSessionForegroundService.start(context)
                            if (prefs.isFloatingTimerOverlayEnabled || isStrict) {
                                com.example.feature.timer.FloatingTimerService.start(
                                    context,
                                    forceShow = isStrict
                                )
                            }
                            Log.d(
                                TAG,
                                "Auto-started ${draft.draft.mode} session for schedule " +
                                    "'${schedule.title}' (remaining=${draft.draft.plannedDurationMillis / 60_000} min, " +
                                    "endsAt=${draft.draft.scheduledEndTimeMillis})"
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
                Log.w(
                    TAG,
                    "A ${active.state} session is already active; scheduled window " +
                        "'${schedule.title}' ($scheduleId) will not auto-start"
                )
            }
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
        private const val DEDUP_PREFS = "focus_schedule_autostart_dedup"

        @Synchronized
        private fun markSlotFiredIfNotAlready(
            context: Context,
            scheduleId: String,
            slotIdentity: String
        ): Boolean {
            val prefs = context.getSharedPreferences(DEDUP_PREFS, Context.MODE_PRIVATE)
            val key = "last_fired_$scheduleId"
            if (prefs.getString(key, null) == slotIdentity) {
                return false
            }
            prefs.edit().putString(key, slotIdentity).apply()
            return true
        }

        /**
         * Clears the deduplication slot for [scheduleId] when the user explicitly creates, edits,
         * or re-enables a schedule so an active window can immediately start.
         */
        @Synchronized
        fun clearFiredSlot(context: Context, scheduleId: String) {
            try {
                context.getSharedPreferences(DEDUP_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .remove("last_fired_$scheduleId")
                    .apply()
            } catch (_: Exception) {
            }
        }

        /**
         * In-process schedule watchdog invoked periodically while the app process is alive and
         * immediately whenever a schedule is created, edited, or enabled.
         *
         *  1. Enforces exact scheduled end-time cutoff on any active scheduled session.
         *  2. Disables any one-time schedule whose active window has finished.
         *  3. Immediately triggers any enabled schedule whose `[startTime, endTime)` window
         *     currently contains `nowMillis` (auto-starting for the exact remaining time until `endTime`).
         */
        suspend fun evaluateDueSchedulesInProcess(app: FocusShieldApp, context: Context) {
            try {
                val nowMillis = System.currentTimeMillis()

                // 1. Enforce wall-clock end cutoff on any running/paused scheduled session
                val activeSession = FocusSessionManager.instance.activeSession.value
                if (activeSession != null &&
                    !activeSession.isCompleted &&
                    !activeSession.isCancelled &&
                    activeSession.scheduledEndTimeMillis > 0L &&
                    nowMillis >= activeSession.scheduledEndTimeMillis
                ) {
                    FocusSessionManager.instance.syncStateFromEngines(nowMillis)
                }

                val enabledSchedules = app.database.focusScheduleDao().getEnabledSchedules()
                if (enabledSchedules.isEmpty()) return

                val receiver = FocusScheduleReceiver()
                for (schedule in enabledSchedules) {
                    // 2. If a one-time schedule's window has completely passed, disable it cleanly
                    if (!schedule.repeatEnabled && schedule.scheduledDateMillis > 0L) {
                        val oneTimeBounds = ScheduleValidation.resolveWindowBoundsForDate(
                            schedule = schedule,
                            referenceDateMillis = schedule.scheduledDateMillis
                        )
                        if (oneTimeBounds != null && nowMillis >= oneTimeBounds.endMillis) {
                            app.database.focusScheduleDao().setScheduleEnabled(schedule.id, false)
                            FocusScheduleAlarmScheduler.cancelFocusSchedule(context, schedule.id)
                            continue
                        }
                    }

                    // 3. If current time is inside this schedule's active [startTime, endTime) window,
                    // trigger it immediately (deduplicated per window occurrence).
                    if (ScheduleValidation.activeWindowBoundsMillis(schedule, nowMillis) != null) {
                        receiver.handle(app, context.applicationContext, schedule.id)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in evaluateDueSchedulesInProcess: ${e.message}")
            }
        }
    }
}
