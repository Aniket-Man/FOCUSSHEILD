package com.zenlock.focusguard.data.db.dao

import androidx.room.*
import com.zenlock.focusguard.data.db.entity.KeywordEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for keyword rules CRUD operations.
 */
@Dao
interface KeywordDao {

    @Query("SELECT * FROM keywords ORDER BY type, keyword ASC")
    fun getAllKeywords(): Flow<List<KeywordEntity>>

    @Query("SELECT * FROM keywords WHERE type = :type AND isActive = 1")
    suspend fun getActiveKeywordsByType(type: String): List<KeywordEntity>

    @Query("SELECT * FROM keywords WHERE type = 'allow' AND isActive = 1")
    suspend fun getAllowKeywords(): List<KeywordEntity>

    @Query("SELECT * FROM keywords WHERE type = 'block' AND isActive = 1")
    suspend fun getBlockKeywords(): List<KeywordEntity>

    @Query("SELECT * FROM keywords WHERE type = 'channel' AND isActive = 1")
    suspend fun getChannelKeywords(): List<KeywordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeyword(keyword: KeywordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeywords(keywords: List<KeywordEntity>)

    @Update
    suspend fun updateKeyword(keyword: KeywordEntity)

    @Delete
    suspend fun deleteKeyword(keyword: KeywordEntity)

    @Query("DELETE FROM keywords")
    suspend fun deleteAllKeywords()

    @Query("SELECT COUNT(*) FROM keywords")
    suspend fun getKeywordCount(): Int
}
