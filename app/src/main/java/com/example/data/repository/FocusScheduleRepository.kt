package com.example.data.repository

import android.content.Context
import com.example.data.local.dao.FocusScheduleDao
import com.example.data.local.entity.FocusScheduleEntity
import com.example.feature.session.notification.FocusScheduleAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class FocusScheduleRepository(
    private val dao: FocusScheduleDao,
    private val context: Context? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    val allSchedules: Flow<List<FocusScheduleEntity>> = dao.getAllSchedulesFlow()

    suspend fun initializeDefaultSchedulesIfEmpty() {
        if (dao.getScheduleCount() == 0) {
            val defaultSchedules = listOf(
                FocusScheduleEntity(
                    title = "Morning Deep Work",
                    daysOfWeek = "MON,TUE,WED,THU,FRI",
                    startTime = "09:00",
                    endTime = "11:30",
                    isEnabled = true,
                    isAutoStartSession = true,
                    mode = "TIMER",
                    subjectName = "Mathematics & Physics",
                    colorHex = "#4F46E5"
                ),
                FocusScheduleEntity(
                    title = "Afternoon Focus Window",
                    daysOfWeek = "MON,TUE,WED,THU,FRI",
                    startTime = "14:00",
                    endTime = "16:00",
                    isEnabled = true,
                    isAutoStartSession = true,
                    mode = "POMODORO",
                    subjectName = "General Study",
                    colorHex = "#059669"
                )
            )
            for (sch in defaultSchedules) {
                dao.insertSchedule(sch)
            }
            context?.let { ctx ->
                val enabled = dao.getEnabledSchedules()
                FocusScheduleAlarmScheduler.rescheduleAllSchedules(ctx, enabled)
            }
        }
    }

    suspend fun addSchedule(schedule: FocusScheduleEntity) {
        dao.insertSchedule(schedule)
        context?.let { ctx ->
            FocusScheduleAlarmScheduler.scheduleSingleFocusSchedule(ctx, schedule)
        }
    }

    suspend fun updateSchedule(schedule: FocusScheduleEntity) {
        dao.updateSchedule(schedule)
        context?.let { ctx ->
            FocusScheduleAlarmScheduler.scheduleSingleFocusSchedule(ctx, schedule)
        }
    }

    suspend fun toggleScheduleEnabled(id: String, isEnabled: Boolean) {
        dao.setScheduleEnabled(id, isEnabled)
        val updated = dao.getScheduleById(id)
        context?.let { ctx ->
            if (updated != null) {
                if (isEnabled) {
                    FocusScheduleAlarmScheduler.scheduleSingleFocusSchedule(ctx, updated)
                } else {
                    FocusScheduleAlarmScheduler.cancelFocusSchedule(ctx, id)
                }
            }
        }
    }

    suspend fun deleteSchedule(id: String) {
        dao.deleteScheduleById(id)
        context?.let { ctx ->
            FocusScheduleAlarmScheduler.cancelFocusSchedule(ctx, id)
        }
    }

    fun syncAllAlarms(context: Context) {
        coroutineScope.launch {
            val enabled = dao.getEnabledSchedules()
            FocusScheduleAlarmScheduler.rescheduleAllSchedules(context, enabled)
        }
    }
}
