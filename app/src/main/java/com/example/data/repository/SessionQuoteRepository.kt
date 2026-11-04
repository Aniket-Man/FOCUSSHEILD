package com.example.data.repository

import android.util.Log
import com.example.FocusShieldApp
import com.example.R
import com.example.cloud.firestore.OperationType
import com.example.cloud.firestore.handleFirestoreError
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.Serializable

private const val TAG = "SessionQuoteRepository"
private const val COLLECTION_SESSION_QUOTES = "session_quotes"

/**
 * Represents a live-writing motivational quote displayed at the top of the Session Summary screen.
 * Stored in Cloud Firestore under `/session_quotes/{quoteId}` so an external Admin Website can
 * create, edit, enable/disable, or delete quotes in real time without app updates.
 */
data class SessionQuote(
    val id: String = "",
    val text: String = "",
    val author: String = "",
    val category: String = "Deep Work",
    val fontStyle: String = "editorial", // "editorial" | "calligraphy" | "classic"
    val active: Boolean = true,
    val typingSpeedMs: Long = 30L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable

object SessionQuoteRepository {

    @Volatile
    private var lastPickedQuoteId: String? = null

    @Volatile
    private var hasAttemptedPreloadSeed: Boolean = false

    val PRELOADED_QUOTES: List<SessionQuote> get() = AllStudyQuotes.SESSION_QUOTES

    private fun getFirestoreOrNull(): FirebaseFirestore? {
        return try {
            val context = FocusShieldApp.instance
            val dbId = context.getString(R.string.firestore_database_id)
            FirebaseFirestore.getInstance(dbId)
        } catch (e: Exception) {
            Log.w(TAG, "Firestore instance unavailable: ${e.message}")
            null
        }
    }

    /**
     * Curated quotes in `/session_quotes` are managed via the Firebase Admin SDK / Console
     * (client writes are denied by security rules). When the remote collection has no active
     * documents or is unreachable, [fetchRandomQuoteForSummary] falls back to [PRELOADED_QUOTES].
     */
    suspend fun ensurePreloadedInFirestore() = withContext(Dispatchers.IO) {
        hasAttemptedPreloadSeed = true
    }

    /**
     * Fetches active quotes from `/session_quotes` in Cloud Firestore database and picks one random
     * quote, avoiding immediate back-to-back repeats.
     */
    suspend fun fetchRandomQuoteForSummary(): SessionQuote = withContext(Dispatchers.IO) {
        val db = getFirestoreOrNull()
        val activeQuotes = mutableListOf<SessionQuote>()

        if (db != null && FirebaseAuth.getInstance().currentUser != null) {
            try {
                ensurePreloadedInFirestore()
                val snapshot = db.collection(COLLECTION_SESSION_QUOTES).get().await()
                for (doc in snapshot.documents) {
                    val isActive = doc.getBoolean("active") ?: true
                    val text = doc.getString("text")?.trim().orEmpty()
                    val author = doc.getString("author")?.trim().orEmpty()
                    if (isActive && text.isNotEmpty()) {
                        activeQuotes.add(
                            SessionQuote(
                                id = doc.getString("id")?.takeIf { it.isNotBlank() } ?: doc.id,
                                text = text,
                                author = author.ifEmpty { "FocusShield" },
                                category = doc.getString("category")?.takeIf { it.isNotBlank() } ?: "Deep Work",
                                fontStyle = doc.getString("fontStyle")?.takeIf { it.isNotBlank() } ?: "editorial",
                                active = true,
                                typingSpeedMs = (doc.getLong("typingSpeedMs") ?: 30L).coerceIn(10L, 120L)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.LIST, COLLECTION_SESSION_QUOTES)
                Log.w(TAG, "Falling back to local quotes due to network/Firestore error: ${e.message}")
            }
        }

        val pool = if (activeQuotes.isNotEmpty()) activeQuotes else PRELOADED_QUOTES
        val filteredPool = if (pool.size > 1 && lastPickedQuoteId != null) {
            pool.filter { it.id != lastPickedQuoteId }.ifEmpty { pool }
        } else {
            pool
        }

        val chosen = filteredPool.random()
        lastPickedQuoteId = chosen.id
        chosen
    }

    /**
     * Returns an immediate non-repeating quote synchronously for initial composition before
     * or alongside the Firestore fetch.
     */
    fun getImmediateFallbackQuote(): SessionQuote {
        val pool = PRELOADED_QUOTES
        val filtered = if (pool.size > 1 && lastPickedQuoteId != null) {
            pool.filter { it.id != lastPickedQuoteId }.ifEmpty { pool }
        } else {
            pool
        }
        return filtered.random()
    }
}
