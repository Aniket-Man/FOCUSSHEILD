package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.StudyPlanEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyPlanDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: StudyPlanEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(plans: List<StudyPlanEntity>)

    @Update
    suspend fun updatePlan(plan: StudyPlanEntity)

    @Query("SELECT * FROM study_plans ORDER BY startMinutes ASC, createdAt ASC")
    fun getAllPlansFlow(): Flow<List<StudyPlanEntity>>

    @Query("SELECT * FROM study_plans WHERE targetDate >= :startOfDay AND targetDate <= :endOfDay ORDER BY startMinutes ASC, createdAt ASC")
    fun getPlansForDateFlow(startOfDay: Long, endOfDay: Long): Flow<List<StudyPlanEntity>>

    @Query("SELECT * FROM study_plans WHERE targetDate >= :startOfDay AND targetDate <= :endOfDay ORDER BY startMinutes ASC, createdAt ASC")
    suspend fun getPlansForDate(startOfDay: Long, endOfDay: Long): List<StudyPlanEntity>

    @Query("SELECT * FROM study_plans ORDER BY startMinutes ASC, createdAt ASC")
    suspend fun getAllPlans(): List<StudyPlanEntity>

    @Query("SELECT * FROM study_plans WHERE dateString = :dateString ORDER BY startMinutes ASC, createdAt ASC")
    fun getPlansByDateStringFlow(dateString: String): Flow<List<StudyPlanEntity>>

    @Query("SELECT * FROM study_plans WHERE id = :id LIMIT 1")
    suspend fun getPlanById(id: String): StudyPlanEntity?

    @Query("UPDATE study_plans SET isCompleted = :isCompleted, status = CASE WHEN :isCompleted = 1 THEN 'COMPLETED' ELSE 'PLANNED' END WHERE id = :id")
    suspend fun updatePlanCompletion(id: String, isCompleted: Boolean)

    @Query("DELETE FROM study_plans WHERE id = :id")
    suspend fun deletePlanById(id: String)

    @Query("SELECT COUNT(*) FROM study_plans")
    suspend fun getPlanCount(): Int
}

