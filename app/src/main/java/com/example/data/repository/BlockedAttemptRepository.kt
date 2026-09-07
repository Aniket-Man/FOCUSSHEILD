package com.example.data.repository

import com.example.data.local.dao.BlockedAttemptDao
import com.example.data.local.entity.BlockedAttemptEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class BlockedAttemptRepository(
    private val blockedAttemptDao: BlockedAttemptDao
) {
    val allAttempts: Flow<List<BlockedAttemptEntity>> = blockedAttemptDao.getAllAttemptsFlow()

    fun getTodayAttemptsFlow(): Flow<List<BlockedAttemptEntity>> {
        val (startOfDay, _) = getTodayBounds()
        return blockedAttemptDao.getAttemptsSinceFlow(startOfDay)
    }

    fun getTodayAttemptsCountFlow(): Flow<Int> {
        val (startOfDay, _) = getTodayBounds()
        return blockedAttemptDao.getAttemptCountSinceFlow(startOfDay)
    }

    fun getAttemptsForSessionFlow(sessionId: String): Flow<List<BlockedAttemptEntity>> {
        return blockedAttemptDao.getAttemptsForSessionFlow(sessionId)
    }

    suspend fun recordAttempt(
        packageName: String,
        appName: String,
        sessionId: String? = null
    ): Long {
        val attempt = BlockedAttemptEntity(
            timestamp = System.currentTimeMillis(),
            packageName = packageName,
            appName = appName,
            sessionId = sessionId
        )
        return blockedAttemptDao.insertAttempt(attempt)
    }

    suspend fun clearAttempts() {
        blockedAttemptDao.clearAllAttempts()
    }

    private fun getTodayBounds(): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis - 1
        return Pair(startOfDay, endOfDay)
    }
}
