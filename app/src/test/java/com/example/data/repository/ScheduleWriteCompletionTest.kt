package com.example.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleWriteCompletionTest {
    @Test
    fun `defaults precede activation and alarms precede fallible backup work`() = runTest {
        val events = mutableListOf<String>()
        val result = ScheduleWriteCompletion.run({ events += "room" }, { events += "defaults" },
            { events += "alarm" }, { events += "outbox" })
        assertEquals(listOf("room", "defaults", "alarm", "outbox"), events)
        assertEquals(ScheduleWriteCompletion.Result(), result)
    }

    @Test
    fun `failed Room write changes no defaults alarms or outbox`() = runTest {
        val events = mutableListOf<String>()
        try {
            ScheduleWriteCompletion.run({ throw IOException("room") }, { events += "defaults" },
                { events += "alarm" }, { events += "outbox" })
            fail("Expected the storage error")
        } catch (_: IOException) { }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `defaults failure is partial success and still arms reminders`() = runTest {
        val events = mutableListOf<String>()
        val failure = IOException("defaults")
        val result = ScheduleWriteCompletion.run({ events += "room" }, { throw failure },
            { events += "alarm" }, { events += "outbox" })
        assertSame(failure, result.defaultsFailure)
        assertEquals(listOf("room", "alarm", "outbox"), events)
    }

    @Test
    fun `activation and outbox failures remain separate from storage success`() = runTest {
        val alarm = IOException("alarm")
        val outbox = IOException("outbox")
        val result = ScheduleWriteCompletion.run({}, {}, { throw alarm }, { throw outbox })
        assertNull(result.defaultsFailure)
        assertSame(alarm, result.activationFailure)
        assertSame(outbox, result.trackingFailure)
    }

    @Test
    fun `watchdog cannot read a new schedule with old defaults`() = runTest {
        val defaultsStarted = CompletableDeferred<Unit>()
        val releaseDefaults = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val writer = launch {
            ScheduleMutationGate.run {
                ScheduleWriteCompletion.run({ events += "room" }, {
                    defaultsStarted.complete(Unit)
                    releaseDefaults.await()
                    events += "defaults"
                }, { events += "alarm" }, {})
            }
        }
        runCurrent()
        assertTrue(defaultsStarted.isCompleted)
        val watchdog = launch { ScheduleMutationGate.run { events += "start" } }
        runCurrent()
        assertEquals(listOf("room"), events)
        writer.cancel() // Activity recreation must not open the gate halfway through setup.
        releaseDefaults.complete(Unit)
        writer.join()
        watchdog.join()
        assertEquals(listOf("room", "defaults", "alarm", "start"), events)
    }
}
