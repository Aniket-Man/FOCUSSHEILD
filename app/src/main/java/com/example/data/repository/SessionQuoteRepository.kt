package com.example.data.repository

import android.util.Log
import com.example.FocusShieldApp
import com.example.R
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

    val PRELOADED_QUOTES: List<SessionQuote> = listOf(
        SessionQuote(
            id = "quote_01",
            text = "Focus is not about saying yes to the task in front of you. It is about saying no to a hundred other good ideas.",
            author = "Steve Jobs",
            category = "Deep Work",
            fontStyle = "editorial",
            active = true,
            typingSpeedMs = 28L
        ),
        SessionQuote(
            id = "quote_02",
            text = "Efforts and courage are not enough without purpose and direction. Every focused hour compounds into mastery.",
            author = "John F. Kennedy",
            category = "Mastery",
            fontStyle = "calligraphy",
            active = true,
            typingSpeedMs = 30L
        ),
        SessionQuote(
            id = "quote_03",
            text = "To produce at your peak level you need to work for extended periods with full concentration on a single task free from distraction.",
            author = "Cal Newport",
            category = "Deep Work",
            fontStyle = "editorial",
            active = true,
            typingSpeedMs = 26L
        ),
        SessionQuote(
            id = "quote_04",
            text = "We are what we repeatedly do. Excellence, then, is not an act, but a disciplined daily habit.",
            author = "Aristotle",
            category = "Discipline",
            fontStyle = "classic",
            active = true,
            typingSpeedMs = 32L
        ),
        SessionQuote(
            id = "quote_05",
            text = "You do not rise to the level of your goals. You fall to the level of your systems and your daily focus.",
            author = "James Clear",
            category = "Systems",
            fontStyle = "editorial",
            active = true,
            typingSpeedMs = 28L
        ),
        SessionQuote(
            id = "quote_06",
            text = "The successful warrior is the average person, with laser-like focus when everyone else is distracted.",
            author = "Bruce Lee",
            category = "Focus",
            fontStyle = "calligraphy",
            active = true,
            typingSpeedMs = 30L
        ),
        SessionQuote(
            id = "quote_07",
            text = "There is nothing outside of yourself that can ever enable you to get better, stronger, richer, quicker, or smarter. Everything is within.",
            author = "Miyamoto Musashi",
            category = "Perseverance",
            fontStyle = "classic",
            active = true,
            typingSpeedMs = 26L
        ),
        SessionQuote(
            id = "quote_08",
            text = "Concentrate all your thoughts upon the work at hand. The sun's rays do not burn until brought to a single point of focus.",
            author = "Alexander Graham Bell",
            category = "Concentration",
            fontStyle = "editorial",
            active = true,
            typingSpeedMs = 28L
        ),
        SessionQuote(
            id = "quote_09",
            text = "Discipline is choosing between what you want right now and what you want most in your life.",
            author = "Abraham Lincoln",
            category = "Discipline",
            fontStyle = "calligraphy",
            active = true,
            typingSpeedMs = 32L
        ),
        SessionQuote(
            id = "quote_10",
            text = "He who has a why to live for can bear almost any how. Keep building your future one quiet session at a time.",
            author = "Friedrich Nietzsche",
            category = "Purpose",
            fontStyle = "editorial",
            active = true,
            typingSpeedMs = 28L
        ),
        SessionQuote(
            id = "quote_11",
            text = "Clarity of vision creates stillness of mind. Guard your attention like your most valuable possession.",
            author = "Marcus Aurelius",
            category = "Stillness",
            fontStyle = "classic",
            active = true,
            typingSpeedMs = 28L
        ),
        SessionQuote(
            id = "quote_12",
            text = "It is not that we have a short time to live, but that we waste a lot of it. Own your hours.",
            author = "Seneca",
            category = "Time",
            fontStyle = "editorial",
            active = true,
            typingSpeedMs = 30L
        ),
        SessionQuote(
            id = "quote_13",
            text = "Genius is 1% talent and 99% hard work. Concentrated study dissolves every obstacle.",
            author = "Albert Einstein",
            category = "Mastery",
            fontStyle = "calligraphy",
            active = true,
            typingSpeedMs = 26L
        ),
        SessionQuote(
            id = "quote_14",
            text = "Our greatest weakness lies in giving up. The most certain way to succeed is always to try just one more time.",
            author = "Thomas Edison",
            category = "Perseverance",
            fontStyle = "classic",
            active = true,
            typingSpeedMs = 28L
        ),
        SessionQuote(
            id = "quote_15",
            text = "It has long since come to my attention that people of accomplishment rarely sat back and let things happen to them.",
            author = "Leonardo da Vinci",
            category = "Focus",
            fontStyle = "editorial",
            active = true,
            typingSpeedMs = 30L
        ),
        SessionQuote(
            id = "quote_16",
            text = "You may encounter many defeats, but you must not be defeated. Step forward into your goals.",
            author = "Maya Angelou",
            category = "Courage",
            fontStyle = "calligraphy",
            active = true,
            typingSpeedMs = 28L
        ),
        SessionQuote(
            id = "quote_17",
            text = "It always seems impossible until it's done. Silent daily work builds invincible power.",
            author = "Nelson Mandela",
            category = "Determination",
            fontStyle = "classic",
            active = true,
            typingSpeedMs = 30L
        ),
        SessionQuote(
            id = "quote_18",
            text = "It does not matter how slowly you go as long as you do not stop.",
            author = "Confucius",
            category = "Consistency",
            fontStyle = "editorial",
            active = true,
            typingSpeedMs = 28L
        ),
        SessionQuote(
            id = "quote_19",
            text = "First say to yourself what you would be; and then do what you have to do.",
            author = "Epictetus",
            category = "Discipline",
            fontStyle = "classic",
            active = true,
            typingSpeedMs = 28L
        ),
        SessionQuote(
            id = "quote_20",
            text = "Your future is shaped by the silent hours of focus you invest today.",
            author = "FocusShield",
            category = "Daily Mastery",
            fontStyle = "editorial",
            active = true,
            typingSpeedMs = 26L
        )
    )

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
     * Ensures the `/session_quotes` collection in Cloud Firestore is populated with all curated
     * quotes so the database serves as the live source of truth for both the app and the external Admin Website.
     */
    suspend fun ensurePreloadedInFirestore() = withContext(Dispatchers.IO) {
        if (hasAttemptedPreloadSeed) return@withContext
        val db = getFirestoreOrNull() ?: return@withContext
        try {
            val snapshot = db.collection(COLLECTION_SESSION_QUOTES).get().await()
            val existingIds = snapshot.documents.map { it.id }.toSet()
            val now = System.currentTimeMillis()

            for (quote in PRELOADED_QUOTES) {
                if (!existingIds.contains(quote.id)) {
                    val payload = hashMapOf<String, Any>(
                        "id" to quote.id,
                        "text" to quote.text,
                        "author" to quote.author,
                        "category" to quote.category,
                        "fontStyle" to quote.fontStyle,
                        "active" to quote.active,
                        "typingSpeedMs" to quote.typingSpeedMs,
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                    runCatching {
                        db.collection(COLLECTION_SESSION_QUOTES)
                            .document(quote.id)
                            .set(payload, SetOptions.merge())
                            .await()
                    }
                }
            }
            hasAttemptedPreloadSeed = true
        } catch (e: Exception) {
            Log.w(TAG, "Skipping Firestore quote preload seed: ${e.message}")
        }
    }

    /**
     * Fetches active quotes from `/session_quotes` in Cloud Firestore database and picks one random
     * quote, avoiding immediate back-to-back repeats.
     */
    suspend fun fetchRandomQuoteForSummary(): SessionQuote = withContext(Dispatchers.IO) {
        val db = getFirestoreOrNull()
        val activeQuotes = mutableListOf<SessionQuote>()

        if (db != null) {
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
