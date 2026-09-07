package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.StudyActivityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyActivityDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivity(activity: StudyActivityEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActivities(activities: List<StudyActivityEntity>)

    @Update
    suspend fun updateActivity(activity: StudyActivityEntity)

    @Query("SELECT * FROM study_activities WHERE id = :id LIMIT 1")
    suspend fun getActivityById(id: String): StudyActivityEntity?

    @Query("SELECT * FROM study_activities WHERE sessionId = :sessionId ORDER BY startedAt ASC")
    fun getActivitiesForSessionFlow(sessionId: String): Flow<List<StudyActivityEntity>>

    @Query("SELECT * FROM study_activities WHERE sessionId = :sessionId ORDER BY startedAt ASC")
    suspend fun getActivitiesForSession(sessionId: String): List<StudyActivityEntity>

    @Query("SELECT * FROM study_activities WHERE startedAt >= :startTime AND startedAt <= :endTime ORDER BY startedAt DESC")
    fun getActivitiesBetweenFlow(startTime: Long, endTime: Long): Flow<List<StudyActivityEntity>>

    @Query("SELECT * FROM study_activities WHERE startedAt >= :startTime AND startedAt <= :endTime")
    suspend fun getActivitiesBetween(startTime: Long, endTime: Long): List<StudyActivityEntity>

    @Query("SELECT * FROM study_activities ORDER BY startedAt DESC")
    fun getAllActivitiesFlow(): Flow<List<StudyActivityEntity>>

    @Query("SELECT SUM(durationMillis) FROM study_activities WHERE startedAt >= :startTime AND startedAt <= :endTime")
    fun getTotalDurationBetweenFlow(startTime: Long, endTime: Long): Flow<Long?>

    @Query("DELETE FROM study_activities WHERE sessionId = :sessionId")
    suspend fun deleteActivitiesForSession(sessionId: String)

    @Query("DELETE FROM study_activities WHERE id = :id")
    suspend fun deleteActivityById(id: String)
}
