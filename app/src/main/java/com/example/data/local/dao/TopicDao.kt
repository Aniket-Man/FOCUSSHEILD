package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.TopicEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TopicDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopic(topic: TopicEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(topics: List<TopicEntity>)

    @Query("SELECT * FROM study_topics WHERE subjectId = :subjectId ORDER BY name ASC")
    fun getTopicsForSubjectFlow(subjectId: String): Flow<List<TopicEntity>>
}
