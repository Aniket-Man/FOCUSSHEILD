package com.example.cloud.sync

import androidx.room.withTransaction
import com.example.cloud.SupabaseConfig
import com.example.cloud.auth.AuthRepository
import com.example.cloud.auth.AuthSession
import com.example.cloud.http.CloudNotConfiguredException
import com.example.cloud.http.SupabaseApiException
import com.example.cloud.http.SupabaseHttp
import com.example.data.local.FocusShieldDatabase
import com.example.data.local.dao.CloudBulkDao
import com.example.data.local.dao.SyncOutboxDao
import com.example.data.local.entity.SyncOutboxEntity
import com.example.data.preferences.FocusPreferencesRepository
import java.io.IOException
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes one round-trip against Supabase for the authenticated user.
 *
 * Room stays the source of truth and is written first; the engine drains the outbox (locally-pending
 * edits) and only then reconciles against the cloud, so an unsent local change is never overwritten
 * by a pull. The whole cycle is serialized on [SyncTracker.lock], the same lock repositories take in
 * `SyncTracker.enqueue*` — a local Room write can therefore never race a reconcile that clears and
 * re-inserts the same tables.
 *
 * Cloud tables mirror the Room camelCase columns 1:1 (keywords use snake_case natural keys). Every
 * row carries `user_id` (RLS enforced server-side) plus server-managed `created_at`/`updated_at`.
 *
 * Account-boundary state machine (spec §20–21):
 *  - owner == uid, or uid was already migrated from this install → normal drain + reconcile.
 *  - owner == null and the account's cloud tables are empty (first ever account) → automatic, safe
 *    migration. Owner is *reserved first*, so an interrupted migration resumes idempotently next
 *    cycle instead of ever being mistaken for a destructive restore. Never clears Room.
 *  - cloud tables non-empty while owner is null or belongs to a *different* account → destructive
 *    restore required. [SyncEngine] refuses to run it automatically; the UI must confirm first.
 *  - a different, brand-new (empty) account signs in → never auto-migrate the previous owner's data
 *    into it (that would leak data across accounts); the UI must confirm a destructive reset instead.
 */
