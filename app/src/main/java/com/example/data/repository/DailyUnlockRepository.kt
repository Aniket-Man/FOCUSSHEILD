package com.example.data.repository

import android.util.Log
import com.example.data.local.dao.DailyUnlockDao
import com.example.data.local.entity.DailyUnlockEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Repository tracking daily phone unlock counts.
 *
 * Unlocks are recorded by the [com.example.core.tracking.PhoneUnlockTracker] whenever the
 * keyguard is dismissed (ACTION_USER_PRESENT). A short debounce window prevents a single
 * unlock burst (screen-on + keyguard events) from double counting.
 */
class DailyUnlockRepository(
    private val dao: DailyUnlockDao,
    private val scope: CoroutineScope
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val writeMutex = Mutex()

    fun getTodayUnlockFlow(dateString: String = getTodayDateString()): Flow<DailyUnlockEntity?> =
        dao.getUnlockFlow(dateString).flowOn(Dispatchers.IO)

    suspend fun getTodayUnlock(dateString: String = getTodayDateString()): DailyUnlockEntity? =
        withContext(Dispatchers.IO) { dao.getUnlock(dateString) }

    fun getUnlocksBetweenFlow(fromDate: String, toDate: String): Flow<List<DailyUnlockEntity>> =
        dao.getUnlocksBetweenFlow(fromDate, toDate).flowOn(Dispatchers.IO)

    suspend fun getUnlocksBetween(fromDate: String, toDate: String): List<DailyUnlockEntity> =
        withContext(Dispatchers.IO) { dao.getUnlocksBetween(fromDate, toDate) }

    suspend fun getAverageUnlocks(fromDate: String, toDate: String): Double =
        withContext(Dispatchers.IO) { dao.getAverageUnlocksBetween(fromDate, toDate) ?: 0.0 }

    /**
     * Records one unlock. Read-modify-write is guarded by a mutex because the SQLite
     * upsert clause is unavailable below API 29 (minSdk is 24).
     */
    fun recordUnlockAsync(timestamp: Long = System.currentTimeMillis()) {
        scope.launch {
            try {
                writeMutex.withLock {
                    val dateString = withContext(Dispatchers.Default) {
                        synchronized(dateFormat) { dateFormat.format(Date(timestamp)) }
                    }
                    val existing = dao.getUnlock(dateString)
                    if (existing == null) {
                        dao.insert(
                            DailyUnlockEntity(
                                dateString = dateString,
                                unlockCount = 1,
                                firstUnlockAt = timestamp,
                                lastUnlockAt = timestamp
                            )
                        )
                    } else {
                        dao.insert(
                            existing.copy(
                                unlockCount = existing.unlockCount + 1,
                                lastUnlockAt = timestamp
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record unlock", e)
            }
        }
    }

    fun getTodayDateString(): String =
        synchronized(dateFormat) { dateFormat.format(Date()) }

    companion object {
        private const val TAG = "DailyUnlockRepo"
    }
}
