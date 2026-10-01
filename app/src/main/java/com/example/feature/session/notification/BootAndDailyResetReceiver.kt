package com.example.feature.session.notification

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.SQLException
import android.util.Log
import com.example.FocusShieldApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Re-arms everything that a clock, calendar, power or permission change can invalidate.
 *
 * Triggers (see the manifest intent filter):
 *  - `BOOT_COMPLETED` — alarms do not survive a reboot;
 *  - `TIME_SET`, `TIMEZONE_CHANGED`, `DATE_CHANGED` — a wall-clock schedule is anchored to local
 *    time, so every stored time may now map to a different instant (and DST transitions change
 *    offsets without any user action);
 *  - `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` — the user granted or revoked "Alarms &
 *    reminders". Without handling this, schedules armed while the permission was missing would stay
 *    inexact until the next reboot, and schedules armed exactly would silently keep firing after a
 *    revocation (the platform cancels exact alarms on revocation, but the app would not know);
 *  - `ACTION_DAILY_RESET` — FocusShield's own midnight alarm (explicit intent only; sender is
 *    verified because the receiver itself is exported for the system broadcasts above).
 *
 * Work done: today's study-plan rows are ensured, study-plan reminders and the midnight reset are
 * re-armed, and every enabled automated focus schedule is re-planned through
 * [FocusScheduleAlarmScheduler] (which also *cancels* alarms for schedules that are disabled or no
 * longer valid, so this is a full reconcile rather than an append).
 */
class BootAndDailyResetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received action: $action")

        // The custom midnight action is sent via FocusShield's own explicit PendingIntent; it is
        // no longer declared in the manifest filter, but an exported receiver can still be targeted
        // directly — so require the per-install token that only our own PendingIntent carries
        // (issue #15).
        //
        // This used to read `sendingPackage`, which is not a member of BroadcastReceiver at all and
        // did not compile. The real API, `getSentFromPackage()`, is API 34+ (minSdk here is 24) and
        // returns null unless the sender opted into sharing its identity, so it could not have done
        // this job either.
        if (action == StudyPlanAlarmScheduler.ACTION_DAILY_RESET) {
            val presented = intent.getStringExtra(StudyPlanAlarmScheduler.EXTRA_RESET_TOKEN)
            if (presented != StudyPlanAlarmScheduler.dailyResetToken(context)) {
                Log.w(TAG, "Ignoring ACTION_DAILY_RESET without a valid token")
                return
            }
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            // Arming the *next* midnight alarm is the single most important thing this receiver
            // does: it is what brings every reminder back tomorrow. It used to run at step 3, after
            // the fallible `ensureTodayPlansExist()` DB call — so one locked or migrating database
            // killed every future reminder until the user happened to open the app (issue #28). It
            // is now armed before anything that can throw, and re-tried in the `finally` below if
            // that first attempt failed.
            var midnightArmed = StudyPlanAlarmScheduler.scheduleMidnightDailyReset(context)

            try {
                // `as?` first, with the static instance as a fallback: on BOOT_COMPLETED the
                // applicationContext is the Application, but a direct-boot or test harness may differ.
                val app = context.applicationContext as? FocusShieldApp ?: FocusShieldApp.instance

                if (action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) {
                    // Nothing else needs recomputing: only the exactness of already-planned alarms
                    // changed, so re-plan and re-arm.
                    Log.i(
                        TAG,
                        "Exact-alarm permission changed; re-arming schedules " +
                            "(exact=${FocusScheduleAlarmScheduler.canScheduleExactAlarms(context)})"
                    )
                    FocusScheduleAlarmScheduler.rescheduleAllSchedules(
                        context,
                        app.database.focusScheduleDao().getEnabledSchedules()
                    )
                    StudyPlanAlarmScheduler.schedulePlanReminders(
                        context,
                        app.studyPlanRepository.ensureTodayPlansExist()
                    )
                    TimeZoneOffsetGuard.record(context)
                    return@launch
                }

                // 1. Ensure today's study plans exist and are ready for the new day
                val todayPlans = app.studyPlanRepository.ensureTodayPlansExist()

                // 2. Reschedule alarms for today's study plans (this also re-arms midnight)
                StudyPlanAlarmScheduler.schedulePlanReminders(context, todayPlans)
                midnightArmed = true

                // 3. Reschedule all automated recurring focus schedules. Awaited, not fire-and-
                //    forget: this runs inside goAsync(), so a `launch` that outlived `finish()`
                //    could be killed with schedules left unarmed and its exception unobserved
                //    (issue #28).
                app.focusScheduleRepository.syncAllAlarms(context)

                // 4. Everything above was planned from the offset in force right now; record it so
                //    a later DST transition is detected by the receivers that fire next.
                TimeZoneOffsetGuard.record(context)

                // 5. Prune old app usage and session records (keep last 30 days)
                try {
                    val thirtyDaysAgo = java.time.LocalDate.now().minusDays(30)
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    app.database.dailyAppUsageDao().pruneOldRecords(thirtyDaysAgo)
                    app.database.appLimitSessionDao().pruneOldSessions(thirtyDaysAgo)
                } catch (e: SQLException) {
                    // Pruning is housekeeping; a locked/corrupt table must not stop alarm re-arming.
                    Log.w(TAG, "Pruning old usage rows failed: ${e.message}")
                }

                // 6. Refresh home screen widgets for the new day
                try {
                    com.example.feature.widgets.FocusShieldWidgetUpdater.updateAll(context, force = true)
                } catch (e: IllegalStateException) {
                    // WorkManager/Glance not initialised yet (can happen on early boot); the widgets
                    // catch up on the next session-state or usage emission.
                    Log.w(TAG, "Widget refresh skipped: ${e.message}")
                }

                // 7. Restore 18+ website blocking VPN engine if previously enabled
                if (action == Intent.ACTION_BOOT_COMPLETED) {
                    try {
                        com.example.feature.websiteblocker.vpn.AdultFilteringEngine.instance?.syncWithPreferences()
                    } catch (e: Exception) {
                        Log.w(TAG, "Restoring VPN filter skipped: ${e.message}")
                    }
                }

                Log.d(
                    TAG,
                    "Daily reset & alarm rescheduling completed. ${todayPlans.size} plans scheduled."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SQLException) {
                Log.e(TAG, "Database error during daily reset/alarm rescheduling", e)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "Application was not ready for daily reset/alarm rescheduling", e)
            } catch (e: RuntimeException) {
                // Boundary catch: a receiver that throws would take the process down, and this runs
                // unattended on boot / at midnight. Reported, never swallowed silently.
                Log.e(TAG, "Unexpected error during daily reset/alarm rescheduling", e)
            } finally {
                // Last line of defence. If the first attempt failed (no AlarmManager yet on early
                // boot, a SecurityException that has since been resolved) try once more before this
                // process is allowed to go away: without tomorrow's alarm the app has no way back.
                if (!midnightArmed) {
                    try {
                        midnightArmed = StudyPlanAlarmScheduler.scheduleMidnightDailyReset(context)
                    } catch (e: RuntimeException) {
                        Log.e(TAG, "Final midnight re-arm attempt failed", e)
                    }
                    if (!midnightArmed) {
                        Log.e(
                            TAG,
                            "Midnight daily reset is NOT armed; reminders resume on the next app " +
                                "launch or boot"
                        )
                    }
                }
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "DailyResetReceiver"
    }
}
