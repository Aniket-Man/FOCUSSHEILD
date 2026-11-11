package com.example.feature.profile

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Photo replacement is copy → persist → retire, never delete → persist. */
internal object ProfileEditorSave {
    sealed interface Outcome {
        data class Saved(val photoUri: String?, val followUpFailure: Exception? = null) : Outcome
        data class Failed(val exception: Exception) : Outcome
    }

    suspend fun run(
        selectedPhoto: String?,
        preparePhoto: suspend (String?) -> String?,
        persistProfile: suspend (String?) -> Unit,
        discardPreparedPhoto: suspend (String) -> Unit,
        finishPhoto: suspend (String?) -> Unit
    ): Outcome {
        currentCoroutineContext().ensureActive()
        // These short local writes form one critical section. A rotation must not delete a new
        // file after DataStore committed its URI but before it returned to the caller.
        return withContext(NonCancellable) {
            var preparedPhoto: String? = null
            try {
                preparedPhoto = preparePhoto(selectedPhoto)
                persistProfile(preparedPhoto)
            } catch (cancelled: CancellationException) {
                // Keep the candidate when the persistence result is uncertain.
                throw cancelled
            } catch (exception: Exception) {
                if (preparedPhoto != null && preparedPhoto != selectedPhoto) {
                    try { discardPreparedPhoto(preparedPhoto) }
                    catch (_: Exception) { /* An orphan is safer than misreporting the persisted profile. */ }
                }
                return@withContext Outcome.Failed(exception)
            }
            val followUpFailure = try { finishPhoto(preparedPhoto); null }
            catch (exception: Exception) { exception }
            Outcome.Saved(preparedPhoto, followUpFailure)
        }
    }
}
