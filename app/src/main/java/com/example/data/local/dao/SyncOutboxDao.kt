package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.SyncOutboxEntity

/**
 * Outbox of pending cloud-write operations, ordered by insertion ([SyncOutboxEntity.seq]).
 *
 * Coalescing is intentionally not encoded in SQL here: it needs atomic
 * read-then-replace semantics, so [deleteAllFor] + [insert] are run inside a single
 * Room transaction by the sync tracker ("latest local intent wins per table+row").
 */
@Dao
interface SyncOutboxDao {

    /** Oldest [limit] pending ops (FIFO). */
    @Query("SELECT * FROM sync_outbox ORDER BY seq ASC LIMIT :limit")
    suspend fun peekOldest(limit: Int): List<SyncOutboxEntity>

    /** Remove every pending op for one local row (used to coalesce before enqueueing). */
    @Query("DELETE FROM sync_outbox WHERE tableName = :tableName AND rowId = :rowId")
    suspend fun deleteAllFor(tableName: String, rowId: String)

    @Query("DELETE FROM sync_outbox WHERE seq = :seq")
    suspend fun deleteBySeq(seq: Long)

    /** Remove every pending op (used by cross-account restore, never in normal flow). */
    @Query("DELETE FROM sync_outbox")
    suspend fun clearAll()

    @Query("UPDATE sync_outbox SET attemptCount = attemptCount + 1 WHERE seq = :seq")
    suspend fun bumpAttemptCount(seq: Long)

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM sync_outbox WHERE tableName = :tableName AND rowId = :rowId")
    suspend fun hasPendingFor(tableName: String, rowId: String): Int

    /** All pending ops for one table (used by reconcile to preserve locally-pending rows). */
    @Query("SELECT * FROM sync_outbox WHERE tableName = :tableName ORDER BY seq ASC")
    suspend fun pendingForTable(tableName: String): List<SyncOutboxEntity>

    @Query("SELECT seq FROM sync_outbox WHERE tableName = :tableName AND rowId = :rowId LIMIT 1")
    suspend fun pendingSeqFor(tableName: String, rowId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SyncOutboxEntity)
}
