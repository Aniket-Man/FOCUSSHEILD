package com.example.data.repository

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

sealed interface AppLimitWriteResult {
    /** The local write committed. A follow-up failure must not invite a duplicate save. */
    data class Applied(val followUpFailure: Exception? = null) : AppLimitWriteResult
    data class Rejected(val reason: Reason) : AppLimitWriteResult
    data class Failed(val exception: Exception) : AppLimitWriteResult

    enum class Reason { STRICT_LOCKED, MISSING, INVALID, BUSY }
}

/** Completes short local writes even when the editor's activity is recreated. */
internal object AppLimitWriteCompletion {
    suspend fun run(persist: suspend () -> Unit, vararg followUps: suspend () -> Unit): AppLimitWriteResult.Applied {
        currentCoroutineContext().ensureActive()
        return withContext(NonCancellable) {
            persist()
            var failure: Exception? = null
            followUps.forEach { followUp ->
                try { followUp() } catch (exception: Exception) {
                    if (failure == null) failure = exception else failure?.addSuppressed(exception)
                }
            }
            AppLimitWriteResult.Applied(failure)
        }
    }
}
