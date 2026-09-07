package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.SubjectEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(subjects: List<SubjectEntity>)

    @Update
    suspend fun updateSubject(subject: SubjectEntity)

    @Query("SELECT * FROM study_subjects WHERE isArchived = 0 ORDER BY name ASC")
    fun getAllActiveSubjectsFlow(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM study_subjects WHERE id = :id LIMIT 1")
    suspend fun getSubjectById(id: String): SubjectEntity?

    @Query("SELECT COUNT(*) FROM study_subjects")
    suspend fun getSubjectCount(): Int
}
