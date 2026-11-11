package com.example.cloud.sync

import android.net.Uri
import android.util.Log
import com.example.FocusShieldApp
import com.example.R
import com.example.cloud.auth.AuthRepository
import com.example.cloud.firestore.OperationType
import com.example.cloud.firestore.handleFirestoreError
import com.example.cloud.storage.ProfileImageUploader
import com.example.core.util.ChannelLogoStorageManager
import com.example.data.local.FocusShieldDatabase
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
import com.example.data.preferences.FocusPreferencesRepository
import com.example.data.repository.StudyPlanRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

private const val TAG = "FirestoreSyncEngine"

sealed interface SyncCycleOutcome {
    data object Synced : SyncCycleOutcome
    data object Migrated : SyncCycleOutcome
    data object NoSession : SyncCycleOutcome
    data object Offline : SyncCycleOutcome
    data object RestoreRequired : SyncCycleOutcome
    data object CrossAccountEmptyAccount : SyncCycleOutcome
    data class Failed(val message: String) : SyncCycleOutcome
}

data class SyncCloudDocument(
    val id: String,
    val exists: Boolean = true,
    val fields: Map<String, Any?> = emptyMap()
) {
    fun exists(): Boolean = exists
    fun get(key: String): Any? = fields[key]
}

interface SyncCloudStore {
    suspend fun getDocument(uid: String, collectionName: String? = null, docId: String? = null): SyncCloudDocument
    suspend fun setDocument(uid: String, collectionName: String? = null, docId: String? = null, payload: Map<String, Any?>)
    suspend fun listDocuments(uid: String, collectionName: String): List<SyncCloudDocument>
    suspend fun deleteDocument(uid: String, collectionName: String, docId: String)
}

class FirestoreSyncCloudStore(
    private val firestoreProvider: () -> FirebaseFirestore = {
        val context = FocusShieldApp.instance
        val dbId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
    }
) : SyncCloudStore {
    private fun resolveDocRef(uid: String, collectionName: String?, docId: String?): DocumentReference {
        val userDoc = firestoreProvider().collection("users").document(uid)
        return if (collectionName != null && docId != null) {
            userDoc.collection(collectionName).document(docId)
        } else {
            userDoc
        }
    }

    private fun DocumentSnapshot.toSyncCloudDocument(): SyncCloudDocument =
        SyncCloudDocument(
            id = id,
            exists = exists(),
            fields = data.orEmpty()
        )

    override suspend fun getDocument(uid: String, collectionName: String?, docId: String?): SyncCloudDocument {
        return resolveDocRef(uid, collectionName, docId).get().await().toSyncCloudDocument()
    }

    override suspend fun setDocument(
        uid: String,
        collectionName: String?,
        docId: String?,
        payload: Map<String, Any?>
    ) {
        resolveDocRef(uid, collectionName, docId).set(payload, SetOptions.merge()).await()
    }

    override suspend fun listDocuments(uid: String, collectionName: String): List<SyncCloudDocument> {
        val snap = firestoreProvider().collection("users").document(uid).collection(collectionName).get().await()
        return snap.documents.map { it.toSyncCloudDocument() }
    }

    override suspend fun deleteDocument(uid: String, collectionName: String, docId: String) {
        firestoreProvider().collection("users").document(uid).collection(collectionName).document(docId).delete().await()
    }
}

/**
 * Synchronizes FocusShield user data (study sessions, subjects, topics, study plans, study channels,
 * focus schedules, blocked apps/websites/keywords, study activities, break records, blocked
 * attempts, scratch cards, user preferences, and scholar profile metadata) with Cloud Firestore
 * under /users/{userId}/...
 *
 * Essential local database and Firestore read/write/delete errors propagate so the outbox is never
 * cleared when any required operation fails.
 */
