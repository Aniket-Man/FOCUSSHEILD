package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.FocusScheduleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusScheduleDao {
    @Query("SELECT * FROM focus_schedules ORDER BY startTime ASC")
    fun getAllSchedulesFlow(): Flow<List<FocusScheduleEntity>>

    @Query("SELECT * FROM focus_schedules WHERE isEnabled = 1")
    suspend fun getEnabledSchedules(): List<FocusScheduleEntity>

    @Query("SELECT * FROM focus_schedules WHERE id = :id LIMIT 1")
    suspend fun getScheduleById(id: String): FocusScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: FocusScheduleEntity)

    @Update
    suspend fun updateSchedule(schedule: FocusScheduleEntity)

    @Query("UPDATE focus_schedules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setScheduleEnabled(id: String, isEnabled: Boolean)

    @Query("DELETE FROM focus_schedules WHERE id = :id")
    suspend fun deleteScheduleById(id: String)

    @Query("SELECT COUNT(*) FROM focus_schedules")
    suspend fun getScheduleCount(): Int
}
