package com.example.feature.update.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.FocusShieldApp
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

/**
 * Periodic "is there a newer release?" check.
 *
 * The other triggers (launch, resume, offline → online) all need the user to actually open the app,
 * so a release published while FocusShield stays closed would go unnoticed for as long as the user
 * doesn't return. This worker closes that gap: WorkManager wakes the process on its own schedule,
 * and the existing 6-hour cooldown still decides whether a network call is actually due.
 *
 * It deliberately implements no checking logic of its own — it delegates to the same
 * [com.example.feature.update.engine.UpdateManager] the UI reads, so there is still exactly one
 * update state in the app (prompt.txt §19). It also touches nothing outside the update feature, so
 * it cannot interfere with sessions, blocking or timers (prompt.txt §17).
 */
class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        // WorkManager runs this in our own process, so FocusShieldApp and the notification channel
        // are already initialised by the time we get here.
        val app = applicationContext as? FocusShieldApp ?: return Result.success()

        return try {
            app.updateManager.runBackgroundCheck()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed check stays silent and must not retry-storm GitHub (prompt.txt §14/§16). The
            // manager keeps whatever it already knew; the next period simply tries again.
            Result.success()
        }
    }
}

/**
 * Owns the single periodic update-check job.
 *
 * Exactly one job exists app-wide, which is what keeps "no notification spam" true even when the
 * process is started repeatedly (prompt.txt §14): the unique name plus [ExistingPeriodicWorkPolicy.KEEP]
 * mean re-scheduling on every launch is a no-op rather than a way to accumulate workers.
 */
object UpdateCheckScheduler {

    private const val UNIQUE_WORK_NAME = "focusshield_update_check"

    /**
     * Matches [com.example.feature.update.data.UpdateChecker.AUTO_CHECK_INTERVAL] so the worker and
     * the in-app cooldown agree on what "due" means. WorkManager's own batching and Doze make the
     * real cadence looser than this, which is intended — this is a background safety net, not a poll.
     */
    private const val PERIOD_HOURS = 6L

    /** Idempotent, so it is safe to call on every process start. */
    fun schedule(context: Context) {
        try {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(PERIOD_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        } catch (e: Throwable) {
            android.util.Log.w("UpdateCheckScheduler", "WorkManager unavailable or not initialized: ${e.message}")
        }
    }
}
