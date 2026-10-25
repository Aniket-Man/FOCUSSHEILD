package com.example.cloud.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.FocusShieldApp
import com.example.data.repository.Repository
import com.example.data.repository.SessionQuoteRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "SyncWorker"
private const val SYNC_PREFS_NAME = "focus_shield_sync_worker_prefs"
private const val PREF_KEY_LAST_SYNC_MILLIS = "last_sync_timestamp_millis"

/**
 * Immutable snapshot of the background [SyncWorker] state for UI observation.
 */
data class SyncWorkerStatus(
    val isSyncing: Boolean = false,
    val lastSyncedAtMillis: Long = 0L,
    val lastErrorMessage: String? = null
)

/**
 * Hilt [EntryPoint] allowing [SyncWorker] to access the singleton [Repository] instance.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncWorkerEntryPoint {
    fun repository(): Repository
}

/**
 * [WorkManager] [CoroutineWorker] that periodically fetches and synchronizes data from the remote
 * API and cloud backend, even when the application is in the background.
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        init(applicationContext)
        _syncStatus.update { it.copy(isSyncing = true, lastErrorMessage = null) }

        return try {
            val entryPoint = runCatching {
                EntryPointAccessors.fromApplication(
                    applicationContext,
                    SyncWorkerEntryPoint::class.java
                )
            }.getOrNull()

            val repository = entryPoint?.repository()

            // 1. Fetch latest release metadata from the remote API via Repository
            val releaseResult = repository?.fetchLatestRelease()

            // 2. Fetch remote quote / ensure Firestore quotes & pending local outbox are synchronized
            repository?.fetchRandomQuote()
            runCatching { SessionQuoteRepository.ensurePreloadedInFirestore() }

            val app = applicationContext as? FocusShieldApp
            runCatching { app?.syncGateway?.flushPending() }

            val now = System.currentTimeMillis()
            recordSyncSuccess(applicationContext, now)

            val outputData = Data.Builder()
                .putLong(KEY_LAST_SYNC_TIMESTAMP, now)
                .putBoolean(KEY_SYNC_SUCCESS, releaseResult?.isSuccess ?: true)
                .build()

            Result.success(outputData)
        } catch (e: CancellationException) {
            _syncStatus.update { it.copy(isSyncing = false) }
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Background API sync failed: ${e.message}", e)
            _syncStatus.update {
                it.copy(
                    isSyncing = false,
                    lastErrorMessage = e.localizedMessage ?: "Sync failed"
                )
            }
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(
                    Data.Builder()
                        .putString(KEY_ERROR_MESSAGE, e.localizedMessage ?: "Sync failed")
                        .build()
                )
            }
        }
    }

    companion object {
        const val UNIQUE_PERIODIC_WORK_NAME = "focusshield_periodic_api_sync_work"
        const val UNIQUE_ONETIME_WORK_NAME = "focusshield_onetime_api_sync_work"
        const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        const val KEY_SYNC_SUCCESS = "sync_success"
        const val KEY_ERROR_MESSAGE = "error_message"

        private const val DEFAULT_SYNC_INTERVAL_MINUTES = 15L
        private const val MIN_BACKOFF_SECONDS = 30L
        private const val MAX_RETRY_ATTEMPTS = 3

        @Volatile
        private var initialized = false

        private val _syncStatus = MutableStateFlow(SyncWorkerStatus())

        /**
         * Hot [StateFlow] emitting the current [SyncWorkerStatus] for UI components.
         */
        val syncStatus: StateFlow<SyncWorkerStatus> = _syncStatus.asStateFlow()

        /**
         * Loads the persisted last-sync timestamp from [ android.content.SharedPreferences ].
         */
        fun init(context: Context) {
            if (initialized) return
            synchronized(this) {
                if (initialized) return
                runCatching {
                    val prefs = context.applicationContext
                        .getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
                    val savedMillis = prefs.getLong(PREF_KEY_LAST_SYNC_MILLIS, 0L)
                    if (savedMillis > 0L && _syncStatus.value.lastSyncedAtMillis == 0L) {
                        _syncStatus.update { it.copy(lastSyncedAtMillis = savedMillis) }
                    }
                }
                initialized = true
            }
        }

        /**
         * Records a successful synchronization timestamp both in memory and on disk.
         */
        fun recordSyncSuccess(
            context: Context? = null,
            timestampMillis: Long = System.currentTimeMillis()
        ) {
            _syncStatus.update {
                it.copy(
                    isSyncing = false,
                    lastSyncedAtMillis = timestampMillis,
                    lastErrorMessage = null
                )
            }
            val targetContext = context ?: runCatching { FocusShieldApp.instance }.getOrNull()
            targetContext?.let { ctx ->
                runCatching {
                    ctx.applicationContext
                        .getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putLong(PREF_KEY_LAST_SYNC_MILLIS, timestampMillis)
                        .apply()
                }
            }
        }

        /**
         * Formats the elapsed time since [lastSyncedAtMillis] as `'Last synced: X minutes ago'`.
         */
        fun formatLastSyncedLabel(
            lastSyncedAtMillis: Long,
            nowMillis: Long = System.currentTimeMillis()
        ): String {
            if (lastSyncedAtMillis <= 0L) {
                return "Last synced: Never"
            }
            val elapsedMillis = (nowMillis - lastSyncedAtMillis).coerceAtLeast(0L)
            val minutes = elapsedMillis / 60_000L
            return if (minutes == 1L) {
                "Last synced: 1 minute ago"
            } else {
                "Last synced: $minutes minutes ago"
            }
        }

        /**
         * Schedules periodic background synchronization using [WorkManager].
         */
        fun schedulePeriodicSync(
            context: Context,
            repeatIntervalMinutes: Long = DEFAULT_SYNC_INTERVAL_MINUTES
        ) {
            init(context)
            val safeInterval = maxOf(repeatIntervalMinutes, DEFAULT_SYNC_INTERVAL_MINUTES)
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(
                    safeInterval,
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        MIN_BACKOFF_SECONDS,
                        TimeUnit.SECONDS
                    )
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicRequest
                )
            }.onFailure { e ->
                Log.w(TAG, "Unable to schedule periodic SyncWorker: ${e.message}")
            }
        }

        /**
         * Triggers an immediate one-shot synchronization via [WorkManager].
         */
        fun enqueueImmediateSync(context: Context) {
            init(context)
            runCatching {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val oneTimeRequest = OneTimeWorkRequestBuilder<SyncWorker>()
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    UNIQUE_ONETIME_WORK_NAME,
                    ExistingWorkPolicy.REPLACE,
                    oneTimeRequest
                )
            }.onFailure { e ->
                Log.w(TAG, "Unable to enqueue immediate SyncWorker: ${e.message}")
            }
        }
    }
}