class SyncEngine(
    private val database: FocusShieldDatabase,
    private val installState: CloudInstallState,
    private val preferencesRepository: FocusPreferencesRepository,
    private val authRepository: AuthRepository
) {

    private val http = SupabaseHttp(SupabaseConfig)

    private val bulk: CloudBulkDao get() = database.cloudBulkDao()
    private val outbox: SyncOutboxDao get() = database.syncOutboxDao()

    private val bindings: List<TableBinding> = buildBindings()

    // ---- public entry points -----------------------------------------------------------

    /** Run one sync cycle for the currently signed-in user. Never throws. */
    suspend fun runCycle(): SyncCycleOutcome {
        if (!SupabaseConfig.isConfigured) return SyncCycleOutcome.NoSession
        return SyncTracker.lock.withLock {
            try {
                runCycleLocked()
            } catch (e: SupabaseApiException) {
                SyncCycleOutcome.Failed(e.message ?: "Sync failed (${e.status})")
            } catch (e: CloudNotConfiguredException) {
                SyncCycleOutcome.NoSession
            } catch (e: IOException) {
                SyncCycleOutcome.Offline
            } catch (e: Exception) {
                SyncCycleOutcome.Failed(e.message ?: "Unexpected sync error")
            }
        }
    }

    /** First-login migration: drain, then push the full local snapshot. Never destructive. */
    suspend fun migrate(): SyncCycleOutcome {
        if (!SupabaseConfig.isConfigured) return SyncCycleOutcome.NoSession
        return SyncTracker.lock.withLock {
            try {
                val session = authRepository.requireFreshSession() ?: return SyncCycleOutcome.NoSession
                migrateLocked(session)
            } catch (e: SupabaseApiException) {
                SyncCycleOutcome.Failed(e.message ?: "Sync failed (${e.status})")
            } catch (e: CloudNotConfiguredException) {
                SyncCycleOutcome.NoSession
            } catch (e: IOException) {
                SyncCycleOutcome.Offline
            } catch (e: Exception) {
                SyncCycleOutcome.Failed(e.message ?: "Unexpected sync error")
            }
        }
    }

    /**
     * Destructive cross-account / fresh-install-into-existing-account restore: clears every synced
     * table and the outbox on this device, then pulls the target account's cloud data and applies its
     * profile + preferences. Only called after explicit UI confirmation (the app's single destructive
     * operation).
     *
     * Every bound table is cleared, telemetry included — that is the point of the feature: the
     * account's history has to be reproduced from the cloud, and a table left behind would show the
     * *previous* owner's events alongside the restored account's. The only state still surviving a
     * restore is what was never account-level to begin with: the device-local preference keys, and
     * the per-device enforcement columns of `daily_app_usage`, which are restored as defaults.
     */
    suspend fun restore(uid: String): SyncCycleOutcome {
        if (!SupabaseConfig.isConfigured) return SyncCycleOutcome.NoSession
        return SyncTracker.lock.withLock {
            try {
                val session = authRepository.requireFreshSession()
                    ?: return SyncCycleOutcome.NoSession
                if (session.userId != uid) return SyncCycleOutcome.NoSession
                restoreLocked(session)
            } catch (e: SupabaseApiException) {
                SyncCycleOutcome.Failed(e.message ?: "Restore failed (${e.status})")
            } catch (e: CloudNotConfiguredException) {
                SyncCycleOutcome.NoSession
            } catch (e: IOException) {
                SyncCycleOutcome.Offline
            } catch (e: Exception) {
                SyncCycleOutcome.Failed(e.message ?: "Unexpected restore error")
            }
        }
    }

    // ---- cycle core ----------------------------------------------------------------------

    private suspend fun runCycleLocked(): SyncCycleOutcome {
        val session = authRepository.requireFreshSession() ?: return SyncCycleOutcome.NoSession
        val uid = session.userId
        installState.rememberAccount(uid)

        if (installState.migratedAccounts().contains(uid)) {
            return runNormalCycle(session)
        }

        val owner = installState.dataOwnerAccountId()
        return when {
            owner == uid -> {
                // Owner reserved by an earlier (possibly interrupted) migration; resume it. Idempotent
                // (cloud upserts merge on the stable keys), so re-running over a partial push is safe.
                migrateLocked(session)
                SyncCycleOutcome.Synced
            }
            owner == null -> {
                if (probeCloudHasRows(session)) SyncCycleOutcome.RestoreRequired
                else migrateLocked(session)
            }
            else -> {
                // A different account. Never migrate the previous owner's data into it.
                if (probeCloudHasRows(session)) SyncCycleOutcome.RestoreRequired
                else SyncCycleOutcome.CrossAccountEmptyAccount
            }
        }
    }

    private suspend fun runNormalCycle(session: AuthSession): SyncCycleOutcome {
        drain(session)
        pushDirtyDocuments(session)
        reconcileAll(session)
        applyDocumentsIfNotDirty(session)
        return SyncCycleOutcome.Synced
    }

    private suspend fun migrateLocked(session: AuthSession): SyncCycleOutcome {
        val uid = session.userId
        // Reserve ownership first: if anything below throws (network, server), the next cycle sees
        // owner == uid with no migrated flag and resumes this migration rather than demanding a
        // destructive restore of a half-uploaded account.
        installState.setDataOwnerAccountId(uid)
        installState.rememberAccount(uid)
        // Drain best-effort. A single wedged op must not stop a first migration: the snapshot below
        // already reflects current Room (outbox ops coalesce with it), and any op that could not be
        // pushed stays pending for a later normal cycle.
        try {
            drain(session)
        } catch (_: Exception) {
            // continue with the snapshot
        }
        pushSnapshotAllTables(session)
        pushDocuments(session)
        installState.markMigrated(uid)
        installState.setProfileDirty(false)
        installState.setPrefsDirty(false)
        return SyncCycleOutcome.Migrated
    }

    private suspend fun restoreLocked(session: AuthSession): SyncCycleOutcome {
        val uid = session.userId
        // 1) Fetch everything we will need *before* touching local state, so a network failure
        //    leaves the device exactly as it was.
        val fetched = LinkedHashMap<String, List<JSONObject>>()
        for (b in bindings) {
            fetched[b.meta.tableKey] = fetchRows(session, b.meta.cloudTable, null)
        }
        val profileRows = fetchRows(session, SyncTables.PROFILES, null)
        val prefsRows = fetchRows(session, SyncTables.USER_PREFERENCES, null)

        // 2) Clear every synced table + the outbox, then load the target account's data. This is the
        //    one deliberately destructive path (a cross-account restore, or a fresh install adopting
        //    an existing account). `daily_app_usage` is cleared here too — its device-only emergency
        //    and bypass columns belong to the *previous* device/account pairing, so carrying them
        //    into the restored account would be wrong.
        database.withTransaction {
            outbox.clearAll()
            for (b in bindings) b.clearAll()
            for (b in bindings) {
                val rows = fetched[b.meta.tableKey].orEmpty()
                if (rows.isNotEmpty()) b.decodeAndInsert(rows)
            }
        }
        installState.resetTransient()

        // 3) Apply the account's profile + preferences documents.
        applyProfileRows(profileRows)
        applyPreferencesRows(prefsRows)

        installState.setDataOwnerAccountId(uid)
        installState.markMigrated(uid)
        installState.rememberAccount(uid)
        installState.setProfileDirty(false)
        installState.setPrefsDirty(false)
        return SyncCycleOutcome.Synced
    }

    /** Drain pending outbox ops oldest-first. The first failing op is counted and rethrown so the
     *  cycle aborts (a reconcile never runs over edits we could not upload) and the caller classifies
     *  it as Offline or Failed. */
    private suspend fun drain(session: AuthSession) {
        while (true) {
            val batch = outbox.peekOldest(40)
            if (batch.isEmpty()) return
            for (op in batch) {
                try {
                    pushOutboxOp(session, op)
                    outbox.deleteBySeq(op.seq)
                } catch (e: Exception) {
                    outbox.bumpAttemptCount(op.seq)
                    throw e
                }
            }
        }
    }

    private suspend fun pushOutboxOp(session: AuthSession, op: SyncOutboxEntity) {
        when (op.op) {
            SyncTracker.OP_UPSERT -> {
                val payload = op.payload ?: return
                val row = JSONObject(payload)
                row.put("user_id", session.userId)
                postRows(session, metaFor(op.tableName).cloudTable, listOf(row))
            }
            SyncTracker.OP_DELETE -> {
                val meta = metaFor(op.tableName)
                val query = if (op.tableName == SyncTables.BLOCKED_KEYWORDS) {
                    val pair = SyncKeycode.decodeKeywordKey(op.rowId)
                    if (pair == null) return
                    keywordDeleteQuery(session.userId, pair.first, pair.second)
                } else {
                    ownerDeleteQuery(session.userId, meta.pkField, op.rowId)
                }
                http.call("DELETE", restPath(meta.cloudTable), query = query, accessToken = session.accessToken)
            }
            else -> return
        }
    }

    // ---- reconcile + history pull ------------------------------------------------------

    private suspend fun reconcileAll(session: AuthSession) {
        // Config tables: full LWW. Remote delete propagates (local rows absent from remote and with
        // no pending op are cleared), while locally-pending rows are kept and re-inserted from their
        // payload so an unsent edit survives the reconcile.
        for (b in bindings) {
            if (b.meta.pullMode == PullMode.HISTORY) continue
            val remote = fetchRows(session, b.meta.cloudTable, null)
            val pending = outbox.pendingForTable(b.meta.tableKey).associateBy { it.rowId }
            val keepRemote = remote.filterNot { pending.containsKey(b.rowKeyOfJson(it)) }
            if (!b.meta.clearsOnReconcile) {
                // Merge tables keep every local row: clearing would drop the device-only columns the
                // cloud never stored, and the pending/keepRemote split is unnecessary when nothing is
                // deleted. The binding decides per row whether the remote copy is actually newer.
                database.withTransaction {
                    if (keepRemote.isNotEmpty()) b.mergeRemote(keepRemote)
                }
                continue
            }
            database.withTransaction {
                b.clearAll()
                if (keepRemote.isNotEmpty()) b.decodeAndInsert(keepRemote)
                for (p in pending.values) {
                    if (p.op == SyncTracker.OP_UPSERT && p.payload != null) {
                        b.decodePayloadAndInsert(p.payload)
                    }
                }
            }
        }
        // History: append-only pull since the watermark; never delete locally from a pull.
        for (b in bindings) {
            if (b.meta.pullMode != PullMode.HISTORY) continue
            val watermark = installState.historyWatermark(b.meta.tableKey)
            val extra = if (watermark == null) "order=created_at.asc"
            else "created_at=gt.${http.encodeValue(watermark)}"
            val remote = fetchRows(session, b.meta.cloudTable, extra)
            val pending = outbox.pendingForTable(b.meta.tableKey).associateBy { it.rowId }
            val keep = remote.filterNot { pending.containsKey(b.rowKeyOfJson(it)) }
            if (keep.isNotEmpty()) database.withTransaction { b.decodeAndInsert(keep) }
            val maxCreated = remote.maxOfOrNull { it.optString("created_at") }
            if (maxCreated != null) installState.setHistoryWatermark(b.meta.tableKey, maxCreated)
        }
    }

    private suspend fun fetchRows(session: AuthSession, cloudTable: String, extraQuery: String?): List<JSONObject> {
        val sb = StringBuilder("user_id=eq.${http.encodeValue(session.userId)}")
        if (extraQuery != null) sb.append('&').append(extraQuery)
        val res = http.call("GET", restPath(cloudTable), query = sb.toString(), accessToken = session.accessToken)
        return try {
            val out = mutableListOf<JSONObject>()
            val arr = JSONArray(res.body)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out.add(o)
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Cheap presence probe used only at the account boundary (fresh-install decisions). */
    private suspend fun probeCloudHasRows(session: AuthSession): Boolean {
        // profiles is auto-created for every account by the signup trigger, so it is deliberately
        // excluded — only real synced data counts as "has rows". `user_id` exists on every table
        // (blocked_apps/websites/limits/keywords have no `id` column).
        for (b in bindings) {
            val res = try {
                http.call(
                    "GET", restPath(b.meta.cloudTable),
                    query = "user_id=eq.${http.encodeValue(session.userId)}&select=user_id&limit=1",
                    accessToken = session.accessToken
                )
            } catch (e: Exception) {
                continue
            }
            val arr = try {
                JSONArray(res.body)
            } catch (e: Exception) {
                continue
            }
            if (arr.length() > 0) return true
        }
        return false
    }

    // ---- whole-document rows (profiles / user_preferences) -----------------------------

    private suspend fun pushDirtyDocuments(session: AuthSession) {
        if (installState.profileDirty()) {
            pushProfile(session)
            installState.setProfileDirty(false)
        }
        if (installState.prefsDirty()) {
            pushPreferences(session)
            installState.setPrefsDirty(false)
        }
    }

    private suspend fun pushDocuments(session: AuthSession) {
        pushProfile(session)
        pushPreferences(session)
    }

    private suspend fun pushProfile(session: AuthSession) {
        val p = preferencesRepository.snapshot()
        val avatarPath = preferencesRepository.cloudAvatarPath()
        val row = CloudJson.profileToJson(p.userName, p.userAvatarPreset, p.userMotto, p.userAcademicGoal, avatarPath)
        row.put("user_id", session.userId)
        postRows(session, SyncTables.PROFILES, listOf(row))
    }

    private suspend fun pushPreferences(session: AuthSession) {
        val p = preferencesRepository.snapshot()
        val row = JSONObject()
            .put("user_id", session.userId)
            .put("payload", CloudJson.accountPreferencesJson(p))
        postRows(session, SyncTables.USER_PREFERENCES, listOf(row))
    }

    private suspend fun applyDocumentsIfNotDirty(session: AuthSession) {
        if (!installState.profileDirty()) {
            applyProfileRows(fetchRows(session, SyncTables.PROFILES, null))
        }
        if (!installState.prefsDirty()) {
            applyPreferencesRows(fetchRows(session, SyncTables.USER_PREFERENCES, null))
        }
    }

    private suspend fun applyProfileRows(rows: List<JSONObject>) {
        if (rows.isEmpty()) return
        val row = rows[0]
        val avatarPath = if (row.isNull("avatarPath")) null else row.optString("avatarPath").takeIf { it.isNotBlank() }
        preferencesRepository.applyCloudProfile(
            row.optString("displayName", "Focus Scholar"),
            row.optString("avatarPreset", "SHIELD"),
            row.optString("motto", "Deep Work & Daily Mastery"),
            row.optString("academicGoal", "Exam Rank & Cognitive Stamina"),
            avatarPath
        )
    }

    private suspend fun applyPreferencesRows(rows: List<JSONObject>) {
        if (rows.isEmpty()) return
        val payload = rows[0].optJSONObject("payload") ?: return
        preferencesRepository.applyCloudPreferences(payload.toString())
    }

    // ---- migration snapshot push ---------------------------------------------------------

    private suspend fun pushSnapshotAllTables(session: AuthSession) {
        for (b in bindings) {
            val rows = b.snapshotJsonRows()
            if (rows.isEmpty()) continue
            for (chunk in rows.chunked(50)) {
                val withOwner = chunk.map { it.put("user_id", session.userId) }
                postRows(session, b.meta.cloudTable, withOwner)
            }
        }
    }

    // ---- HTTP primitives ------------------------------------------------------------------

    private fun restPath(cloudTable: String) = "/rest/v1/$cloudTable"

    /** DELETE filter for a normal table: `user_id = uid AND "<pkColumn>" = rowId`. The PK column is
     *  double-quoted (mixed-case Room names) and the quotes percent-encoded for PostgREST. */
    private fun ownerDeleteQuery(uid: String, pkColumn: String, rowId: String): String {
        val pk = http.encodeValue("\"$pkColumn\"")
        return "user_id=eq.${http.encodeValue(uid)}&$pk=eq.${http.encodeValue(rowId)}"
    }

    private fun keywordDeleteQuery(uid: String, type: String, keyword: String): String {
        val typeCol = http.encodeValue("\"keyword_type\"")
        val valueCol = http.encodeValue("\"keyword_value\"")
        return "user_id=eq.${http.encodeValue(uid)}&$typeCol=eq.${http.encodeValue(type)}&$valueCol=eq.${http.encodeValue(keyword)}"
    }

    private suspend fun postRows(session: AuthSession, cloudTable: String, rows: List<JSONObject>) {
        val arr = JSONArray()
        for (r in rows) arr.put(r)
        http.call(
            "POST", restPath(cloudTable),
            jsonBody = arr.toString(),
            accessToken = session.accessToken,
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
        )
    }

    private fun metaFor(tableKey: String): TableMeta =
        SYNCED_ROOM_TABLES.firstOrNull { it.tableKey == tableKey }
            ?: SYNCED_ROOM_TABLES.first() // unreachable; outbox only holds synced room tables

    // ---- typed table bindings --------------------------------------------------------------

    private interface TableBinding {
        val meta: TableMeta
        val rowKeyOfJson: (JSONObject) -> String
        suspend fun decodeAndInsert(rows: List<JSONObject>)
        suspend fun decodePayloadAndInsert(payload: String)
        /**
         * Write remote rows onto the existing local rows without clearing first, so columns the
         * cloud does not carry (device-only enforcement state) survive the pull. Only reached for
         * tables whose [TableMeta.clearsOnReconcile] is false.
         */
        suspend fun mergeRemote(rows: List<JSONObject>)
        suspend fun clearAll()
        suspend fun snapshotJsonRows(): List<JSONObject>
    }

    private fun <E> bind(
        meta: TableMeta,
        keyOfJson: (JSONObject) -> String,
        fromJson: (JSONObject) -> E,
        toJson: (E) -> JSONObject,
        readAll: suspend () -> List<E>,
        insert: suspend (List<E>) -> Unit,
        clear: suspend () -> Unit,
        merge: (suspend (List<JSONObject>) -> Unit)? = null
    ): TableBinding = object : TableBinding {
        override val meta = meta
        override val rowKeyOfJson = keyOfJson
        override suspend fun decodeAndInsert(rows: List<JSONObject>) {
            val decoded = rows.mapNotNull { row ->
                try {
                    fromJson(row)
                } catch (e: Exception) {
                    null
                }
            }
            if (decoded.isNotEmpty()) insert(decoded)
        }
        override suspend fun decodePayloadAndInsert(payload: String) {
            try {
                insert(listOf(fromJson(JSONObject(payload))))
            } catch (e: Exception) {
                // Malformed payload → skip; the row stays pending in the outbox and is retried later.
            }
        }
        override suspend fun mergeRemote(rows: List<JSONObject>) {
            val custom = merge
            if (custom != null) custom(rows) else decodeAndInsert(rows)
        }
        override suspend fun clearAll() = clear()
        override suspend fun snapshotJsonRows(): List<JSONObject> {
            val es = readAll()
            return es.map { toJson(it) }
        }
    }

    private fun buildBindings(): List<TableBinding> {
        val l = mutableListOf<TableBinding>()

        l += bind(
            meta = TableMeta(SyncTables.STUDY_SUBJECTS, SyncTables.STUDY_SUBJECTS, "id", PullMode.RECONCILE),
            keyOfJson = { it.optString("id") },
            fromJson = { CloudJson.subjectFromCloud(it) },
            toJson = { CloudJson.subjectToJson(it) },
            readAll = { bulk.subjects() },
            insert = { bulk.upsertSubjects(it) },
            clear = { bulk.clearSubjects() }
        )
        l += bind(
            meta = TableMeta(SyncTables.STUDY_TOPICS, SyncTables.STUDY_TOPICS, "id", PullMode.RECONCILE),
            keyOfJson = { it.optString("id") },
            fromJson = { CloudJson.topicFromCloud(it) },
            toJson = { CloudJson.topicToJson(it) },
            readAll = { bulk.topics() },
            insert = { bulk.upsertTopics(it) },
            clear = { bulk.clearTopics() }
        )
        l += bind(
            meta = TableMeta(SyncTables.STUDY_PLANS, SyncTables.STUDY_PLANS, "id", PullMode.RECONCILE),
            keyOfJson = { it.optString("id") },
            fromJson = { CloudJson.studyPlanFromCloud(it) },
            toJson = { CloudJson.studyPlanToJson(it) },
            readAll = { bulk.plans() },
            insert = { bulk.upsertPlans(it) },
            clear = { bulk.clearPlans() }
        )
        l += bind(
            meta = TableMeta(SyncTables.FOCUS_SCHEDULES, SyncTables.FOCUS_SCHEDULES, "id", PullMode.RECONCILE),
            keyOfJson = { it.optString("id") },
            fromJson = { CloudJson.scheduleFromCloud(it) },
            toJson = { CloudJson.focusScheduleToJson(it) },
            readAll = { bulk.schedules() },
            insert = { bulk.upsertSchedules(it) },
            clear = { bulk.clearSchedules() }
        )
        l += bind(
            meta = TableMeta(SyncTables.BLOCKED_APPS, SyncTables.BLOCKED_APPS, "packageName", PullMode.RECONCILE),
            keyOfJson = { it.optString("packageName") },
            fromJson = { CloudJson.blockedAppFromCloud(it) },
            toJson = { CloudJson.blockedAppToJson(it) },
            readAll = { bulk.blockedApps() },
            insert = { bulk.upsertBlockedApps(it) },
            clear = { bulk.clearBlockedApps() }
        )
        l += bind(
            meta = TableMeta(SyncTables.BLOCKED_WEBSITES, SyncTables.BLOCKED_WEBSITES, "domain", PullMode.RECONCILE),
            keyOfJson = { it.optString("domain") },
            fromJson = { CloudJson.blockedWebsiteFromCloud(it) },
            toJson = { CloudJson.blockedWebsiteToJson(it) },
            readAll = { bulk.blockedWebsites() },
            insert = { bulk.upsertBlockedWebsites(it) },
            clear = { bulk.clearBlockedWebsites() }
        )
        l += bind(
            meta = TableMeta(SyncTables.APP_LIMITS, SyncTables.APP_LIMITS, "packageName", PullMode.RECONCILE),
            keyOfJson = { it.optString("packageName") },
            fromJson = { CloudJson.appLimitFromCloud(it) },
            toJson = { CloudJson.appLimitToJson(it) },
            readAll = { bulk.appLimits() },
            insert = { bulk.upsertAppLimits(it) },
            clear = { bulk.clearAppLimits() }
        )
        l += bind(
            meta = TableMeta(SyncTables.STUDY_CHANNELS, SyncTables.STUDY_CHANNELS, "id", PullMode.RECONCILE),
            keyOfJson = { it.optString("id") },
            fromJson = { CloudJson.studyChannelFromCloud(it) },
            toJson = { CloudJson.studyChannelToJson(it) },
            readAll = { bulk.channels() },
            insert = { bulk.upsertChannels(it) },
            clear = { bulk.clearChannels() }
        )
        // Keywords are keyed by their natural (type, keyword) pair in the cloud; the local autogen
        // id is never synced.
        l += bind(
            meta = TableMeta(SyncTables.BLOCKED_KEYWORDS, SyncTables.BLOCKED_KEYWORDS, "", PullMode.RECONCILE),
            keyOfJson = { SyncKeycode.keywordKey(it.optString("keyword_type"), it.optString("keyword_value")) },
            fromJson = { CloudJson.keywordFromCloud(it) },
            toJson = { CloudJson.keywordToJson(it) },
            readAll = { bulk.keywords() },
            insert = { bulk.upsertKeywords(it) },
            clear = { bulk.clearKeywords() }
        )
        // History tables are append-only on pull.
        l += bind(
            meta = TableMeta(SyncTables.SESSION_RECORDS, SyncTables.SESSION_RECORDS, "id", PullMode.HISTORY),
            keyOfJson = { it.optString("id") },
            fromJson = { CloudJson.sessionRecordFromCloud(it) },
            toJson = { CloudJson.sessionRecordToJson(it) },
            readAll = { bulk.sessions() },
            insert = { bulk.insertSessionsMissing(it) },
            clear = { bulk.clearSessions() }
        )
        l += bind(
            meta = TableMeta(SyncTables.STUDY_ACTIVITIES, SyncTables.STUDY_ACTIVITIES, "id", PullMode.HISTORY),
            keyOfJson = { it.optString("id") },
            fromJson = { CloudJson.studyActivityFromCloud(it) },
            toJson = { CloudJson.studyActivityToJson(it) },
            readAll = { bulk.activities() },
            insert = { bulk.insertActivitiesMissing(it) },
            clear = { bulk.clearActivities() }
        )
        l += bind(
            meta = TableMeta(SyncTables.BREAK_RECORDS, SyncTables.BREAK_RECORDS, "id", PullMode.HISTORY),
            keyOfJson = { it.optString("id") },
            fromJson = { CloudJson.breakRecordFromCloud(it) },
            toJson = { CloudJson.breakRecordToJson(it) },
            readAll = { bulk.breaks() },
            insert = { bulk.insertBreaksMissing(it) },
            clear = { bulk.clearBreaks() }
        )
        l += bind(
            meta = TableMeta(SyncTables.BLOCKED_ATTEMPTS, SyncTables.BLOCKED_ATTEMPTS, "eventId", PullMode.HISTORY),
            keyOfJson = { it.optString("eventId") },
            fromJson = { CloudJson.blockedAttemptFromCloud(it) },
            toJson = { CloudJson.blockedAttemptToJson(it) },
            readAll = { bulk.blockedAttempts() },
            insert = { bulk.insertBlockedAttemptsMissing(it) },
            clear = { bulk.clearBlockedAttempts() }
        )
        l += bind(
            meta = TableMeta(SyncTables.APP_LIMIT_SESSIONS, SyncTables.APP_LIMIT_SESSIONS, "id", PullMode.HISTORY),
            keyOfJson = { it.optString("id") },
            fromJson = { CloudJson.appLimitSessionFromCloud(it) },
            toJson = { CloudJson.appLimitSessionToJson(it) },
            readAll = { bulk.appLimitSessions() },
            insert = { bulk.insertAppLimitSessionsMissing(it) },
            clear = { bulk.clearAppLimitSessions() }
        )
        l += bind(
            meta = TableMeta(SyncTables.SCRATCH_CARDS, SyncTables.SCRATCH_CARDS, "sessionId", PullMode.HISTORY),
            keyOfJson = { it.optString("sessionId") },
            fromJson = { CloudJson.scratchCardFromCloud(it) },
            toJson = { CloudJson.scratchCardToJson(it) },
            readAll = { bulk.scratchCards() },
            insert = { bulk.insertScratchCardsMissing(it) },
            clear = { bulk.clearScratchCards() }
        )
        // daily_unlocks is a natural-key reconcile table.
        l += bind(
            meta = TableMeta(SyncTables.DAILY_UNLOCKS, SyncTables.DAILY_UNLOCKS, "dateString", PullMode.RECONCILE),
            keyOfJson = { it.optString("dateString") },
            fromJson = { CloudJson.dailyUnlockFromCloud(it) },
            toJson = { CloudJson.dailyUnlockToJson(it) },
            readAll = { bulk.dailyUnlocks() },
            insert = { bulk.upsertDailyUnlocks(it) },
            clear = { bulk.clearDailyUnlocks() }
        )
        // daily_app_usage merges instead of clearing, so this device's emergency/bypass columns
        // survive. Remote usage is applied only when it is at least as recent as the local row,
        // which keeps this device's newer counts rather than silently regressing them; the local
        // row's pending upsert still converges the cloud copy on the next drain.
        l += bind(
            meta = TableMeta(
                SyncTables.DAILY_APP_USAGE,
                SyncTables.DAILY_APP_USAGE,
                "packageName",
                PullMode.RECONCILE,
                clearsOnReconcile = false
            ),
            keyOfJson = { SyncKeycode.appUsageKey(it.optString("packageName"), it.optString("dateString")) },
            fromJson = { CloudJson.dailyAppUsageMerge(null, it) },
            toJson = { CloudJson.dailyAppUsageToJson(it) },
            readAll = { bulk.dailyAppUsage() },
            insert = { bulk.upsertDailyAppUsage(it) },
            clear = { bulk.clearDailyAppUsage() },
            merge = { rows -> mergeDailyAppUsage(rows) }
        )
        return l
    }

    /**
     * Apply remote `daily_app_usage` rows onto the local ones. A remote row is written only when it
     * is newer than the local row it would replace, and it is always combined with the local row's
     * device-only columns (emergency usage, bypass) which the cloud does not store.
     */
    private suspend fun mergeDailyAppUsage(rows: List<JSONObject>) {
        val local = bulk.dailyAppUsage().associateBy {
            SyncKeycode.appUsageKey(it.packageName, it.dateString)
        }
        val merged = rows.mapNotNull { row ->
            val key = SyncKeycode.appUsageKey(row.optString("packageName"), row.optString("dateString"))
            val existing = local[key]
            if (existing != null && existing.lastActiveTimestamp > row.optLong("lastActiveTimestamp", 0L)) {
                return@mapNotNull null
            }
            try {
                CloudJson.dailyAppUsageMerge(existing, row)
            } catch (e: Exception) {
                null
            }
        }
        if (merged.isNotEmpty()) bulk.upsertDailyAppUsage(merged)
    }
}

/** Outcome of one [SyncEngine] cycle / migration / restore attempt. */
sealed interface SyncCycleOutcome {
    /** Normal sync completed. */
    data object Synced : SyncCycleOutcome

    /** Automatic first-account migration completed (local data intact). */
    data object Migrated : SyncCycleOutcome

    /** No usable session; nothing was attempted. */
    data object NoSession : SyncCycleOutcome

    /** Network/unavailable; local data untouched, ops remain pending. */
    data object Offline : SyncCycleOutcome

    /**
     * This device's synced data belongs to a different account (or the account already has cloud
     * data and this is a fresh install). Applying it requires a destructive local restore, which is
     * never run automatically.
     */
    data object RestoreRequired : SyncCycleOutcome

    /** A different, brand-new (empty) account was signed in; migrating would leak the previous
     *  owner's data into it, so the UI must confirm a destructive reset first. */
    data object CrossAccountEmptyAccount : SyncCycleOutcome

    /** A server-side (non-transient) error occurred. */
    data class Failed(val message: String) : SyncCycleOutcome
}
