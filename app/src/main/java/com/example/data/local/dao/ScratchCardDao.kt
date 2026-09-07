package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ScratchCardEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for scratch card reward records.
 */
@Dao
interface ScratchCardDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(card: ScratchCardEntity): Long

    @Query("SELECT * FROM scratch_cards WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getCardForSession(sessionId: String): ScratchCardEntity?

    @Query("SELECT * FROM scratch_cards WHERE sessionId = :sessionId AND isRevealed = 0 LIMIT 1")
    suspend fun getUnrevealedCardForSession(sessionId: String): ScratchCardEntity?

    @Query("SELECT * FROM scratch_cards WHERE isRevealed = 0 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestUnrevealedCard(): ScratchCardEntity?

    @Query("SELECT * FROM scratch_cards WHERE isRevealed = 0 ORDER BY createdAt DESC LIMIT 1")
    fun getLatestUnrevealedCardFlow(): Flow<ScratchCardEntity?>

    @Query("UPDATE scratch_cards SET isRevealed = 1 WHERE sessionId = :sessionId")
    suspend fun markRevealed(sessionId: String)

    @Query("SELECT * FROM scratch_cards ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentCards(limit: Int): List<ScratchCardEntity>

    @Query("SELECT COUNT(*) FROM scratch_cards WHERE isRevealed = 1")
    fun getRevealedCountFlow(): Flow<Int>
}
