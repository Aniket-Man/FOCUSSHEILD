package com.example.cloud.sync

import androidx.room.withTransaction
import com.example.data.local.FocusShieldDatabase
import com.example.data.local.dao.SyncOutboxDao
import com.example.data.local.entity.SyncOutboxEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide outbox writer.
 *
 * FocusShield is Room-first: every syncable repository performs its local Room mutation first, then
 * calls one of the `enqueue*` functions here to record the change for later replay against
 * Supabase. Ops sit in `sync_outbox` while the user is signed out or offline and are drained in
 * `seq` order by [SyncEngine] once a session is available.
 *
 * Coalescing = "latest local intent wins": before inserting a new op for a (table, row) we delete
 * any prior op for that same (table, row) inside one Room transaction, so an UPSERT followed by a
 * DELETE (and vice-versa) collapses to a single op.
 *
 * [lock] is the single write serialization point shared with [SyncEngine]. The engine holds it for
 * an entire cycle and repositories take it inside `enqueue*`, so a local Room write can never race
 * a reconcile that is clearing/reinserting the same tables.
 */
object SyncTracker {

    const val OP_UPSERT = "UPSERT"
    const val OP_DELETE = "DELETE"

    /** Shared with [SyncEngine]; see class doc. */
    val lock = Mutex()

    @Volatile
    private var database: FocusShieldDatabase? = null

    /** Invoked (on the caller's coroutine) after a pending op is committed. Set by app wiring. */
    @Volatile
    var onPendingChanged: (() -> Unit)? = null

    fun init(database: FocusShieldDatabase) {
        this.database = database
    }

    /** Record that a local row's current content ([payloadJson], a cloud-row JSON snapshot) must be
     *  upserted to the cloud. Coalesces any prior pending op for the same (table, row). */
    suspend fun enqueueUpsert(tableKey: String, rowId: String, payloadJson: String) {
        write(tableKey, rowId, OP_UPSERT, payloadJson)
    }

    /** Record that a local row (identified by its stable sync key [rowId]) was deleted locally. */
    suspend fun enqueueDelete(tableKey: String, rowId: String) {
        write(tableKey, rowId, OP_DELETE, null)
    }

    // ---- keyword natural-key helpers (keywords have no stable Room id) ----------------

    suspend fun enqueueKeywordUpsert(type: String, keyword: String, payloadJson: String) {
        write(SyncTables.BLOCKED_KEYWORDS, SyncKeycode.keywordKey(type, keyword), OP_UPSERT, payloadJson)
    }

    suspend fun enqueueKeywordDelete(type: String, keyword: String) {
        write(SyncTables.BLOCKED_KEYWORDS, SyncKeycode.keywordKey(type, keyword), OP_DELETE, null)
    }

    private suspend fun write(tableKey: String, rowId: String, op: String, payload: String?) {
        val db = database ?: return
        lock.withLock {
            db.withTransaction {
                val outbox: SyncOutboxDao = db.syncOutboxDao()
                outbox.deleteAllFor(tableKey, rowId)
                outbox.insert(
                    SyncOutboxEntity(
                        tableName = tableKey,
                        rowId = rowId,
                        op = op,
                        payload = payload
                    )
                )
            }
        }
        onPendingChanged?.invoke()
    }
}
