package com.example.cloud.sync

import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteException
import com.example.cloud.auth.AuthRepository
import com.example.cloud.auth.AuthState
import com.example.cloud.firestore.OperationType
import com.example.cloud.firestore.handleFirestoreError
import com.example.cloud.net.ConnectivityMonitor
import com.example.data.local.FocusShieldDatabase
import com.example.data.local.dao.AppLimitDao
import com.example.data.local.dao.AppLimitSessionDao
import com.example.data.local.dao.BlockedAppDao
import com.example.data.local.dao.BlockedAttemptDao
import com.example.data.local.dao.BlockedWebsiteDao
import com.example.data.local.dao.BreakRecordDao
import com.example.data.local.dao.CloudBulkDao
import com.example.data.local.dao.DailyAppUsageDao
import com.example.data.local.dao.DailyUnlockDao
import com.example.data.local.dao.FocusScheduleDao
import com.example.data.local.dao.KeywordDao
import com.example.data.local.dao.ScratchCardDao
import com.example.data.local.dao.SessionDao
import com.example.data.local.dao.StudyActivityDao
import com.example.data.local.dao.StudyChannelDao
import com.example.data.local.dao.StudyPlanDao
import com.example.data.local.dao.SubjectDao
import com.example.data.local.dao.SyncOutboxDao
import com.example.data.local.dao.TopicDao
import com.example.data.local.entity.BlockedAppEntity
import com.example.data.local.entity.BlockedAttemptEntity
import com.example.data.local.entity.BlockedWebsiteEntity
import com.example.data.local.entity.BreakRecordEntity
import com.example.data.local.entity.FocusScheduleEntity
import com.example.data.local.entity.KeywordEntity
import com.example.data.local.entity.ScratchCardEntity
import com.example.data.local.entity.SessionRecordEntity
import com.example.data.local.entity.StudyActivityEntity
import com.example.data.local.entity.StudyChannelEntity
import com.example.data.local.entity.StudyPlanEntity
import com.example.data.local.entity.SubjectEntity
import com.example.data.local.entity.SyncOutboxEntity
import com.example.data.local.entity.TopicEntity
import com.example.data.model.SessionMode
import com.example.data.preferences.FocusPreferences
import com.example.data.preferences.FocusPreferencesRepository
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SyncReliabilityAndSecurityTest {

    private lateinit var context: Context
    private lateinit var database: FakeFocusShieldDatabase
    private lateinit var installState: FakeCloudInstallState
    private lateinit var preferencesRepository: FakeFocusPreferencesRepository
    private lateinit var fakeAuth: FakeAuthRepository
    private lateinit var fakeConnectivity: FakeConnectivityMonitor
    private lateinit var fakeCloudStore: FakeSyncCloudStore

    @Before
    fun setUp() {
        context = ContextWrapper(null)
        database = FakeFocusShieldDatabase(context)
        installState = FakeCloudInstallState(context)
        preferencesRepository = FakeFocusPreferencesRepository(context)
        fakeAuth = FakeAuthRepository(
            context = context,
            initialUid = "uid_scholar_1",
            initialEmail = "scholar@example.com"
        )
        fakeConnectivity = FakeConnectivityMonitor(context, initialOnline = true)
        fakeCloudStore = FakeSyncCloudStore()
    }

    // -------------------------------------------------------------------------
    // 1. Firestore write failure does not result in successful synchronization
    // -------------------------------------------------------------------------
    @Test
    fun `firestore write failure does not result in successful synchronization and preserves outbox`() = runTest {
        database.syncOutboxDao().insert(
            SyncOutboxEntity(
                tableName = SyncTables.STUDY_CHANNELS,
                rowId = "chan_1",
                op = SyncTracker.OP_UPSERT,
                payload = "{\"id\":\"chan_1\"}"
            )
        )
        database.sessionDao().insertSession(
            SessionRecordEntity(
                id = "sess_1",
                mode = SessionMode.TIMER,
                title = "Physics - Mechanics",
                subject = "Physics",
                topic = "Mechanics",
                goal = "Deep Focus",
                plannedDurationMillis = 25 * 60_000L,
                actualDurationMillis = 25 * 60_000L,
                startTime = 1_000L,
                endTime = 1_501_000L,
                completed = true,
                cancelled = false
            )
        )

        fakeCloudStore.failOnSetDocument = IllegalStateException("Firestore write rejected")

        val engine = SyncEngine(
            database = database,
            installState = installState,
            preferencesRepository = preferencesRepository,
            authRepository = fakeAuth,
            cloudStore = fakeCloudStore
        )

        val outcome = engine.runCycle()
        assertTrue(
            "Expected SyncCycleOutcome.Failed when Firestore write fails, got $outcome",
            outcome is SyncCycleOutcome.Failed
        )
        assertEquals(
            "Pending outbox operation must be preserved when Firestore write fails",
            1,
            database.syncOutboxDao().countPending()
        )
    }

    // -------------------------------------------------------------------------
    // 2. Firestore read failure preserves pending operations
    // -------------------------------------------------------------------------
    @Test
    fun `firestore read failure preserves pending operations`() = runTest {
        database.syncOutboxDao().insert(
            SyncOutboxEntity(
                tableName = SyncTables.STUDY_PLANS,
                rowId = "plan_1",
                op = SyncTracker.OP_UPSERT,
                payload = "{\"id\":\"plan_1\"}"
            )
        )

        fakeCloudStore.failOnListCollection = "sessions"

        val engine = SyncEngine(
            database = database,
            installState = installState,
            preferencesRepository = preferencesRepository,
            authRepository = fakeAuth,
            cloudStore = fakeCloudStore
        )

        val outcome = engine.runCycle()
        assertTrue(
            "Expected SyncCycleOutcome.Failed when Firestore read fails, got $outcome",
            outcome is SyncCycleOutcome.Failed
        )
        assertEquals(
            "Outbox must remain intact after Firestore read failure",
            1,
            database.syncOutboxDao().countPending()
        )
    }

    // -------------------------------------------------------------------------
    // 3. Firestore deletion failure does not clear the outbox
    // -------------------------------------------------------------------------
    @Test
    fun `firestore deletion failure does not clear the outbox`() = runTest {
        database.syncOutboxDao().insert(
            SyncOutboxEntity(
                tableName = SyncTables.STUDY_PLANS,
                rowId = "plan_to_delete",
                op = SyncTracker.OP_DELETE,
                payload = null
            )
        )
        fakeCloudStore.failOnDeleteDocument = IllegalStateException("Delete permission denied")

        val engine = SyncEngine(
            database = database,
            installState = installState,
            preferencesRepository = preferencesRepository,
            authRepository = fakeAuth,
            cloudStore = fakeCloudStore
        )

        val outcome = engine.runCycle()
        assertTrue(
            "Expected SyncCycleOutcome.Failed when Firestore delete fails, got $outcome",
            outcome is SyncCycleOutcome.Failed
        )
        assertEquals(
            "Outbox delete entry must not be cleared when cloud deletion fails",
            1,
            database.syncOutboxDao().countPending()
        )
    }

    // -------------------------------------------------------------------------
    // 4. Local database read failure does not silently become an empty collection
    // -------------------------------------------------------------------------
    @Test
    fun `local database read failure does not silently become an empty collection`() = runTest {
        database.syncOutboxDao().insert(
            SyncOutboxEntity(
                tableName = SyncTables.STUDY_CHANNELS,
                rowId = "chan_local_1",
                op = SyncTracker.OP_UPSERT,
                payload = "{\"id\":\"chan_local_1\"}"
            )
        )

        val failingSessionDao = Proxy.newProxyInstance(
            SessionDao::class.java.classLoader,
            arrayOf(SessionDao::class.java)
        ) { _, method, _ ->
            if (method.name == "getAllSessionsAscending") {
                throw SQLiteException("Simulated disk I/O error reading session_records")
            }
            throw UnsupportedOperationException("Unexpected call: ${method.name}")
        } as SessionDao

        val failingDatabase = FailingSessionReadDatabase(database, failingSessionDao)

        val engine = SyncEngine(
            database = failingDatabase,
            installState = installState,
            preferencesRepository = preferencesRepository,
            authRepository = fakeAuth,
            cloudStore = fakeCloudStore
        )

        val outcome = engine.runCycle()
        assertTrue(
            "Expected SyncCycleOutcome.Failed when local database read throws, got $outcome",
            outcome is SyncCycleOutcome.Failed
        )
        assertEquals(
            "Outbox must not be cleared when a local database read fails",
            1,
            database.syncOutboxDao().countPending()
        )
    }

    // -------------------------------------------------------------------------
    // 5. The outbox clears only after successful synchronization
    // -------------------------------------------------------------------------
    @Test
    fun `outbox clears only after all synchronization operations succeed`() = runTest {
        database.syncOutboxDao().insert(
            SyncOutboxEntity(
                tableName = SyncTables.STUDY_CHANNELS,
                rowId = "chan_sync_1",
                op = SyncTracker.OP_UPSERT,
                payload = "{\"id\":\"chan_sync_1\"}"
            )
        )
        assertEquals(1, database.syncOutboxDao().countPending())

        // First attempt: fail halfway through (when syncing study_plans)
        fakeCloudStore.failOnListCollection = "study_plans"
        val engine = SyncEngine(
            database = database,
            installState = installState,
            preferencesRepository = preferencesRepository,
            authRepository = fakeAuth,
            cloudStore = fakeCloudStore
        )

        val failedOutcome = engine.runCycle()
        assertTrue(failedOutcome is SyncCycleOutcome.Failed)
        assertEquals(
            "Outbox must remain populated when a mid-cycle step fails",
            1,
            database.syncOutboxDao().countPending()
        )

        // Second attempt: clear failure injection and run to completion
        fakeCloudStore.failOnListCollection = null
        val successOutcome = engine.runCycle()
        assertEquals(SyncCycleOutcome.Synced, successOutcome)
        assertEquals(
            "Outbox must be cleared once all sync steps succeed",
            0,
            database.syncOutboxDao().countPending()
        )
        assertEquals(0, SyncTracker.pendingCount.value)
    }

    // -------------------------------------------------------------------------
    // 6. An authenticated UID mismatch prevents synchronization
    // -------------------------------------------------------------------------
    @Test
    fun `authenticated UID mismatch prevents synchronization in restore and gateway`() = runTest {
        val engine = SyncEngine(
            database = database,
            installState = installState,
            preferencesRepository = preferencesRepository,
            authRepository = fakeAuth,
            cloudStore = fakeCloudStore
        )
        val gateway = SyncGateway(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            authRepository = fakeAuth,
            connectivityMonitor = fakeConnectivity,
            engine = engine,
            installState = installState,
            preferencesRepository = preferencesRepository
        )

        // 6a. Direct restore with a different UID fails without touching Firestore
        val directMismatch = engine.restore("different_uid_999")
        assertTrue(
            "Expected Failed when restore UID does not match authenticated user",
            directMismatch is SyncCycleOutcome.Failed
        )
        assertEquals(0, fakeCloudStore.operationCount)

        // 6b. Gateway restore with a different UID fails without touching Firestore
        val gatewayMismatch = gateway.restore("different_uid_999")
        assertTrue(
            "Expected Failed from SyncGateway.restore when UID mismatches",
            gatewayMismatch is SyncCycleOutcome.Failed
        )
        assertEquals(0, fakeCloudStore.operationCount)

        // 6c. Cross-account local data owner mismatch prevents synchronization
        installState.setDataOwnerAccountId("other_owner_uid")
        val ownerMismatch = engine.restore("uid_scholar_1")
        assertTrue(
            "Expected Failed when local data belongs to a different account",
            ownerMismatch is SyncCycleOutcome.Failed
        )
        assertEquals(0, fakeCloudStore.operationCount)
    }

    // -------------------------------------------------------------------------
    // 7. Error logging does not expose email addresses or user IDs
    // -------------------------------------------------------------------------
    @Test
    fun `error logging does not expose email addresses user IDs or sensitive paths`() {
        val sensitiveUid = "uid_secret_987654321"
        val sensitiveEmail = "private.student@university.edu"
        val sensitivePath = "/users/$sensitiveUid/study_plans/secret_plan"
        val exception = RuntimeException(
            "PERMISSION_DENIED for user $sensitiveUid ($sensitiveEmail) at $sensitivePath token=eyJhbGciOi"
        )

        val payload = handleFirestoreError(
            exception = exception,
            operationType = OperationType.WRITE,
            path = sensitivePath
        )

        // Verify returned JSON payload contains only safe diagnostic metadata
        assertFalse("Payload must not contain UID", payload.contains(sensitiveUid))
        assertFalse("Payload must not contain email", payload.contains(sensitiveEmail))
        assertFalse("Payload must not contain document path", payload.contains("secret_plan"))
        assertFalse("Payload must not contain auth token", payload.contains("eyJhbGciOi"))
        assertTrue("Payload should include operationType", payload.contains("\"operationType\":\"write\""))
        assertTrue("Payload should include errorType", payload.contains("\"errorType\":\"RuntimeException\""))
    }

    // -------------------------------------------------------------------------
    // 8. Existing successful synchronization continues to work
    // -------------------------------------------------------------------------
    @Test
    fun `existing successful synchronization pushes local data and pulls remote data`() = runTest {
        val uid = "uid_scholar_1"
        // Seed local session and study channel
        database.sessionDao().insertSession(
            SessionRecordEntity(
                id = "local_sess_1",
                mode = SessionMode.POMODORO,
                title = "Math - Calculus",
                subject = "Math",
                topic = "Calculus",
                goal = "Exam Prep",
                plannedDurationMillis = 50 * 60_000L,
                actualDurationMillis = 50 * 60_000L,
                startTime = 10_000L,
                endTime = 3_010_000L,
                completed = true,
                cancelled = false,
                pomodoroCycles = 2,
                completedPomodoroCycles = 2
            )
        )
        database.studyChannelDao().insertChannel(
            StudyChannelEntity(
                id = "local_chan_1",
                channelId = "@mitocw",
                channelName = "MIT OpenCourseWare",
                channelUrl = "https://www.youtube.com/@mitocw",
                thumbnailUrl = "",
                isApproved = true
            )
        )
        database.syncOutboxDao().insert(
            SyncOutboxEntity(
                tableName = SyncTables.STUDY_CHANNELS,
                rowId = "local_chan_1",
                op = SyncTracker.OP_UPSERT,
                payload = "{\"id\":\"local_chan_1\"}"
            )
        )

        // Seed remote study plan and remote session in FakeSyncCloudStore
        fakeCloudStore.seedSubcollectionDocument(
            uid = uid,
            collectionName = "sessions",
            docId = "remote_sess_2",
            fields = mapOf(
                "id" to "remote_sess_2",
                "userId" to uid,
                "mode" to "TIMER",
                "title" to "Chemistry - Organic",
                "subject" to "Chemistry",
                "topic" to "Organic",
                "durationMinutes" to 30,
                "actualDurationMillis" to 30 * 60_000L,
                "plannedDurationMillis" to 30 * 60_000L,
                "completed" to true,
                "cancelled" to false,
                "startTime" to 5_000_000L,
                "endTime" to 6_800_000L
            )
        )
        fakeCloudStore.seedSubcollectionDocument(
            uid = uid,
            collectionName = "study_plans",
            docId = "remote_plan_1",
            fields = mapOf(
                "id" to "remote_plan_1",
                "userId" to uid,
                "subjectId" to "sub_chem",
                "subjectName" to "Chemistry",
                "topicName" to "Thermodynamics",
                "plannedDurationMinutes" to 45,
                "targetDate" to 1_730_000_000_000L,
                "dateString" to "2026-11-04",
                "startTime" to "10:00",
                "endTime" to "10:45",
                "isCompleted" to false
            )
        )

        val engine = SyncEngine(
            database = database,
            installState = installState,
            preferencesRepository = preferencesRepository,
            authRepository = fakeAuth,
            cloudStore = fakeCloudStore
        )

        val outcome = engine.restore(uid)
        assertEquals(SyncCycleOutcome.Synced, outcome)

        // Verify local session and channel were pushed to cloud
        val pushedSession = fakeCloudStore.getDocument(uid, "sessions", "local_sess_1")
        assertTrue("Local session must be pushed to cloud", pushedSession.exists())
        assertEquals("Math", pushedSession.get("subject"))

        val pushedChannel = fakeCloudStore.getDocument(uid, "study_channels", "local_chan_1")
        assertTrue("Local study channel must be pushed to cloud", pushedChannel.exists())
        assertEquals("MIT OpenCourseWare", pushedChannel.get("channelName"))

        // Verify remote session and study plan were pulled into local database
        val pulledSession = database.sessionDao().getSessionById("remote_sess_2")
        assertNotNull("Remote session must be pulled into local DB", pulledSession)
        assertEquals("Chemistry", pulledSession?.subject)

        val pulledPlan = database.studyPlanDao().getPlanById("remote_plan_1")
        assertNotNull("Remote study plan must be pulled into local DB", pulledPlan)
        assertEquals("Thermodynamics", pulledPlan?.topicName)

        // Verify outbox was drained and account ownership recorded
        assertEquals(0, database.syncOutboxDao().countPending())
        assertEquals(uid, installState.dataOwnerAccountId())
        assertTrue(uid in installState.migratedAccounts())
    }

    // -------------------------------------------------------------------------
    // 9. Backup rules exclude sensitive session and cloud state SharedPreferences
    // -------------------------------------------------------------------------
    @Test
    fun `backup and data extraction rules exclude active session and cloud state prefs`() {
        val backupRulesFile = File("src/main/res/xml/backup_rules.xml")
            .takeIf { it.exists() }
            ?: File("app/src/main/res/xml/backup_rules.xml")
        val dataExtractionFile = File("src/main/res/xml/data_extraction_rules.xml")
            .takeIf { it.exists() }
            ?: File("app/src/main/res/xml/data_extraction_rules.xml")

        assertTrue("backup_rules.xml must exist", backupRulesFile.exists())
        assertTrue("data_extraction_rules.xml must exist", dataExtractionFile.exists())

        val backupXml = backupRulesFile.readText()
        assertTrue(
            "backup_rules.xml must exclude focus_active_session_prefs.xml",
            backupXml.contains("focus_active_session_prefs.xml")
        )
        assertTrue(
            "backup_rules.xml must exclude focus_shield_cloud_state.xml",
            backupXml.contains("focus_shield_cloud_state.xml")
        )

        val extractionXml = dataExtractionFile.readText()
        val cloudBackupSection = extractionXml.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        val deviceTransferSection = extractionXml.substringAfter("<device-transfer>").substringBefore("</device-transfer>")

        assertTrue(
            "cloud-backup must exclude focus_active_session_prefs.xml",
            cloudBackupSection.contains("focus_active_session_prefs.xml")
        )
        assertTrue(
            "cloud-backup must exclude focus_shield_cloud_state.xml",
            cloudBackupSection.contains("focus_shield_cloud_state.xml")
        )
        assertTrue(
            "device-transfer must exclude focus_active_session_prefs.xml",
            deviceTransferSection.contains("focus_active_session_prefs.xml")
        )
        assertTrue(
            "device-transfer must exclude focus_shield_cloud_state.xml",
            deviceTransferSection.contains("focus_shield_cloud_state.xml")
        )
    }

    // =========================================================================
    // Test Doubles
    // =========================================================================

    private class FakeAuthRepository(
        context: Context,
        var initialUid: String?,
        var initialEmail: String? = "scholar@example.com"
    ) : AuthRepository(context, authProvider = { null }) {

        private val stateFlow = MutableStateFlow<AuthState>(
            if (initialUid != null) {
                AuthState.Authenticated(
                    userId = initialUid!!,
                    email = initialEmail.orEmpty(),
                    displayName = "Focus Scholar",
                    photoUrl = null
                )
            } else {
                AuthState.LocalOnly
            }
        )

        override val authState: StateFlow<AuthState> = stateFlow
        override val isConfigured: Boolean = true
        override fun currentUserId(): String? = initialUid
        override fun currentUserEmail(): String? = initialEmail
        override fun currentDisplayName(): String? = "Focus Scholar"
        override fun currentPhotoUrl(): String? = null
    }

    private class FakeConnectivityMonitor(
        context: Context,
        initialOnline: Boolean = true
    ) : ConnectivityMonitor(context) {
        val onlineFlow = MutableStateFlow(initialOnline)
        override val isOnline: StateFlow<Boolean> = onlineFlow
    }

    private class FakeCloudInstallState(context: Context) : CloudInstallState(context) {
        private var ownerId: String? = null
        private val migrated = mutableSetOf<String>()
        private val known = mutableSetOf<String>()
        private var profileDirtyFlag = false
        private var prefsDirtyFlag = false
        private val watermarks = mutableMapOf<String, String>()
        private val pendingPhotos = mutableMapOf<String, PendingProfilePhoto>()
        private var nextPhotoToken = 0

        override suspend fun installId(): String = "test_install_id"
        override suspend fun dataOwnerAccountId(): String? = ownerId
        override suspend fun setDataOwnerAccountId(uid: String?): Boolean {
            ownerId = uid
            return true
        }
        override suspend fun migratedAccounts(): Set<String> = migrated.toSet()
        override suspend fun markMigrated(uid: String): Boolean {
            migrated.add(uid)
            return true
        }
        override suspend fun knownAccounts(): Set<String> = known.toSet()
        override suspend fun rememberAccount(uid: String) {
            known.add(uid)
        }
        override suspend fun forgetAllAccounts() {
            known.clear()
        }
        override suspend fun historyWatermark(tableKey: String): String? = watermarks[tableKey]
        override suspend fun setHistoryWatermark(tableKey: String, iso: String?) {
            if (iso == null) watermarks.remove(tableKey) else watermarks[tableKey] = iso
        }
        override suspend fun profileDirty(): Boolean = profileDirtyFlag
        override suspend fun setProfileDirty(dirty: Boolean) {
            profileDirtyFlag = dirty
        }
        override suspend fun prefsDirty(): Boolean = prefsDirtyFlag
        override suspend fun setPrefsDirty(dirty: Boolean) {
            prefsDirtyFlag = dirty
        }
        override suspend fun setPendingProfilePhoto(uid: String, photoUri: String?) {
            pendingPhotos[uid] = PendingProfilePhoto(uid, photoUri, "photo_${++nextPhotoToken}")
        }
        override suspend fun pendingProfilePhoto(uid: String): PendingProfilePhoto? = pendingPhotos[uid]
        override suspend fun pendingProfilePhotoUris(): Set<String> =
            pendingPhotos.values.mapNotNull { it.photoUri }.toSet()
        override suspend fun clearPendingProfilePhoto(pending: PendingProfilePhoto) {
            if (pendingPhotos[pending.userId]?.token == pending.token) {
                pendingPhotos.remove(pending.userId)
            }
        }
        override suspend fun resetTransient() {
            profileDirtyFlag = false
            prefsDirtyFlag = false
            watermarks.clear()
        }
    }

    private class FakeFocusPreferencesRepository(context: Context) : FocusPreferencesRepository(context) {
        private val prefsState = MutableStateFlow(FocusPreferences())
        private var avatarPath: String? = null

        override val preferencesFlow: Flow<FocusPreferences> = prefsState
        override suspend fun snapshot(): FocusPreferences = prefsState.value
        override suspend fun cloudAvatarPath(): String? = avatarPath
        override suspend fun setCloudAvatarPath(path: String?) {
            avatarPath = path
        }
        override suspend fun updateDailyGoalMinutes(minutes: Int) {
            prefsState.value = prefsState.value.copy(dailyGoalMinutes = minutes)
        }
        override suspend fun updateMinimumStreakThresholdMinutes(minutes: Int) {
            prefsState.value = prefsState.value.copy(minimumStreakThresholdMinutes = minutes)
        }
        override suspend fun applyCloudPreferences(payloadJson: String) {
            // No-op or lightweight state update in unit test
        }
        override suspend fun applyCloudProfile(
            displayName: String,
            avatarPreset: String,
            motto: String,
            academicGoal: String,
            avatarPath: String?
        ) {
            prefsState.value = prefsState.value.copy(
                userName = displayName,
                userAvatarPreset = avatarPreset,
                userMotto = motto,
                userAcademicGoal = academicGoal
            )
            this.avatarPath = avatarPath
        }
    }

    private class FakeSyncCloudStore : SyncCloudStore {
        private val userDocs = mutableMapOf<String, MutableMap<String, Any?>>()
        private val subcollections = mutableMapOf<Pair<String, String>, MutableMap<String, MutableMap<String, Any?>>>()

        var failOnSetDocument: Exception? = null
        var failOnGetDocument: Exception? = null
        var failOnListCollection: String? = null
        var failOnDeleteDocument: Exception? = null
        var operationCount: Int = 0
            private set

        fun seedSubcollectionDocument(
            uid: String,
            collectionName: String,
            docId: String,
            fields: Map<String, Any?>
        ) {
            val col = subcollections.getOrPut(uid to collectionName) { mutableMapOf() }
            col[docId] = fields.toMutableMap()
        }

        override suspend fun getDocument(
            uid: String,
            collectionName: String?,
            docId: String?
        ): SyncCloudDocument {
            operationCount++
            failOnGetDocument?.let { throw it }
            return if (collectionName == null || docId == null) {
                val fields = userDocs[uid]
                SyncCloudDocument(id = uid, exists = fields != null, fields = fields.orEmpty())
            } else {
                val fields = subcollections[uid to collectionName]?.get(docId)
                SyncCloudDocument(id = docId, exists = fields != null, fields = fields.orEmpty())
            }
        }

        override suspend fun setDocument(
            uid: String,
            collectionName: String?,
            docId: String?,
            payload: Map<String, Any?>
        ) {
            operationCount++
            failOnSetDocument?.let { throw it }
            if (collectionName == null || docId == null) {
                val existing = userDocs.getOrPut(uid) { mutableMapOf() }
                existing.putAll(payload)
            } else {
                val col = subcollections.getOrPut(uid to collectionName) { mutableMapOf() }
                val existing = col.getOrPut(docId) { mutableMapOf() }
                existing.putAll(payload)
            }
        }

        override suspend fun listDocuments(
            uid: String,
            collectionName: String
        ): List<SyncCloudDocument> {
            operationCount++
            if (failOnListCollection == collectionName) {
                throw IllegalStateException("Simulated Firestore read failure on $collectionName")
            }
            val col = subcollections[uid to collectionName].orEmpty()
            return col.entries.map { (id, data) ->
                SyncCloudDocument(id = id, exists = true, fields = data.toMap())
            }
        }

        override suspend fun deleteDocument(
            uid: String,
            collectionName: String,
            docId: String
        ) {
            operationCount++
            failOnDeleteDocument?.let { throw it }
            subcollections[uid to collectionName]?.remove(docId)
        }
    }

    private class FakeFocusShieldDatabase(context: Context) : FocusShieldDatabase() {
        private val outboxEntries = mutableListOf<SyncOutboxEntity>()
        private var nextSeq = 1L

        private val sessionsMap = linkedMapOf<String, SessionRecordEntity>()
        private val plansMap = linkedMapOf<String, StudyPlanEntity>()
        private val channelsMap = linkedMapOf<String, StudyChannelEntity>()
        private val schedulesMap = linkedMapOf<String, FocusScheduleEntity>()
        private val subjectsMap = linkedMapOf<String, SubjectEntity>()
        private val topicsMap = linkedMapOf<String, TopicEntity>()
        private val blockedAppsMap = linkedMapOf<String, BlockedAppEntity>()
        private val blockedWebsitesMap = linkedMapOf<String, BlockedWebsiteEntity>()
        private val keywordsList = mutableListOf<KeywordEntity>()
        private val activitiesMap = linkedMapOf<String, StudyActivityEntity>()
        private val breaksMap = linkedMapOf<String, BreakRecordEntity>()
        private val blockedAttemptsMap = linkedMapOf<String, BlockedAttemptEntity>()
        private val scratchCardsMap = linkedMapOf<String, ScratchCardEntity>()

        private val outboxDaoImpl = object : SyncOutboxDao {
            override suspend fun peekOldest(limit: Int): List<SyncOutboxEntity> =
                outboxEntries.sortedBy { it.seq }.take(limit)

            override suspend fun deleteAllFor(tableName: String, rowId: String) {
                outboxEntries.removeAll { it.tableName == tableName && it.rowId == rowId }
            }

            override suspend fun deleteBySeq(seq: Long) {
                outboxEntries.removeAll { it.seq == seq }
            }

            override suspend fun clearAll() {
                outboxEntries.clear()
            }

            override suspend fun bumpAttemptCount(seq: Long) {
                val idx = outboxEntries.indexOfFirst { it.seq == seq }
                if (idx >= 0) {
                    val cur = outboxEntries[idx]
                    outboxEntries[idx] = cur.copy(attemptCount = cur.attemptCount + 1)
                }
            }

            override suspend fun countPending(): Int = outboxEntries.size

            override suspend fun hasPendingFor(tableName: String, rowId: String): Int =
                outboxEntries.count { it.tableName == tableName && it.rowId == rowId }

            override suspend fun pendingForTable(tableName: String): List<SyncOutboxEntity> =
                outboxEntries.filter { it.tableName == tableName }.sortedBy { it.seq }

            override suspend fun pendingSeqFor(tableName: String, rowId: String): Long? =
                outboxEntries.firstOrNull { it.tableName == tableName && it.rowId == rowId }?.seq

            override suspend fun insert(entity: SyncOutboxEntity) {
                val assigned = if (entity.seq == 0L) entity.copy(seq = nextSeq++) else entity
                outboxEntries.removeAll { it.seq == assigned.seq }
                outboxEntries.add(assigned)
            }
        }

        private val sessionDaoImpl = object : SessionDao {
            override suspend fun insertSession(session: SessionRecordEntity) {
                sessionsMap[session.id] = session
            }
            override suspend fun updateSession(session: SessionRecordEntity) {
                sessionsMap[session.id] = session
            }
            override suspend fun getSessionById(id: String): SessionRecordEntity? = sessionsMap[id]
            override fun getAllSessionsFlow(): Flow<List<SessionRecordEntity>> = emptyFlow()
            override fun getRecentSessionsFlow(limit: Int): Flow<List<SessionRecordEntity>> = emptyFlow()
            override fun getSessionsBetweenFlow(startTime: Long, endTime: Long): Flow<List<SessionRecordEntity>> = emptyFlow()
            override suspend fun getSessionsBetween(startTime: Long, endTime: Long): List<SessionRecordEntity> = emptyList()
            override fun getSessionsForSubjectFlow(subject: String): Flow<List<SessionRecordEntity>> = emptyFlow()
            override fun getCompletedSessionsCountSinceFlow(startOfDay: Long): Flow<Int> = emptyFlow()
            override fun getCompletedSessionsCountBetweenFlow(startTime: Long, endTime: Long): Flow<Int> = emptyFlow()
            override fun getTotalStudyTimeMillisSinceFlow(startOfDay: Long): Flow<Long?> = emptyFlow()
            override fun getTotalStudyTimeMillisBetweenFlow(startTime: Long, endTime: Long): Flow<Long?> = emptyFlow()
            override fun getAllTimeStudyTimeMillisFlow(): Flow<Long?> = emptyFlow()
            override fun getAllTimeSessionsCountFlow(): Flow<Int> = emptyFlow()
            override fun getAllTimeCompletedSessionsCountFlow(): Flow<Int> = emptyFlow()
            override suspend fun getAllSessionsAscending(): List<SessionRecordEntity> =
                sessionsMap.values.sortedBy { it.startTime }
            override suspend fun deleteSessionById(id: String) {
                sessionsMap.remove(id)
            }
            override suspend fun deleteAllSessions() {
                sessionsMap.clear()
            }
        }

        private val studyPlanDaoImpl = object : StudyPlanDao {
            override suspend fun insertPlan(plan: StudyPlanEntity) {
                plansMap[plan.id] = plan
            }
            override suspend fun insertAll(plans: List<StudyPlanEntity>) {
                plans.forEach { if (it.id !in plansMap) plansMap[it.id] = it }
            }
            override suspend fun updatePlan(plan: StudyPlanEntity) {
                plansMap[plan.id] = plan
            }
            override fun getAllPlansFlow(): Flow<List<StudyPlanEntity>> = emptyFlow()
            override fun getPlansForDateFlow(startOfDay: Long, endOfDay: Long): Flow<List<StudyPlanEntity>> = emptyFlow()
            override suspend fun getPlansForDate(startOfDay: Long, endOfDay: Long): List<StudyPlanEntity> = emptyList()
            override suspend fun getAllPlans(): List<StudyPlanEntity> = plansMap.values.toList()
            override fun getPlansByDateStringFlow(dateString: String): Flow<List<StudyPlanEntity>> = emptyFlow()
            override suspend fun getPlanById(id: String): StudyPlanEntity? = plansMap[id]
            override suspend fun updatePlanCompletion(id: String, isCompleted: Boolean) {
                plansMap[id]?.let {
                    plansMap[id] = it.copy(
                        isCompleted = isCompleted,
                        status = if (isCompleted) "COMPLETED" else "PLANNED"
                    )
                }
            }
            override suspend fun deletePlanById(id: String) {
                plansMap.remove(id)
            }
            override suspend fun getPlanCount(): Int = plansMap.size
        }

        private val studyChannelDaoImpl = object : StudyChannelDao {
            override suspend fun insertChannel(channel: StudyChannelEntity) {
                channelsMap[channel.id] = channel
            }
            override suspend fun insertAll(channels: List<StudyChannelEntity>) {
                channels.forEach { if (it.id !in channelsMap) channelsMap[it.id] = it }
            }
            override suspend fun updateChannel(channel: StudyChannelEntity) {
                channelsMap[channel.id] = channel
            }
            override fun getAllChannelsFlow(): Flow<List<StudyChannelEntity>> = emptyFlow()
            override suspend fun getAllChannels(): List<StudyChannelEntity> = channelsMap.values.toList()
            override fun getApprovedChannelsFlow(): Flow<List<StudyChannelEntity>> = emptyFlow()
            override suspend fun getApprovedChannels(): List<StudyChannelEntity> =
                channelsMap.values.filter { it.isApproved }
            override suspend fun getChannelByChannelId(channelId: String): StudyChannelEntity? =
                channelsMap.values.firstOrNull { it.channelId == channelId }
            override suspend fun setApprovalStatus(id: String, isApproved: Boolean) {
                channelsMap[id]?.let { channelsMap[id] = it.copy(isApproved = isApproved) }
            }
            override suspend fun updateThumbnailUrl(id: String, thumbnailUrl: String) {
                channelsMap[id]?.let { channelsMap[id] = it.copy(thumbnailUrl = thumbnailUrl) }
            }
            override suspend fun updateThumbnailByChannelId(channelId: String, thumbnailUrl: String) {
                channelsMap.values.filter { it.channelId == channelId }.forEach {
                    channelsMap[it.id] = it.copy(thumbnailUrl = thumbnailUrl)
                }
            }
            override suspend fun getChannelById(id: String): StudyChannelEntity? = channelsMap[id]
            override suspend fun getChannelCount(): Int = channelsMap.size
            override suspend fun deleteChannelById(id: String) {
                channelsMap.remove(id)
            }
            override suspend fun deleteAllChannels() {
                channelsMap.clear()
            }
        }

        private val focusScheduleDaoImpl = object : FocusScheduleDao {
            override fun getAllSchedulesFlow(): Flow<List<FocusScheduleEntity>> = emptyFlow()
            override suspend fun getAllSchedules(): List<FocusScheduleEntity> = schedulesMap.values.toList()
            override suspend fun getEnabledSchedules(): List<FocusScheduleEntity> =
                schedulesMap.values.filter { it.isEnabled }
            override suspend fun getScheduleById(id: String): FocusScheduleEntity? = schedulesMap[id]
            override suspend fun insertSchedule(schedule: FocusScheduleEntity) {
                schedulesMap[schedule.id] = schedule
            }
            override suspend fun updateSchedule(schedule: FocusScheduleEntity) {
                schedulesMap[schedule.id] = schedule
            }
            override suspend fun setScheduleEnabled(id: String, isEnabled: Boolean) {
                schedulesMap[id]?.let { schedulesMap[id] = it.copy(isEnabled = isEnabled) }
            }
            override suspend fun deleteScheduleById(id: String) {
                schedulesMap.remove(id)
            }
            override suspend fun getScheduleCount(): Int = schedulesMap.size
        }

        private val scratchCardDaoImpl = object : ScratchCardDao {
            override suspend fun insert(card: ScratchCardEntity): Long {
                if (card.sessionId !in scratchCardsMap) {
                    scratchCardsMap[card.sessionId] = card
                }
                return 1L
            }
            override suspend fun getCardForSession(sessionId: String): ScratchCardEntity? =
                scratchCardsMap[sessionId]
            override suspend fun getUnrevealedCardForSession(sessionId: String): ScratchCardEntity? =
                scratchCardsMap[sessionId]?.takeIf { !it.isRevealed }
            override suspend fun getLatestUnrevealedCard(): ScratchCardEntity? =
                scratchCardsMap.values.filter { !it.isRevealed }.maxByOrNull { it.createdAt }
            override fun getLatestUnrevealedCardFlow(): Flow<ScratchCardEntity?> = emptyFlow()
            override suspend fun markRevealed(sessionId: String) {
                scratchCardsMap[sessionId]?.let { scratchCardsMap[sessionId] = it.copy(isRevealed = true) }
            }
            override suspend fun getRecentCards(limit: Int): List<ScratchCardEntity> =
                scratchCardsMap.values.sortedByDescending { it.createdAt }.take(limit)
            override fun getRevealedCountFlow(): Flow<Int> = emptyFlow()
        }

        private val cloudBulkDaoImpl = object : CloudBulkDao {
            override suspend fun subjects(): List<SubjectEntity> = subjectsMap.values.toList()
            override suspend fun upsertSubjects(rows: List<SubjectEntity>) {
                rows.forEach { subjectsMap[it.id] = it }
            }
            override suspend fun clearSubjects() = subjectsMap.clear()

            override suspend fun topics(): List<TopicEntity> = topicsMap.values.toList()
            override suspend fun upsertTopics(rows: List<TopicEntity>) {
                rows.forEach { topicsMap[it.id] = it }
            }
            override suspend fun clearTopics() = topicsMap.clear()

            override suspend fun plans(): List<StudyPlanEntity> = plansMap.values.toList()
            override suspend fun upsertPlans(rows: List<StudyPlanEntity>) {
                rows.forEach { plansMap[it.id] = it }
            }
            override suspend fun clearPlans() = plansMap.clear()

            override suspend fun schedules(): List<FocusScheduleEntity> = schedulesMap.values.toList()
            override suspend fun upsertSchedules(rows: List<FocusScheduleEntity>) {
                rows.forEach { schedulesMap[it.id] = it }
            }
            override suspend fun clearSchedules() = schedulesMap.clear()

            override suspend fun blockedApps(): List<BlockedAppEntity> = blockedAppsMap.values.toList()
            override suspend fun upsertBlockedApps(rows: List<BlockedAppEntity>) {
                rows.forEach { blockedAppsMap[it.packageName] = it }
            }
            override suspend fun clearBlockedApps() = blockedAppsMap.clear()

            override suspend fun blockedWebsites(): List<BlockedWebsiteEntity> = blockedWebsitesMap.values.toList()
            override suspend fun upsertBlockedWebsites(rows: List<BlockedWebsiteEntity>) {
                rows.forEach { blockedWebsitesMap[it.domain] = it }
            }
            override suspend fun clearBlockedWebsites() = blockedWebsitesMap.clear()

            override suspend fun channels(): List<StudyChannelEntity> = channelsMap.values.toList()
            override suspend fun upsertChannels(rows: List<StudyChannelEntity>) {
                rows.forEach { channelsMap[it.id] = it }
            }
            override suspend fun clearChannels() = channelsMap.clear()

            override suspend fun keywords(): List<KeywordEntity> = keywordsList.toList()
            override suspend fun upsertKeywords(rows: List<KeywordEntity>) {
                keywordsList.addAll(rows)
            }
            override suspend fun clearKeywords() = keywordsList.clear()

            override suspend fun sessions(): List<SessionRecordEntity> = sessionsMap.values.toList()
            override suspend fun insertSessionsMissing(rows: List<SessionRecordEntity>) {
                rows.forEach { if (it.id !in sessionsMap) sessionsMap[it.id] = it }
            }
            override suspend fun clearSessions() = sessionsMap.clear()

            override suspend fun activities(): List<StudyActivityEntity> = activitiesMap.values.toList()
            override suspend fun insertActivitiesMissing(rows: List<StudyActivityEntity>) {
                rows.forEach { if (it.id !in activitiesMap) activitiesMap[it.id] = it }
            }
            override suspend fun clearActivities() = activitiesMap.clear()

            override suspend fun breaks(): List<BreakRecordEntity> = breaksMap.values.toList()
            override suspend fun insertBreaksMissing(rows: List<BreakRecordEntity>) {
                rows.forEach { if (it.id !in breaksMap) breaksMap[it.id] = it }
            }
            override suspend fun clearBreaks() = breaksMap.clear()

            override suspend fun blockedAttempts(): List<BlockedAttemptEntity> = blockedAttemptsMap.values.toList()
            override suspend fun insertBlockedAttemptsMissing(rows: List<BlockedAttemptEntity>) {
                rows.forEach { if (it.eventId !in blockedAttemptsMap) blockedAttemptsMap[it.eventId] = it }
            }
            override suspend fun clearBlockedAttempts() = blockedAttemptsMap.clear()

            override suspend fun scratchCards(): List<ScratchCardEntity> = scratchCardsMap.values.toList()
            override suspend fun insertScratchCardsMissing(rows: List<ScratchCardEntity>) {
                rows.forEach { if (it.sessionId !in scratchCardsMap) scratchCardsMap[it.sessionId] = it }
            }
            override suspend fun clearScratchCards() = scratchCardsMap.clear()
        }

        private inline fun <reified T> unsupportedDao(): T =
            Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
                throw UnsupportedOperationException("Unexpected DAO call: ${method.name}")
            } as T

        override fun sessionDao(): SessionDao = sessionDaoImpl
        override fun subjectDao(): SubjectDao = unsupportedDao()
        override fun topicDao(): TopicDao = unsupportedDao()
        override fun studyPlanDao(): StudyPlanDao = studyPlanDaoImpl
        override fun blockedAppDao(): BlockedAppDao = unsupportedDao()
        override fun blockedAttemptDao(): BlockedAttemptDao = unsupportedDao()
        override fun breakRecordDao(): BreakRecordDao = unsupportedDao()
        override fun studyChannelDao(): StudyChannelDao = studyChannelDaoImpl
        override fun studyActivityDao(): StudyActivityDao = unsupportedDao()
        override fun appLimitDao(): AppLimitDao = unsupportedDao()
        override fun dailyAppUsageDao(): DailyAppUsageDao = unsupportedDao()
        override fun appLimitSessionDao(): AppLimitSessionDao = unsupportedDao()
        override fun blockedWebsiteDao(): BlockedWebsiteDao = unsupportedDao()
        override fun focusScheduleDao(): FocusScheduleDao = focusScheduleDaoImpl
        override fun keywordDao(): KeywordDao = unsupportedDao()
        override fun dailyUnlockDao(): DailyUnlockDao = unsupportedDao()
        override fun scratchCardDao(): ScratchCardDao = scratchCardDaoImpl
        override fun syncOutboxDao(): SyncOutboxDao = outboxDaoImpl
        override fun cloudBulkDao(): CloudBulkDao = cloudBulkDaoImpl

        override fun clearAllTables() {
            outboxEntries.clear()
            sessionsMap.clear()
            plansMap.clear()
            channelsMap.clear()
            schedulesMap.clear()
            subjectsMap.clear()
            topicsMap.clear()
            blockedAppsMap.clear()
            blockedWebsitesMap.clear()
            keywordsList.clear()
            activitiesMap.clear()
            breaksMap.clear()
            blockedAttemptsMap.clear()
            scratchCardsMap.clear()
        }

        override fun createInvalidationTracker() =
            throw UnsupportedOperationException()

        override fun createOpenHelper(config: androidx.room.DatabaseConfiguration) =
            throw UnsupportedOperationException()
    }

    private class FailingSessionReadDatabase(
        private val delegate: FocusShieldDatabase,
        private val failingSessionDao: SessionDao
    ) : FocusShieldDatabase() {
        override fun sessionDao(): SessionDao = failingSessionDao
        override fun subjectDao(): SubjectDao = delegate.subjectDao()
        override fun topicDao(): TopicDao = delegate.topicDao()
        override fun studyPlanDao(): StudyPlanDao = delegate.studyPlanDao()
        override fun blockedAppDao(): BlockedAppDao = delegate.blockedAppDao()
        override fun blockedAttemptDao(): BlockedAttemptDao = delegate.blockedAttemptDao()
        override fun breakRecordDao(): BreakRecordDao = delegate.breakRecordDao()
        override fun studyChannelDao(): StudyChannelDao = delegate.studyChannelDao()
        override fun studyActivityDao(): StudyActivityDao = delegate.studyActivityDao()
        override fun appLimitDao(): AppLimitDao = delegate.appLimitDao()
        override fun dailyAppUsageDao(): DailyAppUsageDao = delegate.dailyAppUsageDao()
        override fun appLimitSessionDao(): AppLimitSessionDao = delegate.appLimitSessionDao()
        override fun blockedWebsiteDao(): BlockedWebsiteDao = delegate.blockedWebsiteDao()
        override fun focusScheduleDao(): FocusScheduleDao = delegate.focusScheduleDao()
        override fun keywordDao(): KeywordDao = delegate.keywordDao()
        override fun dailyUnlockDao(): DailyUnlockDao = delegate.dailyUnlockDao()
        override fun scratchCardDao(): ScratchCardDao = delegate.scratchCardDao()
        override fun syncOutboxDao(): SyncOutboxDao = delegate.syncOutboxDao()
        override fun cloudBulkDao(): CloudBulkDao = delegate.cloudBulkDao()
        override fun clearAllTables() = delegate.clearAllTables()
        override fun createInvalidationTracker() =
            throw UnsupportedOperationException()
        override fun createOpenHelper(config: androidx.room.DatabaseConfiguration) =
            throw UnsupportedOperationException()
    }
}
