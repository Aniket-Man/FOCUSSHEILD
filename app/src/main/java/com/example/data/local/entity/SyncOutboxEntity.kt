package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A pending, not-yet-uploaded change to one cloud-synced row.
 *
 * FocusShield is Room-first: every user mutation of a synced table first writes Room,
 * then enqueues a row here describing the change to replay against Supabase later.
 * Ops stay pending while the user is signed out or offline, and are drained in
 * `seq` order once a session is available.
 *
 * @param op "UPSERT" (row content replaced by [payload]) or "DELETE" (row removed).
 * @param payload JSON snapshot of the row for UPSERT ops; null for DELETE.
 */
@Entity(
    tableName = "sync_outbox",
    indices = [Index(value = ["tableName", "rowId"])]
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0L,
    val tableName: String,
    val rowId: String,
    val op: String,
    val payload: String? = null,
    val attemptCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
