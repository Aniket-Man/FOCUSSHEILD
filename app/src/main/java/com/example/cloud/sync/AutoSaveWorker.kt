package com.example.cloud.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.FocusShieldApp
import com.example.data.preferences.FocusPreferences
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

private const val TAG = "AutoSaveWorker"

/**
 * Background half of the periodic cloud auto-save.
 *
 * The in-process loop in [SyncGateway] honours the user's cadence exactly (15s–5min) while the app
 * is alive. This worker covers the other case: the app is closed, or the OS killed it, and local
 * edits would otherwise sit in the outbox until the user next opens FocusShield.
 *
 * It contains no sync logic of its own — it delegates to [SyncGateway.flushPending], which no-ops
 * unless a session exists and the outbox is non-empty. So a signed-out install, an offline device,
 * or an install with nothing pending all cost one cheap local check and zero Firestore calls.
 *
 * Note the cadence floor: WorkManager refuses a periodic interval below 15 minutes, so this can
 * never honour a seconds-level request. That is a platform limit, not a policy one, and it is the
 * reason the foreground loop exists — it is where the sub-minute guarantee comes from.
 */
class AutoSaveWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? FocusShieldApp ?: return Result.success()

        return try {
            app.syncGateway.flushPending()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed push must not retry-storm Firestore. The outbox keeps the ops, the foreground
            // loop tries again on its next tick, and the next period simply tries again.
            Log.w(TAG, "Background auto-save skipped: ${e.message}")
            Result.success()
        }
    }
}

/**
 * Owns the single periodic auto-save job.
 *
 * Exactly one request exists app-wide: the unique name plus
 * [ExistingPeriodicWorkPolicy.UPDATE] means repeated scheduling is idempotent, while a cadence the
 * user changed in Settings still takes effect (KEEP would pin the old period forever).
 */
object AutoSaveScheduler {

    private const val UNIQUE_WORK_NAME = "focusshield_cloud_autosave_v1"

    /** WorkManager's documented floor for periodic work. */
    private const val MIN_PERIODIC_SECONDS = 15 * 60L

    /**
     * The period to actually request, given what the user asked for.
     *
     * Two bounds apply and they pull in opposite directions: the user's preference (15s–5min, from
     * [FocusPreferences]) and WorkManager's floor, which rejects anything under 15 minutes. The floor
     * always wins, so a "15 second" preference becomes 15 minutes here — which is exactly why the
     * foreground loop in [SyncGateway] exists and is not optional.
     */
    internal fun resolveBackgroundPeriodSeconds(requestedSeconds: Int): Long = maxOf(
        requestedSeconds
            .coerceIn(
                FocusPreferences.MIN_AUTO_SAVE_INTERVAL_SECONDS,
                FocusPreferences.MAX_AUTO_SAVE_INTERVAL_SECONDS
            )
            .toLong(),
        MIN_PERIODIC_SECONDS
    )

    /**
     * (Re)schedules the background auto-save. Idempotent — safe to call on every process start and
     * on every interval change.
     */
    fun schedule(context: Context, intervalSeconds: Int) {
        val period = resolveBackgroundPeriodSeconds(intervalSeconds)
        try {
            val request = PeriodicWorkRequestBuilder<AutoSaveWorker>(
                period, TimeUnit.SECONDS
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                // REPLACE rather than KEEP so a cadence the user changed in Settings actually takes
                // effect. WorkManager's UPDATE policy would do that without restarting the period,
                // but it only exists from 2.8.0 and this project pins 2.7.1. The cost is that a cold
                // start restarts the period — acceptable, because a user opening the app that often
                // is already covered by the foreground loop and the on-stop flush.
                ExistingPeriodicWorkPolicy.REPLACE,
                request
            )
        } catch (e: Throwable) {
            // A missing google-services.json must not take the app down at startup; the foreground
            // loop still syncs.
            Log.w(TAG, "Could not schedule background auto-save: ${e.message}")
        }
    }

    /** Cancels the background auto-save (signed out, or the user turned auto-save off). */
    fun cancel(context: Context) {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        } catch (e: Throwable) {
            Log.w(TAG, "WorkManager unavailable or not initialized: ${e.message}")
        }
    }
}