package com.example.data.repository

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** After Room accepts the row, finish local setup before any alarm or watchdog may start it. */
internal object ScheduleWriteCompletion {
    data class Result(
        val defaultsFailure: Exception? = null,
        val activationFailure: Exception? = null,
        val trackingFailure: Exception? = null
    )

    suspend fun run(
        persistSchedule: suspend () -> Unit,
        saveDefaults: suspend () -> Unit,
        armSchedule: suspend () -> Unit,
        trackChange: suspend () -> Unit
    ): Result {
        currentCoroutineContext().ensureActive()
        // A rotation cannot stop between the Room write and the DataStore edit. These are separate
        // stores: failures after persistence are partial success, never a rejected/duplicate save.
        return withContext(NonCancellable) {
            persistSchedule()
            val defaultsFailure = try { saveDefaults(); null } catch (exception: Exception) { exception }
            val activationFailure = try { armSchedule(); null } catch (exception: Exception) { exception }
            // Alarm setup precedes fallible outbox work so a backup error cannot kill reminders.
            val trackingFailure = try { trackChange(); null } catch (exception: Exception) { exception }
            Result(defaultsFailure, activationFailure, trackingFailure)
        }
    }
}
