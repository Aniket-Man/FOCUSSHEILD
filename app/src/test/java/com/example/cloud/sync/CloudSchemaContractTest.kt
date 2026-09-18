package com.example.cloud.sync

import com.example.data.local.entity.BlockedAppEntity
import com.example.data.local.entity.BlockedAttemptEntity
import com.example.data.local.entity.BlockedEventSource
import com.example.data.local.entity.BlockedEventType
import com.example.data.local.entity.BlockedWebsiteEntity
import com.example.data.local.entity.BreakRecordEntity
import com.example.data.local.entity.FocusScheduleEntity
import com.example.data.local.entity.KeywordEntity
import com.example.data.local.entity.ScratchCardEntity
import com.example.data.local.entity.SessionRecordEntity
import com.example.data.local.entity.StudyActivityEntity
import com.example.data.local.entity.StudyActivitySource
import com.example.data.local.entity.StudyActivityType
import com.example.data.local.entity.StudyChannelEntity
import com.example.data.local.entity.StudyPlanEntity
import com.example.data.local.entity.SubjectEntity
import com.example.data.local.entity.TopicEntity
import com.example.data.model.SessionMode
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Issue 12 regression suite — the sync contract is written down in four places (Kotlin table
 * constants, the binding list, the CloudJson mappers, and `supabase/cloud_schema.sql`), and nothing
 * used to compare them. A rename or a new field in only one of them produced a sync that silently
 * stopped working (unknown column at PostgREST, or a row that comes back missing its new field).
 *
 * These tests parse the *actual SQL schema file* and hold it against the app's mappers and metadata,
 * so the drift is caught here instead of in a user's account:
 *
 *  1. every table the app syncs exists in the schema, and the schema declares nothing the app does
 *     not know about (the legacy `app_limits` table is the one deliberate exception, asserted);
 *  2. every column a mapper writes exists in the schema, and every schema column the mapper does not
 *     write is a known server-managed one (`user_id`, `created_at`, `updated_at`);
 *  3. the primary key in SQL matches the app's declared key for that table;
 *  4. a row written by the app round-trips back through `*FromCloud` unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudSchemaContractTest {

    // ---- the SQL schema ----------------------------------------------------------------

    private val schemaFile: File by lazy {
        val candidates = listOf(
            File("../supabase/cloud_schema.sql"), // Gradle runs unit tests from the module dir
            File("supabase/cloud_schema.sql"),
            File(System.getProperty("user.dir") ?: ".", "../supabase/cloud_schema.sql")
        )
        candidates.firstOrNull { it.exists() }
            ?: throw AssertionError(
                "Could not locate supabase/cloud_schema.sql; looked in " +
                    candidates.joinToString { it.absolutePath }
            )
    }

    private data class SqlTable(val columns: Set<String>, val primaryKey: List<String>)

    /** Minimal parser for the schema's `create table if not exists public.<name> ( … );` blocks. */
    private val sqlTables: Map<String, SqlTable> by lazy {
        val text = schemaFile.readText()
        val result = LinkedHashMap<String, SqlTable>()
        val blockRegex = Regex(
            "create table if not exists public\\.([a-z_]+)\\s*\\((.*?)\\n\\);",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        for (match in blockRegex.findAll(text)) {
            val table = match.groupValues[1]
            val body = match.groupValues[2]
            val columnPart = body.substringBefore("primary key")
            val columns = columnPart.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .mapNotNull { line ->
                    val quoted = Regex("^\"([A-Za-z0-9_]+)\"").find(line)?.groupValues?.get(1)
                    val bare = Regex("^([a-z_][a-z0-9_]*)\\s").find(line)?.groupValues?.get(1)
                    quoted ?: bare
                }
                .toSet()
            val pkText = Regex("primary key\\s*\\(([^)]*)\\)").find(body)?.groupValues?.get(1).orEmpty()
            val pk = pkText.split(',')
                .map { it.trim().trim('"') }
                .filter { it.isNotEmpty() && it != "user_id" }
            result[table] = SqlTable(columns, pk)
        }
        assertTrue("schema file produced no tables", result.size >= 16)
        result
    }

    private val serverManagedColumns = setOf("user_id", "created_at", "updated_at")

    // ---- the app's side of the contract -------------------------------------------------

    /** One Room-backed synced table: metadata + a representative row serialized by CloudJson. */
    private data class AppTable(
        val meta: TableMeta,
        val expectedPkColumns: List<String>,
        val toJson: () -> JSONObject
    )

    private val appTables: List<AppTable> = listOf(
        AppTable(
            meta = metaOf(SyncTables.STUDY_SUBJECTS),
            expectedPkColumns = listOf("id"),
            toJson = { CloudJson.subjectToJson(SubjectEntity(id = "sub-1", name = "Physics")) }
        ),
        AppTable(
            meta = metaOf(SyncTables.STUDY_TOPICS),
            expectedPkColumns = listOf("id"),
            toJson = {
                CloudJson.topicToJson(
                    TopicEntity(id = "topic-1", subjectId = "sub-1", name = "Optics")
                )
            }
        ),
        AppTable(
            meta = metaOf(SyncTables.STUDY_PLANS),
            expectedPkColumns = listOf("id"),
            toJson = {
                CloudJson.studyPlanToJson(
                    StudyPlanEntity(
                        id = "plan-1",
                        subjectId = "sub-1",
                        subjectName = "Physics",
                        topicName = "Optics"
                    )
                )
            }
        ),
        AppTable(
            meta = metaOf(SyncTables.FOCUS_SCHEDULES),
            expectedPkColumns = listOf("id"),
            toJson = { CloudJson.focusScheduleToJson(FocusScheduleEntity(id = "sch-1", title = "Deep Work")) }
        ),
        AppTable(
            meta = metaOf(SyncTables.BLOCKED_APPS),
            expectedPkColumns = listOf("packageName"),
            toJson = {
                CloudJson.blockedAppToJson(
                    BlockedAppEntity(packageName = "com.instagram.android", appName = "Instagram")
                )
            }
        ),
        AppTable(
            meta = metaOf(SyncTables.BLOCKED_WEBSITES),
            expectedPkColumns = listOf("domain"),
            toJson = { CloudJson.blockedWebsiteToJson(BlockedWebsiteEntity(domain = "example.com")) }
        ),
        AppTable(
            meta = metaOf(SyncTables.STUDY_CHANNELS),
            expectedPkColumns = listOf("id"),
            toJson = {
                CloudJson.studyChannelToJson(
                    StudyChannelEntity(
                        id = "ch-1",
                        channelId = "UC123",
                        channelName = "Physics Wallah",
                        channelUrl = "https://youtube.com/@pwlive"
                    )
                )
            }
        ),
        AppTable(
            meta = metaOf(SyncTables.BLOCKED_KEYWORDS),
            // Natural key: the Room autogen id is never synced (see SyncKeycode).
            expectedPkColumns = listOf("keyword_type", "keyword_value"),
            toJson = { CloudJson.keywordToJson(KeywordEntity(keyword = "shorts", type = "block")) }
        ),
        AppTable(
            meta = metaOf(SyncTables.SESSION_RECORDS),
            expectedPkColumns = listOf("id"),
            toJson = {
                CloudJson.sessionRecordToJson(
                    SessionRecordEntity(
                        id = "sess-1",
                        mode = SessionMode.TIMER,
                        title = "Deep Work",
                        subject = "Physics",
                        topic = "Optics",
                        goal = "Finish optics",
                        plannedDurationMillis = 3_600_000L,
                        actualDurationMillis = 3_000_000L,
                        startTime = 1_700_000_000_000L,
                        endTime = 1_700_003_000_000L,
                        completed = true,
                        cancelled = false
                    )
                )
            }
        ),
        AppTable(
            meta = metaOf(SyncTables.STUDY_ACTIVITIES),
            expectedPkColumns = listOf("id"),
            toJson = {
                CloudJson.studyActivityToJson(
                    StudyActivityEntity(
                        id = "act-1",
                        sessionId = "sess-1",
                        activityType = StudyActivityType.FOCUS_SESSION,
                        source = StudyActivitySource.TIMER,
                        subject = "Physics",
                        topic = "Optics",
                        startedAt = 1_700_000_000_000L,
                        endedAt = 1_700_001_000_000L,
                        durationMillis = 1_000_000L
                    )
                )
            }
        ),
        AppTable(
            meta = metaOf(SyncTables.BREAK_RECORDS),
            expectedPkColumns = listOf("id"),
            toJson = {
                CloudJson.breakRecordToJson(
                    BreakRecordEntity(
                        id = "break-1",
                        sessionId = "sess-1",
                        requestedDurationMillis = 300_000L,
                        actualDurationMillis = 240_000L,
                        startTime = 1_700_000_000_000L,
                        endTime = 1_700_000_240_000L
                    )
                )
            }
        ),
        AppTable(
            meta = metaOf(SyncTables.BLOCKED_ATTEMPTS),
            // Cloud identity is `eventId`; the Room autogen `id` is local-only and never uploaded.
            expectedPkColumns = listOf("eventId"),
            toJson = {
                CloudJson.blockedAttemptToJson(
                    BlockedAttemptEntity(
                        eventId = "event-1",
                        eventType = BlockedEventType.APP_BLOCKED,
                        source = BlockedEventSource.ACCESSIBILITY_SERVICE,
                        packageName = "com.instagram.android",
                        appName = "Instagram"
                    )
                )
            }
        ),
        AppTable(
            meta = metaOf(SyncTables.SCRATCH_CARDS),
            expectedPkColumns = listOf("sessionId"),
            toJson = {
                CloudJson.scratchCardToJson(
                    ScratchCardEntity(
                        sessionId = "sess-1",
                        createdAt = 1_700_000_000_000L,
                        rewardType = "QUOTE",
                        rewardEmoji = "🎉",
                        rewardTitle = "Nice work",
                        rewardMessage = "Keep going",
                        studyMinutes = 60
                    )
                )
            }
        )
    )

    private fun metaOf(tableKey: String): TableMeta =
        SYNCED_ROOM_TABLES.firstOrNull { it.tableKey == tableKey }
            ?: throw AssertionError("$tableKey is missing from SYNCED_ROOM_TABLES")

    private fun keys(json: JSONObject): Set<String> = json.keys().asSequence().toSet()

    @Test
    fun `the contract covers every synced table`() {
        // Guards the guard: a new synced table must be added here (which forces the four layers to
        // be reconciled) rather than silently escaping the comparison.
        assertEquals(
            SYNCED_ROOM_TABLES.map { it.tableKey }.toSet(),
            appTables.map { it.meta.tableKey }.toSet()
        )
    }

    // ---- 1. table sets ------------------------------------------------------------------

    @Test
    fun `every synced table exists in the SQL schema`() {
        for (meta in SYNCED_ROOM_TABLES) {
            assertTrue(
                "${meta.tableKey} is synced but not declared in cloud_schema.sql",
                sqlTables.containsKey(meta.cloudTable)
            )
        }
        // The two whole-document tables are not Room-backed but are still part of the contract.
        assertTrue(sqlTables.containsKey(SyncTables.PROFILES))
        assertTrue(sqlTables.containsKey(SyncTables.USER_PREFERENCES))
    }

    @Test
    fun `the schema declares no synced table the app does not know about`() {
        // 13 Room-backed + profiles + user_preferences + the deliberate legacy app_limits.
        val expectedKnown = SYNCED_ROOM_TABLES.map { it.cloudTable }.toSet() +
            setOf(SyncTables.PROFILES, SyncTables.USER_PREFERENCES, "app_limits")
        val unexpected = sqlTables.keys - expectedKnown
        assertTrue("schema has tables the app does not sync: $unexpected", unexpected.isEmpty())

        // app_limits is legacy: still created and protected, but never written by the app.
        assertTrue("app_limits must remain protected in the schema", sqlTables.containsKey("app_limits"))
        assertTrue(
            "no app-side table may map to the legacy app_limits table",
            SYNCED_ROOM_TABLES.none { it.cloudTable == "app_limits" }
        )
    }

    // ---- 2. columns ---------------------------------------------------------------------

    @Test
    fun `mapper columns and schema columns agree for every synced table`() {
        for (table in appTables) {
            val sql = sqlTables[table.meta.cloudTable]
            assertNotNull("${table.meta.cloudTable} missing from the schema", sql)
            val written = keys(table.toJson())
            val missingInSql = written - sql!!.columns
            assertTrue(
                "${table.meta.cloudTable}: CloudJson writes columns that SQL does not declare: $missingInSql",
                missingInSql.isEmpty()
            )
            val unknownInSql = sql.columns - written - serverManagedColumns
            assertTrue(
                "${table.meta.cloudTable}: SQL declares columns no mapper writes: $unknownInSql",
                unknownInSql.isEmpty()
            )
        }
    }

    @Test
    fun `no mapper writes a server managed column`() {
        // created_at/updated_at/user_id are the server's; writing them from the client would let a
        // device move another device's watermark or claim ownership of a row.
        for (table in appTables) {
            val written = keys(table.toJson())
            assertFalse(
                "${table.meta.cloudTable} writes user_id",
                written.contains("user_id")
            )
            assertFalse(
                "${table.meta.cloudTable} writes updated_at",
                written.contains("updated_at")
            )
            // `blocked_keywords.created_at` is deliberately the app's own millis column.
            if (table.meta.cloudTable != SyncTables.BLOCKED_KEYWORDS) {
                assertFalse(
                    "${table.meta.cloudTable} writes the server created_at column",
                    written.contains("created_at")
                )
            }
        }
    }

    // ---- 3. primary keys ----------------------------------------------------------------

    @Test
    fun `the SQL primary key matches the app's declared key`() {
        for (table in appTables) {
            val sql = sqlTables[table.meta.cloudTable]!!
            assertEquals(
                "${table.meta.cloudTable} primary key drift",
                table.expectedPkColumns.toSet(),
                sql.primaryKey.toSet()
            )
            // Declared key must also match TableMeta.pkField where the app uses one
            // (blocked_keywords uses a natural key and leaves pkField empty).
            if (table.meta.pkField.isNotEmpty()) {
                assertTrue(
                    "${table.meta.cloudTable}: pkField '${table.meta.pkField}' is not in the SQL key " +
                        sql.primaryKey,
                    sql.primaryKey.contains(table.meta.pkField)
                )
            }
        }
    }

    // ---- 4. round trip ------------------------------------------------------------------

    @Test
    fun `a schedule row round-trips through cloud json unchanged`() {
        val original = FocusScheduleEntity(
            id = "sch-round-trip",
            title = "Evening Revision",
            daysOfWeek = "MON,WED,FRI",
            startTime = "20:15",
            endTime = "22:00",
            isEnabled = true,
            isAutoStartSession = false,
            mode = "POMODORO",
            subjectName = "Chemistry",
            colorHex = "#059669",
            repeatEnabled = true,
            scheduledDateMillis = 0L,
            breakMinutes = 10,
            description = "Revise organic chemistry",
            blockedAppPackages = "com.instagram.android,com.zhiliaoapp.musically",
            blockNotifications = true,
            createdAt = 1_700_000_000_000L
        )
        val restored = CloudJson.scheduleFromCloud(CloudJson.focusScheduleToJson(original))
        assertEquals(original, restored)
    }

    @Test
    fun `history rows round-trip through cloud json`() {
        // issue 13: the history tables are append-only and are the account's only record of study
        // time, so a mangled round-trip is unrecoverable. Each assertion covers exactly the columns
        // that travel (local-only id/createdAt surrogates excluded).
        val session = SessionRecordEntity(
            id = "sess-rt",
            mode = SessionMode.POMODORO,
            title = "Deep Work",
            subject = "Physics",
            topic = "Optics",
            goal = "Finish chapter 4",
            plannedDurationMillis = 25 * 60_000L,
            actualDurationMillis = 24 * 60_000L,
            startTime = 1_700_000_000_000L,
            endTime = 1_700_001_440_000L,
            completed = true,
            cancelled = false,
            pomodoroCycles = 4,
            completedPomodoroCycles = 3
        )
        assertEquals(session, CloudJson.sessionRecordFromCloud(CloudJson.sessionRecordToJson(session)))

        val activity = StudyActivityEntity(
            id = "act-rt",
            sessionId = "sess-rt",
            activityType = StudyActivityType.YOUTUBE_STUDY,
            source = StudyActivitySource.YOUTUBE,
            subject = "Physics",
            topic = "Optics",
            channelName = "Physics Wallah",
            channelId = "UC123",
            videoTitle = "Ray optics in one shot",
            startedAt = 1_700_000_000_000L,
            endedAt = 1_700_001_000_000L,
            durationMillis = 1_000_000L
        )
        assertEquals(activity, CloudJson.studyActivityFromCloud(CloudJson.studyActivityToJson(activity)))

        // `blocked_attempts` uploads `eventId` as the identity and keeps the Room autogen `id`
        // local, so the two local-only columns are excluded from the comparison.
        val attempt = BlockedAttemptEntity(
            eventId = "event-rt",
            timestamp = 1_700_000_000_000L,
            eventType = BlockedEventType.WEBSITE_BLOCKED,
            source = BlockedEventSource.ACCESSIBILITY_SERVICE,
            packageName = "com.android.chrome",
            appName = "Chrome",
            domain = "example.com",
            matchedKeyword = "shorts",
            sessionId = "sess-rt"
        )
        val restoredAttempt = CloudJson.blockedAttemptFromCloud(CloudJson.blockedAttemptToJson(attempt))
        assertEquals(attempt.eventId, restoredAttempt.eventId)
        assertEquals(attempt.eventType, restoredAttempt.eventType)
        assertEquals(attempt.source, restoredAttempt.source)
        assertEquals(attempt.packageName, restoredAttempt.packageName)
        assertEquals(attempt.domain, restoredAttempt.domain)
        assertEquals(attempt.matchedKeyword, restoredAttempt.matchedKeyword)
        assertEquals(attempt.sessionId, restoredAttempt.sessionId)
        assertEquals(attempt.timestamp, restoredAttempt.timestamp)
        // Columns the app did not observe stay null rather than being synthesised.
        assertNull(restoredAttempt.ruleRef)
        assertNull(restoredAttempt.deviceId)

        val card = ScratchCardEntity(
            sessionId = "sess-rt",
            createdAt = 1_700_000_000_000L,
            rewardType = "STREAK_FIRE",
            rewardEmoji = "🔥",
            rewardTitle = "3 days",
            rewardMessage = "Keep the streak alive",
            studyMinutes = 120,
            isRevealed = false
        )
        assertEquals(card, CloudJson.scratchCardFromCloud(CloudJson.scratchCardToJson(card)))
    }

    // ---- 4b. pull modes + natural keys ---------------------------------------------------

    @Test
    fun `config tables reconcile and history tables are append-only`() {
        // A mix-up here is silent and expensive: a HISTORY table reconciled as RECONCILE would delete
        // local history when the cloud copy is momentarily unreadable, and a config table treated as
        // append-only would never propagate a local delete.
        val history = setOf(
            SyncTables.SESSION_RECORDS,
            SyncTables.STUDY_ACTIVITIES,
            SyncTables.BREAK_RECORDS,
            SyncTables.BLOCKED_ATTEMPTS,
            SyncTables.SCRATCH_CARDS
        )
        for (meta in SYNCED_ROOM_TABLES) {
            val expected = if (meta.tableKey in history) PullMode.HISTORY else PullMode.RECONCILE
            assertEquals("${meta.tableKey} pull mode", expected, meta.pullMode)
        }
        assertEquals(5, SYNCED_ROOM_TABLES.count { it.pullMode == PullMode.HISTORY })
    }

    @Test
    fun `keyword natural keys are unambiguous and match the cloud columns`() {
        // The cloud row's identity is (keyword_type, keyword_value); the outbox key is derived from
        // the same two values by SyncKeycode, so both must round-trip identically for every keyword.
        val cases = listOf(
            "block" to "shorts",
            "allow" to "physics wallah",
            "block" to "a:b:1:c", // a keyword containing the separator must not confuse the decoder
            "block" to ""
        )
        for ((type, keyword) in cases) {
            val key = SyncKeycode.keywordKey(type, keyword)
            assertEquals(type to keyword, SyncKeycode.decodeKeywordKey(key))
        }
        assertNull(SyncKeycode.decodeKeywordKey("block"))
        assertNull(SyncKeycode.decodeKeywordKey("block:notanumber:x"))
        assertNull(SyncKeycode.decodeKeywordKey("block:5:abc"))

        val row = CloudJson.keywordToJson(KeywordEntity(keyword = "shorts", type = "block"))
        assertTrue(row.has("keyword_type"))
        assertTrue(row.has("keyword_value"))
        assertEquals("block", row.getString("keyword_type"))
        assertEquals("shorts", row.getString("keyword_value"))
    }

    // ---- 5. strict parsing (issue 10) ----------------------------------------------------

    @Test
    fun `an unknown session mode rejects the row instead of defaulting to TIMER`() {
        val row = CloudJson.sessionRecordToJson(
            SessionRecordEntity(
                id = "sess-x",
                mode = SessionMode.TIMER,
                title = "t",
                subject = "s",
                topic = "tp",
                goal = "g",
                plannedDurationMillis = 1L,
                actualDurationMillis = 1L,
                startTime = 1L,
                endTime = 2L,
                completed = true,
                cancelled = false
            )
        ).put("mode", "DEEP_WORK")

        val failure = runCatching { CloudJson.sessionRecordFromCloud(row) }.exceptionOrNull()
        assertTrue("expected CloudDataException, got $failure", failure is CloudDataException)
        assertEquals("mode", (failure as CloudDataException).field)
    }

    @Test
    fun `an invalid schedule time rejects the row instead of being stored as 09 00`() {
        val row = CloudJson.focusScheduleToJson(
            FocusScheduleEntity(id = "sch-bad", title = "Bad")
        ).put("startTime", "9:5")

        val failure = runCatching { CloudJson.scheduleFromCloud(row) }.exceptionOrNull()
        assertTrue("expected CloudDataException, got $failure", failure is CloudDataException)
        assertEquals("startTime", (failure as CloudDataException).field)
    }

    @Test
    fun `an unknown day list rejects the row instead of defaulting to weekdays`() {
        val row = CloudJson.focusScheduleToJson(FocusScheduleEntity(id = "sch-bad", title = "Bad"))
            .put("daysOfWeek", "MON,SMURF")
        val failure = runCatching { CloudJson.scheduleFromCloud(row) }.exceptionOrNull()
        assertTrue("expected CloudDataException, got $failure", failure is CloudDataException)
        assertEquals("daysOfWeek", (failure as CloudDataException).field)
    }

    @Test
    fun `a missing identity column rejects the row instead of colliding on an empty key`() {
        val row = JSONObject()
            .put("appName", "Instagram")
            .put("isEnabled", true)
        val failure = runCatching { CloudJson.blockedAppFromCloud(row) }.exceptionOrNull()
        assertTrue("expected CloudDataException, got $failure", failure is CloudDataException)
        assertEquals("packageName", (failure as CloudDataException).field)
    }

    @Test
    fun `wrongly typed scalar columns reject the row`() {
        val row = CloudJson.scratchCardToJson(
            ScratchCardEntity(
                sessionId = "sess-1",
                createdAt = 1L,
                rewardType = "QUOTE",
                rewardEmoji = "🎉",
                rewardTitle = "t",
                rewardMessage = "m",
                studyMinutes = 30
            )
        ).put("studyMinutes", "not a number")
        val failure = runCatching { CloudJson.scratchCardFromCloud(row) }.exceptionOrNull()
        assertTrue("expected CloudDataException, got $failure", failure is CloudDataException)
        assertEquals("studyMinutes", (failure as CloudDataException).field)
    }

    @Test
    fun `absent optional columns still use their documented defaults`() {
        // Partial payloads are legitimate (nullable columns), so absence must not reject the row.
        val row = JSONObject()
            .put("id", "sub-1")
            .put("name", "Physics")
        val subject = CloudJson.subjectFromCloud(row)
        assertEquals("Physics", subject.name)
        assertEquals("#7C3AED", subject.colorHex)
        assertFalse(subject.isArchived)
    }

    // ---- 6. outbox diagnostics ----------------------------------------------------------

    @Test
    fun `rejected cloud rows are recorded for diagnostics`() {
        val before = SyncTracker.rejectedCloudRows.value.size
        SyncTracker.recordRejectedCloudRow(
            table = "session_records",
            rowKey = "sess-1",
            cause = CloudDataException("session_records", "mode", "DEEP_WORK", "unknown SessionMode value")
        )
        val after = SyncTracker.rejectedCloudRows.value
        assertEquals(before + 1, after.size)
        assertEquals("session_records", after.first().table)
        assertEquals("mode", after.first().field)
        assertTrue(after.first().reason.contains("DEEP_WORK"))
    }
}
