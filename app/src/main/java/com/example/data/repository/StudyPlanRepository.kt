package com.example.data.repository

import com.example.data.local.dao.StudyPlanDao
import com.example.data.local.entity.StudyPlanEntity
import com.example.feature.session.notification.StudyPlanAlarmScheduler
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
        val startMins = parseTimeToMinutes(startTime)
        val endMins = parseTimeToMinutes(endTime)
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
        
        try {
            val context = com.example.FocusShieldApp.instance
            StudyPlanAlarmScheduler.scheduleSinglePlanReminder(context, plan)
        } catch (e: Exception) {
            // Safe fallback
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
        val startMins = parseTimeToMinutes(startTime)
        val endMins = parseTimeToMinutes(endTime)
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

        try {
            val context = com.example.FocusShieldApp.instance
            if (isCompleted) {
                StudyPlanAlarmScheduler.cancelPlanReminder(context, id)
            } else {
                StudyPlanAlarmScheduler.scheduleSinglePlanReminder(context, plan)
            }
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    suspend fun togglePlanCompletion(id: String, isCompleted: Boolean) {
        studyPlanDao.updatePlanCompletion(id, isCompleted)
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
        } catch (e: Exception) {
            // Safe fallback
        }
    }

    suspend fun deletePlan(id: String) {
        studyPlanDao.deletePlanById(id)
        try {
            val context = com.example.FocusShieldApp.instance
            StudyPlanAlarmScheduler.cancelPlanReminder(context, id)
        } catch (e: Exception) {
            // Safe fallback
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
                    subjectId = "chemistry",
                    subjectName = "Chemistry",
                    topicName = "Chemical Bonding",
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
                    notes = "VSEPR theory and hybridization"
                ),
                StudyPlanEntity(
                    id = "default-plan-3",
                    subjectId = "mathematics",
                    subjectName = "Mathematics",
                    topicName = "Calculus",
                    plannedDurationMinutes = 120,
                    targetDate = now,
                    dateString = dateStr,
                    startTime = "16:00",
                    endTime = "18:00",
                    startMinutes = 960,
                    endMinutes = 1080,
                    isCompleted = false,
                    status = "PLANNED",
                    colorHex = "#22C55E",
                    notes = "Definite integrals and area under curve"
                ),
                StudyPlanEntity(
                    id = "default-plan-4",
                    subjectId = "physics",
                    subjectName = "Physics",
                    topicName = "Current Electricity",
                    plannedDurationMinutes = 60,
                    targetDate = now,
                    dateString = dateStr,
                    startTime = "20:00",
                    endTime = "21:00",
                    startMinutes = 1200,
                    endMinutes = 1260,
                    isCompleted = false,
                    status = "PLANNED",
                    colorHex = "#7C3AED",
                    notes = "Kirchhoff's laws revision"
                )
            )
            studyPlanDao.insertAll(defaultPlans)
            
            try {
                val context = com.example.FocusShieldApp.instance
                StudyPlanAlarmScheduler.schedulePlanReminders(context, defaultPlans)
            } catch (e: Exception) {
                // Safe fallback
            }
        }
    }

    companion object {
        fun parseTimeToMinutes(timeString: String): Int {
            return try {
                val parts = timeString.trim().split(":")
                val hour = parts[0].toInt()
                val min = if (parts.size > 1) parts[1].toInt() else 0
                hour * 60 + min
            } catch (e: Exception) {
                480 // 08:00 fallback
            }
        }

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

