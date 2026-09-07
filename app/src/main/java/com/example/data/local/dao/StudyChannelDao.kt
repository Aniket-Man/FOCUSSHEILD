package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.StudyChannelEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyChannelDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannel(channel: StudyChannelEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(channels: List<StudyChannelEntity>)

    @Update
    suspend fun updateChannel(channel: StudyChannelEntity)

    @Query("SELECT * FROM study_channels ORDER BY channelName ASC")
    fun getAllChannelsFlow(): Flow<List<StudyChannelEntity>>

    @Query("SELECT * FROM study_channels WHERE isApproved = 1 ORDER BY channelName ASC")
    fun getApprovedChannelsFlow(): Flow<List<StudyChannelEntity>>

    @Query("SELECT * FROM study_channels WHERE isApproved = 1")
    suspend fun getApprovedChannels(): List<StudyChannelEntity>

    @Query("SELECT * FROM study_channels WHERE channelId = :channelId LIMIT 1")
    suspend fun getChannelByChannelId(channelId: String): StudyChannelEntity?

    @Query("UPDATE study_channels SET isApproved = :isApproved WHERE id = :id")
    suspend fun setApprovalStatus(id: String, isApproved: Boolean)

    @Query("UPDATE study_channels SET thumbnailUrl = :thumbnailUrl WHERE id = :id")
    suspend fun updateThumbnailUrl(id: String, thumbnailUrl: String)

    @Query("UPDATE study_channels SET thumbnailUrl = :thumbnailUrl WHERE channelId = :channelId")
    suspend fun updateThumbnailByChannelId(channelId: String, thumbnailUrl: String)

    @Query("SELECT * FROM study_channels WHERE id = :id LIMIT 1")
    suspend fun getChannelById(id: String): StudyChannelEntity?

    @Query("SELECT COUNT(*) FROM study_channels")
    suspend fun getChannelCount(): Int

    @Query("DELETE FROM study_channels WHERE id = :id")
    suspend fun deleteChannelById(id: String)
}
