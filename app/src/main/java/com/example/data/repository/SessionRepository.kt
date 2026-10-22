package com.example.data.repository

import com.example.cloud.sync.CloudJson
import com.example.cloud.sync.SyncTables
import com.example.cloud.sync.SyncTracker
import com.example.core.util.DayBounds
import com.example.data.local.dao.SessionDao
import com.example.data.local.dao.StudyActivityDao
import com.example.data.local.entity.SessionRecordEntity
import com.example.data.local.entity.StudyActivityEntity
import com.example.data.local.entity.StudyActivitySource
import com.example.data.local.entity.StudyActivityType
import com.example.data.model.SessionMode
import com.example.feature.session.domain.FocusSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SessionRepository(
    private val sessionDao: SessionDao,
    private val studyActivityDao: StudyActivityDao? = null
) {
    val allSessions: Flow<List<SessionRecordEntity>> = sessionDao.getAllSessionsFlow()

    fun getRecentSessions(limit: Int = 10): Flow<List<SessionRecordEntity>> {
        return sessionDao.getRecentSessionsFlow(limit)
    }

    // These three flows rebuild their SQL bounds whenever [DayBounds.todayFlow] re-emits (each
    // local midnight). Computing the bounds once per call — as before — froze "today" at
    // construction time, so after 00:00 the collectors kept querying yesterday's window until the
    // process restarted.
    fun getTodaySessionsFlow(): Flow<List<SessionRecordEntity>> {
        return DayBounds.todayFlow().flatMapLatest { (startOfDay, endOfDay) ->
            sessionDao.getSessionsBetweenFlow(startOfDay, endOfDay)
        }
    }

    suspend fun getTodaySessions(): List<SessionRecordEntity> {
        val (startOfDay, endOfDay) = DayBounds.today()
        return sessionDao.getSessionsBetween(startOfDay, endOfDay)
    }

    fun getTodayTotalStudyTimeMillisFlow(): Flow<Long> {
        return DayBounds.todayFlow().flatMapLatest { (startOfDay, _) ->
            sessionDao.getTotalStudyTimeMillisSinceFlow(startOfDay).map { it ?: 0L }
        }
    }

    fun getTodayCompletedSessionsCountFlow(): Flow<Int> {
        return DayBounds.todayFlow().flatMapLatest { (startOfDay, _) ->
            sessionDao.getCompletedSessionsCountSinceFlow(startOfDay)
        }
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

        val cleanSubject = session.subject.trim()
        val cleanTopic = session.topic.trim()
        val sessionTitle = when {
            cleanSubject.isNotBlank() && cleanTopic.isNotBlank() -> "$cleanSubject - $cleanTopic"
            cleanSubject.isNotBlank() -> cleanSubject
            cleanTopic.isNotBlank() -> cleanTopic
            else -> "${session.mode.label} Session"
        }

        val record = SessionRecordEntity(
            id = session.id,
            mode = session.mode,
            title = sessionTitle,
            subject = cleanSubject,
            topic = cleanTopic,
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
        SyncTracker.enqueueUpsert(
            SyncTables.SESSION_RECORDS,
            record.id,
            CloudJson.sessionRecordToJson(record).toString()
        )

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
                SyncTracker.enqueueUpsert(
                    SyncTables.STUDY_ACTIVITIES,
                    studyActivity.id,
                    CloudJson.studyActivityToJson(studyActivity).toString()
                )
            } catch (e: Exception) {
                // Ignore activity persistence error
            }
        }
    }

    suspend fun insertSession(record: SessionRecordEntity) {
        sessionDao.insertSession(record)
        SyncTracker.enqueueUpsert(
            SyncTables.SESSION_RECORDS,
            record.id,
            CloudJson.sessionRecordToJson(record).toString()
        )
    }

    suspend fun getSessionById(id: String): SessionRecordEntity? {
        return sessionDao.getSessionById(id)
    }

    suspend fun deleteSession(id: String) {
        // History rows are append-only on pull, so deleting a session must propagate the delete for
        // the session AND each of its activities row-by-row (ids are read before the delete).
        val activities = try {
            studyActivityDao?.getActivitiesForSession(id).orEmpty()
        } catch (e: Exception) {
            emptyList()
        }
        sessionDao.deleteSessionById(id)
        SyncTracker.enqueueDelete(SyncTables.SESSION_RECORDS, id)
        try {
            studyActivityDao?.deleteActivitiesForSession(id)
        } catch (e: Exception) {
            // Ignore
        }
        activities.forEach { activity ->
            SyncTracker.enqueueDelete(SyncTables.STUDY_ACTIVITIES, activity.id)
        }
    }
}
