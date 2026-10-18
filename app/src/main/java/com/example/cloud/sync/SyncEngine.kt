package com.example.cloud.sync

import android.util.Log
import com.example.FocusShieldApp
import com.example.R
import com.example.cloud.auth.AuthRepository
import com.example.cloud.firestore.OperationType
import com.example.cloud.firestore.handleFirestoreError
import com.example.core.util.ChannelLogoStorageManager
import com.example.data.local.FocusShieldDatabase
import com.example.data.local.entity.FocusScheduleEntity
import com.example.data.local.entity.SessionRecordEntity
import com.example.data.local.entity.StudyChannelEntity
import com.example.data.local.entity.StudyPlanEntity
import com.example.data.model.SessionMode
import com.example.data.preferences.FocusPreferencesRepository
import com.example.data.repository.StudyPlanRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
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

/**
 * Synchronizes FocusShield study sessions, study plans, study channels, focus schedules,
 * and profile metadata with Cloud Firestore under /users/{userId}/...
 */
class SyncEngine(
    private val database: FocusShieldDatabase,
    private val installState: CloudInstallState,
    private val preferencesRepository: FocusPreferencesRepository,
    private val authRepository: AuthRepository
) {
    private val db: FirebaseFirestore by lazy {
        val context = FocusShieldApp.instance
        val dbId = context.getString(R.string.firestore_database_id)
        FirebaseFirestore.getInstance(dbId)
    }

    suspend fun runCycle(): SyncCycleOutcome {
        val uid = authRepository.currentUserId() ?: return SyncCycleOutcome.NoSession
        return SyncTracker.lock.withLock {
            try {
                syncWithFirestore(uid)
                SyncCycleOutcome.Synced
            } catch (e: IOException) {
                SyncCycleOutcome.Offline
            } catch (e: Exception) {
                Log.e(TAG, "Sync error: ${e.message}", e)
                SyncCycleOutcome.Failed(e.localizedMessage ?: "Sync error")
            }
        }
    }

    suspend fun migrate(): SyncCycleOutcome = runCycle()

    suspend fun restore(uid: String): SyncCycleOutcome = runCycle()

    private suspend fun syncWithFirestore(uid: String) {
        val appContext = try { FocusShieldApp.instance } catch (_: Exception) { null }
        val userDoc = db.collection("users").document(uid)

        // 0. Inspect Pending Outbox Operations (Deletions & Local Upserts) BEFORE Pulling
        val pendingPlanOps = try {
            database.syncOutboxDao().pendingForTable(SyncTables.STUDY_PLANS)
        } catch (_: Exception) {
            emptyList()
        }
        val deletedPlanIds = pendingPlanOps
            .filter { it.op == SyncTracker.OP_DELETE }
            .map { it.rowId }
            .toMutableSet()
            .apply {
                add("default-plan-1")
                add("default-plan-2")
                add("default-plan-3")
            }

        val pendingChannelOps = try {
            database.syncOutboxDao().pendingForTable(SyncTables.STUDY_CHANNELS)
        } catch (_: Exception) {
            emptyList()
        }
        val deletedChannelIds = pendingChannelOps
            .filter { it.op == SyncTracker.OP_DELETE }
            .map { it.rowId }
            .toSet()
        val locallyModifiedChannelIds = pendingChannelOps
            .filter { it.op == SyncTracker.OP_UPSERT }
            .map { it.rowId }
            .toSet()

        val pendingScheduleOps = try {
            database.syncOutboxDao().pendingForTable(SyncTables.FOCUS_SCHEDULES)
        } catch (_: Exception) {
            emptyList()
        }
        val deletedScheduleIds = pendingScheduleOps
            .filter { it.op == SyncTracker.OP_DELETE }
            .map { it.rowId }
            .toMutableSet()

        // Execute pending Firestore deletions FIRST so deleted items never reappear on pull
        for (planId in deletedPlanIds) {
            try {
                userDoc.collection("study_plans").document(planId).delete().await()
            } catch (_: Exception) {}
        }
        for (channelId in deletedChannelIds) {
            try {
                userDoc.collection("study_channels").document(channelId).delete().await()
            } catch (_: Exception) {}
        }
        for (scheduleId in deletedScheduleIds) {
            try {
                userDoc.collection("focus_schedules").document(scheduleId).delete().await()
            } catch (_: Exception) {}
        }

        // 1. Push Local Sessions
        val initialLocalSessions = database.sessionDao().getAllSessionsAscending()
        for (session in initialLocalSessions) {
            val sessionDoc = userDoc.collection("sessions").document(session.id)
            val sessionPayload = hashMapOf<String, Any>(
                "id" to session.id,
                "userId" to uid,
                "mode" to session.mode.name,
                "durationMinutes" to ((session.actualDurationMillis / (1000 * 60)).toInt()),
                "subject" to session.subject,
                "topic" to session.topic,
                "goal" to session.goal,
                "actualDurationMillis" to session.actualDurationMillis,
                "completed" to session.completed,
                "completedAt" to Timestamp(Date(session.endTime)),
                "createdAt" to Timestamp(Date(session.createdAt))
            )
            try {
                sessionDoc.set(sessionPayload, SetOptions.merge()).await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, sessionDoc.path)
            }
        }

        // 2. Pull Remote Sessions (so signing in on a second phone restores all session history)
        try {
            val remoteSessionsSnap = userDoc.collection("sessions").get().await()
            for (doc in remoteSessionsSnap.documents) {
                val id = doc.getString("id") ?: doc.id
                val existing = database.sessionDao().getSessionById(id)
                if (existing == null) {
                    val modeStr = doc.getString("mode") ?: "TIMER"
                    val mode = runCatching { SessionMode.valueOf(modeStr) }
                        .getOrDefault(SessionMode.TIMER)
                    val durMinutes = (doc.getLong("durationMinutes") ?: 0L).toInt()
                    val durMillis = durMinutes * 60 * 1000L
                    val subject = doc.getString("subject") ?: "Study"
                    val topic = doc.getString("topic") ?: "General"
                    val goal = doc.getString("goal") ?: "Deep Focus"
                    val actualMillis = doc.getLong("actualDurationMillis") ?: durMillis
                    val completed = doc.getBoolean("completed") ?: true
                    val completedAt = doc.getTimestamp("completedAt")?.toDate()?.time ?: System.currentTimeMillis()
                    val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: completedAt

                    val restoredSession = SessionRecordEntity(
                        id = id,
                        mode = mode,
                        title = "$subject - $topic",
                        subject = subject,
                        topic = topic,
                        goal = goal,
                        plannedDurationMillis = durMillis,
                        actualDurationMillis = actualMillis,
                        startTime = completedAt - actualMillis,
                        endTime = completedAt,
                        completed = completed,
                        cancelled = !completed,
                        pomodoroCycles = 1,
                        completedPomodoroCycles = 1,
                        createdAt = createdAt
                    )
                    database.sessionDao().insertSession(restoredSession)
                }
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "${userDoc.path}/sessions")
        }

        // 3. Sync Profile Document (computed after merging local + remote sessions)
        val mergedSessions = database.sessionDao().getAllSessionsAscending()
        val calculatedStreak = com.example.feature.analytics.domain.StreakCalculator.calculate(mergedSessions).currentStreak
        val currentPrefs = preferencesRepository.preferencesFlow.first()
        var userGoalMinutes = currentPrefs.dailyGoalMinutes

        try {
            val userSnap = userDoc.get().await()
            if (userSnap.exists()) {
                val remoteGoal = userSnap.getLong("dailyStudyGoalMinutes")?.toInt()
                if (remoteGoal != null && remoteGoal > 0 && initialLocalSessions.isEmpty() && userGoalMinutes == 240) {
                    userGoalMinutes = remoteGoal
                }
                val remoteDisplayName = userSnap.getString("displayName")
                if (!remoteDisplayName.isNullOrBlank() && currentPrefs.userName == "Focus Scholar") {
                    preferencesRepository.updateUserName(remoteDisplayName)
                }
            }
            val existingCreatedAt = if (userSnap.exists()) userSnap.get("createdAt") else null
            val profilePayload = hashMapOf<String, Any>(
                "userId" to uid,
                "displayName" to (authRepository.currentDisplayName().takeUnless { it.isNullOrBlank() } ?: currentPrefs.userName),
                "email" to (authRepository.currentUserEmail() ?: ""),
                "dailyStudyGoalMinutes" to userGoalMinutes,
                "currentStreak" to calculatedStreak,
                "updatedAt" to FieldValue.serverTimestamp(),
                "createdAt" to (existingCreatedAt ?: FieldValue.serverTimestamp())
            )
            userDoc.set(profilePayload, SetOptions.merge()).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userDoc.path)
            throw e
        }

        // 4. Push Local Study Plans
        val localPlans = database.studyPlanDao().getAllPlans()
        val isoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        for (plan in localPlans) {
            if (plan.id in deletedPlanIds || isLegacyPreloadedStudyPlan(plan.id, plan.subjectName, plan.topicName, plan.startTime, plan.endTime)) {
                database.studyPlanDao().deletePlanById(plan.id)
                try {
                    userDoc.collection("study_plans").document(plan.id).delete().await()
                } catch (_: Exception) {}
                continue
            }
            val planDoc = userDoc.collection("study_plans").document(plan.id)
            val schedDate = if (plan.dateString.isNotBlank()) plan.dateString else isoFormat.format(Date(plan.targetDate))
            val planPayload = hashMapOf<String, Any>(
                "id" to plan.id,
                "userId" to uid,
                "title" to plan.subjectName,
                "subject" to plan.topicName,
                "targetMinutes" to plan.plannedDurationMinutes,
                "isCompleted" to plan.isCompleted,
                "scheduledDate" to schedDate,
                "startTime" to plan.startTime,
                "endTime" to plan.endTime,
                "colorHex" to plan.colorHex,
                "notes" to plan.notes,
                "targetDateMillis" to plan.targetDate,
                "createdAt" to Timestamp(Date(plan.createdAt)),
                "updatedAt" to Timestamp(Date(System.currentTimeMillis()))
            )
            try {
                planDoc.set(planPayload, SetOptions.merge()).await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, planDoc.path)
            }
        }

        // 5. Pull Remote Study Plans (skipping deleted or legacy preloaded plans)
        try {
            val remotePlansSnap = userDoc.collection("study_plans").get().await()
            for (doc in remotePlansSnap.documents) {
                val id = doc.getString("id") ?: doc.id
                val title = doc.getString("title") ?: "Study Plan"
                val subject = doc.getString("subject") ?: ""
                val startTime = doc.getString("startTime") ?: "08:00"
                val endTime = doc.getString("endTime") ?: "09:00"

                if (id in deletedPlanIds || isLegacyPreloadedStudyPlan(id, title, subject, startTime, endTime)) {
                    try {
                        userDoc.collection("study_plans").document(id).delete().await()
                    } catch (_: Exception) {}
                    continue
                }

                val existing = database.studyPlanDao().getPlanById(id)
                val isDone = doc.getBoolean("isCompleted") ?: false
                if (existing == null) {
                    val duration = (doc.getLong("targetMinutes") ?: 30L).toInt()
                    val schedDate = doc.getString("scheduledDate") ?: ""
                    val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                    val parsedDateMillis = if (schedDate.isNotBlank()) {
                        runCatching { isoFormat.parse(schedDate)?.time }.getOrNull()
                    } else null
                    val targetDate = doc.getLong("targetDateMillis") ?: parsedDateMillis ?: createdAt
                    val colorHex = doc.getString("colorHex") ?: "#7C3AED"
                    val notes = doc.getString("notes") ?: ""
                    val startMins = StudyPlanRepository.parseTimeToMinutesOrNull(startTime) ?: 480
                    val endMins = StudyPlanRepository.parseTimeToMinutesOrNull(endTime) ?: (startMins + duration)

                    val restoredPlan = StudyPlanEntity(
                        id = id,
                        subjectId = "sub_$id",
                        subjectName = title,
                        topicName = subject,
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
                    database.studyPlanDao().insertPlan(restoredPlan)
                } else if (existing.isCompleted != isDone) {
                    database.studyPlanDao().updatePlanCompletion(id, isDone)
                }
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "${userDoc.path}/study_plans")
        }

        // 6. Two-Way Sync & Merge for Study Channels (/users/{uid}/study_channels/{channelId})
        // 6a. Push locally modified channels first so local additions/approval toggles take precedence
        val localChannelsBeforePull = database.studyChannelDao().getAllChannels()
        for (channel in localChannelsBeforePull) {
            if (channel.id in deletedChannelIds) continue
            if (channel.id in locallyModifiedChannelIds) {
                pushChannelToFirestore(userDoc, uid, channel)
            }
        }

        // 6b. Pull remote channels from Firestore and merge with local channels
        val remoteChannelIdsSeen = mutableSetOf<String>()
        try {
            val remoteChannelsSnap = userDoc.collection("study_channels").get().await()
            for (doc in remoteChannelsSnap.documents) {
                val id = doc.getString("id") ?: doc.id
                if (id in deletedChannelIds) continue

                val channelId = doc.getString("channelId")?.trim().orEmpty()
                val channelName = doc.getString("channelName")?.trim().orEmpty()
                if (channelId.isBlank() || channelName.isBlank()) continue

                val channelUrl = doc.getString("channelUrl") ?: "https://www.youtube.com/$channelId"
                val thumbnailUrl = doc.getString("thumbnailUrl") ?: ""
                val isApproved = doc.getBoolean("isApproved") ?: true
                val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()

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
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "${userDoc.path}/study_channels")
        }

        // 6c. Push any local channels not yet in Firestore so cloud + local are fully merged
        val mergedLocalChannels = database.studyChannelDao().getAllChannels()
        for (channel in mergedLocalChannels) {
            if (channel.id in deletedChannelIds) continue
            if (channel.id !in remoteChannelIdsSeen && channel.id !in locallyModifiedChannelIds) {
                pushChannelToFirestore(userDoc, uid, channel)
            }
        }

        // Refresh in-memory approved channel sets immediately for YouTube Study Mode
        try {
            appContext?.studyChannelRepository?.refreshApprovedCache()
        } catch (_: Exception) {}

        // 7. Two-Way Sync for Recurring Focus Schedules (/users/{uid}/focus_schedules/{scheduleId})
        val localSchedules = try {
            database.focusScheduleDao().getAllSchedules()
        } catch (_: Exception) {
            emptyList()
        }
        for (schedule in localSchedules) {
            if (schedule.id in deletedScheduleIds || isLegacyPreloadedSchedule(schedule.title, schedule.subjectName, schedule.startTime, schedule.endTime)) {
                database.focusScheduleDao().deleteScheduleById(schedule.id)
                try {
                    userDoc.collection("focus_schedules").document(schedule.id).delete().await()
                } catch (_: Exception) {}
                continue
            }
            val scheduleDoc = userDoc.collection("focus_schedules").document(schedule.id)
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
            try {
                scheduleDoc.set(schedulePayload, SetOptions.merge()).await()
            } catch (_: Exception) {}
        }

        try {
            val remoteSchedulesSnap = userDoc.collection("focus_schedules").get().await()
            for (doc in remoteSchedulesSnap.documents) {
                val id = doc.getString("id") ?: doc.id
                val title = doc.getString("title") ?: ""
                val subjectName = doc.getString("subjectName") ?: ""
                val startTime = doc.getString("startTime") ?: "09:00"
                val endTime = doc.getString("endTime") ?: "10:00"

                if (id in deletedScheduleIds || isLegacyPreloadedSchedule(title, subjectName, startTime, endTime)) {
                    try {
                        userDoc.collection("focus_schedules").document(id).delete().await()
                    } catch (_: Exception) {}
                    continue
                }

                val existing = database.focusScheduleDao().getScheduleById(id)
                if (existing == null && title.isNotBlank()) {
                    val restoredSchedule = FocusScheduleEntity(
                        id = id,
                        title = title,
                        daysOfWeek = doc.getString("daysOfWeek") ?: "MON,TUE,WED,THU,FRI",
                        startTime = startTime,
                        endTime = endTime,
                        isEnabled = doc.getBoolean("isEnabled") ?: true,
                        isAutoStartSession = doc.getBoolean("isAutoStartSession") ?: true,
                        mode = doc.getString("mode") ?: "TIMER",
                        subjectName = subjectName,
                        colorHex = doc.getString("colorHex") ?: "#4F46E5",
                        repeatEnabled = doc.getBoolean("repeatEnabled") ?: true,
                        scheduledDateMillis = doc.getLong("scheduledDateMillis") ?: 0L,
                        breakMinutes = (doc.getLong("breakMinutes") ?: 5L).toInt(),
                        description = doc.getString("description") ?: "",
                        blockedAppPackages = doc.getString("blockedAppPackages") ?: "",
                        blockNotifications = doc.getBoolean("blockNotifications") ?: false,
                        createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()
                    )
                    database.focusScheduleDao().insertSchedule(restoredSchedule)
                }
            }
        } catch (_: Exception) {}

        // Clear local outbox as data is synchronized
        try {
            database.syncOutboxDao().clearAll()
            SyncTracker.markOutboxDrained()
        } catch (_: Exception) {}

        installState.setDataOwnerAccountId(uid)
        installState.markMigrated(uid)
        installState.rememberAccount(uid)
    }

    private suspend fun pushChannelToFirestore(
        userDoc: com.google.firebase.firestore.DocumentReference,
        uid: String,
        channel: StudyChannelEntity
    ) {
        val channelDoc = userDoc.collection("study_channels").document(channel.id)
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
        try {
            channelDoc.set(payload, SetOptions.merge()).await()
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, channelDoc.path)
        }
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
