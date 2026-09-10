package com.example.data.repository

import android.util.Log
import com.example.cloud.sync.CloudJson
import com.example.cloud.sync.SyncTables
import com.example.cloud.sync.SyncTracker
import com.example.data.local.dao.ScratchCardDao
import com.example.data.local.entity.ScratchCardEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Repository for scratch card rewards. Creates one card per eligible completed
 * session (>= 10 pure study minutes), tracks reveal state, and exposes the
 * latest un-revealed card for the session completion screen.
 */
class ScratchCardRepository(
    private val dao: ScratchCardDao,
    private val scope: CoroutineScope
) {

    /** Minimum pure study minutes for a session to earn a scratch card. */
    private val minEligibleMinutes = 10

    fun getLatestUnrevealedCardFlow(): Flow<ScratchCardEntity?> = dao.getLatestUnrevealedCardFlow()

    suspend fun getCardForSession(sessionId: String): ScratchCardEntity? =
        withContext(Dispatchers.IO) { dao.getCardForSession(sessionId) }

    suspend fun getRecentCards(limit: Int = 20): List<ScratchCardEntity> =
        withContext(Dispatchers.IO) { dao.getRecentCards(limit) }

    fun getRevealedCountFlow(): Flow<Int> = dao.getRevealedCountFlow()

    /**
     * Creates a scratch card for a completed session if eligible and no card
     * exists yet for that session. Returns the created (or existing) card, or
     * null when the session is not eligible.
     */
    suspend fun createCardIfEligible(
        sessionId: String,
        subject: String,
        studyMinutes: Int,
        currentStreak: Int,
        allTimeStudyMillis: Long
    ): ScratchCardEntity? = withContext(Dispatchers.IO) {
        val existing = dao.getCardForSession(sessionId)
        if (existing != null) return@withContext existing
        if (studyMinutes < minEligibleMinutes) return@withContext null

        val card = ScratchCardRewardFactory.generate(
            sessionId = sessionId,
            subject = subject,
            studyMinutes = studyMinutes,
            currentStreak = currentStreak,
            allTimeStudyMillis = allTimeStudyMillis
        )
        val insertResult = dao.insert(card)
        if (insertResult == -1L) {
            null
        } else {
            enqueueCardUpsert(card)
            card
        }
    }

    fun markRevealedAsync(sessionId: String) {
        scope.launch {
            try {
                dao.markRevealed(sessionId)
                // The DAO mutates a flag without returning the row, so read it back to push the
                // revealed state rather than re-deriving the card here.
                dao.getCardForSession(sessionId)?.let { enqueueCardUpsert(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to mark scratch card revealed", e)
            }
        }
    }

    private suspend fun enqueueCardUpsert(card: ScratchCardEntity) {
        SyncTracker.enqueueUpsert(
            SyncTables.SCRATCH_CARDS,
            card.sessionId,
            CloudJson.scratchCardToJson(card).toString()
        )
    }

    companion object {
        private const val TAG = "ScratchCardRepo"
    }
}
