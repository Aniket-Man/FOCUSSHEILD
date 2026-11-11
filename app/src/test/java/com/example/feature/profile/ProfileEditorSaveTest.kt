package com.example.feature.profile

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileEditorSaveTest {
    @Test
    fun `replacement is persisted before the old photo is retired`() = runTest {
        val events = mutableListOf<String>()
        val result = ProfileEditorSave.run("staged", { events += "copy"; "candidate" },
            { events += "persist:$it" }, { events += "discard:$it" }, { events += "retire-old:$it" })
        assertEquals(listOf("copy", "persist:candidate", "retire-old:candidate"), events)
        assertEquals("candidate", (result as ProfileEditorSave.Outcome.Saved).photoUri)
    }

    @Test
    fun `failed persistence discards only the candidate and keeps editable input`() = runTest {
        val events = mutableListOf<String>()
        val failure = IOException("DataStore unavailable")
        val result = ProfileEditorSave.run("staged", { "candidate" }, { throw failure },
            { events += "discard:$it" }, { events += "retire-old" })
        assertSame(failure, (result as ProfileEditorSave.Outcome.Failed).exception)
        assertEquals(listOf("discard:candidate"), events)
    }

    @Test
    fun `failed removal never deletes the saved photo`() = runTest {
        var deleted = false
        val result = ProfileEditorSave.run(null, { it }, { throw IOException("write failed") },
            { deleted = true }, { deleted = true })
        assertTrue(result is ProfileEditorSave.Outcome.Failed)
        assertFalse(deleted)
    }

    @Test
    fun `unavailable crop is not persisted as a fallback URI`() = runTest {
        var persisted = false
        val result = ProfileEditorSave.run("missing crop", { throw IOException("missing") },
            { persisted = true }, {}, {})
        assertTrue(result is ProfileEditorSave.Outcome.Failed)
        assertFalse(persisted)
    }

    @Test
    fun `follow up failure is partial success and never rolls back a persisted URI`() = runTest {
        var discarded = false
        val failure = IOException("backup marker failed")
        val result = ProfileEditorSave.run("staged", { "candidate" }, {}, { discarded = true }, { throw failure })
        assertEquals("candidate", (result as ProfileEditorSave.Outcome.Saved).photoUri)
        assertSame(failure, result.followUpFailure)
        assertFalse(discarded)
    }

    @Test
    fun `rotation during persistence completes the local critical section`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var finished = false
        var discarded = false
        val job = launch {
            ProfileEditorSave.run("staged", { "candidate" }, { started.complete(Unit); release.await() },
                { discarded = true }, { finished = true })
        }
        runCurrent()
        assertTrue(started.isCompleted)
        job.cancel()
        release.complete(Unit)
        job.join()
        assertTrue(finished)
        assertFalse(discarded)
    }
}
