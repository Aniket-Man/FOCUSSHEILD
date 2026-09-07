package com.zenlock.focusguard.data.repository

import com.zenlock.focusguard.data.db.dao.FocusSessionDao
import com.zenlock.focusguard.data.db.entity.FocusSessionEntity
import com.zenlock.focusguard.domain.repository.FocusSessionRepository
import kotlinx.coroutines.flow.Flow

class FocusSessionRepositoryImpl(
    private val dao: FocusSessionDao
) : FocusSessionRepository {

    override suspend fun insertSession(session: FocusSessionEntity): Long =
        dao.insertSession(session)

    override suspend fun updateSession(session: FocusSessionEntity) =
        dao.updateSession(session)

    override fun getAllSessions(): Flow<List<FocusSessionEntity>> =
        dao.getAllSessions()

    override fun getCompletedSessions(): Flow<List<FocusSessionEntity>> =
        dao.getCompletedSessions()

    override fun getSessionsSince(startTime: Long): Flow<List<FocusSessionEntity>> =
        dao.getSessionsSince(startTime)

    override suspend fun getAllSessionsSince(startTime: Long): List<FocusSessionEntity> =
        dao.getAllSessionsSince(startTime)

    override suspend fun getCompletedSessionsSince(startTime: Long): List<FocusSessionEntity> =
        dao.getCompletedSessionsSince(startTime)

    override suspend fun getTotalFocusTimeSince(startTime: Long): Long =
        dao.getTotalFocusTimeSince(startTime) ?: 0L

    override suspend fun getSessionCountSince(startTime: Long): Int =
        dao.getSessionCountSince(startTime)

    override suspend fun getCompletedSessionCountSince(startTime: Long): Int =
        dao.getCompletedSessionCountSince(startTime)

    override suspend fun getTotalBlockedAttemptsSince(startTime: Long): Int =
        dao.getTotalBlockedAttemptsSince(startTime) ?: 0

    override suspend fun getSessionById(id: Long): FocusSessionEntity? =
        dao.getSessionById(id)

    override suspend fun getActiveSession(): FocusSessionEntity? =
        dao.getActiveSession()
}
