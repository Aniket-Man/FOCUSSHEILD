package com.zenlock.focusguard.domain.repository

import com.zenlock.focusguard.data.db.entity.FocusSessionEntity
import kotlinx.coroutines.flow.Flow

interface FocusSessionRepository {
    suspend fun insertSession(session: FocusSessionEntity): Long
    suspend fun updateSession(session: FocusSessionEntity)
    fun getAllSessions(): Flow<List<FocusSessionEntity>>
    fun getCompletedSessions(): Flow<List<FocusSessionEntity>>
    fun getSessionsSince(startTime: Long): Flow<List<FocusSessionEntity>>
    suspend fun getAllSessionsSince(startTime: Long): List<FocusSessionEntity>
    suspend fun getCompletedSessionsSince(startTime: Long): List<FocusSessionEntity>
    suspend fun getTotalFocusTimeSince(startTime: Long): Long
    suspend fun getSessionCountSince(startTime: Long): Int
    suspend fun getCompletedSessionCountSince(startTime: Long): Int
    suspend fun getTotalBlockedAttemptsSince(startTime: Long): Int
    suspend fun getSessionById(id: Long): FocusSessionEntity?
    suspend fun getActiveSession(): FocusSessionEntity?
}
