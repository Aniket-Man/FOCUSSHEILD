package com.example.data.repository

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Shared by local schedule mutations and alarm/watchdog starts, in the app's single process. */
internal object ScheduleMutationGate {
    private val mutex = Mutex()
    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}
