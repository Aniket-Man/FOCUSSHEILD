package com.example.data.repository

import android.util.Log
import com.example.cloud.sync.CloudJson
import com.example.cloud.sync.SyncTables
import com.example.cloud.sync.SyncTracker
import com.example.data.local.dao.StudyPlanDao
import com.example.data.local.entity.StudyPlanEntity
import com.example.feature.session.notification.StudyPlanAlarmScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class StudyPlanRepository(
    private val studyPlanDao: StudyPlanDao
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private companion object {
        const val TAG = "StudyPlanRepository"
    }

    val allPlans: Flow<List<StudyPlanEntity>> = studyPlanDao.getAllPlansFlow()

    fun getTodayPlansFlow(): Flow<List<StudyPlanEntity>> {
        val (startOfDay, endOfDay) = getTodayBounds()
        return studyPlanDao.getPlansForDateFlow(startOfDay, endOfDay)
    }

    suspend fun getTodayPlans(): List<StudyPlanEntity> {
        val (startOfDay, endOfDay) = getTodayBounds()
        return studyPlanDao.getPlansForDate(startOfDay, endOfDay)
    }

    fun getPlansForDateFlow(dateMillis: Long): Flow<List<StudyPlanEntity>> {
        val (startOfDay, endOfDay) = getBoundsForDate(dateMillis)
        return studyPlanDao.getPlansForDateFlow(startOfDay, endOfDay)
    }

    suspend fun getPlanById(id: String): StudyPlanEntity? {
        return studyPlanDao.getPlanById(id)
    }

    suspend fun addPlan(
        subjectId: String,
        subjectName: String,
        topicName: String,
        startTime: String = "08:00",
        endTime: String = "10:00",
        targetDate: Long = System.currentTimeMillis(),
        colorHex: String = getColorForSubject(subjectName),
        notes: String = ""
    ): StudyPlanEntity {
        val startMins = requireStoredMinutes(startTime)
        val endMins = requireStoredMinutes(endTime)
        val durationMins = calculateDurationMinutes(startMins, endMins)
        val dateStr = dateFormat.format(Date(targetDate))

        val plan = StudyPlanEntity(
            id = UUID.randomUUID().toString(),
            subjectId = subjectId,
            subjectName = subjectName,
            topicName = topicName,
            plannedDurationMinutes = durationMins,
            targetDate = targetDate,
            dateString = dateStr,
            startTime = startTime,
            endTime = endTime,
            startMinutes = startMins,
            endMinutes = endMins,
            isCompleted = false,
            status = "PLANNED",
            colorHex = colorHex,
            notes = notes,
            createdAt = System.currentTimeMillis()
        )
        studyPlanDao.insertPlan(plan)
        SyncTracker.enqueueUpsert(
            SyncTables.STUDY_PLANS,
            plan.id,
            CloudJson.studyPlanToJson(plan).toString()
        )

        try {
            val context = com.example.FocusShieldApp.instance
            StudyPlanAlarmScheduler.scheduleSinglePlanReminder(context, plan)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // Arming a reminder is a side effect of a successful write, so a failure here must not
            // fail the write — but it used to be swallowed with no trace at all, which made a plan
            // that never fired completely undiagnosable.
            Log.w(TAG, "Could not arm a study-plan reminder for plan ${plan.id}", e)
        }

        return plan
    }

    suspend fun updatePlan(
        id: String,
        subjectId: String,
        subjectName: String,
        topicName: String,
        startTime: String,
        endTime: String,
        targetDate: Long,
        colorHex: String = getColorForSubject(subjectName),
        notes: String = "",
        isCompleted: Boolean = false
    ) {
        val startMins = requireStoredMinutes(startTime)
        val endMins = requireStoredMinutes(endTime)
        val durationMins = calculateDurationMinutes(startMins, endMins)
        val dateStr = dateFormat.format(Date(targetDate))

        val existing = studyPlanDao.getPlanById(id)
        val plan = StudyPlanEntity(
            id = id,
            subjectId = subjectId,
            subjectName = subjectName,
            topicName = topicName,
            plannedDurationMinutes = durationMins,
            targetDate = targetDate,
            dateString = dateStr,
            startTime = startTime,
            endTime = endTime,
            startMinutes = startMins,
            endMinutes = endMins,
            isCompleted = isCompleted,
            status = if (isCompleted) "COMPLETED" else (existing?.status ?: "PLANNED"),
            colorHex = colorHex,
            notes = notes,
            createdAt = existing?.createdAt ?: System.currentTimeMillis()
        )
        studyPlanDao.updatePlan(plan)
        SyncTracker.enqueueUpsert(
            SyncTables.STUDY_PLANS,
            plan.id,
            CloudJson.studyPlanToJson(plan).toString()
        )

        try {
            val context = com.example.FocusShieldApp.instance
            if (isCompleted) {
                StudyPlanAlarmScheduler.cancelPlanReminder(context, id)
            } else {
                StudyPlanAlarmScheduler.scheduleSinglePlanReminder(context, plan)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // Arming a reminder is a side effect of a successful write, so a failure here must not
            // fail the write — but it used to be swallowed with no trace at all, which made a plan
            // that never fired completely undiagnosable. Logged with the plan id so it can be traced.
            Log.w(TAG, "Could not update the study-plan alarm for plan $id", e)
        }
    }

    suspend fun togglePlanCompletion(id: String, isCompleted: Boolean) {
        studyPlanDao.updatePlanCompletion(id, isCompleted)
        // Read back the full row so the reconcile pull never reverts an unpushed toggle.
        val updated = studyPlanDao.getPlanById(id)
        if (updated != null) {
            SyncTracker.enqueueUpsert(
                SyncTables.STUDY_PLANS,
                updated.id,
                CloudJson.studyPlanToJson(updated).toString()
            )
        }
        try {
            val context = com.example.FocusShieldApp.instance
            if (isCompleted) {
                StudyPlanAlarmScheduler.cancelPlanReminder(context, id)
            } else {
                val plan = studyPlanDao.getPlanById(id)
                if (plan != null) {
                    StudyPlanAlarmScheduler.scheduleSinglePlanReminder(context, plan)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // Arming a reminder is a side effect of a successful write, so a failure here must not
            // fail the write — but it used to be swallowed with no trace at all, which made a plan
            // that never fired completely undiagnosable. Logged with the plan id so it can be traced.
            Log.w(TAG, "Could not update the study-plan alarm for plan $id", e)
        }
    }

    suspend fun deletePlan(id: String) {
        studyPlanDao.deletePlanById(id)
        SyncTracker.enqueueDelete(SyncTables.STUDY_PLANS, id)
        try {
            val context = com.example.FocusShieldApp.instance
            StudyPlanAlarmScheduler.cancelPlanReminder(context, id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            // Arming a reminder is a side effect of a successful write, so a failure here must not
            // fail the write — but it used to be swallowed with no trace at all, which made a plan
            // that never fired completely undiagnosable. Logged with the plan id so it can be traced.
            Log.w(TAG, "Could not update the study-plan alarm for plan $id", e)
        }
    }

    /**
     * Ensures that today has active study plans.
     * When a new day begins, if today has no scheduled items yet, this generates today's clean plan
     * (resetting isCompleted to false) based on recurring templates or previous plans,
     * maintaining a fresh daily schedule while preserving past session history in the database.
     */
    suspend fun ensureTodayPlansExist(): List<StudyPlanEntity> {
        val (todayStart, todayEnd) = getTodayBounds()
        val todayPlans = studyPlanDao.getPlansForDate(todayStart, todayEnd)
        if (todayPlans.isNotEmpty()) {
            return todayPlans
        }

        val allPlans = studyPlanDao.getAllPlans()
        val now = System.currentTimeMillis()
        val dateStr = dateFormat.format(Date(now))

        if (allPlans.isNotEmpty()) {
            // Group by distinct subject + start time to avoid duplicates
            val distinctTemplates = allPlans.distinctBy { "${it.subjectName}_${it.startTime}_${it.topicName}" }
            val newTodayPlans = distinctTemplates.map { template ->
                StudyPlanEntity(
                    id = UUID.randomUUID().toString(),
                    subjectId = template.subjectId,
                    subjectName = template.subjectName,
                    topicName = template.topicName,
                    plannedDurationMinutes = template.plannedDurationMinutes,
                    targetDate = now,
                    dateString = dateStr,
                    startTime = template.startTime,
                    endTime = template.endTime,
                    startMinutes = template.startMinutes,
                    endMinutes = template.endMinutes,
                    isCompleted = false,
                    status = "PLANNED",
                    colorHex = template.colorHex,
                    notes = template.notes,
                    createdAt = now
                )
            }
            studyPlanDao.insertAll(newTodayPlans)
            newTodayPlans.forEach { plan ->
                SyncTracker.enqueueUpsert(
                    SyncTables.STUDY_PLANS,
                    plan.id,
                    CloudJson.studyPlanToJson(plan).toString()
                )
            }
            return newTodayPlans
        } else {
            initializeDefaultPlansIfEmpty()
            return studyPlanDao.getPlansForDate(todayStart, todayEnd)
        }
    }

    suspend fun initializeDefaultPlansIfEmpty() {
        if (studyPlanDao.getPlanCount() == 0) {
            val now = System.currentTimeMillis()
            val dateStr = dateFormat.format(Date(now))
            val defaultPlans = listOf(
                StudyPlanEntity(
                    id = "default-plan-1",
                    subjectId = "physics",
                    subjectName = "Physics",
                    topicName = "Electrostatics",
                    plannedDurationMinutes = 120,
                    targetDate = now,
                    dateString = dateStr,
                    startTime = "08:00",
                    endTime = "10:00",
                    startMinutes = 480,
                    endMinutes = 600,
                    isCompleted = false,
                    status = "PLANNED",
                    colorHex = "#7C3AED",
                    notes = "Solve 20 JEE Advanced problems"
                ),
                StudyPlanEntity(
                    id = "default-plan-2",
                    subjectId = "mathematics",
                    subjectName = "Mathematics",
                    topicName = "Differential Equations",
                    plannedDurationMinutes = 90,
                    targetDate = now,
                    dateString = dateStr,
                    startTime = "11:00",
                    endTime = "12:30",
                    startMinutes = 660,
                    endMinutes = 750,
                    isCompleted = false,
                    status = "PLANNED",
                    colorHex = "#F59E0B",
                    notes = "First-order differential equations and integrating factors"
                ),
                StudyPlanEntity(
                    id = "default-plan-3",
                    subjectId = "chemistry",
                    subjectName = "Chemistry",
                    topicName = "Chemical Bonding",
                    plannedDurationMinutes = 180,
                    targetDate = now,
                    dateString = dateStr,
                    startTime = "16:00",
                    endTime = "19:00",
                    startMinutes = 960,
                    endMinutes = 1140,
                    isCompleted = false,
                    status = "PLANNED",
                    colorHex = "#10B981",
                    notes = "VSEPR theory and hybridization"
                )
            )
            studyPlanDao.insertAll(defaultPlans)
            defaultPlans.forEach { plan ->
                SyncTracker.enqueueUpsert(
                    SyncTables.STUDY_PLANS,
                    plan.id,
                    CloudJson.studyPlanToJson(plan).toString()
                )
            }

            try {
                val context = com.example.FocusShieldApp.instance
                StudyPlanAlarmScheduler.schedulePlanReminders(context, defaultPlans)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                // Seeding is best-effort (the plans are already stored); logged, not swallowed.
                Log.w(TAG, "Could not arm reminders for the default study plans", e)
            }
        }
    }

    companion object {
        /**
         * Minutes-of-day for a stored `HH:mm` value.
         *
         * Strictly parsed by [com.example.feature.session.notification.ScheduleTime] — "9:5", "24:00"
         * and "half past eight" are all *invalid*, not coerced. When a value is unparsable this returns
         * [UNKNOWN_TIME_MINUTES] so callers that must have a number (duration display, overlap checks in
         * the plan dialogs) keep rendering something sensible.
         *
         * Scheduling code must **not** use this function: [UNKNOWN_TIME_MINUTES] is a display
         * placeholder, and using it to arm an alarm would remind the student at 08:00 for a plan whose
         * time is unknown. Alarm scheduling goes through
         * [com.example.feature.session.notification.ScheduleTime.parseToMinutesOrNull] and skips
         * unusable plans instead (see `StudyPlanAlarmScheduler`).
         */
        fun parseTimeToMinutes(timeString: String): Int =
            parseTimeToMinutesOrNull(timeString) ?: UNKNOWN_TIME_MINUTES

        /**
         * Minutes-of-day for a value that is about to be **stored**.
         *
         * A write must not fabricate a clock time, so an unparsable value is logged with its raw
         * text and stored as [UNKNOWN_TIME_MINUTES] rather than being quietly presented as 08:00.
         * The UI validates before it gets here (`HomeScreen`'s plan dialog refuses the save), so a
         * warning here means the value arrived from somewhere the user cannot see — a synced row or
         * an older build — and it is exactly the case worth being able to find in logcat.
         */
        private fun requireStoredMinutes(timeString: String): Int {
            val parsed = parseTimeToMinutesOrNull(timeString)
            if (parsed == null) {
                Log.w(TAG, "Storing plan time '$timeString' as unknown: not a valid HH:mm value")
                return UNKNOWN_TIME_MINUTES
            }
            return parsed
        }

        /** Minutes-of-day for a stored `HH:mm` value, or null when it is not a valid clock time. */
        fun parseTimeToMinutesOrNull(timeString: String): Int? =
            com.example.feature.session.notification.ScheduleTime.parseToMinutesOrNull(timeString)

        /** 08:00 — deliberately only a display placeholder; never used to arm an alarm. */
        const val UNKNOWN_TIME_MINUTES: Int = 8 * 60

        fun formatMinutesToTime(totalMinutes: Int): String {
            val h = (totalMinutes / 60) % 24
            val m = totalMinutes % 60
            return String.format(Locale.getDefault(), "%02d:%02d", h, m)
        }

        fun calculateDurationMinutes(startMinutes: Int, endMinutes: Int): Int {
            return if (endMinutes >= startMinutes) {
                endMinutes - startMinutes
            } else {
                (24 * 60 - startMinutes) + endMinutes
            }.coerceAtLeast(5)
        }

        fun formatDurationHoursMins(minutes: Int): String {
            val h = minutes / 60
            val m = minutes % 60
            return when {
                h > 0 && m > 0 -> "${h}h ${m}m"
                h > 0 -> "${h}h"
                m > 0 -> "${m}m"
                else -> "0m"
            }
        }

        fun getColorForSubject(subjectName: String): String {
            return when (subjectName.trim().lowercase()) {
                "physics" -> "#7C3AED" // Purple
                "chemistry" -> "#F59E0B" // Amber
                "mathematics", "maths", "math" -> "#22C55E" // Emerald Green
                "biology", "botany", "zoology" -> "#06B6D4" // Cyan
                else -> "#6366F1" // Indigo
            }
        }

        fun detectOverlaps(plans: List<StudyPlanEntity>): List<Pair<StudyPlanEntity, StudyPlanEntity>> {
            val sorted = plans.sortedBy { it.startMinutes }
            val overlaps = mutableListOf<Pair<StudyPlanEntity, StudyPlanEntity>>()
            for (i in 0 until sorted.size) {
                for (j in i + 1 until sorted.size) {
                    val p1 = sorted[i]
                    val p2 = sorted[j]
                    // Overlap condition: start of p2 is before end of p1
                    if (p2.startMinutes < p1.endMinutes) {
                        overlaps.add(Pair(p1, p2))
                    }
                }
            }
            return overlaps
        }
    }

    private fun getTodayBounds(): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis
        return Pair(startOfDay, endOfDay)
    }

    private fun getBoundsForDate(dateMillis: Long): Pair<Long, Long> {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.set(Calendar.HOUR_OF_DAY, 23)
        calendar.set(Calendar.MINUTE, 59)
        calendar.set(Calendar.SECOND, 59)
        calendar.set(Calendar.MILLISECOND, 999)
        val endOfDay = calendar.timeInMillis
        return Pair(startOfDay, endOfDay)
    }
}

