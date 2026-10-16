package com.example.cloud.sync

import android.util.Log
import com.example.FocusShieldApp
import com.example.R
import com.example.cloud.auth.AuthRepository
import com.example.cloud.firestore.OperationType
import com.example.cloud.firestore.handleFirestoreError
import com.example.data.local.FocusShieldDatabase
import com.example.data.local.entity.SessionRecordEntity
import com.example.data.local.entity.StudyPlanEntity
import com.example.data.model.SessionMode
import com.example.data.preferences.FocusPreferencesRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.io.IOException
import java.util.Date
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
 * Synchronizes FocusShield study sessions, study plans, and profile metadata
 * with Cloud Firestore under /users/{userId}/...
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
        val userDoc = db.collection("users").document(uid)

        // 1. Sync Profile Document
        val profilePayload = hashMapOf<String, Any>(
            "userId" to uid,
            "displayName" to (authRepository.currentDisplayName().takeUnless { it.isNullOrBlank() } ?: "User"),
            "email" to (authRepository.currentUserEmail() ?: ""),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        try {
            val userSnap = userDoc.get().await()
            if (!userSnap.exists()) {
                profilePayload["createdAt"] = FieldValue.serverTimestamp()
                profilePayload["dailyStudyGoalMinutes"] = 60
                profilePayload["currentStreak"] = 0
                userDoc.set(profilePayload).await()
            } else {
                userDoc.set(profilePayload, SetOptions.merge()).await()
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userDoc.path)
            throw e
        }

        // 2. Push Local Sessions
        val localSessions = database.sessionDao().getAllSessionsAscending()
        for (session in localSessions) {
            val sessionDoc = userDoc.collection("sessions").document(session.id)
            val sessionPayload = hashMapOf<String, Any>(
                "id" to session.id,
                "userId" to uid,
                "mode" to session.mode.name,
                "durationMinutes" to ((session.actualDurationMillis / (1000 * 60)).toInt()),
                "completedAt" to Timestamp(Date(session.endTime)),
                "createdAt" to Timestamp(Date(session.createdAt))
            )
            try {
                sessionDoc.set(sessionPayload, SetOptions.merge()).await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, sessionDoc.path)
            }
        }

        // 3. Pull Remote Sessions
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
                    val completedAt = doc.getTimestamp("completedAt")?.toDate()?.time ?: System.currentTimeMillis()
                    val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: completedAt

                    val restoredSession = SessionRecordEntity(
                        id = id,
                        mode = mode,
                        title = "Focus Session",
                        subject = "Study",
                        topic = "General",
                        goal = "Deep Focus",
                        plannedDurationMillis = durMillis,
                        actualDurationMillis = durMillis,
                        startTime = completedAt - durMillis,
                        endTime = completedAt,
                        completed = true,
                        cancelled = false,
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

        // 4. Push Local Study Plans
        val localPlans = database.studyPlanDao().getAllPlans()
        for (plan in localPlans) {
            val planDoc = userDoc.collection("study_plans").document(plan.id)
            val planPayload = hashMapOf<String, Any>(
                "id" to plan.id,
                "userId" to uid,
                "title" to plan.subjectName,
                "targetSubject" to plan.topicName,
                "durationMinutes" to plan.plannedDurationMinutes,
                "activeDays" to listOf("Mon", "Tue", "Wed", "Thu", "Fri"),
                "createdAt" to Timestamp(Date(plan.createdAt)),
                "updatedAt" to Timestamp(Date(plan.createdAt))
            )
            try {
                planDoc.set(planPayload, SetOptions.merge()).await()
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, planDoc.path)
            }
        }

        // 5. Pull Remote Study Plans
        try {
            val remotePlansSnap = userDoc.collection("study_plans").get().await()
            for (doc in remotePlansSnap.documents) {
                val id = doc.getString("id") ?: doc.id
                val existing = database.studyPlanDao().getPlanById(id)
                if (existing == null) {
                    val title = doc.getString("title") ?: "Study Plan"
                    val subject = doc.getString("targetSubject") ?: ""
                    val duration = (doc.getLong("durationMinutes") ?: 30L).toInt()
                    val createdAt = doc.getTimestamp("createdAt")?.toDate()?.time ?: System.currentTimeMillis()

                    val restoredPlan = StudyPlanEntity(
                        id = id,
                        subjectId = "sub_$id",
                        subjectName = title,
                        topicName = subject,
                        plannedDurationMinutes = duration,
                        targetDate = createdAt,
                        isCompleted = false,
                        colorHex = "#7C3AED",
                        createdAt = createdAt,
                        dateString = "",
                        startTime = "08:00",
                        endTime = "09:00",
                        startMinutes = 480,
                        endMinutes = 540,
                        status = "PLANNED",
                        notes = ""
                    )
                    database.studyPlanDao().insertPlan(restoredPlan)
                }
            }
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, "${userDoc.path}/study_plans")
        }

        // Clear local outbox as data is synchronized
        try {
            database.syncOutboxDao().clearAll()
            // The periodic auto-save loop gates on this count, so it has to learn that the
            // outbox is empty here — otherwise the next tick would fire a redundant full push.
            SyncTracker.markOutboxDrained()
        } catch (_: Exception) {}

        installState.setDataOwnerAccountId(uid)
        installState.markMigrated(uid)
        installState.rememberAccount(uid)
    }
}
