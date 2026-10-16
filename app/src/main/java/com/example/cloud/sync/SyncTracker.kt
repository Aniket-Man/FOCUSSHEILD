package com.example.cloud.sync

import android.util.Log
import androidx.room.withTransaction
import com.example.data.local.FocusShieldDatabase
import com.example.data.local.dao.SyncOutboxDao
import com.example.data.local.entity.SyncOutboxEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Process-wide outbox writer.
 *
 * FocusShield is Room-first: every syncable repository performs its local Room mutation first, then
 * calls one of the `enqueue*` functions here to record the change for later replay against
 * Firebase. Ops sit in `sync_outbox` while the user is signed out or offline and are drained in
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

    private const val TAG = "SyncTracker"

    const val OP_UPSERT = "UPSERT"
    const val OP_DELETE = "DELETE"

    /** Shared with [SyncEngine]; see class doc. */
    val lock = Mutex()

    @Volatile
    private var database: FocusShieldDatabase? = null

    /** Invoked (on the caller's coroutine) after a pending op is committed. Set by app wiring. */
    @Volatile
    var onPendingChanged: (() -> Unit)? = null

    /**
     * A cloud row that could not be converted into a local entity, with the reason.
     *
     * Cloud data the app cannot understand used to be coerced into a default and inserted anyway
     * (an unknown session mode became TIMER). It is now rejected and recorded here, so the condition
     * is visible — in the log, and to any diagnostics surface that collects [rejectedCloudRows] —
     * instead of quietly changing behaviour. The row stays in the cloud; the next cycle retries it.
     */
    data class RejectedCloudRow(
        val table: String,
        val rowKey: String,
        val field: String?,
        val reason: String,
        val atMillis: Long = System.currentTimeMillis()
    )

    private const val MAX_RECORDED_REJECTIONS = 20

    private val _rejectedCloudRows = MutableStateFlow<List<RejectedCloudRow>>(emptyList())

    /** The most recent [MAX_RECORDED_REJECTIONS] rejected cloud rows (newest first). */
    val rejectedCloudRows: StateFlow<List<RejectedCloudRow>> = _rejectedCloudRows.asStateFlow()

    private val _pendingCount = MutableStateFlow(0)

    /**
     * Number of ops waiting in the outbox.
     *
     * The periodic auto-save loop uses this to stay quiet while nothing has changed: a timer tick
     * that finds an empty outbox returns immediately instead of pushing a redundant full snapshot to
     * Firestore, so an idle phone costs no writes. Maintained at the two points where the outbox
     * actually changes size — an insert in [write] and [markOutboxDrained] after a successful cycle —
     * so it is a live count, not a polled approximation.
     */
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()

    /** Records that the outbox was emptied by a completed sync cycle. */
    fun markOutboxDrained() {
        _pendingCount.value = 0
    }

    /**
     * Records a rejected cloud row. Called by [SyncEngine] for every row it refuses to insert; never
     * silently dropped, and never fatal for the cycle (the other rows still sync).
     */
    fun recordRejectedCloudRow(table: String, rowKey: String, cause: Throwable) {
        val field = (cause as? CloudDataException)?.field
        val row = RejectedCloudRow(table = table, rowKey = rowKey, field = field, reason = cause.message ?: cause.toString())
        Log.w(TAG, "Rejected cloud row ${table}[$rowKey]: ${row.reason}")
        _rejectedCloudRows.value = (listOf(row) + _rejectedCloudRows.value).take(MAX_RECORDED_REJECTIONS)
    }

    /**
     * Attaches the database and seeds [pendingCount] from whatever survived the last process.
     *
     * The seed matters: an outbox that still holds ops from a previous run (the user edited while
     * signed out, offline, or the process was killed before a cycle ran) is real pending work, and
     * without this the periodic auto-save loop would read zero, decide there was nothing to push, and
     * those edits would sit there until the user happened to edit something else.
     *
     * [scope] is the application scope, so the read outlives this call.
     */
    fun init(database: FocusShieldDatabase, scope: CoroutineScope) {
        this.database = database
        scope.launch {
            lock.withLock {
                runCatching { _pendingCount.value = database.syncOutboxDao().countPending() }
            }
        }
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
                // Re-count inside the same transaction so the published count can never disagree
                // with the committed outbox (a concurrent writer's op is already visible here).
                _pendingCount.value = outbox.countPending()
            }
        }
        onPendingChanged?.invoke()
    }
}
