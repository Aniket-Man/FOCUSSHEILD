package com.example.data.repository

import com.example.data.local.dao.BreakRecordDao
import com.example.data.local.entity.BreakRecordEntity
import com.example.feature.session.domain.ManualBreakInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository handling persistence of session breaks into Room.
 */
class BreakRepository(
    private val breakRecordDao: BreakRecordDao
) {
    suspend fun recordBreak(breakInfo: ManualBreakInfo, isCompleted: Boolean) = withContext(Dispatchers.IO) {
        val entity = BreakRecordEntity(
            id = breakInfo.id,
            sessionId = breakInfo.sessionId,
            requestedDurationMillis = breakInfo.requestedDurationMillis,
            actualDurationMillis = breakInfo.actualDurationMillis,
            startTime = breakInfo.startedAt,
            endTime = breakInfo.startedAt + breakInfo.actualDurationMillis,
            completed = isCompleted,
            createdAt = breakInfo.startedAt
        )
        breakRecordDao.insertBreak(entity)
    }

    suspend fun updateBreak(breakInfo: ManualBreakInfo, isCompleted: Boolean) = withContext(Dispatchers.IO) {
        val entity = BreakRecordEntity(
            id = breakInfo.id,
            sessionId = breakInfo.sessionId,
            requestedDurationMillis = breakInfo.requestedDurationMillis,
            actualDurationMillis = breakInfo.actualDurationMillis,
            startTime = breakInfo.startedAt,
            endTime = breakInfo.startedAt + breakInfo.actualDurationMillis,
            completed = isCompleted,
            createdAt = breakInfo.startedAt
        )
        breakRecordDao.updateBreak(entity)
    }

    fun getBreaksForSessionFlow(sessionId: String): Flow<List<BreakRecordEntity>> {
        return breakRecordDao.getBreaksForSessionFlow(sessionId)
    }
}
