package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.cloud.sync.CloudJson
import com.example.cloud.sync.SyncTables
import com.example.cloud.sync.SyncTracker
import com.example.data.local.dao.FocusScheduleDao
import com.example.data.local.entity.FocusScheduleEntity
import com.example.feature.session.notification.FocusScheduleAlarmScheduler
import com.example.feature.session.notification.ScheduleTime
import com.example.feature.session.notification.ScheduleValidation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * Room-first store for automated focus schedules, plus the alarm side-effects that must follow every
 * change to one.
 *
 * Invariants maintained here (each used to be a way for a schedule to silently misbehave):
 *  - **A schedule is validated before it is stored.** [validate] rejects an unusable start/end clock
 *    time, an empty or unrecognised day list, and a zero-length window (start == end, which the time
 *    picker allows and which the session starter previously turned into a *24-hour* session). The
 *    saved row is normalised (days canonicalised through [ScheduleTime.formatDaysOfWeek]) so the same
 *    semantic day set always has one stored spelling.
 *  - **Every mutation re-plans the alarm.** Add/update/toggle/delete all end by calling the scheduler,
 *    which cancels the previous alarm first — so disabling, deleting or editing into an invalid state
 *    can never leave a stale trigger armed (see [FocusScheduleAlarmScheduler]).
 *  - **A rejected write changes nothing**: no Room row, no outbox op, no alarm. Callers get the reason
 *    and surface it; nothing is silently dropped or defaulted.
 */
class FocusScheduleRepository(
    private val dao: FocusScheduleDao,
    private val context: Context? = null,
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    val allSchedules: Flow<List<FocusScheduleEntity>> = dao.getAllSchedulesFlow()

    /** Outcome of a write attempt. */
    sealed interface SaveResult {
        /** Stored (possibly normalised). [schedule] reflects exactly what was persisted. */
        data class Saved(val schedule: FocusScheduleEntity) : SaveResult

        /** Not stored; [reason] is user-presentable ("End time must be after the start time"). */
        data class Rejected(val reason: String) : SaveResult
    }

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
                SyncTracker.enqueueUpsert(
                    SyncTables.FOCUS_SCHEDULES,
                    sch.id,
                    CloudJson.focusScheduleToJson(sch).toString()
                )
            }
            context?.let { ctx ->
                val enabled = dao.getEnabledSchedules()
                FocusScheduleAlarmScheduler.rescheduleAllSchedules(ctx, enabled)
            }
        }
    }

    suspend fun addSchedule(schedule: FocusScheduleEntity): SaveResult {
        val normalized = validateAndNormalize(schedule)
            ?: return reject("addSchedule", schedule)

        dao.insertSchedule(normalized)
        SyncTracker.enqueueUpsert(
            SyncTables.FOCUS_SCHEDULES,
            normalized.id,
            CloudJson.focusScheduleToJson(normalized).toString()
        )
        // Invalid schedules scheduled nothing (and cancelled anything previously armed for this id),
        // so the alarm state always matches the stored row.
        context?.let { ctx ->
            if (normalized.isEnabled) {
                FocusScheduleAlarmScheduler.scheduleSingleFocusSchedule(ctx, normalized)
            } else {
                FocusScheduleAlarmScheduler.cancelFocusSchedule(ctx, normalized.id)
            }
        }
        return SaveResult.Saved(normalized)
    }

    suspend fun updateSchedule(schedule: FocusScheduleEntity): SaveResult {
        val normalized = validateAndNormalize(schedule)
            ?: return reject("updateSchedule", schedule)

        dao.updateSchedule(normalized)
        SyncTracker.enqueueUpsert(
            SyncTables.FOCUS_SCHEDULES,
            normalized.id,
            CloudJson.focusScheduleToJson(normalized).toString()
        )
        context?.let { ctx ->
            if (normalized.isEnabled) {
                FocusScheduleAlarmScheduler.scheduleSingleFocusSchedule(ctx, normalized)
            } else {
                FocusScheduleAlarmScheduler.cancelFocusSchedule(ctx, normalized.id)
            }
        }
        return SaveResult.Saved(normalized)
    }

    suspend fun toggleScheduleEnabled(id: String, isEnabled: Boolean) {
        dao.setScheduleEnabled(id, isEnabled)
        val updated = dao.getScheduleById(id)
        if (updated != null) {
            SyncTracker.enqueueUpsert(
                SyncTables.FOCUS_SCHEDULES,
                updated.id,
                CloudJson.focusScheduleToJson(updated).toString()
            )
        }
        context?.let { ctx ->
            if (updated != null) {
                if (isEnabled) {
                    // Re-planning from scratch: if the stored row cannot be parsed, the scheduler
                    // cancels and logs instead of arming a guessed time.
                    FocusScheduleAlarmScheduler.scheduleSingleFocusSchedule(ctx, updated)
                } else {
                    FocusScheduleAlarmScheduler.cancelFocusSchedule(ctx, id)
                }
            }
        }
    }

    suspend fun deleteSchedule(id: String) {
        dao.deleteScheduleById(id)
        SyncTracker.enqueueDelete(SyncTables.FOCUS_SCHEDULES, id)
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

    // ---- validation -------------------------------------------------------------------

    /**
     * Normalises a schedule for storage (trimmed, canonical day spelling), or null when it is
     * invalid. The rule set itself lives in [ScheduleValidation], so the dialog, the repository and
     * the tests all apply exactly the same rules.
     */
    private fun validateAndNormalize(schedule: FocusScheduleEntity): FocusScheduleEntity? =
        ScheduleValidation.normalize(schedule)

    /** Null when [schedule] can be stored, otherwise the user-presentable reason it cannot. */
    fun validate(schedule: FocusScheduleEntity): String? = ScheduleValidation.validate(schedule)

    private suspend fun reject(operation: String, schedule: FocusScheduleEntity): SaveResult {
        val reason = validate(schedule) ?: "invalid schedule"
        // Logged at warning level: a rejected write means the UI and the store disagree, which is
        // exactly the situation that must not disappear quietly.
        Log.w(
            TAG,
            "$operation rejected schedule '${schedule.title}' (${schedule.id}): $reason"
        )
        return SaveResult.Rejected(reason)
    }

    private companion object {
        const val TAG = "FocusScheduleRepository"
    }
}
