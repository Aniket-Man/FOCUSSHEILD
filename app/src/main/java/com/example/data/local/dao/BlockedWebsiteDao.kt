package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.BlockedWebsiteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedWebsiteDao {
    @Query("SELECT * FROM blocked_websites ORDER BY createdAt DESC")
    fun getAllBlockedWebsites(): Flow<List<BlockedWebsiteEntity>>

    @Query("SELECT * FROM blocked_websites WHERE isEnabled = 1")
    fun getActiveBlockedWebsites(): Flow<List<BlockedWebsiteEntity>>

    @Query("SELECT * FROM blocked_websites WHERE isEnabled = 1")
    suspend fun getActiveBlockedWebsitesSync(): List<BlockedWebsiteEntity>

    @Query("SELECT * FROM blocked_websites WHERE domain = :domain LIMIT 1")
    suspend fun getWebsiteByDomain(domain: String): BlockedWebsiteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWebsite(website: BlockedWebsiteEntity)

    @Query("DELETE FROM blocked_websites WHERE domain = :domain")
    suspend fun deleteWebsite(domain: String)

    @Query("UPDATE blocked_websites SET isEnabled = :isEnabled WHERE domain = :domain")
    suspend fun updateEnabled(domain: String, isEnabled: Boolean)

    @Query("SELECT COUNT(*) FROM blocked_websites")
    suspend fun getWebsiteCount(): Int
}
