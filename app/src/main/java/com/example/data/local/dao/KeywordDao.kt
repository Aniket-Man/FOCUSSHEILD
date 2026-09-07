package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.KeywordEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for YouTube content-filter keyword rules.
 */
@Dao
interface KeywordDao {

    @Query("SELECT * FROM keywords ORDER BY type, keyword ASC")
    fun getAllKeywordsFlow(): Flow<List<KeywordEntity>>

    @Query("SELECT * FROM keywords WHERE type = 'allow' AND isActive = 1")
    suspend fun getAllowKeywords(): List<KeywordEntity>

    @Query("SELECT * FROM keywords WHERE type = 'block' AND isActive = 1")
    suspend fun getBlockKeywords(): List<KeywordEntity>

    @Query("SELECT COUNT(*) FROM keywords")
    suspend fun getKeywordCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeyword(keyword: KeywordEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertKeywords(keywords: List<KeywordEntity>)

    @Delete
    suspend fun deleteKeyword(keyword: KeywordEntity)

    @Query("DELETE FROM keywords WHERE id = :id")
    suspend fun deleteKeywordById(id: Long)
}
