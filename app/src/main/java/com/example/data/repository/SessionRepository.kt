package com.example.data.repository

import com.example.data.local.dao.SessionDao
import com.example.data.local.dao.StudyActivityDao
import com.example.data.local.entity.SessionRecordEntity
import com.example.data.local.entity.StudyActivityEntity
import com.example.data.local.entity.StudyActivitySource
import com.example.data.local.entity.StudyActivityType
import com.example.data.model.SessionMode
import com.example.feature.session.domain.FocusSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import java.util.UUID

class SessionRepository(
    private val sessionDao: SessionDao,
    private val studyActivityDao: StudyActivityDao? = null
) {
    val allSessions: Flow<List<SessionRecordEntity>> = sessionDao.getAllSessionsFlow()

    fun getRecentSessions(limit: Int = 10): Flow<List<SessionRecordEntity>> {
        return sessionDao.getRecentSessionsFlow(limit)
    }

    fun getTodaySessionsFlow(): Flow<List<SessionRecordEntity>> {
        val (startOfDay, endOfDay) = getTodayBounds()
        return sessionDao.getSessionsBetweenFlow(startOfDay, endOfDay)
    }

    suspend fun getTodaySessions(): List<SessionRecordEntity> {
        val (startOfDay, endOfDay) = getTodayBounds()
        return sessionDao.getSessionsBetween(startOfDay, endOfDay)
    }

    fun getTodayTotalStudyTimeMillisFlow(): Flow<Long> {
        val (startOfDay, _) = getTodayBounds()
        return sessionDao.getTotalStudyTimeMillisSinceFlow(startOfDay).map { it ?: 0L }
    }

    fun getTodayCompletedSessionsCountFlow(): Flow<Int> {
        val (startOfDay, _) = getTodayBounds()
        return sessionDao.getCompletedSessionsCountSinceFlow(startOfDay)
    }

    fun getSessionsBetweenFlow(startTime: Long, endTime: Long): Flow<List<SessionRecordEntity>> {
        return sessionDao.getSessionsBetweenFlow(startTime, endTime)
    }

    suspend fun getAllSessionsAscending(): List<SessionRecordEntity> {
        return sessionDao.getAllSessionsAscending()
    }

    suspend fun recordSession(
        session: FocusSession,
        actualStudyDurationMillis: Long,
        isCompleted: Boolean,
        isCancelled: Boolean,
        endTime: Long = System.currentTimeMillis()
    ) {
        val completedCycles = if (session.mode == SessionMode.POMODORO) {
            if (isCompleted) session.totalCycles else (session.currentCycle - 1).coerceAtLeast(0)
        } else 0

        val pureStudyDuration = actualStudyDurationMillis.coerceAtLeast(0L)

        val record = SessionRecordEntity(
            id = session.id,
            mode = session.mode,
            title = "${session.subject} - ${session.topic}",
            subject = session.subject,
            topic = session.topic,
            goal = session.goal,
            plannedDurationMillis = session.plannedDurationMillis,
            actualDurationMillis = pureStudyDuration,
            startTime = session.startedAt,
            endTime = endTime,
            completed = isCompleted,
            cancelled = isCancelled,
            pomodoroCycles = session.totalCycles,
            completedPomodoroCycles = completedCycles,
            createdAt = session.startedAt
        )
        sessionDao.insertSession(record)

        // Also record corresponding verified StudyActivity
        if (pureStudyDuration > 0 && studyActivityDao != null) {
            val activitySource = when (session.mode) {
                SessionMode.TIMER -> StudyActivitySource.TIMER
                SessionMode.STOPWATCH -> StudyActivitySource.STOPWATCH
                SessionMode.POMODORO -> StudyActivitySource.POMODORO
            }
            val activityType = if (session.mode == SessionMode.POMODORO) {
                StudyActivityType.POMODORO_FOCUS
            } else {
                StudyActivityType.FOCUS_SESSION
            }

            val studyActivity = StudyActivityEntity(
                id = UUID.randomUUID().toString(),
                sessionId = session.id,
                activityType = activityType,
                source = activitySource,
                subject = session.subject,
                topic = session.topic,
                startedAt = session.startedAt,
                endedAt = endTime,
                durationMillis = pureStudyDuration,
                createdAt = System.currentTimeMillis()
            )
            try {
                studyActivityDao.insertActivity(studyActivity)
            } catch (e: Exception) {
                // Ignore activity persistence error
            }
        }
    }

    suspend fun insertSession(record: SessionRecordEntity) {
        sessionDao.insertSession(record)
    }

    suspend fun getSessionById(id: String): SessionRecordEntity? {
        return sessionDao.getSessionById(id)
    }

    suspend fun deleteSession(id: String) {
        sessionDao.deleteSessionById(id)
        try {
            studyActivityDao?.deleteActivitiesForSession(id)
        } catch (e: Exception) {
            // Ignore
        }
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
