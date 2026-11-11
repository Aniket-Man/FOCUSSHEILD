package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.cloud.sync.CloudJson
import com.example.cloud.sync.SyncTables
import com.example.cloud.sync.SyncTracker
import com.example.data.local.dao.FocusScheduleDao
import com.example.data.local.entity.FocusScheduleEntity
import com.example.feature.session.notification.FocusScheduleAlarmScheduler
import com.example.feature.session.notification.ScheduleTime
import com.example.feature.session.notification.ScheduleValidation
import kotlinx.coroutines.flow.Flow

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
    private val context: Context? = null
) {
    val allSchedules: Flow<List<FocusScheduleEntity>> = dao.getAllSchedulesFlow()

    /** Outcome of a write attempt. */
    sealed interface SaveResult {
        /** Stored (possibly normalised). [schedule] reflects exactly what was persisted. */
        data class Saved(
            val schedule: FocusScheduleEntity,
            val defaultsFailure: Exception? = null,
            val followUpFailure: Exception? = null
        ) : SaveResult

        /** Not stored; [reason] is user-presentable ("End time must be after the start time"). */
        data class Rejected(val reason: String) : SaveResult
    }

    suspend fun clearPreloadedDefaultSchedules() {
        val all = try {
            dao.getAllSchedules()
        } catch (_: Exception) {
            emptyList()
        }
        for (sch in all) {
            val isPreloadedDefault =
                (sch.title == "Morning Deep Work" && sch.subjectName == "Mathematics & Physics" && sch.startTime == "09:00" && sch.endTime == "11:30") ||
                (sch.title == "Afternoon Focus Window" && sch.subjectName == "General Study" && sch.startTime == "14:00" && sch.endTime == "16:00")
            if (isPreloadedDefault) {
                dao.deleteScheduleById(sch.id)
                SyncTracker.enqueueDelete(SyncTables.FOCUS_SCHEDULES, sch.id)
                context?.let { ctx ->
                    FocusScheduleAlarmScheduler.cancelFocusSchedule(ctx, sch.id)
                }
            }
        }
    }

    suspend fun initializeDefaultSchedulesIfEmpty() {
        clearPreloadedDefaultSchedules()
    }

    suspend fun addSchedule(schedule: FocusScheduleEntity, saveDefaults: suspend () -> Unit = {}): SaveResult =
        save(schedule, updating = false, saveDefaults = saveDefaults)

    suspend fun updateSchedule(schedule: FocusScheduleEntity, saveDefaults: suspend () -> Unit = {}): SaveResult =
        save(schedule, updating = true, saveDefaults = saveDefaults)

    private suspend fun save(schedule: FocusScheduleEntity, updating: Boolean, saveDefaults: suspend () -> Unit): SaveResult {
        val result = ScheduleMutationGate.run {
            val operation = if (updating) "updateSchedule" else "addSchedule"
            val existing = dao.getScheduleById(schedule.id)
            if (updating && existing == null) {
                return@run SaveResult.Rejected(context?.getString(R.string.planner_missing_schedule_error)
                    ?: "This schedule was removed. Close this editor and add a new schedule.")
            }
            if (existing != null && ScheduleValidation.isCurrentlyActiveWindow(existing)) {
                val reason = context?.getString(R.string.planner_active_edit_error, existing.title)
                    ?: "\"${existing.title}\" is running. Wait until its focus window ends, then edit it."
                Log.w(TAG, "$operation rejected '${existing.title}': window is currently active")
                return@run SaveResult.Rejected(reason)
            }
            // A restored new-editor ID updates its one saved row; never silently ignore new input.
            val normalized = validateAndNormalize(schedule)?.let { value ->
                if (existing != null) value.copy(createdAt = existing.createdAt) else value
            } ?: return@run reject(operation, schedule)
            rejectOverlap(operation, normalized)?.let { return@run it }
            val completion = ScheduleWriteCompletion.run(
                persistSchedule = { if (updating || existing != null) dao.updateSchedule(normalized) else dao.insertSchedule(normalized) },
                saveDefaults = saveDefaults,
                armSchedule = {
                    context?.let { ctx ->
                        if (normalized.isEnabled) {
                            com.example.feature.session.notification.FocusScheduleReceiver.clearFiredSlot(ctx, normalized.id)
                            when (val scheduled = FocusScheduleAlarmScheduler.scheduleSingleFocusSchedule(ctx, normalized)) {
                                is FocusScheduleAlarmScheduler.SchedulingResult.Failed ->
                                    throw IllegalStateException("Alarm could not be armed: ${scheduled.detail}")
                                is FocusScheduleAlarmScheduler.SchedulingResult.Skipped ->
                                    if (scheduled.reason != com.example.feature.session.notification.ScheduleAlarmPlanner.Reason.DISABLED) {
                                        throw IllegalStateException("Alarm could not be armed: ${scheduled.detail}")
                                    }
                                is FocusScheduleAlarmScheduler.SchedulingResult.Armed -> Unit
                            }
                        } else FocusScheduleAlarmScheduler.cancelFocusSchedule(ctx, normalized.id)
                    }
                },
                trackChange = { SyncTracker.enqueueUpsert(SyncTables.FOCUS_SCHEDULES, normalized.id,
                    CloudJson.focusScheduleToJson(normalized).toString()) }
            )
            completion.defaultsFailure?.let { Log.e(TAG, "Schedule saved but protection defaults failed", it) }
            completion.activationFailure?.let { Log.e(TAG, "Schedule saved but alarm setup failed", it) }
            completion.trackingFailure?.let { Log.e(TAG, "Schedule saved but backup tracking failed", it) }
            SaveResult.Saved(normalized, completion.defaultsFailure, completion.activationFailure ?: completion.trackingFailure)
        }
        // Never evaluate while holding the gate: the receiver re-enters it and re-reads the row.
        if (result !is SaveResult.Saved) return result
        // The row has already committed. A watchdog failure is partial success, not a failed save.
        return try {
            evaluateDueSchedules()
            result
        } catch (exception: Exception) {
            Log.e(TAG, "Schedule saved but immediate evaluation failed", exception)
            result.copy(followUpFailure = result.followUpFailure ?: exception)
        }
    }

    private suspend fun evaluateDueSchedules() {
        context?.let { ctx -> (ctx.applicationContext as? com.example.FocusShieldApp)?.let { app ->
            com.example.feature.session.notification.FocusScheduleReceiver.evaluateDueSchedulesInProcess(app, ctx)
        } }
    }

    suspend fun toggleScheduleEnabled(id: String, isEnabled: Boolean): Boolean {
        val applied = ScheduleMutationGate.run {
        val existing = dao.getScheduleById(id)
        if (existing == null) return@run false
        if (!isEnabled && existing != null && ScheduleValidation.isCurrentlyActiveWindow(existing)) {
            Log.w(TAG, "Refused to disable schedule '${existing.title}' ($id): window is currently active")
            return@run false
        }
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
                    com.example.feature.session.notification.FocusScheduleReceiver.clearFiredSlot(ctx, updated.id)
                    FocusScheduleAlarmScheduler.scheduleSingleFocusSchedule(ctx, updated)
                } else {
                    FocusScheduleAlarmScheduler.cancelFocusSchedule(ctx, id)
                }
            }
        }
        true
        }
        if (applied && isEnabled) {
            try {
                evaluateDueSchedules()
            } catch (exception: Exception) {
                // The enable flag and its alarm are already committed. A watchdog failure must not
                // turn that successful write into a misleading "could not change schedule" error.
                Log.e(TAG, "Schedule enabled but immediate evaluation failed", exception)
            }
        }
        return applied
    }

    suspend fun deleteSchedule(id: String): Boolean = ScheduleMutationGate.run {
        val existing = dao.getScheduleById(id)
        if (existing != null && ScheduleValidation.isCurrentlyActiveWindow(existing)) {
            Log.w(TAG, "Refused to delete schedule '${existing.title}' ($id): window is currently active")
            return@run false
        }
        dao.deleteScheduleById(id)
        SyncTracker.enqueueDelete(SyncTables.FOCUS_SCHEDULES, id)
        context?.let { ctx ->
            FocusScheduleAlarmScheduler.cancelFocusSchedule(ctx, id)
        }
        true
    }

    /**
     * Re-plans and re-arms every enabled schedule.
     *
     * `suspend`, and therefore awaited by its caller. It used to fire a `launch` on a repository
     * scope from inside a `BroadcastReceiver`'s `goAsync()` block: the receiver could call
     * `finish()` — and the process could then be killed — while the re-arm was still queued, and
     * any exception it threw landed nowhere. Boot is exactly when that is most likely, and exactly
     * when leaving schedules unarmed matters most (issue #28).
     */
    suspend fun syncAllAlarms(context: Context) {
        val enabled = dao.getEnabledSchedules()
        FocusScheduleAlarmScheduler.rescheduleAllSchedules(context, enabled)
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

    /**
     * Null when [normalized] collides with nothing, otherwise a [SaveResult.Rejected] naming the
     * schedule it collides with.
     *
     * Overlaps used to be accepted silently and then lost at runtime: the alarm for the second
     * schedule fired, found a session already running, and wrote one debug line before giving up.
     * The planner UI still showed both windows as protected (issue #28). Refusing the save is the
     * only point at which the user can actually see and fix the conflict.
     */
    private suspend fun rejectOverlap(
        operation: String,
        normalized: FocusScheduleEntity
    ): SaveResult.Rejected? {
        if (!normalized.isEnabled) return null
        val clash = ScheduleValidation.findOverlap(normalized, dao.getEnabledSchedules())
            ?: return null
        val reason = context?.getString(R.string.planner_overlap_error, clash.title, clash.startTime, clash.endTime)
            ?: "This overlaps \"${clash.title}\" (${clash.startTime}–${clash.endTime}). Adjust one of the two windows."
        Log.w(
            TAG,
            "$operation rejected schedule '${normalized.title}' (${normalized.id}): " +
                "overlaps '${clash.title}' (${clash.id})"
        )
        return SaveResult.Rejected(reason)
    }

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