class SyncEngine(
    private val database: FocusShieldDatabase,
    private val installState: CloudInstallState,
    private val preferencesRepository: FocusPreferencesRepository,
    private val authRepository: AuthRepository,
    private val cloudStore: SyncCloudStore = FirestoreSyncCloudStore()
) {

    suspend fun runCycle(expectedUid: String? = null): SyncCycleOutcome {
        val initialUid = authRepository.currentUserId()?.trim()?.takeIf { it.isNotEmpty() }
            ?: return SyncCycleOutcome.NoSession
        if (expectedUid != null && expectedUid.trim() != initialUid) {
            return SyncCycleOutcome.Failed("Requested UID does not match authenticated account")
        }
        return executeCycleForUid(initialUid)
    }

    suspend fun migrate(expectedUid: String? = null): SyncCycleOutcome = runCycle(expectedUid)

    suspend fun restore(uid: String): SyncCycleOutcome {
        val requestedUid = uid.trim()
        if (requestedUid.isEmpty()) {
            return SyncCycleOutcome.Failed("Requested UID must not be blank")
        }
        val currentUid = authRepository.currentUserId()?.trim()?.takeIf { it.isNotEmpty() }
            ?: return SyncCycleOutcome.NoSession
        if (currentUid != requestedUid) {
            return SyncCycleOutcome.Failed("Requested UID does not match authenticated account")
        }
        return executeCycleForUid(requestedUid)
    }

    private suspend fun executeCycleForUid(targetUid: String): SyncCycleOutcome {
        return SyncTracker.lock.withLock {
            val authenticatedUid = authRepository.currentUserId()?.trim()?.takeIf { it.isNotEmpty() }
                ?: return@withLock SyncCycleOutcome.NoSession
            if (authenticatedUid != targetUid) {
                return@withLock SyncCycleOutcome.Failed("Requested UID does not match authenticated account")
            }
            val ownerUid = installState.dataOwnerAccountId()?.trim()?.takeIf { it.isNotEmpty() }
            if (ownerUid != null && ownerUid != authenticatedUid) {
                return@withLock SyncCycleOutcome.Failed("Local data belongs to a different account")
            }
            try {
                syncWithFirestore(authenticatedUid)
                SyncCycleOutcome.Synced
            } catch (e: IOException) {
                SyncCycleOutcome.Offline
            } catch (e: Exception) {
                Log.e(TAG, "Sync error (${e.javaClass.simpleName})")
                SyncCycleOutcome.Failed(e.localizedMessage ?: e.message ?: "Sync error")
            }
        }
    }

    private fun ensureStillAuthenticatedAs(expectedUid: String) {
        val activeUid = authRepository.currentUserId()?.trim()
        if (activeUid.isNullOrEmpty() || activeUid != expectedUid) {
            throw IllegalStateException("Authenticated account changed during synchronization")
        }
    }

    // ---- Type-Safe Document Readers (Never throw on type mismatch) ----

    private fun SyncCloudDocument.readString(key: String): String? {
        return when (val v = get(key)) {
            is String -> v
            else -> null
        }
    }

    private fun SyncCloudDocument.readBoolean(key: String): Boolean? {
        return when (val v = get(key)) {
            is Boolean -> v
            is String -> v.lowercase(Locale.ROOT).toBooleanStrictOrNull()
            is Number -> v.toInt() != 0
            else -> null
        }
    }

    private fun SyncCloudDocument.readLong(key: String): Long? {
        return when (val v = get(key)) {
            is Number -> v.toLong()
            is Timestamp -> v.toDate().time
            is Date -> v.time
            is String -> v.toLongOrNull()
            else -> null
        }
    }

    private fun SyncCloudDocument.readInt(key: String): Int? = readLong(key)?.toInt()

    private fun SyncCloudDocument.readMillis(key: String): Long? = readLong(key)

    private fun safeDocId(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return "empty"
        val sanitized = trimmed
            .replace("/", "%2F")
            .let { if (it == "." || it == "..") it.replace(".", "%2E") else it }
            .let { if (it.startsWith("__") && it.endsWith("__")) "_${it}_" else it }
        return if (sanitized.length > 240) sanitized.take(240) else sanitized
    }

    private fun keywordCompositeKey(type: String, keyword: String): String =
        "$type:${keyword.length}:$keyword"

    private suspend fun syncWithFirestore(uid: String) {
        ensureStillAuthenticatedAs(uid)
        val appContext = try { FocusShieldApp.instance } catch (_: Exception) { null }
        val isFirstSyncForAccount =
            uid !in installState.migratedAccounts() || installState.dataOwnerAccountId() != uid

        // 0. Inspect Pending Outbox Operations (Deletions & Local Upserts) BEFORE Pulling.
        // Local DB errors must propagate rather than returning empty lists.
        val outboxDao = database.syncOutboxDao()

        val pendingPlanOps = outboxDao.pendingForTable(SyncTables.STUDY_PLANS)
        val deletedPlanIds = pendingPlanOps
            .filter { it.op == SyncTracker.OP_DELETE }
            .map { it.rowId }
            .toSet()
        val locallyModifiedPlanIds = pendingPlanOps
            .filter { it.op == SyncTracker.OP_UPSERT }
            .map { it.rowId }
            .toSet()

        val pendingChannelOps = outboxDao.pendingForTable(SyncTables.STUDY_CHANNELS)
        val deletedChannelIds = pendingChannelOps
            .filter { it.op == SyncTracker.OP_DELETE }
            .map { it.rowId }
            .toSet()
        val locallyModifiedChannelIds = pendingChannelOps
            .filter { it.op == SyncTracker.OP_UPSERT }
            .map { it.rowId }
            .toSet()

        val pendingScheduleOps = outboxDao.pendingForTable(SyncTables.FOCUS_SCHEDULES)
        val deletedScheduleIds = pendingScheduleOps
            .filter { it.op == SyncTracker.OP_DELETE }
            .map { it.rowId }
            .toSet()
        val locallyModifiedScheduleIds = pendingScheduleOps
            .filter { it.op == SyncTracker.OP_UPSERT }
            .map { it.rowId }
            .toSet()

        val pendingSubjectOps = outboxDao.pendingForTable(SyncTables.STUDY_SUBJECTS)
        val deletedSubjectIds = pendingSubjectOps
            .filter { it.op == SyncTracker.OP_DELETE }
            .map { it.rowId }
            .toSet()
        val locallyModifiedSubjectIds = pendingSubjectOps
            .filter { it.op == SyncTracker.OP_UPSERT }
            .map { it.rowId }
            .toSet()

        val pendingTopicOps = outboxDao.pendingForTable(SyncTables.STUDY_TOPICS)
        val deletedTopicIds = pendingTopicOps
            .filter { it.op == SyncTracker.OP_DELETE }
            .map { it.rowId }
            .toSet()
        val locallyModifiedTopicIds = pendingTopicOps
            .filter { it.op == SyncTracker.OP_UPSERT }
            .map { it.rowId }
            .toSet()

        val pendingBlockedAppOps = outboxDao.pendingForTable(SyncTables.BLOCKED_APPS)
        val deletedBlockedAppIds = pendingBlockedAppOps
            .filter { it.op == SyncTracker.OP_DELETE }
            .map { it.rowId }
            .toSet()
        val locallyModifiedBlockedAppIds = pendingBlockedAppOps
            .filter { it.op == SyncTracker.OP_UPSERT }
            .map { it.rowId }
            .toSet()

        val pendingWebsiteOps = outboxDao.pendingForTable(SyncTables.BLOCKED_WEBSITES)
        val deletedWebsiteDomains = pendingWebsiteOps
            .filter { it.op == SyncTracker.OP_DELETE }
            .map { it.rowId }
            .toSet()
        val locallyModifiedWebsiteDomains = pendingWebsiteOps
            .filter { it.op == SyncTracker.OP_UPSERT }
            .map { it.rowId }
            .toSet()

        val pendingKeywordOps = outboxDao.pendingForTable(SyncTables.BLOCKED_KEYWORDS)
        val deletedKeywordKeys = pendingKeywordOps
            .filter { it.op == SyncTracker.OP_DELETE }
            .map { it.rowId }
            .toSet()
        val locallyModifiedKeywordKeys = pendingKeywordOps
            .filter { it.op == SyncTracker.OP_UPSERT }
            .map { it.rowId }
            .toSet()

        // Execute pending Firestore deletions FIRST so deleted items never reappear on pull.
        // Deletion failures must propagate so the outbox is never cleared when a delete fails.
        for (planId in deletedPlanIds) {
            deleteFromFirestore(uid, "study_plans", planId)
        }
        for (channelId in deletedChannelIds) {
            deleteFromFirestore(uid, "study_channels", channelId)
        }
        for (scheduleId in deletedScheduleIds) {
            deleteFromFirestore(uid, "focus_schedules", scheduleId)
        }
        for (subjectId in deletedSubjectIds) {
            deleteFromFirestore(uid, "study_subjects", subjectId)
        }
        for (topicId in deletedTopicIds) {
            deleteFromFirestore(uid, "study_topics", topicId)
        }
        for (pkg in deletedBlockedAppIds) {
            deleteFromFirestore(uid, "blocked_apps", pkg)
        }
        for (domain in deletedWebsiteDomains) {
            deleteFromFirestore(uid, "blocked_websites", domain)
        }
        for (kwKey in deletedKeywordKeys) {
            deleteFromFirestore(uid, "blocked_keywords", kwKey)
        }

        // 1. Push Local Sessions (preserving full session timing, Pomodoro cycles, and completion status)
        val initialLocalSessions = database.sessionDao().getAllSessionsAscending()
        for (session in initialLocalSessions) {
            val sessionPayload = hashMapOf<String, Any>(
                "id" to session.id,
                "userId" to uid,
                "mode" to session.mode.name,
                "title" to session.title,
                "durationMinutes" to ((session.actualDurationMillis / (1000 * 60)).toInt()),
                "plannedDurationMillis" to session.plannedDurationMillis,
                "subject" to session.subject,
                "topic" to session.topic,
                "goal" to session.goal,
                "actualDurationMillis" to session.actualDurationMillis,
                "startTime" to session.startTime,
                "endTime" to session.endTime,
                "completed" to session.completed,
                "cancelled" to session.cancelled,
                "pomodoroCycles" to session.pomodoroCycles,
                "completedPomodoroCycles" to session.completedPomodoroCycles,
                "completedAt" to Timestamp(Date(session.endTime)),
                "createdAt" to Timestamp(Date(session.createdAt))
            )
            writeToFirestore(uid, "sessions", safeDocId(session.id), sessionPayload)
        }

        // 2. Pull Remote Sessions
        pullSessionsFromCollection(uid, "sessions")

        // 2b. Two-Way Append-Only Sync for Study Activities (YouTube Study Mode & Study Browser usage)
        syncStudyActivitiesWithFirestore(uid)

        // 2c. Two-Way Append-Only Sync for Break Records
        syncBreakRecordsWithFirestore(uid)

        // 2d. Two-Way Append-Only Sync for Blocked Attempts (Apps, Shorts, Reels, Websites)
        syncBlockedAttemptsWithFirestore(uid)

        // 2e. Two-Way Sync for Scratch Card Rewards
        syncScratchCardsWithFirestore(uid)

        // 3. Sync Profile & User Preferences Document
        syncProfileAndPreferencesWithFirestore(
            uid = uid,
            isFirstSyncForAccount = isFirstSyncForAccount,
            appContext = appContext
        )

        // 4. Two-Way Sync for Study Subjects & Topics
        syncSubjectsAndTopicsWithFirestore(
            uid = uid,
            deletedSubjectIds = deletedSubjectIds,
            locallyModifiedSubjectIds = locallyModifiedSubjectIds,
            deletedTopicIds = deletedTopicIds,
            locallyModifiedTopicIds = locallyModifiedTopicIds
        )

        // 5. Two-Way Sync for Study Plans
        syncStudyPlansWithFirestore(
            uid = uid,
            deletedPlanIds = deletedPlanIds,
            locallyModifiedPlanIds = locallyModifiedPlanIds
        )

        // 6. Two-Way Sync & Merge for Study Channels (/users/{uid}/study_channels/{channelId})
        val localChannelsBeforePull = database.studyChannelDao().getAllChannels()
        for (channel in localChannelsBeforePull) {
            if (channel.id in deletedChannelIds) continue
            if (channel.id in locallyModifiedChannelIds) {
                pushChannelToFirestore(uid, channel)
            }
        }

        val remoteChannelIdsSeen = mutableSetOf<String>()
        val remoteChannelsDocs = listFromFirestore(uid, "study_channels")
        for (doc in remoteChannelsDocs) {
            val id = doc.readString("id") ?: doc.id
            if (id in deletedChannelIds) continue

            val channelId = doc.readString("channelId")?.trim().orEmpty()
            val channelName = doc.readString("channelName")?.trim().orEmpty()
            if (channelId.isBlank() || channelName.isBlank()) {
                SyncTracker.recordRejectedCloudRow(
                    SyncTables.STUDY_CHANNELS,
                    id,
                    IllegalArgumentException("Missing channelId or channelName")
                )
                continue
            }

            val channelUrl = doc.readString("channelUrl") ?: "https://www.youtube.com/$channelId"
            val thumbnailUrl = doc.readString("thumbnailUrl") ?: ""
            val isApproved = doc.readBoolean("isApproved") ?: true
            val createdAt = doc.readMillis("createdAt") ?: System.currentTimeMillis()

            remoteChannelIdsSeen.add(id)

            val existingById = database.studyChannelDao().getChannelById(id)
            val existingByHandle = database.studyChannelDao().getChannelByChannelId(channelId)
            val existing = existingById ?: existingByHandle

            if (existing == null) {
                val restoredChannel = StudyChannelEntity(
                    id = id,
                    channelId = channelId,
                    channelName = channelName,
                    channelUrl = channelUrl,
                    thumbnailUrl = thumbnailUrl,
                    isApproved = isApproved,
                    createdAt = createdAt
                )
                database.studyChannelDao().insertChannel(restoredChannel)
                if (appContext != null) {
                    // Best-effort logo caching on disk
                    runCatching {
                        ChannelLogoStorageManager.cacheAndPersistToDatabase(
                            appContext,
                            restoredChannel,
                            database.studyChannelDao()
                        )
                    }
                }
            } else if (existing.id !in locallyModifiedChannelIds) {
                if (existing.isApproved != isApproved || existing.channelName != channelName) {
                    val updatedChannel = existing.copy(
                        channelName = channelName,
                        channelUrl = channelUrl.ifBlank { existing.channelUrl },
                        thumbnailUrl = existing.thumbnailUrl.ifBlank { thumbnailUrl },
                        isApproved = isApproved
                    )
                    database.studyChannelDao().updateChannel(updatedChannel)
                }
            }
        }

        val mergedLocalChannels = database.studyChannelDao().getAllChannels()
        for (channel in mergedLocalChannels) {
            if (channel.id in deletedChannelIds) continue
            if (channel.id !in remoteChannelIdsSeen && channel.id !in locallyModifiedChannelIds) {
                pushChannelToFirestore(uid, channel)
            }
        }

        // Best-effort in-memory cache refresh for YouTube Study Mode
        try {
            appContext?.studyChannelRepository?.refreshApprovedCache()
        } catch (_: Exception) {}

        // 7. Two-Way Sync for Recurring Focus Schedules (/users/{uid}/focus_schedules/{scheduleId})
        val localSchedules = database.focusScheduleDao().getAllSchedules()
        for (schedule in localSchedules) {
            if (schedule.id in deletedScheduleIds || isLegacyPreloadedSchedule(schedule.title, schedule.subjectName, schedule.startTime, schedule.endTime)) {
                database.focusScheduleDao().deleteScheduleById(schedule.id)
                deleteFromFirestore(uid, "focus_schedules", schedule.id)
                continue
            }
            if (schedule.id in locallyModifiedScheduleIds) {
                pushScheduleToFirestore(uid, schedule)
            }
        }

        val remoteScheduleIdsSeen = mutableSetOf<String>()
        val remoteSchedulesDocs = listFromFirestore(uid, "focus_schedules")
        for (doc in remoteSchedulesDocs) {
            val id = doc.readString("id") ?: doc.id
            val title = doc.readString("title") ?: ""
            val subjectName = doc.readString("subjectName") ?: ""
            val startTime = doc.readString("startTime") ?: "09:00"
            val endTime = doc.readString("endTime") ?: "10:00"

            if (id in deletedScheduleIds || isLegacyPreloadedSchedule(title, subjectName, startTime, endTime)) {
                deleteFromFirestore(uid, "focus_schedules", id)
                continue
            }

            if (title.isBlank()) {
                SyncTracker.recordRejectedCloudRow(
                    SyncTables.FOCUS_SCHEDULES,
                    id,
                    IllegalArgumentException("Missing schedule title")
                )
                continue
            }
            remoteScheduleIdsSeen.add(id)

            val existing = database.focusScheduleDao().getScheduleById(id)
            val remoteEntity = FocusScheduleEntity(
                id = id,
                title = title,
                daysOfWeek = doc.readString("daysOfWeek") ?: "MON,TUE,WED,THU,FRI",
                startTime = startTime,
                endTime = endTime,
                isEnabled = doc.readBoolean("isEnabled") ?: true,
                isAutoStartSession = doc.readBoolean("isAutoStartSession") ?: true,
                mode = doc.readString("mode") ?: "TIMER",
                subjectName = subjectName,
                colorHex = doc.readString("colorHex") ?: "#4F46E5",
                repeatEnabled = doc.readBoolean("repeatEnabled") ?: true,
                scheduledDateMillis = doc.readLong("scheduledDateMillis") ?: 0L,
                breakMinutes = doc.readInt("breakMinutes") ?: 5,
                description = doc.readString("description") ?: "",
                blockedAppPackages = doc.readString("blockedAppPackages") ?: "",
                blockNotifications = doc.readBoolean("blockNotifications") ?: false,
                createdAt = doc.readMillis("createdAt")
                    ?: existing?.createdAt
                    ?: System.currentTimeMillis()
            )

            if (existing == null) {
                database.focusScheduleDao().insertSchedule(remoteEntity)
            } else if (existing.id !in locallyModifiedScheduleIds && existing != remoteEntity) {
                database.focusScheduleDao().updateSchedule(remoteEntity)
            }
        }

        val mergedLocalSchedules = database.focusScheduleDao().getAllSchedules()
        for (schedule in mergedLocalSchedules) {
            if (schedule.id in deletedScheduleIds) continue
            if (schedule.id !in remoteScheduleIdsSeen && schedule.id !in locallyModifiedScheduleIds) {
                pushScheduleToFirestore(uid, schedule)
            }
        }

        // 8. Two-Way Sync for Blocked Apps, Blocked Websites, and Blocked Keywords
        syncBlockedAppsWithFirestore(
            uid = uid,
            deletedPackages = deletedBlockedAppIds,
            locallyModifiedPackages = locallyModifiedBlockedAppIds
        )
        syncBlockedWebsitesWithFirestore(
            uid = uid,
            deletedDomains = deletedWebsiteDomains,
            locallyModifiedDomains = locallyModifiedWebsiteDomains
        )
        syncKeywordsWithFirestore(
            uid = uid,
            deletedKeys = deletedKeywordKeys,
            locallyModifiedKeys = locallyModifiedKeywordKeys
        )

        // Best-effort alarm maintenance after schedule reconciliation
        if (appContext != null) {
            try {
                appContext.focusScheduleRepository.syncAllAlarms(appContext)
            } catch (_: Exception) {}
        }

        // Verify identity before committing final outbox clear & ownership state
        ensureStillAuthenticatedAs(uid)

        // Clear local outbox ONLY after all required synchronization operations have succeeded,
        // and mark outbox drained ONLY after clearAll() succeeds.
        outboxDao.clearAll()
        SyncTracker.markOutboxDrained()

        installState.setDataOwnerAccountId(uid)
        installState.markMigrated(uid)
        installState.rememberAccount(uid)
    }

    private suspend fun writeToFirestore(
        uid: String,
        collectionName: String? = null,
        docId: String? = null,
        payload: Map<String, Any?>
    ) {
        try {
            cloudStore.setDocument(uid, collectionName, docId, payload)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, null)
            throw e
        }
    }

    private suspend fun getFromFirestore(
        uid: String,
        collectionName: String? = null,
        docId: String? = null
    ): SyncCloudDocument {
        return try {
            cloudStore.getDocument(uid, collectionName, docId)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, null)
            throw e
        }
    }

    private suspend fun listFromFirestore(
        uid: String,
        collectionName: String
    ): List<SyncCloudDocument> {
        return try {
            cloudStore.listDocuments(uid, collectionName)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, null)
            throw e
        }
    }

    private suspend fun deleteFromFirestore(
        uid: String,
        collectionName: String,
        rawId: String
    ) {
        try {
            cloudStore.deleteDocument(uid, collectionName, safeDocId(rawId))
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, null)
            throw e
        }
    }

    private suspend fun pullSessionsFromCollection(
        uid: String,
        collectionName: String
    ) {
        val remoteSessionsDocs = listFromFirestore(uid, collectionName)
        for (doc in remoteSessionsDocs) {
            val id = doc.readString("id") ?: doc.id
            val existing = database.sessionDao().getSessionById(id)
            if (existing == null) {
                val parsed = runCatching {
                    val modeStr = doc.readString("mode") ?: "TIMER"
                    val mode = runCatching { SessionMode.valueOf(modeStr) }
                        .getOrDefault(SessionMode.TIMER)
                    val durMinutes = doc.readInt("durationMinutes") ?: 0
                    val durMillis = durMinutes * 60 * 1000L
                    val subject = doc.readString("subject") ?: "Study"
                    val topic = doc.readString("topic") ?: "General"
                    val title = doc.readString("title")?.takeIf { it.isNotBlank() } ?: "$subject - $topic"
                    val goal = doc.readString("goal") ?: "Deep Focus"
                    val actualMillis = doc.readLong("actualDurationMillis") ?: durMillis
                    val plannedMillis = doc.readLong("plannedDurationMillis") ?: durMillis.coerceAtLeast(actualMillis)
                    val completed = doc.readBoolean("completed") ?: true
                    val cancelled = doc.readBoolean("cancelled") ?: !completed
                    val completedAt = doc.readMillis("completedAt")
                        ?: doc.readLong("endTime")
                        ?: System.currentTimeMillis()
                    val startTime = doc.readLong("startTime") ?: (completedAt - actualMillis)
                    val endTime = doc.readLong("endTime") ?: completedAt
                    val pomodoroCycles = doc.readInt("pomodoroCycles") ?: 1
                    val completedPomodoroCycles = doc.readInt("completedPomodoroCycles") ?: if (completed) 1 else 0
                    val createdAt = doc.readMillis("createdAt") ?: completedAt

                    SessionRecordEntity(
                        id = id,
                        mode = mode,
                        title = title,
                        subject = subject,
                        topic = topic,
                        goal = goal,
                        plannedDurationMillis = plannedMillis,
                        actualDurationMillis = actualMillis,
                        startTime = startTime,
                        endTime = endTime,
                        completed = completed,
                        cancelled = cancelled,
                        pomodoroCycles = pomodoroCycles,
                        completedPomodoroCycles = completedPomodoroCycles,
                        createdAt = createdAt
                    )
                }.getOrElse { e ->
                    SyncTracker.recordRejectedCloudRow(SyncTables.SESSION_RECORDS, id, e)
                    null
                }
                if (parsed != null) {
                    database.sessionDao().insertSession(parsed)
                }
            }
        }
    }

    private suspend fun syncProfileAndPreferencesWithFirestore(
        uid: String,
        isFirstSyncForAccount: Boolean,
        appContext: FocusShieldApp?
    ) {
        val pendingPhoto = installState.pendingProfilePhoto(uid)
        if (pendingPhoto != null) {
            val avatarContext = appContext ?: error("Application context is required to back up a pending profile photo")
            val uploader = ProfileImageUploader(avatarContext, authRepository)
            val uploaded = pendingPhoto.photoUri?.let { uploader.uploadOwnImage(it, expectedUserId = uid) }
                ?: uploader.deleteOwnImage(expectedUserId = uid)
            check(uploaded) { "Profile photo is saved locally. Check your connection and use Sync now to retry its backup." }
            installState.clearPendingProfilePhoto(pendingPhoto)
            installState.setProfileDirty(true)
        }
        val userSnap = getFromFirestore(uid)
        val prefSnap = getFromFirestore(uid, "user_preferences", "default")

        // 3a. Pull Remote Preferences if this account is being imported/migrated or local prefs aren't dirty
        val shouldPullPrefs = isFirstSyncForAccount || !installState.prefsDirty()
        if (shouldPullPrefs) {
            val remotePrefsJsonStr = prefSnap.takeIf { it.exists() }?.readString("preferencesJson")
                ?: userSnap.takeIf { it.exists() }?.readString("preferencesJson")

            if (!remotePrefsJsonStr.isNullOrBlank()) {
                preferencesRepository.applyCloudPreferences(remotePrefsJsonStr)
                installState.setPrefsDirty(false)
            } else if (userSnap.exists()) {
                val remoteGoal = userSnap.readInt("dailyStudyGoalMinutes")
                if (remoteGoal != null && remoteGoal > 0) {
                    preferencesRepository.updateDailyGoalMinutes(remoteGoal)
                }
                val remoteThreshold = userSnap.readInt("minimumStreakThresholdMinutes")
                if (remoteThreshold != null && remoteThreshold > 0) {
                    preferencesRepository.updateMinimumStreakThresholdMinutes(remoteThreshold)
                }
                installState.setPrefsDirty(false)
            }
        }

        // 3b. Pull Remote Profile metadata if this account is being imported/migrated or local profile isn't dirty
        val shouldPullProfile = pendingPhoto == null && installState.pendingProfilePhoto(uid) == null &&
            (isFirstSyncForAccount || !installState.profileDirty())
        if (shouldPullProfile && userSnap.exists()) {
            val currentPrefsBeforeProfile = preferencesRepository.preferencesFlow.first()
            val remoteDisplayName = userSnap.readString("displayName")?.takeIf { it.isNotBlank() }
            val remoteAvatarPreset = userSnap.readString("avatarPreset")?.takeIf { it.isNotBlank() }
            val remoteMotto = userSnap.readString("motto")?.takeIf { it.isNotBlank() }
            val remoteAcademicGoal = userSnap.readString("academicGoal")?.takeIf { it.isNotBlank() }

            if (remoteDisplayName != null || remoteAvatarPreset != null || remoteMotto != null || remoteAcademicGoal != null) {
                preferencesRepository.applyCloudProfile(
                    displayName = remoteDisplayName ?: currentPrefsBeforeProfile.userName,
                    avatarPreset = remoteAvatarPreset ?: currentPrefsBeforeProfile.userAvatarPreset,
                    motto = remoteMotto ?: currentPrefsBeforeProfile.userMotto,
                    academicGoal = remoteAcademicGoal ?: currentPrefsBeforeProfile.userAcademicGoal,
                    avatarPath = preferencesRepository.cloudAvatarPath()
                )
            }

            // Best-effort avatar cache download
            val remotePhotoBase64 = userSnap.readString("photoBase64")
            if (!remotePhotoBase64.isNullOrBlank() && appContext != null) {
                runCatching {
                    val uploader = ProfileImageUploader(appContext, authRepository)
                    val cachedPath = uploader.downloadOwnAvatarToCache(uid)
                    if (!cachedPath.isNullOrBlank()) {
                        preferencesRepository.updateUserPhotoUri(Uri.fromFile(File(cachedPath)).toString())
                    }
                }
            }
            installState.setProfileDirty(false)
        }

        // 3c. Push Merged Profile & Preferences to Firestore
        val mergedSessions = database.sessionDao().getAllSessionsAscending()
        val mergedActivities = database.cloudBulkDao().activities()
        val mergedAttempts = database.cloudBulkDao().blockedAttempts()
        val currentPrefs = preferencesRepository.preferencesFlow.first()
        val streakResult = com.example.feature.analytics.domain.StreakCalculator.calculate(
            sessions = mergedSessions,
            minDailyThresholdMinutes = currentPrefs.minimumStreakThresholdMinutes
        )

        val localTotalStudyMillis = mergedSessions.sumOf { it.actualDurationMillis }
        val localCompletedSessions = mergedSessions.count { it.completed }
        val localYtMillis = mergedActivities
            .filter { it.source == StudyActivitySource.YOUTUBE }
            .sumOf { it.durationMillis }
        val localBrowserMillis = mergedActivities
            .filter { it.source == StudyActivitySource.BROWSER }
            .sumOf { it.durationMillis }
        val localBlockedCount = mergedAttempts.size

        val remoteLongestStreak = if (userSnap.exists()) (userSnap.readInt("longestStreak") ?: 0) else 0
        val remoteTotalStudyMillis = if (userSnap.exists()) (userSnap.readLong("totalStudyTimeMillis") ?: 0L) else 0L
        val remoteCompletedSessions = if (userSnap.exists()) (userSnap.readInt("completedSessionsCount") ?: 0) else 0

        val rawCreatedAt = if (userSnap.exists()) userSnap.get("createdAt") else null
        val safeCreatedAt: Any = when (rawCreatedAt) {
            is Timestamp -> rawCreatedAt
            is Number -> Timestamp(Date(rawCreatedAt.toLong()))
            else -> FieldValue.serverTimestamp()
        }

        val resolvedDisplayName = currentPrefs.userName.takeUnless { it.isBlank() || it == "Focus Scholar" }
            ?: authRepository.currentDisplayName()?.takeIf { it.isNotBlank() }
            ?: currentPrefs.userName.ifBlank { "Focus Scholar" }

        val preferencesJsonString = CloudJson.accountPreferencesJson(currentPrefs).toString()
            .takeUnless { it.isNullOrBlank() } ?: "{}"

        val profilePayload = hashMapOf<String, Any>(
            "userId" to uid,
            "displayName" to resolvedDisplayName,
            "email" to (authRepository.currentUserEmail() ?: ""),
            "avatarPreset" to currentPrefs.userAvatarPreset,
            "motto" to currentPrefs.userMotto,
            "academicGoal" to currentPrefs.userAcademicGoal,
            "preferencesJson" to preferencesJsonString,
            "dailyStudyGoalMinutes" to currentPrefs.dailyGoalMinutes,
            "minimumStreakThresholdMinutes" to currentPrefs.minimumStreakThresholdMinutes,
            "currentStreak" to streakResult.currentStreak,
            "longestStreak" to maxOf(streakResult.longestStreak, remoteLongestStreak),
            "totalStudyTimeMillis" to maxOf(localTotalStudyMillis, remoteTotalStudyMillis),
            "completedSessionsCount" to maxOf(localCompletedSessions, remoteCompletedSessions),
            "youtubeStudyTimeMillis" to localYtMillis,
            "browserStudyTimeMillis" to localBrowserMillis,
            "blockedAttemptsCount" to localBlockedCount,
            "updatedAt" to FieldValue.serverTimestamp(),
            "createdAt" to safeCreatedAt
        )

        writeToFirestore(uid, null, null, profilePayload)
        installState.setProfileDirty(false)

        val prefPayload = hashMapOf<String, Any>(
            "userId" to uid,
            "preferencesJson" to preferencesJsonString,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        writeToFirestore(uid, "user_preferences", "default", prefPayload)
        installState.setPrefsDirty(false)
    }

    private suspend fun syncSubjectsAndTopicsWithFirestore(
        uid: String,
        deletedSubjectIds: Set<String>,
        locallyModifiedSubjectIds: Set<String>,
        deletedTopicIds: Set<String>,
        locallyModifiedTopicIds: Set<String>
    ) {
        val bulkDao = database.cloudBulkDao()

        // 4a. Push locally modified subjects first
        val localSubjectsBefore = bulkDao.subjects()
        for (subject in localSubjectsBefore) {
            if (subject.id in deletedSubjectIds) continue
            if (subject.id in locallyModifiedSubjectIds) {
                pushSubjectToFirestore(uid, subject)
            }
        }

        // 4b. Pull remote subjects
        val remoteSubjectIdsSeen = mutableSetOf<String>()
        val subjectDocs = listFromFirestore(uid, "study_subjects")
        val currentLocalSubjects = bulkDao.subjects()
        val byId = currentLocalSubjects.associateBy { it.id }.toMutableMap()
        val byName = currentLocalSubjects.associateBy { it.name.trim().lowercase(Locale.ROOT) }
        val subjectsToUpsert = mutableListOf<SubjectEntity>()

        for (doc in subjectDocs) {
            val id = doc.readString("id") ?: doc.id
            if (id in deletedSubjectIds) continue
            val name = doc.readString("name")?.trim().orEmpty()
            if (name.isBlank()) {
                SyncTracker.recordRejectedCloudRow(
                    SyncTables.STUDY_SUBJECTS,
                    id,
                    IllegalArgumentException("Missing subject name")
                )
                continue
            }

            remoteSubjectIdsSeen.add(id)
            val remoteSubject = SubjectEntity(
                id = id,
                name = name,
                colorHex = doc.readString("colorHex") ?: "#7C3AED",
                iconIdentifier = doc.readString("iconIdentifier") ?: "default",
                isArchived = doc.readBoolean("isArchived") ?: false,
                createdAt = doc.readMillis("createdAt") ?: System.currentTimeMillis()
            )

            val existing = byId[id] ?: byName[name.lowercase(Locale.ROOT)]
            if (existing == null) {
                subjectsToUpsert.add(remoteSubject)
                byId[id] = remoteSubject
            } else if (existing.id !in locallyModifiedSubjectIds && existing.id == id && existing != remoteSubject) {
                subjectsToUpsert.add(remoteSubject)
            }
        }
        if (subjectsToUpsert.isNotEmpty()) {
            bulkDao.upsertSubjects(subjectsToUpsert)
        }

        // 4c. Push any local subjects not yet in Firestore
        val mergedSubjects = bulkDao.subjects()
        for (subject in mergedSubjects) {
            if (subject.id in deletedSubjectIds) continue
            if (subject.id !in remoteSubjectIdsSeen && subject.id !in locallyModifiedSubjectIds) {
                pushSubjectToFirestore(uid, subject)
            }
        }

        // 4d. Push locally modified topics first
        val localTopicsBefore = bulkDao.topics()
        for (topic in localTopicsBefore) {
            if (topic.id in deletedTopicIds) continue
            if (topic.id in locallyModifiedTopicIds) {
                pushTopicToFirestore(uid, topic)
            }
        }

        // 4e. Pull remote topics
        val remoteTopicIdsSeen = mutableSetOf<String>()
        val topicDocs = listFromFirestore(uid, "study_topics")
        val currentLocalTopics = bulkDao.topics()
        val topicsById = currentLocalTopics.associateBy { it.id }
        val topicsToUpsert = mutableListOf<TopicEntity>()

        for (doc in topicDocs) {
            val id = doc.readString("id") ?: doc.id
            if (id in deletedTopicIds) continue
            val name = doc.readString("name")?.trim().orEmpty()
            if (name.isBlank()) {
                SyncTracker.recordRejectedCloudRow(
                    SyncTables.STUDY_TOPICS,
                    id,
                    IllegalArgumentException("Missing topic name")
                )
                continue
            }
            val subjectId = doc.readString("subjectId") ?: ""

            remoteTopicIdsSeen.add(id)
            val remoteTopic = TopicEntity(
                id = id,
                subjectId = subjectId,
                name = name,
                createdAt = doc.readMillis("createdAt") ?: System.currentTimeMillis()
            )

            val existing = topicsById[id]
            if (existing == null) {
                topicsToUpsert.add(remoteTopic)
            } else if (existing.id !in locallyModifiedTopicIds && existing != remoteTopic) {
                topicsToUpsert.add(remoteTopic)
            }
        }
        if (topicsToUpsert.isNotEmpty()) {
            bulkDao.upsertTopics(topicsToUpsert)
        }

        // 4f. Push any local topics not yet in Firestore
        val mergedTopics = bulkDao.topics()
        for (topic in mergedTopics) {
            if (topic.id in deletedTopicIds) continue
            if (topic.id !in remoteTopicIdsSeen && topic.id !in locallyModifiedTopicIds) {
                pushTopicToFirestore(uid, topic)
            }
        }
    }

    private suspend fun pushSubjectToFirestore(
        uid: String,
        subject: SubjectEntity
    ) {
        val payload = hashMapOf<String, Any>(
            "id" to subject.id,
            "userId" to uid,
            "name" to subject.name,
            "colorHex" to subject.colorHex,
            "iconIdentifier" to subject.iconIdentifier,
            "isArchived" to subject.isArchived,
            "createdAt" to Timestamp(Date(subject.createdAt))
        )
        writeToFirestore(uid, "study_subjects", safeDocId(subject.id), payload)
    }

    private suspend fun pushTopicToFirestore(
        uid: String,
        topic: TopicEntity
    ) {
        val payload = hashMapOf<String, Any>(
            "id" to topic.id,
            "userId" to uid,
            "subjectId" to topic.subjectId,
            "name" to topic.name,
            "createdAt" to Timestamp(Date(topic.createdAt))
        )
        writeToFirestore(uid, "study_topics", safeDocId(topic.id), payload)
    }

    private suspend fun syncStudyPlansWithFirestore(
        uid: String,
        deletedPlanIds: Set<String>,
        locallyModifiedPlanIds: Set<String>
    ) {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val localPlans = database.studyPlanDao().getAllPlans()

        // 5a. Push local study plans (writing both canonical StudyPlanEntity fields and legacy aliases)
        for (plan in localPlans) {
            if (plan.id in deletedPlanIds || isLegacyPreloadedStudyPlan(plan.id, plan.subjectName, plan.topicName, plan.startTime, plan.endTime)) {
                database.studyPlanDao().deletePlanById(plan.id)
                deleteFromFirestore(uid, "study_plans", plan.id)
                continue
            }
            val schedDate = if (plan.dateString.isNotBlank()) plan.dateString else isoFormat.format(Date(plan.targetDate))
            val planPayload = hashMapOf<String, Any>(
                "id" to plan.id,
                "userId" to uid,
                "subjectId" to plan.subjectId,
                "subjectName" to plan.subjectName,
                "topicName" to plan.topicName,
                "plannedDurationMinutes" to plan.plannedDurationMinutes,
                "targetDate" to plan.targetDate,
                "dateString" to schedDate,
                "startMinutes" to plan.startMinutes,
                "endMinutes" to plan.endMinutes,
                "status" to plan.status,
                // Legacy aliases for backwards compatibility
                "title" to plan.subjectName,
                "subject" to plan.topicName,
                "targetMinutes" to plan.plannedDurationMinutes,
                "scheduledDate" to schedDate,
                "targetDateMillis" to plan.targetDate,
                "isCompleted" to plan.isCompleted,
                "startTime" to plan.startTime,
                "endTime" to plan.endTime,
                "colorHex" to plan.colorHex,
                "notes" to plan.notes,
                "createdAt" to Timestamp(Date(plan.createdAt)),
                "updatedAt" to Timestamp(Date(System.currentTimeMillis()))
            )
            writeToFirestore(uid, "study_plans", safeDocId(plan.id), planPayload)
        }

        // 5b. Pull remote study plans (reading both canonical and legacy field names)
        val remotePlansDocs = listFromFirestore(uid, "study_plans")
        for (doc in remotePlansDocs) {
            val id = doc.readString("id") ?: doc.id
            val subjectName = doc.readString("subjectName")?.takeIf { it.isNotBlank() }
                ?: doc.readString("title")?.takeIf { it.isNotBlank() }
                ?: "Study Plan"
            val topicName = doc.readString("topicName")
                ?: doc.readString("subject")
                ?: ""
            val startTime = doc.readString("startTime") ?: "08:00"
            val endTime = doc.readString("endTime") ?: "09:00"

            if (id in deletedPlanIds || isLegacyPreloadedStudyPlan(id, subjectName, topicName, startTime, endTime)) {
                deleteFromFirestore(uid, "study_plans", id)
                continue
            }

            val existing = database.studyPlanDao().getPlanById(id)
            val isDone = doc.readBoolean("isCompleted") ?: (doc.readString("status") == "COMPLETED")
            val duration = doc.readInt("plannedDurationMinutes")
                ?: doc.readInt("targetMinutes")
                ?: 30
            val schedDate = doc.readString("dateString")?.takeIf { it.isNotBlank() }
                ?: doc.readString("scheduledDate").orEmpty()
            val createdAt = doc.readMillis("createdAt") ?: existing?.createdAt ?: System.currentTimeMillis()
            val parsedDateMillis = if (schedDate.isNotBlank()) {
                runCatching { isoFormat.parse(schedDate)?.time }.getOrNull()
            } else null
            val targetDate = doc.readLong("targetDate")
                ?: doc.readLong("targetDateMillis")
                ?: parsedDateMillis
                ?: createdAt
            val colorHex = doc.readString("colorHex") ?: "#7C3AED"
            val notes = doc.readString("notes") ?: ""
            val startMins = doc.readInt("startMinutes")
                ?: StudyPlanRepository.parseTimeToMinutesOrNull(startTime)
                ?: 480
            val endMins = doc.readInt("endMinutes")
                ?: StudyPlanRepository.parseTimeToMinutesOrNull(endTime)
                ?: (startMins + duration)
            val subjectId = doc.readString("subjectId")?.takeIf { it.isNotBlank() }
                ?: existing?.subjectId
                ?: "sub_$id"

            val restoredPlan = StudyPlanEntity(
                id = id,
                subjectId = subjectId,
                subjectName = subjectName,
                topicName = topicName,
                plannedDurationMinutes = duration,
                targetDate = targetDate,
                isCompleted = isDone,
                colorHex = colorHex,
                createdAt = createdAt,
                dateString = schedDate.ifBlank { isoFormat.format(Date(targetDate)) },
                startTime = startTime,
                endTime = endTime,
                startMinutes = startMins,
                endMinutes = endMins,
                status = if (isDone) "COMPLETED" else "PLANNED",
                notes = notes
            )

            if (existing == null) {
                database.studyPlanDao().insertPlan(restoredPlan)
            } else if (id !in locallyModifiedPlanIds && existing != restoredPlan) {
                database.studyPlanDao().updatePlan(restoredPlan)
            }
        }
    }

    private suspend fun syncBlockedAppsWithFirestore(
        uid: String,
        deletedPackages: Set<String>,
        locallyModifiedPackages: Set<String>
    ) {
        val bulkDao = database.cloudBulkDao()
        val localBefore = bulkDao.blockedApps()

        for (app in localBefore) {
            if (app.packageName in deletedPackages) continue
            if (app.packageName in locallyModifiedPackages) {
                pushBlockedAppToFirestore(uid, app)
            }
        }

        val remotePackagesSeen = mutableSetOf<String>()
        val docs = listFromFirestore(uid, "blocked_apps")
        val byPkg = localBefore.associateBy { it.packageName }
        val toUpsert = mutableListOf<BlockedAppEntity>()

        for (doc in docs) {
            val pkg = doc.readString("packageName")?.trim().orEmpty()
            if (pkg.isBlank()) {
                SyncTracker.recordRejectedCloudRow(
                    SyncTables.BLOCKED_APPS,
                    doc.id,
                    IllegalArgumentException("Missing packageName")
                )
                continue
            }
            if (pkg in deletedPackages) continue
            remotePackagesSeen.add(pkg)

            val remoteEntity = BlockedAppEntity(
                packageName = pkg,
                appName = doc.readString("appName") ?: pkg,
                isEnabled = doc.readBoolean("isEnabled") ?: true,
                createdAt = doc.readMillis("createdAt") ?: System.currentTimeMillis()
            )

            val existing = byPkg[pkg]
            if (existing == null) {
                toUpsert.add(remoteEntity)
            } else if (pkg !in locallyModifiedPackages && existing != remoteEntity) {
                toUpsert.add(remoteEntity)
            }
        }
        if (toUpsert.isNotEmpty()) {
            bulkDao.upsertBlockedApps(toUpsert)
        }

        val mergedLocal = bulkDao.blockedApps()
        for (app in mergedLocal) {
            if (app.packageName in deletedPackages) continue
            if (app.packageName !in remotePackagesSeen && app.packageName !in locallyModifiedPackages) {
                pushBlockedAppToFirestore(uid, app)
            }
        }
    }

    private suspend fun pushBlockedAppToFirestore(
        uid: String,
        app: BlockedAppEntity
    ) {
        val payload = hashMapOf<String, Any>(
            "packageName" to app.packageName,
            "userId" to uid,
            "appName" to app.appName,
            "isEnabled" to app.isEnabled,
            "createdAt" to app.createdAt
        )
        writeToFirestore(uid, "blocked_apps", safeDocId(app.packageName), payload)
    }

    private suspend fun syncBlockedWebsitesWithFirestore(
        uid: String,
        deletedDomains: Set<String>,
        locallyModifiedDomains: Set<String>
    ) {
        val bulkDao = database.cloudBulkDao()
        val localBefore = bulkDao.blockedWebsites()

        for (site in localBefore) {
            if (site.domain in deletedDomains) continue
            if (site.domain in locallyModifiedDomains) {
                pushBlockedWebsiteToFirestore(uid, site)
            }
        }

        val remoteDomainsSeen = mutableSetOf<String>()
        val docs = listFromFirestore(uid, "blocked_websites")
        val byDomain = localBefore.associateBy { it.domain }
        val toUpsert = mutableListOf<BlockedWebsiteEntity>()

        for (doc in docs) {
            val domain = doc.readString("domain")?.trim().orEmpty()
            if (domain.isBlank()) {
                SyncTracker.recordRejectedCloudRow(
                    SyncTables.BLOCKED_WEBSITES,
                    doc.id,
                    IllegalArgumentException("Missing domain")
                )
                continue
            }
            if (domain in deletedDomains) continue
            remoteDomainsSeen.add(domain)

            val categoryRaw = doc.readString("category")?.uppercase(Locale.ROOT) ?: "MANUAL"
            val category = if (categoryRaw == "ADULT") "ADULT" else "MANUAL"
            val remoteEntity = BlockedWebsiteEntity(
                domain = domain,
                isEnabled = doc.readBoolean("isEnabled") ?: true,
                category = category,
                createdAt = doc.readMillis("createdAt") ?: System.currentTimeMillis()
            )

            val existing = byDomain[domain]
            if (existing == null) {
                toUpsert.add(remoteEntity)
            } else if (domain !in locallyModifiedDomains && existing != remoteEntity) {
                toUpsert.add(remoteEntity)
            }
        }
        if (toUpsert.isNotEmpty()) {
            bulkDao.upsertBlockedWebsites(toUpsert)
        }

        val mergedLocal = bulkDao.blockedWebsites()
        for (site in mergedLocal) {
            if (site.domain in deletedDomains) continue
            if (site.domain !in remoteDomainsSeen && site.domain !in locallyModifiedDomains) {
                pushBlockedWebsiteToFirestore(uid, site)
            }
        }
    }

    private suspend fun pushBlockedWebsiteToFirestore(
        uid: String,
        site: BlockedWebsiteEntity
    ) {
        val payload = hashMapOf<String, Any>(
            "domain" to site.domain,
            "userId" to uid,
            "isEnabled" to site.isEnabled,
            "category" to site.category,
            "createdAt" to site.createdAt
        )
        writeToFirestore(uid, "blocked_websites", safeDocId(site.domain), payload)
    }

    private suspend fun syncKeywordsWithFirestore(
        uid: String,
        deletedKeys: Set<String>,
        locallyModifiedKeys: Set<String>
    ) {
        val bulkDao = database.cloudBulkDao()
        val localBefore = bulkDao.keywords()

        for (kw in localBefore) {
            val key = keywordCompositeKey(kw.type, kw.keyword)
            if (key in deletedKeys) continue
            if (key in locallyModifiedKeys) {
                pushKeywordToFirestore(uid, kw)
            }
        }

        val remoteKeysSeen = mutableSetOf<String>()
        val docs = listFromFirestore(uid, "blocked_keywords")
        val byNaturalKey = localBefore.associateBy { keywordCompositeKey(it.type, it.keyword) }
        val toInsert = mutableListOf<KeywordEntity>()

        for (doc in docs) {
            val typeRaw = (doc.readString("keyword_type") ?: doc.readString("type") ?: "block")
                .trim().lowercase(Locale.ROOT)
            val type = if (typeRaw == "allow") "allow" else "block"
            val value = (doc.readString("keyword_value") ?: doc.readString("keyword") ?: "")
                .trim().lowercase(Locale.ROOT)
            if (value.isBlank()) {
                SyncTracker.recordRejectedCloudRow(
                    SyncTables.BLOCKED_KEYWORDS,
                    doc.id,
                    IllegalArgumentException("Missing keyword_value")
                )
                continue
            }

            val key = keywordCompositeKey(type, value)
            if (key in deletedKeys) continue
            remoteKeysSeen.add(key)

            if (key !in byNaturalKey) {
                toInsert.add(
                    KeywordEntity(
                        id = 0L,
                        keyword = value,
                        type = type,
                        isActive = doc.readBoolean("is_active") ?: doc.readBoolean("isActive") ?: true,
                        createdAt = doc.readMillis("created_at") ?: doc.readMillis("createdAt") ?: System.currentTimeMillis()
                    )
                )
            }
        }
        if (toInsert.isNotEmpty()) {
            bulkDao.upsertKeywords(toInsert)
        }

        val mergedLocal = bulkDao.keywords()
        for (kw in mergedLocal) {
            val key = keywordCompositeKey(kw.type, kw.keyword)
            if (key in deletedKeys) continue
            if (key !in remoteKeysSeen && key !in locallyModifiedKeys) {
                pushKeywordToFirestore(uid, kw)
            }
        }
    }

    private suspend fun pushKeywordToFirestore(
        uid: String,
        kw: KeywordEntity
    ) {
        val key = keywordCompositeKey(kw.type, kw.keyword)
        val payload = hashMapOf<String, Any>(
            "userId" to uid,
            "keyword_type" to kw.type,
            "keyword_value" to kw.keyword,
            "is_active" to kw.isActive,
            "created_at" to kw.createdAt
        )
        writeToFirestore(uid, "blocked_keywords", safeDocId(key), payload)
    }

    private suspend fun syncScratchCardsWithFirestore(
        uid: String
    ) {
        val bulkDao = database.cloudBulkDao()
        val localCards = bulkDao.scratchCards()
        val localBySessionId = localCards.associateBy { it.sessionId }

        for (card in localCards) {
            val payload = hashMapOf<String, Any>(
                "sessionId" to card.sessionId,
                "userId" to uid,
                "createdAt" to card.createdAt,
                "rewardType" to card.rewardType,
                "rewardEmoji" to card.rewardEmoji,
                "rewardTitle" to card.rewardTitle,
                "rewardMessage" to card.rewardMessage,
                "studyMinutes" to card.studyMinutes,
                "isRevealed" to card.isRevealed
            )
            writeToFirestore(uid, "scratch_cards", safeDocId(card.sessionId), payload)
        }

        val docs = listFromFirestore(uid, "scratch_cards")
        val toInsert = mutableListOf<ScratchCardEntity>()
        for (doc in docs) {
            val sessionId = doc.readString("sessionId") ?: doc.id
            if (sessionId.isBlank()) continue
            val remoteRevealed = doc.readBoolean("isRevealed") ?: false
            val existing = localBySessionId[sessionId]
            if (existing == null) {
                toInsert.add(
                    ScratchCardEntity(
                        sessionId = sessionId,
                        createdAt = doc.readMillis("createdAt") ?: System.currentTimeMillis(),
                        rewardType = doc.readString("rewardType") ?: "QUOTE",
                        rewardEmoji = doc.readString("rewardEmoji") ?: "✨",
                        rewardTitle = doc.readString("rewardTitle") ?: "Focus Milestone",
                        rewardMessage = doc.readString("rewardMessage") ?: "Great focus session!",
                        studyMinutes = doc.readInt("studyMinutes") ?: 25,
                        isRevealed = remoteRevealed
                    )
                )
            } else if (!existing.isRevealed && remoteRevealed) {
                database.scratchCardDao().markRevealed(sessionId)
            }
        }
        if (toInsert.isNotEmpty()) {
            bulkDao.insertScratchCardsMissing(toInsert)
        }
    }

    private suspend fun pushChannelToFirestore(
        uid: String,
        channel: StudyChannelEntity
    ) {
        val remoteThumb = if (channel.thumbnailUrl.startsWith("http")) channel.thumbnailUrl else ""
        val payload = hashMapOf<String, Any>(
            "id" to channel.id,
            "userId" to uid,
            "channelId" to channel.channelId,
            "channelName" to channel.channelName,
            "channelUrl" to channel.channelUrl,
            "thumbnailUrl" to remoteThumb,
            "isApproved" to channel.isApproved,
            "createdAt" to Timestamp(Date(channel.createdAt)),
            "updatedAt" to Timestamp(Date(System.currentTimeMillis()))
        )
        writeToFirestore(uid, "study_channels", safeDocId(channel.id), payload)
    }

    private suspend fun pushScheduleToFirestore(
        uid: String,
        schedule: FocusScheduleEntity
    ) {
        val schedulePayload = hashMapOf<String, Any>(
            "id" to schedule.id,
            "userId" to uid,
            "title" to schedule.title,
            "daysOfWeek" to schedule.daysOfWeek,
            "startTime" to schedule.startTime,
            "endTime" to schedule.endTime,
            "isEnabled" to schedule.isEnabled,
            "isAutoStartSession" to schedule.isAutoStartSession,
            "mode" to schedule.mode,
            "subjectName" to schedule.subjectName,
            "colorHex" to schedule.colorHex,
            "repeatEnabled" to schedule.repeatEnabled,
            "scheduledDateMillis" to schedule.scheduledDateMillis,
            "breakMinutes" to schedule.breakMinutes,
            "description" to schedule.description,
            "blockedAppPackages" to schedule.blockedAppPackages,
            "blockNotifications" to schedule.blockNotifications,
            "createdAt" to Timestamp(Date(schedule.createdAt)),
            "updatedAt" to Timestamp(Date(System.currentTimeMillis()))
        )
        writeToFirestore(uid, "focus_schedules", safeDocId(schedule.id), schedulePayload)
    }

    private fun isLegacyPreloadedStudyPlan(
        id: String,
        subjectName: String,
        topicName: String,
        startTime: String,
        endTime: String
    ): Boolean {
        return id.startsWith("default-plan") ||
            (subjectName == "Physics" && topicName == "Electrostatics" && startTime == "08:00" && endTime == "10:00") ||
            (subjectName == "Chemistry" && topicName == "Organic Chemistry" && startTime == "10:30" && endTime == "12:00") ||
            (subjectName == "Mathematics" && topicName == "Calculus" && startTime == "14:00" && endTime == "15:30")
    }

    private suspend fun syncStudyActivitiesWithFirestore(
        uid: String
    ) {
        val localActivities = database.cloudBulkDao().activities()
        val localIds = localActivities.map { it.id }.toSet()

        // Push local study activities (YouTube Study Mode & Study Browser usage)
        for (act in localActivities) {
            val payload = hashMapOf<String, Any?>(
                "id" to act.id,
                "userId" to uid,
                "sessionId" to act.sessionId,
                "activityType" to act.activityType.name,
                "source" to act.source.name,
                "subject" to act.subject,
                "topic" to act.topic,
                "channelName" to act.channelName,
                "channelId" to act.channelId,
                "videoTitle" to act.videoTitle,
                "startedAt" to act.startedAt,
                "endedAt" to act.endedAt,
                "durationMillis" to act.durationMillis,
                "createdAt" to act.createdAt
            )
            writeToFirestore(uid, "study_activities", safeDocId(act.id), payload)
        }

        // Pull remote study activities missing locally
        val docs = listFromFirestore(uid, "study_activities")
        val toInsert = mutableListOf<StudyActivityEntity>()
        for (doc in docs) {
            val id = doc.readString("id") ?: doc.id
            if (id in localIds) continue
            val typeStr = doc.readString("activityType") ?: "FOCUS_SESSION"
            val sourceStr = doc.readString("source") ?: "TIMER"
            val activityType = runCatching {
                StudyActivityType.valueOf(typeStr)
            }.getOrDefault(StudyActivityType.FOCUS_SESSION)
            val source = runCatching {
                StudyActivitySource.valueOf(sourceStr)
            }.getOrDefault(StudyActivitySource.TIMER)
            val durationMillis = doc.readLong("durationMillis") ?: 0L
            val endedAt = doc.readLong("endedAt") ?: System.currentTimeMillis()
            val startedAt = doc.readLong("startedAt") ?: (endedAt - durationMillis)
            val createdAt = doc.readMillis("createdAt") ?: endedAt

            toInsert.add(
                StudyActivityEntity(
                    id = id,
                    sessionId = doc.readString("sessionId") ?: "",
                    activityType = activityType,
                    source = source,
                    subject = doc.readString("subject") ?: "Study",
                    topic = doc.readString("topic") ?: "General",
                    channelName = doc.readString("channelName"),
                    channelId = doc.readString("channelId"),
                    videoTitle = doc.readString("videoTitle"),
                    startedAt = startedAt,
                    endedAt = endedAt,
                    durationMillis = durationMillis,
                    createdAt = createdAt
                )
            )
        }
        if (toInsert.isNotEmpty()) {
            database.cloudBulkDao().insertActivitiesMissing(toInsert)
        }
    }

    private suspend fun syncBreakRecordsWithFirestore(
        uid: String
    ) {
        val localBreaks = database.cloudBulkDao().breaks()
        val localIds = localBreaks.map { it.id }.toSet()

        for (brk in localBreaks) {
            val payload = hashMapOf<String, Any>(
                "id" to brk.id,
                "userId" to uid,
                "sessionId" to brk.sessionId,
                "requestedDurationMillis" to brk.requestedDurationMillis,
                "actualDurationMillis" to brk.actualDurationMillis,
                "startTime" to brk.startTime,
                "endTime" to brk.endTime,
                "completed" to brk.completed,
                "createdAt" to brk.createdAt
            )
            writeToFirestore(uid, "break_records", safeDocId(brk.id), payload)
        }

        val docs = listFromFirestore(uid, "break_records")
        val toInsert = mutableListOf<BreakRecordEntity>()
        for (doc in docs) {
            val id = doc.readString("id") ?: doc.id
            if (id in localIds) continue
            val sessionId = doc.readString("sessionId")?.takeIf { it.isNotBlank() }
            if (sessionId == null) {
                SyncTracker.recordRejectedCloudRow(
                    SyncTables.BREAK_RECORDS,
                    id,
                    IllegalArgumentException("Missing sessionId")
                )
                continue
            }
            val actualDurationMillis = doc.readLong("actualDurationMillis") ?: 0L
            val requestedDurationMillis = doc.readLong("requestedDurationMillis") ?: actualDurationMillis
            val endTime = doc.readLong("endTime") ?: System.currentTimeMillis()
            val startTime = doc.readLong("startTime") ?: (endTime - actualDurationMillis)
            val createdAt = doc.readMillis("createdAt") ?: endTime

            toInsert.add(
                BreakRecordEntity(
                    id = id,
                    sessionId = sessionId,
                    requestedDurationMillis = requestedDurationMillis,
                    actualDurationMillis = actualDurationMillis,
                    startTime = startTime,
                    endTime = endTime,
                    completed = doc.readBoolean("completed") ?: true,
                    createdAt = createdAt
                )
            )
        }
        if (toInsert.isNotEmpty()) {
            database.cloudBulkDao().insertBreaksMissing(toInsert)
        }
    }

    private suspend fun syncBlockedAttemptsWithFirestore(
        uid: String
    ) {
        val localAttempts = database.cloudBulkDao().blockedAttempts()
        val localEventIds = localAttempts.map { it.eventId }.toSet()

        // Push recent blocked attempts (last 500 events to keep sync fast while preserving analytics history)
        val attemptsToPush = if (localAttempts.size > 500) {
            localAttempts.sortedByDescending { it.timestamp }.take(500)
        } else {
            localAttempts
        }
        for (att in attemptsToPush) {
            if (att.eventId.isBlank()) continue
            val payload = hashMapOf<String, Any?>(
                "eventId" to att.eventId,
                "userId" to uid,
                "timestamp" to att.timestamp,
                "eventType" to att.eventType.name,
                "source" to att.source.name,
                "packageName" to att.packageName,
                "appName" to att.appName,
                "domain" to att.domain,
                "channelId" to att.channelId,
                "channelName" to att.channelName,
                "videoTitle" to att.videoTitle,
                "matchedKeyword" to att.matchedKeyword,
                "ruleRef" to att.ruleRef,
                "sessionId" to att.sessionId,
                "scheduleId" to att.scheduleId,
                "subject" to att.subject,
                "topic" to att.topic,
                "deviceId" to att.deviceId,
                "createdAt" to att.timestamp
            )
            writeToFirestore(uid, "blocked_attempts", safeDocId(att.eventId), payload)
        }

        val docs = listFromFirestore(uid, "blocked_attempts")
        val toInsert = mutableListOf<BlockedAttemptEntity>()
        for (doc in docs) {
            val eventId = doc.readString("eventId") ?: doc.id
            if (eventId.isBlank() || eventId in localEventIds) continue
            val eventTypeStr = doc.readString("eventType") ?: "LEGACY"
            val sourceStr = doc.readString("source") ?: "LEGACY"
            val eventType = runCatching {
                BlockedEventType.valueOf(eventTypeStr)
            }.getOrDefault(BlockedEventType.LEGACY)
            val source = runCatching {
                BlockedEventSource.valueOf(sourceStr)
            }.getOrDefault(BlockedEventSource.LEGACY)

            toInsert.add(
                BlockedAttemptEntity(
                    eventId = eventId,
                    timestamp = doc.readLong("timestamp") ?: System.currentTimeMillis(),
                    eventType = eventType,
                    source = source,
                    packageName = doc.readString("packageName") ?: "",
                    appName = doc.readString("appName") ?: "",
                    domain = doc.readString("domain"),
                    channelId = doc.readString("channelId"),
                    channelName = doc.readString("channelName"),
                    videoTitle = doc.readString("videoTitle"),
                    matchedKeyword = doc.readString("matchedKeyword"),
                    ruleRef = doc.readString("ruleRef"),
                    sessionId = doc.readString("sessionId"),
                    scheduleId = doc.readString("scheduleId"),
                    subject = doc.readString("subject"),
                    topic = doc.readString("topic"),
                    deviceId = doc.readString("deviceId")
                )
            )
        }
        if (toInsert.isNotEmpty()) {
            database.cloudBulkDao().insertBlockedAttemptsMissing(toInsert)
        }
    }

    private fun isLegacyPreloadedSchedule(
        title: String,
        subjectName: String,
        startTime: String,
        endTime: String
    ): Boolean {
        return (title == "Morning Deep Work" && subjectName == "Mathematics & Physics" && startTime == "09:00" && endTime == "11:30") ||
            (title == "Afternoon Focus Window" && subjectName == "General Study" && startTime == "14:00" && endTime == "16:00")
    }
}
