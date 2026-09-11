package com.example.feature.session.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.FocusShieldApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver responsible for executing the Daily Session Reset and rescheduling
 * all study plan timing alarms on device reboot, timezone change, date change, or midnight alarm.
 *
 * Ensures:
 * 1. Today's sessions start fresh each day while all historical sessions stay safely stored in Room.
 * 2. Today's study plan checklist is clean and ready.
 * 3. Exact alarms for all upcoming study timings for today are active.
 */
class BootAndDailyResetReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "BootAndDailyResetReceiver received action: $action")

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as? FocusShieldApp ?: FocusShieldApp.instance
                
                // 1. Ensure today's study plans exist and are ready for the new day
                val todayPlans = app.studyPlanRepository.ensureTodayPlansExist()

                // 2. Reschedule alarms for today's study plans
                StudyPlanAlarmScheduler.schedulePlanReminders(context, todayPlans)

                // 3. Schedule next midnight reset
                StudyPlanAlarmScheduler.scheduleMidnightDailyReset(context)

                // 4. Reschedule all automated recurring focus schedules
                app.focusScheduleRepository.syncAllAlarms(context)

                // 5. Prune old app usage and session records (keep last 30 days)
                try {
                    val thirtyDaysAgo = java.time.LocalDate.now().minusDays(30).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                    app.database.dailyAppUsageDao().pruneOldRecords(thirtyDaysAgo)
                    app.database.appLimitSessionDao().pruneOldSessions(thirtyDaysAgo)
                } catch (_: Exception) {
                }

                // 6. Refresh home screen widgets for the new day
                try {
                    com.example.feature.widgets.FocusShieldWidgetUpdater.updateAll(context, force = true)
                } catch (_: Exception) {
                }

                Log.d(TAG, "Daily reset & alarm rescheduling completed successfully. ${todayPlans.size} plans scheduled.")
            } catch (e: Exception) {
                Log.e(TAG, "Error executing daily reset/alarm rescheduling", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        private const val TAG = "DailyResetReceiver"
    }
}
